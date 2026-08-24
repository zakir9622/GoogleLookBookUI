package com.zakir.vestra.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zakir.vestra.ui.TestTags

@Composable
fun LiveGenConsole(lines: List<String>, generationStartedAtMs: Long? = null) {
    if (lines.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    GlassCard(modifier = Modifier.testTag(TestTags.LIVE_CONSOLE)) {
        val header = if (generationStartedAtMs != null) {
            val elapsed = ((System.currentTimeMillis() - generationStartedAtMs) / 1_000L).coerceAtLeast(0L)
            "LIVE · ${elapsed}s"
        } else {
            "LIVE"
        }
        GlassSectionLabel(header)
        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) {
            scroll.animateScrollTo(scroll.maxValue)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .verticalScroll(scroll),
        ) {
            lines.takeLast(24).forEach { line ->
                Text(
                    "· $line",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
