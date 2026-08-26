package com.zakir.vestra.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.cloud.TokenValidationState
import com.zakir.vestra.shared.cloud.TokenValidator
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.platformHttpClient
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.storage.TokenSidecar
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.launch

/**
 * Processing mode — simple switch between on-device and cloud allowed.
 */
internal fun LazyListScope.settingsCloudMasterToggleSection(appSettings: AppSettings) {
    item(key = "cloud-master-toggle") {
        val cloudEnabled by appSettings.cloudModelsEnabled.collectAsState()
        GlassCard {
            GlassSectionLabel("PROCESSING MODE")
            Spacer(Modifier.height(10.dp))
            ProcessingModeCard(
                title = "On-device only (100% Private Offline)",
                description = "All AI models run locally on your device hardware with zero network calls.",
                selected = !cloudEnabled,
                onSelect = { appSettings.setCloudModelsEnabled(false) },
                testTag = TestTags.PROCESSING_MODE_LOCAL,
            )
            Spacer(Modifier.height(8.dp))
            ProcessingModeCard(
                title = "Cloud allowed (Free community hosts)",
                description = "Enable fast access to free cloud inference (Groq, Hugging Face ZeroGPU, OpenRouter).",
                selected = cloudEnabled,
                onSelect = { appSettings.setCloudModelsEnabled(true) },
                testTag = TestTags.PROCESSING_MODE_CLOUD,
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
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
            Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = VestraColors.Accent)
        }
    }
}

/** Minimal, refined API keys card with validate buttons and free model catalog launcher. */
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
    onApplyClipboard: () -> Boolean,
    onOpenPortal: (String) -> Unit,
    onSaveTokens: () -> Unit,
    importTokensLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    onOpenFreeModels: () -> Unit,
) {
    item(key = "keys") {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val validator = remember { TokenValidator(platformHttpClient()) }

        var hfStatus by remember { mutableStateOf<TokenValidationState>(TokenValidationState.Idle) }
        var groqStatus by remember { mutableStateOf<TokenValidationState>(TokenValidationState.Idle) }
        var openRouterStatus by remember { mutableStateOf<TokenValidationState>(TokenValidationState.Idle) }

        GlassCard {
            GlassSectionLabel("API KEYS & ACCESS TOKENS")
            Text(
                "Add your free provider tokens to unlock fast cloud models. Tokens are securely stored on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Action: View Free Models Button
            OutlinedButton(
                onClick = onOpenFreeModels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = VestraColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("View Available Free Models", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(14.dp))

            // Quick Portal Links
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onOpenPortal(TokenSidecar.Portal.HF) },
                    modifier = Modifier.weight(1f),
                ) { Text("Get HF Key", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { onOpenPortal(TokenSidecar.Portal.GROQ) },
                    modifier = Modifier.weight(1f),
                ) { Text("Get Groq Key", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { onOpenPortal(TokenSidecar.Portal.OPENROUTER) },
                    modifier = Modifier.weight(1f),
                ) { Text("Get OpenRouter", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(10.dp))

            // Clipboard Paste & File Import
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (!onApplyClipboard()) {
                            Toast.makeText(context, "No API key found on clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Pasted key from clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Paste clipboard", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        importTokensLauncher.launch(arrayOf("application/json", "text/plain", "text/*", "*/*"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import file", fontSize = 12.sp)
                }
            }

            clipboardHint?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = VestraColors.Accent)
            }

            Spacer(Modifier.height(14.dp))

            // Hugging Face Input + Validate
            ValidatedKeyField(
                label = "Hugging Face Access Token",
                placeholder = "hf_...",
                value = hfInput,
                onValueChange = {
                    onHfInput(it)
                    hfStatus = TokenValidationState.Idle
                },
                validationState = hfStatus,
                onValidate = {
                    scope.launch {
                        hfStatus = TokenValidationState.Validating
                        hfStatus = validator.validateHfToken(hfInput)
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            // Groq Input + Validate
            ValidatedKeyField(
                label = "Groq API Key (LPU Fast Inference)",
                placeholder = "gsk_...",
                value = groqInput,
                onValueChange = {
                    onGroqInput(it)
                    groqStatus = TokenValidationState.Idle
                },
                validationState = groqStatus,
                onValidate = {
                    scope.launch {
                        groqStatus = TokenValidationState.Validating
                        groqStatus = validator.validateGroqKey(groqInput)
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            // OpenRouter Input + Validate
            ValidatedKeyField(
                label = "OpenRouter API Key (Free Models)",
                placeholder = "sk-or-...",
                value = openRouterInput,
                onValueChange = {
                    onOpenRouterInput(it)
                    openRouterStatus = TokenValidationState.Idle
                },
                validationState = openRouterStatus,
                onValidate = {
                    scope.launch {
                        openRouterStatus = TokenValidationState.Validating
                        openRouterStatus = validator.validateOpenRouterKey(openRouterInput)
                    }
                },
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onSaveTokens,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (keysSavedFlash) "Tokens Saved ✓" else LookbookCopy.ACTION_SAVE_TOKENS)
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ValidatedKeyField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    validationState: TokenValidationState,
    onValidate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        KeyField(label = label, value = value, onChange = onValueChange)

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (validationState) {
                    is TokenValidationState.Validating -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = VestraColors.Accent,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Checking token...", style = MaterialTheme.typography.labelSmall, color = VestraColors.InkMuted)
                        }
                    }
                    is TokenValidationState.Valid -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(validationState.accountInfo, style = MaterialTheme.typography.labelSmall, color = VestraColors.Accent)
                        }
                    }
                    is TokenValidationState.Invalid -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = VestraColors.Danger, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(validationState.message, style = MaterialTheme.typography.labelSmall, color = VestraColors.Danger)
                        }
                    }
                    is TokenValidationState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = VestraColors.AccentSoft, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(validationState.reason, style = MaterialTheme.typography.labelSmall, color = VestraColors.AccentSoft)
                        }
                    }
                    TokenValidationState.Idle -> {
                        if (value.isNotBlank()) {
                            Text("Token entered · Tap Validate to test", style = MaterialTheme.typography.labelSmall, color = VestraColors.InkMuted)
                        }
                    }
                }
            }

            if (value.isNotBlank()) {
                OutlinedButton(
                    onClick = onValidate,
                    modifier = Modifier.height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("Validate", fontSize = 11.sp)
                }
            }
        }
    }
}
