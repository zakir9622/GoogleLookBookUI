package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap
import com.zakir.vestra.shared.diagnostics.DiagnosticsHook
import com.zakir.vestra.shared.time.EpochClock
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.PackVerifyStatus
import com.zakir.vestra.shared.domain.TryOnError
import com.zakir.vestra.shared.domain.TryOnRequest
import com.zakir.vestra.shared.domain.TryOnResult
import com.zakir.vestra.shared.domain.effectiveCategory
import com.zakir.vestra.shared.engine.Availability
import com.zakir.vestra.shared.engine.TryOnEngine
import com.zakir.vestra.shared.engine.UnavailableReason
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.quality.NoOpQualityPostProcessor
import com.zakir.vestra.shared.quality.QualityEnhancer
import com.zakir.vestra.shared.quality.QualityPostProcessor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Fully-offline try-on for every supported device:
 *
 *  1. garment_seg.onnx  → soft garment mask → alpha cutout
 *  2. human_parse.onnx  → per-pixel body-region classes on the person image
 *  3. contour warp      → garment meshed onto the target region's row profile
 *  4. harmonize + blend → luminance match, feathered composite
 *  5. finalize          → AI watermark + EXIF tag, JPEG to app storage
 *
 * Model files come from the "lite-v1" pack; nothing here touches the network.
 */
class LiteEngine(
    private val packs: ModelPackManager,
    private val io: LiteEngineIo,
    private val parsing: HumanParsing,
    private val quality: QualityPostProcessor = NoOpQualityPostProcessor,
) : TryOnEngine {

    override val tier: EngineTier = EngineTier.LITE

    override fun isAvailable(): Availability = when {
        !packs.isInstalled(PACK_ID) ->
            Availability.Unavailable(UnavailableReason.PACK_NOT_INSTALLED)
        packs.isReady(PACK_ID) -> Availability.Ready
        packs.verifyStatus(PACK_ID) == PackVerifyStatus.FAILED ->
            Availability.Unavailable(UnavailableReason.PACK_VERIFY_FAILED)
        else ->
            Availability.Unavailable(UnavailableReason.PACK_VERIFY_PENDING)
    }

    override fun generate(request: TryOnRequest): Flow<GenerationState> = flow {
        if (!packs.isReady(PACK_ID)) {
            val msg = when (packs.verifyStatus(PACK_ID)) {
                PackVerifyStatus.FAILED ->
                    "Lite pack failed verification — open Settings → Model packs and re-download lite-v1."
                PackVerifyStatus.VERIFYING ->
                    "Lite pack is verifying — wait a moment and try again."
                else ->
                    "Lite pack not ready yet — wait for verification to finish."
            }
            emit(GenerationState.Failed(TryOnError.Internal(msg)))
            return@flow
        }
        val packDir = packs.installedDir(PACK_ID)
        if (packDir == null) {
            emit(GenerationState.Failed(TryOnError.ModelPackMissing))
            return@flow
        }

        packs.markPackInUse(PACK_ID)
        OrtSessionCache.enterInference()
        val diag = DiagnosticsHook.startTryOn(EngineTier.LITE)
        try {
            val startedAt = EpochClock.System.nowMs()

            emit(GenerationState.Preparing("Reading images"))
            var t0 = EpochClock.System.nowMs()
            val garment = io.loadBitmap(request.garment.uri)
            val person = io.loadPerson(request.person)
            DiagnosticsHook.stage(diag, "read_images", t0)
            if (garment == null || person == null) {
                DiagnosticsHook.completeTryOn(diag, false, "Couldn't read the selected images")
                emit(GenerationState.Failed(TryOnError.Internal("Couldn't read the selected images")))
                return@flow
            }

            emit(GenerationState.Running(0.12f, "Loading Lite models…"))
            // Yield so the UI can paint before heavy ORT session create (Pixel 9 LMK path).
            kotlinx.coroutines.yield()

            emit(GenerationState.Running(0.15f, "Extracting garment"))
            t0 = EpochClock.System.nowMs()
            val garmentCut = runCatching {
                OrtSessionCache.open("$packDir/garment_seg.onnx").let { model ->
                    extractGarment(model, garment)
                }
            }.getOrElse { error ->
                DiagnosticsHook.completeTryOn(diag, false, error.message?.take(120))
                emit(
                    GenerationState.Failed(
                        TryOnError.Internal(
                            softOrtMessage(error)
                                ?: "Lite segmentation model failed — re-download lite-v1 in Settings → Model packs.",
                        ),
                    ),
                )
                return@flow
            }
            DiagnosticsHook.stage(diag, "garment_seg", t0)

            emit(GenerationState.Running(0.45f, "Reading the body"))
            t0 = EpochClock.System.nowMs()
            // Auto: one ATR pass on the person → full taxonomy; reuse map for region.
            // Manual chip: skip classify; still one parse for the mask.
            val parsed = parsing.parse(person)
            val category = request.garment.category?.effectiveCategory()
                ?: parsed?.let { GarmentClassifier.classifyFromAtr(it.classMap) }
                ?: GarmentClassifier.classify(garmentCut)
            val region = if (parsed != null) {
                parsing.regionFrom(parsed, person, category)
            } else {
                null
            }
            DiagnosticsHook.stage(
                diag,
                "human_parse",
                t0,
                "${category.name}${if (request.garment.category == null) "+auto" else ""}",
            )
            if (region == null) {
                DiagnosticsHook.completeTryOn(diag, false, "No person detected")
                emit(
                    GenerationState.Failed(
                        TryOnError.Internal("No person detected — try a clearer full-body photo"),
                    ),
                )
                return@flow
            }

            emit(GenerationState.Running(0.7f, "Fitting the garment"))
            t0 = EpochClock.System.nowMs()
            val fitted = ContourWarp.fit(garmentCut, region)
            DiagnosticsHook.stage(diag, "contour_warp", t0)

            emit(GenerationState.Running(0.82f, "Harmonizing light"))
            t0 = EpochClock.System.nowMs()
            val composed = Harmonizer.compose(person, fitted, region)
            DiagnosticsHook.stage(diag, "harmonize", t0)

            // Studio backdrop: segment the model out and re-stage on the chosen scene.
            val staged = if (request.backdrop == com.zakir.vestra.shared.domain.Backdrop.ORIGINAL) {
                composed
            } else {
                emit(GenerationState.Running(0.9f, "Setting the backdrop"))
                t0 = EpochClock.System.nowMs()
                runCatching {
                    BackdropCompositor("$packDir/garment_seg.onnx").apply(composed, request.backdrop)
                }.getOrElse { composed }.also {
                    DiagnosticsHook.stage(diag, "backdrop", t0, request.backdrop.name)
                }
            }

            emit(GenerationState.Running(0.95f, "Developing"))
            t0 = EpochClock.System.nowMs()
            val finalImage = QualityEnhancer.upscaleIfInstalled(quality, staged)
            val outPath = io.saveResult(Watermark.apply(finalImage))
            DiagnosticsHook.stage(diag, "finalize", t0)

            DiagnosticsHook.completeTryOn(diag, success = true, note = "${EpochClock.System.nowMs() - startedAt}ms total")
            emit(
                GenerationState.Complete(
                    TryOnResult(
                        imagePath = outPath,
                        executedTier = EngineTier.LITE,
                        durationMillis = EpochClock.System.nowMs() - startedAt,
                        watermarked = true,
                    ),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DiagnosticsHook.completeTryOn(diag, false, error.message)
            emit(GenerationState.Failed(TryOnError.Internal(softOrtMessage(error) ?: error.message ?: "Generation failed")))
        } catch (error: Throwable) {
            // Catch LinkageError / UnsatisfiedLinkError that bypass Exception.
            DiagnosticsHook.completeTryOn(diag, false, error.message)
            emit(
                GenerationState.Failed(
                    TryOnError.Internal(
                        softOrtMessage(error)
                            ?: "On-device try-on crashed in native code — try again, or re-download lite-v1.",
                    ),
                ),
            )
        } finally {
            OrtSessionCache.leaveInference()
            packs.markPackIdle(PACK_ID)
        }
    }.flowOn(Dispatchers.Default)

    private fun softOrtMessage(error: Throwable): String? {
        val msg = error.message.orEmpty()
        return when {
            error is UnsatisfiedLinkError || error.cause is UnsatisfiedLinkError ->
                "ONNX Runtime failed to load — reinstall the app or re-download lite-v1."
            msg.contains("NoSuchMethodError", ignoreCase = true) ||
                msg.contains("NodeInfo", ignoreCase = true) ->
                "ONNX Runtime mismatch — reinstall this build (R8 keep rules). Re-download lite-v1 if it persists."
            msg.contains("ONNX", ignoreCase = true) || msg.contains("Ort", ignoreCase = true) ->
                msg.take(160)
            else -> null
        }
    }

    private fun extractGarment(model: OrtModel, garment: Bitmap): Bitmap {
        val (h, w) = model.inputSize(defaultSize = 320)
        val (output, shape) = model.run(ImageOps.toNormalizedChw(garment, h, w), h, w)
        val maskH = shape.getOrNull(2)?.toInt() ?: h
        val maskW = shape.getOrNull(3)?.toInt() ?: w
        // First output plane of u2net-family models is the fused saliency map.
        val plane = output.copyOfRange(0, maskH * maskW)
        val mask = ImageOps.normalizeAndResizeMask(plane, maskW, maskH, garment.width, garment.height)
        val cut = ImageOps.applyAlphaMask(garment, mask)
        return refineGarmentMatte(cropToAlpha(cut))
    }

    private fun refineGarmentMatte(cut: Bitmap): Bitmap {
        val (rgba, width, height) = ImageOps.toRgba(cut)
        val refined = quality.refineMatteIfAvailable(rgba, width, height) ?: return cut
        return cropToAlpha(ImageOps.fromRgba(refined, width, height))
    }

    private fun cropToAlpha(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val opaque = BooleanArray(width * height) { (pixels[it] ushr 24) > 40 }
        val box = ImageOps.boundingBox(opaque, width, height) ?: return bitmap
        return Bitmap.createBitmap(bitmap, box[0], box[1], box[2] - box[0] + 1, box[3] - box[1] + 1)
    }

    companion object {
        const val PACK_ID = "lite-v1"
    }
}
