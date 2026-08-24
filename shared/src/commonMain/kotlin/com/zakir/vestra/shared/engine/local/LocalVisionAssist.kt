package com.zakir.vestra.shared.engine.local

/**
 * Offline vision-language assist — describe garment / reference photos without cloud.
 */
interface LocalVisionAssist {
    fun isReady(): Boolean
    fun describeImage(imagePath: String, question: String): LocalAssistResult
}

sealed class LocalAssistResult {
    data class Ok(val text: String) : LocalAssistResult()
    data class Unavailable(val reason: String) : LocalAssistResult()
}

object UnimplementedLocalVisionAssist : LocalVisionAssist {
    override fun isReady(): Boolean = false
    override fun describeImage(imagePath: String, question: String): LocalAssistResult =
        LocalAssistResult.Unavailable("Vision assist not wired on this platform.")
}
