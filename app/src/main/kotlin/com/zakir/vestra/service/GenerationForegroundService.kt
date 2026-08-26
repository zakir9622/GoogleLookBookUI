package com.zakir.vestra.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Android Foreground Service with WakeLock ensuring continuous execution
 * of AI model inference and virtual try-on pipelines even when the app is minimized
 * or running in the background.
 */
class GenerationForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GenerationNotificationHelper.ensureChannels(this)
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Lookbook:GenerationWakeLock",
            )?.apply {
                setReferenceCounted(false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "AI Generation in Progress"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Processing neural network layers..."
                val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, true)
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val maxProgress = intent.getIntExtra(EXTRA_MAX_PROGRESS, 100)

                val notification = GenerationNotificationHelper.buildProgressNotification(
                    context = this,
                    title = title,
                    statusText = subtitle,
                    progress = progress,
                    maxProgress = maxProgress,
                    indeterminate = indeterminate,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        GenerationNotificationHelper.NOTIFICATION_ID_PROGRESS,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(GenerationNotificationHelper.NOTIFICATION_ID_PROGRESS, notification)
                }
                isForegroundActive = true

                runCatching {
                    wakeLock?.acquire(15 * 60 * 1000L) // 15 min safeguard timeout
                }
            }

            ACTION_UPDATE_PROGRESS -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "AI Generation in Progress"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Processing..."
                val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, false)
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val maxProgress = intent.getIntExtra(EXTRA_MAX_PROGRESS, 100)

                val notification = GenerationNotificationHelper.buildProgressNotification(
                    context = this,
                    title = title,
                    statusText = subtitle,
                    progress = progress,
                    maxProgress = maxProgress,
                    indeterminate = indeterminate,
                )

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(GenerationNotificationHelper.NOTIFICATION_ID_PROGRESS, notification)
            }

            ACTION_COMPLETE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Generation Complete ✨"
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Your look has been rendered."
                val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
                val isFailure = intent.getBooleanExtra(EXTRA_IS_FAILURE, false)
                val deepLinkRoute = intent.getStringExtra(EXTRA_DEEP_LINK_ROUTE) ?: "tryon"

                // Always alert on completion
                GenerationNotificationHelper.postCompletionNotification(
                    context = this,
                    title = title,
                    message = message,
                    imagePath = imagePath,
                    isFailure = isFailure,
                    deepLinkRoute = deepLinkRoute,
                )

                stopServiceInternal()
            }

            ACTION_STOP -> {
                stopServiceInternal()
            }
        }

        return START_NOT_STICKY
    }

    private fun stopServiceInternal() {
        if (isForegroundActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForegroundActive = false
        }
        GenerationNotificationHelper.cancelOngoing(this)
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GenerationService"

        const val ACTION_START = "com.zakir.vestra.action.START_GENERATION"
        const val ACTION_UPDATE_PROGRESS = "com.zakir.vestra.action.UPDATE_PROGRESS"
        const val ACTION_COMPLETE = "com.zakir.vestra.action.COMPLETE_GENERATION"
        const val ACTION_STOP = "com.zakir.vestra.action.STOP_GENERATION"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_MAX_PROGRESS = "extra_max_progress"
        const val EXTRA_INDETERMINATE = "extra_indeterminate"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_IS_FAILURE = "extra_is_failure"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_DEEP_LINK_ROUTE = "extra_deep_link_route"

        fun start(
            context: Context,
            title: String = "Virtual Try-On in Progress",
            subtitle: String = "Synthesizing outfit onto model...",
        ) {
            val intent = Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_INDETERMINATE, true)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start generation foreground service: ${e.message}")
            }
        }

        fun updateProgress(
            context: Context,
            title: String,
            subtitle: String,
            progress: Int,
            maxProgress: Int = 100,
        ) {
            val intent = Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_MAX_PROGRESS, maxProgress)
                putExtra(EXTRA_INDETERMINATE, false)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update progress: ${e.message}")
            }
        }

        fun complete(
            context: Context,
            title: String,
            message: String,
            imagePath: String? = null,
            isFailure: Boolean = false,
            deepLinkRoute: String = "tryon",
        ) {
            val intent = Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_IMAGE_PATH, imagePath)
                putExtra(EXTRA_IS_FAILURE, isFailure)
                putExtra(EXTRA_DEEP_LINK_ROUTE, deepLinkRoute)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // If service was stopped, post notification directly
                GenerationNotificationHelper.postCompletionNotification(
                    context = context,
                    title = title,
                    message = message,
                    imagePath = imagePath,
                    isFailure = isFailure,
                    deepLinkRoute = deepLinkRoute,
                )
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GenerationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop service: ${e.message}")
            }
        }
    }
}
