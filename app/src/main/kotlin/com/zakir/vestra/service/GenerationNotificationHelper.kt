package com.zakir.vestra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zakir.vestra.MainActivity
import com.zakir.vestra.R
import java.io.File

/**
 * Manages notification channels, foreground service progress notifications,
 * and high-priority completion alerts for on-device & cloud generation jobs.
 */
object GenerationNotificationHelper {

    const val CHANNEL_PROGRESS_ID = "lookbook_gen_progress_v2"
    const val CHANNEL_COMPLETE_ID = "lookbook_gen_complete_v2"

    const val NOTIFICATION_ID_PROGRESS = 4001
    const val NOTIFICATION_ID_COMPLETE = 4002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Ongoing progress channel (Low importance: quiet, no sound spam during denoise steps)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Generation in Progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows live progress and steps while generating virtual try-on or studio looks"
                setShowBadge(false)
            }
            manager.createNotificationChannel(progressChannel)

            // Completion alert channel (High importance: heads-up banner, vibration, sound)
            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                "Generation Complete",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts you with a preview when your AI generation is ready"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(completeChannel)
        }
    }

    fun buildProgressNotification(
        context: Context,
        title: String,
        statusText: String,
        progress: Int = 0,
        maxProgress: Int = 100,
        indeterminate: Boolean = true,
    ): Notification {
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(maxProgress.coerceAtLeast(1), progress.coerceIn(0, maxProgress), false)
        }

        return builder.build()
    }

    fun postCompletionNotification(
        context: Context,
        title: String,
        message: String,
        imagePath: String? = null,
        isFailure: Boolean = false,
        deepLinkRoute: String = "tryon",
    ) {
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("lookbook://screen/$deepLinkRoute"),
            context,
            MainActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (!isFailure && imagePath != null) {
            val file = File(imagePath)
            if (file.exists()) {
                val bitmap = decodeSampledBitmap(file.absolutePath, 512, 512)
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap)
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .setBigContentTitle(title)
                            .setSummaryText(message),
                    )
                }
            }
        }

        manager.notify(NOTIFICATION_ID_COMPLETE, builder.build())
    }

    fun cancelOngoing(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(NOTIFICATION_ID_PROGRESS)
    }

    private fun decodeSampledBitmap(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(filePath, options)
        }.getOrNull()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
