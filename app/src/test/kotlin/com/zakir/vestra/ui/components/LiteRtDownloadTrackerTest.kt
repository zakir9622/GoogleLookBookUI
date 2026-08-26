package com.zakir.vestra.ui.components

import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.ModelPack
import com.zakir.vestra.shared.domain.PackState
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.domain.PackVerifyStatus
import com.zakir.vestra.shared.engine.local.LiteRtLmPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for LiteRT-LM model catalog metadata and download tracking state transitions.
 */
class LiteRtDownloadTrackerTest {

    @Test
    fun catalogContainsAllPrimaryLiteRtModels() {
        val models = LiteRtModelCatalog.allModels
        assertTrue("Catalog should contain at least 4 models", models.size >= 4)

        val qwen = LiteRtModelCatalog.find(LiteRtLmPacks.QWEN3_CODE)
        assertNotNull(qwen)
        assertEquals("Qwen3 0.6B INT4", qwen?.displayName)
        assertTrue(qwen?.isPrimaryRecommendation == true)
        assertEquals(AiCapability.CODE, qwen?.capability)

        val gemma4 = LiteRtModelCatalog.find(LiteRtLmPacks.GEMMA4_CODE)
        assertNotNull(gemma4)
        assertEquals("Gemma 4 E2B", gemma4?.displayName)
        assertEquals(AiCapability.CODE, gemma4?.capability)

        val functionGemma = LiteRtModelCatalog.find(LiteRtLmPacks.FUNCTION_GEMMA)
        assertNotNull(functionGemma)
        assertEquals("FunctionGemma Tools", functionGemma?.displayName)

        val legacyGemma = LiteRtModelCatalog.find(LiteRtLmPacks.LEGACY_GEMMA3)
        assertNotNull(legacyGemma)
    }

    @Test
    fun modelPackIdsAreUnique() {
        val ids = LiteRtModelCatalog.allModels.map { it.packId }
        assertEquals("All model pack IDs must be distinct", ids.size, ids.distinct().size)
    }

    @Test
    fun packStateReadinessEvaluatesCorrectly() {
        val dummyPack = ModelPack(
            id = LiteRtLmPacks.QWEN3_CODE,
            version = 1,
            tier = EngineTier.LITE,
            displayName = "Qwen3",
            description = "Test LiteRT Model",
            totalBytes = 331_000_000L,
            files = emptyList(),
        )

        // 1. Not installed
        val notInstalled = PackState(pack = dummyPack, status = PackStatus.NOT_INSTALLED, progress = 0f)
        assertFalse(notInstalled.isReady())

        // 2. Downloading
        val downloading = PackState(pack = dummyPack, status = PackStatus.DOWNLOADING, progress = 0.45f)
        assertFalse(downloading.isReady())

        // 3. Installed but verify pending
        val installedUnverified = PackState(pack = dummyPack, status = PackStatus.INSTALLED, verifyStatus = PackVerifyStatus.UNKNOWN)
        assertFalse(installedUnverified.isReady())

        // 4. Installed and verified -> READY
        val installedVerified = PackState(pack = dummyPack, status = PackStatus.INSTALLED, verifyStatus = PackVerifyStatus.VERIFIED)
        assertTrue(installedVerified.isReady())

        // 5. Incompatible
        val incompatible = PackState(pack = dummyPack, status = PackStatus.INCOMPATIBLE)
        assertFalse(incompatible.isReady())
    }
}
