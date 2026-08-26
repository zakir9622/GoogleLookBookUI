package com.zakir.vestra.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassTopBar
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.VestraColors

@Composable
fun SettingsHubScreen(
    onBack: (() -> Unit)? = null,
    onOpenCloud: () -> Unit,
    onOpenEngines: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
    onOpenModelConfig: (() -> Unit)? = null,
) {
    SpatialBackground {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                GlassTopBar(
                    title = LookbookCopy.STUDIO_SETTINGS,
                    subtitle = "Engines · cloud · appearance",
                    navigation = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = VestraColors.Ink,
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.height(18.dp))
            }
            item {
                SettingsHubItem(
                    title = "Model Config & Orchestrator",
                    subtitle = "Gemma, OpenRouter, Groq & HF routing",
                    onClick = { onOpenModelConfig?.invoke() ?: onOpenCloud() },
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsHubItem(
                    title = "Cloud Models & API Keys",
                    subtitle = "API keys, token wizard & model selection",
                    onClick = onOpenCloud,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsHubItem(
                    title = "Engines & Model Packs",
                    subtitle = "On-device model packs & tier selection",
                    onClick = onOpenEngines,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsHubItem(
                    title = "Appearance & Storage",
                    subtitle = "Theme, dark mode, storage & cache",
                    onClick = onOpenAppearance,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsHubItem(
                    title = "Diagnostics & Logs",
                    subtitle = "Troubleshooting logs & crash export",
                    onClick = { onOpenDiagnostics?.invoke() },
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsHubItem(
                    title = "Usage & Health",
                    subtitle = "Quota ledger & model readiness status",
                    onClick = onOpenUsage,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsHubItem(
                    title = "Help & FAQ",
                    subtitle = LookbookCopy.STUDIO_HELP,
                    onClick = onOpenHelp,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsHubItem(
                    title = "Privacy Policy",
                    subtitle = LookbookCopy.ACTION_OPEN_PRIVACY,
                    onClick = onOpenPrivacy,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsHubItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    GlassCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VestraColors.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VestraColors.InkMuted,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = VestraColors.InkMuted.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
