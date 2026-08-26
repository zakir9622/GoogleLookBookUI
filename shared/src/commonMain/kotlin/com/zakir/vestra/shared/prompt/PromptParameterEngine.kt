package com.zakir.vestra.shared.prompt

import com.zakir.vestra.shared.audio.VoiceKnobs
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.effectiveCategory

/**
 * Universal Intelligent Parameter Extractor.
 * Automatically analyzes user prompts and context across all modalities (Image Gen, Image Edit,
 * Image Classification/Vision, Code, Chat, Audio/TTS, Video, Try-On) to extract optimal execution
 * parameters (aspect ratios, dimensions, steps, CFG scale, temperature, negative prompt, seeds,
 * languages, frameworks, voice knobs, camera motion, garment types, etc.).
 */
object PromptParameterEngine {

    // ─────────────────────────────────────────────────────────────────────────────
    // IMAGE GENERATION PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────────

    data class ImageParams(
        val cleanedPrompt: String,
        val width: Int,
        val height: Int,
        val aspectRatio: String,
        val steps: Int,
        val guidanceScale: Double,
        val negativePrompt: String,
        val seed: Int?,
        val stylePreset: String?,
    )

    fun extractImageParams(prompt: String, defaultSteps: Int = 4, isFastDistilled: Boolean = true): ImageParams {
        val lower = prompt.lowercase()
        var cleaned = prompt.trim()

        // 1. Aspect ratio & resolution extraction
        var width = 1024
        var height = 1024
        var aspectRatio = "1:1"

        when {
            lower.contains("16:9") || lower.contains("landscape") || lower.contains("wallpaper") ||
                lower.contains("widescreen") || lower.contains("cinematic landscape") -> {
                width = 1280
                height = 720
                aspectRatio = "16:9"
            }
            lower.contains("9:16") || lower.contains("portrait") || lower.contains("reel") ||
                lower.contains("story") || lower.contains("phone wallpaper") || lower.contains("full body") -> {
                width = 720
                height = 1280
                aspectRatio = "9:16"
            }
            lower.contains("4:3") -> {
                width = 1024
                height = 768
                aspectRatio = "4:3"
            }
            lower.contains("3:4") -> {
                width = 768
                height = 1024
                aspectRatio = "3:4"
            }
            lower.contains("3:2") -> {
                width = 1080
                height = 720
                aspectRatio = "3:2"
            }
            lower.contains("2:3") -> {
                width = 720
                height = 1080
                aspectRatio = "2:3"
            }
            lower.contains("square") || lower.contains("1:1") || lower.contains("avatar") || lower.contains("icon") -> {
                width = 1024
                height = 1024
                aspectRatio = "1:1"
            }
        }

        // Custom dimension regex e.g. "1200x800" or "--res 768x1024" or "--ar 16:9"
        val customResMatch = Regex("""(\d{3,4})\s*[xX*]\s*(\d{3,4})""").find(cleaned)
        if (customResMatch != null) {
            val w = customResMatch.groupValues[1].toIntOrNull()
            val h = customResMatch.groupValues[2].toIntOrNull()
            if (w != null && h != null && w in 256..2048 && h in 256..2048) {
                width = (w / 8) * 8
                height = (h / 8) * 8
                aspectRatio = "${width}:${height}"
                cleaned = cleaned.replace(customResMatch.value, "").trim()
            }
        }

        // 2. Step count extraction
        var steps = defaultSteps
        val stepMatch = Regex("""(?:steps?|--steps?)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(cleaned)
        if (stepMatch != null) {
            val s = stepMatch.groupValues[1].toIntOrNull()
            if (s != null && s in 1..60) {
                steps = s
                cleaned = cleaned.replace(stepMatch.value, "").trim()
            }
        } else if (!isFastDistilled) {
            steps = when {
                lower.contains("masterpiece") || lower.contains("highly detailed") || lower.contains("8k") ||
                    lower.contains("photorealistic") || lower.contains("studio lighting") -> 30
                lower.contains("fast") || lower.contains("quick") || lower.contains("draft") -> 12
                else -> 20
            }
        }

        // 3. Guidance scale (CFG) extraction
        var guidanceScale = if (isFastDistilled) 1.0 else 7.0
        val cfgMatch = Regex("""(?:cfg|guidance|--cfg)\s*[:=]?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(cleaned)
        if (cfgMatch != null) {
            val c = cfgMatch.groupValues[1].toDoubleOrNull()
            if (c != null && c in 0.5..20.0) {
                guidanceScale = c
                cleaned = cleaned.replace(cfgMatch.value, "").trim()
            }
        } else if (!isFastDistilled) {
            guidanceScale = when {
                lower.contains("photorealistic") || lower.contains("raw photo") -> 7.5
                lower.contains("artistic") || lower.contains("dreamy") -> 5.5
                else -> 7.0
            }
        }

        // 4. Seed extraction
        var seed: Int? = null
        val seedMatch = Regex("""(?:seed|--seed)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(cleaned)
        if (seedMatch != null) {
            seed = seedMatch.groupValues[1].toIntOrNull()
            cleaned = cleaned.replace(seedMatch.value, "").trim()
        }

        // 5. Negative prompt extraction
        var negativePrompt = ""
        val negMatch = Regex("""(?:negative|--neg|--negative|without|no)\s*[:=]\s*([^,;.\n]+(?:,[^,;.\n]+)*)""", RegexOption.IGNORE_CASE).find(cleaned)
        if (negMatch != null) {
            negativePrompt = negMatch.groupValues[1].trim()
            cleaned = cleaned.replace(negMatch.value, "").trim()
        } else {
            negativePrompt = "blurry, distorted, low quality, bad anatomy, deformed limbs, watermark, text, out of frame"
        }

        // 6. Style preset detection
        val stylePreset = when {
            lower.contains("editorial") || lower.contains("vogue") || lower.contains("lookbook") -> "Editorial"
            lower.contains("couture") || lower.contains("atelier") || lower.contains("luxury") -> "Haute Couture"
            lower.contains("cinematic") || lower.contains("movie shot") -> "Cinematic"
            lower.contains("portrait") || lower.contains("headshot") -> "Studio Portrait"
            lower.contains("vintage") || lower.contains("analog") || lower.contains("35mm") -> "Vintage 35mm"
            lower.contains("cyberpunk") || lower.contains("neon") -> "Cyberpunk"
            lower.contains("watercolor") || lower.contains("illustration") -> "Artistic Illustration"
            else -> null
        }

        return ImageParams(
            cleanedPrompt = cleaned.trim().trimEnd(',', ';', '-'),
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            steps = steps,
            guidanceScale = guidanceScale,
            negativePrompt = negativePrompt,
            seed = seed,
            stylePreset = stylePreset,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // IMAGE EDIT PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────────

    enum class EditIntent {
        RECOLOR,
        CHANGE_FABRIC,
        ADD_ELEMENT,
        REMOVE_ELEMENT,
        REPLACE,
        STYLE_TRANSFER,
        LIGHTING_CHANGE,
        GENERAL_EDIT,
    }

    data class ImageEditParams(
        val cleanedPrompt: String,
        val intent: EditIntent,
        val textGuidance: Double,
        val imageGuidance: Double,
        val steps: Int,
        val seed: Int?,
    )

    fun extractImageEditParams(prompt: String): ImageEditParams {
        val lower = prompt.lowercase()
        var cleaned = prompt.trim()

        val intent = when {
            lower.startsWith("recolor") || lower.contains("change color to") || lower.contains("color of") || lower.contains("turn into red") || lower.contains("make it blue") -> EditIntent.RECOLOR
            lower.contains("fabric") || lower.contains("silk") || lower.contains("linen") || lower.contains("velvet") || lower.contains("cotton") -> EditIntent.CHANGE_FABRIC
            lower.startsWith("add") || lower.contains("put on") || lower.contains("include") -> EditIntent.ADD_ELEMENT
            lower.startsWith("remove") || lower.contains("delete") || lower.contains("without") || lower.contains("get rid of") -> EditIntent.REMOVE_ELEMENT
            lower.startsWith("replace") || lower.contains("switch") || lower.contains("swap") -> EditIntent.REPLACE
            lower.contains("lighting") || lower.contains("golden hour") || lower.contains("studio light") || lower.contains("night") -> EditIntent.LIGHTING_CHANGE
            lower.contains("style") || lower.contains("painting") || lower.contains("sketch") || lower.contains("anime") -> EditIntent.STYLE_TRANSFER
            else -> EditIntent.GENERAL_EDIT
        }

        var textGuidance = when (intent) {
            EditIntent.RECOLOR, EditIntent.LIGHTING_CHANGE -> 6.5
            EditIntent.ADD_ELEMENT, EditIntent.REPLACE -> 8.0
            EditIntent.CHANGE_FABRIC -> 7.5
            else -> 7.5
        }
        var imageGuidance = when (intent) {
            EditIntent.RECOLOR, EditIntent.LIGHTING_CHANGE -> 1.8
            EditIntent.ADD_ELEMENT, EditIntent.REPLACE -> 1.3
            else -> 1.5
        }

        var steps = 8
        val stepMatch = Regex("""(?:steps?|--steps?)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(cleaned)
        if (stepMatch != null) {
            stepMatch.groupValues[1].toIntOrNull()?.let { if (it in 4..40) steps = it }
            cleaned = cleaned.replace(stepMatch.value, "").trim()
        }

        var seed: Int? = null
        val seedMatch = Regex("""(?:seed|--seed)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(cleaned)
        if (seedMatch != null) {
            seed = seedMatch.groupValues[1].toIntOrNull()
            cleaned = cleaned.replace(seedMatch.value, "").trim()
        }

        return ImageEditParams(
            cleanedPrompt = cleaned,
            intent = intent,
            textGuidance = textGuidance,
            imageGuidance = imageGuidance,
            steps = steps,
            seed = seed,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // VISION & CLASSIFICATION PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────────

    enum class VisionTask {
        GARMENT_CLASSIFICATION,
        COLOR_PALETTE,
        FABRIC_TEXTURE,
        STYLE_ANALYSIS,
        IMAGE_CAPTION,
        VISUAL_QA,
    }

    data class VisionParams(
        val task: VisionTask,
        val promptDirective: String,
        val expectedFormat: String,
    )

    fun extractVisionParams(prompt: String): VisionParams {
        val lower = prompt.lowercase()
        return when {
            lower.contains("classify") || lower.contains("garment type") || lower.contains("category") || lower.contains("what is this clothing") ->
                VisionParams(
                    task = VisionTask.GARMENT_CLASSIFICATION,
                    promptDirective = "Identify and classify the garment category, silhouette, neckline, and structure precisely.",
                    expectedFormat = "CATEGORY: <garment_type>\nSILHOUETTE: <fit>\nDETAILS: <key_features>",
                )
            lower.contains("color") || lower.contains("palette") || lower.contains("shade") || lower.contains("hex") ->
                VisionParams(
                    task = VisionTask.COLOR_PALETTE,
                    promptDirective = "Extract the primary color, secondary accents, undertones, and palette mood of this image.",
                    expectedFormat = "PRIMARY: <color>\nACCENTS: <colors>\nMOOD: <palette_mood>",
                )
            lower.contains("fabric") || lower.contains("material") || lower.contains("texture") || lower.contains("weave") ->
                VisionParams(
                    task = VisionTask.FABRIC_TEXTURE,
                    promptDirective = "Analyze the fabric type, drape, surface texture, weave, and embroidery details.",
                    expectedFormat = "FABRIC: <type>\nTEXTURE: <drape_and_finish>\nEMBELLISHMENTS: <details>",
                )
            lower.contains("caption") || lower.contains("describe") || lower.contains("summary") ->
                VisionParams(
                    task = VisionTask.IMAGE_CAPTION,
                    promptDirective = "Provide a high-fidelity, detailed description suitable for generative recreation and lookbook cataloging.",
                    expectedFormat = "A concise 2-3 sentence visual summary followed by specific stylistic tags.",
                )
            else ->
                VisionParams(
                    task = VisionTask.VISUAL_QA,
                    promptDirective = prompt,
                    expectedFormat = "Direct, precise answer addressing the query.",
                )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CODE GENERATION PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────────

    data class CodeParams(
        val language: String,
        val framework: String?,
        val temperature: Double,
        val systemPrompt: String,
        val cleanedPrompt: String,
    )

    fun extractCodeParams(prompt: String): CodeParams {
        val lower = prompt.lowercase()
        val cleaned = prompt.trim()

        val language = when {
            lower.contains("kotlin") || lower.contains(".kt") || lower.contains("compose") -> "Kotlin"
            lower.contains("python") || lower.contains("django") || lower.contains("flask") || lower.contains("pytorch") -> "Python"
            lower.contains("typescript") || lower.contains("ts") || lower.contains("react") || lower.contains("next.js") -> "TypeScript"
            lower.contains("javascript") || lower.contains("js") || lower.contains("node") -> "JavaScript"
            lower.contains("swift") || lower.contains("swiftui") || lower.contains("ios") -> "Swift"
            lower.contains("rust") || lower.contains("cargo") -> "Rust"
            lower.contains("sql") || lower.contains("postgres") || lower.contains("sqlite") || lower.contains("room query") -> "SQL"
            lower.contains("c++") || lower.contains("cpp") -> "C++"
            lower.contains("java") -> "Java"
            else -> "Kotlin" // Vestra native default
        }

        val framework = when {
            lower.contains("compose") || lower.contains("jetpack") -> "Jetpack Compose"
            lower.contains("ktor") -> "Ktor Client/Server"
            lower.contains("coroutines") || lower.contains("flow") -> "Kotlin Coroutines & StateFlow"
            lower.contains("room") -> "Android Room ORM"
            lower.contains("onnx") || lower.contains("ort") -> "ONNX Runtime Mobile"
            lower.contains("react") -> "React"
            lower.contains("spring") -> "Spring Boot"
            else -> null
        }

        val isStrictCode = lower.startsWith("write") || lower.startsWith("implement") ||
            lower.startsWith("code") || lower.startsWith("function") || lower.startsWith("class") ||
            lower.contains("fix bug") || lower.contains("refactor") || lower.contains("unit test")

        val temperature = when {
            lower.contains("explain") || lower.contains("why") || lower.contains("how does") -> 0.35
            lower.contains("brainstorm") || lower.contains("ideas") || lower.contains("design") -> 0.65
            isStrictCode -> 0.15
            else -> 0.25
        }

        val systemPrompt = buildString {
            append("You are an expert $language software engineer and software architect. ")
            if (framework != null) {
                append("Specialize in $framework with modern clean architecture, idiomatic syntax, and production patterns. ")
            }
            append("Provide working, complete, robust code. Use Markdown code blocks with language identifiers. Avoid placeholders or truncated code.")
        }

        return CodeParams(
            language = language,
            framework = framework,
            temperature = temperature,
            systemPrompt = systemPrompt,
            cleanedPrompt = cleaned,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // AUDIO / VOICE PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────────

    data class AudioParams(
        val cleanedText: String,
        val voiceId: String,
        val speed: Float,
        val pitchSemitones: Float,
        val emotion: String,
    )

    fun extractAudioParams(
        text: String,
        currentVoiceId: String = "af_heart",
        currentKnobs: VoiceKnobs = VoiceKnobs(),
    ): AudioParams {
        val lower = text.lowercase()
        var cleaned = text.trim()

        var speed = currentKnobs.speed
        when {
            lower.contains("slow") || lower.contains("slowly") || lower.contains("calm pace") -> speed = 0.85f
            lower.contains("fast") || lower.contains("quickly") || lower.contains("rapid") -> speed = 1.25f
            lower.contains("0.8x") -> speed = 0.8f
            lower.contains("1.2x") -> speed = 1.2f
            lower.contains("1.5x") -> speed = 1.5f
        }

        var pitch = currentKnobs.pitchSemitones
        when {
            lower.contains("high pitch") || lower.contains("higher tone") -> pitch = (pitch + 3f).coerceIn(-12f, 12f)
            lower.contains("deep voice") || lower.contains("low tone") || lower.contains("gravelly") -> pitch = (pitch - 3f).coerceIn(-12f, 12f)
        }

        val emotion = when {
            lower.contains("whisper") || lower.contains("softly") -> "whisper"
            lower.contains("energetic") || lower.contains("excited") || lower.contains("enthusiastic") -> "energetic"
            lower.contains("formal") || lower.contains("news") || lower.contains("broadcast") -> "formal"
            lower.contains("calm") || lower.contains("meditative") || lower.contains("gentle") -> "calm"
            lower.contains("dramatic") || lower.contains("storytelling") -> "dramatic"
            else -> "neutral"
        }

        var voice = currentVoiceId
        when {
            lower.contains("male voice") || lower.contains("man speaking") || lower.contains("guy") -> {
                voice = if (currentVoiceId.contains("Edge", ignoreCase = true) || currentVoiceId.contains("en-US", ignoreCase = true)) {
                    "en-US-GuyNeural - en-US (Male)"
                } else {
                    "am_adam"
                }
            }
            lower.contains("female voice") || lower.contains("woman speaking") || lower.contains("girl") -> {
                voice = if (currentVoiceId.contains("Edge", ignoreCase = true) || currentVoiceId.contains("en-US", ignoreCase = true)) {
                    "en-US-JennyNeural - en-US (Female)"
                } else {
                    "af_heart"
                }
            }
            lower.contains("british") || lower.contains("uk accent") -> {
                voice = "en-GB-SoniaNeural - en-GB (Female)"
            }
            lower.contains("arabic") -> {
                voice = "ar-SA-ZariyahNeural - ar-SA (Female)"
            }
            lower.contains("urdu") || lower.contains("hindi") -> {
                voice = "ur-PK-UzmaNeural - ur-PK (Female)"
            }
        }

        return AudioParams(
            cleanedText = cleaned,
            voiceId = voice,
            speed = speed,
            pitchSemitones = pitch,
            emotion = emotion,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // VIDEO GENERATION PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────────

    data class VideoParams(
        val cleanedPrompt: String,
        val cameraMotion: String,
        val width: Int,
        val height: Int,
        val durationSeconds: Double,
        val frames: Int,
        val steps: Int,
        val cfg: Double,
        val negativePrompt: String,
    )

    fun extractVideoParams(prompt: String): VideoParams {
        val lower = prompt.lowercase()
        var cleaned = prompt.trim()

        val motion = when {
            lower.contains("pan left") || lower.contains("pan right") || lower.contains("slow pan") -> "slow pan"
            lower.contains("zoom in") || lower.contains("zoom out") || lower.contains("push in") -> "zoom in"
            lower.contains("drone") || lower.contains("aerial") -> "aerial sweep"
            lower.contains("orbit") || lower.contains("rotate around") || lower.contains("360") -> "360 orbit"
            lower.contains("tracking") || lower.contains("follow") || lower.contains("walking") -> "tracking shot"
            lower.contains("static") || lower.contains("still camera") || lower.contains("locked off") -> "static camera"
            else -> "cinematic motion"
        }

        var width = 704
        var height = 512
        when {
            lower.contains("16:9") || lower.contains("landscape") || lower.contains("widescreen") || lower.contains("cinematic") -> {
                width = 832
                height = 480
            }
            lower.contains("9:16") || lower.contains("portrait") || lower.contains("reel") || lower.contains("vertical") || lower.contains("story") -> {
                width = 480
                height = 832
            }
            lower.contains("square") || lower.contains("1:1") -> {
                width = 512
                height = 512
            }
        }

        var duration = 2.0
        when {
            lower.contains("3s") || lower.contains("3 seconds") -> duration = 3.0
            lower.contains("4s") || lower.contains("4 seconds") -> duration = 4.0
            lower.contains("5s") || lower.contains("5 seconds") -> duration = 5.0
        }
        val frames = (duration * 16).toInt().coerceIn(17, 49)

        return VideoParams(
            cleanedPrompt = cleaned,
            cameraMotion = motion,
            width = width,
            height = height,
            durationSeconds = duration,
            frames = frames,
            steps = 25,
            cfg = 5.0,
            negativePrompt = "worst quality, inconsistent motion, blurry, jittery, distorted, watermark, text",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // VIRTUAL TRY-ON PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────────

    data class TryOnParams(
        val category: GarmentCategory,
        val clothType: String,
        val garmentDesc: String,
        val steps: Int,
        val cfg: Double,
        val seed: Int,
        val preserveFace: Boolean,
        val preserveBackground: Boolean,
    )

    fun extractTryOnParams(
        promptOrHint: String? = null,
        explicitCategory: GarmentCategory? = null,
        defaultSteps: Int = 30,
    ): TryOnParams {
        val lower = (promptOrHint ?: "").lowercase()

        val category = explicitCategory?.effectiveCategory() ?: when {
            lower.contains("abaya") -> GarmentCategory.ABAYA
            lower.contains("hijab") || lower.contains("scarf") || lower.contains("headscarf") -> GarmentCategory.HIJAB
            lower.contains("niqab") -> GarmentCategory.NIQAB
            lower.contains("dupatta") -> GarmentCategory.DUPATTA
            lower.contains("jilbab") -> GarmentCategory.JILBAB
            lower.contains("kaftan") || lower.contains("caftan") -> GarmentCategory.KAFTAN
            lower.contains("shalwar") || lower.contains("kameez") || lower.contains("kurta") -> GarmentCategory.SHALWAR_KAMEEZ
            lower.contains("lehenga") || lower.contains("sari") || lower.contains("saree") -> GarmentCategory.LEHENGA
            lower.contains("pants") || lower.contains("skirt") || lower.contains("trousers") || lower.contains("jeans") -> GarmentCategory.LOWER_BODY
            lower.contains("shirt") || lower.contains("blouse") || lower.contains("top") || lower.contains("jacket") || lower.contains("hoodie") -> GarmentCategory.UPPER_BODY
            lower.contains("dress") || lower.contains("gown") -> GarmentCategory.DRESS
            else -> GarmentCategory.ABAYA
        }

        val clothType = when (category) {
            GarmentCategory.LOWER_BODY -> "lower"
            GarmentCategory.ABAYA, GarmentCategory.JILBAB, GarmentCategory.KAFTAN,
            GarmentCategory.DRESS, GarmentCategory.LEHENGA, GarmentCategory.FULL_COVERAGE,
            GarmentCategory.SHALWAR_KAMEEZ -> "overall"
            else -> "upper"
        }

        val garmentDesc = when (category) {
            GarmentCategory.LOWER_BODY -> "Lower-body garment"
            GarmentCategory.HIJAB, GarmentCategory.NIQAB, GarmentCategory.DUPATTA, GarmentCategory.HEADSCARF ->
                "Upper-body / head covering"
            GarmentCategory.ABAYA, GarmentCategory.JILBAB, GarmentCategory.KAFTAN, GarmentCategory.SHALWAR_KAMEEZ ->
                "Modest couture full silhouette garment"
            else -> "Dress / fashion garment"
        }

        val steps = when {
            lower.contains("fast") || lower.contains("quick") -> 20
            lower.contains("detailed") || lower.contains("high quality") -> 35
            else -> defaultSteps
        }

        return TryOnParams(
            category = category,
            clothType = clothType,
            garmentDesc = garmentDesc,
            steps = steps,
            cfg = 2.5,
            seed = 42,
            preserveFace = true,
            preserveBackground = true,
        )
    }

    /**
     * Extracts a list of concise UI badge descriptions highlighting parameters
     * automatically derived from the prompt across modalities.
     */
    fun detectParameterBadges(prompt: String): List<String> {
        if (prompt.isBlank()) return emptyList()
        val badges = mutableListOf<String>()
        val lower = prompt.lowercase()

        // Aspect ratio
        if (lower.contains("16:9") || lower.contains("landscape") || lower.contains("wallpaper")) {
            badges.add("16:9 (1280x720)")
        } else if (lower.contains("9:16") || lower.contains("portrait") || lower.contains("reel") || lower.contains("story")) {
            badges.add("9:16 (720x1280)")
        } else if (lower.contains("4:3")) {
            badges.add("4:3 (1024x768)")
        } else if (lower.contains("3:4")) {
            badges.add("3:4 (768x1024)")
        }

        // Steps
        val stepsMatch = Regex("""(?:--steps|-s|steps:|steps\s+)(\d+)""").find(lower)
        if (stepsMatch != null) {
            badges.add("${stepsMatch.groupValues[1]} steps")
        } else if (lower.contains("ultra detailed") || lower.contains("masterpiece") || lower.contains("8k")) {
            badges.add("High fidelity steps")
        }

        // CFG
        val cfgMatch = Regex("""(?:--cfg|-c|cfg:|guidance:?)\s*(\d+(?:\.\d+)?)""").find(lower)
        if (cfgMatch != null) {
            badges.add("CFG ${cfgMatch.groupValues[1]}")
        }

        // Seed
        val seedMatch = Regex("""(?:--seed|-seed|seed:|seed\s+)(\d+)""").find(lower)
        if (seedMatch != null) {
            badges.add("Seed ${seedMatch.groupValues[1]}")
        }

        // Coding language/framework
        when {
            lower.contains("kotlin") -> badges.add("Kotlin")
            lower.contains("compose") -> badges.add("Jetpack Compose")
            lower.contains("python") -> badges.add("Python")
            lower.contains("rust") -> badges.add("Rust")
            lower.contains("typescript") -> badges.add("TypeScript")
            lower.contains("swift") -> badges.add("Swift")
        }

        // Audio voice/speed
        val speedMatch = Regex("""(\d(?:\.\d+)?)x\s*(?:speed)?""").find(lower)
        if (speedMatch != null) {
            badges.add("${speedMatch.groupValues[1]}x speed")
        }

        // Video motion
        when {
            lower.contains("slow motion") || lower.contains("slow-mo") -> badges.add("Slow motion")
            lower.contains("drone") || lower.contains("aerial") -> badges.add("Aerial drone")
            lower.contains("cinematic") -> badges.add("Cinematic 24fps")
        }

        return badges.distinct()
    }
}
