package com.zakir.vestra.shared.cloud

import kotlin.test.Test
import kotlin.test.assertEquals

class GenerationBatchTest {
    @Test
    fun selectedCandidateFallsBackToFirstAvailableOption() {
        val first = candidate(id = "one", index = 0)
        val second = candidate(id = "two", index = 1)
        val batch = GenerationBatch(
            id = "batch",
            prompt = "editorial portrait",
            createdAtEpochMillis = 100L,
            requestedCandidateCount = 2,
            candidates = listOf(first, second),
            selectedCandidateId = "missing",
        )

        assertEquals(first, batch.selectedCandidate)
    }

    @Test
    fun variationRetainsExactParentCandidate() {
        val variation = candidate(id = "variation", index = 0, parentId = "source")
        assertEquals("source", variation.parentCandidateId)
    }

    private fun candidate(
        id: String,
        index: Int,
        parentId: String? = null,
    ) = GenerationCandidate(
        id = id,
        batchId = "batch",
        path = "/images/$id.png",
        providerId = "local",
        prompt = "editorial portrait",
        createdAtEpochMillis = 100L + index,
        candidateIndex = index,
        candidateCount = 2,
        parentCandidateId = parentId,
        seed = 40L + index,
    )
}
