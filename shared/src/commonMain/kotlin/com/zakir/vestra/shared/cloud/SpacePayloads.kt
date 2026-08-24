package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.domain.GarmentCategory
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Typed Gradio `data` arrays matched to live Space `/info` schemas (Aug 2026).
 * Wrong apiName / shape was the root cause of empty/failed generations.
 *
 * Every image argument must be a [fileData] object. Gradio validates image inputs as
 * `ImageData`/`FileData`, so a bare data-URL string fails validation before the model runs
 * and streams back an empty error instead of a message.
 */
object SpacePayloads {

    fun forImageGen(providerId: String, prompt: String): List<JsonElement> = when (providerId) {
        "flux-schnell-hf" -> listOf(
            JsonPrimitive(prompt),
            JsonPrimitive(0), // seed
            JsonPrimitive(true), // randomize
            JsonPrimitive(1024), // width
            JsonPrimitive(1024), // height
            JsonPrimitive(4), // steps
        )
        "sdxl-lightning-hf" -> listOf(
            JsonPrimitive(prompt),
            JsonPrimitive("4-Step"),
        )
        else -> error("No hand-tuned image-gen payload for $providerId — use GradioSchemaClient")
    }

    fun hasImageGen(providerId: String): Boolean =
        providerId == "flux-schnell-hf" || providerId == "sdxl-lightning-hf"

    fun forImageEdit(providerId: String, prompt: String, imageDataUrl: String): List<JsonElement> =
        when (providerId) {
            "instruct-pix2pix-hf" -> listOf(
                fileData(imageDataUrl),
                JsonPrimitive(prompt),
                JsonPrimitive(8), // steps — lower to fit free ZeroGPU seconds
                JsonPrimitive("Randomize Seed"),
                JsonPrimitive(42),
                JsonPrimitive("Fix CFG"),
                JsonPrimitive(7.5),
                JsonPrimitive(1.5),
            )
            "qwen-image-edit-hf" -> listOf(
                fileData(imageDataUrl),
                JsonPrimitive(prompt),
                JsonPrimitive(0), // seed
                JsonPrimitive(true), // randomize seed
                JsonPrimitive(1.0), // true guidance scale
                JsonPrimitive(8), // steps
                JsonPrimitive(false), // enhance prompt — off avoids extra HF Inference call
            )
            else -> error("No hand-tuned image-edit payload for $providerId — use GradioSchemaClient")
        }

    fun hasImageEdit(providerId: String): Boolean =
        providerId == "instruct-pix2pix-hf" || providerId == "qwen-image-edit-hf"

    fun forVideo(providerId: String, prompt: String): List<JsonElement> = when (providerId) {
        "wan2-video-hf" -> listOf(
            JsonPrimitive(prompt),
            JsonNull, // optional i2v image
            JsonPrimitive(832),
            JsonPrimitive(480),
            JsonPrimitive(33), // frames
            JsonPrimitive(25), // steps
            JsonPrimitive(5.0),
            JsonPrimitive(-1),
        )
        "ltx-zerogpu-hf" -> listOf(
            JsonPrimitive(prompt),
            JsonPrimitive("worst quality, inconsistent motion, blurry, jittery, distorted, watermark, text"),
            JsonNull, // image_n — must be null for text-to-video, not ""
            JsonNull, // video_n
            JsonPrimitive(512), // height
            JsonPrimitive(704), // width (live Space default)
            JsonPrimitive("text-to-video"),
            JsonPrimitive(2.0), // duration seconds (live default)
            JsonPrimitive(9), // frames from input video
            JsonPrimitive(42), // seed
            JsonPrimitive(true), // randomize
            JsonPrimitive(1.0), // cfg (live default)
            JsonPrimitive(true), // improve texture
            JsonPrimitive(false), // slow motion
        )
        else -> error("No hand-tuned video payload for $providerId — use GradioSchemaClient")
    }

    fun hasVideo(providerId: String): Boolean =
        providerId == "wan2-video-hf" || providerId == "ltx-zerogpu-hf"

    fun forAudio(
        providerId: String,
        text: String,
        voiceId: String,
        knobs: com.zakir.vestra.shared.audio.VoiceKnobs,
        edgeVoiceLabel: String = voiceId,
    ): List<JsonElement> = when (providerId) {
        // Remsky Kokoro expects a multi-select voice list (not a bare string).
        "kokoro-tts-hf" -> listOf(
            JsonPrimitive(text),
            kotlinx.serialization.json.buildJsonArray {
                add(JsonPrimitive(voiceId.ifBlank { "af_heart" }))
            },
            JsonPrimitive(knobs.speed.coerceIn(0.5f, 2f).toDouble()),
        )
        // innoai Edge-TTS: text, voice dropdown label, rate, pitch.
        "edge-tts-hf" -> listOf(
            JsonPrimitive(text),
            JsonPrimitive(edgeVoiceLabel.ifBlank { "en-US-JennyNeural - en-US (Female)" }),
            JsonPrimitive(((knobs.speed - 1f) * 50f).coerceIn(-50f, 50f).toDouble()),
            JsonPrimitive((knobs.pitchSemitones * 2f).coerceIn(-50f, 50f).toDouble()),
        )
        else -> error("No hand-tuned audio payload for $providerId — use GradioSchemaClient")
    }

    fun hasAudio(providerId: String): Boolean =
        providerId == "kokoro-tts-hf" || providerId == "edge-tts-hf"

    /**
     * Virtual try-on payloads. Throws with a model-specific message when the
     * selected Space requires mask/pose inputs the app cannot supply.
     */
    fun forTryOn(
        providerId: String,
        personDataUrl: String,
        garmentDataUrl: String,
        category: GarmentCategory,
    ): List<JsonElement> {
        CloudModelContracts.preflightOrNull(
            CloudModelCatalog.byId(providerId) ?: error("Unknown try-on model: $providerId"),
        )?.let { error(it) }

        return when (providerId) {
            "idm-vton-hf" -> listOf(
                imageEditor(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(category.idmGarmentDesc()),
                JsonPrimitive(true), // auto-generated mask
                JsonPrimitive(false), // auto-crop
                JsonPrimitive(30), // denoising steps
                JsonPrimitive(42), // seed
            )
            "ootd-hf" -> listOf(
                fileData(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(1), // number of images
                JsonPrimitive(20), // steps
                JsonPrimitive(2.0), // guidance
                JsonPrimitive(42), // seed
            )
            "catvton-hf" -> listOf(
                imageEditor(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(category.catvtonClothType()),
                JsonPrimitive(50), // inference steps
                JsonPrimitive(2.5), // cfg
                JsonPrimitive(42), // seed
                JsonPrimitive("result only"),
            )
            "leffa-hf" -> listOf(
                fileData(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive("vton"),
                JsonPrimitive(1.0),
            )
            "catvton-flux-hf" -> listOf(
                imageEditor(personDataUrl),
                fileData(garmentDataUrl),
                JsonPrimitive(category.catvtonClothType()),
                JsonPrimitive(28),
                JsonPrimitive(3.5),
                JsonPrimitive(42),
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

private fun GarmentCategory.idmGarmentDesc(): String = when (this) {
    GarmentCategory.LOWER_BODY -> "Lower-body garment"
    GarmentCategory.HIJAB, GarmentCategory.NIQAB, GarmentCategory.DUPATTA, GarmentCategory.HEADSCARF ->
        "Upper-body / head covering"
    else -> "Dress / full garment"
}

private fun GarmentCategory.catvtonClothType(): String = when (this) {
    GarmentCategory.LOWER_BODY -> "lower"
    GarmentCategory.ABAYA, GarmentCategory.JILBAB, GarmentCategory.KAFTAN,
    GarmentCategory.DRESS, GarmentCategory.LEHENGA, GarmentCategory.FULL_COVERAGE,
    GarmentCategory.SHALWAR_KAMEEZ -> "overall"
    else -> "upper"
}
