package com.zakir.vestra.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.PackState
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.engine.Availability
import com.zakir.vestra.shared.engine.EngineRouter
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.local.LocalModelEntry
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel

/** Local try-on engine · pack download · usage shortcut. */
internal fun LazyListScope.settingsEnginesSection(
    appSettings: AppSettings,
    engineRouter: EngineRouter,
    selectedTier: EngineTier,
    selectedPackId: String,
    onSelectPackId: (String) -> Unit,
    localPackChoices: List<LocalModelEntry>,
    packStates: Map<String, PackState>,
    packCatalogError: String?,
    startDownload: (String) -> Unit,
    onOpenPacks: () -> Unit,
    onOpenUsage: () -> Unit,
    handshakeBusy: Boolean = false,
    handshakeDetail: String? = null,
    handshakeOk: Boolean? = null,
    onHandshakeSelected: () -> Unit = {},
    onHandshakeAll: () -> Unit = {},
) {
    item(key = "engine") {
        GlassCard {
            GlassSectionLabel("LOCAL TRY-ON ENGINE")
            Text(
                "On-device engines. Cloud is never chosen by Auto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            EngineDropdown(
                selected = selectedTier,
                availability = { tier ->
                    if (tier == EngineTier.AUTO) Availability.Ready else engineRouter.availability(tier)
                },
                onSelect = appSettings::setEngineTier,
            )
            Spacer(Modifier.height(12.dp))
            val preferNnapi by appSettings.preferNnapi.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prefer NNAPI", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Off by default — safer on Pixel. Turn on only if try-on is stable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferNnapi,
                    onCheckedChange = appSettings::setPreferNnapi,
                )
            }
            Spacer(Modifier.height(12.dp))
            val preferLiteRtGpu by appSettings.preferLiteRtLmGpu.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LiteRT-LM GPU", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Off by default — CPU for Gemma 4 / vision / audio. Enable after Pixel 9 verify.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferLiteRtGpu,
                    onCheckedChange = appSettings::setPreferLiteRtLmGpu,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    item(key = "local-pack") {
        GlassCard {
            GlassSectionLabel("LOCAL MODEL PACK")
            Text(
                "Select a pack, then download. Transfers resume if interrupted. Durable storage is requested on download so packs survive reinstall.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            PackDropdown(
                choices = localPackChoices.mapNotNull { entry ->
                    entry.packId?.let { id ->
                        id to "${entry.displayName} · ${entry.approxSizeLabel}"
                    }
                },
                selectedId = selectedPackId,
                onSelect = { id ->
                    onSelectPackId(id)
                    LocalModelCatalog.entries
                        .firstOrNull { it.packId == id }
                        ?.engineTier
                        ?.let { appSettings.setEngineTier(it) }
                },
            )
            val status = packStates[selectedPackId]?.status
            val progress = packStates[selectedPackId]?.progress ?: 0f
            Spacer(Modifier.height(8.dp))
            Text(
                when (status) {
                    PackStatus.INSTALLED -> packStates[selectedPackId]?.verifyLabel()
                        ?: "Installed — verification pending"
                    PackStatus.DOWNLOADING -> "Downloading ${(progress * 100).toInt()}%…"
                    PackStatus.INCOMPATIBLE -> "This device doesn’t meet pack requirements"
                    PackStatus.UPDATE_AVAILABLE -> "Update available"
                    PackStatus.NOT_INSTALLED -> if (progress > 0f) "Partial download — can resume" else "Not installed"
                    null -> when {
                        packStates.isEmpty() && !packCatalogError.isNullOrBlank() ->
                            "Catalog unavailable — open All packs to retry"
                        packStates.isEmpty() -> "Loading pack catalog…"
                        else -> "Not in catalog yet — open All packs or tap Download"
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (selectedPackId.isNotBlank()) startDownload(selectedPackId) },
                    enabled = status != PackStatus.INCOMPATIBLE && selectedPackId.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (status) {
                            PackStatus.INSTALLED -> "Re-download"
                            PackStatus.DOWNLOADING -> "Downloading…"
                            else -> if (progress > 0f) "Resume" else "Download"
                        },
                    )
                }
                OutlinedButton(onClick = onOpenPacks, modifier = Modifier.weight(1f)) {
                    Text("All packs")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Device handshake confirms the pack is on disk, integrity-checked, and wired to the matching studio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onHandshakeSelected,
                    enabled = !handshakeBusy &&
                        selectedPackId.isNotBlank() &&
                        status == PackStatus.INSTALLED,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (handshakeBusy) "Handshaking…" else "Verify link")
                }
                OutlinedButton(
                    onClick = onHandshakeAll,
                    enabled = !handshakeBusy &&
                        packStates.values.any { it.status == PackStatus.INSTALLED },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Verify all")
                }
            }
            handshakeDetail?.let { detail ->
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (handshakeOk) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    item(key = "usage") {
        GlassCard(onClick = onOpenUsage) {
            GlassSectionLabel("USAGE")
            Text(LookbookCopy.STUDIO_USAGE, style = MaterialTheme.typography.titleMedium)
            Text(
                "Local ledger of free-tier cloud requests, tokens, and failure notes. Local packs are \$0.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
