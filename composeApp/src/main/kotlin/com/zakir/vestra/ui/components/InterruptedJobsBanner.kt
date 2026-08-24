package com.zakir.vestra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.jobs.LocalJobStore
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Surfaces local generations that were still [com.zakir.vestra.shared.jobs.LocalJobStatus.RUNNING]
 * the last time the app ran — the process was very likely reclaimed mid-generation (a Bonsai
 * Image 4B run is several minutes on CPU; backgrounding the app during one used to lose all
 * trace that anything had been asked for). This does not resume the generation — ONNX/LiteRT
 * sessions aren't checkpointable mid-run — it just tells the user what didn't finish, instead of
 * silently vanishing.
 */
@Composable
fun InterruptedJobsBanner(localJobStore: LocalJobStore?) {
    if (localJobStore == null) return
    val jobs by localJobStore.jobs.collectAsState()
    val interrupted = jobs.filter {
        it.status == com.zakir.vestra.shared.jobs.LocalJobStatus.RUNNING ||
            it.status == com.zakir.vestra.shared.jobs.LocalJobStatus.QUEUED
    }
    if (interrupted.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag(TestTags.INTERRUPTED_JOBS_BANNER)) {
        interrupted.forEach { job ->
            GlassCard {
                Text(
                    "Interrupted: ${job.capability.lowercase().replace('_', ' ')}",
                    style = MaterialTheme.typography.titleSmall,
                    color = VestraColors.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "\"${job.promptPreview}\" didn't finish — the app was likely closed mid-generation. " +
                        "Try again from the studio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    GlassSecondaryButton(
                        text = "Dismiss",
                        onClick = { localJobStore.dismiss(job.id) },
                        modifier = Modifier.testTag(TestTags.interruptedJobDismiss(job.id)),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
