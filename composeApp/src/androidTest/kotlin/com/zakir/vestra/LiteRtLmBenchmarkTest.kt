package com.zakir.vestra

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.zakir.vestra.shared.engine.litert.LiteRtLmEngine
import com.zakir.vestra.shared.engine.local.LiteRtLmPacks
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * LiteRT-LM benchmark harness (L0/L1). Skips when no `.litertlm` is pushed to device.
 *
 * Manual push:
 *   adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.zakir.vestra/files/packs/local-gemma-4-e2b-v1/1/
 */
@RunWith(AndroidJUnit4::class)
class LiteRtLmBenchmarkTest {

    @Test
    fun spikeEngineWhenPackPresent() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val packDir = File(context.filesDir, "packs/${LiteRtLmPacks.GEMMA4_CODE}/1")
        val model = File(packDir, LiteRtLmPacks.GEMMA4_FILE)
        if (!model.isFile || model.length() < 500_000_000L) {
            Log.i(TAG, "SKIP — no Gemma 4 pack at ${model.absolutePath}")
            return
        }
        val engine = LiteRtLmEngine(
            context = context,
            modelPath = model.absolutePath,
            useGpu = false,
        )
        engine.use {
            it.initialize()
            val loadMs = it.coldLoadMs()
            val started = System.currentTimeMillis()
            val result = it.generateText(
                prompt = "Write a Kotlin hello world.",
                system = "You are a concise coding assistant.",
            )
            val genMs = System.currentTimeMillis() - started
            assertTrue(result is com.zakir.vestra.shared.engine.litert.LiteRtLmGenerateResult.Ok)
            val json = JSONObject()
                .put("packId", LiteRtLmPacks.GEMMA4_CODE)
                .put("backend", "CPU")
                .put("coldLoadMs", loadMs)
                .put("generateMs", genMs)
                .put("modelBytes", model.length())
            Log.i(TAG, "BENCH $json")
        }
    }

    @Test
    fun probeNativeEngineOpen() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val packDir = File(context.filesDir, "packs/${LiteRtLmPacks.GEMMA4_CODE}/1")
        val model = File(packDir, LiteRtLmPacks.GEMMA4_FILE)
        if (!model.isFile) {
            Log.i(TAG, "SKIP probe — no model")
            return
        }
        val config = EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU())
        Engine(config).use { engine ->
            engine.initialize()
            Log.i(TAG, "Engine probe OK · ${model.name}")
        }
    }

    companion object {
        private const val TAG = "LookbookLiteRtLm"
    }
}
