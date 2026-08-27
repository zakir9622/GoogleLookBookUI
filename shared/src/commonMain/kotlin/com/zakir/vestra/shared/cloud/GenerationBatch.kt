package com.zakir.vestra.shared.cloud

import kotlinx.serialization.Serializable

/**
 * One image produced within a Creative Studio generation batch.
 *
 * [parentCandidateId] links variations back to the exact source candidate instead of
 * treating every result as an unrelated gallery item. All fields have durable values so
 * the same record can be persisted in the wardrobe index and reopened later.
 */
@Serializable
data class GenerationCandidate(
    val id: String,
    val batchId: String,
    val path: String,
    val providerId: String,
    val prompt: String,
    val createdAtEpochMillis: Long,
    val candidateIndex: Int,
    val candidateCount: Int,
    val parentCandidateId: String? = null,
    val seed: Long? = null,
)

/** Immutable batch snapshot emitted after the requested candidates have completed. */
@Serializable
data class GenerationBatch(
    val id: String,
    val prompt: String,
    val createdAtEpochMillis: Long,
    val requestedCandidateCount: Int,
    val candidates: List<GenerationCandidate>,
    val selectedCandidateId: String? = candidates.firstOrNull()?.id,
    val parentCandidateId: String? = null,
) {
    val selectedCandidate: GenerationCandidate?
        get() = candidates.firstOrNull { it.id == selectedCandidateId } ?: candidates.firstOrNull()
}
