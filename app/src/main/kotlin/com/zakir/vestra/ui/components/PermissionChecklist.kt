package com.zakir.vestra.ui.components

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.storage.DurableStorage
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.hasAudioPermission
import com.zakir.vestra.ui.util.hasCameraPermission
import com.zakir.vestra.ui.util.hasDurableStoragePermission
import com.zakir.vestra.ui.util.hasPostNotificationsPermission
import com.zakir.vestra.ui.util.openAppSystemSettings
import com.zakir.vestra.ui.util.openManageStorageSettings
import com.zakir.vestra.ui.util.openNotificationSettings

data class PermissionEntry(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isMandatory: Boolean = true,
    val onAction: (Context) -> Unit,
)

/**
 * Interactive checklist showing mandatory permissions with real-time ✓ / ✗ status
 * and one-tap direct action to request or navigate to permission settings.
 */
@Composable
fun PermissionChecklist(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onAllGrantedChanged: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    var refreshEpoch by remember { mutableIntStateOf(0) }

    // Live refresh when returning from settings or system permission dialog
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshEpoch++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission request launchers
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshEpoch++ }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshEpoch++ }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshEpoch++ }

    val hasNotif = remember(refreshEpoch) { context.hasPostNotificationsPermission() }
    val hasCam = remember(refreshEpoch) { context.hasCameraPermission() }
    val hasMic = remember(refreshEpoch) { context.hasAudioPermission() }
    val hasStorage = remember(refreshEpoch) { context.hasDurableStoragePermission() }

    val allGranted = hasNotif && hasCam && hasMic && hasStorage
    DisposableEffect(allGranted) {
        onAllGrantedChanged?.invoke(allGranted)
        onDispose { }
    }

    val permissions = remember(refreshEpoch) {
        listOf(
            PermissionEntry(
                key = "notifications",
                title = "Notifications",
                description = "Pack download alerts & generation status",
                icon = Icons.Outlined.Notifications,
                isGranted = hasNotif,
                isMandatory = true,
                onAction = { ctx ->
                    if (!hasNotif) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ctx.openNotificationSettings()
                        }
                    } else {
                        ctx.openNotificationSettings()
                    }
                },
            ),
            PermissionEntry(
                key = "camera",
                title = "Camera Access",
                description = "Garment capture & real-time virtual try-on",
                icon = Icons.Outlined.CameraAlt,
                isGranted = hasCam,
                isMandatory = true,
                onAction = { ctx ->
                    if (!hasCam) {
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        ctx.openAppSystemSettings()
                    }
                },
            ),
            PermissionEntry(
                key = "audio",
                title = "Microphone & Voice",
                description = "Hands-free voice prompt dictation",
                icon = Icons.Outlined.Mic,
                isGranted = hasMic,
                isMandatory = false,
                onAction = { ctx ->
                    if (!hasMic) {
                        audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        ctx.openAppSystemSettings()
                    }
                },
            ),
            PermissionEntry(
                key = "storage",
                title = "Durable Storage",
                description = "Preserves downloaded AI packs across reinstalls",
                icon = Icons.Outlined.Folder,
                isGranted = hasStorage,
                isMandatory = true,
                onAction = { ctx ->
                    ctx.openManageStorageSettings()
                },
            ),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MANDATORY PERMISSIONS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = VestraColors.Accent,
                )
                val grantedCount = permissions.count { it.isGranted }
                Text(
                    text = "$grantedCount/${permissions.size} Granted",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (grantedCount == permissions.size) VestraColors.Accent else VestraColors.InkMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        permissions.forEach { perm ->
            PermissionItemRow(
                entry = perm,
                onClick = { perm.onAction(context) },
            )
        }
    }
}

@Composable
fun PermissionItemRow(
    entry: PermissionEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val granted = entry.isGranted

    val borderColor by animateColorAsState(
        targetValue = if (granted) VestraColors.Accent.copy(alpha = 0.5f) else VestraColors.GlassBorder.copy(alpha = 0.5f),
        label = "permBorder",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VestraColors.SurfaceRaised)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (granted) VestraColors.Accent.copy(alpha = 0.15f)
                    else VestraColors.GlassFill,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = if (granted) VestraColors.Accent else VestraColors.InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title and description
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = VestraColors.Ink,
                )
                if (entry.isMandatory) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Required",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = VestraColors.AccentSoft,
                    )
                }
            }
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(10.dp))

        // Checkmark (✓) or Cross (✗) status badge
        Surface(
            shape = RoundedCornerShape(20),
            color = if (granted) VestraColors.Accent.copy(alpha = 0.18f) else VestraColors.Danger.copy(alpha = 0.15f),
            modifier = Modifier
                .clip(RoundedCornerShape(20))
                .border(
                    1.dp,
                    if (granted) VestraColors.Accent else VestraColors.Danger.copy(alpha = 0.6f),
                    RoundedCornerShape(20),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = if (granted) "Granted" else "Not provided",
                    tint = if (granted) VestraColors.Accent else VestraColors.Danger,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = if (granted) "Allowed" else "Grant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (granted) VestraColors.Accent else VestraColors.Danger,
                )
            }
        }
    }
}
