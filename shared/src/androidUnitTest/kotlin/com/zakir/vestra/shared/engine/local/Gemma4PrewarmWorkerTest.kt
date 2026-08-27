package com.zakir.vestra.shared.engine.local

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import com.zakir.vestra.shared.packs.AndroidDeviceProbe
import com.zakir.vestra.shared.packs.AndroidPackFileSystem
import com.zakir.vestra.shared.packs.AndroidPackIntegrityChecker
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.platformHttpClient
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class Gemma4PrewarmWorkerTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var packManager: ModelPackManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        testDir = File(context.cacheDir, "prewarm_test_${System.currentTimeMillis()}").apply { mkdirs() }
        packManager = ModelPackManager(
            fs = AndroidPackFileSystem(context) { testDir },
            device = AndroidDeviceProbe(context),
            http = platformHttpClient(),
            manifestUrl = "https://example.com/manifest.json",
            integrityChecker = AndroidPackIntegrityChecker(),
        )
        Gemma4PrewarmWorker.dependencies = { packManager }
        Gemma4PrewarmWorker.gpuPreference = { false }
        Gemma4PrewarmWorker.customPrewarmAction = null
        Gemma4PrewarmWorker.packReadyOverride = null
        Gemma4PrewarmWorker.installedDirOverride = null
    }

    @After
    fun tearDown() {
        Gemma4PrewarmWorker.dependencies = null
        Gemma4PrewarmWorker.customPrewarmAction = null
        Gemma4PrewarmWorker.packReadyOverride = null
        Gemma4PrewarmWorker.installedDirOverride = null
        testDir.deleteRecursively()
    }

    @Test
    fun constraintsRequireChargingAndUnmeteredNetwork() {
        val constraints = Gemma4PrewarmWorker.buildConstraints()
        assertTrue(constraints.requiresCharging(), "Worker constraints must require charging")
        assertEquals(
            NetworkType.UNMETERED,
            constraints.requiredNetworkType,
            "Worker constraints must require unmetered Wi-Fi network",
        )
    }

    @Test
    fun workerReturnsSuccessWhenPackNotReady() = runBlocking {
        val result = Gemma4PrewarmWorker.executePrewarm(
            context = context,
            manager = packManager,
            packId = LiteRtLmPacks.GEMMA4_CODE,
        )

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("pack_not_ready", result.outputData.getString(Gemma4PrewarmWorker.KEY_STATUS))
    }

    @Test
    fun workerExecutesPrewarmSuccessfullyWhenReady() = runBlocking {
        val packDir = File(testDir, LiteRtLmPacks.GEMMA4_CODE).apply { mkdirs() }
        val modelFile = File(packDir, LiteRtLmPacks.GEMMA4_FILE)
        modelFile.writeBytes(ByteArray(100))

        Gemma4PrewarmWorker.packReadyOverride = { _, _, _ -> true }
        Gemma4PrewarmWorker.installedDirOverride = { _, _ -> packDir.absolutePath }

        var prewarmExecuted = false
        Gemma4PrewarmWorker.customPrewarmAction = { _, _, packId, _ ->
            assertEquals(LiteRtLmPacks.GEMMA4_CODE, packId)
            prewarmExecuted = true
            null // null indicates success
        }

        val result = Gemma4PrewarmWorker.executePrewarm(
            context = context,
            manager = packManager,
            packId = LiteRtLmPacks.GEMMA4_CODE,
        )

        assertTrue(prewarmExecuted, "Pre-warm action should have executed")
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("prewarmed", result.outputData.getString(Gemma4PrewarmWorker.KEY_STATUS))
    }

    @Test
    fun workerRetriesWhenPrewarmFails() = runBlocking {
        val packDir = File(testDir, LiteRtLmPacks.GEMMA4_CODE).apply { mkdirs() }
        val modelFile = File(packDir, LiteRtLmPacks.GEMMA4_FILE)
        modelFile.writeBytes(ByteArray(100))

        Gemma4PrewarmWorker.packReadyOverride = { _, _, _ -> true }
        Gemma4PrewarmWorker.installedDirOverride = { _, _ -> packDir.absolutePath }

        Gemma4PrewarmWorker.customPrewarmAction = { _, _, _, _ ->
            "Synthetic out-of-memory error during loading"
        }

        val result = Gemma4PrewarmWorker.executePrewarm(
            context = context,
            manager = packManager,
            packId = LiteRtLmPacks.GEMMA4_CODE,
        )

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun workRequestsConfiguredWithExpectedConstraintsAndTags() {
        val periodic = Gemma4PrewarmWorker.buildPeriodicWorkRequest(repeatIntervalHours = 6, flexIntervalHours = 1)
        assertTrue(periodic.workSpec.constraints.requiresCharging())
        assertEquals(NetworkType.UNMETERED, periodic.workSpec.constraints.requiredNetworkType)

        val oneTime = Gemma4PrewarmWorker.buildOneTimeWorkRequest(
            packId = LiteRtLmPacks.GEMMA4_CODE,
            useGpu = true,
        )
        assertTrue(oneTime.workSpec.constraints.requiresCharging())
        assertEquals(NetworkType.UNMETERED, oneTime.workSpec.constraints.requiredNetworkType)
        assertEquals(LiteRtLmPacks.GEMMA4_CODE, oneTime.workSpec.input.getString(Gemma4PrewarmWorker.KEY_PACK_ID))
        assertEquals(true, oneTime.workSpec.input.getBoolean(Gemma4PrewarmWorker.KEY_USE_GPU, false))
    }
}
