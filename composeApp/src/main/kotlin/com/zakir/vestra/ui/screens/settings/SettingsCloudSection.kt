package com.zakir.vestra.ui.screens.settings

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.storage.TokenSidecar
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Processing mode — a single, prominent, honestly-labeled setting (lookbookweb's
 * Auto/On-device-only/Cloud-only framing, adapted to what this app actually does).
 *
 * This app doesn't implement a true "Auto" fallback router — each capability has its own
 * explicit model selection (local or cloud) below, and this switch only controls whether a
 * cloud selection is *allowed to run at all*. So the honest two states are "On-device only"
 * (a capability with a cloud model selected is blocked with a clear message rather than
 * reaching the network) and "Cloud allowed" (a capability runs whatever model was explicitly
 * selected for it, local or cloud) — not a third state that silently picks between them, which
 * this codebase doesn't do and this repo's AUTO-tier-never-cloud invariant means it never
 * should for try-on specifically.
 */
internal fun LazyListScope.settingsCloudMasterToggleSection(appSettings: AppSettings) {
    item(key = "cloud-master-toggle") {
        val cloudEnabled by appSettings.cloudModelsEnabled.collectAsState()
        GlassCard {
            GlassSectionLabel("PROCESSING MODE")
            Spacer(Modifier.height(10.dp))
            ProcessingModeCard(
                title = "On-device only",
                description = "No network call is ever made. A capability with a local model " +
                    "selected below runs it; one with a cloud model selected is blocked with a " +
                    "clear message instead of silently reaching the network.",
                selected = !cloudEnabled,
                onSelect = { appSettings.setCloudModelsEnabled(false) },
                testTag = TestTags.PROCESSING_MODE_LOCAL,
            )
            Spacer(Modifier.height(8.dp))
            ProcessingModeCard(
                title = "Cloud allowed",
                description = "Each capability below runs whatever model you selected for it — " +
                    "local or a free cloud model (Groq / OpenRouter / Hugging Face).",
                selected = cloudEnabled,
                onSelect = { appSettings.setCloudModelsEnabled(true) },
                testTag = TestTags.PROCESSING_MODE_CLOUD,
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@androidx.compose.runtime.Composable
private fun ProcessingModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(shape)
            .background(if (selected) VestraColors.Accent.copy(alpha = 0.14f) else VestraColors.GlassFill)
            .border(
                1.dp,
                if (selected) VestraColors.Accent.copy(alpha = 0.55f) else VestraColors.GlassBorder,
                shape,
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VestraColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Spacer(Modifier.height(0.dp))
            Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = VestraColors.Accent)
        }
    }
}

/** API keys card + per-capability cloud model dropdowns. */
internal fun LazyListScope.settingsCloudKeysSection(
    appSettings: AppSettings,
    hfTokenSaved: Boolean,
    hfInput: String,
    groqInput: String,
    openRouterInput: String,
    onHfInput: (String) -> Unit,
    onGroqInput: (String) -> Unit,
    onOpenRouterInput: (String) -> Unit,
    keysSavedFlash: Boolean,
    clipboardHint: String?,
    durableReady: Boolean,
    onApplyClipboard: () -> Boolean,
    onOpenPortal: (String) -> Unit,
    onSaveTokens: () -> Unit,
    importTokensLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    onKeysLoadedFromDocuments: (count: Int) -> Unit,
) {
    item(key = "keys") {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        GlassCard {
            GlassSectionLabel("API KEYS")
            Text(
                "Create a free classic HF Write/Read key with Inference Providers (not fine-grained discussion-only), copy it, then Save — or import tokens.json / tokens.txt. Clipboard keys are detected automatically. Local Lite/Pro packs never need a key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onOpenPortal(TokenSidecar.Portal.HF) },
                    modifier = Modifier.weight(1f),
                ) { Text("Hugging Face") }
                OutlinedButton(
                    onClick = { onOpenPortal(TokenSidecar.Portal.GROQ) },
                    modifier = Modifier.weight(1f),
                ) { Text("Groq") }
                OutlinedButton(
                    onClick = { onOpenPortal(TokenSidecar.Portal.OPENROUTER) },
                    modifier = Modifier.weight(1f),
                ) { Text("OpenRouter") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (!onApplyClipboard()) {
                        Toast.makeText(
                            context,
                            "No Hugging Face / Groq / OpenRouter key found on clipboard",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Paste key from clipboard")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    importTokensLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/plain",
                            "text/*",
                            "*/*",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import tokens from JSON / TXT file")
            }
            if (durableReady) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val count = withContext(Dispatchers.IO) {
                                TokenSidecar.autoFetchFromDocuments(
                                    appSettings,
                                    overwriteExisting = true,
                                )
                            }
                            onKeysLoadedFromDocuments(count)
                            Toast.makeText(
                                context,
                                if (count > 0) {
                                    "Loaded $count key(s) from Documents/TheLookbook"
                                } else {
                                    "No tokens.json / tokens.txt found in Documents/TheLookbook"
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Auto-fetch from Documents/TheLookbook")
                }
            }
            clipboardHint?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = VestraColors.Accent)
            }
            Spacer(Modifier.height(10.dp))
            KeyField("Hugging Face API key", hfInput, onHfInput)
            KeyField("Groq API key", groqInput, onGroqInput)
            KeyField("OpenRouter API key (free models)", openRouterInput, onOpenRouterInput)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSaveTokens,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (keysSavedFlash) "Saved" else LookbookCopy.ACTION_SAVE_TOKENS)
            }
            if (hfTokenSaved) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "HF token saved · for Code use curated Qwen2.5-Coder / Groq (not random auto-listed models).",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.Accent,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

internal fun LazyListScope.settingsCloudCapabilitiesSection(
    appSettings: AppSettings,
    freeCloudDiscovery: FreeCloudDiscovery,
    tryOnId: String,
    imageGenId: String,
    imageEditId: String,
    codeId: String,
    videoId: String,
    audioId: String,
) {
    // Try-on cloud model row hidden while try-on is temporarily disabled app-wide
    // (see HomeTab.TRY_ON_TAB_ENABLED). Re-add the item(key = "cap-tryon") block to restore.
    item(key = "cap-gen") {
        CloudCapabilityDropdown(
            title = "IMAGE GENERATION",
            capability = AiCapability.IMAGE_GEN,
            selectedId = imageGenId,
            appSettings = appSettings,
            discovery = freeCloudDiscovery,
            onSelect = appSettings::setImageGenProvider,
        )
    }
    item(key = "cap-edit") {
        CloudCapabilityDropdown(
            title = "IMAGE EDIT / RECREATE",
            capability = AiCapability.IMAGE_EDIT,
            selectedId = imageEditId,
            appSettings = appSettings,
            discovery = freeCloudDiscovery,
            onSelect = appSettings::setImageEditProvider,
        )
    }
    item(key = "cap-code") {
        CloudCapabilityDropdown(
            title = "CODING MODELS",
            capability = AiCapability.CODE,
            selectedId = codeId,
            appSettings = appSettings,
            discovery = freeCloudDiscovery,
            onSelect = appSettings::setCodeProvider,
        )
    }
    item(key = "cap-video") {
        CloudCapabilityDropdown(
            title = "VIDEO MODELS",
            capability = AiCapability.VIDEO,
            selectedId = videoId,
            appSettings = appSettings,
            discovery = freeCloudDiscovery,
            onSelect = appSettings::setVideoProvider,
        )
    }
    item(key = "cap-audio") {
        CloudCapabilityDropdown(
            title = "AUDIO / TTS MODELS",
            capability = AiCapability.AUDIO,
            selectedId = audioId,
            appSettings = appSettings,
            discovery = freeCloudDiscovery,
            onSelect = appSettings::setAudioProvider,
        )
    }
}
