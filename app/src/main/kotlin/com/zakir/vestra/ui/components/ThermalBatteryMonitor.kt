package com.zakir.vestra.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.delay

/**
 * State holding battery and thermal telemetry for on-device AI generation.
 */
data class DeviceHealthState(
    val batteryPct: Int = 100,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val thermalStatus: Int = 0, // 0 = NONE, 1 = LIGHT, 2 = MODERATE, 3 = SEVERE, 4 = CRITICAL
    val estimatedTempCelsius: Float = 34f,
) {
    val isThrottled: Boolean
        get() = thermalStatus >= 2 || (batteryPct <= 15 && !isCharging) || isPowerSaveMode

    val warningMessage: String?
        get() = when {
            thermalStatus >= 3 -> "High device temperature. Generating in Turbo 4-Step Mode to prevent overheating."
            thermalStatus == 2 -> "Device is warm. Reducing latent diffusion steps to protect performance."
            batteryPct <= 15 && !isCharging -> "Low battery ($batteryPct%). Connect charger or switch to Turbo mode."
            isPowerSaveMode -> "Power saver active. Background tensor operations may be throttled."
            else -> null
        }
}

/**
 * Hook to observe on-device hardware telemetry (battery level, power mode, thermal status).
 */
@Composable
fun rememberDeviceHealth(): DeviceHealthState {
    val context = LocalContext.current
    var health by remember { mutableStateOf(DeviceHealthState()) }

    DisposableEffect(context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
                val isPowerSave = powerManager?.isPowerSaveMode == true
                val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager?.currentThermalStatus ?: 0
                } else 0

                val tempEst = when (thermal) {
                    0 -> 33f
                    1 -> 37f
                    2 -> 41f
                    3 -> 45f
                    else -> 48f
                }

                health = DeviceHealthState(
                    batteryPct = pct,
                    isCharging = isCharging,
                    isPowerSaveMode = isPowerSave,
                    thermalStatus = thermal,
                    estimatedTempCelsius = tempEst,
                )
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)

        onDispose {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {}
        }
    }

    return health
}

/**
 * Actionable UI indicator & banner warning the user about battery drain or thermal throttling
 * during on-device generative tasks.
 */
@Composable
fun ThermalBatteryWarningCard(
    health: DeviceHealthState,
    onEnableTurboMode: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var dismissed by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = health.isThrottled && !dismissed,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(18.dp)
        val warningBg = if (health.thermalStatus >= 2) {
            Color(0xFF2A1B14)
        } else {
            Color(0xFF231E12)
        }
        val borderTint = if (health.thermalStatus >= 2) {
            Color(0xFFFF7A45).copy(alpha = 0.5f)
        } else {
            Color(0xFFFFB800).copy(alpha = 0.5f)
        }
        val iconTint = if (health.thermalStatus >= 2) Color(0xFFFF7A45) else Color(0xFFFFB800)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(warningBg)
                .border(1.dp, borderTint, shape)
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (health.thermalStatus >= 2) Icons.Outlined.LocalFireDepartment else Icons.Outlined.Bolt,
                            contentDescription = "Device Health Warning",
                            tint = iconTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column {
                        Text(
                            text = if (health.thermalStatus >= 2) "THERMAL THROTTLING DETECTED" else "BATTERY PRESERVATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = iconTint,
                        )
                        Text(
                            text = "${health.batteryPct}% Battery · ${health.estimatedTempCelsius.toInt()}°C",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = VestraColors.IvoryMuted,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        dismissed = true
                        onDismiss?.invoke()
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = VestraColors.IvoryMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = health.warningMessage ?: "On-device AI uses significant compute.",
                style = MaterialTheme.typography.bodySmall,
                color = VestraColors.Ivory,
            )

            if (onEnableTurboMode != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(iconTint.copy(alpha = 0.2f))
                            .border(1.dp, iconTint.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable {
                                onEnableTurboMode()
                                dismissed = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "Switch to Turbo Mode (Fast)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = iconTint,
                            )
                        }
                    }
                }
            }
        }
    }
}
