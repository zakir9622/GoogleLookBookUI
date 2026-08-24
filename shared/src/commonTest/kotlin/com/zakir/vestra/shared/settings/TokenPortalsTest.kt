package com.zakir.vestra.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenPortalsTest {
    @Test
    fun detectsHfGroqOpenRouter() {
        assertEquals(TokenPortals.Kind.HF, TokenPortals.detectClipboardToken("hf_abc12345")?.first)
        assertEquals(TokenPortals.Kind.GROQ, TokenPortals.detectClipboardToken("gsk_abc12345")?.first)
        assertEquals(TokenPortals.Kind.OPENROUTER, TokenPortals.detectClipboardToken("sk-or-v1-abcdefghijklmnop")?.first)
    }

    @Test
    fun rejectsNoise() {
        assertNull(TokenPortals.detectClipboardToken("hello"))
        assertNull(TokenPortals.detectClipboardToken(""))
    }
}
