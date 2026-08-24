package com.zakir.vestra.shared.local

import com.zakir.vestra.shared.cloud.AiCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalModelCatalogTest {

    @Test
    fun imageStudioPickerExcludesQualityUpscalers() {
        val ids = LocalModelCatalog.forStudioPicker(AiCapability.IMAGE_GEN).map { it.id }
        assertTrue(ids.contains("local-sdturbo-v1"))
        assertFalse(ids.contains("local-quality-realesrgan"))
        assertFalse(ids.contains("local-quality-gfpgan"))
        assertFalse(ids.contains("local-quality-birefnet"))
    }

    @Test
    fun imageEditStudioPickerOffersLocalImg2Img() {
        val ids = LocalModelCatalog.forStudioPicker(AiCapability.IMAGE_EDIT).map { it.id }
        assertEquals(listOf("local-sdturbo-edit"), ids)
        val entry = LocalModelCatalog.entries.first { it.id == "local-sdturbo-edit" }
        assertTrue(entry.runnable)
        assertEquals("local-sdturbo-v1", entry.packId)
        assertTrue(LocalModelCatalog.studioEntryReady(entry, packReady = true))
        assertFalse(LocalModelCatalog.studioEntryReady(entry, packReady = false))
    }

    @Test
    fun audioStudioShowsTtsScaffoldAndVoiceChanger() {
        val ids = LocalModelCatalog.forStudioPicker(AiCapability.AUDIO).map { it.id }
        // Non-runnable scaffolds (local-tts-v1) stay out of the studio picker. local-audio-scribe-v1
        // is also excluded: the published local-gemma-4-e2b-v1 pack ships with audio disabled
        // (config.json "audio": false), so it can never actually transcribe.
        assertEquals(
            setOf("local-tts-system", "local-voice-changer"),
            ids.toSet(),
        )
        val system = LocalModelCatalog.entries.first { it.id == "local-tts-system" }
        assertTrue(system.runnable)
        assertEquals("Ready offline", LocalModelCatalog.studioStatusLabel(system, packReady = false))
        val tts = LocalModelCatalog.entries.first { it.id == "local-tts-v1" }
        assertFalse(tts.runnable)
        val changer = LocalModelCatalog.entries.first { it.id == "local-voice-changer" }
        assertTrue(changer.runnable)
        assertEquals("Ready offline", LocalModelCatalog.studioStatusLabel(changer, packReady = false))
        val scribe = LocalModelCatalog.entries.first { it.id == "local-audio-scribe-v1" }
        assertFalse(scribe.runnable)
    }

    @Test
    fun videoStudioOffersLocalStillClip() {
        val video = LocalModelCatalog.forStudioPicker(AiCapability.VIDEO)
        assertEquals(listOf("local-stillclip-v1"), video.map { it.id })
        assertTrue(video.first().runnable)
        assertEquals("local-sdturbo-v1", video.first().packId)
        assertTrue(
            LocalModelCatalog.studioStatusLabel(video.first(), false)
                .contains("Download", ignoreCase = true),
        )
        assertEquals(
            "Ready offline (still-clip)",
            LocalModelCatalog.studioStatusLabel(video.first(), true),
        )
    }

    @Test
    fun codeStudioOffersGemma4LegacyAndFunctionGemma() {
        val code = LocalModelCatalog.forStudioPicker(AiCapability.CODE)
        assertTrue(code.map { it.id }.contains("local-gemma-4-e2b-v1"))
        assertTrue(code.map { it.id }.contains("local-gemma-v1"))
        assertTrue(code.map { it.id }.contains("local-functiongemma-v1"))
        val gemma4 = code.first { it.id == "local-gemma-4-e2b-v1" }
        assertTrue(gemma4.runnable)
        assertEquals("local-gemma-4-e2b-v1", gemma4.packId)
    }

    @Test
    fun codeStudioLegacyGemmaStillSelectable() {
        val legacy = LocalModelCatalog.entries.first { it.id == "local-gemma-v1" }
        assertTrue(legacy.runnable)
        assertTrue(LocalModelCatalog.studioEntryReady(legacy, packReady = true))
        assertFalse(LocalModelCatalog.studioEntryReady(legacy, packReady = false))
    }

    @Test
    fun imageStudioPickerPromptsDownloadWhenPackMissing() {
        val entry = LocalModelCatalog.entries.first { it.id == "local-sdturbo-v1" }
        assertTrue(entry.runnable)
        assertTrue(
            LocalModelCatalog.studioStatusLabel(entry, packReady = false)
                .contains("Download", ignoreCase = true),
        )
        assertFalse(LocalModelCatalog.studioEntryReady(entry, packReady = false))
    }

    @Test
    fun sdturboShowsReadyOfflineWhenPackGraphsInstalled() {
        val entry = LocalModelCatalog.entries.first { it.id == "local-sdturbo-v1" }
        assertEquals("Ready offline", LocalModelCatalog.studioStatusLabel(entry, packReady = true))
        assertTrue(LocalModelCatalog.studioEntryReady(entry, packReady = true))
    }

    @Test
    fun qualityPacksStayInCatalogForSettings() {
        val quality = LocalModelCatalog.entries.filter {
            it.pickerRole == LocalModelPickerRole.QUALITY_POST
        }
        assertTrue(quality.any { it.packId == "realesrgan-v1" })
        assertTrue(quality.any { it.packId == "birefnet-v1" })
        val tryOnQuality = quality.filter {
            it.id !in setOf("local-gemma-4-vision-v1")
        }
        tryOnQuality.forEach {
            assertTrue(
                it.capability == AiCapability.TRY_ON,
                "${it.id} should be TRY_ON quality post, was ${it.capability}",
            )
        }
        assertTrue(quality.any { it.id == "local-gemma-4-vision-v1" && it.capability == AiCapability.IMAGE_GEN })
    }

    @Test
    fun selectableStudioIdsMatchRunnableGenerators() {
        assertTrue(LocalModelCatalog.isSelectableStudioId("local-sdturbo-v1", AiCapability.IMAGE_GEN))
        assertTrue(LocalModelCatalog.isSelectableStudioId("local-gemma-4-e2b-v1", AiCapability.CODE))
        assertTrue(LocalModelCatalog.isSelectableStudioId("local-functiongemma-v1", AiCapability.CODE))
        assertTrue(LocalModelCatalog.isSelectableStudioId("local-gemma-v1", AiCapability.CODE))
        assertFalse(LocalModelCatalog.isSelectableStudioId("local-quality-realesrgan", AiCapability.TRY_ON))
        assertFalse(LocalModelCatalog.isSelectableStudioId("flux-schnell-hf", AiCapability.IMAGE_GEN))
        assertEquals("local-sdturbo-v1", LocalModelCatalog.byId("local-sdturbo-v1")?.id)
    }
}
