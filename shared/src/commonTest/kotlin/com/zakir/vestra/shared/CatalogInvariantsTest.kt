package com.zakir.vestra.shared

import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.settings.PreflightResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exploratory invariant sweep over the catalogs and the settings state machine.
 *
 * These are written to fail loudly on the kinds of inconsistency this app has actually shipped:
 * a catalog row promising a capability it cannot deliver, an id that resolves to nothing, and a
 * settings combination that lets a disabled route through.
 */
class CatalogInvariantsTest {

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

    private fun settings() = AppSettings(MemorySettings())

    // ---------- catalog integrity ----------

    @Test
    fun localCatalogIdsAreUnique() {
        val ids = LocalModelCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate local ids: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test
    fun everyLocalIdResolvesViaById() {
        LocalModelCatalog.entries.forEach { entry ->
            assertNotNull(LocalModelCatalog.byId(entry.id), "${entry.id} does not resolve")
        }
    }

    @Test
    fun cloudCatalogIdsAreUnique() {
        val ids = CloudModelCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate cloud ids: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test
    fun everyCapabilityHasAResolvableCloudDefault() {
        AiCapability.entries.forEach { capability ->
            val default = CloudModelCatalog.defaultFor(capability)
            assertNotNull(
                CloudModelCatalog.byId(default.id),
                "default for $capability (${default.id}) is not in the catalog",
            )
        }
    }

    @Test
    fun studioPickerEntriesAreSelectableForTheirOwnCapability() {
        AiCapability.entries.forEach { capability ->
            LocalModelCatalog.forStudioPicker(capability).forEach { entry ->
                assertTrue(
                    LocalModelCatalog.isSelectableStudioId(entry.id, capability),
                    "${entry.id} is offered for $capability but is not selectable for it",
                )
            }
        }
    }

    @Test
    fun everyRunnableLocalEntryDeclaresSomethingToRun() {
        LocalModelCatalog.entries.filter { it.runnable }.forEach { entry ->
            // A runnable row either ships with the app (no pack) or names the pack it needs.
            // A runnable row naming a blank pack id can never be satisfied.
            entry.packId?.let {
                assertTrue(it.isNotBlank(), "${entry.id} is runnable with a blank packId")
            }
            assertTrue(entry.displayName.isNotBlank(), "${entry.id} has no display name")
        }
    }

    // ---------- settings state machine ----------

    @Test
    fun cloudOffBlocksEveryCapabilityThatHasNoLocalSelection() {
        AiCapability.entries.forEach { capability ->
            val s = settings()
            val result = s.preflight(capability)
            assertTrue(
                result is PreflightResult.Blocked,
                "$capability was not blocked with cloud off and no local pick",
            )
        }
    }

    @Test
    fun everySelectableLocalIdPassesPreflightWithCloudOff() {
        AiCapability.entries.forEach { capability ->
            LocalModelCatalog.forStudioPicker(capability).forEach { entry ->
                val s = settings()
                s.setLocalGenerator(capability, entry.id)
                assertTrue(
                    s.preflight(capability) is PreflightResult.Ok,
                    "${entry.id} selected for $capability still fails preflight with cloud off",
                )
                assertTrue(s.prefersLocal(capability), "${entry.id} did not register as local")
            }
        }
    }

    @Test
    fun cloudGenerationAllowedTracksTheToggleForEveryCapability() {
        val s = settings()
        assertFalse(s.cloudGenerationAllowed(), "cloud must be off by default")
        s.setCloudModelsEnabled(true)
        assertTrue(s.cloudGenerationAllowed())
        s.setCloudModelsEnabled(false)
        assertFalse(s.cloudGenerationAllowed())
        AiCapability.entries.forEach { capability ->
            assertTrue(
                s.cloudDisabledReason(capability).isNotBlank(),
                "no disabled-reason copy for $capability",
            )
        }
    }

    @Test
    fun selectionSurvivesARoundTripThroughStorage() {
        // Same backing store, new AppSettings — i.e. an app restart.
        val store = MemorySettings()
        AppSettings(store).apply {
            setLocalGenerator(AiCapability.CODE, "local-qwen3-06b-v1")
            setCloudModelsEnabled(true)
        }
        val reloaded = AppSettings(store)
        assertEquals("local-qwen3-06b-v1", reloaded.selectionId(AiCapability.CODE))
        assertTrue(reloaded.prefersLocal(AiCapability.CODE))
        assertTrue(reloaded.cloudGenerationAllowed(), "cloud toggle did not persist")
    }

    @Test
    fun unknownStoredSelectionFallsBackToSomethingUsable() {
        val store = MemorySettings()
        store.putString("code_provider_id", "model-that-no-longer-exists")
        val s = AppSettings(store)
        // Must not crash, and must resolve to a real catalog entry.
        val provider = s.selectedProvider(AiCapability.CODE)
        assertNotNull(CloudModelCatalog.byId(provider.id), "stale id resolved to a phantom provider")
    }
}
