package com.zakir.vestra.ui.screens.studio

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.components.ShimmerAsyncImage
import com.zakir.vestra.data.SampleGarment
import com.zakir.vestra.data.SampleGarmentCatalog
import com.zakir.vestra.data.StudioModel
import com.zakir.vestra.data.StudioModelRepository
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.domain.Backdrop
import com.zakir.vestra.shared.domain.BodyType
import com.zakir.vestra.shared.domain.CastingProfile
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.Ethnicity
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.GarmentImage
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.HairCoverage
import com.zakir.vestra.shared.domain.PersonSource
import com.zakir.vestra.shared.domain.ShootState
import com.zakir.vestra.shared.domain.SkinTone
import com.zakir.vestra.shared.domain.TryOnError
import com.zakir.vestra.shared.domain.TryOnResult
import com.zakir.vestra.shared.engine.lite.HumanParsing
import com.zakir.vestra.shared.engine.lite.LiteEngineIo
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.wardrobe.WardrobeEntry
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.TryOnViewModel
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.theme.VestraShapes
import com.zakir.vestra.ui.theme.VestraSpacing
import com.zakir.vestra.ui.util.rememberCameraGatedAction
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified Single-Page Virtual Fitting Studio.
 * Seamlessly integrates Garment Selection, Studio/Custom Person Selection, Multi-Cloud Model Routing,
 * Precision Parameter Tuning with Sensible Defaults, In-Place Generation, Interactive Split Comparison,
 * and Instant Wardrobe Persistence in a single cohesive screen.
 */
@Composable
fun TryOnStudioScreen(
    viewModel: TryOnViewModel,
    appSettings: AppSettings,
    wardrobe: WardrobeRepository,
    studioModels: StudioModelRepository,
    humanParsing: HumanParsing,
    liteEngineIo: LiteEngineIo,
    freeCloudDiscovery: FreeCloudDiscovery?,
    packManager: ModelPackManager,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPacks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // State collections
    val outfit by viewModel.outfit.collectAsState()
    val shots by viewModel.shots.collectAsState()
    val casting by viewModel.casting.collectAsState()
    val backdrop by viewModel.backdrop.collectAsState()
    val shootState by viewModel.shoot.collectAsState()
    val liveLogs by viewModel.liveLog.collectAsState()

    // Parameter state flows
    val steps by viewModel.steps.collectAsState()
    val cfg by viewModel.cfg.collectAsState()
    val seed by viewModel.seed.collectAsState()
    val garmentDesc by viewModel.garmentDesc.collectAsState()
    val autoMask by viewModel.autoMask.collectAsState()
    val autoCrop by viewModel.autoCrop.collectAsState()
    val customTier by viewModel.customEngineTier.collectAsState()

    // AppSettings states
    val activeCloudProviderId by appSettings.cloudProviderId.collectAsState()
    val configuredEngineTier by appSettings.engineTier.collectAsState()
    val consentGiven by appSettings.likenessConsentAccepted.collectAsState()
    val wardrobeEntries by wardrobe.entries.collectAsState()
    val recentLooks = remember(wardrobeEntries) { wardrobeEntries.take(8) }

    val activeTier = customTier ?: configuredEngineTier
    val availableStudioModels = remember { studioModels.models() }

    // Local UI state
    var showAdvancedParams by remember { mutableStateOf(false) }
    var showCastingStudio by remember { mutableStateOf(false) }
    var showLikenessDialog by remember { mutableStateOf(false) }
    var pendingLikenessAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var rotatingGarment by remember { mutableStateOf(false) }
    var activeModelTab by remember { mutableStateOf(0) } // 0: Studio Models, 1: My Photo
    var savedSuccessId by remember { mutableStateOf<String?>(null) }

    // Seed text field state
    var seedText by remember(seed) { mutableStateOf(seed?.toString().orEmpty()) }

    // Initialize with default Studio Model & Sample Garment if empty
    LaunchedEffect(Unit) {
        if (shots.isEmpty()) {
            val defaultModel = availableStudioModels.firstOrNull()
            if (defaultModel != null) {
                viewModel.setSinglePerson(PersonSource.AiModel(modelId = defaultModel.id))
            }
        }
        if (outfit.isEmpty()) {
            val defaultGarment = SampleGarmentCatalog.items.first()
            val garmentUri = SampleGarmentCatalog.resolveUri(context, defaultGarment)
            viewModel.setSingleGarment(garmentUri, defaultGarment.category)
            viewModel.setGarmentDesc(defaultGarment.promptHint)
        }
    }

    // Camera and Photo Pickers
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            pendingCaptureUri?.let { uri ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.setSingleGarment(uri.toString(), GarmentCategory.ABAYA)
            }
        }
        pendingCaptureUri = null
    }

    val photoPersonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            pendingCaptureUri?.let { uri ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.setSinglePerson(PersonSource.UserPhoto(uri.toString()))
            }
        }
        pendingCaptureUri = null
    }

    val pickGarmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.setSingleGarment(it.toString(), GarmentCategory.ABAYA)
        }
    }

    val pickPersonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.setSinglePerson(PersonSource.UserPhoto(it.toString()))
        }
    }

    val launchGarmentCamera = rememberCameraGatedAction(
        onGranted = {
            val captures = File(context.filesDir, "captures").apply { mkdirs() }
            val file = File(captures, "garment_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCaptureUri = uri
            captureLauncher.launch(uri)
        },
        onDenied = {
            Toast.makeText(context, "Camera permission needed to snap garment", Toast.LENGTH_SHORT).show()
        },
    )

    val launchPersonCamera = rememberCameraGatedAction(
        onGranted = {
            val captures = File(context.filesDir, "captures").apply { mkdirs() }
            val file = File(captures, "person_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCaptureUri = uri
            photoPersonLauncher.launch(uri)
        },
        onDenied = {
            Toast.makeText(context, "Camera permission needed to take your photo", Toast.LENGTH_SHORT).show()
        },
    )

    // Likeness consent handler
    fun gatePersonPhoto(action: () -> Unit) {
        if (consentGiven) {
            action()
        } else {
            pendingLikenessAction = action
            showLikenessDialog = true
        }
    }

    // Consent Dialog
    if (showLikenessDialog) {
        AlertDialog(
            onDismissRequest = {
                showLikenessDialog = false
                pendingLikenessAction = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Likeness & Privacy Consent", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Your personal photos remain private and encrypted on your device. " +
                        "When using cloud try-on models, photos are processed securely and never retained for model training.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appSettings.setLikenessConsentAccepted()
                        showLikenessDialog = false
                        pendingLikenessAction?.invoke()
                        pendingLikenessAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VestraColors.Accent),
                ) {
                    Text("Accept & Continue", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLikenessDialog = false
                    pendingLikenessAction = null
                }) {
                    Text("Cancel")
                }
            },
        )
    }

    val activeGarment = outfit.firstOrNull()
    val activePerson = shots.firstOrNull()
    val isGenerating = shootState?.let { it.inner is GenerationState.Preparing || it.inner is GenerationState.Running } == true
    val latestCompletedResult = (shootState?.inner as? GenerationState.Complete)?.result
        ?: shootState?.completed?.lastOrNull()

    SpatialBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            // Header Bar
            StudioTopBar(
                activeTier = activeTier,
                activeProviderId = activeCloudProviderId,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onReset = {
                    viewModel.resetSession()
                    viewModel.resetParameters()
                    availableStudioModels.firstOrNull()?.let {
                        viewModel.setSinglePerson(PersonSource.AiModel(modelId = it.id))
                    }
                    val defaultGarment = SampleGarmentCatalog.items.first()
                    viewModel.setSingleGarment(SampleGarmentCatalog.resolveUri(context, defaultGarment), defaultGarment.category)
                    viewModel.setGarmentDesc(defaultGarment.promptHint)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cloud & Engine Model Selector Row
                ModelEnginePickerCard(
                    activeTier = activeTier,
                    activeProviderId = activeCloudProviderId,
                    onSelectTier = { tier ->
                        viewModel.setCustomEngineTier(tier)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onSelectCloudProvider = { providerId ->
                        viewModel.selectCloudProvider(providerId)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                )

                // Dual Selection Workspace: Model + Garment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Left Column: Person / Model Selection Card
                    PersonSelectorCard(
                        modifier = Modifier.weight(1f),
                        activePerson = activePerson,
                        activeTab = activeModelTab,
                        availableStudioModels = availableStudioModels,
                        onSelectTab = { activeModelTab = it },
                        onSelectPreset = { model ->
                            viewModel.setSinglePerson(PersonSource.AiModel(modelId = model.id))
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onPickPhoto = {
                            gatePersonPhoto {
                                pickPersonLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        onTakePhoto = {
                            gatePersonPhoto {
                                launchPersonCamera()
                            }
                        },
                    )

                    // Right Column: Garment / Outfit Selection Card
                    GarmentSelectorCard(
                        modifier = Modifier.weight(1f),
                        activeGarment = activeGarment,
                        onSelectSample = { sample ->
                            val uri = SampleGarmentCatalog.resolveUri(context, sample)
                            viewModel.setSingleGarment(uri, sample.category)
                            viewModel.setGarmentDesc(sample.promptHint)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onPickGarment = {
                            pickGarmentLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onTakeGarment = {
                            launchGarmentCamera()
                        },
                        onRotateGarment = {
                            if (activeGarment != null && !rotatingGarment) {
                                rotatingGarment = true
                                scope.launch {
                                    val rotated = withContext(Dispatchers.IO) {
                                        com.zakir.vestra.data.ImageRotator.rotate90(context, activeGarment.uri)
                                    }
                                    rotated?.let { viewModel.setGarmentUri(0, it) }
                                    rotatingGarment = false
                                }
                            }
                        },
                        onSelectCategory = { category ->
                            viewModel.setGarmentCategory(0, category)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    )
                }

                // In-Place Generation Progress or Result Pane
                if (isGenerating || shootState?.inner is GenerationState.Failed) {
                    GenerationStatusCard(
                        shootState = shootState,
                        liveLogs = liveLogs,
                        onCancel = { viewModel.cancelShoot() },
                        onRetry = { viewModel.startShoot() },
                    )
                } else if (latestCompletedResult != null) {
                    // Result Display with Interactive Split Before/After Slider
                    FittingResultDisplayCard(
                        result = latestCompletedResult,
                        originalPersonModel = activePerson?.let { person ->
                            when (person) {
                                is PersonSource.UserPhoto -> person.uri
                                is PersonSource.AiModel -> availableStudioModels.firstOrNull { it.id == person.modelId }?.coilModel
                            }
                        },
                        onSaveToWardrobe = {
                            savedSuccessId = latestCompletedResult.imagePath
                            Toast.makeText(context, "Saved to Wardrobe ✨", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            runCatching {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(latestCompletedResult.imagePath))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Try-On Fitting"))
                            }
                        },
                        onNewShot = {
                            viewModel.resetSession()
                            availableStudioModels.firstOrNull()?.let {
                                viewModel.setSinglePerson(PersonSource.AiModel(modelId = it.id))
                            }
                        },
                        isSaved = savedSuccessId == latestCompletedResult.imagePath,
                    )
                }

                // Single-Tap Generate Button
                PrimaryFittingActionButton(
                    isReady = activeGarment != null && activePerson != null,
                    isGenerating = isGenerating,
                    activeTier = activeTier,
                    onGenerate = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startShoot()
                    },
                )

                // Advanced Precision Parameters Accordion (Default parameters already active)
                PrecisionParametersAccordion(
                    expanded = showAdvancedParams,
                    onToggle = { showAdvancedParams = !showAdvancedParams },
                    steps = steps,
                    onStepsChange = { viewModel.setSteps(it) },
                    cfg = cfg,
                    onCfgChange = { viewModel.setCfg(it) },
                    seed = seed,
                    seedText = seedText,
                    onSeedChange = {
                        seedText = it
                        viewModel.setSeed(it.toIntOrNull())
                    },
                    onRandomizeSeed = {
                        viewModel.randomizeSeed()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    garmentDesc = garmentDesc,
                    onGarmentDescChange = { viewModel.setGarmentDesc(it) },
                    autoMask = autoMask,
                    onAutoMaskChange = { viewModel.setAutoMask(it) },
                    autoCrop = autoCrop,
                    onAutoCropChange = { viewModel.setAutoCrop(it) },
                    backdrop = backdrop,
                    onBackdropChange = { viewModel.setBackdrop(it) },
                    onResetDefaults = {
                        viewModel.resetParameters()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "Parameters reset to defaults", Toast.LENGTH_SHORT).show()
                    },
                )

                // Casting & Model Diversity Adjustments
                CastingDiversityAccordion(
                    expanded = showCastingStudio,
                    onToggle = { showCastingStudio = !showCastingStudio },
                    casting = casting,
                    onSetEthnicity = { viewModel.setEthnicity(it) },
                    onSetSkinTone = { viewModel.setSkinTone(it) },
                    onSetBodyType = { viewModel.setBodyType(it) },
                    onSetHairCoverage = { viewModel.setHairCoverage(it) },
                )

                // Recent Wardrobe Fittings Carousel
                if (recentLooks.isNotEmpty()) {
                    RecentLooksCarousel(
                        recentLooks = recentLooks,
                        onSelectLook = { entry ->
                            viewModel.setSinglePerson(PersonSource.UserPhoto(entry.imagePath))
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: TopBar, Model Picker, Garment & Person Cards, Accordions, and Split View
// -------------------------------------------------------------------------------------------------

@Composable
private fun StudioTopBar(
    activeTier: EngineTier,
    activeProviderId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
                    modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VestraSpacing.md, vertical = VestraSpacing.xs),

        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VestraColors.GlassFill)
                    .border(1.dp, VestraColors.GlassBorder, CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Virtual Fitting Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VestraColors.Accent.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "SINGLE-PAGE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = VestraColors.Accent,
                        )
                    }
                }
                Text(
                    "Couture neural try-on & multi-model diffusion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VestraColors.GlassFill)
                    .border(1.dp, VestraColors.GlassBorder, CircleShape),
            ) {
                Icon(
                    Icons.Outlined.RestartAlt,
                    contentDescription = "Reset",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VestraColors.GlassFill)
                    .border(1.dp, VestraColors.GlassBorder, CircleShape),
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Model & Engine Picker Card supporting all cloud and local engines.
 */
@Composable
private fun ModelEnginePickerCard(
    activeTier: EngineTier,
    activeProviderId: String,
    onSelectTier: (EngineTier) -> Unit,
    onSelectCloudProvider: (String) -> Unit,
) {
    val cloudProviders = remember { CloudModelCatalog.providers.filter { it.capability == AiCapability.TRY_ON } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VestraShapes.card))
            .background(VestraColors.SurfaceRaised)
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(VestraShapes.card))
            .padding(VestraSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Inference Engine & Model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                if (activeTier == EngineTier.CLOUD) CloudModelCatalog.byId(activeProviderId)?.displayName ?: "Cloud" else activeTier.name,
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.Accent,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Horizontal scroll of all Try-On Models
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Cloud Models
            cloudProviders.forEach { provider ->
                val isSelected = activeTier == EngineTier.CLOUD && activeProviderId == provider.id
                ModelChip(
                    label = provider.displayName,
                    badge = if (provider.id == "ootd-hf") "⚡ READY" else "CLOUD",
                    isSelected = isSelected,
                    onClick = { onSelectCloudProvider(provider.id) },
                )
            }

            // Local Tiers
            ModelChip(
                label = "Local Lite",
                badge = "100% OFFLINE",
                isSelected = activeTier == EngineTier.LITE,
                onClick = { onSelectTier(EngineTier.LITE) },
            )

            ModelChip(
                label = "Local Pro",
                badge = "NEURAL PRO",
                isSelected = activeTier == EngineTier.PRO,
                onClick = { onSelectTier(EngineTier.PRO) },
            )
        }
    }
}

@Composable
private fun ModelChip(
    label: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) VestraColors.Accent else VestraColors.GlassBorder
    val bgColor = if (isSelected) VestraColors.Accent.copy(alpha = 0.15f) else VestraColors.GlassFill

    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(VestraShapes.control))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(VestraShapes.control))
            .clickable(onClick = onClick)
            .padding(horizontal = VestraSpacing.sm, vertical = VestraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSelected) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(14.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) VestraColors.Accent else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                badge,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Person / Model Selection Card with Studio Model Presets and Custom Photo upload options.
 */
@Composable
private fun PersonSelectorCard(
    modifier: Modifier = Modifier,
    activePerson: PersonSource?,
    activeTab: Int,
    availableStudioModels: List<StudioModel>,
    onSelectTab: (Int) -> Unit,
    onSelectPreset: (StudioModel) -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusTokens.lg))
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.lg))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("1. Model", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            if (activePerson != null) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = VestraColors.Accent, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Model Preview Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(RadiusTokens.md))
                .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.md)),
            contentAlignment = Alignment.Center,
        ) {
            if (activePerson != null) {
                val previewModel = when (activePerson) {
                    is PersonSource.UserPhoto -> activePerson.uri
                    is PersonSource.AiModel -> availableStudioModels.firstOrNull { it.id == activePerson.modelId }?.coilModel
                }
                ShimmerAsyncImage(
                    model = previewModel,
                    contentDescription = "Selected Model",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "Pick a model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Segmented Switcher for Studio Models vs Upload
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (activeTab == 0) VestraColors.Accent else Color.Transparent)
                    .clickable { onSelectTab(0) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Studio",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (activeTab == 1) VestraColors.Accent else Color.Transparent)
                    .clickable { onSelectTab(1) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "My Photo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (activeTab == 0) {
            // Studio Model Preset Quick Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                availableStudioModels.forEach { model ->
                    val isSelected = (activePerson as? PersonSource.AiModel)?.modelId == model.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) VestraColors.Accent.copy(alpha = 0.3f) else VestraColors.GlassFill)
                            .border(1.dp, if (isSelected) VestraColors.Accent else VestraColors.GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectPreset(model) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            model.displayName,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        } else {
            // Upload / Camera Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = onPickPhoto,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VestraColors.GlassFill),
                    border = borderStroke(),
                    contentPadding = PaddingValues(2.dp),
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Gallery", fontSize = 10.sp)
                }
                Button(
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VestraColors.GlassFill),
                    border = borderStroke(),
                    contentPadding = PaddingValues(2.dp),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Camera", fontSize = 10.sp)
                }
            }
        }
    }
}

/**
 * Garment / Outfit Selection Card with Sample Garments and Camera/Gallery picker.
 */
@Composable
private fun GarmentSelectorCard(
    modifier: Modifier = Modifier,
    activeGarment: GarmentImage?,
    onSelectSample: (SampleGarment) -> Unit,
    onPickGarment: () -> Unit,
    onTakeGarment: () -> Unit,
    onRotateGarment: () -> Unit,
    onSelectCategory: (GarmentCategory) -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusTokens.lg))
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.lg))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Layers, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("2. Garment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            if (activeGarment != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRotateGarment,
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(Icons.Outlined.Rotate90DegreesCw, contentDescription = "Rotate", modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = VestraColors.Accent, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Garment Preview Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(RadiusTokens.md))
                .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.md)),
            contentAlignment = Alignment.Center,
        ) {
            if (activeGarment != null) {
                ShimmerAsyncImage(
                    model = activeGarment.uri,
                    contentDescription = "Selected Garment",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    "Pick garment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Sample Garments Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SampleGarmentCatalog.items.forEach { sample ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(VestraColors.GlassFill)
                        .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelectSample(sample) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        sample.title.split(" ").firstOrNull().orEmpty(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Upload / Camera Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = onPickGarment,
                modifier = Modifier.weight(1f).height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VestraColors.GlassFill),
                border = borderStroke(),
                contentPadding = PaddingValues(2.dp),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(2.dp))
                Text("Upload", fontSize = 10.sp)
            }
            Button(
                onClick = onTakeGarment,
                modifier = Modifier.weight(1f).height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VestraColors.GlassFill),
                border = borderStroke(),
                contentPadding = PaddingValues(2.dp),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(2.dp))
                Text("Camera", fontSize = 10.sp)
            }
        }
    }
}

/**
 * Prominent Single-Tap Action Button for Try-On.
 */
@Composable
private fun PrimaryFittingActionButton(
    isReady: Boolean,
    isGenerating: Boolean,
    activeTier: EngineTier,
    onGenerate: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Button(
        onClick = onGenerate,
        enabled = isReady && !isGenerating,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(TestTags.SEND_BUTTON),
        shape = RoundedCornerShape(RadiusTokens.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isReady && !isGenerating) {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6).copy(alpha = glowAlpha),
                                Color(0xFFD946EF),
                            ),
                        )
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                    )
                    Text("Rendering Neural Fitting…", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(
                        if (!isReady) "Select Model & Garment to Try-On" else "Generate Virtual Fitting",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

/**
 * Live In-Place Generation Status & Log Monitor.
 */
@Composable
private fun GenerationStatusCard(
    shootState: ShootState?,
    liveLogs: List<String>,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val phase = shootState?.inner

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusTokens.lg))
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.lg))
            .padding(16.dp),
    ) {
        when (phase) {
            is GenerationState.Running -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(phase.stage, fontWeight = FontWeight.Bold, color = VestraColors.Accent)
                    Text("${(phase.fraction * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { phase.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = VestraColors.Accent,
                    trackColor = VestraColors.GlassBorder,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
            is GenerationState.Preparing -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(phase.message, fontWeight = FontWeight.Bold, color = VestraColors.Accent)
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            is GenerationState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (phase.error as? TryOnError.Internal)?.message ?: "Generation encountered an issue",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = VestraColors.Accent),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Retry Fitting")
                }
            }
            else -> Unit
        }

        if (liveLogs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF020617))
                    .padding(8.dp),
            ) {
                Text(
                    liveLogs.takeLast(3).joinToString("\n"),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * In-Place High-Fidelity Result Display with Interactive Split Before/After Slider.
 */
@Composable
private fun FittingResultDisplayCard(
    result: TryOnResult,
    originalPersonModel: Any?,
    onSaveToWardrobe: () -> Unit,
    onShare: () -> Unit,
    onNewShot: () -> Unit,
    isSaved: Boolean,
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusTokens.lg))
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(RadiusTokens.lg))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Fitted Couture Look", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(VestraColors.Accent.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    result.executedTier.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VestraColors.Accent,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Split Comparison Frame
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(RadiusTokens.md))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val widthPx = constraints.maxWidth.toFloat()

            // After / Fitted Image (Full Background)
            ShimmerAsyncImage(
                model = result.imagePath,
                contentDescription = "Fitted Look",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Before / Original Image (Clipped to left side of split fraction)
            if (originalPersonModel != null && splitFraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(
                                left = 0f,
                                top = 0f,
                                right = size.width * splitFraction,
                                bottom = size.height,
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) {
                    ShimmerAsyncImage(
                        model = originalPersonModel,
                        contentDescription = "Original Model",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Draggable Divider Line & Thumb
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x = (splitFraction * widthPx).roundToInt(), y = 0) }
                    .background(Color.White),
            )

            // Slider Grab Handle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x = (splitFraction * widthPx - 18.dp.toPx()).roundToInt(), y = 0) }
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, VestraColors.Accent, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                        ) { change, dragAmount ->
                            change.consume()
                            val newFraction = (splitFraction + (dragAmount.x / widthPx)).coerceIn(0.05f, 0.95f)
                            splitFraction = newFraction
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = "Drag to compare",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Labels: Before / After
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("BEFORE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(VestraColors.Accent.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("AFTER (TRY-ON)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action Buttons Row (Save to Wardrobe, Share, New Look)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSaveToWardrobe,
                modifier = Modifier.weight(1f).height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) Color(0xFF10B981) else VestraColors.Accent,
                ),
                shape = RoundedCornerShape(RadiusTokens.md),
            ) {
                Icon(
                    if (isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isSaved) "Saved" else "Save Look", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(RadiusTokens.md))
                    .background(VestraColors.GlassFill)
                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.md)),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
            }

            IconButton(
                onClick = onNewShot,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(RadiusTokens.md))
                    .background(VestraColors.GlassFill)
                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.md)),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "New Fitting", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Expandable Accordion for Precision Parameters with Sensible Defaults.
 */
@Composable
private fun PrecisionParametersAccordion(
    expanded: Boolean,
    onToggle: () -> Unit,
    steps: Int,
    onStepsChange: (Int) -> Unit,
    cfg: Double,
    onCfgChange: (Double) -> Unit,
    seed: Int?,
    seedText: String,
    onSeedChange: (String) -> Unit,
    onRandomizeSeed: () -> Unit,
    garmentDesc: String,
    onGarmentDescChange: (String) -> Unit,
    autoMask: Boolean,
    onAutoMaskChange: (Boolean) -> Unit,
    autoCrop: Boolean,
    onAutoCropChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    onBackdropChange: (Backdrop) -> Unit,
    onResetDefaults: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusTokens.lg))
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.lg))
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Precision Parameters & Defaults", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Steps: $steps · CFG: $cfg · Auto-Mask: $autoMask", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }

        if (expanded) {
            HorizontalDivider(color = VestraColors.GlassBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Steps Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Denoising Steps", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("$steps steps", style = MaterialTheme.typography.labelSmall, color = VestraColors.Accent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = steps.toFloat(),
                        onValueChange = { onStepsChange(it.roundToInt()) },
                        valueRange = 10f..60f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = VestraColors.Accent,
                            activeTrackColor = VestraColors.Accent,
                        ),
                    )
                }

                // CFG Guidance Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Guidance Scale (CFG)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("$cfg", style = MaterialTheme.typography.labelSmall, color = VestraColors.Accent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = cfg.toFloat(),
                        onValueChange = { onCfgChange(it.toDouble()) },
                        valueRange = 1.0f..7.5f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = VestraColors.Accent,
                            activeTrackColor = VestraColors.Accent,
                        ),
                    )
                }

                // Seed input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = onSeedChange,
                        label = { Text("Seed (Optional / Random)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VestraColors.Accent,
                            unfocusedBorderColor = VestraColors.GlassBorder,
                        ),
                    )
                    Button(
                        onClick = onRandomizeSeed,
                        colors = ButtonDefaults.buttonColors(containerColor = VestraColors.GlassFill),
                        border = borderStroke(),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Outlined.Casino, contentDescription = "Randomize Seed")
                    }
                }

                // Garment Prompt Guide
                OutlinedTextField(
                    value = garmentDesc,
                    onValueChange = onGarmentDescChange,
                    label = { Text("Garment Prompt Guide / Fabric Texture") },
                    placeholder = { Text("e.g. Emerald silk crepe with gold embroidery") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VestraColors.Accent,
                        unfocusedBorderColor = VestraColors.GlassBorder,
                    ),
                )

                // Quick Prompt Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Silk Crepe", "Gold Embroidery", "Chiffon Drape", "Linen Texture", "Velvet Gown").forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VestraColors.GlassFill)
                                .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(12.dp))
                                .clickable { onGarmentDescChange(chip) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(chip, fontSize = 10.sp)
                        }
                    }
                }

                // Toggles for Auto-Mask and Auto-Crop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Auto-Generate Mask", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Extract person outline automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoMask,
                        onCheckedChange = onAutoMaskChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VestraColors.Accent),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Auto-Crop Bounds", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Align subject to central torso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoCrop,
                        onCheckedChange = onAutoCropChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VestraColors.Accent),
                    )
                }

                // Backdrop Environment
                Column {
                    Text("Scene Backdrop", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Backdrop.entries.forEach { b ->
                            val isSelected = backdrop == b
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) VestraColors.Accent.copy(alpha = 0.25f) else VestraColors.GlassFill)
                                    .border(1.dp, if (isSelected) VestraColors.Accent else VestraColors.GlassBorder, RoundedCornerShape(12.dp))
                                    .clickable { onBackdropChange(b) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(b.displayName, fontSize = 11.sp, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // Reset Parameters button
                TextButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset Defaults")
                }
            }
        }
    }
}

/**
 * Expandable Accordion for Casting & Model Diversity Adjustments.
 */
@Composable
private fun CastingDiversityAccordion(
    expanded: Boolean,
    onToggle: () -> Unit,
    casting: CastingProfile,
    onSetEthnicity: (Ethnicity) -> Unit,
    onSetSkinTone: (SkinTone) -> Unit,
    onSetBodyType: (BodyType) -> Unit,
    onSetHairCoverage: (HairCoverage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusTokens.lg))
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.lg))
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Casting & Model Attributes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${casting.ethnicity.name.lowercase().replaceFirstChar { it.uppercase() }} · ${casting.bodyType.name.lowercase()} · ${casting.hairCoverage.name.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }

        if (expanded) {
            HorizontalDivider(color = VestraColors.GlassBorder)
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Ethnicity
                Column {
                    Text("Ethnicity & Region", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Ethnicity.entries.forEach { eth ->
                            val sel = casting.ethnicity == eth
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) VestraColors.Accent.copy(alpha = 0.25f) else VestraColors.GlassFill)
                                    .border(1.dp, if (sel) VestraColors.Accent else VestraColors.GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable { onSetEthnicity(eth) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(eth.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Hair Coverage (Modest fashion focus)
                Column {
                    Text("Modest Styling & Coverage", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HairCoverage.entries.forEach { hc ->
                            val sel = casting.hairCoverage == hc
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) VestraColors.Accent.copy(alpha = 0.25f) else VestraColors.GlassFill)
                                    .border(1.dp, if (sel) VestraColors.Accent else VestraColors.GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable { onSetHairCoverage(hc) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(hc.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recent Looks Wardrobe Carousel at the bottom of the studio screen.
 */
@Composable
private fun RecentLooksCarousel(
    recentLooks: List<WardrobeEntry>,
    onSelectLook: (WardrobeEntry) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Recent Studio Wardrobe Looks", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recentLooks.forEach { entry ->
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(RadiusTokens.md))
                        .background(VestraColors.GlassFill)
                        .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(RadiusTokens.md))
                        .clickable { onSelectLook(entry) },
                    contentAlignment = Alignment.Center,
                ) {
                    ShimmerAsyncImage(
                        model = entry.imagePath,
                        contentDescription = "Recent look",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

private fun borderStroke() = androidx.compose.foundation.BorderStroke(1.dp, VestraColors.GlassBorder)
