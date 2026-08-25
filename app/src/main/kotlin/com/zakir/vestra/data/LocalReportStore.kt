package com.zakir.vestra.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ReportReason(val label: String) {
    SEXUAL("Sexual content"),
    VIOLENCE("Violence"),
    LIKENESS("Likeness misuse"),
    OTHER("Other"),
}

/**
 * Local-only content reports (Play compliance). Stored on device; no paid backend.
 * User can export/share the report log if needed.
 */
class LocalReportStore(context: Context) {
    private val file = File(context.filesDir, "content_reports.json")

    fun submit(imagePath: String, reason: ReportReason, note: String = "") {
        val arr = load()
        arr.put(
            JSONObject()
                .put("id", System.currentTimeMillis().toString())
                .put("at", System.currentTimeMillis())
                .put("reason", reason.name)
                .put("note", note.take(500))
                .put("imagePath", imagePath)
                .put("appVersion", com.zakir.vestra.BuildConfig.VERSION_NAME),
        )
        file.writeText(arr.toString())
    }

    fun count(): Int = load().length()

    fun exportFile(): File = file

    fun exportJson(): String = load().toString(2)

    private fun load(): JSONArray {
        if (!file.exists()) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
    }
}
