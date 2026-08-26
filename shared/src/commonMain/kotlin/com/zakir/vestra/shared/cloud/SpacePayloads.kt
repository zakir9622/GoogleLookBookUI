package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.prompt.PromptParameterEngine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Typed Gradio `data` arrays matched to live Space schemas with dynamic parameter inference.
 *
 * Every image argument must be a [fileData] object. Gradio validates image inputs as
 * `ImageData`/`FileData`, so a bare data-URL string fails validation before the model runs
 * and streams back an empty error instead of a message.
 */
object SpacePayloads {

    fun forImageGen(providerId: String, prompt: String): List<JsonElement> {
        val params = PromptParameterEngine.extractImageParams(prompt, defaultSteps = 4, isFastDistilled = true)
        return when (providerId) {
            "flux-schnell-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.seed ?: 0), // seed
                JsonPrimitive(params.seed == null), // randomize
                JsonPrimitive(params.width), // width
                JsonPrimitive(params.height), // height
                JsonPrimitive(params.steps.coerceIn(1, 12)), // steps
            )
            "sdxl-lightning-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(if (params.steps <= 4) "4-Step" else "8-Step"),
            )
            "kolors-image-gen-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.height),
                JsonPrimitive(params.width),
                JsonPrimitive(params.guidanceScale),
            )
            "playground-v25-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(""),
                JsonPrimitive(params.guidanceScale),
                JsonPrimitive(params.steps.coerceIn(1, 30)),
            )
            "aura-flow-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.seed ?: 0),
                JsonPrimitive(params.seed == null),
                JsonPrimitive(params.steps.coerceIn(1, 25)),
                JsonPrimitive(params.guidanceScale),
            )
            else -> error("No hand-tuned image-gen payload for $providerId — use GradioSchemaClient")
        }
    }

    fun hasImageGen(providerId: String): Boolean =
        providerId in setOf(
            "flux-schnell-hf",
            "sdxl-lightning-hf",
            "kolors-image-gen-hf",
            "playground-v25-hf",
            "aura-flow-hf",
        )

    fun forImageEdit(providerId: String, prompt: String, imageDataUrl: String): List<JsonElement> {
        val params = PromptParameterEngine.extractImageEditParams(prompt)
        return when (providerId) {
            "instruct-pix2pix-hf" -> listOf(
                fileData(imageDataUrl),
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.steps.coerceIn(4, 20)), // steps
                JsonPrimitive(if (params.seed == null) "Randomize Seed" else "Fixed Seed"),
                JsonPrimitive(params.seed ?: 42),
                JsonPrimitive("Fix CFG"),
                JsonPrimitive(params.textGuidance),
                JsonPrimitive(params.imageGuidance),
            )
            "qwen-image-edit-hf" -> listOf(
                fileData(imageDataUrl),
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.seed ?: 0), // seed
                JsonPrimitive(params.seed == null), // randomize seed
                JsonPrimitive(1.0), // true guidance scale
                JsonPrimitive(params.steps.coerceIn(4, 16)), // steps
                JsonPrimitive(false), // enhance prompt — off avoids extra HF Inference call
            )
            "cosxl-edit-hf" -> listOf(
                fileData(imageDataUrl),
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.steps.coerceIn(4, 20)),
                JsonPrimitive(params.textGuidance),
            )
            "magicbrush-hf" -> listOf(
                fileData(imageDataUrl),
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.seed ?: 42),
                JsonPrimitive(params.steps.coerceIn(4, 20)),
            )
            "ledits-plus-plus-hf" -> listOf(
                fileData(imageDataUrl),
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(""),
                JsonPrimitive(params.steps.coerceIn(4, 25)),
            )
            "bria-rmbg-hf" -> listOf(
                fileData(imageDataUrl),
            )
            else -> error("No hand-tuned image-edit payload for $providerId — use GradioSchemaClient")
        }
    }

    fun hasImageEdit(providerId: String): Boolean =
        providerId in setOf(
            "instruct-pix2pix-hf",
            "qwen-image-edit-hf",
            "cosxl-edit-hf",
            "magicbrush-hf",
            "ledits-plus-plus-hf",
            "bria-rmbg-hf",
        )

    fun forVideo(providerId: String, prompt: String): List<JsonElement> {
        val params = PromptParameterEngine.extractVideoParams(prompt)
        return when (providerId) {
            "wan2-video-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonNull, // optional i2v image
                JsonPrimitive(params.width),
                JsonPrimitive(params.height),
                JsonPrimitive(params.frames), // frames
                JsonPrimitive(params.steps), // steps
                JsonPrimitive(params.cfg),
                JsonPrimitive(-1),
            )
            "ltx-zerogpu-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.negativePrompt),
                JsonNull, // image_n — must be null for text-to-video, not ""
                JsonNull, // video_n
                JsonPrimitive(params.height), // height
                JsonPrimitive(params.width), // width (live Space default)
                JsonPrimitive("text-to-video"),
                JsonPrimitive(params.durationSeconds), // duration seconds (live default)
                JsonPrimitive(9), // frames from input video
                JsonPrimitive(42), // seed
                JsonPrimitive(true), // randomize
                JsonPrimitive(1.0), // cfg (live default)
                JsonPrimitive(true), // improve texture
                JsonPrimitive(false), // slow motion
            )
            "cogvideox-5b-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(5),
                JsonPrimitive(8),
                JsonPrimitive(params.cfg),
            )
            "animatediff-hf" -> listOf(
                JsonPrimitive(params.cleanedPrompt.ifBlank { prompt }),
                JsonPrimitive(params.negativePrompt),
                JsonPrimitive(params.steps.coerceIn(10, 25)),
            )
            else -> error("No hand-tuned video payload for $providerId — use GradioSchemaClient")
        }
    }

    fun hasVideo(providerId: String): Boolean =
        providerId in setOf(
            "wan2-video-hf",
            "ltx-zerogpu-hf",
            "cogvideox-5b-hf",
            "animatediff-hf",
        )

    fun forAudio(
        providerId: String,
        text: String,
        voiceId: String,
        knobs: com.zakir.vestra.shared.audio.VoiceKnobs,
        edgeVoiceLabel: String = voiceId,
    ): List<JsonElement> {
        val params = PromptParameterEngine.extractAudioParams(text, voiceId, knobs)
        return when (providerId) {
            // Remsky Kokoro expects a multi-select voice list (not a bare string).
            "kokoro-tts-hf" -> listOf(
                JsonPrimitive(params.cleanedText.ifBlank { text }),
                kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive(params.voiceId.ifBlank { "af_heart" }))
                },
                JsonPrimitive(params.speed.coerceIn(0.5f, 2f).toDouble()),
            )
            // innoai Edge-TTS: text, voice dropdown label, rate, pitch.
            "edge-tts-hf" -> listOf(
                JsonPrimitive(params.cleanedText.ifBlank { text }),
                JsonPrimitive(if (params.voiceId.contains("Neural")) params.voiceId else edgeVoiceLabel.ifBlank { "en-US-JennyNeural - en-US (Female)" }),
                JsonPrimitive(((params.speed - 1f) * 50f).coerceIn(-50f, 50f).toDouble()),
                JsonPrimitive((params.pitchSemitones * 2f).coerceIn(-50f, 50f).toDouble()),
            )
            "parler-tts-hf" -> listOf(
                JsonPrimitive(params.cleanedText.ifBlank { text }),
                JsonPrimitive("A clear, high-quality, expressive voice speaking naturally."),
            )
            "bark-voice-hf" -> listOf(
                JsonPrimitive(params.cleanedText.ifBlank { text }),
                JsonPrimitive(params.voiceId.ifBlank { "v2/en_speaker_6" }),
            )
            "f5-tts-hf" -> listOf(
                JsonPrimitive(params.cleanedText.ifBlank { text }),
                JsonNull,
                JsonPrimitive(""),
                JsonPrimitive(params.speed.coerceIn(0.5f, 2.0f).toDouble()),
            )
            "musicgen-small-hf" -> listOf(
                JsonPrimitive(params.cleanedText.ifBlank { text }),
                JsonNull,
                JsonPrimitive(10),
            )
            "audioldm2-hf" -> listOf(
                JsonPrimitive(params.cleanedText.ifBlank { text }),
                JsonPrimitive(""),
                JsonPrimitive(5),
                JsonPrimitive(params.speed.toDouble()),
            )
            else -> error("No hand-tuned audio payload for $providerId — use GradioSchemaClient")
        }
    }

    fun hasAudio(providerId: String): Boolean =
        providerId in setOf(
            "kokoro-tts-hf",
            "edge-tts-hf",
            "parler-tts-hf",
            "bark-voice-hf",
            "f5-tts-hf",
            "musicgen-small-hf",
            "audioldm2-hf",
        )

    /**
     * Virtual try-on payloads. Throws with a model-specific message when the
     * selected Space requires mask/pose inputs the app cannot supply.
     */
    fun forTryOn(
        providerId: String,
        personDataUrl: String,
        garmentDataUrl: String,
        category: GarmentCategory,
        customSteps: Int? = null,
        customCfg: Double? = null,
        customSeed: Int? = null,
        customGarmentDesc: String? = null,
        autoCrop: Boolean = false,
        autoMask: Boolean = true,
        customClothType: String? = null,
    ): List<JsonElement> {
        CloudModelContracts.preflightOrNull(
            CloudModelCatalog.byId(providerId) ?: error("Unknown try-on model: $providerId"),
        )?.let { error(it) }

        val tryOnParams = PromptParameterEngine.extractTryOnParams(explicitCategory = category)
        val steps = (customSteps ?: tryOnParams.steps).coerceIn(10, 60)
        val cfg = (customCfg ?: tryOnParams.cfg).coerceIn(1.0, 10.0)
        val seed = customSeed ?: tryOnParams.seed
        val garmentDesc = if (!customGarmentDesc.isNullOrBlank()) customGarmentDesc else tryOnParams.garmentDesc
        val clothType = customClothType ?: tryOnParams.clothType

        return when (providerId) {
            "idm-vton-hf" -> listOf(
                imageEditor(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(garmentDesc),
                JsonPrimitive(autoMask),
                JsonPrimitive(autoCrop),
                JsonPrimitive(steps),
                JsonPrimitive(seed),
            )
            "ootd-hf" -> listOf(
                fileData(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(1), // number of images
                JsonPrimitive(steps),
                JsonPrimitive(cfg),
                JsonPrimitive(seed),
            )
            "catvton-hf" -> listOf(
                imageEditor(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(clothType),
                JsonPrimitive(steps),
                JsonPrimitive(cfg),
                JsonPrimitive(seed),
                JsonPrimitive("result only"),
            )
            "leffa-hf" -> listOf(
                fileData(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive("vton"),
                JsonPrimitive(cfg),
            )
            "catvton-flux-hf" -> listOf(
                imageEditor(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(clothType),
                JsonPrimitive(steps.coerceIn(10, 40)),
                JsonPrimitive(cfg),
                JsonPrimitive(seed),
                JsonPrimitive("result only"),
            )
            else -> listOf(fileData(personDataUrl), fileData(garmentDataUrl))
        }
    }

    /** Gradio ImageEditor value for auto-mask Spaces (background only, empty layers). */
    fun imageEditor(backgroundDataUrl: String): JsonElement = buildJsonObject {
        put("background", fileData(backgroundDataUrl))
        put("layers", buildJsonArray { })
        put("composite", JsonNull)
    }

    /**
     * Gradio `FileData` for an inline image. Gradio accepts a base64 data URL in `url`,
     * which is how the app avoids a separate upload round-trip.
     */
    fun fileData(dataUrl: String): JsonElement {
        require(dataUrl.startsWith("data:") || dataUrl.startsWith("http")) {
            "Reference image must be a data URL or https URL"
        }
        val mime = dataUrl.substringAfter("data:", "").substringBefore(";", "")
            .takeIf { it.isNotBlank() } ?: "image/jpeg"
        return buildJsonObject {
            put("path", JsonNull)
            put("url", dataUrl)
            put("size", JsonNull)
            put("orig_name", if (mime.endsWith("png")) "input.png" else "input.jpg")
            put("mime_type", mime)
            put("is_stream", false)
            put("meta", buildJsonObject { put("_type", "gradio.FileData") })
        }
    }
}
