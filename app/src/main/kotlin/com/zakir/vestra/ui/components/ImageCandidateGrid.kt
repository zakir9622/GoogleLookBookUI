package com.zakir.vestra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.cloud.GenerationBatch
import com.zakir.vestra.shared.cloud.GenerationCandidate
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File

@Composable
fun ImageCandidateGrid(
    batch: GenerationBatch,
    selectedCandidateId: String?,
    onOpenCandidate: (GenerationCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        batch.candidates.chunked(2).forEachIndexed { rowIndex, rowCandidates ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowCandidates.forEach { candidate ->
                    CandidateTile(
                        candidate = candidate,
                        selected = candidate.id == selectedCandidateId,
                        onClick = { onOpenCandidate(candidate) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowCandidates.size == 1) Spacer(Modifier.weight(1f))
            }
            if (rowIndex < batch.candidates.chunked(2).lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CandidateTile(
    candidate: GenerationCandidate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(RoundedCornerShape(20.dp))
                .background(VestraColors.SurfaceRaised)
                .clickable(onClick = onClick),
        ) {
            ShimmerAsyncImage(
                model = File(candidate.path),
                contentDescription = "Candidate ${candidate.candidateIndex + 1}. Tap to open fullscreen.",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(VestraColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected candidate",
                        tint = VestraColors.Canvas,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = "Option ${candidate.candidateIndex + 1}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) VestraColors.Accent else VestraColors.Ink,
        )
        Text(
            text = candidate.providerId,
            style = MaterialTheme.typography.labelSmall,
            color = VestraColors.InkMuted,
            maxLines = 1,
        )
    }
}
