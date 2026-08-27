package com.zakir.vestra.shared.engine.local

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import androidx.work.workDataOf
import com.zakir.vestra.shared.engine.litert.LiteRtLmEngineCache
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker that pre-warms the on-device Gemma 4 model
 * when the device is charging and connected to an unmetered Wi-Fi network.
 *
 * This ensures the heavy weights (~2.6 GB) are cached in memory ahead of time,
 * eliminating the cold-load delay on the user's first prompt.
 */
class Gemma4PrewarmWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = dependencies?.invoke()
            ?: run {
                Log.w(TAG, "ModelPackManager dependency not injected. Retrying later.")
                return Result.retry()
            }

        val packId = inputData.getString(KEY_PACK_ID) ?: LiteRtLmPacks.GEMMA4_CODE
        val primaryFile = inputData.getString(KEY_PRIMARY_FILE) ?: LiteRtLmPacks.GEMMA4_FILE
        val useGpu = if (inputData.hasKeyWithValueOfType<Boolean>(KEY_USE_GPU)) {
            inputData.getBoolean(KEY_USE_GPU, false)
        } else {
            gpuPreference?.invoke() ?: false
        }

        return executePrewarm(
            context = applicationContext,
            manager = manager,
            packId = packId,
            primaryFile = primaryFile,
            useGpu = useGpu,
        )
    }

    companion object {
        private const val TAG = "Gemma4PrewarmWorker"

        const val WORK_NAME_PERIODIC = "gemma4_prewarm_periodic"
        const val WORK_NAME_ONE_TIME = "gemma4_prewarm_onetime"

        const val KEY_PACK_ID = "pack_id"
        const val KEY_PRIMARY_FILE = "primary_file"
        const val KEY_USE_GPU = "use_gpu"
        const val KEY_STATUS = "status"

        /** Injected by host application at startup. */
        var dependencies: (() -> ModelPackManager)? = null

        /** Injected GPU preference hook. */
        var gpuPreference: (() -> Boolean)? = null

        /** Optional test override for the prewarm execution. */
        var customPrewarmAction: (suspend (Context, ModelPackManager, String, Boolean) -> String?)? = null

        /** Optional test override for pack ready check. */
        var packReadyOverride: ((ModelPackManager, String, String) -> Boolean)? = null

        /** Optional test override for installed dir resolution. */
        var installedDirOverride: ((ModelPackManager, String) -> String?)? = null

        /**
         * Core logic for verifying requirements and pre-warming the engine.
         */
        suspend fun executePrewarm(
            context: Context,
            manager: ModelPackManager,
            packId: String = LiteRtLmPacks.GEMMA4_CODE,
            primaryFile: String = LiteRtLmPacks.GEMMA4_FILE,
            useGpu: Boolean = false,
            minBytesOverride: Long? = null,
        ): Result {
            val minBytes = minBytesOverride ?: if (packId == LiteRtLmPacks.GEMMA4_CODE) {
                LiteRtLmPackLimits.MIN_GEMMA4_BYTES
            } else {
                LiteRtLmPackLimits.MIN_QWEN3_BYTES
            }

            // 1. Verify model pack is downloaded and intact
            val isReady = packReadyOverride?.invoke(manager, packId, primaryFile)
                ?: LiteRtLmInference.litertLmReady(manager, packId, primaryFile, minBytes)

            if (!isReady) {
                Log.i(TAG, "Model pack '$packId' is not downloaded or ready; skipping background pre-warm.")
                return Result.success(workDataOf(KEY_STATUS to "pack_not_ready"))
            }

            // 2. Check if active user inference is in flight
            if (LiteRtLmEngineCache.hasActiveInference()) {
                Log.w(TAG, "Active inference session in progress; rescheduling pre-warm.")
                return Result.retry()
            }

            val dir = installedDirOverride?.invoke(manager, packId)
                ?: manager.installedDir(packId)
                ?: return Result.success(workDataOf(KEY_STATUS to "pack_dir_missing"))
            val modelPath = LiteRtLmPackConfig.modelPath(File(dir), primaryFile)
                ?: return Result.success(workDataOf(KEY_STATUS to "model_file_missing"))

            // 3. Skip if already warm and loaded
            if (LiteRtLmEngineCache.isModelLoaded(modelPath)) {
                Log.i(TAG, "Gemma 4 model is already warm in memory ($modelPath).")
                return Result.success(workDataOf(KEY_STATUS to "already_warm"))
            }

            Log.i(TAG, "Executing background Gemma 4 pre-warm on unmetered Wi-Fi + charging...")

            val action = customPrewarmAction
            val errorMsg = if (action != null) {
                action(context, manager, packId, useGpu)
            } else {
                withContext(Dispatchers.IO) {
                    LiteRtLmInference.warmUpEngine(
                        context = context,
                        packs = manager,
                        packId = packId,
                        modelPath = modelPath,
                        useGpu = useGpu,
                    )
                }
            }

            return if (errorMsg != null) {
                Log.w(TAG, "Gemma 4 background pre-warm failed: $errorMsg")
                Result.retry()
            } else {
                Log.i(TAG, "Gemma 4 model successfully pre-warmed for immediate inference.")
                Result.success(workDataOf(KEY_STATUS to "prewarmed"))
            }
        }

        /**
         * Builds the WorkManager constraints ensuring execution only occurs
         * while the device is charging and on an unmetered Wi-Fi network.
         */
        fun buildConstraints(): Constraints =
            Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

        /**
         * Builds the periodic WorkRequest with charging and Wi-Fi constraints.
         */
        fun buildPeriodicWorkRequest(
            repeatIntervalHours: Long = 6,
            flexIntervalHours: Long = 1,
        ): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<Gemma4PrewarmWorker>(
                repeatIntervalHours,
                TimeUnit.HOURS,
                flexIntervalHours,
                TimeUnit.HOURS,
            )
                .setConstraints(buildConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()

        /**
         * Builds the one-time WorkRequest with charging and Wi-Fi constraints.
         */
        fun buildOneTimeWorkRequest(
            packId: String = LiteRtLmPacks.GEMMA4_CODE,
            useGpu: Boolean? = null,
        ): OneTimeWorkRequest {
            val dataBuilder = Data.Builder().putString(KEY_PACK_ID, packId)
            if (useGpu != null) {
                dataBuilder.putBoolean(KEY_USE_GPU, useGpu)
            }
            return OneTimeWorkRequestBuilder<Gemma4PrewarmWorker>()
                .setInputData(dataBuilder.build())
                .setConstraints(buildConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()
        }

        /**
         * Schedules periodic background pre-warming (default: every 6 hours with 1 hour flex)
         * with charging and Wi-Fi constraints.
         */
        fun schedulePeriodic(
            context: Context,
            repeatIntervalHours: Long = 6,
            flexIntervalHours: Long = 1,
            forceUpdate: Boolean = false,
        ) {
            val request = buildPeriodicWorkRequest(repeatIntervalHours, flexIntervalHours)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Enqueues a one-time pre-warm request with charging and Wi-Fi constraints.
         */
        fun enqueueOneTime(
            context: Context,
            packId: String = LiteRtLmPacks.GEMMA4_CODE,
            useGpu: Boolean? = null,
            replaceExisting: Boolean = false,
        ) {
            val request = buildOneTimeWorkRequest(packId, useGpu)
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Cancels all scheduled pre-warm jobs.
         */
        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME_PERIODIC)
            wm.cancelUniqueWork(WORK_NAME_ONE_TIME)
        }
    }
}
