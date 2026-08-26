package com.zakir.vestra.cache

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.PersonSource
import com.zakir.vestra.shared.domain.TryOnRequest
import com.zakir.vestra.shared.domain.TryOnResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class CachedTrialMetadata(
    val cacheKey: String,
    val imagePath: String,
    val executedTierName: String,
    val durationMillis: Long,
    val watermarked: Boolean,
    val createdAtEpochMs: Long,
    val providerId: String? = null,
    val garmentUri: String,
    val personIdentifier: String,
    val steps: Int? = null,
    val cfg: Double? = null,
    val seed: Long? = null,
    val garmentDesc: String? = null,
    val autoMask: Boolean = true,
    val autoCrop: Boolean = false,
    val backdropName: String,
    val clothType: String? = null,
)

data class TryOnCacheStats(
    val entryCount: Int,
    val totalSizeBytes: Long,
    val hitCount: Int,
    val savedApiCalls: Int,
) {
    val totalSizeMb: Double get() = (totalSizeBytes / (1024.0 * 1024.0) * 10).toInt() / 10.0
}

/**
 * High-performance disk caching layer integrated with Coil for the Virtual Fitting Studio.
 * Persists rendered trials, tracks API savings, and ensures zero redundant API consumption.
 */
class TryOnDiskCache(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val cacheDir: File = File(context.filesDir, "tryon_trial_cache").apply { mkdirs() }
    private val statsFile: File = File(cacheDir, "_cache_stats.json")

    @Serializable
    private data class PersistedStats(
        val hitCount: Int = 0,
        val savedApiCalls: Int = 0,
    )

    private var hitCount = 0
    private var savedApiCalls = 0

    init {
        loadPersistedStats()
    }

    private fun loadPersistedStats() {
        runCatching {
            if (statsFile.exists()) {
                val stats = json.decodeFromString<PersistedStats>(statsFile.readText())
                hitCount = stats.hitCount
                savedApiCalls = stats.savedApiCalls
            }
        }
    }

    private fun persistStats() {
        runCatching {
            val stats = PersistedStats(hitCount = hitCount, savedApiCalls = savedApiCalls)
            statsFile.writeText(json.encodeToString(stats))
        }
    }

    /**
     * Deterministic cache key generator based on all trial parameters and model sources.
     */
    fun computeCacheKey(request: TryOnRequest, providerId: String? = null): String {
        val personKey = when (val p = request.person) {
            is PersonSource.UserPhoto -> "user_photo:${p.uri.takeLast(80)}"
            is PersonSource.AiModel -> "ai_model:${p.modelId}"
        }
        val garmentKey = "garment:${request.garment.uri.takeLast(80)}:${request.garment.category?.name.orEmpty()}"
        val castingKey = "casting:${request.casting.ethnicity.name}_${request.casting.skinTone.name}_${request.casting.bodyType.name}_${request.casting.hairCoverage.name}"
        val tierKey = "tier:${request.tier.name}:${providerId.orEmpty()}"
        val paramsKey = "params:${request.customSteps ?: 30}_${request.customCfg ?: 2.5}_${request.seed ?: -1}_${request.customGarmentDesc.orEmpty()}_${request.autoMask}_${request.autoCrop}_${request.backdrop.name}_${request.clothType.orEmpty()}"

        val composite = "$personKey|$garmentKey|$castingKey|$tierKey|$paramsKey"
        return "tryon_" + sha256Hex(composite)
    }

    /**
     * Checks if a cached trial result exists on disk for this exact trial request.
     */
    suspend fun get(request: TryOnRequest, providerId: String? = null): TryOnResult? = withContext(Dispatchers.IO) {
        val key = computeCacheKey(request, providerId)
        val imageFile = File(cacheDir, "$key.jpg")
        val metaFile = File(cacheDir, "$key.meta.json")

        if (!imageFile.exists() || !metaFile.exists() || imageFile.length() == 0L) {
            return@withContext null
        }

        return@withContext runCatching {
            val meta = json.decodeFromString<CachedTrialMetadata>(metaFile.readText())
            val tier = runCatching { EngineTier.valueOf(meta.executedTierName) }.getOrDefault(request.tier)

            imageFile.setLastModified(System.currentTimeMillis())
            metaFile.setLastModified(System.currentTimeMillis())

            hitCount++
            if (request.tier == EngineTier.CLOUD || tier == EngineTier.CLOUD) {
                savedApiCalls++
            }
            persistStats()

            // Prime Coil memory/disk cache asynchronously
            preloadCoil(imageFile.absolutePath)

            TryOnResult(
                imagePath = imageFile.absolutePath,
                executedTier = tier,
                durationMillis = meta.durationMillis,
                watermarked = meta.watermarked,
            )
        }.getOrNull()
    }

    /**
     * Persists a newly generated trial result into the disk cache.
     */
    suspend fun put(
        request: TryOnRequest,
        providerId: String? = null,
        result: TryOnResult,
    ): File = withContext(Dispatchers.IO) {
        val key = computeCacheKey(request, providerId)
        val targetImageFile = File(cacheDir, "$key.jpg")
        val metaFile = File(cacheDir, "$key.meta.json")

        runCatching {
            val sourceFile = File(result.imagePath)
            if (sourceFile.exists()) {
                sourceFile.copyTo(targetImageFile, overwrite = true)
            }

            val meta = CachedTrialMetadata(
                cacheKey = key,
                imagePath = targetImageFile.absolutePath,
                executedTierName = result.executedTier.name,
                durationMillis = result.durationMillis,
                watermarked = result.watermarked,
                createdAtEpochMs = System.currentTimeMillis(),
                providerId = providerId,
                garmentUri = request.garment.uri,
                personIdentifier = when (val p = request.person) {
                    is PersonSource.UserPhoto -> p.uri
                    is PersonSource.AiModel -> p.modelId
                },
                steps = request.customSteps,
                cfg = request.customCfg,
                seed = request.seed,
                garmentDesc = request.customGarmentDesc,
                autoMask = request.autoMask,
                autoCrop = request.autoCrop,
                backdropName = request.backdrop.name,
                clothType = request.clothType,
            )
            metaFile.writeText(json.encodeToString(meta))
            evictOldestIfNeeded()

            // Preload saved trial into Coil singleton cache
            preloadCoil(targetImageFile.absolutePath)
        }

        targetImageFile
    }

    /**
     * Preloads and primes Coil's image loader for instant rendering.
     */
    suspend fun preloadCoil(imagePath: String) = withContext(Dispatchers.IO) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            val req = ImageRequest.Builder(context)
                .data(File(imagePath))
                .build()
            loader.enqueue(req)
        }
    }

    fun getStats(): TryOnCacheStats {
        var totalBytes = 0L
        var count = 0
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && !file.name.startsWith("_")) {
                totalBytes += file.length()
                if (file.name.endsWith(".jpg") || file.name.endsWith(".png")) {
                    count++
                }
            }
        }
        return TryOnCacheStats(
            entryCount = count,
            totalSizeBytes = totalBytes,
            hitCount = hitCount,
            savedApiCalls = savedApiCalls,
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && !file.name.startsWith("_stats")) {
                file.delete()
            }
        }
    }

    private fun evictOldestIfNeeded(maxSizeMb: Long = 300) {
        val maxBytes = maxSizeMb * 1024 * 1024
        val files = cacheDir.listFiles()?.filter { it.isFile && !it.name.startsWith("_") } ?: return
        val totalSize = files.sumOf { it.length() }
        if (totalSize <= maxBytes) return

        val sortedByLru = files.sortedBy { it.lastModified() }
        var currentSize = totalSize
        for (file in sortedByLru) {
            if (currentSize <= maxBytes * 0.8) break
            val len = file.length()
            if (file.delete()) {
                currentSize -= len
            }
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
