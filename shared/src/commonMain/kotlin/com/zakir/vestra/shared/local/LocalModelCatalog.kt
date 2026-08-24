package com.zakir.vestra.shared.local

import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.domain.EngineTier

/**
 * How a local entry appears in Create / Audio Studio model pickers.
 * Quality packs run as post-steps, not as txt2img / TTS generators.
 */
enum class LocalModelPickerRole {
    /** Offered in the studio ON-DEVICE list for this capability. */
    STUDIO_GENERATOR,
    /** Settings / packs only — never a Create Studio generator. */
    QUALITY_POST,
    /** Gallery / casting assets — try-on adjacent, not generation. */
    ASSET,
}

/**
 * Catalog of open-source models that run on-device (your phone as a local AI device).
 * Packs are downloaded once from Hugging Face and then work fully offline.
 *
 * This is separate from [com.zakir.vestra.shared.cloud.CloudModelCatalog] — those need network.
 */
data class LocalModelEntry(
    val id: String,
    val displayName: String,
    val description: String,
    val capability: AiCapability,
    /** Matching [com.zakir.vestra.shared.domain.ModelPack.id] when downloadable. */
    val packId: String?,
    val engineTier: EngineTier? = null,
    val license: String,
    val approxSizeLabel: String,
    val openSource: Boolean = true,
    val offlineAfterInstall: Boolean = true,
    /** Ready to run in this app build once the pack is installed. */
    val runnable: Boolean,
    val testingNote: String,
    val pickerRole: LocalModelPickerRole = LocalModelPickerRole.STUDIO_GENERATOR,
)

object LocalModelCatalog {
    val entries: List<LocalModelEntry> = listOf(
        LocalModelEntry(
            id = "local-lite-tryon",
            displayName = "Fast try-on (ONNX)",
            description = "Open garment segmentation + human parsing compositor. Works on Android 15+ (app minSdk).",
            capability = AiCapability.TRY_ON,
            packId = "lite-v1",
            engineTier = EngineTier.LITE,
            license = "Apache-2.0 / open ONNX graphs",
            approxSizeLabel = "~15–40 MB",
            runnable = true,
            testingNote = "Download lite-v1 from Settings → Model packs (~68 MB). Required for Lite and Pro try-on.",
        ),
        LocalModelEntry(
            id = "local-pro-fp16",
            displayName = "Pro try-on FP16 (SD1.5)",
            description = "Higher-fidelity FP16 diffusion pack for devices with more RAM/storage.",
            capability = AiCapability.TRY_ON,
            packId = "pro-v1",
            engineTier = EngineTier.PRO,
            license = "CreativeML OpenRAIL-M (SD1.5)",
            approxSizeLabel = "~4.3 GB",
            runnable = true,
            testingNote = "Preferred Pro pack on HF manifest. Download lite-v1 + pro-v1 for full Pro try-on.",
        ),
        LocalModelEntry(
            id = "local-pro-int8",
            displayName = "Pro try-on INT8 (SD1.5)",
            description = "Quantized Stable Diffusion 1.5 + ControlNet depth — full diffusion try-on on device. Pixel 9 optimized.",
            capability = AiCapability.TRY_ON,
            packId = "pro-v2-int8",
            engineTier = EngineTier.PRO,
            license = "CreativeML OpenRAIL-M (SD1.5)",
            approxSizeLabel = "~2 GB",
            runnable = false,
            testingNote = "Export ready; HF manifest upload pending — not selectable for download until hosted. Prefer pro-v1.",
        ),
        LocalModelEntry(
            id = "local-studio-models",
            displayName = "Studio model gallery",
            description = "Open pose / ethnicity-tagged base model photos for casting shoots (no generation weights).",
            capability = AiCapability.TRY_ON,
            packId = "studio-models-v1",
            license = "App-bundled / pack license",
            approxSizeLabel = "~50–200 MB",
            runnable = true,
            testingNote = "Optional. Improves casting variety for local shoots.",
            pickerRole = LocalModelPickerRole.ASSET,
        ),
        LocalModelEntry(
            id = "local-sdturbo-v1",
            displayName = "Local image gen (tiny-SD)",
            description = "On-device tiny-SD / LCM via ORT — download local-sdturbo-v1 (~1.06 GB) from Model packs, then Create works offline.",
            capability = AiCapability.IMAGE_GEN,
            packId = "local-sdturbo-v1",
            license = "CreativeML OpenRAIL-M (SD1.5 lineage)",
            approxSizeLabel = "~1.06 GB",
            runnable = true,
            testingNote = "Download local-sdturbo-v1 in Settings → Model packs. Airplane mode Create Studio should yield a PNG.",
        ),
        LocalModelEntry(
            id = "local-bonsai-image-v1",
            displayName = "Bonsai Image 4B (LiteRT)",
            description = "Ternary-weight diffusion transformer (FLUX.2-klein architecture) via LiteRT " +
                "CompiledModel/Interpreter — 512x512, fully offline. Several minutes per image on CPU; " +
                "text-to-image only, no edit.",
            capability = AiCapability.IMAGE_GEN,
            packId = "local-bonsai-image-v1",
            license = "Apache-2.0",
            approxSizeLabel = "~4.0 GB",
            runnable = true,
            testingNote = "Download local-bonsai-image-v1 in Settings → Model packs (~4 GB, CPU/XNNPACK). " +
                "Airplane mode Create Studio should yield a 512x512 PNG in several minutes; " +
                "treat 8 GB RAM as the floor, 12 GB+ as the practical target.",
        ),
        LocalModelEntry(
            id = "local-sdturbo-edit",
            displayName = "Local image edit (img2img)",
            description = "Same tiny-SD pack with vae_encoder — attach a reference photo and edit offline.",
            capability = AiCapability.IMAGE_EDIT,
            packId = "local-sdturbo-v1",
            license = "CreativeML OpenRAIL-M (SD1.5 lineage)",
            approxSizeLabel = "~1.06 GB (shared)",
            runnable = true,
            testingNote = "Requires local-sdturbo-v1 v3+ (includes vae_encoder.onnx). Pick a reference, then Generate.",
        ),
        LocalModelEntry(
            id = "local-qwen3-06b-v1",
            displayName = "Local Qwen3 0.6B (fast)",
            description = "LiteRT-LM INT4 — 331 MB, loads far quicker than Gemma 4 E2B.",
            capability = AiCapability.CODE,
            packId = "local-qwen3-06b-v1",
            license = "Apache-2.0",
            approxSizeLabel = "~331 MB",
            runnable = true,
            testingNote = "Download local-qwen3-06b-v1 in Model packs. Fastest offline Code/Chat route.",
        ),
        LocalModelEntry(
            id = "local-gemma-4-e2b-v1",
            displayName = "Local Gemma 4 E2B (code)",
            description = "LiteRT-LM on-device — Gallery-class Gemma 4 for Code Studio.",
            capability = AiCapability.CODE,
            packId = "local-gemma-4-e2b-v1",
            license = "Gemma Terms of Use",
            approxSizeLabel = "~2.6 GB",
            runnable = true,
            testingNote = "Download local-gemma-4-e2b-v1 in Model packs. Airplane mode Code Studio with Gemma 4.",
        ),
        LocalModelEntry(
            id = "local-gemma-v1",
            displayName = "Local Gemma 3 1B (legacy)",
            description = "Legacy MediaPipe LLM — download local-gemma-v1 (~530 MB). Prefer Gemma 4 E2B.",
            capability = AiCapability.CODE,
            packId = "local-gemma-v1",
            license = "Gemma Terms of Use",
            approxSizeLabel = "~530 MB",
            runnable = true,
            testingNote = "Legacy fallback. Download local-gemma-v1 or upgrade to local-gemma-4-e2b-v1.",
        ),
        LocalModelEntry(
            id = "local-gemma-4-vision-v1",
            displayName = "Local Gemma 4 vision assist",
            description = "Offline describe garment / reference photos (Ask Image class). Uses Gemma 4 E2B pack.",
            capability = AiCapability.IMAGE_GEN,
            packId = "local-gemma-4-e2b-v1",
            license = "Gemma Terms of Use",
            approxSizeLabel = "~2.6 GB (shared with Code)",
            runnable = true,
            testingNote = "Install local-gemma-4-e2b-v1 once — toggle Analyze reference in Create Advanced.",
            pickerRole = LocalModelPickerRole.QUALITY_POST,
        ),
        LocalModelEntry(
            id = "local-audio-scribe-v1",
            displayName = "Local audio scribe (STT)",
            description = "Offline speech-to-text via Gemma 4 multimodal audio — shares Code pack.",
            capability = AiCapability.AUDIO,
            packId = "local-gemma-4-e2b-v1",
            license = "Gemma Terms of Use",
            approxSizeLabel = "~2.6 GB (shared with Code)",
            runnable = false,
            testingNote = "Not working: the published local-gemma-4-e2b-v1 pack ships with audio " +
                "disabled (config.json \"audio\": false), so AndroidLocalAudioTranscriber.isReady() is " +
                "always false regardless of download state. Needs a Gemma 4 pack republished with audio " +
                "enabled and verified, or should stay off the picker until then.",
        ),
        LocalModelEntry(
            id = "local-functiongemma-v1",
            displayName = "FunctionGemma tools (experimental)",
            description = "Local tool calling for studio assists — append prompt, set tier, backdrop.",
            capability = AiCapability.CODE,
            packId = "local-functiongemma-v1",
            license = "Gemma Terms of Use",
            approxSizeLabel = "~300 MB",
            runnable = true,
            testingNote = "Experimental · pick in Code ON-DEVICE when pack installed.",
        ),
        LocalModelEntry(
            id = "local-stillclip-v1",
            displayName = "Local video still-clip",
            description = "Honest offline video: generates a local keyframe (tiny-SD) and encodes a short H.264 MP4. Not diffusion video.",
            capability = AiCapability.VIDEO,
            packId = "local-sdturbo-v1",
            license = "CreativeML OpenRAIL-M (shared image pack)",
            approxSizeLabel = "~1.06 GB (shared)",
            runnable = true,
            testingNote = "Uses local-sdturbo-v1. Airplane Video Studio yields a short still-clip MP4.",
        ),
        LocalModelEntry(
            id = "local-tts-system",
            displayName = "Device TTS (system)",
            description = "Offline speak via Android Text-to-speech (Google / OEM voices) + optional DSP knobs.",
            capability = AiCapability.AUDIO,
            packId = null,
            license = "Device TTS engine",
            approxSizeLabel = "0 (built-in)",
            runnable = true,
            testingNote = "Ready offline when a TTS language pack is installed on the phone.",
        ),
        LocalModelEntry(
            id = "local-tts-v1",
            displayName = "Local TTS neural (Kokoro / Piper)",
            description = "On-device neural TTS pack — ONNX / ExecuTorch (optional upgrade over system TTS).",
            capability = AiCapability.AUDIO,
            packId = "local-tts-v1",
            license = "Apache-2.0 (planned)",
            approxSizeLabel = "~80–300 MB",
            runnable = false,
            testingNote = "Scaffold — system TTS works today; neural pack when published.",
        ),
        LocalModelEntry(
            id = "local-voice-changer",
            displayName = "Local voice changer (DSP)",
            description = "Offline pitch / speed / formant / warmth / clarity knobs — no neural pack required.",
            capability = AiCapability.AUDIO,
            packId = null,
            license = "App DSP",
            approxSizeLabel = "0 (built-in)",
            runnable = true,
            testingNote = "Record with the mic or use device/cloud TTS, then apply knobs on-device.",
        ),
        LocalModelEntry(
            id = "local-quality-birefnet",
            displayName = "BiRefNet matting",
            description = "Open bilateral reference net for cleaner garment / person mattes before try-on.",
            capability = AiCapability.TRY_ON,
            packId = "birefnet-v1",
            license = "MIT",
            approxSizeLabel = "~224 MB",
            runnable = true,
            testingNote = "Optional · download birefnet-v1 from Model packs — post-step activates when installed.",
            pickerRole = LocalModelPickerRole.QUALITY_POST,
        ),
        LocalModelEntry(
            id = "local-quality-realesrgan",
            displayName = "Real-ESRGAN upscale",
            description = "Open 2×/4× upscaler for listing-ready stills after try-on or Create.",
            capability = AiCapability.TRY_ON,
            packId = "realesrgan-v1",
            license = "BSD-3-Clause",
            approxSizeLabel = "~5 MB",
            runnable = true,
            testingNote = "Optional quality pack — not an Image Create generator. Auto-upscale when installed.",
            pickerRole = LocalModelPickerRole.QUALITY_POST,
        ),
        LocalModelEntry(
            id = "local-quality-gfpgan",
            displayName = "GFPGAN face restore (planned)",
            description = "Open face restoration for shopper selfies and creator casting stills.",
            capability = AiCapability.TRY_ON,
            packId = null,
            license = "Apache-2.0 (planned)",
            approxSizeLabel = "~100–350 MB",
            runnable = false,
            testingNote = "Quality pack reserved: gfpgan-v1. Optional post-step after diffusion — not Image Edit.",
            pickerRole = LocalModelPickerRole.QUALITY_POST,
        ),
    )

    fun runnable(): List<LocalModelEntry> = entries.filter { it.runnable }

    /** All catalog rows tagged with [capability] (Settings / Usage). */
    fun forCapability(capability: AiCapability): List<LocalModelEntry> =
        entries.filter { it.capability == capability }

    /**
     * Create / Audio Studio ON-DEVICE list — generators and scaffolds only.
     * Excludes quality upscalers / matting packs that must not appear as Image models.
     */
    fun forStudioPicker(capability: AiCapability): List<LocalModelEntry> =
        forCapability(capability).filter {
            it.pickerRole == LocalModelPickerRole.STUDIO_GENERATOR && it.runnable
        }

    /** Honest short status for picker rows. */
    fun studioStatusLabel(entry: LocalModelEntry, packReady: Boolean): String = when {
        entry.id == "local-sdturbo-v1" && packReady -> "Ready offline"
        entry.id == "local-sdturbo-v1" && !packReady ->
            "Download local-sdturbo-v1 in Model packs (~1.06 GB)"
        entry.id == "local-sdturbo-edit" && packReady -> "Ready offline (img2img)"
        entry.id == "local-sdturbo-edit" && !packReady ->
            "Download local-sdturbo-v1 v3+ for offline edit"
        entry.id == "local-qwen3-06b-v1" && packReady -> "Ready offline · Qwen3 0.6B (fastest)"
        entry.id == "local-qwen3-06b-v1" && !packReady ->
            "Download local-qwen3-06b-v1 in Model packs (~331 MB)"
        entry.id == "local-gemma-4-e2b-v1" && packReady -> "Ready offline · Gemma 4"
        entry.id == "local-gemma-4-e2b-v1" && !packReady ->
            "Download local-gemma-4-e2b-v1 in Model packs (~2.6 GB)"
        entry.id == "local-gemma-v1" && packReady -> "Ready offline · legacy Gemma 3"
        entry.id == "local-gemma-v1" && !packReady ->
            "Download local-gemma-v1 in Model packs (~530 MB)"
        entry.id == "local-stillclip-v1" && packReady -> "Ready offline (still-clip)"
        entry.id == "local-stillclip-v1" && !packReady ->
            "Download local-sdturbo-v1 for offline still-clips"
        entry.runnable && (entry.packId == null || packReady) -> "Ready offline"
        !entry.runnable && entry.packId != null -> "Scaffold · weights not published"
        !entry.runnable -> "Coming soon · no on-device weights yet"
        else -> "Download in Settings"
    }

    /** Green-dot readiness for studio ON-DEVICE rows (may differ from catalog [LocalModelEntry.runnable]). */
    fun studioEntryReady(entry: LocalModelEntry, packReady: Boolean): Boolean = when {
        entry.id == "local-sdturbo-v1" ||
            entry.id == "local-sdturbo-edit" ||
            entry.id == "local-stillclip-v1" ||
            entry.id == "local-qwen3-06b-v1" ||
            entry.id == "local-gemma-4-e2b-v1" ||
            entry.id == "local-gemma-v1" -> packReady
        entry.id == "local-gemma-4-vision-v1" -> packReady
        entry.runnable && (entry.packId == null || packReady) -> true
        else -> false
    }

    fun byId(id: String): LocalModelEntry? = entries.firstOrNull { it.id == id }

    /** True when [id] is a studio-selectable on-device generator for [capability]. */
    fun isSelectableStudioId(id: String, capability: AiCapability): Boolean {
        val entry = byId(id) ?: return false
        return entry.capability == capability &&
            entry.pickerRole == LocalModelPickerRole.STUDIO_GENERATOR &&
            entry.runnable
    }
}
