package com.zakir.vestra.shared.packs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zakir.vestra.shared.domain.ModelPack
import com.zakir.vestra.shared.domain.PackFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Resumable model-pack download. Files download in parallel (up to [PARALLEL_FILES])
 * with HTTP Range resume. Verification and the atomic install commit happen in
 * ModelPackManager.completeInstall.
 *
 * The host app must provide the manager via [PackDownloadWorker.dependencies].
 */
class PackDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = dependencies?.invoke()
            ?: return Result.failure()
        val packId = inputData.getString(KEY_PACK_ID) ?: return Result.failure()
        manager.refresh(networkAllowed = true)
        val pack = manager.pack(packId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "unknown_pack"))

        if (!manager.hasSpaceFor(pack)) {
            manager.markFailed(packId)
            return Result.failure(workDataOf(KEY_ERROR to "no_space"))
        }

        val stagingDir = File(manager.stagingDir(pack)).apply { mkdirs() }
        val doneBytes = AtomicLong(
            pack.files.sumOf { file ->
                File(stagingDir, file.path).takeIf { it.exists() }?.length()?.coerceAtMost(file.bytes) ?: 0L
            },
        )
        val startFraction = (doneBytes.get().toFloat() / pack.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
        manager.markDownloading(packId, startFraction)
        // FGS may be blocked when WorkManager retries from the background
        // (ForegroundServiceStartNotAllowedException). Retry later rather than crash.
        if (!promoteToForeground(pack.displayName, (startFraction * 100).toInt())) {
            return Result.retry()
        }
        setProgress(workDataOf(KEY_PROGRESS to startFraction))

        val http = HttpClient(OkHttp) {
            install(HttpTimeout) {
                // Streaming multi-GB transfers — don't abort mid-file on wall clock.
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 45_000
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
            engine {
                config {
                    retryOnConnectionFailure(true)
                    followRedirects(true)
                    followSslRedirects(true)
                }
            }
        }
        try {
            val gate = Semaphore(PARALLEL_FILES)
            coroutineScope {
                pack.files.map { file ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            downloadOneFile(
                                http = http,
                                pack = pack,
                                file = file,
                                stagingDir = stagingDir,
                                doneBytes = doneBytes,
                                manager = manager,
                                packId = packId,
                            )
                        }
                    }
                }.awaitAll()
            }

            return if (manager.completeInstall(packId, stagingDir.absolutePath)) {
                Result.success()
            } else {
                stagingDir.deleteRecursively()
                manager.markFailed(packId)
                Result.failure(workDataOf(KEY_ERROR to "checksum"))
            }
        } catch (error: Exception) {
            // Keep staging + DOWNLOADING so UI shows resume progress across retries.
            val fraction = (doneBytes.get().toFloat() / pack.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
            manager.markDownloading(packId, fraction)
            return if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                manager.markFailed(packId)
                Result.failure(workDataOf(KEY_ERROR to (error.message ?: "download_failed")))
            }
        } finally {
            http.close()
        }
    }

    private suspend fun downloadOneFile(
        http: HttpClient,
        pack: ModelPack,
        file: PackFile,
        stagingDir: File,
        doneBytes: AtomicLong,
        manager: ModelPackManager,
        packId: String,
    ) = withContext(Dispatchers.IO) {
        val target = File(stagingDir, file.path).apply { parentFile?.mkdirs() }
        var existing = target.length()
        if (existing > file.bytes) {
            target.delete()
            existing = 0
        }
        if (existing == file.bytes) return@withContext

        http.prepareGet(file.url) {
            if (existing > 0) header(HttpHeaders.Range, "bytes=$existing-")
        }.execute { response ->
            when (response.status) {
                HttpStatusCode.OK -> {
                    if (existing > 0) {
                        // Server ignored Range — restart this file; adjust counter.
                        doneBytes.addAndGet(-existing)
                        target.delete()
                        existing = 0
                    }
                }
                HttpStatusCode.PartialContent -> {
                    if (existing <= 0) error("Unexpected 206 without Range request")
                }
                HttpStatusCode.RequestedRangeNotSatisfiable -> {
                    doneBytes.addAndGet(-existing)
                    target.delete()
                    existing = 0
                    error("Range not satisfiable — restarting ${file.path}")
                }
                else -> error("Download failed HTTP ${response.status.value} for ${file.path}")
            }

            FileOutputStream(target, existing > 0 && response.status == HttpStatusCode.PartialContent).use { out ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(BUFFER_BYTES)
                var lastUiAt = 0L
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    out.write(buffer, 0, read)
                    val total = doneBytes.addAndGet(read.toLong())
                    val now = System.currentTimeMillis()
                    if (now - lastUiAt >= 350L) {
                        lastUiAt = now
                        val fraction = (total.toFloat() / pack.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
                        manager.markDownloading(packId, fraction)
                        setProgress(workDataOf(KEY_PROGRESS to fraction))
                        promoteToForeground(pack.displayName, (fraction * 100).toInt())
                    }
                }
                out.flush()
            }
        }

        if (target.length() != file.bytes) {
            error("Incomplete ${file.path}: got ${target.length()}, expected ${file.bytes}")
        }
        val fraction = (doneBytes.get().toFloat() / pack.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
        manager.markDownloading(packId, fraction)
        setProgress(workDataOf(KEY_PROGRESS to fraction))
        promoteToForeground(pack.displayName, (fraction * 100).toInt())
    }

    /**
     * Promotes this worker to a dataSync foreground service. Returns false when
     * the OS refuses FGS start (background retry); other failures are swallowed
     * so progress updates never abort an in-flight download.
     */
    private suspend fun promoteToForeground(packName: String, percent: Int): Boolean =
        try {
            setForeground(foregroundInfo(packName, percent))
            true
        } catch (_: Exception) {
            false
        }

    private fun foregroundInfo(packName: String, percent: Int): ForegroundInfo {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW),
        )
        val notification: Notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading $packName")
            .setContentText(if (percent > 0) "$percent% — parallel · resumes if interrupted" else "Starting…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_PACK_ID = "pack_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "pack_downloads"
        private const val NOTIFICATION_ID = 2001
        private const val MAX_RETRIES = 8
        private const val PARALLEL_FILES = 3
        private const val BUFFER_BYTES = 1024 * 1024

        /** Injected by the app's composition root before any work is enqueued. */
        var dependencies: (() -> ModelPackManager)? = null

        fun workName(packId: String): String = "pack_download_$packId"

        fun enqueue(context: Context, packId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<PackDownloadWorker>()
                .setInputData(Data.Builder().putString(KEY_PACK_ID, packId).build())
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(packId),
                // REPLACE lets Force stop → Download restart immediately instead of KEEP stalling.
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context, packId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(packId))
        }
    }
}
