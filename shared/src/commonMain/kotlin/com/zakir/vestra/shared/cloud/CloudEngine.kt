package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.diagnostics.DiagnosticsHook
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.TryOnError
import com.zakir.vestra.shared.domain.TryOnRequest
import com.zakir.vestra.shared.domain.TryOnResult
import com.zakir.vestra.shared.domain.effectiveCategory
import com.zakir.vestra.shared.engine.Availability
import com.zakir.vestra.shared.engine.TryOnEngine
import com.zakir.vestra.shared.engine.UnavailableReason
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.zakir.vestra.shared.time.EpochClock

/**
 * Free-tier cloud try-on via Hugging Face Spaces only.
 * Never auto-routed — user must explicitly select Cloud in Settings.
 */
class CloudEngine(
    private val http: HttpClient,
    private val io: CloudImageIo,
    private val settings: AppSettings,
    private val usage: UsageLedger? = null,
) : TryOnEngine {

    private val hf = HfGradioClient(http)

    override val tier: EngineTier = EngineTier.CLOUD

    override fun isAvailable(): Availability {
        val provider = settings.selectedCloudProvider()
        return when {
            !settings.networkLikelyAvailable() ->
                Availability.Unavailable(UnavailableReason.OFFLINE)
            provider.requiresApiKey && settings.apiKeyFor(provider).isNullOrBlank() ->
                Availability.Unavailable(UnavailableReason.NOT_CONFIGURED)
            CloudModelContracts.preflightOrNull(provider) != null ->
                Availability.Unavailable(UnavailableReason.NOT_CONFIGURED)
            else -> Availability.Ready
        }
    }

    override fun generate(request: TryOnRequest): Flow<GenerationState> = flow {
        val provider = settings.selectedCloudProvider()
        val startedAt = EpochClock.System.nowMs()
        val diag = DiagnosticsHook.startTryOn(EngineTier.CLOUD, modelLabel = provider.displayName)

        CloudModelContracts.preflightOrNull(provider)?.let { blocked ->
            usage?.record(
                provider,
                success = false,
                note = CloudModelContracts.usageFailureNote(provider, blocked),
            )
            emit(GenerationState.Failed(TryOnError.Internal(blocked)))
            DiagnosticsHook.completeTryOn(diag, false, blocked)
            return@flow
        }

        emit(GenerationState.Preparing("Connecting to ${provider.displayName}"))
        var t0 = EpochClock.System.nowMs()
        val personBytes = io.loadImageBytes(request.person)
        val garmentBytes = io.loadImageBytes(request.garment.uri)
        DiagnosticsHook.stage(diag, "load_images", t0)
        if (personBytes == null || garmentBytes == null) {
            DiagnosticsHook.completeTryOn(diag, false, "Couldn't read images")
            emit(GenerationState.Failed(TryOnError.Internal("Couldn't read the selected images")))
            return@flow
        }

        val personDataUrl = io.toDataUrl(personBytes)
        val garmentDataUrl = io.toDataUrl(garmentBytes)
        val category = request.garment.category?.effectiveCategory() ?: GarmentCategory.ABAYA

        var attempted = provider
        try {
            emit(GenerationState.Running(0.2f, "Uploading to ${provider.displayName}…"))
            require(provider.platform == CloudPlatform.HF_SPACE) {
                "Only free Hugging Face Spaces are supported for try-on"
            }
            val candidates = CloudModelRouting.fallbackChain(provider, AiCapability.TRY_ON)
            var lastError: Exception? = null
            for ((modelIndex, candidate) in candidates.withIndex()) {
                attempted = candidate
                if (modelIndex > 0) {
                    emit(
                        GenerationState.Running(
                            0.35f,
                            "${provider.displayName} is busy — trying ${candidate.displayName}…",
                        ),
                    )
                }
                try {
                    t0 = EpochClock.System.nowMs()
                    val resultUrlOrPath = runHfSpace(candidate, personDataUrl, garmentDataUrl, category)
                    DiagnosticsHook.stage(diag, "space_predict", t0, candidate.displayName)
                    emit(GenerationState.Running(0.85f, "Downloading result…"))
                    t0 = EpochClock.System.nowMs()
                    val outPath = io.downloadResult(resultUrlOrPath, spaceHost = candidate.endpoint)
                    DiagnosticsHook.stage(diag, "download", t0)
                    usage?.record(
                        candidate,
                        success = true,
                        note = "Try-on · ${candidate.displayName} · ${CloudModelContracts.statusLabel(candidate)}",
                    )
                    DiagnosticsHook.completeTryOn(diag, success = true, note = candidate.displayName)
                    emit(
                        GenerationState.Complete(
                            TryOnResult(
                                imagePath = outPath,
                                executedTier = EngineTier.CLOUD,
                                durationMillis = EpochClock.System.nowMs() - startedAt,
                                watermarked = false,
                            ),
                        ),
                    )
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (e.isAccountQuotaExhausted()) {
                        // ZeroGPU is account-wide — try remaining Spaces, then fail with guidance.
                        continue
                    }
                }
            }
            throw lastError ?: IllegalStateException("Try-on failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = CloudModelContracts.friendlyFailure(attempted, e.message.orEmpty(), "Try-on")
            usage?.record(
                attempted,
                success = false,
                note = CloudModelContracts.usageFailureNote(attempted, e.message.orEmpty()),
            )
            val error = if (
                friendly.contains("No internet", ignoreCase = true) ||
                e.message.orEmpty().contains("Unable to resolve host", ignoreCase = true) ||
                e.message.orEmpty().contains("timeout", ignoreCase = true)
            ) {
                TryOnError.NetworkUnavailable
            } else {
                TryOnError.Internal(friendly)
            }
            DiagnosticsHook.completeTryOn(diag, false, friendly)
            emit(GenerationState.Failed(error))
        }
    }

    private suspend fun runHfSpace(
        provider: CloudModelProvider,
        person: String,
        garment: String,
        category: GarmentCategory,
    ): String {
        val result = hf.predict(
            spaceHost = provider.endpoint,
            apiName = CloudModelContracts.effectiveApiName(provider),
            data = SpacePayloads.forTryOn(provider.id, person, garment, category),
            hfToken = settings.hfToken.value,
        )
        return GradioOutput.extractMediaRef(result)
    }
}

private fun Exception.isAccountQuotaExhausted(): Boolean {
    val msg = message.orEmpty()
    return msg.contains("quota exceeded", ignoreCase = true) ||
        msg.contains("ZeroGPU quota", ignoreCase = true) ||
        msg.contains("exceeded your free ZeroGPU", ignoreCase = true) ||
        msg.contains("0s left", ignoreCase = true)
}

/** Platform seam for loading/saving images for cloud engines. */
interface CloudImageIo {
    suspend fun loadImageBytes(person: com.zakir.vestra.shared.domain.PersonSource): ByteArray?
    suspend fun loadImageBytes(uri: String): ByteArray?
    fun toDataUrl(jpegBytes: ByteArray): String
    suspend fun downloadResult(urlOrPath: String, spaceHost: String? = null): String
    /** Resolve content/file URI to a local filesystem path for on-device vision assist. */
    fun resolveLocalPath(uri: String): String? = null
}
