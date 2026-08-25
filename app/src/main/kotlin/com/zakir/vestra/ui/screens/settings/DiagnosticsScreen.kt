package com.zakir.vestra.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zakir.vestra.BuildConfig
import com.zakir.vestra.data.DiagnosticsExport
import com.zakir.vestra.diagnostics.CrashReporter
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.diagnostics.RunRecord
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassEmptyState
import com.zakir.vestra.ui.components.GlassScreen
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.theme.VestraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsScreen(
    diagnostics: RunDiagnostics,
    usage: com.zakir.vestra.shared.usage.UsageLedger? = null,
    onBack: () -> Unit,
    onOpenHelp: (() -> Unit)? = null,
) {
    val records by diagnostics.records.collectAsState()
    val usageSummary by usage?.summary?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    var crashTick by remember { mutableStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    val pendingCrash = remember(crashTick) { CrashReporter.hasPendingCrash() }
    val crashSummary = remember(crashTick) { CrashReporter.lastCrashSummary() }

    GlassScreen(
        title = "Run diagnostics",
        subtitle = "Crashes · runs · auto-troubleshooting",
        onBack = onBack,
    ) {
        GlassCard {
            GlassSectionLabel("AUTO TROUBLESHOOTING")
            Text(
                "Crashes, abrupt process deaths (native/ORT/LMK), and app traces append and are never " +
                    "cleared automatically. After a death, reopen → Diagnostics to see the likely cause and share the bundle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (pendingCrash && crashSummary != null) {
            GlassCard {
                GlassSectionLabel("LAST CRASH")
                Text(
                    crashSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VestraColors.Accent,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    CrashReporter.lastCrashLikelyCause().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        GlassCard {
            GlassSectionLabel("HOW IT WORKS")
            Text(
                "Lite segments and warps on-device; Pro runs diffusion; cloud uses HF Spaces. " +
                    "Each run records stage timings. Java fatals and unclean exits (session watchdog) " +
                    "write to crash_log.txt with a classified cause.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onOpenHelp?.let { open ->
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
                    Text(LookbookCopy.ACTION_OPEN_HELP)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (records.isEmpty()) {
            GlassEmptyState(
                message = "No runs recorded yet. Try a try-on shoot or generate an image in the studio.",
            )
            Spacer(Modifier.height(12.dp))
        }

        records.take(40).forEach { record ->
            Spacer(Modifier.height(10.dp))
            RunRecordCard(record, fmt)
        }

        Spacer(Modifier.height(16.dp))
        GlassCard {
            GlassSectionLabel("EXPORT")
            Text(
                "Share runs + crash log + app trace + logcat. Crash history is append-only until you clear it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (exporting) {
                Text(
                    "Preparing bundle (logcat + crash files)… keep the app open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VestraColors.Accent,
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = {
                    if (exporting) return@OutlinedButton
                    exporting = true
                    scope.launch {
                        val prepared = withContext(Dispatchers.IO) {
                            DiagnosticsExport.prepareShareBundle(context, diagnostics, usage)
                        }
                        exporting = false
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "${LookbookCopy.PRODUCT_NAME} troubleshooting")
                            putExtra(Intent.EXTRA_TEXT, prepared.troubleshootingText)
                        }
                        context.startActivity(Intent.createChooser(send, "Share troubleshooting bundle"))
                    }
                },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (exporting) "Preparing…" else "Share troubleshooting bundle")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (exporting) return@OutlinedButton
                    exporting = true
                    scope.launch {
                        val prepared = withContext(Dispatchers.IO) {
                            DiagnosticsExport.prepareShareBundle(context, diagnostics, usage)
                        }
                        exporting = false
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "${LookbookCopy.PRODUCT_NAME} run diagnostics")
                            putExtra(Intent.EXTRA_TEXT, prepared.runHistoryJson)
                        }
                        context.startActivity(Intent.createChooser(send, "Export diagnostics"))
                    }
                },
                enabled = records.isNotEmpty() && !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share run history JSON only")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { diagnostics.clear() },
                enabled = records.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear run history")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    CrashReporter.clearCrashHistory()
                    crashTick++
                    Toast.makeText(context, "Crash log cleared", Toast.LENGTH_SHORT).show()
                },
                enabled = pendingCrash || CrashReporter.readCrashLog(64).isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear crash log (manual)")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    CrashReporter.clearAppTrace()
                    Toast.makeText(context, "App trace cleared", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear app trace (manual)")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RunRecordCard(record: RunRecord, fmt: SimpleDateFormat) {
    var expanded by remember(record.id) { mutableStateOf(!record.success) }
    GlassCard(
        modifier = if (!record.success) {
            Modifier.clickable { expanded = !expanded }
        } else {
            Modifier
        },
    ) {
        Text(
            fmt.format(Date(record.timestampMs)),
            style = MaterialTheme.typography.labelSmall,
            color = VestraColors.InkMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            record.capability.replace('_', ' '),
            style = MaterialTheme.typography.titleMedium,
            color = if (record.success) VestraColors.Ink else VestraColors.Accent,
        )
        Text(
            "${record.totalDurationMs} ms · ${if (record.success) "OK" else "FAILED"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        record.modelLabel?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = VestraColors.InkMuted)
        }
        record.tier?.let {
            Text("Tier: $it", style = MaterialTheme.typography.labelSmall, color = VestraColors.InkMuted)
        }
        if (record.stages.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            record.stages.take(if (expanded) 12 else 4).forEach { stage ->
                Text(
                    "• ${stage.name}: ${stage.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.InkMuted,
                )
            }
        }
        record.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = VestraColors.Accent)
        }
        AnimatedVisibility(visible = expanded && !record.success) {
            Spacer(Modifier.height(8.dp))
            GlassSectionLabel("WHAT HAPPENED?")
            Text(
                record.humanSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = VestraColors.InkMuted,
            )
        }
        if (!record.success) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (expanded) "Tap to collapse" else "Tap for details",
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.Accent,
            )
        }
    }
}
