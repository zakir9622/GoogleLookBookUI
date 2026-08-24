package com.zakir.vestra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.ModelPack
import com.zakir.vestra.shared.domain.PackFile
import com.zakir.vestra.shared.packs.AndroidPackIntegrityChecker
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side ONNX integrity checks for bundled lite-v1 assets.
 * Copies from APK assets into a temp dir so we exercise [AndroidPackIntegrityChecker]
 * without waiting for [DebugPackBootstrap] on app startup.
 */
@RunWith(AndroidJUnit4::class)
class PackIntegrityInstrumentedTest {

    private lateinit var packDir: File
    private val checker = AndroidPackIntegrityChecker()

    private val litePack = ModelPack(
        id = "lite-v1",
        version = 1,
        tier = EngineTier.LITE,
        displayName = "Lite engine",
        description = "test",
        totalBytes = 68_609_539L,
        files = listOf(
            PackFile("garment_seg.onnx", "bundled", "bundled", 1_321_751L),
            PackFile("human_parse.onnx", "bundled", "bundled", 67_287_788L),
        ),
    )

    @Before
    fun copyBundledAssets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        packDir = File(context.cacheDir, "lite-v1-integ").apply { mkdirs() }
        for (name in listOf("garment_seg.onnx", "human_parse.onnx")) {
            val dest = File(packDir, name)
            if (dest.exists() && dest.length() > 1_000_000L) continue
            context.assets.open("packs/lite-v1/$name").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
    }

    @Test
    fun bundledLitePackPassesFileAndOnnxVerification() {
        val dir = packDir.absolutePath
        assertNull(checker.verifyFiles(litePack, dir))
        assertNull(checker.verifyOnnx(litePack, dir))
    }

    @Test
    fun truncatedOnnxFailsVerification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val truncated = File(packDir, "garment_seg.onnx")
        context.assets.open("packs/lite-v1/garment_seg.onnx").use { input ->
            truncated.outputStream().use { input.copyTo(it) }
        }
        truncated.writeBytes(truncated.readBytes().copyOf(64))
        val err = checker.verifyOnnx(litePack, packDir.absolutePath)
        assertNotNull("truncated ONNX should fail load or verify", err)
    }

    @Test
    fun wrongFileSizeFailsVerification() {
        val wrongPack = litePack.copy(
            files = litePack.files.map { f ->
                if (f.path == "garment_seg.onnx") f.copy(bytes = 999L) else f
            },
        )
        val err = checker.verifyFiles(wrongPack, packDir.absolutePath)
        assertNotNull("manifest byte count mismatch should fail", err)
        assertTrue(err!!.contains("incomplete") || err.contains("garment_seg"))
    }
}
