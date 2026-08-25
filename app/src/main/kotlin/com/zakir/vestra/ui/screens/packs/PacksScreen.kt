package com.zakir.vestra.ui.screens.packs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.domain.DeviceSpec
import com.zakir.vestra.shared.domain.PackState
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.domain.PackVerifyStatus
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.packs.PackDownloadWorker
import com.zakir.vestra.shared.packs.PackHandshakeResult
import com.zakir.vestra.shared.packs.PackHandshakeWires
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.storage.DurableStorage
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassScreen
import com.zakir.vestra.ui.util.rememberPackDownloadStarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Model pack management: install/update/remove the engine packs. */
@Composable
fun PacksScreen(
    packManager: ModelPackManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val states by packManager.states.collectAsState()
    val lastError by packManager.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    val startDownload = rememberPackDownloadStarter(showToast = true)
    var durableReady by remember { mutableStateOf(DurableStorage.hasAllFilesAccess()) }
    // The pack currently being handshaked, or null when idle — one at a time (each check opens
    // real ONNX/LiteRT sessions), but tracked per-pack so only the row actually being checked
    // shows "Verifying…"; a single shared boolean made every pack row look busy simultaneously.
    var handshakingPackId by remember { mutableStateOf<String?>(null) }
    val handshakeBusy = handshakingPackId != null
    var handshakeBanner by remember { mutableStateOf<String?>(null) }
    var handshakeBannerOk by remember { mutableStateOf<Boolean?>(null) }
    var packHandshake by remember { mutableStateOf<Map<String, PackHandshakeResult>>(emptyMap()) }

    LaunchedEffect(Unit) {
        durableReady = DurableStorage.hasAllFilesAccess()
        packManager.refresh()
    }

    // Finish packs left on "verification pending" (e.g. pre-.onnx_ok installs).
    LaunchedEffect(states) {
        val pendingIds = states.values
            .filter {
                it.status == PackStatus.INSTALLED &&
                    it.verifyStatus == PackVerifyStatus.UNKNOWN
            }
            .map { it.pack.id }
        if (pendingIds.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            pendingIds.forEach { id -> packManager.verifyInstalled(id) }
        }
    }

    fun runHandshake(packId: String) {
        if (handshakeBusy) return
        scope.launch {
            handshakingPackId = packId
            handshakeBanner = "Handshaking $packId…"
            handshakeBannerOk = null
            val result = withContext(Dispatchers.Default) { packManager.handshake(packId) }
            packHandshake = packHandshake + (packId to result)
            handshakingPackId = null
            handshakeBannerOk = result.ok
            handshakeBanner = PackHandshakeWires.formatDetail(result)
            Toast.makeText(context, PackHandshakeWires.formatUserSummary(result), Toast.LENGTH_SHORT).show()
        }
    }

    fun runHandshakeAll() {
        if (handshakeBusy) return
        scope.launch {
            handshakeBanner = "Handshaking all installed packs…"
            handshakeBannerOk = null
            val report = withContext(Dispatchers.Default) {
                packManager.handshakeAll(onPackStarted = { id -> handshakingPackId = id })
            }
            packHandshake = report.results.associateBy { it.packId }
            handshakingPackId = null
            handshakeBannerOk = report.allOk && report.results.isNotEmpty()
            handshakeBanner = report.summary
            Toast.makeText(context, report.summary, Toast.LENGTH_LONG).show()
        }
    }

    GlassScreen(
        title = LookbookCopy.STUDIO_PACKS,
        subtitle = "On-device · resumable · survives reinstall",
        onBack = onBack,
    ) {
        Text(
            "Download open-source packs once. Transfers resume after network drops. Installed packs work offline with \$0 tokens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (durableReady) {
                "Durable: Documents/TheLookbook/packs — survives uninstall. Reinstall detects packs automatically."
            } else {
                "Enable durable storage (all-files access) so multi-GB packs survive uninstall/reinstall."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!durableReady) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    runCatching { context.startActivity(DurableStorage.manageAllFilesIntent(context)) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enable durable storage now")
            }
        }
        val installedCount = states.values.count { it.status == PackStatus.INSTALLED }
        if (installedCount > 0) {
            val usedBytes = states.values
                .filter { it.status == PackStatus.INSTALLED }
                .sumOf { it.pack.totalBytes }
            val freeBytes = remember(states) { packManager.freeBytesOnDevice() }
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatBytes(usedBytes)} used across $installedCount pack" +
                    (if (installedCount == 1) "" else "s") + " · ${formatBytes(freeBytes)} free",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = ::runHandshakeAll,
            enabled = !handshakeBusy && states.values.any { it.status == PackStatus.INSTALLED },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (handshakeBusy) "Handshaking…" else "Verify all device links")
        }
        handshakeBanner?.let { banner ->
            Spacer(Modifier.height(8.dp))
            Text(
                banner,
                style = MaterialTheme.typography.labelMedium,
                color = when (handshakeBannerOk) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        if (states.isEmpty()) {
            Text(
                lastError?.let { "Couldn't load the pack catalog — $it" }
                    ?: "Couldn't load the pack catalog. Connect once to fetch it — installed packs keep working offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { scope.launch { packManager.refresh() } }) { Text("Retry") }
        }

        lastError?.takeIf { states.isNotEmpty() }?.let { err ->
            GlassCard {
                Text(
                    "Catalog refresh issue — $err. Showing cached packs. Downloads still resume from partial files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        states.values.forEach { state ->
            Spacer(Modifier.height(12.dp))
            PackCard(
                state = state,
                incompatibleChecklist = if (state.status == PackStatus.INCOMPATIBLE) {
                    deviceRequirementChecklist(state.pack.minSpec, packManager)
                } else {
                    null
                },
                handshake = packHandshake[state.pack.id],
                handshakeBusy = handshakeBusy,
                handshakingThisPack = handshakingPackId == state.pack.id,
                onInstall = { startDownload(state.pack.id) },
                onHandshake = { runHandshake(state.pack.id) },
                onCancel = {
                    PackDownloadWorker.cancel(context, state.pack.id)
                    packManager.markCancelled(state.pack.id)
                    Toast.makeText(context, "Download force-stopped — tap Download to resume", Toast.LENGTH_SHORT).show()
                },
                onUninstall = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { packManager.uninstall(state.pack.id) }
                        if (!ok) {
                            Toast.makeText(
                                context,
                                "Can't remove ${state.pack.displayName} while a generation is running",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            packHandshake = packHandshake - state.pack.id
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PackCard(
    state: PackState,
    incompatibleChecklist: String?,
    handshake: PackHandshakeResult?,
    handshakeBusy: Boolean,
    handshakingThisPack: Boolean,
    onInstall: () -> Unit,
    onHandshake: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
) {
    GlassCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                state.pack.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatBytes(state.pack.totalBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            state.pack.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.pack.devOnly) {
            Spacer(Modifier.height(8.dp))
            Text(
                "DEV ONLY — " + (
                    state.pack.licenseNotice
                        ?: "Research-licensed weights. Private testing only; never ships in the published app."
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        when (state.status) {
            PackStatus.NOT_INSTALLED -> Button(
                onClick = onInstall,
                modifier = Modifier.testTag(TestTags.packInstallButton(state.pack.id)),
            ) {
                Text(if (state.progress > 0f) "Resume download" else "Download")
            }
            PackStatus.UPDATE_AVAILABLE -> Button(
                onClick = onInstall,
                modifier = Modifier.testTag(TestTags.packInstallButton(state.pack.id)),
            ) { Text("Update") }
            PackStatus.DOWNLOADING -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${(state.progress * 100).toInt()}% · resumes if network drops",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onCancel) { Text("Force stop") }
                }
            }
            PackStatus.INSTALLED -> Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        state.verifyLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            state.isReady() -> MaterialTheme.colorScheme.primary
                            state.verifyError != null -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                    )
                    OutlinedButton(onClick = onUninstall) { Text("Remove") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onHandshake,
                    enabled = !handshakeBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.packHandshakeButton(state.pack.id)),
                ) {
                    Text(if (handshakingThisPack) "Verifying…" else "Verify device link")
                }
                handshake?.let { hs ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        PackHandshakeWires.formatDetail(hs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hs.ok) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            PackStatus.INCOMPATIBLE -> Column {
                Text(
                    "This device doesn't meet the pack's requirements:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    incompatibleChecklist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
    else -> "%.0f KB".format(bytes / 1e3)
}

/** Scannable RAM/SDK/NPU checklist for PackStatus.INCOMPATIBLE — replaces one terse line. */
private fun deviceRequirementChecklist(spec: DeviceSpec, packManager: ModelPackManager): String {
    val lines = mutableListOf<String>()
    val ramMb = packManager.deviceRamMb()
    if (spec.minRamMb > 0 && ramMb < spec.minRamMb) {
        lines += "• RAM: have ${ramMb} MB, need ${spec.minRamMb} MB"
    }
    val sdk = packManager.deviceSdkInt()
    if (sdk < spec.minSdk) {
        lines += "• Android version: have API $sdk, need API ${spec.minSdk}+"
    }
    if (spec.requiresNpu && !packManager.deviceHasNpu()) {
        lines += "• Needs a hardware accelerator (NNAPI/QNN) this device doesn't have"
    }
    return if (lines.isEmpty()) {
        "Doesn't meet requirements for a reason not yet surfaced here — try re-downloading."
    } else {
        lines.joinToString("\n")
    }
}
