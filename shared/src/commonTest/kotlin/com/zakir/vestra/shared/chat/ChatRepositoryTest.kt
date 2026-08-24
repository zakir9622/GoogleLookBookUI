package com.zakir.vestra.shared.chat

import com.zakir.vestra.shared.testutil.TestMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatRepositoryTest {

    @Test
    fun appendPersistsAndBuildsLlmContext() {
        val repo = ChatRepository(TestMemorySettings())
        repo.append("user", "Hello")
        repo.append("assistant", "Hi there", "groq-llama")
        assertEquals(2, repo.messages.value.size)
        val ctx = repo.contextForLlm(maxTurns = 4)
        assertEquals(2, ctx.size)
        assertEquals("user" to "Hello", ctx[0])
        repo.clear()
        assertEquals(0, repo.messages.value.size)
    }
}
