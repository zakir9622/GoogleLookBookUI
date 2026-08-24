package com.zakir.vestra.shared.engine.pro

import android.graphics.Bitmap
import android.util.Log
import com.zakir.vestra.shared.diagnostics.DiagnosticsHook
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.effectiveCategory
import com.zakir.vestra.shared.engine.pipeline.CastingPromptBuilder
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.PackVerifyStatus
import com.zakir.vestra.shared.domain.TryOnError
import com.zakir.vestra.shared.domain.TryOnRequest
import com.zakir.vestra.shared.domain.TryOnResult
import com.zakir.vestra.shared.engine.Availability
import com.zakir.vestra.shared.engine.ProOrtFailure
import com.zakir.vestra.shared.engine.TryOnEngine
import com.zakir.vestra.shared.engine.UnavailableReason
import com.zakir.vestra.shared.engine.lite.GarmentClassifier
import com.zakir.vestra.shared.engine.lite.LiteEngineIo
import com.zakir.vestra.shared.engine.lite.OrtSessionCache
import com.zakir.vestra.shared.engine.lite.Watermark
import com.zakir.vestra.shared.engine.pipeline.ConditioningStage
import com.zakir.vestra.shared.packs.DeviceProbe
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.quality.NoOpQualityPostProcessor
import com.zakir.vestra.shared.quality.QualityEnhancer
import com.zakir.vestra.shared.quality.QualityPostProcessor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

/**
 * On-device try-on diffusion in the CatVTON style: a single inpainting UNet
 * conditioned by concatenating the garment latent spatially — no text encoder.
 *
 * The pack ("pro-v1") supplies vae_encoder.onnx / vae_decoder.onnx / unet.onnx
 * plus config.json (see [ProPackConfig]). The engine is model-agnostic within
 * that contract; per-stage timings are logged under [TAG] as the benchmark
 * record for real-device validation (see docs/ARCHITECTURE.md, M4).
 */
class DiffusionEngine(
    private val packs: ModelPackManager,
    private val device: DeviceProbe,
    private val io: LiteEngineIo,
    private val masker: PersonMasker,
    private val parsing: com.zakir.vestra.shared.engine.lite.HumanParsing,
    private val applyWatermark: Boolean = false,
    private val quality: QualityPostProcessor = NoOpQualityPostProcessor,
) : TryOnEngine {

    override val tier: EngineTier = EngineTier.PRO

    private fun resolvePackId(): String? =
        PACK_IDS.firstOrNull { packs.isInstalled(it) }

    override fun isAvailable(): Availability {
        val packId = resolvePackId() ?: return Availability.Unavailable(UnavailableReason.PACK_NOT_INSTALLED)
        val pack = packs.pack(packId)
        return when {
            pack != null && !packs.deviceMeets(pack.minSpec) ->
                Availability.Unavailable(UnavailableReason.DEVICE_NOT_CAPABLE)
            !packs.isInstalled(packId) ->
                Availability.Unavailable(UnavailableReason.PACK_NOT_INSTALLED)
            !packs.isReady(packId) -> when (packs.verifyStatus(packId)) {
                PackVerifyStatus.FAILED ->
                    Availability.Unavailable(UnavailableReason.PACK_VERIFY_FAILED)
                else ->
                    Availability.Unavailable(UnavailableReason.PACK_VERIFY_PENDING)
            }
            !packs.isInstalled(com.zakir.vestra.shared.engine.lite.LiteEngine.PACK_ID) ->
                Availability.Unavailable(UnavailableReason.COMPANION_PACK_MISSING)
            !packs.isReady(com.zakir.vestra.shared.engine.lite.LiteEngine.PACK_ID) ->
                when (packs.verifyStatus(com.zakir.vestra.shared.engine.lite.LiteEngine.PACK_ID)) {
                    PackVerifyStatus.FAILED ->
                        Availability.Unavailable(UnavailableReason.PACK_VERIFY_FAILED)
                    else ->
                        Availability.Unavailable(UnavailableReason.PACK_VERIFY_PENDING)
                }
            else -> Availability.Ready
        }
    }

    override fun generate(request: TryOnRequest): Flow<GenerationState> = flow {
        when (val availability = isAvailable()) {
            is Availability.Ready -> Unit
            is Availability.Unavailable -> {
                emit(GenerationState.Failed(availability.reason.toProError()))
                return@flow
            }
        }
        val packId = resolvePackId()
        val packDir = packId?.let { packs.installedDir(it) }
        if (packDir == null) {
            emit(GenerationState.Failed(TryOnError.ModelPackMissing))
            return@flow
        }
        packId.let { packs.markPackInUse(it) }
        packs.markPackInUse(com.zakir.vestra.shared.engine.lite.LiteEngine.PACK_ID)
        OrtSessionCache.enterInference()
        val startedAt = System.currentTimeMillis()
        val diag = DiagnosticsHook.startTryOn(EngineTier.PRO, modelLabel = packId)

        try {
            emit(GenerationState.Preparing("Reading images"))
            kotlinx.coroutines.yield()
            val person = io.loadPerson(request.person)
            val garment = io.loadBitmap(request.garment.uri)
            if (person == null || garment == null) {
                DiagnosticsHook.completeTryOn(diag, false, "Couldn't read the selected images")
                emit(GenerationState.Failed(TryOnError.Internal("Couldn't read the selected images")))
                return@flow
            }

            // Auto: ATR on person when Lite parse is available; else garment geometry.
            emit(GenerationState.Running(0.03f, "Loading body parse…"))
            kotlinx.coroutines.yield()
            val category = request.garment.category?.effectiveCategory()
                ?: parsing.classifyWorn(person)
                ?: GarmentClassifier.classify(garment)
            val promptSpec = com.zakir.vestra.shared.engine.pipeline.PromptSpec(
                positive = CastingPromptBuilder.buildPositive(request.casting, category),
                negative = CastingPromptBuilder.buildNegative(),
            )
            val finish: (Bitmap) -> String = { bmp ->
                val enhanced = QualityEnhancer.upscaleIfInstalled(quality, bmp)
                io.saveResult(if (applyWatermark) Watermark.apply(enhanced) else enhanced)
            }

            val config = Json { ignoreUnknownKeys = true }
                .decodeFromString<ProPackConfig>(File("$packDir/config.json").readText())

            val sdPipeline = SdControlNetPipeline(
                packDir = packDir,
                config = config,
                loadPerson = { person },
                loadGarment = { garment },
                maskProvider = { p -> masker.maskFor(p, category) },
                saveResult = finish,
            )
            if (sdPipeline.requirements().isFullyConditioned) {
                try {
                    val outPath = sdPipeline.run(
                        inputs = com.zakir.vestra.shared.engine.pipeline.ConditioningInputs(
                            personImagePath = "",
                            garmentImagePath = request.garment.uri,
                            maskPath = null,
                            prompt = promptSpec,
                            seed = request.seed,
                        ),
                        onStage = { stage, fraction -> emit(GenerationState.Running(fraction, stage.label)) },
                    )
                    emit(
                        GenerationState.Complete(
                            TryOnResult(
                                imagePath = outPath,
                                executedTier = EngineTier.PRO,
                                durationMillis = System.currentTimeMillis() - startedAt,
                                watermarked = applyWatermark,
                            ),
                        ),
                    )
                    DiagnosticsHook.completeTryOn(diag, success = true, note = "SD-ControlNet · $packId")
                    return@flow
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "SD-ControlNet pipeline failed; falling back", error)
                    val chain = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }
                        .joinToString(" | ")
                    if (ProOrtFailure.isPackIncompatible(chain) || ProOrtFailure.isPackIncompatible(error.message)) {
                        val friendly = friendlyProFailure(error)
                        packs.markGraphIncompatible(packId, friendly)
                        DiagnosticsHook.completeTryOn(diag, false, friendly)
                        emit(GenerationState.Failed(TryOnError.Internal(friendly)))
                        return@flow
                    }
                    emit(
                        GenerationState.Running(
                            0.08f,
                            "SD pack unavailable — using legacy Pro compositor…",
                        ),
                    )
                }
            }

            // ── Stage 1: STRUCTURE — establish structural bounds (ControlNet
            // condition). The parse-derived body mask locks the garment to the
            // model's pose; a dedicated pose/depth model slots in here when the
            // pack ships one (config.structureModel).
            emit(GenerationState.Running(0.05f, ConditioningStage.STRUCTURE.label))
            var t0 = System.currentTimeMillis()
            val mask = masker.maskFor(person, category)
            if (mask == null) {
                emit(
                    GenerationState.Failed(
                        TryOnError.Internal("No person detected — try a clearer full-body photo"),
                    ),
                )
                return@flow
            }
            log(diag, "structure_mask", t0)

            LatentCodec(packDir, config).use { codec ->
                // ── Stage 2: TEXTURE — inject garment features (IP-Adapter). The
                // garment latent carries appearance into cross-attention instead
                // of a hallucination-prone text description.
                emit(GenerationState.Running(0.12f, ConditioningStage.TEXTURE.label))
                t0 = System.currentTimeMillis()
                val personCanvas = codec.centerCrop(person)
                val garmentCanvas = codec.centerCrop(garment)
                val maskCanvas = codec.resizeMask(mask, person.width, person.height)

                val maskedPersonLatent = codec.encodeMasked(personCanvas, maskCanvas)
                val garmentLatent = codec.encode(garmentCanvas)
                val conditionLatent = codec.conditionLatent(maskedPersonLatent, garmentLatent)
                val unconditionalLatent = codec.unconditionalLatent(maskedPersonLatent)
                val maskConcat = codec.maskConcat(maskCanvas)
                log(diag, "ipadapter_encode", t0)

                // ── Stage 3: SYNTHESIS — diffuse under structure + texture +
                // PromptStyle guidance (CFG 7.0, 20–25 steps, mobile-safe).
                val scheduler = DdimScheduler()
                val steps = DiffusionSteps.resolve(config.inferenceSteps, config.lcmDistilled)
                val cfg = config.guidanceScale
                val timesteps = scheduler.timesteps(steps)
                val random = request.seed?.let { Random(it) } ?: Random(System.nanoTime())
                val sample = codec.initialNoise(random)

                t0 = System.currentTimeMillis()
                emit(GenerationState.Running(0.14f, "Loading Pro diffusion UNet (first run may take a minute)…"))
                codec.openUnet().use { unet ->
                    timesteps.forEachIndexed { index, timestep ->
                        val noisePred = unet.predictNoise(
                            sample = sample,
                            conditionLatent = conditionLatent,
                            unconditionalLatent = unconditionalLatent,
                            maskConcat = maskConcat,
                            timestep = timestep,
                            guidanceScale = cfg,
                        )
                        scheduler.step(sample, noisePred, timestep, steps)
                        emit(
                            GenerationState.Running(
                                0.15f + 0.7f * (index + 1) / steps,
                                "${ConditioningStage.SYNTHESIS.label} · ${index + 1}/$steps",
                            ),
                        )
                    }
                }
                log(diag, "synthesis_${steps}steps_cfg${cfg}", t0)

                emit(GenerationState.Running(0.88f, "Developing"))
                t0 = System.currentTimeMillis()
                val decoded = codec.decodePersonHalf(sample)
                // Keep untouched person pixels outside the inpaint mask.
                val composed = codec.pasteBack(person, decoded, maskCanvas)
                log(diag, "vae_decode", t0)

                // Studio backdrop via the Lite pack's segmenter (installed as a Pro dependency).
                val liteDir = packs.installedDir(com.zakir.vestra.shared.engine.lite.LiteEngine.PACK_ID)
                val staged = if (request.backdrop == com.zakir.vestra.shared.domain.Backdrop.ORIGINAL || liteDir == null) {
                    composed
                } else {
                    emit(GenerationState.Running(0.93f, "Setting the backdrop"))
                    runCatching {
                        com.zakir.vestra.shared.engine.lite.BackdropCompositor("$liteDir/garment_seg.onnx")
                            .apply(composed, request.backdrop)
                    }.getOrElse { composed }
                }

                val outPath = finish(staged)
                emit(
                    GenerationState.Complete(
                        TryOnResult(
                            imagePath = outPath,
                            executedTier = EngineTier.PRO,
                            durationMillis = System.currentTimeMillis() - startedAt,
                            watermarked = applyWatermark,
                        ),
                    ),
                )
                DiagnosticsHook.completeTryOn(diag, success = true, note = "legacy Pro · $packId")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Pro generation failed", error)
            val friendly = friendlyProFailure(error)
            if (ProOrtFailure.isPackIncompatible(friendly) || ProOrtFailure.isPackIncompatible(error.message)) {
                packs.markGraphIncompatible(packId, friendly)
            }
            DiagnosticsHook.completeTryOn(diag, false, friendly)
            emit(GenerationState.Failed(TryOnError.Internal(friendly)))
        } catch (error: Throwable) {
            Log.e(TAG, "Pro generation native failure", error)
            val friendly =
                "Pro try-on crashed in native code — switch to Lite, or re-download pro-v1 + lite-v1."
            packs.markGraphIncompatible(packId, friendly)
            DiagnosticsHook.completeTryOn(diag, false, friendly)
            emit(GenerationState.Failed(TryOnError.Internal(friendly)))
        } finally {
            OrtSessionCache.leaveInference()
            packId?.let { packs.markPackIdle(it) }
            packs.markPackIdle(com.zakir.vestra.shared.engine.lite.LiteEngine.PACK_ID)
        }
    }.flowOn(Dispatchers.Default)

    private fun log(diag: DiagnosticsHook.RunHandle?, stage: String, since: Long) {
        val ms = System.currentTimeMillis() - since
        Log.i(TAG, "$stage: $ms ms (ram=${device.totalRamMb()} MB)")
        DiagnosticsHook.stage(diag, stage, since)
    }

    companion object {
        val PACK_IDS = listOf("pro-v1", "pro-v2-int8")
        const val TAG = "VestraProBench"
    }
}

private fun friendlyProFailure(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
    val pathHint = Regex("""Load model from ([^\s]+)""").find(chain)?.groupValues?.getOrNull(1)
        ?: Regex("""\(([^)]+\.onnx)\)""").find(chain)?.groupValues?.getOrNull(1)
    if (pathHint != null) {
        return ProOrtSessions.friendlyMessage(pathHint, error)
    }
    if (ProOrtFailure.isPackIncompatible(chain)) {
        return "Pro pack is incompatible with this ONNX Runtime on your device. " +
            "Use Lite try-on, or re-download pro-v1 / try pro-v2-int8 in Settings → Model packs."
    }
    // Never dump raw ORT to the UI — keep a short actionable line.
    return "Pro try-on failed on this device — switch to Lite, or re-download the Pro pack in Settings."
}

private fun UnavailableReason.toProError(): TryOnError = when (this) {
    UnavailableReason.PACK_VERIFY_FAILED ->
        TryOnError.Internal(
            "Pro pack failed verification — open Settings → Model packs and re-download pro-v1 (or pro-v2-int8) and lite-v1.",
        )
    UnavailableReason.PACK_VERIFY_PENDING ->
        TryOnError.Internal("Model packs are still verifying — wait a moment and try again.")
    UnavailableReason.PACK_NOT_INSTALLED ->
        TryOnError.Internal(
            "Pro model pack not installed. Open Settings → Model packs to download pro-v1 (~4.3 GB) and lite-v1.",
        )
    UnavailableReason.COMPANION_PACK_MISSING ->
        TryOnError.Internal("Pro needs the Lite pack too — download lite-v1 in Settings → Model packs.")
    UnavailableReason.DEVICE_NOT_CAPABLE -> TryOnError.DeviceNotCapable
    else -> TryOnError.Internal("Pro engine unavailable — check Settings → Model packs.")
}

/** Person-region mask provider; implemented by the Lite pipeline's parser. */
fun interface PersonMasker {
    /** 0..1 row-major mask over the person image for the garment area; null = no person found. */
    fun maskFor(person: Bitmap, category: GarmentCategory): FloatArray?
}

@kotlinx.serialization.Serializable
data class ProPackConfig(
    val latentWidth: Int = 96,
    val latentHeight: Int = 128,
    val imageWidth: Int = 768,
    val imageHeight: Int = 1024,
    // Photorealism defaults (PromptStyle): CFG 7.0, 22 steps. A pack tuned for a
    // low-CFG model (e.g. CatVTON) may override both in its config.json.
    val inferenceSteps: Int = com.zakir.vestra.shared.engine.pipeline.PromptStyle.STEPS,
    /** When true, use LCM/Hyper-SD distilled step count (4–8) instead of full diffusion. */
    val lcmDistilled: Boolean = false,
    val guidanceScale: Float = com.zakir.vestra.shared.engine.pipeline.PromptStyle.CFG_SCALE,
    val vaeScale: Float = 0.18215f,
    /** "width" or "height" — axis the garment latent is concatenated along (legacy CatVTON path). */
    val concatAxis: String = "width",
    /** Square working resolution for the SD1.5+ControlNet+IP-Adapter path. */
    val resolution: Int = 512,
    // ── SD1.5 + ControlNet-Depth + IP-Adapter component files (P7) ──
    /** ControlNet-Depth ONNX producing down/mid residuals. */
    val controlNet: String? = null,
    /** Monocular depth estimator ONNX for Stage A structural conditioning. */
    val depthModel: String? = null,
    /** IP-Adapter projection/resampler ONNX. Unused by the fused-UNet pack: the
     *  IP-Adapter Plus resampler + attention are baked into unet.onnx, so the
     *  image encoder's raw embeds feed the UNet directly (see SdControlNetPipeline). */
    val ipAdapter: String? = null,
    /** CLIP-H image encoder ONNX. Emits penultimate hidden states [1,ipEmbedSeq,ipEmbedDim]. */
    val imageEncoder: String? = null,
    /** SD text encoder ONNX (prompt → embeddings) for PromptStyle guidance. */
    val textEncoder: String? = null,
    /** Optional legacy alias retained for older packs. */
    val structureModel: String? = null,
    // ── Shapes emitted by the converter (ml/convert_pro_pack.py) so the runtime
    //    feeds exactly what the exported graphs declare. ──
    /** IP-Adapter image-embed sequence length (CLIP-H penultimate: 257). */
    val ipEmbedSeq: Int = 257,
    /** IP-Adapter image-embed hidden dim (CLIP-H: 1280). */
    val ipEmbedDim: Int = 1280,
    /** ControlNet down_0..down_11 + mid residual shapes (SD1.5 @512), in order. */
    val residualShapes: List<List<Int>> = DEFAULT_RESIDUAL_SHAPES,
) {
    companion object {
        /** SD1.5 ControlNet residual shapes at 512×512 (64×64 latent). */
        val DEFAULT_RESIDUAL_SHAPES: List<List<Int>> = listOf(
            listOf(1, 320, 64, 64), listOf(1, 320, 64, 64), listOf(1, 320, 64, 64),
            listOf(1, 320, 32, 32), listOf(1, 640, 32, 32), listOf(1, 640, 32, 32),
            listOf(1, 640, 16, 16), listOf(1, 1280, 16, 16), listOf(1, 1280, 16, 16),
            listOf(1, 1280, 8, 8), listOf(1, 1280, 8, 8), listOf(1, 1280, 8, 8),
            listOf(1, 1280, 8, 8),
        )
    }
}
