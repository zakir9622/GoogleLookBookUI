package com.zakir.vestra.ui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.packs.PackDownloadWorker
import com.zakir.vestra.storage.DurableStorage

fun Context.hasPostNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

fun Context.openAppSystemSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
            Settings.EXTRA_APP_PACKAGE,
            packageName,
        )
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * Enqueues a pack download after durable storage + optional [POST_NOTIFICATIONS] (API 33+),
 * with an enterprise rationale dialog before the system prompt.
 */
@Composable
fun rememberPackDownloadStarter(showToast: Boolean = true): (String) -> Unit {
    val context = LocalContext.current
    var pendingPackId by remember { mutableStateOf<String?>(null) }
    var showNotifRationale by remember { mutableStateOf(false) }
    var observingPackId by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val id = pendingPackId
        pendingPackId = null
        if (id != null) enqueuePackDownload(context, id, showToast) { observingPackId = id }
    }

    LaunchedEffect(observingPackId) {
        val packId = observingPackId ?: return@LaunchedEffect
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(PackDownloadWorker.workName(packId))
            .collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        if (showToast) {
                            Toast.makeText(
                                context,
                                "Pack installed — ready to use offline",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        observingPackId = null
                    }
                    WorkInfo.State.FAILED -> {
                        if (showToast) {
                            val err = info.outputData.getString(PackDownloadWorker.KEY_ERROR)
                            Toast.makeText(
                                context,
                                packDownloadErrorMessage(err),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        observingPackId = null
                    }
                    WorkInfo.State.CANCELLED -> observingPackId = null
                    else -> Unit
                }
            }
    }

    if (showNotifRationale) {
        AlertDialog(
            onDismissRequest = {
                showNotifRationale = false
                pendingPackId = null
            },
            title = { Text(LookbookCopy.PERM_NOTIFICATIONS_TITLE) },
            text = { Text(LookbookCopy.PERM_NOTIFICATIONS_BODY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotifRationale = false
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val id = pendingPackId
                        showNotifRationale = false
                        pendingPackId = null
                        // Still download without notifications.
                        if (id != null) enqueuePackDownload(context, id, showToast) { observingPackId = id }
                    },
                ) { Text("Not now") }
            },
        )
    }

    return remember(showToast) {
        { packId: String ->
            when {
                !DurableStorage.hasAllFilesAccess() -> {
                    Toast.makeText(
                        context,
                        "Allow all-files access for this multi-GB pack so it survives uninstall, then tap Download again",
                        Toast.LENGTH_LONG,
                    ).show()
                    runCatching { context.startActivity(DurableStorage.manageAllFilesIntent(context)) }
                }
                context.hasPostNotificationsPermission() ->
                    enqueuePackDownload(context, packId, showToast) { observingPackId = packId }
                else -> {
                    pendingPackId = packId
                    showNotifRationale = true
                }
            }
        }
    }
}

private fun packDownloadErrorMessage(error: String?): String = when (error) {
    "verify_failed" -> "Model verification failed — files may be corrupt; tap Download again"
    "checksum" -> "Download failed verification — tap Download again or check your connection"
    "no_space" -> "Not enough storage for this pack — free space and retry"
    "unknown_pack" -> "Pack not found in catalog — open Settings and pull to refresh"
    "download_failed" -> "Download interrupted — retry; partial files resume automatically"
    null -> "Download failed — retry from Settings → Model packs"
    else -> "Download failed: $error"
}

private fun enqueuePackDownload(
    context: Context,
    packId: String,
    showToast: Boolean,
    onEnqueued: () -> Unit = {},
) {
    PackDownloadWorker.enqueue(context, packId)
    onEnqueued()
    if (showToast) {
        Toast.makeText(
            context,
            "Download started — progress appears in notifications when allowed",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/**
 * Requests [CAMERA] with rationale, then runs [onGranted], or [onDenied] if refused.
 */
@Composable
fun rememberCameraGatedAction(onGranted: () -> Unit, onDenied: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    var awaiting by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!awaiting) return@rememberLauncherForActivityResult
        awaiting = false
        if (granted) onGranted() else onDenied()
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(LookbookCopy.PERM_CAMERA_TITLE) },
            text = { Text(LookbookCopy.PERM_CAMERA_BODY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        awaiting = true
                        launcher.launch(Manifest.permission.CAMERA)
                    },
                ) { Text("Allow camera") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        onDenied()
                    },
                ) { Text("Use photo picker") }
            },
        )
    }

    return {
        when {
            context.hasCameraPermission() -> onGranted()
            else -> showRationale = true
        }
    }
}
