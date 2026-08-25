package com.zakir.vestra.shared.chat

import com.zakir.vestra.shared.testutil.TestMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRepositoryTest {

    @Test
    fun appendPersistsAndBuildsLlmContext() {
        val repo = ChatRepository(TestMemorySettings())
        repo.append("user", "Hello")
        repo.append("assistant", "Hi there", "groq-llama", ttftMs = 120L, durationMs = 350L)
        assertEquals(2, repo.messages.value.size)
        val ctx = repo.contextForLlm(maxTurns = 4)
        assertEquals(2, ctx.size)
        assertEquals("user" to "Hello", ctx[0])
        assertEquals(120L, repo.messages.value[1].ttftMs)
        assertEquals(350L, repo.messages.value[1].durationMs)
        repo.clear()
        assertEquals(0, repo.messages.value.size)
    }

    @Test
    fun moduleIsolationAndContextSwitching() {
        val settings = TestMemorySettings()
        val repo = ChatRepository(settings)

        // Append to default "news" module
        repo.append("user", "News query 1", moduleId = ChatRepository.DEFAULT_MODULE)
        repo.append("assistant", "News response 1", moduleId = ChatRepository.DEFAULT_MODULE)
        assertEquals(2, repo.messages.value.size)

        // Switch to "code" module
        repo.switchModule(ChatRepository.MODULE_CODE)
        assertEquals(ChatRepository.MODULE_CODE, repo.activeModule.value)
        assertEquals(0, repo.messages.value.size)

        // Append to "code" module
        repo.append("user", "Write Kotlin code", moduleId = ChatRepository.MODULE_CODE)
        assertEquals(1, repo.messages.value.size)
        assertEquals("Write Kotlin code", repo.messages.value.first().text)

        // Switch to "image" module
        repo.switchModule(ChatRepository.MODULE_IMAGE)
        assertEquals(0, repo.messages.value.size)
        repo.append("user", "Generate modest abaya prompt", moduleId = ChatRepository.MODULE_IMAGE)
        assertEquals(1, repo.messages.value.size)

        // Switch back to "news" module and verify cached messages remain intact
        repo.switchModule(ChatRepository.DEFAULT_MODULE)
        assertEquals(2, repo.messages.value.size)
        assertEquals("News query 1", repo.messages.value[0].text)
        assertEquals("News response 1", repo.messages.value[1].text)

        // Switch back to "code" and verify cached messages remain intact
        repo.switchModule(ChatRepository.MODULE_CODE)
        assertEquals(1, repo.messages.value.size)
        assertEquals("Write Kotlin code", repo.messages.value.first().text)

        // Verify persistence across repository instances
        val newRepoInstance = ChatRepository(settings)
        newRepoInstance.switchModule(ChatRepository.MODULE_CODE)
        assertEquals(1, newRepoInstance.messages.value.size)
        assertEquals("Write Kotlin code", newRepoInstance.messages.value.first().text)

        newRepoInstance.switchModule(ChatRepository.DEFAULT_MODULE)
        assertEquals(2, newRepoInstance.messages.value.size)

        // Clear only code module
        newRepoInstance.clear(ChatRepository.MODULE_CODE)
        assertEquals(0, newRepoInstance.messagesForModule(ChatRepository.MODULE_CODE).size)
        assertEquals(2, newRepoInstance.messagesForModule(ChatRepository.DEFAULT_MODULE).size)
    }
}
