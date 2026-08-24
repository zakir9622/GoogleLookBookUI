package com.zakir.vestra.shared.engine.local

/**
 * Optional callbacks for FunctionGemma local tools — registered by [GenerativeViewModel]
 * when the studio is active; no-ops until wired.
 */
object LocalStudioToolBridge {
    var onAppendPrompt: (String) -> Unit = {}
    var onSetEngineTier: (String) -> Unit = {}
    var onSetBackdrop: (String) -> Unit = {}
}
