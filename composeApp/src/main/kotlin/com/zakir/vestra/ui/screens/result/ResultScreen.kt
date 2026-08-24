package com.zakir.vestra.ui.screens.result

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zakir.vestra.data.LocalReportStore
import com.zakir.vestra.data.ReportReason
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.ui.components.GlassEmptyState
import com.zakir.vestra.ui.components.GlassImageFrame
import com.zakir.vestra.ui.components.GlassPill
import com.zakir.vestra.ui.components.GlassPrimaryButton
import com.zakir.vestra.ui.components.GlassSecondaryButton
import com.zakir.vestra.ui.components.GlassTopBar
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File

@Composable
fun ResultScreen(
    viewModel: com.zakir.vestra.ui.TryOnViewModel,
    wardrobe: WardrobeRepository,
    onNewLook: () -> Unit,
    onBackToStudio: () -> Unit,
    onOpenWardrobe: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val reportStore = remember { LocalReportStore(context) }
    val shoot by viewModel.shoot.collectAsState()
    val wardrobeEntries by wardrobe.entries.collectAsState()
    val results = shoot?.completed.orEmpty()
    var selectedShot by remember { mutableStateOf(0) }
    var showReport by remember { mutableStateOf(false) }
    val result = results.getOrNull(selectedShot.coerceIn(0, (results.size - 1).coerceAtLeast(0)))
    val wardrobeMatch = result?.let { shot ->
        wardrobeEntries.firstOrNull { it.imagePath == shot.imagePath }
    }

    if (showReport && result != null) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Report content") },
            text = {
                Column {
                    Text("Reports are stored on this device only (no paid services). Why are you reporting?")
                    Spacer(Modifier.height(8.dp))
                    ReportReason.entries.forEach { reason ->
                        TextButton(
                            onClick = {
                                reportStore.submit(result.imagePath, reason)
                                showReport = false
                                Toast.makeText(context, "Report saved locally", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text(reason.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReport = false }) { Text("Cancel") }
            },
        )
    }

    SpatialBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
        ) {
            GlassTopBar(
                title = "Your look",
                subtitle = if (results.size > 1) "${results.size} variations" else "Try-on result",
                navigation = {
                    IconButton(onClick = onBackToStudio) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to atelier")
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            val shotSources by viewModel.shots.collectAsState()
            val userPhoto = shotSources.filterIsInstance<com.zakir.vestra.shared.domain.PersonSource.UserPhoto>()
                .firstOrNull()

            if (result == null) {
                GlassEmptyState(
                    message = "No looks to show — start a new try-on.",
                    actionLabel = "Start try-on",
                    onAction = onNewLook,
                    modifier = Modifier.weight(1f),
                )
            } else {
                GlassImageFrame(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (userPhoto != null && results.size == 1) {
                            BeforeAfter(beforeModel = userPhoto.uri, afterModel = File(result.imagePath))
                        } else {
                            AsyncImage(
                                model = File(result.imagePath),
                                contentDescription = "Photoshoot shot ${selectedShot + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }

                if (results.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(results) { index, shot ->
                            AsyncImage(
                                model = File(shot.imagePath),
                                contentDescription = "Shot ${index + 1}",
                                modifier = Modifier
                                    .height(72.dp)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (index == selectedShot) 2.dp else 1.dp,
                                        color = if (index == selectedShot) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { selectedShot = index },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(text = "AI-generated", active = true)
                    GlassPill(text = "Provenance stamped", active = true, accent = VestraColors.Accent)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassSecondaryButton(
                        text = if (results.size > 1) "Save all to Photos" else "Save to Photos",
                        onClick = {
                            MediaExport.saveAllImages(context, results.map { File(it.imagePath) })
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GlassSecondaryButton(
                        text = "Share",
                        onClick = { MediaExport.share(context, File(result.imagePath), "Share look") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                GlassSecondaryButton(text = LookbookCopy.ACTION_REPORT, onClick = { showReport = true })
                if (wardrobeMatch != null) {
                    Spacer(Modifier.height(10.dp))
                    GlassSecondaryButton(
                        text = if (wardrobeMatch.favorited) "Remove favorite" else "Add favorite",
                        onClick = { wardrobe.toggleFavorite(wardrobeMatch.id) },
                    )
                }
                if (onOpenWardrobe != null) {
                    Spacer(Modifier.height(10.dp))
                    GlassSecondaryButton(
                        text = "Open Looks gallery",
                        onClick = onOpenWardrobe,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            GlassPrimaryButton(
                text = LookbookCopy.ACTION_START_TRY_ON,
                onClick = onNewLook,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun BeforeAfter(beforeModel: Any, afterModel: Any) {
    var fraction by remember { mutableStateOf(0.5f) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Before and after comparison. Drag horizontally to adjust." }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    fraction = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                }
            },
    ) {
        val widthPx = constraints.maxWidth
        AsyncImage(
            model = afterModel,
            contentDescription = "Generated try-on result",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        AsyncImage(
            model = beforeModel,
            contentDescription = "Original photo",
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
                },
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset { IntOffset((fraction * widthPx).toInt() - 1, 0) }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
        )
    }
}

