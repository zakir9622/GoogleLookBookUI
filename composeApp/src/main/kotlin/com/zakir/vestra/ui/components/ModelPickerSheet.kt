package com.zakir.vestra.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.cloud.CloudModelContracts
import com.zakir.vestra.shared.cloud.CloudModelProvider
import com.zakir.vestra.shared.cloud.CloudPlatform
import com.zakir.vestra.shared.cloud.ModelHealthTracker
import com.zakir.vestra.shared.cloud.ModelSupportLevel
import com.zakir.vestra.shared.quality.QualityRating
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.VestraColors

data class OnDevicePickerEntry(
    val id: String,
    val displayName: String,
    val detail: String,
    val ready: Boolean,
    /** Short status when not ready — e.g. download vs coming soon. */
    val statusLabel: String = if (ready) "Ready offline" else "Download in Settings",
)

/**
 * In-composer searchable model list — chat-bar model pill opens this sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    title: String = "Available models",
    models: List<CloudModelProvider>,
    selectedId: String,
    onSelect: (CloudModelProvider) -> Unit,
    onDismiss: () -> Unit,
    onDeviceEntries: List<OnDevicePickerEntry> = emptyList(),
    onSelectDevice: ((OnDevicePickerEntry) -> Unit)? = null,
    health: ModelHealthTracker? = null,
) {
    var query by remember { mutableStateOf("") }
    val selectable = remember(models) {
        models.filter { CloudModelContracts.forProvider(it).support != ModelSupportLevel.UNSUPPORTED }
    }
    val filtered = remember(selectable, query, health) {
        val q = query.trim().lowercase()
        val list = if (q.isEmpty()) {
            selectable
        } else {
            selectable.filter {
                it.displayName.lowercase().contains(q) ||
                    it.id.lowercase().contains(q) ||
                    it.platform.name.lowercase().contains(q) ||
                    (it.endpoint?.lowercase()?.contains(q) == true)
            }
        }
        list.sortedWith(
            compareByDescending<CloudModelProvider> {
                when (health?.effectiveSupport(it) ?: CloudModelContracts.forProvider(it).support) {
                    ModelSupportLevel.READY -> 3
                    ModelSupportLevel.DEGRADED -> 2
                    ModelSupportLevel.UNSUPPORTED -> 0
                }
            }.thenByDescending { it.qualityScore }
                .thenBy { it.displayName.lowercase() },
        )
    }
    val grouped = remember(filtered) {
        val huggingFace = filtered.filter {
            it.platform == CloudPlatform.HF_SPACE || it.platform == CloudPlatform.HF_INFERENCE
        }
        val groq = filtered.filter { it.platform == CloudPlatform.GROQ }
        val openRouter = filtered.filter { it.platform == CloudPlatform.OPENROUTER }
        val covered = (huggingFace + groq + openRouter).map { it.id }.toSet()
        val other = filtered.filter { it.id !in covered }
        buildList {
            if (huggingFace.isNotEmpty()) add("Hugging Face" to huggingFace)
            if (groq.isNotEmpty()) add("Groq" to groq)
            if (openRouter.isNotEmpty()) add("OpenRouter" to openRouter)
            if (other.isNotEmpty()) add("Other" to other)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VestraColors.SurfaceRaised,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = VestraColors.InkMuted,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = VestraColors.Accent)
                },
                placeholder = { Text("Search by name…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VestraColors.Accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = VestraColors.GlassBorder,
                    focusedContainerColor = VestraColors.GlassFill,
                    unfocusedContainerColor = VestraColors.GlassFill,
                ),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (query.isNotBlank()) {
                    items(filtered, key = { it.id }) { model ->
                        ModelPickerRow(model, selectedId, onSelect, onDismiss, health)
                    }
                } else {
                    if (onDeviceEntries.isNotEmpty()) {
                        item(key = "header-ondevice") {
                            Text(
                                "ON-DEVICE",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.Accent,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(onDeviceEntries, key = { "local-${it.id}" }) { entry ->
                            OnDevicePickerRow(
                                entry = entry,
                                selected = entry.id == selectedId,
                                onSelect = onSelectDevice,
                                onDismiss = onDismiss,
                            )
                        }
                    }
                    grouped.forEach { (section, models) ->
                        item(key = "header-$section") {
                            Text(
                                section.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.Accent,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(models, key = { it.id }) { model ->
                            ModelPickerRow(model, selectedId, onSelect, onDismiss, health)
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No models match “$query”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VestraColors.InkMuted,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnDevicePickerRow(
    entry: OnDevicePickerEntry,
    selected: Boolean,
    onSelect: ((OnDevicePickerEntry) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val enabled = onSelect != null && entry.ready
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(TestTags.modelPickerRow(entry.id))
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) VestraColors.Accent.copy(alpha = 0.14f) else VestraColors.GlassFill,
            )
            .border(
                1.dp,
                if (selected) VestraColors.Accent.copy(alpha = 0.55f) else VestraColors.GlassBorder,
                RoundedCornerShape(16.dp),
            )
            .then(
                if (enabled) {
                    Modifier.clickable {
                        onSelect?.invoke(entry)
                        onDismiss()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(VestraColors.Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (entry.ready) VestraColors.Accent else VestraColors.InkMuted),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (entry.ready) VestraColors.Ink else VestraColors.InkMuted,
            )
            Text(
                // `detail` is the catalog's testing note and carries "Download <pack> in Model
                // packs…". Appending it to an already-ready row produced the contradictory
                // "Ready offline · Download local-sdturbo-v1…", so it is only shown when the
                // pack really is missing and that instruction is the useful next step.
                if (entry.ready) entry.statusLabel else "${entry.statusLabel} · ${entry.detail}",
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModelPickerRow(
    model: CloudModelProvider,
    selectedId: String,
    onSelect: (CloudModelProvider) -> Unit,
    onDismiss: () -> Unit,
    health: ModelHealthTracker?,
) {
    val selected = model.id == selectedId
    val support = health?.effectiveSupport(model) ?: CloudModelContracts.forProvider(model).support
    val blocked = support == ModelSupportLevel.UNSUPPORTED
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(TestTags.modelPickerRow(model.id))
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) VestraColors.Accent.copy(alpha = 0.14f) else VestraColors.GlassFill,
            )
            .border(
                1.dp,
                if (selected) VestraColors.Accent.copy(alpha = 0.55f) else VestraColors.GlassBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable(enabled = !blocked) {
                onSelect(model)
                onDismiss()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(VestraColors.Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (support) {
                            ModelSupportLevel.READY -> VestraColors.Accent
                            ModelSupportLevel.DEGRADED -> VestraColors.AccentSoft
                            ModelSupportLevel.UNSUPPORTED -> VestraColors.InkMuted
                        },
                    ),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                model.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (blocked) VestraColors.InkMuted else VestraColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(QualityRating.label(model))
                    append(" · ")
                    append(CloudModelContracts.liveStatusLabel(model, health))
                    append(" · ")
                    append(model.platform.name.replace('_', ' ').lowercase())
                    if (blocked) append(" · not selectable")
                },
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Selected",
                tint = VestraColors.Accent,
            )
        } else if (!blocked) {
            Text(
                "Use",
                style = MaterialTheme.typography.labelMedium,
                color = VestraColors.Accent,
            )
        }
    }
}
