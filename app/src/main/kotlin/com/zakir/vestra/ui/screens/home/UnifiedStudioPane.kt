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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.components.ShimmerAsyncImage
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.cloud.ImageEditIntent
import com.zakir.vestra.shared.cloud.StyleModifierCatalog
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassErrorBanner
import com.zakir.vestra.ui.components.GlassOptionToggle
import com.zakir.vestra.ui.components.GlassPill
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.components.PromptComposer
import com.zakir.vestra.ui.components.PromptCurator
import com.zakir.vestra.ui.components.PromptDirectorSheet
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
    val recipe by viewModel.promptRecipe.collectAsState()
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
    var showPromptDirector by remember { mutableStateOf(false) }
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
        AiCapability.IMAGE_GEN -> "Image Generator"
        AiCapability.VIDEO -> "Video Generator"
        AiCapability.CODE -> "Code Generator"
        AiCapability.AUDIO -> "Audio Generator"
        else -> "AI Generator"
    }

    val moduleDescription = when (capability) {
        AiCapability.IMAGE_GEN ->
            if (cloudModelsEnabled) "Modest couture lookbooks with tiny-SD and cloud diffusion."
            else "Modest couture lookbooks with on-device tiny-SD diffusion."
        AiCapability.VIDEO ->
            if (cloudModelsEnabled) "Motion sequences and runway clips via AI video pipelines."
            else "Motion sequences and runway clips with local video pipelines."
        AiCapability.CODE ->
            if (cloudModelsEnabled) "Kotlin Compose UI and architecture with on-device & cloud LLMs."
            else "Kotlin Compose UI and architecture with on-device Gemma LLMs."
        AiCapability.AUDIO ->
            "Speech synthesis and real-time DSP voice transformations."
        else -> "Generative AI studio."
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
            "Describe the visual… cinematic campaign, tactile fabric, late light"
        } else {
            "Describe the change… reshape the light, color, or composition"
        }
        AiCapability.VIDEO -> "Describe the motion… a six-second film with camera direction"
        AiCapability.CODE -> "Ask for a build… a focused Compose screen, flow, or refactor"
        else -> "Describe what you want to make…"
    }

    fun onGenerate() = when (capability) {
        AiCapability.IMAGE_GEN -> viewModel.generateImage()
        AiCapability.VIDEO -> viewModel.generateVideo()
        AiCapability.CODE -> viewModel.generateCode()
        AiCapability.AUDIO -> viewModel.generateAudio()
        else -> Unit
    }

    val scrollState = rememberScrollState()

    val promptSessionSeed = remember { System.currentTimeMillis() }
    val quickPromptItems = remember(capability, reference, promptSessionSeed, prompt) {
        PromptCurator.curate(
            capability = capability,
            referenceAttached = reference != null,
            sessionSeed = promptSessionSeed,
            currentPrompt = prompt,
        )
    }

    val feedItems by viewModel.feedItems.collectAsState()

    // Auto-scroll to bottom of conversational canvas on new turns or deliverables
    LaunchedEffect(
        feedItems.size,
        (state as? GenerativeState.Running)?.stage,
        (state as? GenerativeState.ImageReady)?.path,
        (state as? GenerativeState.ImageBatchReady)?.batch?.selectedCandidateId,
        (state as? GenerativeState.VideoReady)?.path,
    ) {
        if (feedItems.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VestraColors.Canvas),
    ) {
        // Top Minimal Session Action Bar (only when history exists)
        if (feedItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${subtitle(capability).uppercase()} FEED · ${feedItems.size} ${if (feedItems.size == 1) "turn" else "turns"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = VestraColors.InkMuted,
                )
                Text(
                    text = "Clear history",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = VestraColors.Accent,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.clearFeed() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Scrollable Middle Generation & Deliverables Canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            // Preflight error banner if API key or model download required
            if (preflight != null) {
                GlassErrorBanner(
                    message = preflight!!,
                    onRetry = onOpenSettings ?: { showModelPicker = true },
                    retryLabel = if (onOpenSettings != null) LookbookCopy.ACTION_OPEN_SETTINGS else "Choose model",
                    onDismiss = { viewModel.clearResult() },
                )
                Spacer(Modifier.height(10.dp))
            }

            if (feedItems.isEmpty() && state == null && !busy) {
                // Clean empty canvas
                Spacer(Modifier.height(32.dp))
            } else {
                // Conversational Stream of Generations & Outputs
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    feedItems.forEach { feedItem ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // User Message Bubble (Prompt + Reference Thumbnail)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 18.dp,
                                                topEnd = 4.dp,
                                                bottomStart = 18.dp,
                                                bottomEnd = 18.dp,
                                            ),
                                        )
                                        .background(VestraColors.SurfaceRaised)
                                        .border(
                                            1.dp,
                                            VestraColors.Accent.copy(alpha = 0.35f),
                                            RoundedCornerShape(
                                                topStart = 18.dp,
                                                topEnd = 4.dp,
                                                bottomStart = 18.dp,
                                                bottomEnd = 18.dp,
                                            ),
                                        )
                                        .padding(12.dp),
                                ) {
                                    // Attached reference image preview
                                    feedItem.referenceUri?.let { uri ->
                                        Box(
                                            modifier = Modifier
                                                .padding(bottom = 8.dp)
                                                .size(width = 120.dp, height = 120.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, VestraColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                        ) {
                                            ShimmerAsyncImage(
                                                model = uri,
                                                contentDescription = "Reference image",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                        }
                                    }

                                    Text(
                                        text = feedItem.prompt.ifBlank { "Generate look" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VestraColors.Ink,
                                    )

                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        feedItem.modelLabel?.let { label ->
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = VestraColors.Accent,
                                            )
                                        }
                                        Text(
                                            text = formatFeedTime(feedItem.timestampMs),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = VestraColors.InkMuted,
                                        )
                                    }
                                }
                            }

                            // AI Output / Deliverable Pane for this turn
                            ResultPane(
                                state = feedItem.state,
                                liveLog = emptyList(), // Live console removed from prompt box
                                generationStartedAtMs = feedItem.generationStartedAtMs,
                                referenceUri = feedItem.referenceUri,
                                prompt = feedItem.prompt,
                                onCancel = { viewModel.forceStop() },
                                onRemix = {
                                    val remixPrompt = if (feedItem.prompt.isNotBlank()) "Remix: ${feedItem.prompt}" else "Remix look"
                                    viewModel.setPrompt(remixPrompt)
                                    if (feedItem.state is GenerativeState.ImageReady) {
                                        viewModel.setReference(feedItem.state.path)
                                    } else if (feedItem.referenceUri != null) {
                                        viewModel.setReference(feedItem.referenceUri)
                                    }
                                },
                                onSelectCandidate = { candidateId ->
                                    viewModel.selectImageCandidate(feedItem.id, candidateId)
                                },
                                onCreateVariation = { candidateId ->
                                    viewModel.createImageVariation(feedItem.id, candidateId)
                                },
                                onEditIntent = { intent: ImageEditIntent ->
                                    val sourcePath = when (val result = feedItem.state) {
                                        is GenerativeState.ImageReady -> result.path
                                        is GenerativeState.ImageBatchReady -> result.batch.selectedCandidate?.path
                                        else -> feedItem.referenceUri
                                    }
                                    viewModel.setPrompt("${feedItem.prompt}. ${intent.promptClause}")
                                    viewModel.setReference(sourcePath)
                                    onGenerate()
                                },
                                onRetry = {
                                    viewModel.setPrompt(feedItem.prompt)
                                    viewModel.setReference(feedItem.referenceUri)
                                    onGenerate()
                                },
                                onQuickTweak = { tweak ->
                                    val currentPrompt = feedItem.prompt
                                    val newPrompt = if (currentPrompt.contains(tweak)) currentPrompt else "$currentPrompt, $tweak"
                                    viewModel.setPrompt(newPrompt)
                                    viewModel.setReference(feedItem.referenceUri)
                                    onGenerate()
                                },
                                retryLabel = LookbookCopy.ACTION_RETRY,
                                onDismiss = { viewModel.removeFeedItem(feedItem.id) },
                            )
                        }
                    }
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
            onPromptDirectorClick = if (capability == AiCapability.IMAGE_GEN) {
                { showPromptDirector = true }
            } else null,
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
            modelLoading = warmup is GenerativeViewModel.Warmup.Loading,
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

    if (showPromptDirector) {
        PromptDirectorSheet(
            recipe = recipe,
            modifiers = StyleModifierCatalog.all,
            onRecipeChange = { next ->
                viewModel.setPromptSubject(next.subject)
                viewModel.setPromptSetting(next.setting)
                viewModel.setPromptMood(next.mood)
                viewModel.setPromptLighting(next.lighting)
                viewModel.setPromptComposition(next.composition)
                viewModel.setPromptFinish(next.finish)
            },
            onToggleModifier = viewModel::toggleStyleModifier,
            onReset = viewModel::clearPromptDirector,
            onDismiss = { showPromptDirector = false },
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

private fun formatFeedTime(epochMs: Long): String {
    val date = java.util.Date(epochMs)
    val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}
