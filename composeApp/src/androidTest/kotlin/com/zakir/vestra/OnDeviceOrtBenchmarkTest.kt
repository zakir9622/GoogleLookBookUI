package com.zakir.vestra

import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zakir.vestra.shared.engine.lite.OrtEpProbe
import com.zakir.vestra.shared.engine.lite.OrtModel
import com.zakir.vestra.shared.engine.lite.OrtSessionCache
import com.zakir.vestra.shared.engine.pro.OrtGraph
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A0 on-device ORT benchmark (generation-transparency).
 *
 * Exercises production [OrtModel]/[OrtGraph]/[ProOrtSessions] session factories,
 * logs EP availability / registration probes, times Lite graphs (bundled) and any
 * Pro / local-sdturbo packs found under the app files tree.
 *
 * Harvest: `adb shell cat …/files/benchmarks/on-device-ort.json` or
 * `scripts/benchmark-on-device.sh`.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceOrtBenchmarkTest {

    @Test
    fun runOrtBenchmarkAndWriteArtifact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = File(context.filesDir, "benchmarks").apply { mkdirs() }
        val outFile = File(outDir, "on-device-ort.json")

        OrtSessionCache.clearAll()

        val root = JSONObject()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
        root.put("schemaVersion", 1)
        root.put("plan", "generation-transparency/A0")
        root.put("capturedAt", iso)
        root.put(
            "device",
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("product", Build.PRODUCT)
                .put("hardware", Build.HARDWARE)
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE),
        )

        val epProbe = OrtEpProbe.probeRegistration()
        root.put(
            "executionProviders",
            JSONObject()
                .put("available", JSONArray(epProbe.available))
                .put(
                    "registrationProbe",
                    JSONArray().also { arr ->
                        epProbe.attempted.forEach { a ->
                            arr.put(
                                JSONObject()
                                    .put("name", a.name)
                                    .put("registered", a.registered)
                                    .put("error", a.error ?: JSONObject.NULL),
                            )
                        }
                    },
                )
                .put("productionProIntent", OrtEpProbe.productionProEpIntent()),
        )

        // ── Lite (bundled debug assets) ───────────────────────────────────
        val liteDir = File(context.cacheDir, "bench-lite-v1").apply { mkdirs() }
        for (name in listOf("garment_seg.onnx", "human_parse.onnx")) {
            val dest = File(liteDir, name)
            if (!dest.exists() || dest.length() < 100_000L) {
                context.assets.open("packs/lite-v1/$name").use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
        }
        val liteResults = JSONArray()
        liteResults.put(benchLiteModel(File(liteDir, "garment_seg.onnx"), 320, 320, "lite-garment_seg"))
        liteResults.put(benchLiteModel(File(liteDir, "human_parse.onnx"), 512, 512, "lite-human_parse"))
        // Cached second open via OrtSessionCache (Lite production path).
        liteResults.put(benchCachedLite(File(liteDir, "garment_seg.onnx"), 320, 320, "lite-garment_seg-cached"))
        root.put("lite", liteResults)

        // ── Pro graphs (if installed under filesDir packs) ────────────────
        val proDir = findPackDir(context.filesDir, "pro-v1")
            ?: findPackDir(File("/data/local/tmp"), "pro-v1")
        val proResults = JSONArray()
        if (proDir != null) {
            val graphs = listOf(
                "depth.onnx",
                "ip_image_encoder.onnx",
                "text_encoder.onnx",
                "controlnet.onnx",
                "unet.onnx",
                "vae_decoder.onnx",
                "vae_encoder.onnx",
            )
            for (name in graphs) {
                val f = File(proDir, name)
                if (f.isFile && f.length() > 10_000L) {
                    proResults.put(benchProGraphLoad(f, "pro-$name"))
                } else {
                    proResults.put(
                        JSONObject()
                            .put("id", "pro-$name")
                            .put("status", "MISSING")
                            .put("path", f.absolutePath),
                    )
                }
            }
        } else {
            proResults.put(
                JSONObject()
                    .put("id", "pro-v1")
                    .put("status", "PACK_NOT_INSTALLED")
                    .put("note", "Install pro-v1 via Settings → Model packs, then re-run"),
            )
        }
        root.put("pro", proResults)

        // ── Local SD-Turbo graphs (if present) ────────────────────────────
        val sdDir = findPackDir(context.filesDir, "local-sdturbo-v1")
        val sdResults = JSONArray()
        if (sdDir != null) {
            for (name in listOf("text_encoder.onnx", "unet.onnx", "vae_decoder.onnx", "vae_encoder.onnx")) {
                val f = File(sdDir, name)
                if (f.isFile && f.length() > 10_000L) {
                    sdResults.put(benchProGraphLoad(f, "sdturbo-$name"))
                }
            }
        } else {
            sdResults.put(JSONObject().put("id", "local-sdturbo-v1").put("status", "PACK_NOT_INSTALLED"))
        }
        root.put("localImage", sdResults)

        // ── Local audio (system TTS init) ─────────────────────────────────
        root.put("localAudio", benchSystemTts())

        // ── Local video note ──────────────────────────────────────────────
        root.put(
            "localVideo",
            JSONObject()
                .put("id", "local-stillclip-v1")
                .put("status", "CODE_PATH_ONLY")
                .put(
                    "note",
                    "Still-clip is MediaCodec encode of a keyframe — no ORT graph. " +
                        "Timed separately in UI soak; not an ONNX EP path.",
                ),
        )

        val pretty = root.toString(2)
        outFile.writeText(pretty)
        // Also dump to external so `adb pull` works without run-as on debug builds.
        runCatching {
            context.getExternalFilesDir(null)?.let { ext ->
                File(ext, "benchmarks").apply { mkdirs() }
                    .resolve("on-device-ort.json")
                    .writeText(pretty)
            }
        }
        // Chunked logcat for harvest without file pull.
        pretty.lineSequence().forEach { line ->
            Log.i(BENCH_TAG, line)
        }
        Log.i(BENCH_TAG, "WROTE ${outFile.absolutePath}")

        assertTrue("benchmark artifact must be non-empty", outFile.length() > 200L)
        assertTrue(
            "ORT must advertise at least CPU",
            epProbe.available.any { it.contains("CPU", ignoreCase = true) },
        )
    }

    private fun benchLiteModel(file: File, h: Int, w: Int, id: String): JSONObject {
        val obj = JSONObject().put("id", id).put("bytes", file.length())
        if (!file.isFile) {
            return obj.put("status", "MISSING")
        }
        return try {
            lateinit var model: OrtModel
            val coldMs = measureTimeMillis {
                model = OrtModel(file.absolutePath, useNnapi = false)
            }
            val chw = FloatArray(3 * h * w) { 0.5f }
            val warm = mutableListOf<Long>()
            repeat(3) {
                warm += measureTimeMillis { model.run(chw, h, w) }
            }
            model.close()
            obj.put("status", "OK")
                .put("sessionFactory", "OrtModel/CPU")
                .put("epIntent", "CPU, NNAPI=off")
                .put("coldLoadMs", coldMs)
                .put("warmRunMs", JSONArray(warm))
                .put("warmAvgMs", warm.average())
        } catch (t: Throwable) {
            obj.put("status", "FAIL").put("error", t.message?.take(200) ?: t.javaClass.simpleName)
        }
    }

    private fun benchCachedLite(file: File, h: Int, w: Int, id: String): JSONObject {
        val obj = JSONObject().put("id", id)
        return try {
            val firstMs = measureTimeMillis { OrtSessionCache.open(file.absolutePath) }
            val secondMs = measureTimeMillis { OrtSessionCache.open(file.absolutePath) }
            val model = OrtSessionCache.open(file.absolutePath)
            val chw = FloatArray(3 * h * w) { 0.5f }
            val runMs = measureTimeMillis { model.run(chw, h, w) }
            obj.put("status", "OK")
                .put("sessionFactory", "OrtSessionCache")
                .put("firstOpenMs", firstMs)
                .put("secondOpenMs", secondMs)
                .put("warmRunMs", runMs)
        } catch (t: Throwable) {
            obj.put("status", "FAIL").put("error", t.message?.take(200) ?: t.javaClass.simpleName)
        }
    }

    /** Pro/local-image graphs: load via production [OrtGraph] → [ProOrtSessions] (NO_OPT). */
    private fun benchProGraphLoad(file: File, id: String): JSONObject {
        val obj = JSONObject()
            .put("id", id)
            .put("bytes", file.length())
            .put("sessionFactory", "OrtGraph/ProOrtSessions")
            .put("epIntent", OrtEpProbe.productionProEpIntent())
        return try {
            lateinit var graph: OrtGraph
            val coldMs = measureTimeMillis {
                graph = OrtGraph(file.absolutePath)
            }
            val inputs = graph.inputNames
            graph.close()
            obj.put("status", "OK")
                .put("coldLoadMs", coldMs)
                .put("inputCount", inputs.size)
                .put("inputs", JSONArray(inputs.toList()))
        } catch (t: Throwable) {
            obj.put("status", "FAIL")
                .put("error", t.message?.take(240) ?: t.javaClass.simpleName)
        }
    }

    private fun benchSystemTts(): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val latch = CountDownLatch(1)
        var status = "INIT"
        var initMs = -1L
        val t0 = System.nanoTime()
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { code ->
            initMs = (System.nanoTime() - t0) / 1_000_000L
            status = if (code == TextToSpeech.SUCCESS) "OK" else "FAIL_CODE_$code"
            latch.countDown()
        }
        val ok = latch.await(15, TimeUnit.SECONDS)
        if (!ok) status = "TIMEOUT"
        runCatching { tts?.shutdown() }
        return JSONObject()
            .put("id", "system-tts")
            .put("status", status)
            .put("initMs", initMs)
    }

    private fun findPackDir(root: File, packId: String): File? {
        if (!root.exists()) return null
        // Typical layout: files/packs/<id>/<version>/
        val packs = File(root, "packs/$packId")
        if (packs.isDirectory) {
            packs.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }?.let { return it }
            if (File(packs, "unet.onnx").exists() || File(packs, "garment_seg.onnx").exists()) {
                return packs
            }
        }
        // Flat /data/local/tmp/pro-v1
        val flat = File(root, packId)
        if (flat.isDirectory && (File(flat, "unet.onnx").exists() || File(flat, "depth.onnx").exists())) {
            return flat
        }
        return null
    }

    companion object {
        const val BENCH_TAG = "LookbookBench"
    }
}
