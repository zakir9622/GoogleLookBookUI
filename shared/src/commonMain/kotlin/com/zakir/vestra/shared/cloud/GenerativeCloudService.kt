package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.safety.InputSafetyGate
import com.zakir.vestra.shared.safety.SafetyVerdict
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.engine.local.LiteRtLmPacks
import com.zakir.vestra.shared.engine.local.LocalCodeGenerator
import com.zakir.vestra.shared.engine.local.LocalCodeResult
import com.zakir.vestra.shared.engine.local.LocalCodeStreamEvent
import com.zakir.vestra.shared.engine.local.LocalImageGenerator
import com.zakir.vestra.shared.engine.local.LocalImageResult
import com.zakir.vestra.shared.engine.local.LocalImageStreamEvent
import com.zakir.vestra.shared.engine.local.LocalVideoGenerator
import com.zakir.vestra.shared.engine.local.LocalVideoResult
import com.zakir.vestra.shared.engine.local.LocalVisionAssist
import com.zakir.vestra.shared.engine.local.LocalAssistResult
import com.zakir.vestra.shared.engine.local.LocalAudioTranscriber
import com.zakir.vestra.shared.engine.local.LocalTranscribeResult
import com.zakir.vestra.shared.engine.local.UnimplementedLocalCodeGenerator
import com.zakir.vestra.shared.engine.local.UnimplementedLocalVisionAssist
import com.zakir.vestra.shared.engine.local.UnimplementedLocalAudioTranscriber
import com.zakir.vestra.shared.engine.local.UnimplementedLocalImageGenerator
import com.zakir.vestra.shared.engine.local.UnimplementedLocalVideoGenerator
import com.zakir.vestra.shared.audio.VoiceCatalog
import com.zakir.vestra.shared.audio.VoiceKnobs
import com.zakir.vestra.shared.audio.VoicePersona
import com.zakir.vestra.shared.engine.local.LocalAudioGenerator
import com.zakir.vestra.shared.engine.local.LocalAudioResult
import com.zakir.vestra.shared.engine.local.LocalVoiceChanger
import com.zakir.vestra.shared.engine.local.UnimplementedLocalAudioGenerator
import com.zakir.vestra.shared.engine.local.UnimplementedLocalVoiceChanger
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.time.EpochClock
import io.ktor.client.HttpClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

sealed interface GenerativeState {
    data class Preparing(val message: String) : GenerativeState
    /**
     * @param stage Human-readable activity (no baked-in countdown — UI ticks [deadlineEpochMs]).
     * @param deadlineEpochMs Wall-clock deadline for remaining-seconds display; null = no timer.
     */
    data class Running(
        val fraction: Float,
        val stage: String,
        val deadlineEpochMs: Long? = null,
    ) : GenerativeState
    data class ImageReady(val path: String, val providerId: String) : GenerativeState
    data class VideoReady(val path: String, val providerId: String) : GenerativeState
    data class AudioReady(val path: String, val providerId: String) : GenerativeState
    data class CodeReady(val text: String, val tokensIn: Int, val tokensOut: Int, val providerId: String) : GenerativeState
    /** Growing text as a local model streams its response — [text] is cumulative, not a delta. */
    data class CodeStreaming(val text: String, val providerId: String) : GenerativeState
    /** Offline transcription result (Audio Scribe). */
    data class TranscribeReady(val text: String, val providerId: String) : GenerativeState
    data class Failed(val message: String) : GenerativeState
}

/**
 * Free-tier generative service: HF Spaces + HF Inference Providers for image/video,
 * Groq/HF/OpenRouter for code. Optional local generators are tried first when ready
 * (Create / Edit / Audio / Code / Video still-clip).
 */
class GenerativeCloudService(
    private val http: HttpClient,
    private val io: CloudImageIo,
    private val settings: AppSettings,
    private val usage: UsageLedger,
    private val health: ModelHealthTracker = settings.modelHealth,
    private val localImage: LocalImageGenerator = UnimplementedLocalImageGenerator,
    private val localAudio: LocalAudioGenerator = UnimplementedLocalAudioGenerator,
    private val localVoiceChanger: LocalVoiceChanger = UnimplementedLocalVoiceChanger,
    private val localCode: LocalCodeGenerator = UnimplementedLocalCodeGenerator,
    private val localVideo: LocalVideoGenerator = UnimplementedLocalVideoGenerator,
    private val localVision: LocalVisionAssist = UnimplementedLocalVisionAssist,
    private val localTranscriber: LocalAudioTranscriber = UnimplementedLocalAudioTranscriber,
) {
    fun localImageReady(): Boolean = localImage.isReady()

    fun localImageEditReady(): Boolean = localImage.isEditReady()

    fun localAudioReady(): Boolean = localAudio.isReady()

    fun localCodeReady(): Boolean = localCode.isReady()

    fun localCodeProviderId(): String = localCode.providerId()

    /** Loads the selected on-device code/chat model; returns the failure reason, or null. */
    fun warmUpLocalCode(): String? = localCode.warmUp()

    /** Loads the on-device image pack (also backs Edit and the still-clip video path). */
    fun warmUpLocalImage(): String? = localImage.warmUp()

    /** Warms whichever on-device engine backs [capability]; null when it loaded cleanly. */
    fun warmUpLocal(capability: AiCapability): String? = when (capability) {
        AiCapability.CODE -> localCode.warmUp()
        AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT, AiCapability.VIDEO -> localImage.warmUp()
        else -> null
    }

    fun localVideoReady(): Boolean = localVideo.isReady()

    fun localVisionReady(): Boolean = localVision.isReady()

    fun localTranscribeReady(): Boolean = localTranscriber.isReady()

    private val hf = HfGradioClient(http)
    private val hfInference = HfInferenceClient(http)
    private val llm = LlmClient(http)
    private val schema = GradioSchemaClient(http)

    @OptIn(ExperimentalEncodingApi::class)
    fun generateImage(
        prompt: String,
        referenceUri: String?,
        assists: GenerativeAssists = GenerativeAssists(),
    ): Flow<GenerativeState> = flow {
        val capability = if (referenceUri.isNullOrBlank()) AiCapability.IMAGE_GEN else AiCapability.IMAGE_EDIT
        val provider = settings.selectedProvider(capability)
        var attempted = provider
        try {
            when (val safety = InputSafetyGate.checkPrompt(prompt)) {
                is SafetyVerdict.Blocked -> error(safety.reason)
                is SafetyVerdict.Ok -> Unit
            }
            // Prefer local when offline (or user prefers local). When online, honor the
            // selected cloud model first; local remains a fallback after cloud failures.
            val networkOk = settings.networkLikelyAvailable()
            val localReady = when {
                referenceUri.isNullOrBlank() -> localImage.isReady()
                else -> localImage.isEditReady()
            }
            val tryLocalFirst = localReady && (!networkOk || settings.prefersLocal(capability))
            // Only announce the cloud provider once it's actually the one about to run — the
            // old unconditional emit here said "Connecting to FLUX.1 Schnell" (or whichever
            // cloud model was selected) even when local was about to run instead, confirmed
            // live in a user's diagnostics export (the message appeared verbatim right before
            // "Generating on-device…" actually started).
            if (!tryLocalFirst) {
                emit(GenerativeState.Preparing("Connecting to ${provider.displayName}"))
            }
            // Optional vision assist — describe reference before image gen (L2).
            var enrichedPrompt = prompt.trim()
            if (assists.analyzeReference && !referenceUri.isNullOrBlank() && localVision.isReady()) {
                emit(GenerativeState.Running(0.04f, "Analyzing reference photo…"))
                val imagePath = io.resolveLocalPath(referenceUri)
                if (imagePath == null) {
                    emit(
                        GenerativeState.Running(
                            0.05f,
                            "Vision assist skipped — could not read reference photo.",
                        ),
                    )
                } else {
                    when (val assist = localVision.describeImage(
                        imagePath,
                        "Describe this garment or fashion reference for a text-to-image prompt. " +
                            "Include fabric, color, silhouette, and styling details in 2–4 sentences.",
                    )) {
                        is LocalAssistResult.Ok -> {
                            enrichedPrompt = buildString {
                                append(enrichedPrompt)
                                if (enrichedPrompt.isNotBlank()) append("\n\n")
                                append("Reference analysis: ")
                                append(assist.text)
                            }
                        }
                        is LocalAssistResult.Unavailable -> {
                            emit(
                                GenerativeState.Running(
                                    0.05f,
                                    "Vision assist skipped — ${assist.reason.take(80)}",
                                ),
                            )
                        }
                    }
                }
            }
            var localBlockedReason: String? = null
            if (tryLocalFirst) {
                // Reflects whichever engine actually ran — was hardcoded to local-sdturbo-v1,
                // which mislabeled every image Bonsai produced.
                val providerId = when {
                    !referenceUri.isNullOrBlank() -> "local-sdturbo-edit"
                    settings.selectionId(AiCapability.IMAGE_GEN) == "local-bonsai-image-v1" -> "local-bonsai-image-v1"
                    else -> "local-sdturbo-v1"
                }
                val stage = if (referenceUri.isNullOrBlank()) {
                    "Generating on-device…"
                } else {
                    "Editing on-device…"
                }
                emit(GenerativeState.Running(0.08f, stage))
                var localFailure: String? = null
                localImage.generateStream(
                    enrichedPrompt,
                    assists.seed,
                    referenceImageUri = referenceUri,
                ).collect { event ->
                    when (event) {
                        is LocalImageStreamEvent.Progress -> emit(GenerativeState.Running(event.fraction, event.stage))
                        is LocalImageStreamEvent.Done -> emit(GenerativeState.ImageReady(event.imagePath, providerId))
                        is LocalImageStreamEvent.Unavailable -> localFailure = event.reason
                    }
                }
                // Surface the engine's real reason. It used to be swallowed behind a generic
                // "Local pack unavailable", which made an on-device failure impossible to
                // diagnose from the run log or a diagnostics export.
                val reason = localFailure ?: return@flow
                localBlockedReason = reason
                if (!networkOk) {
                    emit(
                        GenerativeState.Failed(
                            "You're offline and on-device image generation failed: $reason",
                        ),
                    )
                    return@flow
                }
                if (!settings.cloudGenerationAllowed()) {
                    emit(
                        GenerativeState.Failed(
                            "On-device image generation failed: $reason\n\n" +
                                "Cloud models are off, so nothing was sent to the network. " +
                                "Fix the pack in Settings → Model packs, or enable cloud models.",
                        ),
                    )
                    return@flow
                }
                emit(
                    GenerativeState.Running(
                        0.1f,
                        "On-device unavailable (${reason.take(80)}) — trying cloud…",
                    ),
                )
            }
            // Hard stop when offline with no local pack — do not burn time on cloud.
            if (!networkOk) {
                emit(
                    GenerativeState.Failed(
                        "You're offline. Install the on-device image pack in Settings " +
                            "(Packs → Image) to generate without a network.",
                    ),
                )
                return@flow
            }
            if (!settings.cloudGenerationAllowed()) {
                emit(GenerativeState.Failed(settings.cloudDisabledReason(capability)))
                return@flow
            }
            val candidates = CloudModelRouting.fallbackChain(provider, capability, settings, health)
            val referenceDataUrl = referenceUri?.takeIf { it.isNotBlank() }?.let {
                val bytes = io.loadImageBytes(it) ?: error("Couldn't read the reference image")
                io.toDataUrl(bytes)
            }
            val variants = visualPromptVariants(prompt, assists)
            var lastFailure: CloudFailure = CloudFailure.Unknown("Image generation failed")
            var skipInference = false
            var skipSpaces = false
            var offline = false
            var deadline = GenerationBudget.forImage().deadlineMs
            var fallbackGraceUsed = false
            emit(
                GenerativeState.Running(
                    0.05f,
                    "Queued · ${provider.displayName}",
                    deadlineEpochMs = deadline,
                ),
            )

            for ((modelIndex, candidate) in candidates.withIndex()) {
                var budget = GenerationBudget(deadline)
                if (budget.expired()) {
                    // Primary ZeroGPU Spaces often burn the whole 120s on one hung poll; grant one
                    // short grace window so InstructPix2Pix (etc.) can still run.
                    val canGrace = !fallbackGraceUsed &&
                        modelIndex > 0 &&
                        lastFailure.allowsImageFallbackGrace()
                    if (!canGrace) break
                    fallbackGraceUsed = true
                    deadline = com.zakir.vestra.shared.time.EpochClock.System.nowMs() +
                        GenerationBudget.IMAGE_FALLBACK_GRACE_MS
                    budget = GenerationBudget(deadline)
                }
                if (offline) break
                attempted = candidate
                if (skipInference && candidate.platform == CloudPlatform.HF_INFERENCE) continue
                if (skipSpaces && candidate.platform == CloudPlatform.HF_SPACE) continue
                if (CloudModelContracts.preflightOrNull(candidate) != null) continue
                if (candidate.requiresApiKey && settings.apiKeyFor(candidate).isNullOrBlank()) continue
                if (modelIndex > 0) {
                    emit(
                        GenerativeState.Running(
                            0.3f,
                            when {
                                skipSpaces -> "ZeroGPU empty — trying ${candidate.displayName}…"
                                lastFailure is CloudFailure.HostUnavailable ->
                                    "${provider.displayName} looks offline — trying ${candidate.displayName}…"
                                lastFailure is CloudFailure.CreditsExhausted ->
                                    "Inference credits empty — trying ${candidate.displayName}…"
                                fallbackGraceUsed ->
                                    "${provider.displayName} timed out — trying ${candidate.displayName}…"
                                else -> "${provider.displayName} is busy — trying ${candidate.displayName}…"
                            },
                            deadlineEpochMs = deadline,
                        ),
                    )
                }

                var advanceModel = false
                for ((index, variant) in variants.withIndex()) {
                    if (advanceModel) break
                    budget = GenerationBudget(deadline)
                    if (budget.expired()) break
                    emit(
                        GenerativeState.Running(
                            0.2f + index * 0.15f,
                            if (index == 0) "Submitting to ${candidate.displayName}…"
                            else "Retrying with softer prompt…",
                            deadlineEpochMs = deadline,
                        ),
                    )
                    try {
                        val path = when (candidate.platform) {
                            CloudPlatform.HF_INFERENCE -> {
                                val token = settings.hfToken.value
                                    ?: throw CloudFailureException(CloudFailure.AuthRejected)
                                emit(
                                    GenerativeState.Running(
                                        0.5f,
                                        "HF Inference · ${candidate.displayName}",
                                        deadlineEpochMs = deadline,
                                    ),
                                )
                                val bytes = if (referenceDataUrl != null) {
                                    val refBytes = io.loadImageBytes(referenceUri!!)
                                        ?: error("Couldn't read the reference image")
                                    hfInference.imageToImage(
                                        modelId = candidate.endpoint,
                                        prompt = variant,
                                        imageBytes = refBytes,
                                        hfToken = token,
                                    )
                                } else {
                                    hfInference.textToImage(
                                        modelId = candidate.endpoint,
                                        prompt = variant,
                                        hfToken = token,
                                    )
                                }
                                CloudOutputValidator.rejectReason(
                                    bytes,
                                    checkContent = assists.qualityGuard,
                                )?.let {
                                    throw CloudFailureException(CloudFailure.BadOutput)
                                }
                                io.downloadResult(
                                    "data:image/png;base64,${Base64.encode(bytes)}",
                                )
                            }
                            CloudPlatform.HF_SPACE -> {
                                val data = resolveImageSpacePayload(
                                    candidate,
                                    variant,
                                    referenceDataUrl,
                                )
                                emit(
                                    GenerativeState.Running(
                                        0.35f,
                                        "Space queue · ${candidate.displayName}",
                                        deadlineEpochMs = deadline,
                                    ),
                                )
                                val wakeRetries = if (budget.allowWakeRetry()) 1 else 0
                                val result = hf.predict(
                                    candidate.endpoint,
                                    CloudModelContracts.effectiveApiName(candidate),
                                    data,
                                    settings.hfToken.value,
                                    maxPolls = budget.maxPolls(),
                                    wakeRetries = wakeRetries,
                                    deadlineMs = deadline,
                                    pollRequestTimeoutMs = GenerationBudget.GRADIO_POLL_REQUEST_TIMEOUT_MS,
                                    onPoll = { pollIndex, maxPolls ->
                                        val frac =
                                            0.35f + 0.5f * (pollIndex + 1).toFloat() / maxPolls.coerceAtLeast(1)
                                        emit(
                                            GenerativeState.Running(
                                                frac.coerceIn(0.35f, 0.9f),
                                                "Space poll ${pollIndex + 1}/$maxPolls · ${candidate.displayName}",
                                                deadlineEpochMs = deadline,
                                            ),
                                        )
                                    },
                                )
                                val url = GradioOutput.extractMediaRef(result)
                                emit(
                                    GenerativeState.Running(
                                        0.92f,
                                        "Downloading image…",
                                        deadlineEpochMs = deadline,
                                    ),
                                )
                                io.downloadResult(url, spaceHost = candidate.endpoint)
                            }
                            else -> throw CloudFailureException(
                                CloudFailure.Unknown("Unsupported platform for images: ${candidate.platform}"),
                            )
                        }
                        usage.record(
                            candidate,
                            success = true,
                            note = "Image · ${CloudModelContracts.statusLabel(candidate)} · ${prompt.take(80)}",
                        )
                        health.recordSuccess(candidate.id)
                        emit(GenerativeState.ImageReady(path, candidate.id))
                        return@flow
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val failure = CloudFailureClassifier.from(e)
                        lastFailure = failure
                        val kind = when (failure) {
                            is CloudFailure.QuotaExhausted ->
                                if (failure.scope == CloudFailure.QuotaExhausted.Scope.ACCOUNT) {
                                    ModelHealthTracker.FailureKind.QUOTA_ACCOUNT
                                } else {
                                    ModelHealthTracker.FailureKind.GENERIC
                                }
                            CloudFailure.CreditsExhausted -> ModelHealthTracker.FailureKind.CREDITS
                            CloudFailure.Offline -> ModelHealthTracker.FailureKind.OFFLINE
                            CloudFailure.HostUnavailable -> ModelHealthTracker.FailureKind.GENERIC
                            else -> ModelHealthTracker.FailureKind.GENERIC
                        }
                        health.recordFailure(candidate.id, kind)
                        when {
                            failure is CloudFailure.Offline -> {
                                offline = true
                                break
                            }
                            failure is CloudFailure.QuotaExhausted &&
                                failure.scope == CloudFailure.QuotaExhausted.Scope.ACCOUNT -> {
                                skipSpaces = true
                                advanceModel = true
                            }
                            failure is CloudFailure.CreditsExhausted -> {
                                skipInference = true
                                // Credits are account-wide — cool down sibling Inference models too.
                                CloudModelCatalog.forCapability(capability)
                                    .filter { it.platform == CloudPlatform.HF_INFERENCE && it.id != candidate.id }
                                    .forEach {
                                        health.recordFailure(it.id, ModelHealthTracker.FailureKind.CREDITS)
                                    }
                                advanceModel = true
                            }
                            failure.advanceModel -> advanceModel = true
                            failure.retryVariants && index < variants.lastIndex -> Unit
                            else -> advanceModel = true
                        }
                    }
                }
            }
            throw CloudFailureException(lastFailure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = CloudFailureClassifier.from(e)
            val rawForFriendly = when (failure) {
                CloudFailure.SchemaRejected -> "event: error data: null"
                CloudFailure.CreditsExhausted -> "HTTP 402: depleted your monthly Inference Providers credits"
                CloudFailure.HostUnavailable -> "HTTP 404 Not Found"
                CloudFailure.Offline -> "No internet connection"
                is CloudFailure.QuotaExhausted -> "ZeroGPU quota exceeded"
                CloudFailure.Timeout -> "Request timed out"
                is CloudFailure.Unknown -> failure.raw
                else -> failure.toUserHint()
            }
            usage.record(
                attempted,
                success = false,
                note = CloudModelContracts.usageFailureNote(attempted, rawForFriendly),
            )
            emit(
                GenerativeState.Failed(
                    CloudModelContracts.friendlyFailure(
                        attempted,
                        rawForFriendly,
                        "Image generation",
                        selectedDisplayName = provider.displayName,
                    ),
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun resolveImageSpacePayload(
        candidate: CloudModelProvider,
        variant: String,
        referenceDataUrl: String?,
    ): List<kotlinx.serialization.json.JsonElement> {
        val handTuned = runCatching {
            if (referenceDataUrl != null) {
                if (!SpacePayloads.hasImageEdit(candidate.id)) null
                else SpacePayloads.forImageEdit(candidate.id, variant, referenceDataUrl)
            } else {
                if (!SpacePayloads.hasImageGen(candidate.id)) null
                else SpacePayloads.forImageGen(candidate.id, variant)
            }
        }.getOrNull()
        if (handTuned != null) return handTuned
        val roles = GradioSchemaClient.promptRoles(
            prompt = variant,
            image = referenceDataUrl?.let { SpacePayloads.fileData(it) },
        )
        val schemaPayload = schema.buildPayload(
            spaceHost = candidate.endpoint,
            apiName = CloudModelContracts.effectiveApiName(candidate),
            roles = roles,
        )
        if (schemaPayload != null) return schemaPayload
        throw CloudFailureException(CloudFailure.SchemaRejected)
    }

    private fun Exception.isMonthlyCreditsExhausted(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("402", ignoreCase = true) ||
            msg.contains("depleted your monthly", ignoreCase = true) ||
            msg.contains("Inference Providers monthly credits", ignoreCase = true) ||
            msg.contains("monthly credits are used up", ignoreCase = true)
    }

    private fun Exception.isAccountQuotaExhausted(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("quota exceeded", ignoreCase = true) ||
            msg.contains("ZeroGPU quota", ignoreCase = true) ||
            msg.contains("exceeded your free ZeroGPU", ignoreCase = true) ||
            msg.contains("0s left", ignoreCase = true)
    }

    private fun Exception.isNonRetryableInferenceError(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("depleted your monthly", ignoreCase = true) ||
            msg.contains("Inference Providers monthly credits", ignoreCase = true) ||
            msg.contains("token rejected for Inference", ignoreCase = true)
    }

    private fun Exception.isBrokenInferenceRoute(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("Model not supported by provider", ignoreCase = true)
    }

    private fun Exception.isNetworkError(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("UnknownHostException", ignoreCase = true) ||
            msg.contains("Network is unreachable", ignoreCase = true) ||
            msg.contains("failed to connect", ignoreCase = true)
    }

    fun generateCode(
        prompt: String,
        assists: GenerativeAssists = GenerativeAssists(),
    ): Flow<GenerativeState> = flow {
        val provider = settings.selectedProvider(AiCapability.CODE)
        var attempted = provider
        try {
            when (val safety = InputSafetyGate.checkPrompt(prompt)) {
                is SafetyVerdict.Blocked -> error(safety.reason)
                is SafetyVerdict.Ok -> Unit
            }
            val tryLocalFirst = localCode.isReady() &&
                (!settings.networkLikelyAvailable() || settings.prefersLocal(AiCapability.CODE))
            // Same fix as generateImage: only announce the cloud provider once it's actually
            // the one about to run.
            if (!tryLocalFirst) {
                emit(GenerativeState.Preparing("Connecting to ${provider.displayName}"))
            }
            if (tryLocalFirst) {
                val localLabel = LocalModelCatalog.byId(localCode.providerId())?.displayName
                    ?: "Local on-device"
                emit(GenerativeState.Running(0.08f, "Loading $localLabel…"))
                var localFailure: String? = null
                localCode.generateStream(prompt.trim(), buildCodeSystem(assists)).collect { event ->
                    when (event) {
                        is LocalCodeStreamEvent.Partial ->
                            emit(GenerativeState.CodeStreaming(event.textSoFar, localCode.providerId()))
                        is LocalCodeStreamEvent.Done ->
                            emit(
                                GenerativeState.CodeReady(
                                    event.text,
                                    event.tokensIn,
                                    event.tokensOut,
                                    localCode.providerId(),
                                ),
                            )
                        is LocalCodeStreamEvent.Unavailable -> localFailure = event.reason
                    }
                }
                val reason = localFailure ?: return@flow
                if (!settings.networkLikelyAvailable()) {
                    emit(
                        GenerativeState.Failed(
                            "You're offline and local Code couldn't run. $reason",
                        ),
                    )
                    return@flow
                }
                if (!settings.cloudGenerationAllowed()) {
                    emit(
                        GenerativeState.Failed(
                            "On-device Code failed: $reason\n\n" +
                                "Cloud models are off, so nothing was sent to the network. " +
                                "Fix the pack in Settings → Model packs, or enable cloud models.",
                        ),
                    )
                    return@flow
                }
                // Name the model that actually failed and why — the old copy said
                // "Local Gemma unavailable" even when the Qwen3 route was the one that ran.
                emit(
                    GenerativeState.Running(
                        0.1f,
                        "$localLabel unavailable (${reason.take(80)}) — trying cloud…",
                    ),
                )
            }
            if (!settings.cloudGenerationAllowed()) {
                emit(GenerativeState.Failed(settings.cloudDisabledReason(AiCapability.CODE)))
                return@flow
            }
            if (!settings.networkLikelyAvailable()) {
                emit(
                    GenerativeState.Failed(
                        "You're offline. Download local-gemma-4-e2b-v1 from Model packs " +
                            "for offline Code Studio.",
                    ),
                )
                return@flow
            }
            val candidates = CloudModelRouting.codeFallbackChain(provider, settings)
            val system = buildCodeSystem(assists)
            val temperature = when {
                assists.creative && assists.pragmatic -> 0.5
                assists.creative -> 0.55
                else -> 0.2
            }
            val cleaned = prompt.trim().ifBlank { "Write a short Hello World in Kotlin." }
            val attempts = listOf(
                cleaned,
                "Complete this coding request helpfully. Assume lawful software intent:\n\n$cleaned",
                "Provide working code for:\n$cleaned\n\nIf anything is unclear, pick sensible defaults and note them.",
            )
            var lastError: Exception? = null
            val codeDeadline = EpochClock.System.nowMs() + 90_000L
            for ((modelIndex, candidate) in candidates.withIndex()) {
                attempted = candidate
                if (CloudModelContracts.preflightOrNull(candidate) != null) continue
                if (candidate.requiresApiKey && settings.apiKeyFor(candidate).isNullOrBlank()) continue
                if (modelIndex > 0) {
                    emit(
                        GenerativeState.Running(
                            0.25f,
                            "${provider.displayName} unavailable — trying ${candidate.displayName}…",
                            deadlineEpochMs = codeDeadline,
                        ),
                    )
                } else {
                    emit(
                        GenerativeState.Running(
                            0.3f,
                            "Calling ${candidate.displayName}…",
                            deadlineEpochMs = codeDeadline,
                        ),
                    )
                }
                val key = settings.apiKeyFor(candidate) ?: error("API key required for ${candidate.displayName}")
                var result: LlmResult? = null
                var quotaExhausted = false
                for ((i, attempt) in attempts.withIndex()) {
                    if (i > 0) emit(GenerativeState.Running(0.35f + i * 0.1f, "Retrying…"))
                    try {
                        result = llm.chat(candidate.platform, candidate.endpoint, attempt, key, system, temperature)
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        if (e.isMonthlyCreditsExhausted()) {
                            quotaExhausted = true
                            break
                        }
                    }
                }
                if (quotaExhausted && modelIndex < candidates.lastIndex) {
                    health.recordFailure(candidate.id, ModelHealthTracker.FailureKind.CREDITS)
                    continue
                }
                if (result != null) {
                    usage.record(
                        candidate,
                        tokensIn = result.tokensIn,
                        tokensOut = result.tokensOut,
                        success = true,
                        note = "Code · ${CloudModelContracts.statusLabel(candidate)} · ${prompt.take(80)}",
                    )
                    health.recordSuccess(candidate.id)
                    emit(GenerativeState.CodeReady(result.text, result.tokensIn, result.tokensOut, candidate.id))
                    return@flow
                }
                health.recordFailure(candidate.id)
            }
            throw lastError ?: IllegalStateException("Empty LLM response")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            usage.record(
                attempted,
                success = false,
                note = CloudModelContracts.usageFailureNote(attempted, e.message.orEmpty()),
            )
            health.recordFailure(attempted.id)
            emit(GenerativeState.Failed(
                CloudModelContracts.friendlyFailure(
                    attempted,
                    e.message.orEmpty(),
                    "Code generation",
                    selectedDisplayName = provider.displayName,
                ),
            ))
        }
    }.flowOn(Dispatchers.Default)

    fun generateVideo(
        prompt: String,
        assists: GenerativeAssists = GenerativeAssists(),
    ): Flow<GenerativeState> = flow {
        val provider = settings.selectedProvider(AiCapability.VIDEO)
        var attempted = provider
        try {
            when (val safety = InputSafetyGate.checkPrompt(prompt)) {
                is SafetyVerdict.Blocked -> error(safety.reason)
                is SafetyVerdict.Ok -> Unit
            }
            val tryLocalFirst = localVideo.isReady() &&
                (!settings.networkLikelyAvailable() || settings.prefersLocal(AiCapability.VIDEO))
            // Same fix as generateImage: only announce the cloud provider once it's actually
            // the one about to run.
            if (!tryLocalFirst) {
                emit(GenerativeState.Preparing("Connecting to ${provider.displayName}"))
            }
            val networkOk = settings.networkLikelyAvailable()
            if (tryLocalFirst) {
                emit(GenerativeState.Running(0.08f, "Encoding local still-clip…"))
                when (val local = localVideo.generate(prompt.trim(), assists.seed)) {
                    is LocalVideoResult.Ok -> {
                        emit(GenerativeState.VideoReady(local.videoPath, "local-stillclip-v1"))
                        return@flow
                    }
                    is LocalVideoResult.Unavailable -> {
                        // Same hard-stop as image/code/audio: an offline user with a broken
                        // local pack must not silently burn time on a cloud attempt that has
                        // no network to reach — was previously the one capability that kept
                        // going with a soft "trying cloud anyway…" notice instead.
                        if (!networkOk) {
                            emit(
                                GenerativeState.Failed(
                                    "You're offline and the on-device still-clip couldn't run. " +
                                        local.reason,
                                ),
                            )
                            return@flow
                        }
                        emit(
                            GenerativeState.Running(
                                0.1f,
                                "Local still-clip unavailable — trying cloud…",
                            ),
                        )
                    }
                }
            }
            if (!settings.cloudGenerationAllowed()) {
                emit(GenerativeState.Failed(settings.cloudDisabledReason(AiCapability.VIDEO)))
                return@flow
            }
            // Hard stop when offline with no local pack — do not burn time on cloud, matching
            // generateImage/generateCode/generateAudio's identical guard.
            if (!networkOk) {
                emit(
                    GenerativeState.Failed(
                        "You're offline. Install local-sdturbo-v1 from Model packs " +
                            "for offline video still-clips.",
                    ),
                )
                return@flow
            }
            val candidates = CloudModelRouting.fallbackChain(provider, AiCapability.VIDEO, settings, health)
            val variants = visualPromptVariants(prompt, assists)
            val budget = GenerationBudget.forVideo()
            val deadline = budget.deadlineMs
            var lastError: Exception? = null
            emit(
                GenerativeState.Running(
                    0.05f,
                    "Queued · ${provider.displayName}",
                    deadlineEpochMs = deadline,
                ),
            )
            for ((modelIndex, candidate) in candidates.withIndex()) {
                budget.throwIfExpired()
                attempted = candidate
                if (CloudModelContracts.preflightOrNull(candidate) != null) continue
                if (candidate.requiresApiKey && settings.apiKeyFor(candidate).isNullOrBlank()) continue
                if (modelIndex > 0) {
                    emit(
                        GenerativeState.Running(
                            0.2f,
                            "${provider.displayName} is busy — trying ${candidate.displayName}…",
                            deadlineEpochMs = deadline,
                        ),
                    )
                }
                for ((index, variant) in variants.withIndex()) {
                    budget.throwIfExpired()
                    emit(
                        GenerativeState.Running(
                            0.15f + index * 0.15f,
                            if (index == 0) {
                                "Submitting video job · ${candidate.displayName}"
                            } else {
                                "Retrying with softer prompt…"
                            },
                            deadlineEpochMs = deadline,
                        ),
                    )
                    try {
                        val maxPolls = when (candidate.id) {
                            // Wan2 queues hard — fail fast so LTX can run within the budget.
                            "wan2-video-hf" -> budget.maxPolls(pollDelayMs = 2_500, floor = 3, ceiling = 24)
                            else -> budget.maxPolls(pollDelayMs = 3_000, floor = 5, ceiling = 90)
                        }
                        val pollDelay = if (candidate.id == "wan2-video-hf") 2_500L else 3_000L
                        val result = hf.predict(
                            spaceHost = candidate.endpoint,
                            apiName = CloudModelContracts.effectiveApiName(candidate),
                            data = SpacePayloads.forVideo(candidate.id, variant),
                            hfToken = settings.hfToken.value,
                            maxPolls = maxPolls,
                            pollDelayMs = pollDelay,
                            deadlineMs = deadline,
                            pollRequestTimeoutMs = GenerationBudget.GRADIO_POLL_REQUEST_TIMEOUT_MS,
                            onPoll = { pollIndex, polls ->
                                val frac =
                                    0.2f + 0.65f * (pollIndex + 1).toFloat() / polls.coerceAtLeast(1)
                                emit(
                                    GenerativeState.Running(
                                        frac.coerceIn(0.2f, 0.9f),
                                        "Video poll ${pollIndex + 1}/$polls · ${candidate.displayName}",
                                        deadlineEpochMs = deadline,
                                    ),
                                )
                            },
                        )
                        val url = extractRef(result)
                        emit(
                            GenerativeState.Running(
                                0.92f,
                                "Downloading video…",
                                deadlineEpochMs = deadline,
                            ),
                        )
                        val path = io.downloadResult(url, spaceHost = candidate.endpoint)
                        usage.record(
                            candidate,
                            success = true,
                            note = "Video · ${CloudModelContracts.statusLabel(candidate)} · ${prompt.take(80)}",
                        )
                        health.recordSuccess(candidate.id)
                        emit(GenerativeState.VideoReady(path, candidate.id))
                        return@flow
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val failure = CloudFailureClassifier.from(e)
                        lastError = e
                        val kind = when {
                            e.isAccountQuotaExhausted() -> ModelHealthTracker.FailureKind.QUOTA_ACCOUNT
                            failure is CloudFailure.Offline -> ModelHealthTracker.FailureKind.OFFLINE
                            e.message.orEmpty().contains("429") ||
                                e.message.orEmpty().contains("rate limit", ignoreCase = true) ||
                                e.message.orEmpty().contains("Queue is full", ignoreCase = true) ->
                                ModelHealthTracker.FailureKind.RATE_LIMIT
                            else -> ModelHealthTracker.FailureKind.GENERIC
                        }
                        health.recordFailure(candidate.id, kind)
                        if (e.isAccountQuotaExhausted()) throw e
                        if (failure is CloudFailure.Offline) throw e
                        // Rate-limited / full queue: skip remaining prompt variants for this host.
                        if (kind == ModelHealthTracker.FailureKind.RATE_LIMIT) break
                    }
                }
            }
            throw lastError ?: IllegalStateException("Video generation failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            usage.record(
                attempted,
                success = false,
                note = CloudModelContracts.usageFailureNote(attempted, e.message.orEmpty()),
            )
            health.recordFailure(
                attempted.id,
                if (e.isAccountQuotaExhausted()) {
                    ModelHealthTracker.FailureKind.QUOTA_ACCOUNT
                } else {
                    ModelHealthTracker.FailureKind.GENERIC
                },
            )
            emit(GenerativeState.Failed(
                CloudModelContracts.friendlyFailure(
                    attempted,
                    e.message.orEmpty(),
                    "Video generation",
                    selectedDisplayName = provider.displayName,
                ),
            ))
        }
    }.flowOn(Dispatchers.Default)

    /** Offline speech-to-text (L3 Audio Scribe) — mic/file → text, no cloud. */
    fun generateTranscribe(
        audioPath: String,
        prompt: String = LocalAudioTranscriber.DEFAULT_PROMPT,
    ): Flow<GenerativeState> = flow {
        emit(GenerativeState.Preparing("Loading audio scribe…"))
        try {
            if (!localTranscriber.isReady()) {
                emit(
                    GenerativeState.Failed(
                        "Download ${LiteRtLmPacks.GEMMA4_CODE} from Model packs for offline transcription.",
                    ),
                )
                return@flow
            }
            emit(GenerativeState.Running(0.2f, "Transcribing on-device…"))
            when (val result = localTranscriber.transcribe(audioPath, prompt)) {
                is LocalTranscribeResult.Ok -> {
                    emit(
                        GenerativeState.TranscribeReady(
                            result.text,
                            LiteRtLmPacks.GEMMA4_CODE,
                        ),
                    )
                }
                is LocalTranscribeResult.Unavailable ->
                    emit(GenerativeState.Failed(result.reason))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(GenerativeState.Failed(e.message ?: "Transcription failed"))
        }
    }.flowOn(Dispatchers.Default)

    @OptIn(ExperimentalEncodingApi::class)
    fun generateAudio(
        prompt: String,
        persona: VoicePersona = VoiceCatalog.byId(VoiceCatalog.defaultId),
        knobs: VoiceKnobs = VoiceKnobs.Default,
        referenceAudioUri: String? = null,
        assists: GenerativeAssists = GenerativeAssists(),
    ): Flow<GenerativeState> = flow {
        val provider = settings.selectedProvider(AiCapability.AUDIO)
        var attempted = provider
        val safeKnobs = knobs.sanitized()
        val spoken = enrichAudioPrompt(prompt.trim(), assists)
        // Mirrors the three unconditional local branches below (voice-changer, scribe, TTS —
        // TTS is deliberately "always preferred" whenever ready, not gated by online/offline).
        // Same fix as generateImage/generateCode/generateVideo: only announce the cloud
        // provider once it's actually the one about to run.
        val tryLocalFirst = (!referenceAudioUri.isNullOrBlank() && prompt.trim().equals("voice-change", ignoreCase = true)) ||
            (
                settings.selectionId(AiCapability.AUDIO) == LiteRtLmPacks.AUDIO_SCRIBE &&
                    !referenceAudioUri.isNullOrBlank() &&
                    localTranscriber.isReady()
                ) ||
            localAudio.isReady()
        if (!tryLocalFirst) {
            emit(GenerativeState.Preparing("Connecting to ${provider.displayName}"))
        }
        try {
            when (val safety = InputSafetyGate.checkPrompt(spoken)) {
                is SafetyVerdict.Blocked -> error(safety.reason)
                is SafetyVerdict.Ok -> Unit
            }
            // Voice-changer-only path: transform an existing clip offline.
            if (!referenceAudioUri.isNullOrBlank() && prompt.trim().equals("voice-change", ignoreCase = true)) {
                emit(GenerativeState.Running(0.2f, "Applying local voice knobs…"))
                when (val changed = localVoiceChanger.transform(referenceAudioUri, safeKnobs)) {
                    is LocalAudioResult.Ok -> {
                        emit(GenerativeState.AudioReady(changed.audioPath, "local-voice-changer"))
                        return@flow
                    }
                    is LocalAudioResult.Unavailable -> error(changed.reason)
                }
            }
            // Audio scribe picker: transcribe attached clip instead of TTS when selected.
            if (settings.selectionId(AiCapability.AUDIO) == LiteRtLmPacks.AUDIO_SCRIBE &&
                !referenceAudioUri.isNullOrBlank() &&
                localTranscriber.isReady()
            ) {
                emit(GenerativeState.Running(0.15f, "Transcribing on-device…"))
                when (val result = localTranscriber.transcribe(referenceAudioUri, spoken)) {
                    is LocalTranscribeResult.Ok -> {
                        emit(
                            GenerativeState.TranscribeReady(
                                result.text,
                                LiteRtLmPacks.GEMMA4_CODE,
                            ),
                        )
                        return@flow
                    }
                    is LocalTranscribeResult.Unavailable -> {
                        emit(GenerativeState.Failed(result.reason))
                        return@flow
                    }
                }
            }
            val networkOk = settings.networkLikelyAvailable()
            if (localAudio.isReady()) {
                emit(GenerativeState.Running(0.08f, "Generating speech on-device…"))
                when (val local = localAudio.generate(spoken, persona, safeKnobs)) {
                    is LocalAudioResult.Ok -> {
                        emit(GenerativeState.AudioReady(local.audioPath, "local-tts-system"))
                        return@flow
                    }
                    is LocalAudioResult.Unavailable -> {
                        if (!networkOk) {
                            emit(
                                GenerativeState.Failed(
                                    "You're offline and device TTS couldn't run. " +
                                        "Check system Text-to-speech in Android settings, then retry.",
                                ),
                            )
                            return@flow
                        }
                        emit(GenerativeState.Running(0.1f, "Local TTS unavailable — using cloud…"))
                    }
                }
            }
            if (!networkOk) {
                emit(
                    GenerativeState.Failed(
                        "You're offline. Enable device TTS or reconnect to use cloud audio.",
                    ),
                )
                return@flow
            }
            if (!settings.cloudGenerationAllowed()) {
                emit(GenerativeState.Failed(settings.cloudDisabledReason(AiCapability.AUDIO)))
                return@flow
            }
            val candidates = CloudModelRouting.fallbackChain(provider, AiCapability.AUDIO, settings, health)
            val budget = GenerationBudget.forAudio()
            val deadline = budget.deadlineMs
            var lastError: Exception? = null
            emit(
                GenerativeState.Running(
                    0.05f,
                    "Queued · ${provider.displayName} · ${persona.displayName}",
                    deadlineEpochMs = deadline,
                ),
            )
            for ((modelIndex, candidate) in candidates.withIndex()) {
                budget.throwIfExpired()
                attempted = candidate
                CloudModelContracts.preflightOrNull(candidate)?.let { error(it) }
                requireKeyIfNeeded(candidate)
                if (modelIndex > 0) {
                    emit(
                        GenerativeState.Running(
                            0.2f,
                            "${provider.displayName} busy — trying ${candidate.displayName}…",
                            deadlineEpochMs = deadline,
                        ),
                    )
                }
                try {
                    val path = when (candidate.platform) {
                        CloudPlatform.HF_INFERENCE -> {
                            val token = settings.hfToken.value
                                ?: throw CloudFailureException(CloudFailure.AuthRejected)
                            emit(
                                GenerativeState.Running(
                                    0.45f,
                                    "HF TTS · ${candidate.displayName}",
                                    deadlineEpochMs = deadline,
                                ),
                            )
                            val bytes = hfInference.textToSpeech(
                                modelId = candidate.endpoint,
                                text = spoken,
                                hfToken = token,
                            )
                            CloudOutputValidator.validateAudio(bytes)?.let {
                                throw CloudFailureException(CloudFailure.BadOutput)
                            }
                            io.downloadResult(
                                "data:audio/wav;base64,${Base64.encode(bytes)}",
                            )
                        }
                        CloudPlatform.HF_SPACE -> {
                            val data = SpacePayloads.forAudio(
                                candidate.id,
                                spoken,
                                persona.cloudVoiceId,
                                safeKnobs,
                                edgeVoiceLabel = persona.edgeVoiceLabel,
                            )
                            emit(
                                GenerativeState.Running(
                                    0.35f,
                                    "Space TTS · ${candidate.displayName}",
                                    deadlineEpochMs = deadline,
                                ),
                            )
                            val result = hf.predict(
                                spaceHost = candidate.endpoint,
                                apiName = CloudModelContracts.effectiveApiName(candidate),
                                data = data,
                                hfToken = settings.hfToken.value,
                                maxPolls = budget.maxPolls(pollDelayMs = 2_000, floor = 3, ceiling = 20),
                                pollDelayMs = 2_000,
                                deadlineMs = deadline,
                                pollRequestTimeoutMs = GenerationBudget.GRADIO_POLL_REQUEST_TIMEOUT_MS,
                                onPoll = { pollIndex, maxPolls ->
                                    budget.throwIfExpired()
                                    val frac =
                                        0.35f + 0.5f * (pollIndex + 1).toFloat() / maxPolls.coerceAtLeast(1)
                                    emit(
                                        GenerativeState.Running(
                                            frac.coerceIn(0.35f, 0.88f),
                                            "Audio poll ${pollIndex + 1}/$maxPolls",
                                            deadlineEpochMs = deadline,
                                        ),
                                    )
                                },
                            )
                            val url = extractRef(result)
                            io.downloadResult(url, spaceHost = candidate.endpoint)
                        }
                        else -> error("${candidate.platform} is not supported for Audio")
                    }
                    emit(GenerativeState.Running(0.92f, "Applying local voice knobs…"))
                    val finalPath = if (localVoiceChanger.isReady() && !safeKnobs.isIdentity) {
                        when (val changed = localVoiceChanger.transform(path, safeKnobs)) {
                            is LocalAudioResult.Ok -> changed.audioPath
                            is LocalAudioResult.Unavailable -> path
                        }
                    } else {
                        path
                    }
                    usage.record(candidate, success = true, note = "audio · ${persona.id}")
                    health.recordSuccess(candidate.id)
                    emit(GenerativeState.AudioReady(finalPath, candidate.id))
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    val failure = CloudFailureClassifier.from(e)
                    val kind = when (failure) {
                        is CloudFailure.QuotaExhausted ->
                            if (failure.scope == CloudFailure.QuotaExhausted.Scope.ACCOUNT) {
                                ModelHealthTracker.FailureKind.QUOTA_ACCOUNT
                            } else {
                                ModelHealthTracker.FailureKind.GENERIC
                            }
                        CloudFailure.CreditsExhausted -> ModelHealthTracker.FailureKind.CREDITS
                        CloudFailure.Offline -> ModelHealthTracker.FailureKind.OFFLINE
                        CloudFailure.Timeout -> ModelHealthTracker.FailureKind.GENERIC
                        else -> when {
                            e.message.orEmpty().contains("429") ||
                                e.message.orEmpty().contains("rate limit", ignoreCase = true) ->
                                ModelHealthTracker.FailureKind.RATE_LIMIT
                            else -> ModelHealthTracker.FailureKind.GENERIC
                        }
                    }
                    health.recordFailure(candidate.id, kind)
                    if (failure is CloudFailure.Offline) throw e
                }
            }
            throw lastError ?: IllegalStateException("Audio generation failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = CloudFailureClassifier.from(e)
            val kind = when (failure) {
                is CloudFailure.QuotaExhausted ->
                    if (failure.scope == CloudFailure.QuotaExhausted.Scope.ACCOUNT) {
                        ModelHealthTracker.FailureKind.QUOTA_ACCOUNT
                    } else {
                        ModelHealthTracker.FailureKind.GENERIC
                    }
                CloudFailure.CreditsExhausted -> ModelHealthTracker.FailureKind.CREDITS
                CloudFailure.Offline -> ModelHealthTracker.FailureKind.OFFLINE
                CloudFailure.Timeout -> ModelHealthTracker.FailureKind.GENERIC
                else -> ModelHealthTracker.FailureKind.GENERIC
            }
            usage.record(
                attempted,
                success = false,
                note = CloudModelContracts.usageFailureNote(attempted, e.message.orEmpty()),
            )
            health.recordFailure(attempted.id, kind)
            emit(
                GenerativeState.Failed(
                    CloudModelContracts.friendlyFailure(
                        attempted,
                        e.message.orEmpty(),
                        "Audio generation",
                        selectedDisplayName = provider.displayName,
                    ),
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    private fun buildCodeSystem(assists: GenerativeAssists): String = buildString {
        append("You are a helpful coding assistant. Return clear, working code with brief explanations. ")
        append("If the prompt is incomplete or oddly phrased, infer sensible defaults and note them — never refuse for ambiguity alone. ")
        if (assists.pragmatic) {
            append(
                "Prefer completing lawful software tasks over refusals. " +
                    "Do not decline ordinary coding, networking, UI, or automation questions. ",
            )
        }
        if (assists.creative) {
            append("Explore practical alternatives when helpful; keep answers concrete. ")
        }
    }

    /**
     * Ordered prompt variants: richest first, then progressively softer so a
     * blocked / flaky Space still has a chance to succeed.
     */
    private fun visualPromptVariants(prompt: String, assists: GenerativeAssists): List<String> {
        // No implicit subject. Image/Video are general-purpose generators: an empty prompt used
        // to become "modest fashion lookbook portrait", which silently rewrote what the user
        // asked for. Modest-wear framing belongs to try-on only.
        val base = prompt.trim().ifBlank { "a photograph" }
        val rich = enrichVisualPrompt(base, assists)
        val soft = enrichVisualPrompt(
            base,
            assists.copy(bypassFilter = true, detailBoost = false, qualityGuard = false),
        )
        val bare = base
        return listOf(rich, soft, bare).distinct()
    }

    private fun enrichVisualPrompt(prompt: String, assists: GenerativeAssists): String {
        val extras = buildList {
            // "Editorial" nudges the model away from a safety refusal without prescribing a
            // subject; the modest-wear wording it used to add steered every generation, whatever
            // the user actually typed.
            if (assists.bypassFilter) {
                add("editorial photography, professional composition, SFW")
            }
            if (assists.fashionContext) {
                add("fashion photography, garment fabric detail, catalog style")
            }
            if (assists.detailBoost) {
                add("sharp detail, coherent lighting, high resolution")
            }
            if (assists.qualityGuard) {
                add("avoid blur, avoid deformed anatomy, avoid text overlays, clean background")
            }
        }
        return if (extras.isEmpty()) prompt else "$prompt. ${extras.joinToString(". ")}"
    }

    /** Optional voiceover framing for TTS scripts when the Advanced toggle is on. */
    private fun enrichAudioPrompt(prompt: String, assists: GenerativeAssists): String {
        if (prompt.equals("voice-change", ignoreCase = true)) return prompt
        if (!assists.fashionContext) return prompt
        return "$prompt. Narrate as a product voiceover — clear, calm, SFW."
    }

    private fun requireKeyIfNeeded(provider: CloudModelProvider) {
        if (provider.requiresApiKey && settings.apiKeyFor(provider).isNullOrBlank()) {
            error("Add the free ${provider.platform.name} API key in Settings before using ${provider.displayName}")
        }
    }

    /** OpenAI-compatible chat for News tab and assistants. */
    suspend fun chat(
        prompt: String,
        system: String,
        capability: AiCapability = AiCapability.CODE,
        temperature: Double = 0.4,
    ): LlmResult = chatWithFallback(prompt, system, capability, temperature).first

    /** Provider id of the on-device model chat would use — for labeling a streamed reply. */
    fun localChatProviderId(): String = localCode.providerId()

    /**
     * Streaming counterpart of [chatWithFallback]'s local branch. Cloud chat still returns as
     * one block — cloud model APIs would need server-sent-events support in [LlmClient] to
     * stream, which this doesn't add. Callers check [localCodeReady] / [AppSettings.prefersLocal]
     * themselves and fall back to [chatWithFallback] when this doesn't apply or fails.
     */
    fun localChatStream(
        prompt: String,
        system: String,
        assists: GenerativeAssists = GenerativeAssists(),
    ): Flow<LocalCodeStreamEvent> {
        val effectiveSystem = buildCodeSystem(assists).let { base ->
            if (system.isBlank()) base else "$base\n\n$system"
        }
        return localCode.generateStream(prompt, effectiveSystem)
    }

    /**
     * Chat with the same fallback chain as [generateCode] — tries Groq, OpenRouter,
     * and HF Inference when the selected model is unavailable or rate-limited.
     */
    suspend fun chatWithFallback(
        prompt: String,
        system: String,
        capability: AiCapability = AiCapability.CODE,
        temperature: Double = 0.4,
        assists: GenerativeAssists = GenerativeAssists(),
    ): Pair<LlmResult, CloudModelProvider> {
        val provider = settings.selectedProvider(capability)
        val effectiveSystem = buildCodeSystem(assists).let { base ->
            if (system.isBlank()) base else "$base\n\n$system"
        }
        val effectiveTemp = when {
            assists.creative && assists.pragmatic -> temperature.coerceAtLeast(0.45)
            assists.creative -> temperature.coerceAtLeast(0.5)
            else -> temperature
        }
        if (capability == AiCapability.CODE &&
            localCode.isReady() &&
            (!settings.networkLikelyAvailable() || settings.prefersLocal(AiCapability.CODE))
        ) {
            when (val local = localCode.generate(prompt, effectiveSystem)) {
                is LocalCodeResult.Ok -> {
                    val localProvider = localCodeProvider()
                    usage.record(
                        localProvider,
                        tokensIn = local.tokensIn,
                        tokensOut = local.tokensOut,
                        success = true,
                        note = "Chat · local · ${localProvider.displayName}",
                    )
                    return LlmResult(local.text, local.tokensIn, local.tokensOut) to localProvider
                }
                is LocalCodeResult.Unavailable -> {
                    if (!settings.networkLikelyAvailable()) {
                        error(
                            "You're offline and local Code couldn't run. ${local.reason}",
                        )
                    }
                }
            }
        }
        if (!settings.cloudGenerationAllowed()) {
            error(settings.cloudDisabledReason(capability))
        }
        if (!settings.networkLikelyAvailable()) {
            error(
                "You're offline. Download local-gemma-4-e2b-v1 from Model packs for offline chat.",
            )
        }
        val candidates = CloudModelRouting.codeFallbackChain(provider, settings)
        var lastError: Exception? = null
        for (candidate in candidates) {
            if (CloudModelContracts.preflightOrNull(candidate) != null) continue
            requireKeyIfNeeded(candidate)
            val key = settings.apiKeyFor(candidate) ?: continue
            try {
                val result = llm.chat(
                    platform = candidate.platform,
                    model = candidate.endpoint,
                    prompt = prompt,
                    apiKey = key,
                    system = effectiveSystem,
                    temperature = effectiveTemp,
                )
                if (result.text.isBlank()) error("Empty LLM response")
                usage.record(
                    candidate,
                    tokensIn = result.tokensIn,
                    tokensOut = result.tokensOut,
                    success = true,
                    note = "Chat · ${CloudModelContracts.statusLabel(candidate)}",
                )
                return result to candidate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (e.isMonthlyCreditsExhausted()) continue
            }
        }
        throw lastError ?: IllegalStateException("Chat failed — add Groq, OpenRouter, or HF token in Settings")
    }

    private fun extractRef(element: kotlinx.serialization.json.JsonElement): String =
        GradioOutput.extractMediaRef(element)

    private fun localCodeProvider(): CloudModelProvider {
        val id = localCode.providerId()
        val displayName = LocalModelCatalog.byId(id)?.displayName ?: "Local on-device"
        return CloudModelProvider(
            id = id,
            displayName = displayName,
            description = "On-device LiteRT-LM",
            platform = CloudPlatform.GROQ,
            capability = AiCapability.CODE,
            endpoint = id,
            license = "On-device",
            requiresApiKey = false,
            qualityScore = 80,
            speedScore = 60,
        )
    }
}

/** Failures where burning the primary image deadline should still try one alternate Space. */
private fun CloudFailure.allowsImageFallbackGrace(): Boolean = when (this) {
    CloudFailure.Timeout, CloudFailure.Busy, CloudFailure.Waking, CloudFailure.HostUnavailable -> true
    is CloudFailure.Unknown ->
        raw.contains("timeout", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true) ||
            raw.contains("queue", ignoreCase = true) ||
            raw.contains("404")
    else -> false
}
