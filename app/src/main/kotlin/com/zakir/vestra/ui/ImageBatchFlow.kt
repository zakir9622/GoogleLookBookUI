package com.zakir.vestra.ui

import com.zakir.vestra.shared.cloud.GenerationBatch
import com.zakir.vestra.shared.cloud.GenerationCandidate
import com.zakir.vestra.shared.cloud.GenerativeAssists
import com.zakir.vestra.shared.cloud.GenerativeCloudService
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.cloud.ReferenceReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Creative Studio V2 adapter over the existing single-image provider contract.
 *
 * Candidates run sequentially so local engines and lower-memory devices never load several
 * diffusion jobs concurrently. Providers that only support one image remain compatible, while
 * the UI receives a durable [GenerationBatch] with exact parent and seed lineage.
 */
internal fun generateImageBatch(
    service: GenerativeCloudService,
    prompt: String,
    referenceUri: String?,
    assists: GenerativeAssists,
    candidateCount: Int,
    parentCandidateId: String? = null,
    nowMillis: () -> Long = System::currentTimeMillis,
    newId: () -> String = { java.util.UUID.randomUUID().toString() },
): Flow<GenerativeState> = flow {
    val requestedCount = candidateCount.coerceIn(1, 4)
    val batchId = newId()
    val batchStartedAt = nowMillis()
    val baseSeed = assists.seed ?: batchStartedAt
    val referenceReceipt = referenceUri?.takeIf { it.isNotBlank() }?.let { sourceUri ->
        ReferenceReceipt(
            sourceUri = sourceUri,
            requestMode = "image-to-image",
            attachedAtEpochMillis = batchStartedAt,
        )
    }
    val completed = mutableListOf<GenerationCandidate>()
    var lastFailure: GenerativeState.Failed? = null

    for (index in 0 until requestedCount) {
        val candidateSeed = baseSeed + index * SEED_STRIDE
        var ready: GenerativeState.ImageReady? = null
        var failed: GenerativeState.Failed? = null

        try {
            service.generateImage(
                prompt = prompt,
                referenceUri = referenceUri,
                assists = assists.copy(seed = candidateSeed),
            ).collect { next ->
                when (next) {
                    is GenerativeState.Preparing -> emit(
                        GenerativeState.Running(
                            fraction = index.toFloat() / requestedCount,
                            stage = "Candidate ${index + 1}/$requestedCount · ${next.message}",
                        ),
                    )
                    is GenerativeState.Running -> emit(
                        next.copy(
                            fraction = ((index + next.fraction.coerceIn(0f, 1f)) / requestedCount)
                                .coerceIn(0f, 0.99f),
                            stage = "Candidate ${index + 1}/$requestedCount · ${next.stage}",
                        ),
                    )
                    is GenerativeState.ImageReady -> ready = next
                    is GenerativeState.Failed -> failed = next
                    else -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }

        val single = ready
        if (single != null) {
            completed += GenerationCandidate(
                id = newId(),
                batchId = batchId,
                path = single.path,
                providerId = single.providerId,
                prompt = prompt,
                createdAtEpochMillis = nowMillis(),
                candidateIndex = index,
                candidateCount = requestedCount,
                parentCandidateId = parentCandidateId,
                seed = candidateSeed,
                referenceReceipt = referenceReceipt,
            )
        } else {
            lastFailure = failed ?: GenerativeState.Failed("Candidate ${index + 1} did not return an image.")
            if (completed.isEmpty()) break
        }
    }

    if (completed.isEmpty()) {
        emit(lastFailure ?: GenerativeState.Failed("No image candidates were generated."))
        return@flow
    }

    emit(
        GenerativeState.ImageBatchReady(
            GenerationBatch(
                id = batchId,
                prompt = prompt,
                createdAtEpochMillis = batchStartedAt,
                requestedCandidateCount = requestedCount,
                candidates = completed,
                selectedCandidateId = completed.first().id,
                parentCandidateId = parentCandidateId,
            ),
        ),
    )
}

private const val SEED_STRIDE = 9_973L
