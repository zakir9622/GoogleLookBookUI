package com.zakir.vestra.ui.screens.wardrobe

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File

@Composable
fun WardrobeScreen(
    wardrobe: WardrobeRepository,
    onBack: (() -> Unit)? = null,
    onStartTryOn: (() -> Unit)? = null,
    onReusePrompt: ((String) -> Unit)? = null,
) {
    val entries by wardrobe.entries.collectAsState()
    val context = LocalContext.current
    var favoritesOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<WardrobeEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<WardrobeEntry?>(null) }
    val visible = remember(entries, favoritesOnly, query) {
        val base = if (favoritesOnly) entries.filter { it.favorited } else entries
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) base else base.filter { entry ->
            listOfNotNull(
                entry.personLabel,
                entry.garmentUri,
                entry.prompt,
                entry.providerId,
                entry.batchId,
                entry.candidateId,
            ).any { it.lowercase().contains(normalized) }
        }
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
            onReusePrompt = onReusePrompt,
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wardrobe_search"),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search saved creations",
                        tint = VestraColors.Accent,
                    )
                },
                placeholder = { Text("Search prompts, styles, people, or engines") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VestraColors.Accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = VestraColors.GlassBorder,
                    focusedContainerColor = VestraColors.GlassFill,
                    unfocusedContainerColor = VestraColors.GlassFill,
                ),
                shape = RoundedCornerShape(RadiusTokens.md),
            )
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(visible, key = { it.id }) { entry ->
                        val file = File(entry.imagePath)
                        val isVideo = file.extension.lowercase() in setOf("mp4", "webm")
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RadiusTokens.lg))
                                .background(VestraColors.SurfaceRaised)
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            VestraColors.Accent.copy(alpha = 0.35f),
                                            VestraColors.GlassBorder.copy(alpha = 0.2f),
                                        ),
                                    ),
                                    RoundedCornerShape(RadiusTokens.lg),
                                )
                                .clickable {
                                    if (!file.exists()) {
                                        Toast.makeText(context, "File missing", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    detail = entry
                                },
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.78f),
                                ) {
                                    MediaThumb(
                                        file = file,
                                        contentDescription = if (isVideo) {
                                            "Video look ${entry.personLabel}. Opens details."
                                        } else {
                                            "Generated look ${entry.personLabel}. Opens details."
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )

                                    // Top Badges
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(VestraColors.AtelierCanvas.copy(alpha = 0.75f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = if (isVideo) "VIDEO" else entry.tier.name,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = VestraColors.Accent,
                                            )
                                        }

                                        // Floating Favorite Icon
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(VestraColors.AtelierCanvas.copy(alpha = 0.75f))
                                                .clickable { wardrobe.toggleFavorite(entry.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = if (entry.favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                contentDescription = if (entry.favorited) "Remove favorite" else "Add favorite",
                                                tint = if (entry.favorited) VestraColors.Accent else Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }

                                // Card Footer Info
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = entry.personLabel.ifBlank { "Lookbook Creation" },
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                        ),
                                        color = VestraColors.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Tap to view & export",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = VestraColors.InkMuted,
                                    )
                                }
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
    onReusePrompt: ((String) -> Unit)? = null,
) {
    val file = File(entry.imagePath)
    val isVideo = file.extension.lowercase() in setOf("mp4", "webm")
    val history = remember(entry, allEntries) { ancestorChain(entry, allEntries) }
    val batchCandidates = remember(entry, allEntries) {
        entry.batchId?.let { batchId ->
            allEntries
                .filter { it.batchId == batchId }
                .sortedBy { it.candidateIndex ?: Int.MAX_VALUE }
        }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    entry.personLabel.ifBlank { "Look" },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
        },
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
                    buildString {
                        append(entry.tier.name.lowercase())
                        append(" · ")
                        append(if (isVideo) "video clip" else "still")
                        entry.candidateIndex?.let { index ->
                            append(" · option ${index + 1}/${entry.candidateCount ?: batchCandidates.size.coerceAtLeast(1)}")
                        }
                        if (history.isNotEmpty()) append(" · version ${history.size + 1}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.providerId?.let { provider ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append(provider)
                            entry.seed?.let { append(" · seed $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                entry.prompt?.takeIf { it.isNotBlank() }?.let { savedPrompt ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        savedPrompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onReusePrompt != null) {
                        Spacer(Modifier.height(8.dp))
                        GlassSecondaryButton(
                            text = "Reuse creative direction",
                            onClick = { onReusePrompt(savedPrompt) },
                        )
                    }
                }
                if (batchCandidates.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "BATCH · ${batchCandidates.size} OPTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    batchCandidates.forEach { sibling ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectEntry(sibling) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            MediaThumb(
                                file = File(sibling.imagePath),
                                contentDescription = "Batch option ${(sibling.candidateIndex ?: 0) + 1}",
                                modifier = Modifier
                                    .height(48.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (sibling.id == entry.id) "Selected option" else "Option ${(sibling.candidateIndex ?: 0) + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sibling.id == entry.id) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
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
