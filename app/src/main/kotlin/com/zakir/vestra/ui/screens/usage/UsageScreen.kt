package com.zakir.vestra.ui.screens.usage

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.CloudModelContracts
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.domain.PackVerifyStatus
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.usage.displayLabel
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassScreen
import com.zakir.vestra.ui.components.GlassSectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsageScreen(
    usage: UsageLedger,
    appSettings: AppSettings? = null,
    packManager: ModelPackManager? = null,
    onBack: () -> Unit,
    onOpenCreate: (() -> Unit)? = null,
) {
    val summary by usage.summary.collectAsState()
    val events by usage.events.collectAsState()
    val packStates = packManager?.states?.collectAsState()?.value.orEmpty()
    val fmt = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    val failCount = summary.totalRequests - summary.successCount

    GlassScreen(title = "Cloud usage", subtitle = "Free-tier request ledger", onBack = onBack) {
        if (summary.totalRequests == 0) {
            com.zakir.vestra.ui.components.GlassEmptyState(
                message = "No cloud runs yet. Open Image or Video studio to generate with free Spaces.",
                actionLabel = onOpenCreate?.let { "Open Image studio" },
                onAction = onOpenCreate,
            )
            Spacer(Modifier.height(12.dp))
        }

        GlassCard {
            GlassSectionLabel("SUMMARY")
            Text(
                "${summary.totalRequests} requests · ${summary.successCount} ok · $failCount failed",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tokens in ${summary.totalTokensIn} · out ${summary.totalTokensOut}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "All cloud models are free-tier · \$0.00 estimated spend",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { usage.clear() }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear history")
            }
        }

        if (summary.byProvider.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            GlassCard {
                GlassSectionLabel("BY MODEL / SERVICE")
                summary.byProvider.values.sortedByDescending { it.requests }.forEach { p ->
                    Spacer(Modifier.height(8.dp))
                    val catalog = CloudModelCatalog.byId(p.providerId)
                    val status = catalog?.let {
                        CloudModelContracts.liveStatusLabel(it, appSettings?.modelHealth)
                    }
                    Text(
                        buildString {
                            append(p.displayName)
                            if (status != null) append(" · $status")
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${p.requests} runs · ${p.tokensIn + p.tokensOut} tokens · free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    catalog?.usageNote?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (packManager != null) {
            GlassCard {
                GlassSectionLabel("LOCAL PACK HEALTH")
                val enginePacks = listOf("lite-v1", "pro-v2-int8", "pro-v1", "studio-models-v1")
                enginePacks.forEach { id ->
                    val state = packStates[id] ?: return@forEach
                    Spacer(Modifier.height(6.dp))
                    val label = when {
                        state.isReady() -> "Verified · ready offline"
                        state.verifyStatus == PackVerifyStatus.VERIFYING -> "Verifying ONNX…"
                        state.verifyStatus == PackVerifyStatus.FAILED ->
                            state.verifyError ?: "Verification failed"
                        state.status == PackStatus.INSTALLED -> "Installed · verification pending"
                        state.status == PackStatus.DOWNLOADING ->
                            "Downloading ${(state.progress * 100).toInt()}%"
                        else -> "Not installed"
                    }
                    Text(
                        "${state.pack.displayName} · $label",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            state.isReady() -> MaterialTheme.colorScheme.primary
                            state.verifyStatus == PackVerifyStatus.FAILED ->
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                LocalModelCatalog.runnable().filter { it.packId != null }.forEach { entry ->
                    val id = entry.packId ?: return@forEach
                    if (id in enginePacks) return@forEach
                    val state = packStates[id] ?: return@forEach
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${entry.displayName} · ${state.verifyLabel().ifBlank { state.status.name }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (appSettings != null) {
            GlassCard {
                GlassSectionLabel("MODEL HEALTH")
                AiCapability.entries.filter { it != AiCapability.TRY_ON }.forEach { cap ->
                    val provider = appSettings.selectedProvider(cap)
                    val status = CloudModelContracts.liveStatusLabel(provider, appSettings.modelHealth)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${cap.name.replace('_', ' ')} · ${provider.displayName} · $status",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        GlassCard {
            GlassSectionLabel("RECENT")
            if (events.isEmpty()) {
                Text(
                    "No cloud usage yet. Run Create, Code, Video, or Cloud try-on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                events.take(40).forEach { e ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${fmt.format(Date(e.timestampMs))} · ${e.providerName}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val capabilityLabel = runCatching {
                        AiCapability.valueOf(e.capability).displayLabel()
                    }.getOrDefault(e.capability)
                    Text(
                        buildString {
                            append(capabilityLabel)
                            append(" · ")
                            append(e.platform)
                            if (e.tokensIn + e.tokensOut > 0) {
                                append(" · ${e.tokensIn}→${e.tokensOut} tok")
                            }
                            append(" · free")
                            if (!e.success) append(" · failed")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (e.success) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (e.note.isNotBlank()) {
                        Text(
                            e.note.take(280),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (e.success) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
