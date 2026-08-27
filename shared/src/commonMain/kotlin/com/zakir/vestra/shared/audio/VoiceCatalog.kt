package com.zakir.vestra.shared.audio

/**
 * Named voice personas for Audio Studio — different persons / varieties.
 * Cloud TTS maps [cloudVoiceId] / [edgeVoiceLabel]; local packs map [localSpeakerId] when wired.
 */
data class VoicePersona(
    val id: String,
    val displayName: String,
    val description: String,
    val variety: VoiceVariety,
    /** Kokoro-style speaker id (e.g. af_heart). */
    val cloudVoiceId: String,
    /** Edge-TTS dropdown label as served by innoai Edge-TTS Space. */
    val edgeVoiceLabel: String,
    val localSpeakerId: String = id,
)

enum class VoiceVariety {
    FEMALE_WARM,
    FEMALE_BRIGHT,
    FEMALE_SOFT,
    FEMALE_ELEGANT,
    MALE_BARITONE,
    MALE_TENOR,
    MALE_DEEP,
    NEUTRAL,
    STORYTELLER,
    ANNOUNCER,
    CINEMATIC,
    WHISPER,
}

enum class VoiceCategory(val displayName: String, val emoji: String) {
    ALL("All Voices", "✨"),
    HUMAN("Real Human", "👤"),
    CUSTOM("Custom Samples", "🎙️"),
    CHARACTER("Characters", "🎭"),
    EFFECTS("FX & Filters", "⚡"),
}

data class VoiceEffectPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val knobs: VoiceKnobs,
    val iconEmoji: String = "✨",
    val category: VoiceCategory = VoiceCategory.CHARACTER,
)

object VoicePresets {
    val presets: List<VoiceEffectPreset> = listOf(
        // Real Human Voices
        VoiceEffectPreset(
            id = "natural",
            displayName = "Natural Voice",
            description = "Original natural pitch and balanced studio clarity",
            knobs = VoiceKnobs.Default,
            iconEmoji = "🎙️",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "grandpa",
            displayName = "Grandpa / Old Man",
            description = "Aged male vocal tract with warm rasp and subtle natural vocal tremor",
            knobs = VoiceKnobs(
                pitchSemitones = -4.5f,
                speed = 0.93f,
                formant = 0.82f,
                warmth = 0.88f,
                clarity = 0.52f,
                tremorRateHz = 5.2f,
                tremorDepth = 0.22f,
                raspyMidGain = 0.45f,
            ),
            iconEmoji = "👴",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "grandma",
            displayName = "Grandma / Old Lady",
            description = "Gentle mature female resonance with cozy warmth and subtle age flutter",
            knobs = VoiceKnobs(
                pitchSemitones = 1.8f,
                speed = 0.94f,
                formant = 1.06f,
                warmth = 0.90f,
                clarity = 0.58f,
                tremorRateHz = 5.8f,
                tremorDepth = 0.16f,
                raspyMidGain = 0.25f,
            ),
            iconEmoji = "👵",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "young_girl",
            displayName = "Young Girl",
            description = "Bright youthful female timbre with clear harmonics and airy presence",
            knobs = VoiceKnobs(
                pitchSemitones = 5.2f,
                speed = 1.04f,
                formant = 1.25f,
                warmth = 0.35f,
                clarity = 0.92f,
            ),
            iconEmoji = "👧",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "young_boy",
            displayName = "Young Boy / Kid",
            description = "Energetic youthful child vocal tract with compact resonance",
            knobs = VoiceKnobs(
                pitchSemitones = 6.8f,
                speed = 1.06f,
                formant = 1.28f,
                warmth = 0.40f,
                clarity = 0.85f,
            ),
            iconEmoji = "👦",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "deep_male",
            displayName = "Deep Adult Male",
            description = "Rich chest resonance with authoritative baritone low-end presence",
            knobs = VoiceKnobs(
                pitchSemitones = -4.2f,
                speed = 0.97f,
                formant = 0.88f,
                warmth = 0.92f,
                clarity = 0.65f,
                raspyMidGain = 0.20f,
            ),
            iconEmoji = "👨",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "mature_woman",
            displayName = "Mature Woman",
            description = "Smooth velvety female speech with warm midrange resonance",
            knobs = VoiceKnobs(
                pitchSemitones = 3.4f,
                speed = 1.0f,
                formant = 1.14f,
                warmth = 0.65f,
                clarity = 0.82f,
            ),
            iconEmoji = "👩",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "toddler",
            displayName = "Cute Toddler",
            description = "High cheerful baby / toddler pitch with brisk tempo",
            knobs = VoiceKnobs(
                pitchSemitones = 9.2f,
                speed = 1.12f,
                formant = 1.45f,
                warmth = 0.25f,
                clarity = 0.88f,
            ),
            iconEmoji = "👶",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "whisper",
            displayName = "Intimate Whisper",
            description = "Soft breathy vocal profile with high presence and attenuated bass",
            knobs = VoiceKnobs(
                pitchSemitones = 1.5f,
                speed = 0.95f,
                formant = 1.15f,
                warmth = 0.20f,
                clarity = 0.95f,
                breathiness = 0.65f,
            ),
            iconEmoji = "🤫",
            category = VoiceCategory.HUMAN,
        ),
        VoiceEffectPreset(
            id = "studio_announcer",
            displayName = "Broadcast Host",
            description = "Punchy upfront commercial vocal presence with balanced warmth",
            knobs = VoiceKnobs(
                pitchSemitones = -1.2f,
                speed = 1.02f,
                formant = 1.0f,
                warmth = 0.70f,
                clarity = 0.85f,
            ),
            iconEmoji = "🎙️",
            category = VoiceCategory.HUMAN,
        ),

        // Characters & Fantasy
        VoiceEffectPreset(
            id = "chipmunk",
            displayName = "Chipmunk / Helium",
            description = "High pitch + brisk tempo for playful animated character voices",
            knobs = VoiceKnobs(pitchSemitones = 8.5f, speed = 1.25f, formant = 1.35f, warmth = 0.2f, clarity = 0.9f),
            iconEmoji = "🐿️",
            category = VoiceCategory.CHARACTER,
        ),
        VoiceEffectPreset(
            id = "deep_bass",
            displayName = "Deep Baritone",
            description = "Heavy resonance with rich warmth and saturated low-end",
            knobs = VoiceKnobs(pitchSemitones = -7.5f, speed = 0.92f, formant = 0.75f, warmth = 0.95f, clarity = 0.45f),
            iconEmoji = "📻",
            category = VoiceCategory.CHARACTER,
        ),
        VoiceEffectPreset(
            id = "titan",
            displayName = "Cinematic Titan",
            description = "Heavy theatrical presence with maximum pitch shift down",
            knobs = VoiceKnobs(pitchSemitones = -11.0f, speed = 0.85f, formant = 0.65f, warmth = 1.0f, clarity = 0.35f),
            iconEmoji = "🗿",
            category = VoiceCategory.CHARACTER,
        ),
        VoiceEffectPreset(
            id = "cyber_robot",
            displayName = "Cyber Robot",
            description = "Synthetic metallic tone with sharp high-frequency clarity",
            knobs = VoiceKnobs(pitchSemitones = -2.0f, speed = 1.05f, formant = 0.9f, warmth = 0.1f, clarity = 1.0f),
            iconEmoji = "🤖",
            category = VoiceCategory.CHARACTER,
        ),
        VoiceEffectPreset(
            id = "sprite_fairy",
            displayName = "Fairy / Sprite",
            description = "Bright crystalline airy high vocal formant",
            knobs = VoiceKnobs(pitchSemitones = 10.0f, speed = 1.18f, formant = 1.4f, warmth = 0.15f, clarity = 0.95f),
            iconEmoji = "🧚",
            category = VoiceCategory.CHARACTER,
        ),
        VoiceEffectPreset(
            id = "monster_shadow",
            displayName = "Shadow Entity",
            description = "Gravelly underworld resonance with heavy formant drop and echo",
            knobs = VoiceKnobs(pitchSemitones = -12.0f, speed = 0.8f, formant = 0.55f, warmth = 0.9f, clarity = 0.2f),
            iconEmoji = "👹",
            category = VoiceCategory.CHARACTER,
        ),
        VoiceEffectPreset(
            id = "alien",
            displayName = "Alien Vocalizer",
            description = "Altered extraterrestrial timbre and shifted harmonic envelope",
            knobs = VoiceKnobs(pitchSemitones = 5.0f, speed = 0.9f, formant = 0.7f, warmth = 0.5f, clarity = 0.8f),
            iconEmoji = "👽",
            category = VoiceCategory.CHARACTER,
        ),
        VoiceEffectPreset(
            id = "giant_ogre",
            displayName = "Mountain Giant",
            description = "Colossal deep cavernous voice with thunderous bass",
            knobs = VoiceKnobs(pitchSemitones = -10.0f, speed = 0.88f, formant = 0.6f, warmth = 0.95f, clarity = 0.3f),
            iconEmoji = "🌋",
            category = VoiceCategory.CHARACTER,
        ),

        // FX & Filters
        VoiceEffectPreset(
            id = "vintage_radio",
            displayName = "Vintage Tube Radio",
            description = "Warm mid-range bandpass emulation with analog tube saturation",
            knobs = VoiceKnobs(pitchSemitones = 0.5f, speed = 1.0f, formant = 1.08f, warmth = 0.85f, clarity = 0.8f),
            iconEmoji = "📻",
            category = VoiceCategory.EFFECTS,
        ),
        VoiceEffectPreset(
            id = "echo_cave",
            displayName = "Echo Cave",
            description = "Expansive cavern reverberation with spatial delay repeats",
            knobs = VoiceKnobs(pitchSemitones = -1.0f, speed = 0.95f, formant = 0.95f, warmth = 0.6f, clarity = 0.8f),
            iconEmoji = "🏔️",
            category = VoiceCategory.EFFECTS,
        ),
        VoiceEffectPreset(
            id = "walkie_talkie",
            displayName = "Walkie Talkie",
            description = "Narrow communications channel with high-end boost",
            knobs = VoiceKnobs(pitchSemitones = 2.0f, speed = 1.0f, formant = 1.2f, warmth = 0.2f, clarity = 0.9f),
            iconEmoji = "📡",
            category = VoiceCategory.EFFECTS,
        ),
        VoiceEffectPreset(
            id = "telephone",
            displayName = "Vintage Telephone",
            description = "Classic narrow bandpass landline audio filter (300Hz - 3.4kHz)",
            knobs = VoiceKnobs(pitchSemitones = 1.0f, speed = 1.0f, formant = 1.1f, warmth = 0.1f, clarity = 0.95f),
            iconEmoji = "☎️",
            category = VoiceCategory.EFFECTS,
        ),
        VoiceEffectPreset(
            id = "space_droid",
            displayName = "Space Droid",
            description = "Cosmic android communicator with crisp high harmonics & echo",
            knobs = VoiceKnobs(pitchSemitones = -3.5f, speed = 1.1f, formant = 0.85f, warmth = 0.2f, clarity = 1.0f),
            iconEmoji = "🛸",
            category = VoiceCategory.EFFECTS,
        ),
        VoiceEffectPreset(
            id = "speed_up",
            displayName = "Fast Forward (1.5×)",
            description = "Sped-up upbeat tempo with lively energy",
            knobs = VoiceKnobs(pitchSemitones = 3.0f, speed = 1.5f, formant = 1.15f, warmth = 0.4f, clarity = 0.8f),
            iconEmoji = "⚡",
            category = VoiceCategory.EFFECTS,
        ),
        VoiceEffectPreset(
            id = "slow_motion",
            displayName = "Slow-Mo Baritone",
            description = "Dramatic cinematic slow-motion resonance",
            knobs = VoiceKnobs(pitchSemitones = -5.0f, speed = 0.72f, formant = 0.85f, warmth = 0.85f, clarity = 0.6f),
            iconEmoji = "⏳",
            category = VoiceCategory.EFFECTS,
        ),
    )

    fun byId(id: String): VoiceEffectPreset =
        presets.firstOrNull { it.id == id } ?: presets.first()
}

object VoiceCatalog {
    val personas: List<VoicePersona> = listOf(
        VoicePersona(
            id = "amina",
            displayName = "Amina",
            description = "Warm mezzo — modest fashion narration & couture lookbooks",
            variety = VoiceVariety.FEMALE_WARM,
            cloudVoiceId = "af_heart",
            edgeVoiceLabel = "en-US-AvaNeural - en-US (Female)",
        ),
        VoicePersona(
            id = "noor",
            displayName = "Noor",
            description = "Bright clear voice — product showcases & catalog listings",
            variety = VoiceVariety.FEMALE_BRIGHT,
            cloudVoiceId = "af_bella",
            edgeVoiceLabel = "en-US-JennyNeural - en-US (Female)",
        ),
        VoicePersona(
            id = "layla",
            displayName = "Layla",
            description = "Soft alto — calm atelier reads & serene narration",
            variety = VoiceVariety.FEMALE_SOFT,
            cloudVoiceId = "af_sarah",
            edgeVoiceLabel = "en-GB-SoniaNeural - en-GB (Female)",
        ),
        VoicePersona(
            id = "zara",
            displayName = "Zara",
            description = "Luxury soprano — runway showcases & high-fashion editorials",
            variety = VoiceVariety.FEMALE_ELEGANT,
            cloudVoiceId = "af_kore",
            edgeVoiceLabel = "en-US-EmmaNeural - en-US (Female)",
        ),
        VoicePersona(
            id = "yasir",
            displayName = "Yasir",
            description = "Warm baritone — brand films & cinematic introductions",
            variety = VoiceVariety.MALE_BARITONE,
            cloudVoiceId = "am_adam",
            edgeVoiceLabel = "en-US-GuyNeural - en-US (Male)",
        ),
        VoicePersona(
            id = "omar",
            displayName = "Omar",
            description = "Light tenor — short social clips & dynamic lifestyle shorts",
            variety = VoiceVariety.MALE_TENOR,
            cloudVoiceId = "am_michael",
            edgeVoiceLabel = "en-US-BrianNeural - en-US (Male)",
        ),
        VoicePersona(
            id = "tariq",
            displayName = "Tariq",
            description = "Deep bass resonance — documentary narration & heritage stories",
            variety = VoiceVariety.MALE_DEEP,
            cloudVoiceId = "am_eric",
            edgeVoiceLabel = "en-US-DavisNeural - en-US (Male)",
        ),
        VoicePersona(
            id = "sam",
            displayName = "Sam",
            description = "Neutral mid — UI walkthroughs, captions and software demos",
            variety = VoiceVariety.NEUTRAL,
            cloudVoiceId = "af_nicole",
            edgeVoiceLabel = "en-US-AndrewNeural - en-US (Male)",
        ),
        VoicePersona(
            id = "rana",
            displayName = "Rana",
            description = "Storyteller — narrative lookbooks & atelier journey scripts",
            variety = VoiceVariety.STORYTELLER,
            cloudVoiceId = "bf_emma",
            edgeVoiceLabel = "en-GB-LibbyNeural - en-GB (Female)",
        ),
        VoicePersona(
            id = "kai",
            displayName = "Kai",
            description = "Announcer — launch events, live promos and stage presentations",
            variety = VoiceVariety.ANNOUNCER,
            cloudVoiceId = "am_fenrir",
            edgeVoiceLabel = "en-US-ChristopherNeural - en-US (Male)",
        ),
        VoicePersona(
            id = "hamza",
            displayName = "Hamza",
            description = "Cinematic narrator — epic trailer reads & dramatic presentations",
            variety = VoiceVariety.CINEMATIC,
            cloudVoiceId = "am_liam",
            edgeVoiceLabel = "en-US-JasonNeural - en-US (Male)",
        ),
        VoicePersona(
            id = "elena",
            displayName = "Elena",
            description = "Melodic whisper — poetic fashion reviews & ASMR boutique audio",
            variety = VoiceVariety.WHISPER,
            cloudVoiceId = "af_sky",
            edgeVoiceLabel = "en-GB-MaisieNeural - en-GB (Female)",
        ),
        VoicePersona(
            id = "soraya",
            displayName = "Soraya",
            description = "Expressive couture host — vibrant runway commentaries",
            variety = VoiceVariety.FEMALE_BRIGHT,
            cloudVoiceId = "af_aoede",
            edgeVoiceLabel = "en-US-AriaNeural - en-US (Female)",
        ),
        VoicePersona(
            id = "marcus",
            displayName = "Marcus",
            description = "Crisp broadcast — tech breakdowns & atelier developer guides",
            variety = VoiceVariety.ANNOUNCER,
            cloudVoiceId = "am_onyx",
            edgeVoiceLabel = "en-US-SteffanNeural - en-US (Male)",
        ),
        VoicePersona(
            id = "maya",
            displayName = "Maya",
            description = "Modern podcast host — conversational trends & style talks",
            variety = VoiceVariety.FEMALE_WARM,
            cloudVoiceId = "af_jessica",
            edgeVoiceLabel = "en-US-MichelleNeural - en-US (Female)",
        ),
        VoicePersona(
            id = "leo",
            displayName = "Leo",
            description = "Energetic commercial — high-tempo teasers & promotional ads",
            variety = VoiceVariety.MALE_TENOR,
            cloudVoiceId = "am_puck",
            edgeVoiceLabel = "en-US-RogerNeural - en-US (Male)",
        ),
    )

    fun byId(id: String): VoicePersona =
        personas.firstOrNull { it.id == id } ?: personas.first()

    val defaultId: String = "amina"
}
