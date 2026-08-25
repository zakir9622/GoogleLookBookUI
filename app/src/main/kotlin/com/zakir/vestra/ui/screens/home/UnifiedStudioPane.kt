package com.zakir.vestra.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.components.ExamplePromptRow
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassErrorBanner
import com.zakir.vestra.ui.components.GlassOptionToggle
import com.zakir.vestra.ui.components.GlassPill
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.components.QuickPromptItem
import com.zakir.vestra.ui.components.PromptComposer
import com.zakir.vestra.ui.components.ResultPane
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a pack-readiness probe off the UI thread.
 */
@Composable
private fun produceLocalReadiness(
    vararg keys: Any?,
    probe: () -> Boolean,
): State<Boolean> = produceState(initialValue = false, keys = keys) {
    value = withContext(Dispatchers.IO) { runCatching(probe).getOrDefault(false) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedStudioPane(
    capability: AiCapability,
    viewModel: GenerativeViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    freeCloudDiscovery: FreeCloudDiscovery? = null,
    packManager: ModelPackManager? = null,
) {
    LaunchedEffect(capability) {
        viewModel.bindStudio(capability)
    }

    val prompt by viewModel.prompt.collectAsState()
    val reference by viewModel.referenceUri.collectAsState()
    val state by viewModel.state.collectAsState()
    val liveLog by viewModel.liveLog.collectAsState()
    val generationStartedAtMs by viewModel.generationStartedAtMs.collectAsState()
    val preflight by viewModel.preflightMessage.collectAsState()
    val creative by viewModel.creativeMode.collectAsState()
    val pragmatic by viewModel.pragmaticMode.collectAsState()
    val detailBoost by viewModel.detailBoost.collectAsState()
    val fashionContext by viewModel.fashionContext.collectAsState()
    val bypassFilter by viewModel.bypassFilter.collectAsState()
    val qualityGuard by viewModel.qualityGuard.collectAsState()
    val analyzeReference by viewModel.analyzeReference.collectAsState()
    val lastUsedId by viewModel.lastUsedProviderId.collectAsState()
    val packStates by packManager?.states?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    val warmup by viewModel.warmup.collectAsState()
    val cloudModelsEnabled by viewModel.appSettings.cloudModelsEnabled.collectAsState()
    val imageGenId by viewModel.appSettings.imageGenProviderId.collectAsState()
    val imageEditId by viewModel.appSettings.imageEditProviderId.collectAsState()
    val codeId by viewModel.appSettings.codeProviderId.collectAsState()
    val videoId by viewModel.appSettings.videoProviderId.collectAsState()

    val effectiveCapability = when (capability) {
        AiCapability.IMAGE_GEN -> if (reference == null) AiCapability.IMAGE_GEN else AiCapability.IMAGE_EDIT
        else -> capability
    }
    val provider = viewModel.appSettings.selectedProvider(effectiveCapability)
    val selectedId = when (effectiveCapability) {
        AiCapability.IMAGE_GEN -> imageGenId
        AiCapability.IMAGE_EDIT -> imageEditId
        AiCapability.CODE -> codeId
        AiCapability.VIDEO -> videoId
        AiCapability.AUDIO -> provider.id
        else -> provider.id
    }
    val estimate = viewModel.usage.estimateNext(provider)
    val preflightChip = viewModel.preflightLabel(effectiveCapability)
    val busy = state is GenerativeState.Running || state is GenerativeState.Preparing

    val localImageReady by produceLocalReadiness(packStates, busy) { viewModel.localImageOfflineReady() }
    val localImageEditReady by produceLocalReadiness(packStates, busy) { viewModel.localImageEditOfflineReady() }
    val localCodeReady by produceLocalReadiness(packStates, busy) { viewModel.localCodeOfflineReady() }
    val localVideoReady by produceLocalReadiness(packStates, busy) { viewModel.localVideoOfflineReady() }
    val localVisionReady by produceLocalReadiness(packStates, busy) { viewModel.localVisionOfflineReady() }

    val assistCount = when (capability) {
        AiCapability.CODE -> listOf(pragmatic, creative).count { it }
        AiCapability.AUDIO -> listOf(fashionContext).count { it }
        AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT, AiCapability.VIDEO ->
            listOf(bypassFilter, fashionContext, detailBoost, qualityGuard, analyzeReference).count { it }
        else -> 0
    }

    var showModelPicker by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    val pickerModels = remember(effectiveCapability, freeCloudDiscovery, cloudModelsEnabled) {
        if (!cloudModelsEnabled) {
            emptyList()
        } else {
            freeCloudDiscovery?.selectable(viewModel.appSettings, effectiveCapability)
                ?: CloudModelCatalog.forCapability(effectiveCapability)
        }
    }
    val onDeviceEntries = remember(
        packStates,
        effectiveCapability,
        localImageReady,
        localImageEditReady,
        localCodeReady,
        localVideoReady,
    ) {
        LocalModelCatalog.forStudioPicker(effectiveCapability).map { entry ->
            val packReady = when (entry.id) {
                "local-sdturbo-v1" -> localImageReady
                "local-sdturbo-edit" -> localImageEditReady
                "local-stillclip-v1" -> localVideoReady
                "local-gemma-v1" -> packStates["local-gemma-v1"]?.isReady() == true
                "local-gemma-4-e2b-v1" -> packStates["local-gemma-4-e2b-v1"]?.isReady() == true
                "local-functiongemma-v1" -> packStates["local-functiongemma-v1"]?.isReady() == true
                else -> entry.packId?.let { packStates[it]?.isReady() == true } == true
            }
            OnDevicePickerEntry(
                id = entry.id,
                displayName = entry.displayName,
                detail = entry.testingNote,
                ready = LocalModelCatalog.studioEntryReady(entry, packReady),
                statusLabel = LocalModelCatalog.studioStatusLabel(entry, packReady),
            )
        }
    }

    LaunchedEffect(selectedId, effectiveCapability) {
        viewModel.warmUpLocal(effectiveCapability)
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.setReference(uri?.toString())
    }

    val moduleTitle = when (capability) {
        AiCapability.IMAGE_GEN -> "IMAGE GENERATION MODULE"
        AiCapability.VIDEO -> "VIDEO GENERATION MODULE"
        AiCapability.CODE -> "CODE GENERATION MODULE"
        AiCapability.AUDIO -> "AUDIO STUDIO MODULE"
        else -> "GENERATION MODULE"
    }

    val moduleDescription = when (capability) {
        AiCapability.IMAGE_GEN ->
            if (cloudModelsEnabled) {
                "Renders high-definition modest couture lookbooks and fashion photography with on-device tiny-SD or ultra-speed cloud diffusion models."
            } else {
                "Renders high-definition modest couture lookbooks and fashion photography with on-device tiny-SD diffusion models."
            }
        AiCapability.VIDEO ->
            if (cloudModelsEnabled) {
                "Generates motion sequences and still-clip runway transitions using AI video pipelines."
            } else {
                "Generates motion sequences and still-clip runway transitions using local on-device video pipelines."
            }
        AiCapability.CODE ->
            if (cloudModelsEnabled) {
                "Synthesizes production Kotlin Compose UI code and architectural refactors with on-device Gemma or cloud reasoning LLMs."
            } else {
                "Synthesizes production Kotlin Compose UI code and architectural refactors with on-device Gemma LLMs."
            }
        AiCapability.AUDIO ->
            "Generates speech waveforms and applies real-time DSP voice transformations."
        else -> "Interactive generative AI atelier studio."
    }

    val modelDisplayLabel = when {
        !cloudModelsEnabled -> {
            when {
                effectiveCapability == AiCapability.IMAGE_GEN && reference == null -> "Local tiny-SD (offline)"
                effectiveCapability == AiCapability.IMAGE_EDIT || reference != null -> "Local img2img (offline)"
                effectiveCapability == AiCapability.CODE -> "Local Gemma (offline)"
                effectiveCapability == AiCapability.VIDEO -> "Local still-clip (offline)"
                effectiveCapability == AiCapability.AUDIO -> "Device TTS (offline)"
                else -> "Local Model (offline)"
            }
        }
        effectiveCapability == AiCapability.IMAGE_GEN && localImageReady && reference == null ->
            "Local tiny-SD (offline)"
        effectiveCapability == AiCapability.IMAGE_EDIT && localImageEditReady ->
            "Local img2img (offline)"
        effectiveCapability == AiCapability.CODE && localCodeReady ->
            "Local Gemma (offline)"
        effectiveCapability == AiCapability.VIDEO && localVideoReady ->
            "Local still-clip (offline)"
        else -> provider.displayName
    }

    val placeholder = when (capability) {
        AiCapability.IMAGE_GEN -> if (reference == null) {
            "Describe the image… emerald abaya in a Lahore bazaar"
        } else {
            "Describe the edit… change to navy silk, soft studio light"
        }
        AiCapability.VIDEO -> "Describe the clip… abaya walking through a Karachi night bazaar"
        AiCapability.CODE -> "Ask for code… Kotlin Compose glass card with frosted border"
        else -> "Enter a prompt…"
    }

    val examples = when (capability) {
        AiCapability.IMAGE_GEN -> listOf(
            "Emerald abaya in a Lahore bazaar, soft afternoon light",
            "Navy silk hijab portrait, studio softbox, editorial",
            "Cream linen shalwar kameez, courtyard architecture",
        )
        AiCapability.VIDEO -> listOf(
            "Woman in black abaya walking through a Karachi night bazaar",
            "Slow pan across embroidered green shalwar kameez in soft daylight",
            "Hijabi model turning toward camera, linen texture detail",
        )
        AiCapability.CODE -> listOf(
            "Write a Kotlin Compose frosted glass card with border highlight",
            "Explain how to resume an Android OkHttp download with Range headers",
            "Refactor this into a StateFlow ViewModel pattern (paste code)",
        )
        else -> emptyList()
    }

    fun onGenerate() = when (capability) {
        AiCapability.IMAGE_GEN -> viewModel.generateImage()
        AiCapability.VIDEO -> viewModel.generateVideo()
        AiCapability.CODE -> viewModel.generateCode()
        AiCapability.AUDIO -> viewModel.generateAudio()
        else -> Unit
    }

    val scrollState = rememberScrollState()

    val quickPromptItems = remember(capability, reference) {
        when (capability) {
            AiCapability.IMAGE_GEN -> if (reference == null) {
                listOf(
                    QuickPromptItem("Emerald abaya in a Lahore bazaar, soft afternoon light", "Editorial"),
                    QuickPromptItem("Navy silk hijab portrait, studio softbox", "Portrait"),
                    QuickPromptItem("Cream linen shalwar kameez, courtyard architecture", "Couture"),
                    QuickPromptItem("Textured raw silk kaftan with gold embroidery", "Detail"),
                )
            } else {
                listOf(
                    QuickPromptItem("Change fabric to navy raw silk with soft studio lighting", "Recolor"),
                    QuickPromptItem("Add gold zardozi embroidery along the lapels", "Embroidery"),
                    QuickPromptItem("Convert to cinematic outdoor golden hour backdrop", "Lighting"),
                )
            }
            AiCapability.VIDEO -> listOf(
                QuickPromptItem("Woman in black abaya walking through a Karachi night bazaar", "Cinematic"),
                QuickPromptItem("Slow pan across embroidered green shalwar kameez in soft daylight", "Runway"),
                QuickPromptItem("Hijabi model turning toward camera, linen texture detail", "Portrait"),
            )
            AiCapability.CODE -> listOf(
                QuickPromptItem("Write a Kotlin Compose frosted glass card with border highlight", "UI Composable"),
                QuickPromptItem("Explain how to resume an Android OkHttp download with Range headers", "Networking"),
                QuickPromptItem("Refactor this into a StateFlow ViewModel pattern (paste code)", "Architecture"),
            )
            else -> emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VestraColors.Canvas),
    ) {
        // Scrollable Middle Generation Canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            // TOP SECTION MODULE: Module Header & Initialized Model Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(VestraColors.SurfaceRaised)
                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(VestraColors.Accent),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = moduleTitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            ),
                            color = VestraColors.Ink,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(VestraColors.GlassFill)
                            .border(1.dp, VestraColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { showModelPicker = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = modelDisplayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = VestraColors.Accent,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = moduleDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = VestraColors.InkMuted,
                )

                // Model Initialization & Warmup Status
                Spacer(Modifier.height(8.dp))
                when (val w = warmup) {
                    is GenerativeViewModel.Warmup.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VestraColors.GlassFill)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = VestraColors.Accent,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Initializing ${w.label} weights…",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.Ink,
                            )
                        }
                    }
                    is GenerativeViewModel.Warmup.Ready -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VestraColors.GlassFill)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = VestraColors.Accent,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${w.label} initialized & ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.Ink,
                            )
                        }
                    }
                    is GenerativeViewModel.Warmup.Failed -> {
                        GlassErrorBanner(
                            message = "${w.label} load issue: ${w.reason}",
                            onRetry = { viewModel.warmUpLocal(effectiveCapability) },
                            retryLabel = "Retry load",
                            onDismiss = null,
                        )
                    }
                    GenerativeViewModel.Warmup.Idle -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                estimate,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = VestraColors.InkMuted,
                            )
                            if (preflightChip != null && preflight == null) {
                                GlassPill(text = preflightChip, active = true)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Preflight error banner
            if (preflight != null) {
                GlassErrorBanner(
                    message = preflight!!,
                    onRetry = onOpenSettings ?: { showModelPicker = true },
                    retryLabel = if (onOpenSettings != null) LookbookCopy.ACTION_OPEN_SETTINGS else "Choose model",
                    onDismiss = { viewModel.clearResult() },
                )
                Spacer(Modifier.height(10.dp))
            }

            // MIDDLE GENERATION SECTION: Protractored Message & Artifact Canvas
            val hasResult = (viewModel.resultBelongsTo(effectiveCapability) || viewModel.resultBelongsTo(capability)) && state != null

            if (!hasResult && !busy) {
                // Empty state with quick starter prompts
                if (examples.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            "CURATED PROMPT STARTERS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = VestraColors.Accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        ExamplePromptRow(
                            examples = examples,
                            enabled = !busy,
                            onPick = viewModel::setPrompt,
                        )
                    }
                }
            } else {
                // Active / Finished Generation Deliverable Stream
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val failedMsg = (state as? GenerativeState.Failed)?.message.orEmpty()
                    val quotaOrCredits = failedMsg.contains("ZeroGPU", ignoreCase = true) ||
                        failedMsg.contains("monthly credits", ignoreCase = true) ||
                        failedMsg.contains("Inference Providers", ignoreCase = true)

                    ResultPane(
                        state = state,
                        liveLog = emptyList(), // Live logs rendered in persistent bottom dock
                        generationStartedAtMs = generationStartedAtMs,
                        onCancel = { viewModel.forceStop() },
                        onRetry = {
                            viewModel.clearResult()
                            if (quotaOrCredits) {
                                showModelPicker = true
                            } else {
                                onGenerate()
                            }
                        },
                        retryLabel = if (quotaOrCredits) "Choose model" else LookbookCopy.ACTION_RETRY,
                        onDismiss = viewModel::clearResult,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        // BOTTOM PERSISTENT DOCK: Attached chatbox + Live Telemetry & Countdown Box
        PromptComposer(
            prompt = prompt,
            onPromptChange = viewModel::setPrompt,
            modelLabel = modelDisplayLabel,
            assistCount = assistCount,
            busy = busy,
            enabled = true,
            onModelClick = { showModelPicker = true },
            onSend = ::onGenerate,
            onStop = { viewModel.cancel() },
            placeholder = placeholder,
            referenceUri = reference,
            onAddReference = if (capability == AiCapability.IMAGE_GEN || capability == AiCapability.VIDEO) {
                { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            } else null,
            onClearReference = { viewModel.setReference(null) },
            liveLog = liveLog,
            generationStartedAtMs = generationStartedAtMs,
            deadlineEpochMs = (state as? GenerativeState.Running)?.deadlineEpochMs,
            showLiveDock = true,
            quickPrompts = quickPromptItems,
            onSelectQuickPrompt = viewModel::setPrompt,
            assistToggles = {
                AdvancedAssistRow(
                    capability = capability,
                    bypassFilter = bypassFilter,
                    fashionContext = fashionContext,
                    detailBoost = detailBoost,
                    qualityGuard = qualityGuard,
                    analyzeReference = analyzeReference,
                    localVisionReady = localVisionReady,
                    pragmatic = pragmatic,
                    creative = creative,
                    onBypassFilter = { viewModel.setBypassFilter(!bypassFilter) },
                    onFashionContext = { viewModel.setFashionContext(!fashionContext) },
                    onDetailBoost = { viewModel.setDetailBoost(!detailBoost) },
                    onQualityGuard = { viewModel.setQualityGuard(!qualityGuard) },
                    onAnalyzeReference = { viewModel.setAnalyzeReference(!analyzeReference) },
                    onPragmatic = { viewModel.setPragmaticMode(!pragmatic) },
                    onCreative = { viewModel.setCreativeMode(!creative) },
                )
            },
        )
    }

    // Model Selector Sheet Dialog
    if (showModelPicker) {
        ModelPickerSheet(
            title = if (cloudModelsEnabled) "${subtitle(capability)} models" else "${subtitle(capability)} models · on-device",
            models = pickerModels,
            selectedId = selectedId,
            onDeviceEntries = onDeviceEntries,
            health = viewModel.appSettings.modelHealth,
            onSelect = { chosen ->
                when (effectiveCapability) {
                    AiCapability.IMAGE_GEN -> viewModel.appSettings.setImageGenProvider(chosen.id)
                    AiCapability.IMAGE_EDIT -> viewModel.appSettings.setImageEditProvider(chosen.id)
                    AiCapability.CODE -> viewModel.appSettings.setCodeProvider(chosen.id)
                    AiCapability.VIDEO -> viewModel.appSettings.setVideoProvider(chosen.id)
                    AiCapability.AUDIO -> viewModel.appSettings.setAudioProvider(chosen.id)
                    else -> Unit
                }
            },
            onSelectDevice = { entry ->
                if (entry.ready) viewModel.appSettings.setLocalGenerator(effectiveCapability, entry.id)
            },
            onDismiss = { showModelPicker = false },
        )
    }
}

private fun subtitle(capability: AiCapability): String = when (capability) {
    AiCapability.IMAGE_GEN -> "Image"
    AiCapability.VIDEO -> "Video"
    AiCapability.CODE -> "Code"
    AiCapability.AUDIO -> "Audio"
    else -> "Studio"
}

@Composable
private fun AdvancedAssistRow(
    capability: AiCapability,
    bypassFilter: Boolean,
    fashionContext: Boolean,
    detailBoost: Boolean,
    qualityGuard: Boolean,
    analyzeReference: Boolean,
    localVisionReady: Boolean,
    pragmatic: Boolean,
    creative: Boolean,
    onBypassFilter: () -> Unit,
    onFashionContext: () -> Unit,
    onDetailBoost: () -> Unit,
    onQualityGuard: () -> Unit,
    onAnalyzeReference: () -> Unit,
    onPragmatic: () -> Unit,
    onCreative: () -> Unit,
) {
    when (capability) {
        AiCapability.CODE -> {
            GlassOptionToggle(text = "Pragmatic", active = pragmatic, onToggle = onPragmatic)
            GlassOptionToggle(text = "Creative", active = creative, onToggle = onCreative)
        }
        AiCapability.AUDIO -> {
            GlassOptionToggle(text = "Fashion voice", active = fashionContext, onToggle = onFashionContext)
        }
        AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT, AiCapability.VIDEO -> {
            GlassOptionToggle(text = "Detail boost", active = detailBoost, onToggle = onDetailBoost)
            GlassOptionToggle(text = "Quality guard", active = qualityGuard, onToggle = onQualityGuard)
            GlassOptionToggle(text = "Editorial style", active = fashionContext, onToggle = onFashionContext)
            if (localVisionReady) {
                GlassOptionToggle(text = "Vision tag", active = analyzeReference, onToggle = onAnalyzeReference)
            }
        }
        else -> Unit
    }
}
