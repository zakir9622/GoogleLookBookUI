package com.zakir.vestra.shared.content

/**
 * Enterprise Lookbook product copy and FAQ — single source for Help and in-app naming.
 */
object LookbookCopy {
    const val PRODUCT_NAME = "The Lookbook"
    const val PRODUCT_TAGLINE = "Modest fashion atelier · on-device AI"
    const val PRODUCT_BLURB =
        "Modest fashion atelier with true on-device AI. Try-on, Image Create/Edit, Video still-clips, " +
            "Code (Gemma), and Speak run offline after you download Model packs — cloud studios stay optional."

    const val STUDIO_HOME = "Atelier"
    const val STUDIO_TRY_ON = "Virtual try-on"
    const val STUDIO_IMAGE = "Image studio"
    const val STUDIO_VIDEO = "Clip studio"
    const val STUDIO_CODE = "Code studio"
    const val STUDIO_AUDIO = "Audio studio"
    const val STUDIO_WARDROBE = "Looks gallery"
    const val STUDIO_PACKS = "Model packs"
    const val STUDIO_USAGE = "Cloud usage"
    const val STUDIO_SETTINGS = "Settings"
    const val STUDIO_HELP = "Help & FAQ"

    const val ACTION_START_TRY_ON = "Start try-on"
    const val ACTION_GENERATE = "Generate"
    const val ACTION_CANCEL_GENERATION = "Cancel generation"
    const val ACTION_RETRY = "Retry"
    const val ACTION_BACK_ATELIER = "Back to atelier"
    const val ACTION_SAVE_TOKENS = "Save API keys"
    const val ACTION_OPEN_HELP = "Open Help & FAQ"
    const val ACTION_OPEN_PRIVACY = "Privacy policy"
    const val ACTION_OPEN_SETTINGS = "Open Settings"
    const val ACTION_CONTACT_SUPPORT = "Email support"
    const val ACTION_EXPORT_REPORTS = "Export content reports"
    const val SUPPORT_EMAIL = "zakir9622@gmail.com"
    const val ACTION_REPORT = "Report content"
    const val ACTION_SHARE = "Share"
    const val ACTION_OPEN_VIDEO = "Open"
    const val ACTION_SHOW_ALL_LOOKS = "Show all looks"

    const val LABEL_VIRTUAL_TRY_ON = "VIRTUAL TRY-ON"
    const val EMPTY_GALLERY = "Your try-on and Image studio looks appear here."
    const val EMPTY_FAVORITES = "No favorites yet — tap ★ on a look."
    const val PRIVACY_URL =
        "https://github.com/zakir9622/Agentic-AI/blob/main/docs/PRIVACY_POLICY.md"

    const val ASSIST_EDITORIAL = "Editorial assist"
    const val ASSIST_FASHION = "Modest fashion"
    const val ASSIST_DETAIL = "Detail enhance"
    const val ASSIST_QUALITY = "Quality check"
    const val ASSIST_PRAGMATIC = "Pragmatic"
    const val ASSIST_CREATIVE = "Creative"

    const val PERM_NOTIFICATIONS_TITLE = "Download progress alerts"
    const val PERM_NOTIFICATIONS_BODY =
        "Allow notifications so The Lookbook can show model-pack download progress and completion. " +
            "You can change this anytime in system Settings."
    const val PERM_CAMERA_TITLE = "Camera for garment capture"
    const val PERM_CAMERA_BODY =
        "Camera access lets you photograph a garment for try-on. " +
            "You can always choose a photo from the system picker instead."
    const val PERM_STORAGE_TITLE = "Durable storage"
    const val PERM_STORAGE_BODY =
        "All-files access stores model packs and API keys under Documents/TheLookbook " +
            "so they survive uninstall. Without it, packs stay in private app storage."
}

data class HelpTopic(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
)

object HelpCatalog {
    val topics: List<HelpTopic> = listOf(
        HelpTopic(
            id = "start-tryon",
            category = "Getting started",
            question = "How do I run a virtual try-on?",
            answer = "From the Atelier home screen, tap Start try-on (or the Try-on control on the bottom rail). " +
                "Choose a garment photo, optional casting profile, then a person source. " +
                "Generation uses your selected local engine (Lite/Pro) or Cloud try-on if you enabled it in Settings.",
        ),
        HelpTopic(
            id = "start-image",
            category = "Getting started",
            question = "What is Image studio?",
            answer = "Image studio creates or edits stills on-device with local-sdturbo-v1 (Settings → Model packs), " +
                "or via free Hugging Face Spaces/Inference when you prefer cloud. " +
                "Attach a reference photo for Edit. Tap Verify link after download to confirm the pack is wired.",
        ),
        HelpTopic(
            id = "start-video-code",
            category = "Getting started",
            question = "How do Clip and Code studios work?",
            answer = "Clip studio can encode a short on-device still-clip from local-sdturbo-v1, or use free HF Spaces for cloud video. " +
                "Code studio runs local Gemma 4 (local-gemma-4-e2b-v1, LiteRT-LM) or legacy Gemma 3 offline, " +
                    "or Groq / HF / OpenRouter with your API keys. " +
                "Download packs once, then Verify link for a device handshake.",
        ),
        HelpTopic(
            id = "packs-offline",
            category = "Model packs",
            question = "How do Lite and Pro packs work offline?",
            answer = "Download a pack from Settings → Model packs or All packs. " +
                "After install, try-on runs on-device with no network. " +
                "Also download local-sdturbo-v1 (Create/Edit/Clip) and local-gemma-4-e2b-v1 (Code, vision assist, transcribe). " +
                    "Optional: local-functiongemma-v1 for debug local tools. " +
                "Enable durable storage so Documents/TheLookbook/packs survives uninstall.",
        ),
        HelpTopic(
            id = "packs-resume",
            category = "Model packs",
            question = "My pack download stopped. Can I resume?",
            answer = "Yes. Open Model packs and tap Resume (or Download again). " +
                "Transfers use Android WorkManager and continue after interruptions when notifications and storage permissions are granted.",
        ),
        HelpTopic(
            id = "tokens-hf",
            category = "API keys & cloud",
            question = "Which Hugging Face token do I need?",
            answer = "Use a classic Read or Write token with Inference Providers permission for coding models. " +
                "Fine-grained tokens limited to discussions will fail. " +
                "HF Spaces for try-on/image/video often work without a token, but a token can raise rate limits.",
        ),
        HelpTopic(
            id = "tokens-save",
            category = "API keys & cloud",
            question = "How do I save API keys safely?",
            answer = "Paste Hugging Face, Groq, and OpenRouter keys in Settings → API keys, then Save. " +
                "Enable durable storage and optionally Export tokens file to Documents/TheLookbook/tokens.json. " +
                "Keys never leave your device except when calling the selected free provider.",
        ),
        HelpTopic(
            id = "cloud-queue",
            category = "API keys & cloud",
            question = "Generation says queue full or timed out.",
            answer = "Free Spaces share public GPU quotas. The app caps each Space poll (~12s) and will try one " +
                "alternate edit model after a primary timeout. Wait a few minutes, retry off-peak, or switch " +
                "model in the composer (InstructPix2Pix is often faster than Qwen when ZeroGPU is busy). " +
                "Cancel generation if a job is stuck, then try again with a simpler prompt.",
        ),
        HelpTopic(
            id = "model-unsupported",
            category = "API keys & cloud",
            question = "A cloud model is missing or blocked.",
            answer = "Some Spaces need inputs the app cannot supply yet (for example FitDiT mask + pose). " +
                "Those models stay listed as unsupported. Prefer IDM-VTON or OOTDiffusion for try-on.",
        ),
        HelpTopic(
            id = "perm-notifications",
            category = "Permissions",
            question = "Why does the app ask for notifications?",
            answer = LookbookCopy.PERM_NOTIFICATIONS_BODY,
        ),
        HelpTopic(
            id = "perm-camera",
            category = "Permissions",
            question = "Why does the app ask for camera?",
            answer = LookbookCopy.PERM_CAMERA_BODY + " Deny camera and use Add photo if you prefer.",
        ),
        HelpTopic(
            id = "perm-storage",
            category = "Permissions",
            question = "What is durable / all-files access?",
            answer = LookbookCopy.PERM_STORAGE_BODY + " Open Settings → Survives reinstall to enable it.",
        ),
        HelpTopic(
            id = "privacy",
            category = "Privacy & security",
            question = "Where do my photos and looks go?",
            answer = "Local try-on stays on device. Cloud studios upload only what you submit to the selected free provider. " +
                "Looks gallery and caches remain on this phone. Clear cache in Settings anytime; packs and gallery index are kept.",
        ),
        HelpTopic(
            id = "usage-ledger",
            category = "Privacy & security",
            question = "What does Cloud usage show?",
            answer = "A local ledger of free-tier cloud requests, success/failure, and LLM token counts. " +
                "Estimated spend is always \$0. Clearing the ledger does not delete looks or packs.",
        ),
        HelpTopic(
            id = "troubleshoot-empty",
            category = "Troubleshooting",
            question = "Nothing generates / empty result.",
            answer = "Confirm internet for cloud studios, Save a valid API key if required, and check the selected model in Settings. " +
                "Enable Editorial assist for image/video filters. For try-on, ensure Lite/Pro is installed or Cloud try-on is selected with a ready Space.",
        ),
        HelpTopic(
            id = "troubleshoot-crash",
            category = "Troubleshooting",
            question = "The app felt stuck or crashed after opening Settings.",
            answer = "Update to the latest build. Heavy screens use instant navigation to avoid ANRs. " +
                "Force-stop the app from system Settings if a download notification is orphaned, then reopen and Resume the pack.",
        ),
        HelpTopic(
            id = "support-contact",
            category = "Troubleshooting",
            question = "How do I get further help?",
            answer = "Use this Help & FAQ first, then review Cloud usage for the last failure note. " +
                "Email ${LookbookCopy.SUPPORT_EMAIL} for pack or device issues. Open Privacy policy in Settings for data practices.",
        ),
        HelpTopic(
            id = "privacy-policy",
            category = "Privacy & security",
            question = "Where is the Privacy Policy?",
            answer = "Open Settings → About → Privacy policy for the full on-device policy. " +
                "A hosted GitHub copy is also linked for store listings.",
        ),
        HelpTopic(
            id = "content-reports",
            category = "Privacy & security",
            question = "What happens when I report content?",
            answer = "Reports are stored only on this device (reason + path). " +
                "Export them from Settings → Storage & privacy → Export content reports for Play review workflows.",
        ),
    )

    fun categories(): List<String> = topics.map { it.category }.distinct()

    fun search(query: String): List<HelpTopic> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return topics
        return topics.filter {
            it.question.lowercase().contains(q) ||
                it.answer.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    /** Live catalog readiness lines for Help — kept in sync with CloudModelContracts. */
    fun modelReadinessLines(): List<String> =
        com.zakir.vestra.shared.cloud.CloudModelCatalog.providers.map { provider ->
            val c = com.zakir.vestra.shared.cloud.CloudModelContracts.forProvider(provider)
            "${provider.displayName} · ${
                c.support.name.lowercase().replaceFirstChar { ch -> ch.uppercaseChar() }
            } · ${c.schemaNote}"
        }
}
