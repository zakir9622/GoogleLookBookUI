package com.zakir.vestra.shared.packs

import com.zakir.vestra.shared.domain.DeviceSpec
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.domain.PackVerifyStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeFs : PackFileSystem {
    val files = mutableMapOf<String, String>()
    val hashes = mutableMapOf<String, String>()
    var free = Long.MAX_VALUE

    override fun packsRoot(): String = "/packs"
    override fun exists(path: String): Boolean = files.containsKey(path)
    override fun fileSize(path: String): Long = files[path]?.length?.toLong() ?: 0L
    override fun delete(path: String) {
        files.keys.removeAll { it == path || it.startsWith("$path/") }
    }
    override fun mkdirs(path: String) {}
    override fun move(from: String, to: String) {
        val moved = files.filterKeys { it.startsWith("$from/") || it == from }
        moved.forEach { (path, content) ->
            files.remove(path)
            val hash = hashes.remove(path)
            val newPath = to + path.removePrefix(from)
            files[newPath] = content
            hash?.let { hashes[newPath] = it }
        }
    }
    override fun sha256(path: String): String = hashes[path] ?: "missing"
    override fun listFiles(path: String): List<String> =
        files.keys.mapNotNull { candidate ->
            val prefix = "$path/"
            if (candidate.startsWith(prefix)) {
                prefix + candidate.removePrefix(prefix).substringBefore('/')
            } else {
                null
            }
        }.distinct()
    override fun readText(path: String): String? = files[path]
    override fun writeText(path: String, content: String) {
        files[path] = content
    }
    override fun freeBytes(): Long = free
}

private class FakeProbe(
    private val ram: Long = 8192,
    private val npu: Boolean = true,
    private val sdk: Int = 35,
) : DeviceProbe {
    override fun totalRamMb(): Long = ram
    override fun hasNpu(): Boolean = npu
    override fun sdkInt(): Int = sdk
}

private const val MANIFEST = """
{
  "schemaVersion": 1,
  "packs": [
    {
      "id": "lite-v1",
      "version": 2,
      "tier": "LITE",
      "displayName": "Lite engine",
      "description": "d",
      "totalBytes": 100,
      "files": [
        {"path": "a.onnx", "url": "https://packs/a.onnx", "sha256": "hash-a", "bytes": 60},
        {"path": "b.onnx", "url": "https://packs/b.onnx", "sha256": "hash-b", "bytes": 40}
      ],
      "minSpec": {"minRamMb": 0, "requiresNpu": false, "minSdk": 35}
    },
    {
      "id": "pro-v1",
      "version": 1,
      "tier": "PRO",
      "displayName": "Pro engine",
      "description": "d",
      "totalBytes": 1000,
      "files": [
        {"path": "unet.onnx", "url": "https://packs/unet.onnx", "sha256": "hash-u", "bytes": 1000}
      ],
      "minSpec": {"minRamMb": 16000, "requiresNpu": true, "minSdk": 35}
    }
  ]
}
"""

private fun manifestClient(): HttpClient = HttpClient(
    MockEngine { respond(MANIFEST, HttpStatusCode.OK, headersOf()) },
)

class ModelPackManagerTest {

    @Test
    fun refreshBuildsStatesAndGatesIncompatiblePacks() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(ram = 8192), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        assertEquals(PackStatus.NOT_INSTALLED, manager.states.value.getValue("lite-v1").status)
        // pro-v1 wants 16 GB RAM; this "device" has 8 GB.
        assertEquals(PackStatus.INCOMPATIBLE, manager.states.value.getValue("pro-v1").status)
    }

    @Test
    fun manifestIsCachedForOfflineStart() = runTest {
        val fs = FakeFs()
        ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json").refresh()
        // New manager, network forbidden — states come from the cached manifest.
        val offline = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        offline.refresh(networkAllowed = false)
        assertTrue(offline.states.value.containsKey("lite-v1"))
    }

    @Test
    fun bundledPackSurvivesARefreshThatOmitsIt() = runTest {
        // The published catalog only lists pro-v1 and lite-v1; a sideloaded pack that is not
        // in it must still be reported as installed rather than disappearing on refresh.
        val fs = FakeFs()
        fs.files["/packs/${ModelPackManager.BUNDLED_MANIFEST}"] = """
            {"schemaVersion":1,"packs":[
              {"id":"lite-bundled","version":1,"tier":"LITE","displayName":"Bundled Lite",
               "description":"d","totalBytes":10,
               "files":[{"path":"a.onnx","url":"bundled","sha256":"bundled","bytes":10}],
               "minSpec":{"minRamMb":0,"requiresNpu":false,"minSdk":35}}
            ]}
        """.trimIndent()
        fs.files["/packs/lite-bundled/1/a.onnx"] = "X".repeat(10)
        fs.files["/packs/lite-bundled/1/${ModelPackManager.COMPLETE_MARKER}"] = "1"

        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()

        assertTrue(manager.isInstalled("lite-bundled"))
        assertEquals(PackStatus.INSTALLED, manager.states.value.getValue("lite-bundled").status)
        assertTrue(manager.states.value.containsKey("pro-v1"), "remote packs are still listed")
    }

    @Test
    fun completeInstallVerifiesChecksumsAndCommitsAtomically() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"

        assertTrue(manager.completeInstall("lite-v1", staging))
        assertTrue(manager.isInstalled("lite-v1"))
        assertTrue(manager.isReady("lite-v1"))
        assertEquals("/packs/lite-v1/2", manager.installedDir("lite-v1"))
        assertEquals(PackStatus.INSTALLED, manager.states.value.getValue("lite-v1").status)
    }

    @Test
    fun corruptFileAbortsInstall() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A"
        fs.files["$staging/b.onnx"] = "B"
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "WRONG"

        assertFalse(manager.completeInstall("lite-v1", staging))
        assertFalse(manager.isInstalled("lite-v1"))
    }

    @Test
    fun installedOlderVersionShowsUpdateAvailable() = runTest {
        val fs = FakeFs()
        // Simulate a v1 install on disk; the manifest offers v2.
        fs.files["/packs/lite-v1/1/${ModelPackManager.COMPLETE_MARKER}"] = "1"
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        assertEquals(PackStatus.UPDATE_AVAILABLE, manager.states.value.getValue("lite-v1").status)
    }

    @Test
    fun uninstallClearsState() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        manager.completeInstall("lite-v1", staging)

        manager.uninstall("lite-v1")
        assertFalse(manager.isInstalled("lite-v1"))
        assertEquals(PackStatus.NOT_INSTALLED, manager.states.value.getValue("lite-v1").status)
    }

    @Test
    fun uninstallBlockedWhilePackInUse() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertTrue(manager.completeInstall("lite-v1", staging))

        manager.markPackInUse("lite-v1")
        assertFalse(manager.uninstall("lite-v1"))
        assertTrue(manager.isInstalled("lite-v1"))

        manager.markPackIdle("lite-v1")
        assertTrue(manager.uninstall("lite-v1"))
        assertFalse(manager.isInstalled("lite-v1"))
    }

    @Test
    fun completeInstallBlockedWhilePackInUse() = runTest {
        val fs = FakeFs()
        val changed = mutableListOf<String>()
        val manager = ModelPackManager(
            fs,
            FakeProbe(),
            manifestClient(),
            "https://m/manifest.json",
            onPackFilesChanging = { changed += it },
        )
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertTrue(manager.completeInstall("lite-v1", staging))
        assertTrue(changed.any { it.contains("lite-v1") })

        // Simulate Update while generating — must not swap files under open sessions.
        val staging2 = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging2/a.onnx"] = "A".repeat(60)
        fs.files["$staging2/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging2/a.onnx"] = "hash-a"
        fs.hashes["$staging2/b.onnx"] = "hash-b"
        manager.markPackInUse("lite-v1")
        assertFalse(manager.completeInstall("lite-v1", staging2))
        manager.markPackIdle("lite-v1")
    }

    @Test
    fun packInUseUsesRefcount() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        manager.markPackInUse("lite-v1")
        manager.markPackInUse("lite-v1")
        manager.markPackIdle("lite-v1")
        assertTrue(manager.isPackInUse("lite-v1"))
        manager.markPackIdle("lite-v1")
        assertFalse(manager.isPackInUse("lite-v1"))
    }

    @Test
    fun refreshPreservesDownloadingState() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        manager.markDownloading("lite-v1", 0.42f)
        manager.refresh()
        val state = manager.states.value.getValue("lite-v1")
        assertEquals(PackStatus.DOWNLOADING, state.status)
        assertEquals(0.42f, state.progress, 0.001f)
    }

    @Test
    fun cancelKeepsPartialProgressForResumeLabel() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        manager.markDownloading("lite-v1", 0.6f)
        manager.markCancelled("lite-v1")
        val state = manager.states.value.getValue("lite-v1")
        assertEquals(PackStatus.NOT_INSTALLED, state.status)
        assertTrue(state.progress > 0f)
    }

    @Test
    fun hasSpaceForRespectsMargin() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        fs.free = 100L // pack is 100 bytes but margin demands ~500 MB headroom
        assertFalse(manager.hasSpaceFor(manager.pack("lite-v1")!!))
        fs.free = Long.MAX_VALUE
        assertTrue(manager.hasSpaceFor(manager.pack("lite-v1")!!))
    }

    @Test
    fun staleCompleteMarkerRemovedWhenFilesMissing() = runTest {
        val fs = FakeFs()
        fs.files["/packs/lite-v1/2/${ModelPackManager.COMPLETE_MARKER}"] = "2"
        // a.onnx / b.onnx absent — rebuild should drop the marker.
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        assertFalse(manager.isInstalled("lite-v1"))
        assertEquals(PackStatus.NOT_INSTALLED, manager.states.value.getValue("lite-v1").status)
    }

    @Test
    fun completeInstallAbortsWhenOnnxVerifyFails() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(
            fs,
            FakeProbe(),
            manifestClient(),
            "https://m/manifest.json",
            integrityChecker = object : PackIntegrityChecker {
                override fun verifyFiles(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String) = null
                override fun verifyOnnx(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String) =
                    "ONNX smoke failed"
            },
        )
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertFalse(manager.completeInstall("lite-v1", staging))
        assertFalse(manager.isInstalled("lite-v1"))
    }

    @Test
    fun verifyInstalledMarksFailedWhenCheckerFails() = runTest {
        val fs = FakeFs()
        val checker = object : PackIntegrityChecker {
            var allowOnnx = true
            override fun verifyFiles(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String) = null
            override fun verifyOnnx(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String) =
                if (allowOnnx) null else "ONNX smoke failed"
        }
        val manager = ModelPackManager(
            fs,
            FakeProbe(),
            manifestClient(),
            "https://m/manifest.json",
            integrityChecker = checker,
        )
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertTrue(manager.completeInstall("lite-v1", staging))
        checker.allowOnnx = false
        assertFalse(manager.verifyInstalled("lite-v1"))
        assertEquals(PackVerifyStatus.FAILED, manager.states.value.getValue("lite-v1").verifyStatus)
    }

    @Test
    fun installedNotReadyUntilVerified() = runTest {
        val fs = FakeFs()
        // Marker present but verify status UNKNOWN after refresh (no prior verified state).
        fs.files["/packs/lite-v1/2/a.onnx"] = "A".repeat(60)
        fs.files["/packs/lite-v1/2/b.onnx"] = "B".repeat(40)
        fs.files["/packs/lite-v1/2/${ModelPackManager.COMPLETE_MARKER}"] = "2"
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        assertTrue(manager.isInstalled("lite-v1"))
        assertFalse(manager.isReady("lite-v1"))
        assertEquals(PackVerifyStatus.UNKNOWN, manager.states.value.getValue("lite-v1").verifyStatus)
    }

    @Test
    fun isReadyFalseWhenVerifyFailed() = runTest {
        val fs = FakeFs()
        val checker = object : PackIntegrityChecker {
            var allowOnnx = true
            override fun verifyFiles(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String) = null
            override fun verifyOnnx(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String) =
                if (allowOnnx) null else "ONNX smoke failed"
        }
        val manager = ModelPackManager(
            fs, FakeProbe(), manifestClient(), "https://m/manifest.json", integrityChecker = checker,
        )
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertTrue(manager.completeInstall("lite-v1", staging))
        assertTrue(manager.isReady("lite-v1"))

        checker.allowOnnx = false
        assertFalse(manager.verifyInstalled("lite-v1"))
        assertFalse(manager.isReady("lite-v1"))
        assertEquals(PackVerifyStatus.FAILED, manager.states.value.getValue("lite-v1").verifyStatus)
    }

    @Test
    fun wrongFileSizeOnRebuildDropsCompleteMarker() = runTest {
        val fs = FakeFs()
        fs.files["/packs/lite-v1/2/${ModelPackManager.COMPLETE_MARKER}"] = "2"
        fs.files["/packs/lite-v1/2/a.onnx"] = "A".repeat(30) // manifest expects 60
        fs.files["/packs/lite-v1/2/b.onnx"] = "B".repeat(40)
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        assertFalse(manager.isInstalled("lite-v1"))
        assertFalse(fs.exists("/packs/lite-v1/2/${ModelPackManager.COMPLETE_MARKER}"))
    }

    @Test
    fun verifyAllInstalledChecksEveryPack() = runTest {
        val fs = FakeFs()
        var verifyCount = 0
        val checker = object : PackIntegrityChecker {
            override fun verifyFiles(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String) = null
            override fun verifyOnnx(pack: com.zakir.vestra.shared.domain.ModelPack, dir: String): String? {
                verifyCount++
                return null
            }
        }
        val manager = ModelPackManager(
            fs, FakeProbe(), manifestClient(), "https://m/manifest.json", integrityChecker = checker,
        )
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        manager.completeInstall("lite-v1", staging)
        verifyCount = 0
        manager.verifyAllInstalled()
        // Already verified at install — startup path is files-only (no ONNX session create).
        assertEquals(0, verifyCount)
        assertTrue(manager.isReady("lite-v1"))
    }

    @Test
    fun handshakeReturnsOkSignalWhenInstalledAndVerified() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertTrue(manager.completeInstall("lite-v1", staging))
        val result = manager.handshake("lite-v1", nowMs = 42L)
        assertTrue(result.ok)
        assertEquals(PackHandshakeResult.SIGNAL_OK, result.signal)
        assertTrue(result.wires.any { it.contains("Lite") })
        assertTrue(manager.isReady("lite-v1"))
    }

    @Test
    fun handshakeSkipsWhenNotInstalled() = runTest {
        val manager = ModelPackManager(FakeFs(), FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val result = manager.handshake("lite-v1")
        assertFalse(result.ok)
        assertEquals(PackHandshakeResult.SIGNAL_SKIP, result.signal)
    }

    @Test
    fun handshakeAllReportsWhichPackIsCurrentlyRunningBeforeItsResultLands() = runTest {
        // Regression for a real UI bug: PacksScreen used one shared "busy" flag for every pack
        // row, so tapping "Verify all" made every row look mid-check with no way to tell which
        // one actually was. handshakeAll must expose per-pack progress so the UI can show that.
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertTrue(manager.completeInstall("lite-v1", staging))

        val startedIds = mutableListOf<String>()
        val report = manager.handshakeAll(nowMs = 42L, onPackStarted = { id -> startedIds.add(id) })

        assertEquals(listOf("lite-v1"), startedIds)
        assertEquals(1, report.results.size)
        assertEquals("lite-v1", report.results.single().packId)
    }

    @Test
    fun markGraphIncompatibleClearsReadySoAutoSkipsPro() = runTest {
        val fs = FakeFs()
        val manager = ModelPackManager(fs, FakeProbe(), manifestClient(), "https://m/manifest.json")
        manager.refresh()
        val staging = manager.stagingDir(manager.pack("lite-v1")!!)
        fs.files["$staging/a.onnx"] = "A".repeat(60)
        fs.files["$staging/b.onnx"] = "B".repeat(40)
        fs.hashes["$staging/a.onnx"] = "hash-a"
        fs.hashes["$staging/b.onnx"] = "hash-b"
        assertTrue(manager.completeInstall("lite-v1", staging))
        assertTrue(manager.isReady("lite-v1"))
        manager.markGraphIncompatible(
            "lite-v1",
            "Pro pack UNet (unet.onnx) is incompatible with this ONNX Runtime (FP16 type mismatch).",
        )
        assertFalse(manager.isReady("lite-v1"))
        assertEquals(PackVerifyStatus.FAILED, manager.verifyStatus("lite-v1"))
        assertTrue(
            manager.states.value.getValue("lite-v1").verifyLabel()
                .contains("Incompatible", ignoreCase = true),
        )
    }

    @Test
    fun deviceSpecGate() {
        val manager = ModelPackManager(FakeFs(), FakeProbe(ram = 4096, npu = false), manifestClient(), "u")
        assertTrue(manager.deviceMeets(DeviceSpec(minRamMb = 0, requiresNpu = false)))
        assertFalse(manager.deviceMeets(DeviceSpec(minRamMb = 8000, requiresNpu = false)))
        assertFalse(manager.deviceMeets(DeviceSpec(minRamMb = 0, requiresNpu = true)))
        assertTrue(manager.deviceMeets(DeviceSpec(minRamMb = 0, requiresNpu = false, minSdk = 35)))
        assertFalse(
            manager.deviceMeets(DeviceSpec(minRamMb = 0, requiresNpu = false, minSdk = 36)),
        )
    }
}
