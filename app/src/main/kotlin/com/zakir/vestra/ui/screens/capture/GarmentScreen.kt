package com.zakir.vestra.ui.screens.capture

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.zakir.vestra.shared.domain.Backdrop
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.engine.lite.HumanParsing
import com.zakir.vestra.shared.engine.lite.LiteEngineIo
import com.zakir.vestra.ui.TryOnViewModel
import com.zakir.vestra.ui.components.AtelierFilterChip
import com.zakir.vestra.ui.components.GlassImageFrame
import com.zakir.vestra.ui.components.GlassTopBar
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.util.rememberCameraGatedAction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GarmentScreen(
    viewModel: TryOnViewModel,
    humanParsing: HumanParsing,
    liteEngineIo: LiteEngineIo,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val outfit by viewModel.outfit.collectAsState()
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPiece by remember { mutableIntStateOf(0) }
    var rotating by remember { mutableStateOf(false) }
    var wornPhotoWarning by remember { mutableStateOf<String?>(null) }

    fun validateGarmentUri(uri: String, onAccept: () -> Unit) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { liteEngineIo.loadBitmap(uri) }
            if (bitmap == null) {
                Toast.makeText(context, "Couldn't read that image — try another photo", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val worn = withContext(Dispatchers.Default) {
                runCatching { humanParsing.looksLikeWornPhoto(bitmap) }.getOrDefault(false)
            }
            if (worn) {
                wornPhotoWarning = uri
            } else {
                onAccept()
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            validateGarmentUri(it.toString()) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addGarment(it.toString())
                selectedPiece = outfit.size
            }
        }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        if (saved) {
            pendingCaptureUri?.let { uri ->
                validateGarmentUri(uri.toString()) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.addGarment(uri.toString())
                    selectedPiece = outfit.size
                }
            }
        }
        pendingCaptureUri = null
    }

    fun openCameraCapture() {
        val captures = File(context.filesDir, "captures").apply { mkdirs() }
        val file = File(captures, "garment_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureUri = uri
        captureLauncher.launch(uri)
    }

    val launchCamera = rememberCameraGatedAction(
        onGranted = { openCameraCapture() },
        onDenied = {
            Toast.makeText(context, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show()
        },
    )

    fun pickFromGallery() =
        pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val active = outfit.getOrNull(selectedPiece.coerceIn(0, (outfit.size - 1).coerceAtLeast(0)))

    wornPhotoWarning?.let { warnedUri ->
        AlertDialog(
            onDismissRequest = { wornPhotoWarning = null },
            title = { Text("Looks like a worn photo") },
            text = {
                Text(
                    "This image shows a person wearing clothes. For try-on, use a flat lay, " +
                        "hanger, or mannequin shot of the garment alone — not a model wearing it.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.addGarment(warnedUri)
                        selectedPiece = outfit.size
                        wornPhotoWarning = null
                    },
                ) { Text("Use anyway") }
            },
            dismissButton = {
                TextButton(onClick = { wornPhotoWarning = null }) { Text("Pick another") }
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
                    title = if (outfit.size > 1) "Your outfit" else "Your garment",
                    subtitle = "Step 1 · Capture",
                    navigation = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )

                Spacer(Modifier.height(12.dp))

                GlassImageFrame(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (active != null) {
                        AsyncImage(
                            model = active.uri,
                            contentDescription = "Selected garment",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        IconButton(
                            onClick = {
                                if (rotating) return@IconButton
                                rotating = true
                                scope.launch {
                                    val rotated = withContext(Dispatchers.IO) {
                                        com.zakir.vestra.data.ImageRotator.rotate90(context, active.uri)
                                    }
                                    rotated?.let { viewModel.setGarmentUri(selectedPiece, it) }
                                    rotating = false
                                }
                            },
                            enabled = !rotating,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        ) {
                            Icon(Icons.Outlined.Rotate90DegreesCw, contentDescription = "Rotate 90°")
                        }
                    } else {
                        Text(
                            "Add abaya, hijab, niqab, kurta, or shalwar kameez.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    }
                }

                Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(outfit) { index, piece ->
                    Box {
                        AsyncImage(
                            model = piece.uri,
                            contentDescription = "Piece ${index + 1}",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (index == selectedPiece) 2.dp else 1.dp,
                                    color = if (index == selectedPiece) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { selectedPiece = index },
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = {
                                viewModel.removeGarment(index)
                                selectedPiece = 0
                            },
                            modifier = Modifier.size(22.dp).align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove piece")
                        }
                    }
                }
                item {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable(onClick = ::pickFromGallery),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add a piece")
                    }
                }
            }

            if (active != null) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AtelierFilterChip(
                            selected = active.category == null,
                            onClick = { viewModel.setGarmentCategory(selectedPiece, null) },
                            label = { Text("Auto") },
                        )
                    }
                    items(categoryChips) { (category, label) ->
                        AtelierFilterChip(
                            selected = active.category == category,
                            onClick = { viewModel.setGarmentCategory(selectedPiece, category) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("BACKDROP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            val selectedBackdrop by viewModel.backdrop.collectAsState()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Backdrop.entries.size) { index ->
                    val backdrop = Backdrop.entries[index]
                    AtelierFilterChip(
                        selected = selectedBackdrop == backdrop,
                        onClick = { viewModel.setBackdrop(backdrop) },
                        label = { Text(backdrop.displayName) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = ::pickFromGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Pick from gallery")
                    Spacer(Modifier.width(4.dp))
                    Text(if (outfit.isEmpty()) "Gallery" else "Add piece")
                }
                OutlinedButton(onClick = launchCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "Capture with camera")
                    Spacer(Modifier.width(4.dp))
                    Text("Camera")
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNext,
                enabled = outfit.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text("Set casting parameters")
            }
        }
    }
}

private val categoryChips = listOf(
    GarmentCategory.ABAYA to "Abaya",
    GarmentCategory.JILBAB to "Jilbab",
    GarmentCategory.KAFTAN to "Kaftan",
    GarmentCategory.HIJAB to "Hijab",
    GarmentCategory.NIQAB to "Niqab",
    GarmentCategory.DUPATTA to "Dupatta",
    GarmentCategory.HEADSCARF to "Headscarf",
    GarmentCategory.SHALWAR_KAMEEZ to "Shalwar kameez",
    GarmentCategory.KURTA to "Kurta",
    GarmentCategory.LEHENGA to "Lehenga",
    GarmentCategory.DRESS to "Dress",
    GarmentCategory.UPPER_BODY to "Upper body",
    GarmentCategory.LOWER_BODY to "Trousers",
    GarmentCategory.FULL_COVERAGE to "Full coverage",
)
