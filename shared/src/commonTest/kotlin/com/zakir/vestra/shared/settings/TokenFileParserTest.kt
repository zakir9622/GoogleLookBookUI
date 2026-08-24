package com.zakir.vestra.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenFileParserTest {

    @Test
    fun parsesCanonicalJson() {
        val t = TokenFileParser.parse(
            """{"hfToken":"hf_abc","groqApiKey":"gsk_xyz","openRouterApiKey":"sk-or-v1-1234567890abcdef"}""",
        )
        assertEquals("hf_abc", t.hfToken)
        assertEquals("gsk_xyz", t.groqApiKey)
        assertEquals("sk-or-v1-1234567890abcdef", t.openRouterApiKey)
        assertTrue(t.anyPresent())
    }

    @Test
    fun parsesEnvAliasJson() {
        val t = TokenFileParser.parse(
            """{"HF_TOKEN":"hf_from_env","GROQ_API_KEY":"gsk_from_env"}""",
        )
        assertEquals("hf_from_env", t.hfToken)
        assertEquals("gsk_from_env", t.groqApiKey)
        assertNull(t.openRouterApiKey)
    }

    @Test
    fun parsesPlainEnvLinesAndBareTokens() {
        val t = TokenFileParser.parse(
            """
            # comments ignored
            HF_TOKEN=hf_plain
            GROQ_API_KEY = gsk_plain
            sk-or-v1-abcdefghijklmnop
            """.trimIndent(),
        )
        assertEquals("hf_plain", t.hfToken)
        assertEquals("gsk_plain", t.groqApiKey)
        assertEquals("sk-or-v1-abcdefghijklmnop", t.openRouterApiKey)
    }

    @Test
    fun parsesSingleBareHfToken() {
        val t = TokenFileParser.parse("hf_onlytoken123")
        assertEquals("hf_onlytoken123", t.hfToken)
        assertNull(t.groqApiKey)
    }
}
