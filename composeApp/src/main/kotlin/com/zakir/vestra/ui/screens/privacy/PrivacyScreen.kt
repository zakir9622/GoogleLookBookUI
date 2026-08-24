package com.zakir.vestra.ui.screens.privacy

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassScreen
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.GlassSecondaryButton

/**
 * Offline Privacy Policy — Play / enterprise reviewers can read without network.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    GlassScreen(
        title = LookbookCopy.ACTION_OPEN_PRIVACY,
        subtitle = "On-device · free cloud optional",
        onBack = onBack,
    ) {
        GlassCard {
            GlassSectionLabel("THE LOOKBOOK")
            Text(
                "Last updated: 2026-08-21",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                PRIVACY_BODY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        GlassSecondaryButton(
            text = "Open hosted copy (GitHub)",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(LookbookCopy.PRIVACY_URL)),
                    )
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private const val PRIVACY_BODY =
    "The Lookbook (package com.zakir.vestra) generates modest-fashion try-on looks " +
        "and optional free-tier cloud studio outputs (image, video, code).\n\n" +
        "On your device\n" +
        "• Photos you pick or capture are processed on-device when using Lite or Pro packs. " +
        "They stay in app-private storage (or Documents/TheLookbook when durable storage is enabled).\n" +
        "• Generated looks are marked AI-generated (watermark on store builds + metadata).\n" +
        "• Deleting the app removes private storage; durable Documents copies remain until you delete them.\n\n" +
        "Optional free-tier cloud\n" +
        "• Only when you select Cloud try-on or run Image / Video / Code studio: prompt text and " +
        "attached images go over HTTPS to the selected Hugging Face Space, Groq, or OpenRouter job. " +
        "Provider retention policies apply. The Lookbook does not keep uploads on a Lookbook server.\n" +
        "• Images are re-encoded before upload to strip EXIF when possible.\n" +
        "• API keys stay on your device and are sent only to the matching provider when you generate.\n\n" +
        "Content reports\n" +
        "• Reports (reason + file path) are stored on this device only. Export them from Settings → Storage & privacy.\n\n" +
        "What we never do\n" +
        "• No ads, trackers, or analytics SDKs.\n" +
        "• No sale of personal data for advertising.\n" +
        "• No contacts, precise location, or browsing history collection.\n\n" +
        "Children\n" +
        "• Not directed at children under 13. Likeness consent prohibits generating anyone without permission.\n\n" +
        "Contact\n" +
        "• ${LookbookCopy.SUPPORT_EMAIL}"
