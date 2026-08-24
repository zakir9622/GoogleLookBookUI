package com.zakir.vestra.shared.engine.local

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Demo local tools for FunctionGemma (L4) — wired only in debug when pack installed.
 * Maps to studio assist actions without cloud.
 */
class LookbookStudioToolSet(
    private val onAppendPrompt: (String) -> Unit = {},
    private val onSetEngineTier: (String) -> Unit = {},
    private val onSetBackdrop: (String) -> Unit = {},
) : ToolSet {

    @Tool(description = "Append a clause to the current studio prompt composer.")
    fun appendPromptClause(
        @ToolParam(description = "Text to append to the prompt") clause: String,
    ): Map<String, String> {
        onAppendPrompt(clause.trim())
        return mapOf("status" to "appended", "clause" to clause.trim())
    }

    @Tool(description = "Set the try-on engine tier (AUTO, LITE, or PRO).")
    fun setEngineTier(
        @ToolParam(description = "Tier name: AUTO, LITE, or PRO") tier: String,
    ): Map<String, String> {
        onSetEngineTier(tier.uppercase())
        return mapOf("status" to "set", "tier" to tier.uppercase())
    }

    @Tool(description = "Set a try-on backdrop description.")
    fun setBackdrop(
        @ToolParam(description = "Backdrop scene description") backdrop: String,
    ): Map<String, String> {
        onSetBackdrop(backdrop.trim())
        return mapOf("status" to "set", "backdrop" to backdrop.trim())
    }
}
