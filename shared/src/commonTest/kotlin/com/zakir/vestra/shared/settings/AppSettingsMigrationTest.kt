package com.zakir.vestra.shared.settings

import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.local.LocalModelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemorySettings : Settings {
    private val map = mutableMapOf<String, Any?>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

class AppSettingsMigrationTest {

    @Test
    fun codeProviderMigratesFromGroqToHfWhenGroqKeyMissing() {
        val raw = MemorySettings()
        raw.putString("code_provider_id", "llama33-70b-groq")
        raw.putString("hf_token", "hf_test")
        val settings = AppSettings(raw)
        assertEquals("qwen25-coder-hf", settings.selectedProvider(com.zakir.vestra.shared.cloud.AiCapability.CODE).id)
    }

    @Test
    fun imageEditMigratesAwayFromInstructPix2PixDefault() {
        val raw = MemorySettings()
        raw.putString("image_edit_provider_id", "instruct-pix2pix-hf")
        val settings = AppSettings(raw)
        assertEquals(CloudModelCatalog.defaultImageEditId, settings.selectedProvider(
            com.zakir.vestra.shared.cloud.AiCapability.IMAGE_EDIT,
        ).id)
    }

    @Test
    fun localGeneratorSelectionPersistsAndPrefersLocal() {
        val settings = AppSettings(MemorySettings())
        settings.setLocalGenerator(
            com.zakir.vestra.shared.cloud.AiCapability.IMAGE_GEN,
            "local-sdturbo-v1",
        )
        assertEquals("local-sdturbo-v1", settings.selectionId(com.zakir.vestra.shared.cloud.AiCapability.IMAGE_GEN))
        assertEquals(true, settings.prefersLocal(com.zakir.vestra.shared.cloud.AiCapability.IMAGE_GEN))
        // Cloud default still resolves for fallback / estimates.
        assertEquals(
            CloudModelCatalog.defaultFor(com.zakir.vestra.shared.cloud.AiCapability.IMAGE_GEN).id,
            settings.selectedProvider(com.zakir.vestra.shared.cloud.AiCapability.IMAGE_GEN).id,
        )
    }

    @Test
    fun cloudModelsOffByDefaultBlocksCloudCapability() {
        val settings = AppSettings(MemorySettings())
        val result = settings.preflight(AiCapability.IMAGE_GEN)
        assertTrue(result is PreflightResult.Blocked)
        assertTrue((result as PreflightResult.Blocked).reason.contains("Cloud models are off"))
    }

    @Test
    fun enablingCloudModelsRemovesTheGlobalBlock() {
        val settings = AppSettings(MemorySettings())
        settings.setCloudModelsEnabled(true)
        val result = settings.preflight(AiCapability.IMAGE_GEN)
        val blockedOnGlobalToggle =
            result is PreflightResult.Blocked && result.reason.contains("Cloud models are off")
        assertFalse(blockedOnGlobalToggle)
    }

    @Test
    fun localSelectionBypassesTheCloudToggleEvenWhenOff() {
        val settings = AppSettings(MemorySettings())
        settings.setLocalGenerator(AiCapability.IMAGE_GEN, "local-sdturbo-v1")
        val result = settings.preflight(AiCapability.IMAGE_GEN)
        assertTrue(result is PreflightResult.Ok)
    }

    /**
     * The News/Chat model picker calls setLocalGenerator(CODE, …) for every on-device row
     * it renders, and that call throws on a non-selectable id — so each CODE studio entry
     * must actually be accepted, and must then make chat prefer the local route.
     */
    @Test
    fun everyLocalCodeStudioEntryIsSelectableForChat() {
        val entries = LocalModelCatalog.forStudioPicker(AiCapability.CODE)
        assertTrue(entries.isNotEmpty(), "expected at least one on-device CODE generator")
        entries.forEach { entry ->
            val settings = AppSettings(MemorySettings())
            settings.setLocalGenerator(AiCapability.CODE, entry.id)
            assertEquals(entry.id, settings.selectionId(AiCapability.CODE))
            assertTrue(settings.prefersLocal(AiCapability.CODE), "${entry.id} should prefer local")
            // Cloud off is the default — a local chat pick must still be allowed through.
            assertTrue(settings.preflight(AiCapability.CODE) is PreflightResult.Ok)
        }
    }
}
