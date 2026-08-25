package com.zakir.vestra.diagnostics

/**
 * Maps stack traces / abrupt-exit hints to actionable Lookbook causes.
 * Pure Kotlin — unit-tested without Android.
 */
object CrashClassifier {
    fun classify(throwable: Throwable, stack: String = throwable.stackTraceToString()): String {
        val msg = (throwable.message ?: "").lowercase()
        val name = throwable.javaClass.name
        val s = stack.lowercase()
        return when {
            throwable is OutOfMemoryError || name.contains("OutOfMemory") || msg.contains("out of memory") ->
                "OutOfMemory — Pro pack / large bitmap; free RAM or use Fast try-on"
            s.contains("onnxruntime") || s.contains("ortsession") || s.contains("ortmodel") ||
                s.contains("ortgraph") || s.contains("nodeinfo") ->
                "ONNX Runtime — pack corrupt, R8 keep missing, or in-use; reinstall app / re-download pack"
            s.contains("cancellationexception") || msg.contains("standaloneCoroutine was cancelled") ->
                "Job cancelled — usually back-press during generation (not a hard crash)"
            s.contains("native method") && (s.contains("libc") || msg.contains("signal")) ->
                "Native crash — often ORT/NNAPI; try again or reinstall lite/pro pack"
            name.contains("NullPointerException") ->
                "NullPointerException — missing state; share troubleshooting bundle from Diagnostics"
            name.contains("IllegalStateException") && s.contains("compose") ->
                "Compose IllegalState — UI race; include app_trace + last screen breadcrumb"
            s.contains("sqlite") || s.contains("disk") || msg.contains("enospc") || msg.contains("no space") ->
                "Storage — free space then retry pack download"
            s.contains("unknownhost") || s.contains("sslhandshake") ||
                (s.contains("network") && s.contains("exception")) ->
                "Network — offline or TLS; local packs should still work"
            else -> "Unhandled ${throwable.javaClass.simpleName} — see crash_log.txt stack"
        }
    }

    /**
     * Classify a process death that left no Java uncaught exception
     * (native SIGSEGV / LMK / ORT abort). Used by the session watchdog.
     */
    fun classifyAbrupt(
        breadcrumb: String,
        logcatHints: String = "",
        lowMemorySeen: Boolean = false,
    ): String {
        val screen = breadcrumb.lowercase()
        val hints = logcatHints.lowercase()
        return when {
            hints.contains("out of memory") || hints.contains("outofmemory") ||
                hints.contains("lowmemorykiller") || hints.contains("lmk") ||
                (lowMemorySeen && riskyScreen(screen)) ->
                "Process killed (OOM/LMK) — often loading ONNX packs; free RAM or use lite pack"
            hints.contains("signal 11") || hints.contains("sigsegv") ||
                hints.contains("signal 6") || hints.contains("sigabrt") ||
                hints.contains("tombstone") || hints.contains("fatal signal") ||
                hints.contains("debuggee is dying") ->
                "Native crash (signal) — often ORT/NNAPI; re-download pack or disable NNAPI"
            hints.contains("nodeinfo") ||
                (hints.contains("nosuchmethoderror") && hints.contains("onnx")) ->
                "ONNX Runtime R8 mismatch (NodeInfo) — reinstall this build; keep rules must retain ai.onnxruntime.*"
            hints.contains("onnxruntime") || hints.contains("nnapi") ||
                screen.contains("packs") || screen.contains("engines") ||
                screen.contains("garment") ->
                "Abrupt exit during ONNX/pack/garment work — UncaughtExceptionHandler cannot catch native kills; " +
                    "retry after reboot or reinstall pack"
            riskyScreen(screen) ->
                "Abrupt process exit on $breadcrumb — likely native kill; share new troubleshooting bundle"
            else ->
                "Abrupt process exit (no Java stack) — last screen=$breadcrumb; " +
                    "may be OS LMK while backgrounded"
        }
    }

    /** Screens where native OOM / ORT death is common enough to surface as LAST CRASH. */
    fun riskyScreen(breadcrumb: String): Boolean {
        val s = breadcrumb.lowercase()
        return s.contains("packs") ||
            s.contains("engines") ||
            s.contains("studio") ||
            s.contains("tryon") ||
            s.contains("garment") ||
            s.contains("wardrobe") ||
            s.contains("generate") ||
            s.contains("person")
    }

    /** True when abrupt exit should update last_crash.json (banner), not only crash_log. */
    fun abruptIsActionable(
        breadcrumb: String,
        logcatHints: String = "",
        lowMemorySeen: Boolean = false,
    ): Boolean {
        val hints = logcatHints.lowercase()
        if (lowMemorySeen) return true
        if (riskyScreen(breadcrumb)) return true
        if (hints.contains("signal") || hints.contains("tombstone") ||
            hints.contains("fatal") || hints.contains("outofmemory") ||
            hints.contains("lowmemorykiller") || hints.contains("onnxruntime") ||
            hints.contains("nnapi")
        ) {
            return true
        }
        return false
    }
}
