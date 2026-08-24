package com.zakir.vestra.shared.safety

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InputSafetyGateTest {

    @Test
    fun allowsFashionPrompts() {
        assertIs<SafetyVerdict.Ok>(InputSafetyGate.checkPrompt("modest abaya lookbook editorial"))
    }

    @Test
    fun blocksExplicitPrompts() {
        val verdict = InputSafetyGate.checkPrompt("nude portrait")
        assertIs<SafetyVerdict.Blocked>(verdict)
        assertTrue(verdict.reason.contains("safety filter"))
    }
}
