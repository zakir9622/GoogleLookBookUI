package com.zakir.vestra.shared.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log source identifier indicating origin engine/provider.
 */
enum class LogSource(val label: String) {
    LITERT("LiteRT"),
    CLOUD_API("Cloud API"),
    SYSTEM("System"),
}

/**
 * Log severity level.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Individual real-time log entry captured across LiteRT local inference and cloud AI calls.
 */
data class LogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val source: LogSource,
    val level: LogLevel = LogLevel.INFO,
    val tag: String,
    val message: String,
) {
    fun formatDisplay(): String {
        val timeStr = timeFormat.format(Date(timestampMs))
        return "[$timeStr] [${source.label}] $message"
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}

/**
 * LogState manager collecting real-time event streams from the LiteRT engine,
 * on-device model runners, and cloud API providers.
 */
class LogStateManager(
    private val maxCapacity: Int = 100,
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _formattedLines = MutableStateFlow<List<String>>(emptyList())
    val formattedLines: StateFlow<List<String>> = _formattedLines.asStateFlow()

    fun log(
        source: LogSource,
        message: String,
        level: LogLevel = LogLevel.INFO,
        tag: String = "Gen",
    ) {
        val entry = LogEntry(
            source = source,
            level = level,
            tag = tag,
            message = message.trim().take(300),
        )
        val updated = (_entries.value + entry).takeLast(maxCapacity)
        _entries.value = updated
        _formattedLines.value = updated.map { it.formatDisplay() }
    }

    fun info(source: LogSource, message: String, tag: String = "Gen") {
        log(source, message, LogLevel.INFO, tag)
    }

    fun warn(source: LogSource, message: String, tag: String = "Gen") {
        log(source, message, LogLevel.WARN, tag)
    }

    fun error(source: LogSource, message: String, tag: String = "Gen") {
        log(source, message, LogLevel.ERROR, tag)
    }

    fun debug(source: LogSource, message: String, tag: String = "Gen") {
        log(source, message, LogLevel.DEBUG, tag)
    }

    fun clear() {
        _entries.value = emptyList()
        _formattedLines.value = emptyList()
    }
}
