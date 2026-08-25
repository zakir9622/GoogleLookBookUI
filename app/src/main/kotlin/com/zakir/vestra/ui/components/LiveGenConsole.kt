package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.VestraColors
import java.util.Locale
import kotlinx.coroutines.delay

private fun formatTimeDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) {
        String.format(Locale.US, "%02d:%02dm", mins, secs)
    } else {
        String.format(Locale.US, "%02ds", secs)
    }
}

@Composable
fun LiveGenConsole(
    lines: List<String>,
    generationStartedAtMs: Long? = null,
    deadlineEpochMs: Long? = null,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    defaultExpanded: Boolean = true,
) {
    if (lines.isEmpty()) return

    var tick by remember(generationStartedAtMs, deadlineEpochMs) { mutableIntStateOf(0) }
    LaunchedEffect(generationStartedAtMs, deadlineEpochMs) {
        while (true) {
            delay(1_000)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    tick

    var expanded by remember { mutableStateOf(defaultExpanded) }
    val elapsedSec = generationStartedAtMs?.let { ((System.currentTimeMillis() - it) / 1_000L).coerceAtLeast(0L) }
    val remSec = deadlineEpochMs?.let { ((it - System.currentTimeMillis()) / 1_000L).coerceAtLeast(0L) }

    val timerLabel = when {
        remSec != null && remSec > 0 -> "COUNTDOWN: ${formatTimeDuration(remSec)} left"
        elapsedSec != null -> "ELAPSED: ${formatTimeDuration(elapsedSec)}"
        else -> "LIVE"
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.LIVE_CONSOLE)
            .clip(shape)
            .background(VestraColors.SurfaceRaised)
            .border(1.dp, VestraColors.GlassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VestraColors.Accent),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = VestraColors.Accent,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "MODEL LOGS & TELEMETRY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = VestraColors.Ink,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timerLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = VestraColors.Accent,
                )
                if (collapsible) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse logs" else "Expand logs",
                        modifier = Modifier.size(16.dp),
                        tint = VestraColors.InkMuted,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            val scroll = rememberScrollState()
            LaunchedEffect(lines.size) {
                scroll.animateScrollTo(scroll.maxValue)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(max = 140.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                lines.takeLast(30).forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            "› ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = VestraColors.Accent,
                            ),
                        )
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = VestraColors.InkMuted,
                        )
                    }
                }
            }
        }
    }
}
