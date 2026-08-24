package com.zakir.vestra.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.GlassTopBar
import com.zakir.vestra.ui.components.SpatialBackground

@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenEngines: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    SpatialBackground {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
        ) {
            item {
                GlassTopBar(
                    title = LookbookCopy.STUDIO_SETTINGS,
                    subtitle = "Engines · cloud · appearance",
                    navigation = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                GlassCard(onClick = onOpenCloud) {
                    GlassSectionLabel("CLOUD MODELS & KEYS")
                    Text("HF · Groq · OpenRouter tokens, model picker, token wizard", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                GlassCard(onClick = onOpenEngines) {
                    GlassSectionLabel("ENGINES & PACKS")
                    Text("Lite/Pro/Cloud tier, pack download, local models", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                GlassCard(onClick = onOpenAppearance) {
                    GlassSectionLabel("APPEARANCE & PRIVACY")
                    Text("Theme, permissions, cache, durable storage", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                GlassCard(onClick = { onOpenDiagnostics?.invoke() }) {
                    GlassSectionLabel("DIAGNOSTICS")
                    Text("Crashes · run history · export", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Auto-troubleshooting logs append on every crash — share the bundle without losing history",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                GlassCard(onClick = onOpenUsage) {
                    GlassSectionLabel("USAGE & MODEL HEALTH")
                    Text("Free-tier ledger and live model readiness", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                GlassCard(onClick = onOpenHelp) {
                    GlassSectionLabel("HELP")
                    Text(LookbookCopy.STUDIO_HELP, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                GlassCard(onClick = onOpenPrivacy) {
                    GlassSectionLabel("PRIVACY")
                    Text(LookbookCopy.ACTION_OPEN_PRIVACY, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
