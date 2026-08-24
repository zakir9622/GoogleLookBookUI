package com.zakir.vestra.shared.wardrobe

import com.zakir.vestra.shared.domain.EngineTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class InMemoryStore : TextFileStore {
    val files = mutableMapOf<String, String>()
    override fun read(name: String): String? = files[name]
    override fun write(name: String, content: String) {
        files[name] = content
    }
}

class WardrobeRepositoryTest {

    private fun entry(id: String) = WardrobeEntry(
        id = id,
        createdAtEpochMillis = 1000L,
        imagePath = "/images/$id.jpg",
        garmentUri = "content://garment/$id",
        personLabel = "AI model",
        tier = EngineTier.LITE,
    )

    @Test
    fun addPersistsAndPrepends() {
        val store = InMemoryStore()
        val repo = WardrobeRepository(store)
        repo.add(entry("a"))
        repo.add(entry("b"))
        assertEquals(listOf("b", "a"), repo.entries.value.map { it.id })
        assertTrue(store.files.getValue("wardrobe_index.json").contains("\"b\""))
    }

    @Test
    fun reloadsFromStore() {
        val store = InMemoryStore()
        WardrobeRepository(store).add(entry("a"))
        val reloaded = WardrobeRepository(store)
        assertEquals(listOf("a"), reloaded.entries.value.map { it.id })
    }

    @Test
    fun removeDeletesEntry() {
        val store = InMemoryStore()
        val repo = WardrobeRepository(store)
        repo.add(entry("a"))
        repo.add(entry("b"))
        repo.remove("a")
        assertEquals(listOf("b"), repo.entries.value.map { it.id })
    }

    @Test
    fun toggleFavoritePinsAndPersists() {
        val store = InMemoryStore()
        val repo = WardrobeRepository(store)
        repo.add(entry("a"))
        repo.add(entry("b"))
        repo.toggleFavorite("a")
        assertEquals(listOf("a", "b"), repo.entries.value.map { it.id })
        assertTrue(repo.entries.value.first { it.id == "a" }.favorited)
        val reloaded = WardrobeRepository(store)
        assertTrue(reloaded.entries.value.first { it.id == "a" }.favorited)
        assertEquals("a", reloaded.entries.value.first().id)
    }

    @Test
    fun corruptIndexFallsBackToEmpty() {
        val store = InMemoryStore()
        store.files["wardrobe_index.json"] = "{not json"
        assertEquals(emptyList(), WardrobeRepository(store).entries.value)
    }

    @Test
    fun parentGenerationIdRoundTripsThroughStorage() {
        val store = InMemoryStore()
        val repo = WardrobeRepository(store)
        repo.add(entry("v1"))
        repo.add(entry("v2").copy(parentGenerationId = "v1"))

        val reloaded = WardrobeRepository(store)
        assertEquals("v1", reloaded.entries.value.first { it.id == "v2" }.parentGenerationId)
        assertEquals(null, reloaded.entries.value.first { it.id == "v1" }.parentGenerationId)
    }
}
