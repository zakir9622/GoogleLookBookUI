package com.zakir.vestra.shared.cloud

import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
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

class CloudModelRoutingTest {

    @Test
    fun imageFallbackPrefersReadyModelsAfterSelection() {
        val selected = CloudModelCatalog.byId("flux-schnell-hf")!!
        val chain = CloudModelRouting.fallbackChain(selected, AiCapability.IMAGE_GEN)
        assertEquals("flux-schnell-hf", chain.first().id)
        assertTrue(chain.none { it.id == "sdxl-lightning-hf" })
    }

    @Test
    fun imageFallbackIncludesInferenceWhenHfTokenConfigured() {
        val settings = AppSettings(MemorySettings()).apply { setHfToken("hf_test") }
        val selected = CloudModelCatalog.byId("flux-schnell-hf")!!
        val chain = CloudModelRouting.fallbackChain(selected, AiCapability.IMAGE_GEN, settings)
        assertTrue(chain.any { it.id == "flux-schnell-inference" })
    }

    @Test
    fun imageFallbackIncludesReadyWhenUserSelectedUnsupportedLightning() {
        val selected = CloudModelCatalog.byId("sdxl-lightning-hf")!!
        assertEquals(
            ModelSupportLevel.UNSUPPORTED,
            CloudModelContracts.forProvider(selected).support,
        )
        val chain = CloudModelRouting.fallbackChain(selected, AiCapability.IMAGE_GEN)
        assertEquals("sdxl-lightning-hf", chain.first().id)
        assertTrue(chain.any { it.id == "flux-schnell-hf" })
    }

    @Test
    fun codeFallbackSkipsProvidersWithoutKeys() {
        val settings = AppSettings(MemorySettings())
        settings.setCodeProvider("llama33-70b-groq")
        settings.setHfToken("hf_test")
        settings.setOpenRouterApiKey("sk-or-test")
        settings.setGroqApiKey("gsk_test")
        val chain = CloudModelRouting.codeFallbackChain(
            CloudModelCatalog.byId("llama33-70b-groq")!!,
            settings,
        )
        assertEquals("llama33-70b-groq", chain.first().id)
        assertTrue(chain.any { it.id == "qwen25-coder-hf" })
        assertTrue(chain.any { it.id == "openrouter-free" })
    }

    @Test
    fun codeFallbackOmitsGroqWhenKeyMissing() {
        val settings = AppSettings(MemorySettings())
        settings.setCodeProvider("llama33-70b-groq")
        settings.setHfToken("hf_test")
        val chain = CloudModelRouting.codeFallbackChain(
            CloudModelCatalog.byId("llama33-70b-groq")!!,
            settings,
        )
        assertTrue(chain.none { it.platform == CloudPlatform.GROQ })
        assertEquals("qwen25-coder-7b-hf", chain.first().id)
    }

    @Test
    fun codeFallbackPrefersOpenRouterThenGroqThenHf7b() {
        val settings = AppSettings(MemorySettings()).apply {
            setHfToken("hf_test")
            setGroqApiKey("gsk_test")
            setOpenRouterApiKey("sk-or-test")
        }
        val chain = CloudModelRouting.codeFallbackChain(
            CloudModelCatalog.byId("qwen25-coder-hf")!!,
            settings,
        )
        val platforms = chain.map { it.platform }
        val openRouterIdx = platforms.indexOf(CloudPlatform.OPENROUTER)
        val groqIdx = platforms.indexOf(CloudPlatform.GROQ)
        val hf7bIdx = chain.indexOfFirst { it.id == "qwen25-coder-7b-hf" }
        assertTrue(openRouterIdx >= 0 && groqIdx >= 0 && hf7bIdx >= 0)
        assertTrue(openRouterIdx < groqIdx)
        assertTrue(groqIdx < hf7bIdx)
    }

    @Test
    fun imageEditFallbackExcludesBrokenInferenceRoute() {
        val settings = AppSettings(MemorySettings()).apply { setHfToken("hf_test") }
        val selected = CloudModelCatalog.byId("qwen-image-edit-hf")!!
        val chain = CloudModelRouting.fallbackChain(selected, AiCapability.IMAGE_EDIT, settings)
        assertEquals("qwen-image-edit-hf", chain.first().id)
        assertTrue(chain.any { it.id == "instruct-pix2pix-hf" })
        assertTrue(chain.none { it.id == "instruct-pix2pix-inference" })
    }

    @Test
    fun tryOnFallbackExcludesUnsupportedFitDiT() {
        val selected = CloudModelCatalog.byId("ootd-hf")!!
        val chain = CloudModelRouting.fallbackChain(selected, AiCapability.TRY_ON)
        assertTrue(chain.none { it.id == "fitdit-hf" })
        assertEquals("ootd-hf", chain.first().id)
    }

    @Test
    fun tryOnFallbackIncludesReadyAlternatesWhenUserSelectedDegraded() {
        val selected = CloudModelCatalog.byId("catvton-hf")!!
        val chain = CloudModelRouting.fallbackChain(selected, AiCapability.TRY_ON)
        assertTrue(chain.any { it.id == "ootd-hf" })
    }

    @Test
    fun imageFallbackSkipsInferenceWhenCreditsCooldown() {
        val settings = AppSettings(MemorySettings()).apply { setHfToken("hf_test") }
        val health = ModelHealthTracker(MemorySettings())
        health.recordFailure("flux-schnell-inference", ModelHealthTracker.FailureKind.CREDITS)
        val selected = CloudModelCatalog.byId("flux-schnell-hf")!!
        val chain = CloudModelRouting.fallbackChain(selected, AiCapability.IMAGE_GEN, settings, health)
        assertEquals("flux-schnell-hf", chain.first().id)
        assertTrue(chain.none { it.platform == CloudPlatform.HF_INFERENCE }, chain.map { it.id }.toString())
        assertTrue(CloudModelRouting.inferenceCreditsExhausted(AiCapability.IMAGE_GEN, health))
    }
}
