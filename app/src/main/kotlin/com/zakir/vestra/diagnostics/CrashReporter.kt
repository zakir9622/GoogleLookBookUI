package com.zakir.vestra.diagnostics

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.zakir.vestra.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Auto-troubleshooting: catches fatal crashes, detects abrupt process deaths
 * (native OOM / ORT / LMK that bypass UncaughtExceptionHandler), appends to
 * durable files (never auto-clears), and keeps a rotating app-trace log.
 *
 * Files under `filesDir/diagnostics/`:
 * - `crash_log.txt` — append-only crash dump history
 * - `last_crash.json` — structured latest crash + likelyCause
 * - `app_trace.log` — continuous breadcrumbs / warnings (rotated at ~1.5 MB)
 * - `session.json` — open-session watchdog for abrupt exits
 */
object CrashReporter {
    private const val TAG = "LookbookCrash"
    private const val DIR = "diagnostics"
    private const val CRASH_LOG = "crash_log.txt"
    private const val LAST_CRASH = "last_crash.json"
    private const val APP_TRACE = "app_trace.log"
    private const val APP_TRACE_BAK = "app_trace.log.1"
    private const val SESSION = "session.json"
    private const val MAX_TRACE_BYTES = 1_500_000L
    private const val MAX_CRASH_LOG_BYTES = 4_000_000L

    private val appRef = AtomicReference<Application?>(null)
    private val breadcrumb = AtomicReference("boot")
    private val lock = Any()
    private val startedActivities = AtomicInteger(0)
    private var lowMemorySeen = false

    fun install(app: Application) {
        appRef.set(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { recordFatal(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    try {
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } catch (_: Throwable) {
                    }
                }
        }
        // Detect prior unclean exit BEFORE opening a new session. Snapshot on main,
        // then scrape logcat off-main so Application.onCreate never blocks.
        val priorSession = readSession()
        openSession()
        Thread(
            {
                runCatching { detectAbruptExit(priorSession) }
            },
            "lookbook-abrupt-detect",
        ).apply {
            isDaemon = true
            start()
        }
        registerLifecycle(app)
        registerMemoryCallbacks(app)
        i("CrashReporter", "installed v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        lastCrashJson()?.let { json ->
            val cause = json.optString("likelyCause", "unknown")
            val at = json.optString("isoTime", "?")
            w("CrashReporter", "Previous crash still on disk: $cause @ $at — open Settings → Diagnostics")
        }
    }

    fun breadcrumb(screen: String) {
        breadcrumb.set(screen)
        updateSessionBreadcrumb(screen)
        i("Nav", "screen=$screen")
    }

    fun recordNonFatal(tag: String, throwable: Throwable, detail: String = "") {
        w(tag, "nonfatal ${throwable.javaClass.simpleName}: ${throwable.message} $detail")
        runCatching {
            appendCrashFile(
                header = "NONFATAL",
                threadName = Thread.currentThread().name,
                throwable = throwable,
                detail = detail,
                fatal = false,
            )
        }
    }

    fun i(tag: String, message: String) = appendTrace("I", tag, message).also {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) = appendTrace("W", tag, message).also {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        appendTrace("E", tag, message + (throwable?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    fun hasPendingCrash(): Boolean = lastCrashFile()?.isFile == true

    fun lastCrashSummary(): String? {
        val json = lastCrashJson() ?: return null
        val cause = json.optString("likelyCause", "Crash")
        val msg = json.optString("message", "")
        val at = json.optString("isoTime", "")
        val screen = json.optString("breadcrumb", "")
        return buildString {
            append(cause)
            if (at.isNotBlank()) append(" · $at")
            if (screen.isNotBlank()) append(" · screen=$screen")
            if (msg.isNotBlank()) append("\n$msg")
        }
    }

    fun lastCrashLikelyCause(): String? = lastCrashJson()?.optString("likelyCause")?.takeIf { it.isNotBlank() }

    fun readCrashLog(maxChars: Int = 120_000): String {
        val file = crashLogFile() ?: return ""
        if (!file.isFile) return ""
        return readTail(file, maxChars)
    }

    fun readAppTrace(maxChars: Int = 80_000): String {
        val file = appTraceFile() ?: return ""
        if (!file.isFile) return ""
        return readTail(file, maxChars)
    }

    /** User-initiated only — never called automatically. */
    fun clearCrashHistory() {
        synchronized(lock) {
            crashLogFile()?.delete()
            lastCrashFile()?.delete()
        }
        i("CrashReporter", "crash history cleared by user")
    }

    fun clearAppTrace() {
        synchronized(lock) {
            appTraceFile()?.delete()
            File(diagDir() ?: return, APP_TRACE_BAK).delete()
        }
        i("CrashReporter", "app trace cleared by user")
    }

    fun troubleshootingText(
        runHistoryJson: String? = null,
        logcatSnippet: String? = null,
    ): String = buildString {
        appendLine("=== The Lookbook troubleshooting bundle ===")
        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT}")
        appendLine("breadcrumb=${breadcrumb.get()}")
        appendLine()
        appendLine("--- LAST CRASH (likelyCause) ---")
        appendLine(lastCrashSummary() ?: "(none on disk)")
        appendLine()
        appendLine("--- CRASH LOG (append-only, not auto-cleared) ---")
        appendLine(readCrashLog().ifBlank { "(empty)" })
        appendLine()
        appendLine("--- APP TRACE (tail) ---")
        appendLine(readAppTrace().ifBlank { "(empty)" })
        if (!logcatSnippet.isNullOrBlank()) {
            appendLine()
            appendLine("--- LOGCAT ---")
            appendLine(logcatSnippet)
        }
        if (!runHistoryJson.isNullOrBlank()) {
            appendLine()
            appendLine("--- RUN HISTORY JSON ---")
            appendLine(runHistoryJson.take(60_000))
        }
    }

    // ── session watchdog ───────────────────────────────────────────────────

    private fun detectAbruptExit(priorSession: JSONObject? = readSession()) {
        val session = priorSession ?: return
        if (!session.optBoolean("open", false)) return
        if (session.optBoolean("recordedFatal", false)) return
        // Clean finish already closed the session.
        if (session.optBoolean("clean", false)) return

        val lastScreen = session.optString("breadcrumb", breadcrumb.get()).ifBlank { "unknown" }
        val lowMem = session.optBoolean("lowMemorySeen", false)
        val logcatHints = scrapeFatalLogcatHints()
        val cause = CrashClassifier.classifyAbrupt(lastScreen, logcatHints, lowMem)
        val actionable = CrashClassifier.abruptIsActionable(lastScreen, logcatHints, lowMem)
        val iso = isoNow()
        if (!actionable) {
            w(
                "CrashReporter",
                "Unclean prior session (likely background LMK) · screen=$lastScreen — not elevating to LAST CRASH",
            )
            return
        }
        val block = buildString {
            appendLine()
            appendLine("======== ABRUPT_EXIT $iso ========")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("priorPid=${session.optInt("pid", -1)}")
            appendLine("priorVersion=${session.optString("version", "?")}")
            appendLine("breadcrumb=$lastScreen")
            appendLine("lowMemorySeen=$lowMem")
            appendLine("likelyCause=$cause")
            appendLine("actionable=$actionable")
            if (logcatHints.isNotBlank()) {
                appendLine("--- logcat hints ---")
                appendLine(logcatHints.take(8_000))
            }
            appendLine("======== END ========")
        }
        synchronized(lock) {
            val dir = diagDir() ?: return
            val crashLog = File(dir, CRASH_LOG)
            rotateIfHuge(crashLog, MAX_CRASH_LOG_BYTES)
            crashLog.appendText(block)
            File(dir, LAST_CRASH).writeText(
                JSONObject()
                    .put("isoTime", iso)
                    .put("fatal", true)
                    .put("abrupt", true)
                    .put("thread", "process")
                    .put("breadcrumb", lastScreen)
                    .put("exception", "AbruptProcessExit")
                    .put("message", "No Java stack — process died (native/LMK/ORT)")
                    .put("likelyCause", cause)
                    .put("lowMemorySeen", lowMem)
                    .put("logcatHints", logcatHints.take(2_000))
                    .put("stackTop", "ABRUPT_EXIT (session watchdog)")
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .toString(2),
            )
        }
        w("CrashReporter", "Detected abrupt exit · screen=$lastScreen · $cause")
    }

    private fun openSession() {
        val dir = diagDir() ?: return
        synchronized(lock) {
            File(dir, SESSION).writeText(
                JSONObject()
                    .put("open", true)
                    .put("clean", false)
                    .put("recordedFatal", false)
                    .put("pid", android.os.Process.myPid())
                    .put("startedAt", isoNow())
                    .put("breadcrumb", breadcrumb.get())
                    .put("lowMemorySeen", false)
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .toString(2),
            )
        }
    }

    private fun markSessionClean() {
        mutateSession { json ->
            json.put("open", false)
            json.put("clean", true)
            json.put("closedAt", isoNow())
        }
        i("CrashReporter", "session closed cleanly")
    }

    private fun markSessionFatalRecorded() {
        mutateSession { json ->
            json.put("recordedFatal", true)
            json.put("open", false)
            json.put("clean", false)
        }
    }

    private fun updateSessionBreadcrumb(screen: String) {
        mutateSession { json ->
            json.put("breadcrumb", screen)
            json.put("breadcrumbAt", isoNow())
        }
    }

    private fun markLowMemory(level: String) {
        lowMemorySeen = true
        mutateSession { json ->
            json.put("lowMemorySeen", true)
            json.put("lastTrim", level)
            json.put("lastTrimAt", isoNow())
        }
    }

    private fun mutateSession(block: (JSONObject) -> Unit) {
        synchronized(lock) {
            val dir = diagDir() ?: return
            val file = File(dir, SESSION)
            val json = if (file.isFile) {
                runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
            } else {
                JSONObject()
            }
            block(json)
            file.writeText(json.toString(2))
        }
    }

    private fun readSession(): JSONObject? {
        val f = sessionFile() ?: return null
        if (!f.isFile) return null
        return runCatching { JSONObject(f.readText()) }.getOrNull()
    }

    private fun registerLifecycle(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivities.incrementAndGet()
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                val left = startedActivities.decrementAndGet()
                // Last activity finishing → user left the app intentionally.
                if (left <= 0 && activity.isFinishing) {
                    runCatching { markSessionClean() }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isFinishing && startedActivities.get() <= 0) {
                    runCatching { markSessionClean() }
                }
            }
        })
    }

    private fun registerMemoryCallbacks(app: Application) {
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}

            override fun onLowMemory() {
                markLowMemory("onLowMemory")
                w("Mem", "onLowMemory — process may be killed soon")
            }

            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                val label = trimLabel(level)
                // RUNNING_CRITICAL is 15; UI_HIDDEN is 20 — never use >= CRITICAL alone
                // or every background trim clears ORT sessions (seen in v3.0.12 bundles).
                val severe = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                if (severe) {
                    markLowMemory(label)
                    runCatching {
                        com.zakir.vestra.shared.engine.lite.OrtSessionCache.clearAll()
                    }
                    w("Mem", "onTrimMemory $label ($level) — cleared ORT session cache")
                } else if (
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
                ) {
                    w("Mem", "onTrimMemory $label ($level)")
                }
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun trimLabel(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        else -> "level=$level"
    }

    /**
     * Best-effort scrape for native death lines still in the log buffer after restart.
     * Does not require READ_LOGS for the app's own recent process lines on many devices.
     */
    private fun scrapeFatalLogcatHints(maxChars: Int = 6_000): String {
        // Never block Application.onCreate / main thread — Diagnostics share captures logcat later.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return ""
        val raw = runCatching {
            val proc = ProcessBuilder(
                "logcat", "-d", "-t", "400",
                "*:W",
            ).redirectErrorStream(true).start()
            val finished = proc.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@runCatching null
            }
            proc.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull() ?: return ""

        val keys = listOf(
            "fatal", "fatal signal", "signal 11", "signal 6", "sigsegv", "sigabrt",
            "tombstone", "lowmemorykiller", "killed process", "out of memory",
            "outofmemory", "onnxruntime", "nnapi", "debuggee is dying",
            "has died", "crash_dump", BuildConfig.APPLICATION_ID,
        )
        return raw.lineSequence()
            .filter { line ->
                val lower = line.lowercase()
                keys.any { lower.contains(it.lowercase()) }
            }
            .take(80)
            .joinToString("\n")
            .take(maxChars)
    }

    // ── internals ──────────────────────────────────────────────────────────

    private fun recordFatal(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "FATAL on ${thread.name}", throwable)
        appendCrashFile(
            header = "FATAL",
            threadName = thread.name,
            throwable = throwable,
            detail = "",
            fatal = true,
        )
        runCatching { markSessionFatalRecorded() }
    }

    private fun appendCrashFile(
        header: String,
        threadName: String,
        throwable: Throwable,
        detail: String,
        fatal: Boolean,
    ) {
        val dir = diagDir() ?: return
        val iso = isoNow()
        val stack = stackString(throwable)
        val cause = CrashClassifier.classify(throwable, stack)
        val block = buildString {
            appendLine()
            appendLine("======== $header $iso ========")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("thread=$threadName")
            appendLine("breadcrumb=${breadcrumb.get()}")
            appendLine("likelyCause=$cause")
            if (detail.isNotBlank()) appendLine("detail=$detail")
            appendLine("exception=${throwable.javaClass.name}: ${throwable.message}")
            appendLine(stack)
            appendLine("======== END ========")
        }
        synchronized(lock) {
            val crashLog = File(dir, CRASH_LOG)
            rotateIfHuge(crashLog, MAX_CRASH_LOG_BYTES)
            crashLog.appendText(block)
            if (fatal) {
                File(dir, LAST_CRASH).writeText(
                    JSONObject()
                        .put("isoTime", iso)
                        .put("fatal", true)
                        .put("abrupt", false)
                        .put("thread", threadName)
                        .put("breadcrumb", breadcrumb.get())
                        .put("exception", throwable.javaClass.name)
                        .put("message", throwable.message ?: "")
                        .put("likelyCause", cause)
                        .put("stackTop", stack.lineSequence().take(12).joinToString("\n"))
                        .put("version", BuildConfig.VERSION_NAME)
                        .put("versionCode", BuildConfig.VERSION_CODE)
                        .toString(2),
                )
            }
        }
    }

    private fun appendTrace(level: String, tag: String, message: String) {
        val dir = diagDir() ?: return
        val line = "${isoNow()} $level/$tag: ${message.take(500)}\n"
        synchronized(lock) {
            val file = File(dir, APP_TRACE)
            if (file.length() > MAX_TRACE_BYTES) {
                val bak = File(dir, APP_TRACE_BAK)
                bak.delete()
                file.renameTo(bak)
            }
            file.appendText(line)
        }
    }

    private fun stackString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().take(20_000)
    }

    private fun diagDir(): File? {
        val app = appRef.get() ?: return null
        return File(app.filesDir, DIR).also { it.mkdirs() }
    }

    private fun crashLogFile(): File? = diagDir()?.let { File(it, CRASH_LOG) }
    private fun lastCrashFile(): File? = diagDir()?.let { File(it, LAST_CRASH) }
    private fun appTraceFile(): File? = diagDir()?.let { File(it, APP_TRACE) }
    private fun sessionFile(): File? = diagDir()?.let { File(it, SESSION) }

    private fun lastCrashJson(): JSONObject? {
        val f = lastCrashFile() ?: return null
        if (!f.isFile) return null
        return runCatching { JSONObject(f.readText()) }.getOrNull()
    }

    private fun readTail(file: File, maxChars: Int): String {
        val text = file.readText()
        return if (text.length <= maxChars) text else text.takeLast(maxChars)
    }

    private fun rotateIfHuge(file: File, maxBytes: Long) {
        if (!file.isFile || file.length() <= maxBytes) return
        val bak = File(file.parentFile, file.name + ".1")
        bak.delete()
        file.renameTo(bak)
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
}
