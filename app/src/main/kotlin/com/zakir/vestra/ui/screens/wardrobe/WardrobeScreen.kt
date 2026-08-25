package com.zakir.vestra.ui.screens.wardrobe

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.media.MediaThumb
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.wardrobe.WardrobeEntry
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.ui.components.AtelierFilterChip
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassEmptyState
import com.zakir.vestra.ui.components.GlassScreen
import com.zakir.vestra.ui.components.GlassSecondaryButton
import com.zakir.vestra.ui.TestTags
import java.io.File

@Composable
fun WardrobeScreen(
    wardrobe: WardrobeRepository,
    onBack: () -> Unit,
    onStartTryOn: (() -> Unit)? = null,
) {
    val entries by wardrobe.entries.collectAsState()
    val context = LocalContext.current
    var favoritesOnly by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WardrobeEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<WardrobeEntry?>(null) }
    val visible = remember(entries, favoritesOnly) {
        if (favoritesOnly) entries.filter { it.favorited } else entries
    }

    detail?.let { entry ->
        LookDetailDialog(
            entry = entry,
            allEntries = entries,
            onSelectEntry = { detail = it },
            onDismiss = { detail = null },
            onFavorite = {
                wardrobe.toggleFavorite(entry.id)
                detail = wardrobe.entries.value.firstOrNull { it.id == entry.id }
            },
            onShare = {
                val file = File(entry.imagePath)
                if (!file.exists()) {
                    Toast.makeText(context, "File missing", Toast.LENGTH_SHORT).show()
                } else if (file.extension.lowercase() in setOf("mp4", "webm")) {
                    MediaExport.share(context, file, LookbookCopy.ACTION_SHARE)
                } else {
                    MediaExport.share(context, file, LookbookCopy.ACTION_SHARE)
                }
            },
            onSave = {
                val file = File(entry.imagePath)
                if (file.extension.lowercase() in setOf("mp4", "webm")) {
                    MediaExport.saveVideoToGallery(context, file)
                } else {
                    MediaExport.saveImageToGallery(context, file)
                }
            },
            onDelete = {
                detail = null
                pendingDelete = entry
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete look?") },
            text = { Text("This removes the look from your gallery on this device. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = File(entry.imagePath)
                        runCatching { if (file.exists()) file.delete() }
                        wardrobe.remove(entry.id)
                        pendingDelete = null
                        Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    GlassScreen(
        title = LookbookCopy.STUDIO_WARDROBE,
        subtitle = "Looks · listings · assets",
        onBack = onBack,
        scrollable = false,
    ) {
        if (entries.isEmpty()) {
            GlassEmptyState(
                message = LookbookCopy.EMPTY_GALLERY,
                actionLabel = onStartTryOn?.let { LookbookCopy.ACTION_START_TRY_ON },
                onAction = onStartTryOn,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AtelierFilterChip(
                    selected = !favoritesOnly,
                    onClick = { favoritesOnly = false },
                    label = { Text("All (${entries.size})") },
                )
                AtelierFilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = true },
                    label = { Text("Favorites (${entries.count { it.favorited }})") },
                )
            }
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) {
                GlassEmptyState(
                    message = LookbookCopy.EMPTY_FAVORITES,
                    actionLabel = LookbookCopy.ACTION_SHOW_ALL_LOOKS,
                    onAction = { favoritesOnly = false },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.id }) { entry ->
                        val file = File(entry.imagePath)
                        val isVideo = file.extension.lowercase() in setOf("mp4", "webm")
                        GlassCard {
                            MediaThumb(
                                file = file,
                                contentDescription = if (isVideo) {
                                    "Video look ${entry.personLabel}. Opens details."
                                } else {
                                    "Generated look ${entry.personLabel}. Opens details."
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (!file.exists()) {
                                            Toast.makeText(context, "File missing", Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
                                        detail = entry
                                    },
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${entry.personLabel} · ${entry.tier.name.lowercase()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassSecondaryButton(
                                    text = if (entry.favorited) "★ Fav" else "☆ Fav",
                                    onClick = { wardrobe.toggleFavorite(entry.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = if (entry.favorited) {
                                                "Remove from favorites"
                                            } else {
                                                "Add to favorites"
                                            }
                                        },
                                )
                                GlassSecondaryButton(
                                    text = LookbookCopy.ACTION_SHARE,
                                    onClick = {
                                        if (!file.exists()) {
                                            Toast.makeText(context, "File missing", Toast.LENGTH_SHORT).show()
                                        } else {
                                            MediaExport.share(context, file, LookbookCopy.ACTION_SHARE)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassSecondaryButton(
                                    text = if (isVideo) "Save clip" else "Save to Photos",
                                    onClick = {
                                        if (isVideo) {
                                            MediaExport.saveVideoToGallery(context, file)
                                        } else {
                                            MediaExport.saveImageToGallery(context, file)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GlassSecondaryButton(
                                    text = "Delete",
                                    onClick = { pendingDelete = entry },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Walks `parentGenerationId` pointers back to the first attempt — oldest first. */
private fun ancestorChain(entry: WardrobeEntry, all: List<WardrobeEntry>): List<WardrobeEntry> {
    val byId = all.associateBy { it.id }
    val chain = mutableListOf<WardrobeEntry>()
    var parentId = entry.parentGenerationId
    var guard = 0
    while (parentId != null && guard < 50) {
        val parent = byId[parentId] ?: break
        chain += parent
        parentId = parent.parentGenerationId
        guard++
    }
    return chain.asReversed()
}

@Composable
private fun LookDetailDialog(
    entry: WardrobeEntry,
    allEntries: List<WardrobeEntry> = emptyList(),
    onSelectEntry: (WardrobeEntry) -> Unit = {},
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val file = File(entry.imagePath)
    val isVideo = file.extension.lowercase() in setOf("mp4", "webm")
    val history = remember(entry, allEntries) { ancestorChain(entry, allEntries) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.personLabel.ifBlank { "Look" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MediaThumb(
                    file = file,
                    contentDescription = "Look preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${entry.tier.name.lowercase()} · ${if (isVideo) "video clip" else "still"}" +
                        if (history.isNotEmpty()) " · version ${history.size + 1}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "HISTORY · ${history.size} earlier attempt" + (if (history.size == 1) "" else "s"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    history.forEach { ancestor ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.wardrobeHistoryRow(ancestor.id))
                                .clickable { onSelectEntry(ancestor) }
                                .padding(vertical = 4.dp),
                        ) {
                            MediaThumb(
                                file = File(ancestor.imagePath),
                                contentDescription = "Earlier attempt",
                                modifier = Modifier
                                    .height(48.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.height(48.dp)) {
                                Text(
                                    "Tap to view",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassSecondaryButton(
                    text = if (entry.favorited) "Remove favorite" else "Add favorite",
                    onClick = onFavorite,
                )
                Spacer(Modifier.height(8.dp))
                GlassSecondaryButton(text = LookbookCopy.ACTION_SHARE, onClick = onShare)
                Spacer(Modifier.height(8.dp))
                GlassSecondaryButton(
                    text = if (isVideo) "Save clip to Gallery" else "Save to Photos",
                    onClick = onSave,
                )
                Spacer(Modifier.height(8.dp))
                GlassSecondaryButton(text = "Delete look", onClick = onDelete)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
