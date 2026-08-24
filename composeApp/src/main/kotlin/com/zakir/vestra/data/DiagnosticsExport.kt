package com.zakir.vestra.data

import android.content.Context
import com.zakir.vestra.BuildConfig
import com.zakir.vestra.diagnostics.CrashReporter
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.usage.UsageLedger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DiagnosticsExport {
    data class ShareBundle(
        val troubleshootingText: String,
        val runHistoryJson: String,
        val datedJsonFile: File?,
    )

    /**
     * Captures logcat **once**, writes files, and builds share text.
     * Call from a background dispatcher — logcat wait can ANR the main thread.
     */
    fun prepareShareBundle(
        context: Context,
        diagnostics: RunDiagnostics,
        usage: UsageLedger? = null,
    ): ShareBundle {
        val logcat = captureLogcatSnippet()
        val bundle = diagnostics.exportBundle(
            usage = usage?.summary?.value,
            logcatSnippet = logcat,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        File(dir, "run_history.json").writeText(bundle)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val dated = File(dir, "lookbook-diagnostics-$stamp.json")
        dated.writeText(bundle)
        val text = CrashReporter.troubleshootingText(
            runHistoryJson = bundle,
            logcatSnippet = logcat,
        )
        File(dir, "troubleshooting-$stamp.txt").writeText(text)
        return ShareBundle(
            troubleshootingText = text,
            runHistoryJson = bundle,
            datedJsonFile = dated,
        )
    }

    fun writeToFilesDir(
        context: Context,
        diagnostics: RunDiagnostics,
        usage: UsageLedger? = null,
    ): File {
        return prepareShareBundle(context, diagnostics, usage).datedJsonFile
            ?: File(context.filesDir, "diagnostics/run_history.json")
    }

    fun shareTroubleshootingText(
        diagnostics: RunDiagnostics,
        usage: UsageLedger?,
    ): String {
        val logcat = captureLogcatSnippet()
        val bundle = diagnostics.exportBundle(
            usage = usage?.summary?.value,
            logcatSnippet = logcat,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
        return CrashReporter.troubleshootingText(
            runHistoryJson = bundle,
            logcatSnippet = logcat,
        )
    }

    /**
     * Best-effort recent logcat (warnings+). Returns null if the process cannot run
     * (restricted devices / missing READ_LOGS). Never throws into the share path.
     */
    fun captureLogcatSnippet(maxLines: Int = 200, maxChars: Int = 48_000): String? =
        runCatching {
            val proc = ProcessBuilder("logcat", "-d", "-t", maxLines.toString(), "*:W")
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@runCatching null
            }
            val text = proc.inputStream.bufferedReader().use { it.readText() }.trim()
            if (text.isBlank()) null else text.take(maxChars)
        }.getOrNull()
}
