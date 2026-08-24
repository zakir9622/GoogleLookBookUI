package com.zakir.vestra.shared.packs

import com.zakir.vestra.shared.domain.DeviceSpec
import com.zakir.vestra.shared.domain.ModelPack
import com.zakir.vestra.shared.domain.PackVerifyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import com.zakir.vestra.shared.domain.PackManifest
import com.zakir.vestra.shared.domain.PackState
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.time.EpochClock

/**
 * Tracks which model packs exist, which are installed, and where their files
 * live. Downloading itself is platform work (WorkManager on Android) driven
 * through [markDownloading]/[completeInstall]; this class owns all state and
 * validation so that logic is shared and unit-testable.
 *
 * Layout: <packsRoot>/<packId>/<version>/<files…> with a `.complete` marker
 * written only after every file passed its sha256 check **and** optional ONNX
 * verification via [integrityChecker].
 */
class ModelPackManager(
    private val fs: PackFileSystem,
    private val device: DeviceProbe,
    private val http: HttpClient,
    private val manifestUrl: String,
    private val integrityChecker: PackIntegrityChecker = NoOpPackIntegrityChecker,
    /**
     * Called before pack files are deleted or replaced (uninstall / install commit).
     * Android closes cached ORT sessions that point into [packRoot].
     */
    private val onPackFilesChanging: (packRoot: String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _states = MutableStateFlow<Map<String, PackState>>(emptyMap())
    val states: StateFlow<Map<String, PackState>> = _states

    /** Last catalog-load failure (null when the catalog loaded). Surfaced in the UI. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /** Serializes ONNX verification (install + startup) so parallel callers cannot OOM. */
    private val integrityLock = Any()

    /**
     * Refcount of pack IDs referenced by active engine / quality runs.
     * Blocks [uninstall], [completeInstall], and [commitVerifiedInstall] while > 0.
     */
    private val activePackCounts = mutableMapOf<String, Int>()

    fun markPackInUse(id: String) {
        synchronized(activePackCounts) {
            activePackCounts[id] = (activePackCounts[id] ?: 0) + 1
        }
    }

    fun markPackIdle(id: String) {
        synchronized(activePackCounts) {
            val next = (activePackCounts[id] ?: 0) - 1
            if (next <= 0) activePackCounts.remove(id) else activePackCounts[id] = next
        }
    }

    fun isPackInUse(id: String): Boolean =
        synchronized(activePackCounts) { (activePackCounts[id] ?: 0) > 0 }

    /** Loads the last manifest fetched (offline start), then refreshes over the network. */
    suspend fun refresh(networkAllowed: Boolean = true) {
        withContext(Dispatchers.Default) {
            // Runs even without a cached catalog so bundled packs are still discovered.
            rebuildStates(cachedManifest() ?: PackManifest(schemaVersion = 1, packs = emptyList()))
        }
        if (!networkAllowed) return
        runCatching {
            val response = http.get(manifestUrl)
            check(response.status.isSuccess()) { "server returned HTTP ${response.status.value}" }
            val body = response.bodyAsText()
            val manifest = json.decodeFromString<PackManifest>(body)
            withContext(Dispatchers.Default) {
                fs.mkdirs(fs.packsRoot())
                fs.writeText(cachePath(), body)
                rebuildStates(manifest)
            }
        }.onSuccess {
            _lastError.value = null
        }.onFailure { e ->
            // Don't hide it: keep any cached catalog on screen, but report why the
            // refresh failed so the UI can show it and offer a retry.
            _lastError.value = e.message ?: e::class.simpleName ?: "unknown error"
        }
    }

    fun pack(id: String): ModelPack? = _states.value[id]?.pack

    /** Directory containing an installed pack's files, or null when not installed. */
    fun installedDir(id: String): String? {
        val pack = pack(id) ?: return null
        val dir = versionDir(pack)
        return dir.takeIf { fs.exists("$dir/$COMPLETE_MARKER") }
    }

    fun isInstalled(id: String): Boolean = installedDir(id) != null

    /** Installed and passed file + ONNX verification — safe for local inference. */
    fun isReady(id: String): Boolean = _states.value[id]?.isReady() == true

    fun verifyStatus(id: String): PackVerifyStatus =
        _states.value[id]?.verifyStatus ?: PackVerifyStatus.UNKNOWN

    /**
     * Re-validates an installed pack (files + ONNX). Updates [states] with
     * [PackVerifyStatus.VERIFIED] or [PackVerifyStatus.FAILED].
     */
    suspend fun verifyInstalled(id: String): Boolean = withContext(Dispatchers.Default) {
        synchronized(integrityLock) {
            val state = _states.value[id] ?: return@withContext false
            if (state.status != PackStatus.INSTALLED) return@withContext false
            val dir = installedDir(id) ?: return@withContext false
            markVerifying(id)
            val error = runIntegrityChecks(state.pack, dir)
            if (error == null) {
                markVerified(id)
                true
            } else {
                markVerifyFailed(id, error)
                false
            }
        }
    }

    /**
     * Re-checks installed packs after [refresh] on startup.
     *
     * Already-[PackVerifyStatus.VERIFIED] packs only re-check file sizes (no ONNX
     * session create) to avoid a verify→native-kill→restart storm on Pixel-class devices.
     * Unverified / failed packs still run the full integrity path once.
     */
    suspend fun verifyAllInstalled() = withContext(Dispatchers.Default) {
        synchronized(integrityLock) {
            _states.value.values
                .filter { it.status == PackStatus.INSTALLED }
                .forEach { state ->
                    val dir = installedDir(state.pack.id) ?: return@forEach
                    markVerifying(state.pack.id)
                    val fileError = integrityChecker.verifyFiles(state.pack, dir)
                    if (fileError != null) {
                        markVerifyFailed(state.pack.id, fileError)
                        return@forEach
                    }
                    if (state.verifyStatus == PackVerifyStatus.VERIFIED) {
                        markVerified(state.pack.id)
                        return@forEach
                    }
                    val onnxError = integrityChecker.verifyOnnx(state.pack, dir)
                    if (onnxError == null) {
                        markVerified(state.pack.id)
                    } else {
                        markVerifyFailed(state.pack.id, onnxError)
                    }
                }
        }
    }

    /**
     * Record that an installed pack's graphs cannot load on this device (ORT type
     * mismatch, invalid ControlNet, etc.). Marks verify FAILED so [isReady] is false
     * and AUTO try-on stays on Lite without another Pro generate.
     */
    fun markGraphIncompatible(id: String, error: String) {
        markVerifyFailed(id, error.take(220))
    }

    /**
     * Explicit device handshake for one pack: re-verify files + graphs, then
     * report which studio wires are linked. Used by Settings / Packs “Verify” button.
     * Pro packs open UNet once here (not on every cold start).
     */
    suspend fun handshake(id: String, nowMs: Long = EpochClock.System.nowMs()): PackHandshakeResult {
        val state = _states.value[id]
        val wires = PackHandshakeWires.forPackId(id)
        if (state == null) {
            return PackHandshakeResult(
                packId = id,
                displayName = id,
                ok = false,
                signal = PackHandshakeResult.SIGNAL_SKIP,
                message = "Pack not in catalog",
                wires = wires,
                verifiedAtMs = nowMs,
            )
        }
        if (state.status != PackStatus.INSTALLED) {
            return PackHandshakeResult(
                packId = id,
                displayName = state.pack.displayName,
                ok = false,
                signal = PackHandshakeResult.SIGNAL_SKIP,
                message = "Not installed — download first",
                wires = wires,
                verifiedAtMs = nowMs,
            )
        }
        val ok = verifyInstalledHandshake(id)
        val after = _states.value[id]
        val isPro = id.startsWith("pro-")
        return if (ok) {
            PackHandshakeResult(
                packId = id,
                displayName = state.pack.displayName,
                ok = true,
                signal = PackHandshakeResult.SIGNAL_OK,
                message = if (isPro) {
                    "Linked to device · Pro UNet session opened OK"
                } else {
                    "Linked to device · files + engine graphs OK"
                },
                wires = wires,
                verifiedAtMs = after?.verifiedAtMs ?: nowMs,
            )
        } else {
            PackHandshakeResult(
                packId = id,
                displayName = state.pack.displayName,
                ok = false,
                signal = PackHandshakeResult.SIGNAL_FAIL,
                message = after?.verifyError ?: "Integrity check failed — re-download",
                wires = wires,
                verifiedAtMs = nowMs,
            )
        }
    }

    /** Handshake verify — may deep-probe Pro UNet. */
    private suspend fun verifyInstalledHandshake(id: String): Boolean = withContext(Dispatchers.Default) {
        synchronized(integrityLock) {
            val state = _states.value[id] ?: return@withContext false
            if (state.status != PackStatus.INSTALLED) return@withContext false
            val dir = installedDir(id) ?: return@withContext false
            markVerifying(id)
            val fileError = integrityChecker.verifyFiles(state.pack, dir)
            if (fileError != null) {
                markVerifyFailed(id, fileError)
                return@withContext false
            }
            val onnxError = integrityChecker.verifyOnnxHandshake(state.pack, dir)
            if (onnxError == null) {
                markVerified(id)
                true
            } else {
                markVerifyFailed(id, onnxError)
                false
            }
        }
    }

    /**
     * Handshake every installed pack, one at a time; returns an aggregate ACK/NACK report.
     * [onPackStarted] fires just before each pack's own handshake begins so a caller can show
     * which specific pack is being checked right now, rather than one shared "busy" flag that
     * makes every pack row look like it's mid-check at once.
     */
    suspend fun handshakeAll(
        nowMs: Long = EpochClock.System.nowMs(),
        onPackStarted: (packId: String) -> Unit = {},
    ): PackHandshakeReport {
        val started = nowMs
        val installed = _states.value.values
            .filter { it.status == PackStatus.INSTALLED }
            .map { it.pack.id }
        val results = installed.map { id ->
            onPackStarted(id)
            handshake(id, nowMs = EpochClock.System.nowMs())
        }
        return PackHandshakeReport(
            results = results,
            startedAtMs = started,
            finishedAtMs = EpochClock.System.nowMs(),
        )
    }

    fun markDownloading(id: String, progress: Float) {
        updateStatus(id) { it.copy(status = PackStatus.DOWNLOADING, progress = progress) }
    }

    fun markFailed(id: String) {
        updateStatus(id) {
            it.copy(
                status = PackStatus.NOT_INSTALLED,
                progress = 0f,
                verifyStatus = PackVerifyStatus.UNKNOWN,
                verifyError = null,
                verifiedAtMs = null,
            )
        }
    }

    /** Drop an in-flight download UI state while keeping staged bytes for a later resume. */
    fun markCancelled(id: String) {
        updateStatus(id) { current ->
            val staged = stagedBytes(current.pack)
            val progress = (staged.toFloat() / current.pack.totalBytes.coerceAtLeast(1)).coerceIn(0f, 0.99f)
            current.copy(status = PackStatus.NOT_INSTALLED, progress = progress)
        }
    }

    /** Bytes already staged for [pack] (used to restore DOWNLOADING after catalog refresh). */
    fun stagedBytes(pack: ModelPack): Long {
        val staging = stagingDir(pack)
        return pack.files.sumOf { file ->
            val path = "$staging/${file.path}"
            if (fs.exists(path)) fs.fileSize(path).coerceAtMost(file.bytes) else 0L
        }
    }

    fun hasPartialStaging(pack: ModelPack): Boolean = stagedBytes(pack) > 0L

    /**
     * Called by the platform downloader once all files are staged. Verifies
     * every sha256 and ONNX loadability before committing; a corrupt file aborts the install.
     */
    fun completeInstall(id: String, stagingDir: String): Boolean {
        val pack = pack(id) ?: return false
        // Never replace files under open ORT sessions — retry after the run finishes.
        if (isPackInUse(id)) return false
        for (file in pack.files) {
            val staged = "$stagingDir/${file.path}"
            if (!fs.exists(staged) || fs.sha256(staged) != file.sha256) {
                markFailed(id)
                return false
            }
        }
        synchronized(integrityLock) {
            runIntegrityChecks(pack, stagingDir)?.let {
                markFailed(id)
                return false
            }
        }
        val packRoot = "${fs.packsRoot()}/${pack.id}"
        val target = versionDir(pack)
        onPackFilesChanging(packRoot)
        fs.delete(target)
        fs.mkdirs(parentOf(target))
        fs.move(stagingDir, target)
        fs.writeText("$target/$COMPLETE_MARKER", pack.version.toString())
        fs.writeText("$target/$ONNX_OK_MARKER", "ok")
        // Older versions of this pack are dead weight now.
        fs.listFiles(packRoot)
            .filter { it != target }
            .forEach { old ->
                onPackFilesChanging(old)
                fs.delete(old)
            }
        updateStatus(id) {
            it.copy(
                status = PackStatus.INSTALLED,
                progress = 1f,
                verifyStatus = PackVerifyStatus.VERIFIED,
                verifyError = null,
                verifiedAtMs = EpochClock.System.nowMs(),
            )
        }
        return true
    }

    /**
     * Commits a pre-verified directory (e.g. debug bootstrap) without re-checking sha256.
     * Still runs file + ONNX verification when a checker is configured.
     */
    fun commitVerifiedInstall(id: String, sourceDir: String): Boolean {
        val pack = pack(id) ?: return false
        if (isPackInUse(id)) return false
        synchronized(integrityLock) {
            runIntegrityChecks(pack, sourceDir)?.let { return false }
        }
        val packRoot = "${fs.packsRoot()}/${pack.id}"
        val target = versionDir(pack)
        if (target != sourceDir) {
            onPackFilesChanging(packRoot)
            fs.delete(target)
            fs.mkdirs(parentOf(target))
            if (fs.exists(sourceDir)) {
                fs.move(sourceDir, target)
            }
        }
        fs.writeText("$target/$COMPLETE_MARKER", pack.version.toString())
        fs.writeText("$target/$ONNX_OK_MARKER", "ok")
        updateStatus(id) {
            it.copy(
                status = PackStatus.INSTALLED,
                progress = 1f,
                verifyStatus = PackVerifyStatus.VERIFIED,
                verifyError = null,
                verifiedAtMs = EpochClock.System.nowMs(),
            )
        }
        return true
    }

    fun uninstall(id: String): Boolean {
        if (isPackInUse(id)) return false
        val packRoot = "${fs.packsRoot()}/$id"
        onPackFilesChanging(packRoot)
        fs.delete(packRoot)
        updateStatus(id) {
            it.copy(
                status = PackStatus.NOT_INSTALLED,
                progress = 0f,
                verifyStatus = PackVerifyStatus.UNKNOWN,
                verifyError = null,
                verifiedAtMs = null,
            )
        }
        return true
    }

    fun deviceMeets(spec: DeviceSpec): Boolean =
        device.sdkInt() >= spec.minSdk &&
            device.totalRamMb() >= spec.minRamMb &&
            (!spec.requiresNpu || device.hasNpu())

    /** True when the volume has room for the pack plus a safety margin. */
    fun hasSpaceFor(pack: ModelPack): Boolean =
        fs.freeBytes() > pack.totalBytes + SPACE_MARGIN_BYTES

    /** Free bytes on the volume holding the packs root — for a storage-used rollup in the UI. */
    fun freeBytesOnDevice(): Long = fs.freeBytes()

    fun deviceRamMb(): Long = device.totalRamMb()

    fun deviceSdkInt(): Int = device.sdkInt()

    fun deviceHasNpu(): Boolean = device.hasNpu()

    fun stagingDir(pack: ModelPack): String = "${fs.packsRoot()}/.staging/${pack.id}"

    private fun runIntegrityChecks(pack: ModelPack, dir: String): String? {
        integrityChecker.verifyFiles(pack, dir)?.let { return it }
        return integrityChecker.verifyOnnx(pack, dir)
    }

    private fun markVerifying(id: String) {
        updateStatus(id) { it.copy(verifyStatus = PackVerifyStatus.VERIFYING, verifyError = null) }
    }

    private fun markVerified(id: String) {
        updateStatus(id) {
            it.copy(
                verifyStatus = PackVerifyStatus.VERIFIED,
                verifyError = null,
                verifiedAtMs = EpochClock.System.nowMs(),
            )
        }
        installedDir(id)?.let { dir ->
            runCatching { fs.writeText("$dir/$ONNX_OK_MARKER", "ok") }
        }
    }

    private fun markVerifyFailed(id: String, error: String) {
        updateStatus(id) {
            it.copy(
                verifyStatus = PackVerifyStatus.FAILED,
                verifyError = error,
                verifiedAtMs = null,
            )
        }
        installedDir(id)?.let { dir ->
            val marker = "$dir/$ONNX_OK_MARKER"
            if (fs.exists(marker)) runCatching { fs.delete(marker) }
        }
    }

    private fun rebuildStates(manifest: PackManifest) {
        val previous = _states.value
        _states.value = withBundledPacks(manifest.packs).associate { pack ->
            val dir = versionDir(pack)
            val markerPath = "$dir/$COMPLETE_MARKER"
            val prior = previous[pack.id]
            val staged = stagedBytes(pack)

            // Drop stale .complete markers when files are missing or wrong size.
            var status = when {
                fs.exists(markerPath) && !filesIntact(pack, dir) -> {
                    fs.delete(markerPath)
                    PackStatus.NOT_INSTALLED
                }
                fs.exists(markerPath) -> PackStatus.INSTALLED
                prior?.status == PackStatus.DOWNLOADING -> PackStatus.DOWNLOADING
                fs.listFiles("${fs.packsRoot()}/${pack.id}")
                    .any { fs.exists("$it/$COMPLETE_MARKER") } -> PackStatus.UPDATE_AVAILABLE
                !deviceMeets(pack.minSpec) -> PackStatus.INCOMPATIBLE
                else -> PackStatus.NOT_INSTALLED
            }
            val verifyStatus = when {
                status != PackStatus.INSTALLED -> PackVerifyStatus.UNKNOWN
                prior?.pack?.version == pack.version &&
                    prior.verifyStatus == PackVerifyStatus.VERIFIED -> PackVerifyStatus.VERIFIED
                fs.exists("$dir/$ONNX_OK_MARKER") -> PackVerifyStatus.VERIFIED
                else -> PackVerifyStatus.UNKNOWN
            }
            val progress = when (status) {
                PackStatus.DOWNLOADING -> {
                    if (prior?.status == PackStatus.DOWNLOADING && prior.progress > 0f) {
                        prior.progress
                    } else {
                        (staged.toFloat() / pack.totalBytes.coerceAtLeast(1)).coerceIn(0f, 0.99f)
                    }
                }
                PackStatus.INSTALLED -> 1f
                PackStatus.NOT_INSTALLED ->
                    (staged.toFloat() / pack.totalBytes.coerceAtLeast(1)).coerceIn(0f, 0.99f)
                else -> 0f
            }
            pack.id to PackState(
                pack = pack,
                status = status,
                progress = progress,
                verifyStatus = verifyStatus,
                verifyError = prior?.verifyError.takeIf { verifyStatus == PackVerifyStatus.FAILED },
                verifiedAtMs = prior?.verifiedAtMs.takeIf { verifyStatus == PackVerifyStatus.VERIFIED },
            )
        }
    }

    private fun filesIntact(pack: ModelPack, dir: String): Boolean =
        pack.files.all { file ->
            val path = "$dir/${file.path}"
            fs.exists(path) && fs.fileSize(path) == file.bytes
        }

    private fun cachedManifest(): PackManifest? =
        fs.readText(cachePath())?.let { cached ->
            runCatching { json.decodeFromString<PackManifest>(cached) }.getOrNull()
        }

    /**
     * Packs that shipped with the build or were sideloaded are not in the published catalog,
     * so a remote refresh would otherwise drop them and report an installed pack as missing.
     */
    private fun withBundledPacks(remote: List<ModelPack>): List<ModelPack> {
        val bundled = fs.readText(bundledPath())
            ?.let { runCatching { json.decodeFromString<PackManifest>(it) }.getOrNull() }
            ?.packs
            .orEmpty()
        val remoteIds = remote.map { it.id }.toSet()
        return remote + bundled.filter { it.id !in remoteIds }
    }

    private fun updateStatus(id: String, transform: (PackState) -> PackState) {
        val current = _states.value[id] ?: return
        _states.value = _states.value + (id to transform(current))
    }

    private fun versionDir(pack: ModelPack): String =
        "${fs.packsRoot()}/${pack.id}/${pack.version}"

    private fun cachePath(): String = "${fs.packsRoot()}/manifest.cache.json"

    private fun bundledPath(): String = "${fs.packsRoot()}/$BUNDLED_MANIFEST"

    private fun parentOf(path: String): String = path.substringBeforeLast('/')

    companion object {
        const val COMPLETE_MARKER = ".complete"
        /** Written after a successful ONNX (or light) verify so cold starts skip session create. */
        const val ONNX_OK_MARKER = ".onnx_ok"

        /** Catalog entries for packs installed outside the published manifest. */
        const val BUNDLED_MANIFEST = "manifest.bundled.json"
        private const val SPACE_MARGIN_BYTES = 500L * 1024 * 1024
    }
}
