package com.zakir.vestra.ui.screens.person

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zakir.vestra.data.StudioModelRepository
import com.zakir.vestra.shared.domain.PersonSource
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.TryOnViewModel
import com.zakir.vestra.ui.components.GlassImageFrame
import com.zakir.vestra.ui.components.GlassTopBar
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.VestraColors

/**
 * The casting: pick which studio models (poses) wear the garment — each pick
 * is one shot in the photoshoot — or use your own photo (single shot, gated
 * by the likeness-consent acknowledgement; see docs/PLAY_COMPLIANCE.md).
 */
@Composable
fun PersonSourceScreen(
    viewModel: TryOnViewModel,
    appSettings: AppSettings,
    studioModels: StudioModelRepository,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val shots by viewModel.shots.collectAsState()
    val casting by viewModel.casting.collectAsState()
    val allModels = remember { studioModels.models() }
    val models = remember(casting, allModels) {
        val tags = casting.galleryTags()
        val filtered = allModels.filter { model ->
            model.tags.isEmpty() || model.tags.any { it.lowercase() in tags.map { t -> t.lowercase() } }
        }
        filtered.ifEmpty { allModels }
    }
    val consentAccepted by appSettings.likenessConsentAccepted.collectAsState()
    var showConsentDialog by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.setShots(listOf(PersonSource.UserPhoto(it.toString()))) }
    }

    fun requestUserPhoto() {
        if (consentAccepted) {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            showConsentDialog = true
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text("Using personal photos") },
            text = {
                Text(
                    "Only use photos of yourself, or of people who have given you " +
                        "permission. Creating images of someone without their consent may be unlawful.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appSettings.setLikenessConsentAccepted()
                        showConsentDialog = false
                        pickLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) { Text("I understand") }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) { Text("Cancel") }
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
                title = "Choose your model",
                subtitle = "Step 3 · Person",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
            Text(
                "Every model you cast becomes one shot in the photoshoot.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    viewModel.setShots(models.map { PersonSource.AiModel(it.id) })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Full shoot — cast every model")
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    val selected = shots.any { it is PersonSource.UserPhoto }
                    PersonCard(selected = selected, onClick = ::requestUserPhoto) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                Icons.Outlined.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Your photo", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Single shot",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(models, key = { it.id }) { model ->
                    val source = PersonSource.AiModel(model.id)
                    val isSelected = shots.contains(source)
                    PersonCard(
                        selected = isSelected,
                        onClick = { viewModel.toggleShot(source) },
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = model.coilModel,
                                contentDescription = model.displayName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNext,
                enabled = shots.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    when {
                        shots.size > 1 -> "Start the shoot — ${shots.size} shots"
                        else -> "Start the shoot"
                    },
                )
            }
        }
    }
}

@Composable
private fun PersonCard(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    GlassImageFrame(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        content()
    }
}
