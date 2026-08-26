package com.zakir.vestra.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.CloudModelProvider
import com.zakir.vestra.shared.cloud.CloudPlatform
import com.zakir.vestra.ui.theme.VestraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeModelsSheet(
    onDismiss: () -> Unit,
    onSelectModel: ((CloudModelProvider) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPlatform by remember { mutableStateOf<CloudPlatform?>(null) }
    var selectedCapability by remember { mutableStateOf<AiCapability?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val allFreeModels = remember {
        CloudModelCatalog.providers.filter { it.freeTier }
    }

    val filteredModels = remember(selectedPlatform, selectedCapability, searchQuery) {
        allFreeModels.filter { model ->
            val matchPlatform = selectedPlatform == null || model.platform == selectedPlatform
            val matchCap = selectedCapability == null || model.capability == selectedCapability
            val matchSearch = searchQuery.isBlank() ||
                model.displayName.contains(searchQuery, ignoreCase = true) ||
                model.description.contains(searchQuery, ignoreCase = true) ||
                model.id.contains(searchQuery, ignoreCase = true)
            matchPlatform && matchCap && matchSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VestraColors.Canvas,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(VestraColors.Accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = null,
                                tint = VestraColors.Accent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Available Free Cloud Models",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = VestraColors.Ink,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${allFreeModels.size} curated 100% free community & inference models",
                        style = MaterialTheme.typography.bodySmall,
                        color = VestraColors.InkMuted,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = VestraColors.InkMuted)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search models by name, architecture, license…", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = VestraColors.InkMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = VestraColors.InkMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VestraColors.Accent,
                    unfocusedBorderColor = VestraColors.GlassBorder,
                    focusedContainerColor = VestraColors.GlassFill,
                    unfocusedContainerColor = VestraColors.GlassFill,
                ),
            )

            Spacer(Modifier.height(12.dp))

            // Filter Chips (Platforms)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    FilterChip(
                        label = "All Hosts",
                        selected = selectedPlatform == null,
                        onClick = { selectedPlatform = null },
                    )
                }
                item {
                    FilterChip(
                        label = "Groq (Fast)",
                        selected = selectedPlatform == CloudPlatform.GROQ,
                        onClick = { selectedPlatform = CloudPlatform.GROQ },
                    )
                }
                item {
                    FilterChip(
                        label = "Hugging Face Spaces",
                        selected = selectedPlatform == CloudPlatform.HF_SPACE,
                        onClick = { selectedPlatform = CloudPlatform.HF_SPACE },
                    )
                }
                item {
                    FilterChip(
                        label = "Hugging Face Inference",
                        selected = selectedPlatform == CloudPlatform.HF_INFERENCE,
                        onClick = { selectedPlatform = CloudPlatform.HF_INFERENCE },
                    )
                }
                item {
                    FilterChip(
                        label = "OpenRouter Free",
                        selected = selectedPlatform == CloudPlatform.OPENROUTER,
                        onClick = { selectedPlatform = CloudPlatform.OPENROUTER },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Model List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (filteredModels.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No free models found matching filter criteria",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VestraColors.InkMuted,
                            )
                        }
                    }
                } else {
                    items(filteredModels, key = { it.id }) { model ->
                        FreeModelCard(
                            model = model,
                            onClick = {
                                onSelectModel?.invoke(model)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) VestraColors.Accent.copy(alpha = 0.2f) else VestraColors.GlassFill,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                1.dp,
                if (selected) VestraColors.Accent else VestraColors.GlassBorder,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp,
            ),
            color = if (selected) VestraColors.Accent else VestraColors.InkMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FreeModelCard(
    model: CloudModelProvider,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VestraColors.SurfaceRaised)
            .border(1.dp, VestraColors.GlassBorder.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = VestraColors.Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VestraColors.Accent.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = model.capability.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                            color = VestraColors.Accent,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Platform tag
            Text(
                text = when (model.platform) {
                    CloudPlatform.GROQ -> "⚡ Groq LPUs"
                    CloudPlatform.HF_SPACE -> "🤗 HF Space (ZeroGPU)"
                    CloudPlatform.HF_INFERENCE -> "🤗 HF Inference"
                    CloudPlatform.OPENROUTER -> "🌐 OpenRouter :free"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = VestraColors.InkMuted,
            )

            Text("·", style = MaterialTheme.typography.labelSmall, color = VestraColors.InkMuted)

            // Key requirement
            Text(
                text = if (model.requiresApiKey) "Key Required" else "No Key Needed",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = if (model.requiresApiKey) VestraColors.AccentSoft else VestraColors.Accent,
                ),
            )

            Text("·", style = MaterialTheme.typography.labelSmall, color = VestraColors.InkMuted)

            // License tag
            Text(
                text = model.license,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = VestraColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
