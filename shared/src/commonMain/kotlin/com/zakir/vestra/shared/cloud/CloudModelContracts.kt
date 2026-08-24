package com.zakir.vestra.shared.cloud

/**
 * Live Gradio / chat contracts audited against Space `/info` (Aug 2026).
 * Used for Settings readiness, payload builders, preflight, and model-specific errors.
 */
enum class ModelSupportLevel {
    /** Payload matches live schema; expected to work when Space is up. */
    READY,
    /** Schema known but host often queued/503 or quality varies. */
    DEGRADED,
    /** App cannot satisfy required inputs (mask/pose editors, etc.). */
    UNSUPPORTED,
}

data class CloudModelContract(
    val providerId: String,
    val support: ModelSupportLevel,
    /** Gradio api_name override when catalog drifts; null = use catalog. */
    val apiNameOverride: String? = null,
    val requiredInputs: List<String>,
    val schemaNote: String,
    val failureHint: String,
)

object CloudModelContracts {

    private val byId: Map<String, CloudModelContract> = listOf(
        // ── Try-on ──────────────────────────────────────────────────────
        CloudModelContract(
            providerId = "idm-vton-hf",
            support = ModelSupportLevel.DEGRADED,
            requiredInputs = listOf(
                "ImageEditor(person+auto-mask)",
                "Image(garment)",
                "Textbox(garment desc)",
                "Checkbox(auto-mask)",
                "Checkbox(auto-crop)",
                "Number(steps)",
                "Number(seed)",
            ),
            schemaNote = "tryon · 7 args (ImageEditor + garment FileData); host returns session errors",
            failureHint = "IDM-VTON's Space is returning session errors. Switch to OOTDiffusion in Settings.",
        ),
        CloudModelContract(
            providerId = "ootd-hf",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf(
                "Image(model)",
                "Image(garment)",
                "Slider(images)",
                "Slider(steps)",
                "Slider(guidance)",
                "Slider(seed)",
            ),
            schemaNote = "process_hd · FileData model + garment + images/steps/guidance/seed",
            failureHint = "OOTDiffusion needs a clear full-body photo. Retry off-peak or use CatVTON.",
        ),
        CloudModelContract(
            providerId = "catvton-hf",
            support = ModelSupportLevel.DEGRADED,
            requiredInputs = listOf(
                "ImageEditor(person)",
                "Image(garment)",
                "Radio(cloth type)",
                "Slider(steps)",
                "Slider(cfg)",
                "Slider(seed)",
                "Radio(show type)",
            ),
            schemaNote = "submit_function · ImageEditor person + cloth type; host rejects queued runs",
            failureHint = "CatVTON is rejecting runs right now. Use OOTDiffusion.",
        ),
        CloudModelContract(
            providerId = "fitdit-hf",
            support = ModelSupportLevel.UNSUPPORTED,
            requiredInputs = listOf(
                "Image(model)",
                "Image(garment)",
                "ImageEditor(mask)",
                "Image(pose)",
                "steps/guidance/seed/num_images/resolution",
            ),
            schemaNote = "process · requires mask editor + pose image (not in app)",
            failureHint = "FitDiT needs a mask and pose map the app cannot build yet. Switch to IDM-VTON or OOTDiffusion in Settings.",
        ),
        CloudModelContract(
            providerId = "leffa-hf",
            support = ModelSupportLevel.DEGRADED,
            requiredInputs = listOf("Person", "Garment", "mode", "scale"),
            schemaNote = "Host often 503; schema may change when Space wakes",
            failureHint = "Leffa Space is offline or waking up (503). Prefer IDM-VTON.",
        ),
        CloudModelContract(
            providerId = "catvton-flux-hf",
            support = ModelSupportLevel.DEGRADED,
            requiredInputs = listOf("Person", "Garment", "FLUX fill args"),
            schemaNote = "Host often 503 / queued ZeroGPU",
            failureHint = "CatVTON-FLUX Space is busy or offline. Prefer IDM-VTON or CatVTON.",
        ),

        // ── Image gen / edit ────────────────────────────────────────────
        CloudModelContract(
            providerId = "sdxl-turbo-inference",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("HF token with Inference Providers", "prompt"),
            schemaNote = "nscale text-to-image · stabilityai/sdxl-turbo",
            failureHint = "SDXL Turbo via HF Inference failed. Try FLUX Inference or a Space model.",
        ),
        CloudModelContract(
            providerId = "instruct-pix2pix-inference",
            support = ModelSupportLevel.UNSUPPORTED,
            requiredInputs = listOf("HF token with Inference Providers", "reference image", "prompt"),
            schemaNote = "nscale rejects timbrooks/instruct-pix2pix (HTTP 400) — use Qwen or InstructPix2Pix Space",
            failureHint = "HF Inference does not host InstructPix2Pix on nscale. Use Qwen Image Edit or InstructPix2Pix Space.",
        ),
        CloudModelContract(
            providerId = "flux-schnell-inference",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("HF token with Inference Providers", "prompt"),
            schemaNote = "nscale/fal-ai text-to-image · black-forest-labs/FLUX.1-schnell",
            failureHint = "HF Inference credits exhausted or token missing. Add HF token in Settings or wait for monthly reset.",
        ),
        CloudModelContract(
            providerId = "z-image-turbo-inference",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("HF token with Inference Providers", "prompt"),
            schemaNote = "fal-ai text-to-image · Tongyi-MAI/Z-Image-Turbo",
            failureHint = "Z-Image Turbo via HF Inference failed. Check HF token or try FLUX Inference.",
        ),
        CloudModelContract(
            providerId = "qwen25-coder-7b-hf",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("HF token with Inference Providers", "chat messages"),
            schemaNote = "HF Inference chat · Qwen/Qwen2.5-Coder-7B-Instruct",
            failureHint = "HF Inference rejected the 7B coder. Try 32B, Groq, or OpenRouter.",
        ),
        CloudModelContract(
            providerId = "flux-schnell-hf",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("prompt", "seed", "randomize", "width", "height", "steps"),
            schemaNote = "infer · prompt + seed/randomize/size/steps",
            failureHint = "FLUX Schnell Space failed — retry off-peak, add an HF token for FLUX Inference fallback, or switch model.",
        ),
        CloudModelContract(
            providerId = "sdxl-lightning-hf",
            support = ModelSupportLevel.UNSUPPORTED,
            requiredInputs = listOf("prompt", "steps dropdown"),
            schemaNote = "generate_image · Gradio API info returns 404 (Space offline Aug 2026)",
            failureHint = "SDXL Lightning Space is offline. Use FLUX Schnell Space.",
        ),
        CloudModelContract(
            providerId = "qwen-image-edit-hf",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf(
                "image", "prompt", "seed", "randomize", "guidance", "steps", "rewrite",
            ),
            schemaNote = "infer · FileData image + prompt + seed/guidance/steps",
            failureHint = "Qwen Image Edit is out of free ZeroGPU quota or waking. " +
                "The app retries and falls back to InstructPix2Pix Space automatically.",
        ),
        CloudModelContract(
            providerId = "instruct-pix2pix-hf",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf(
                "image", "instruction", "steps", "seed mode", "seed", "cfg mode", "text cfg", "image cfg",
            ),
            schemaNote = "generate · FileData image + instruction + CFG/seed (image is output 4)",
            failureHint = "InstructPix2Pix failed — wait ~30s and retry, or switch model in the composer.",
        ),

        // ── Code LLMs ───────────────────────────────────────────────────
        CloudModelContract(
            providerId = "qwen25-coder-hf",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("HF token with Inference Providers", "chat messages"),
            schemaNote = "HF Inference chat · Qwen/Qwen2.5-Coder-32B-Instruct",
            failureHint = "HF Inference rejected the token or model. Use a classic Write/Read token with Inference Providers, or switch to Groq.",
        ),
        CloudModelContract(
            providerId = "llama33-70b-groq",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("Groq API key", "chat messages"),
            schemaNote = "Groq chat · llama-3.3-70b-versatile",
            failureHint = "Groq free-tier limit or bad key. Wait a minute or re-save the Groq key in Settings.",
        ),
        CloudModelContract(
            providerId = "openrouter-free",
            support = ModelSupportLevel.READY,
            requiredInputs = listOf("OpenRouter API key", "openrouter/free"),
            schemaNote = "OpenRouter · openrouter/free",
            failureHint = "OpenRouter free router failed. Re-save the OpenRouter key or switch to Groq.",
        ),

        // ── Video ───────────────────────────────────────────────────────
        CloudModelContract(
            providerId = "wan2-video-hf",
            support = ModelSupportLevel.DEGRADED,
            requiredInputs = listOf(
                "prompt", "optional image", "width", "height", "frames", "steps", "guidance", "seed",
            ),
            schemaNote = "generate_video · 8 args; queues fill often — short poll then LTX",
            failureHint = "Wan2 queue is full or timed out. Switching to LTX-Video when available.",
        ),
        CloudModelContract(
            providerId = "ltx-zerogpu-hf",
            support = ModelSupportLevel.READY,
            apiNameOverride = "text_to_video",
            requiredInputs = listOf(
                "prompt", "negative", "image_n", "video_n", "h", "w", "task", "duration",
                "frames", "seed", "randomize", "cfg", "texture", "slow-mo",
            ),
            schemaNote = "text_to_video · 14 args (DeepRat ZeroGPU)",
            failureHint = "LTX text_to_video failed — retry off-peak or try Wan2 later.",
        ),

        // ── Audio / TTS ─────────────────────────────────────────────────
        CloudModelContract(
            providerId = "kokoro-tts-hf",
            support = ModelSupportLevel.READY,
            apiNameOverride = "generate_speech_from_ui",
            requiredInputs = listOf("text", "voice list", "speed"),
            schemaNote = "Remsky Kokoro ZeroGPU · generate_speech_from_ui",
            failureHint = "Kokoro is rate-limited or queued — wait a minute or switch to Edge-TTS.",
        ),
        CloudModelContract(
            providerId = "edge-tts-hf",
            support = ModelSupportLevel.READY,
            apiNameOverride = "tts_interface",
            requiredInputs = listOf("text", "voice", "rate", "pitch"),
            schemaNote = "innoai Edge-TTS · tts_interface",
            failureHint = "Edge-TTS failed — switch to Kokoro or retry.",
        ),
        CloudModelContract(
            providerId = "mms-tts-eng-hf",
            support = ModelSupportLevel.DEGRADED,
            requiredInputs = listOf("HF token", "text"),
            schemaNote = "HF Inference TTS · facebook/mms-tts-eng (often rejected)",
            failureHint = "MMS-TTS was rejected by HF Inference Providers. Use Kokoro or Edge-TTS Spaces (no Inference route).",
        ),
    ).associateBy { it.providerId }

    fun forProvider(provider: CloudModelProvider): CloudModelContract =
        byId[provider.id] ?: CloudModelContract(
            providerId = provider.id,
            support = if (provider.id.startsWith("hf-disc-")) {
                ModelSupportLevel.DEGRADED
            } else {
                ModelSupportLevel.READY
            },
            requiredInputs = listOf(provider.apiName),
            schemaNote = provider.usageNote.ifBlank { provider.description },
            failureHint = "${provider.displayName} failed. Switch model in Settings if this keeps happening.",
        )

    fun effectiveApiName(provider: CloudModelProvider): String =
        forProvider(provider).apiNameOverride ?: provider.apiName

    fun statusLabel(provider: CloudModelProvider): String = when (forProvider(provider).support) {
        ModelSupportLevel.READY -> "Ready"
        ModelSupportLevel.DEGRADED -> "Degraded"
        ModelSupportLevel.UNSUPPORTED -> "Unsupported"
    }

    /** Prefer live cooldown / verified labels from [health] when available. */
    fun liveStatusLabel(provider: CloudModelProvider, health: ModelHealthTracker?): String =
        health?.observedLabel(provider.id) ?: statusLabel(provider)

    fun settingsSupportingText(provider: CloudModelProvider): String {
        val c = forProvider(provider)
        val status = statusLabel(provider)
        return "$status · ${c.schemaNote}"
    }

    fun preflightOrNull(provider: CloudModelProvider): String? {
        val c = forProvider(provider)
        return if (c.support == ModelSupportLevel.UNSUPPORTED) c.failureHint else null
    }

    fun friendlyFailure(
        provider: CloudModelProvider,
        raw: String,
        label: String,
        selectedDisplayName: String? = null,
    ): String {
        val c = forProvider(provider)
        val msg = raw.trim()
        val prefix = if (
            selectedDisplayName != null &&
            !selectedDisplayName.equals(provider.displayName, ignoreCase = true)
        ) {
            "$selectedDisplayName unavailable — "
        } else {
            ""
        }
        val body = when {
            c.support == ModelSupportLevel.UNSUPPORTED -> c.failureHint
            // Check credits / 402 BEFORE the broad "Inference Providers" permissions match.
            msg.contains("402") ||
                msg.contains("depleted your monthly", ignoreCase = true) ||
                msg.contains("monthly included credits", ignoreCase = true) ->
                "Your Hugging Face Inference Providers monthly credits are used up. Credits reset each month. " +
                    "Prefer a free Image Space (FLUX Schnell) in Settings, use local Create when a pack is installed, " +
                    "or wait for the allowance to refill."
            msg.contains("sufficient permissions", ignoreCase = true) ||
                (
                    msg.contains("Inference Providers", ignoreCase = true) &&
                        msg.contains("permission", ignoreCase = true)
                    ) ->
                "Your HF token cannot call Inference Providers. Create a token with Inference permission (classic Read/Write), Save in Settings, or switch Code to Groq."
            // ZeroGPU minutes are billed to the Hugging Face account, not the Space, so
            // switching models cannot help until the daily allowance refills.
            msg.contains("quota exceeded", ignoreCase = true) || msg.contains("ZeroGPU quota", ignoreCase = true) ->
                "Your Hugging Face account is out of free ZeroGPU minutes. The allowance refills " +
                    "daily — tap Choose model for an Inference route, use a different HF token, or run try-on locally with Lite/Pro."
            msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true) ->
                "API key rejected for ${provider.displayName}. Re-save the free token in Settings."
            msg.contains("429") ||
                msg.contains("rate limit", ignoreCase = true) ||
                msg.contains("Rate limit", ignoreCase = true) ||
                msg.contains("too many requests", ignoreCase = true) ->
                "Free-tier rate limit on ${provider.displayName}. Cooling down — try another model or wait a minute."
            msg.contains("Queue is full", ignoreCase = true) ||
                msg.contains("503") ||
                msg.contains("Service Unavailable", ignoreCase = true) ->
                c.failureHint
            msg.contains("404") -> {
                val hostLabel = if (provider.displayName.endsWith("Space", ignoreCase = true)) {
                    provider.displayName
                } else {
                    "${provider.displayName} Space"
                }
                "$hostLabel looks offline (404). Switch model in Settings."
            }
            msg.contains("Model not supported by provider", ignoreCase = true) ||
                (
                    msg.contains("does not exist", ignoreCase = true) &&
                        provider.platform == CloudPlatform.HF_INFERENCE
                    ) ||
                (
                    msg.contains("deprecated", ignoreCase = true) &&
                        provider.platform == CloudPlatform.HF_INFERENCE
                    ) ->
                when (provider.capability) {
                    AiCapability.AUDIO ->
                        "HF Inference rejected ${provider.displayName}. Prefer Kokoro or Edge-TTS Spaces in Settings."
                    AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT ->
                        "HF Inference rejected ${provider.displayName}. Prefer a free Image Space (FLUX Schnell) in Settings."
                    else ->
                        "HF Inference rejected ${provider.displayName}. Switch model in Settings."
                }
            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ->
                "$label timed out on ${provider.displayName}. Retry off-peak or pick a faster free model."
            msg.contains("waking", ignoreCase = true) ||
                msg.contains("restarting", ignoreCase = true) ||
                msg.contains("empty error", ignoreCase = true) ||
                (msg.contains("event: error", ignoreCase = true) && msg.contains("null", ignoreCase = true)) ->
                "${provider.displayName} is out of free GPU quota or waking up. " +
                    "Free ZeroGPU quota refills after a while — retry later or pick another model. " +
                    "(${c.failureHint})"
            msg.contains("NSFW", ignoreCase = true) ||
                msg.contains("safety", ignoreCase = true) ||
                msg.contains("content policy", ignoreCase = true) ||
                msg.contains("blocked", ignoreCase = true) ->
                "$label was blocked by ${provider.displayName}. Enable Bypass filter assist or rephrase as fashion/editorial."
            msg.contains("No internet", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("UnknownHostException", ignoreCase = true) ||
                msg.contains("Network is unreachable", ignoreCase = true) ->
                "No internet connection. Reconnect and retry — or use Lite/Pro try-on offline."
            msg.contains("connection abort", ignoreCase = true) ||
                msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("Software caused connection", ignoreCase = true) ||
                msg.contains("Broken pipe", ignoreCase = true) ->
                "$label lost the connection mid-request. Retry, or pick another free model — " +
                    "or use Lite/Pro try-on offline."
            msg.contains("failed to connect", ignoreCase = true) ||
                msg.contains("connection refused", ignoreCase = true) ||
                msg.contains("ConnectException", ignoreCase = true) ->
                "$label could not reach the model host. Retry, switch model, or check VPN — " +
                    "this is usually not a phone offline issue."
            msg.contains("LinkedHashMap", ignoreCase = true) ||
                msg.contains("Kotlin reflection", ignoreCase = true) ->
                "$label failed to encode the request for ${provider.displayName}. Update the app and retry."
            msg.isBlank() -> "${c.failureHint} (${c.schemaNote})"
            else -> "${provider.displayName}: ${sanitizeHostnames(msg).take(220)}"
        }
        return prefix + body
    }

    fun usageFailureNote(provider: CloudModelProvider, raw: String): String {
        val hint = friendlyFailure(provider, raw, provider.capability.name)
        return "${statusLabel(provider)} · $hint"
    }
}
