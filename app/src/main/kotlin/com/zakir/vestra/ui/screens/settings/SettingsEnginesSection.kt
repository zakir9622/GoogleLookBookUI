package com.zakir.vestra.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.PackState
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.domain.PackVerifyStatus
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.local.LocalModelEntry
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.launch

/** Revamped minimal On-Device models manager and hardware acceleration settings. */
internal fun LazyListScope.settingsEnginesSection(
    appSettings: AppSettings,
    packManager: ModelPackManager,
    localPackChoices: List<LocalModelEntry>,
    packStates: Map<String, PackState>,
    startDownload: (String) -> Unit,
) {
    // 1. Hardware Acceleration Settings
    item(key = "hardware-engine") {
        val preferLiteRtGpu by appSettings.preferLiteRtLmGpu.collectAsState()
        val preferNnapi by appSettings.preferNnapi.collectAsState()

        GlassCard {
            GlassSectionLabel("HARDWARE ACCELERATION")
            Text(
                "Optimize on-device neural processing delegates for local LiteRT and ONNX models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // LiteRT GPU switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LiteRT-LM GPU Delegate", style = MaterialTheme.typography.titleSmall, color = VestraColors.Ink)
                    Text(
                        "Accelerate Gemma 4 & Qwen3 via Adreno/Mali OpenCL GPU shaders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferLiteRtGpu,
                    onCheckedChange = appSettings::setPreferLiteRtLmGpu,
                )
            }

            Spacer(Modifier.height(10.dp))

            // NNAPI Delegate switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NNAPI Hardware Delegate", style = MaterialTheme.typography.titleSmall, color = VestraColors.Ink)
                    Text(
                        "Route segmentation and ONNX operations through dedicated NPU cores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferNnapi,
                    onCheckedChange = appSettings::setPreferNnapi,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    // 2. On-Device Models List (with Validate & Delete actions)
    item(key = "ondevice-models-list-header") {
        GlassCard {
            GlassSectionLabel("INSTALLED ON-DEVICE MODELS")
            Text(
                "Manage local model weights stored on your device. Selected models run fully offline with zero internet required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    localPackChoices.distinctBy { it.packId ?: it.id }.forEachIndexed { index, entry ->
        val packId = entry.packId
        if (packId != null) {
            item(key = "model-pack-${entry.id}_${packId}_$index") {
                val packState = packStates[packId]
                OnDeviceModelCard(
                    entry = entry,
                    packState = packState,
                    packManager = packManager,
                    onStartDownload = { startDownload(packId) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun OnDeviceModelCard(
    entry: LocalModelEntry,
    packState: PackState?,
    packManager: ModelPackManager,
    onStartDownload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var validating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<String?>(null) }

    val status = packState?.status ?: PackStatus.NOT_INSTALLED
    val isInstalled = status == PackStatus.INSTALLED
    val isDownloading = status == PackStatus.DOWNLOADING
    val progress = packState?.progress ?: 0f
    val verifyStatus = packState?.verifyStatus ?: PackVerifyStatus.UNKNOWN

    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VestraColors.SurfaceRaised)
            .border(
                1.dp,
                if (isInstalled) VestraColors.Accent.copy(alpha = 0.45f) else VestraColors.GlassBorder,
                shape,
            )
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = VestraColors.Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VestraColors.Accent.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            entry.engineTier?.name ?: "ON-DEVICE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                            color = VestraColors.Accent,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Status & Metadata Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Size: ${entry.approxSizeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.InkMuted,
                )
                Text("·", style = MaterialTheme.typography.labelSmall, color = VestraColors.InkMuted)
                Text(
                    entry.license,
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.InkMuted,
                    maxLines = 1,
                )
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            isInstalled -> VestraColors.Accent.copy(alpha = 0.15f)
                            isDownloading -> VestraColors.AccentSoft.copy(alpha = 0.15f)
                            else -> VestraColors.GlassFill
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = when {
                        isInstalled -> "Ready & Installed"
                        isDownloading -> "Downloading ${(progress * 100).toInt()}%"
                        else -> "Not Installed"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isInstalled -> VestraColors.Accent
                            isDownloading -> VestraColors.AccentSoft
                            else -> VestraColors.InkMuted
                        },
                    ),
                )
            }
        }

        // Live Download Progress Bar
        if (isDownloading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = VestraColors.Accent,
                trackColor = VestraColors.Accent.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
            )
        }

        // Validation outcome text
        validationResult?.let { msg ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(msg, style = MaterialTheme.typography.labelSmall, color = VestraColors.Accent)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Action Buttons Row: Validate, Delete, Download
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isInstalled) {
                // Validate Button
                OutlinedButton(
                    onClick = {
                        if (validating) return@OutlinedButton
                        validating = true
                        scope.launch {
                            val ok = entry.packId?.let { packManager.handshake(it).ok } ?: false
                            validating = false
                            validationResult = if (ok) "Integrity verified (SHA-256 Valid)" else "Validation failed"
                            Toast.makeText(context, if (ok) "Model pack verified" else "Model verification failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !validating,
                    modifier = Modifier.height(34.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    if (validating) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = VestraColors.Accent)
                        Spacer(Modifier.width(4.dp))
                    } else {
                        Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp), tint = VestraColors.Accent)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Validate", fontSize = 12.sp)
                }

                Spacer(Modifier.width(8.dp))

                // Delete Button
                OutlinedButton(
                    onClick = {
                        entry.packId?.let { id ->
                            val deleted = packManager.uninstall(id)
                            if (deleted) {
                                validationResult = null
                                Toast.makeText(context, "Deleted ${entry.displayName} to free storage", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Model is currently in use", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.height(34.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = VestraColors.Danger)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp, color = VestraColors.Danger)
                }
            } else if (!isDownloading) {
                // Download Button
                Button(
                    onClick = onStartDownload,
                    modifier = Modifier.height(34.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download Pack", fontSize = 12.sp)
                }
            }
        }
    }
}
