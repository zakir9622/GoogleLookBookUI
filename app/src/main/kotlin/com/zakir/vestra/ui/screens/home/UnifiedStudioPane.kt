package com.zakir.vestra.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.zakir.vestra.ui.components.PromptComposer
import com.zakir.vestra.ui.components.ResultPane
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a pack-readiness probe off the UI thread.
 *
 * The probes stat files on disk, so calling them directly from a composable body put
 * file-system work on the main thread on every recomposition. [keys] re-runs the probe when the
 * installed packs change or a generation starts/finishes, which is the only time the answer can
 * actually differ.
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

    // These each stat pack files on disk. Called straight from the composable body they ran on
    // the main thread on every recomposition — and ResultPane ticks once a second while a
    // generation is running, so that was five file-system probes per second on the UI thread.
    // Hoist them onto Dispatchers.IO and recompute only when the installed packs change.
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
    // Cloud rows must disappear entirely when the master toggle is off — otherwise the picker
    // offers models that preflight and the runtime gate will refuse to run.
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

    // Picking a model should load it, not defer the cost to the first prompt.
    LaunchedEffect(selectedId, effectiveCapability) {
        viewModel.warmUpLocal(effectiveCapability)
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.setReference(uri?.toString())
    }

    val subtitle = when (capability) {
        AiCapability.IMAGE_GEN -> LookbookCopy.STUDIO_IMAGE
        AiCapability.VIDEO -> LookbookCopy.STUDIO_VIDEO
        AiCapability.CODE -> LookbookCopy.STUDIO_CODE
        else -> "Studio"
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

    // Two regions, not one long scroll: generated content scrolls in the top region while the
    // composer stays docked at the bottom of the screen. Previously everything lived in a single
    // verticalScroll column, so the composer drifted mid-scroll and results pushed it off-screen.
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
        GlassSectionLabel(
            subtitle.uppercase(),
            color = when (capability) {
                AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT, AiCapability.TRY_ON -> VestraColors.ModalityImage
                AiCapability.VIDEO -> VestraColors.ModalityVideo
                AiCapability.CODE -> VestraColors.ModalityCode
                AiCapability.AUDIO -> VestraColors.ModalityAudio
            },
        )
        Text(
            when (capability) {
                AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT ->
                    when {
                        reference != null && localImageEditReady ->
                            "Local img2img ready offline — Edit runs on-device."
                        reference == null && localImageReady ->
                            "Local tiny-SD ready offline — Create Studio runs on-device."
                        cloudModelsEnabled ->
                            "Cloud, until you download a local pack. Settings → Model packs → local-sdturbo-v1 (~1.06 GB) for offline."
                        else ->
                            "On-device only (cloud is off). Download local-sdturbo-v1 (~1.06 GB) in Settings → Model packs to generate."
                    }
                AiCapability.VIDEO ->
                    when {
                        localVideoReady ->
                            "Local still-clip ready — short on-device MP4 from tiny-SD (not diffusion video)."
                        cloudModelsEnabled ->
                            "Cloud HF Spaces, until you download local-sdturbo-v1 for offline still-clips."
                        else ->
                            "On-device only (cloud is off). Download local-sdturbo-v1 in Settings → Model packs to generate."
                    }
                AiCapability.AUDIO ->
                    "Device TTS works offline + voice-changer knobs. Cloud TTS optional."
                AiCapability.CODE ->
                    when {
                        localCodeReady ->
                            "Local Gemma 4 / legacy Gemma ready offline — Code Studio runs on-device."
                        cloudModelsEnabled ->
                            "Cloud, until you download a local pack. local-gemma-4-e2b-v1 (~2.6 GB) for offline."
                        else ->
                            "On-device only (cloud is off). Download local-gemma-4-e2b-v1 (~2.6 GB) in Settings → Model packs to generate."
                    }
                else -> estimate
            },
            style = MaterialTheme.typography.bodySmall,
            color = VestraColors.InkMuted,
        )
        Spacer(Modifier.height(4.dp))
        // FlowRow, not Row: `estimate` can be a long provider sentence, and in a plain Row it
        // consumed the full width and squeezed the chips beside it down to one-character-wide
        // columns of vertical text. FlowRow wraps them onto the next line instead.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                estimate,
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (preflightChip != null && preflight == null) {
                GlassPill(text = preflightChip, active = true)
            }
            val lastUsedName = lastUsedId?.let { id -> CloudModelCatalog.byId(id)?.displayName }
            if (lastUsedName != null) {
                Text(
                    "Last: $lastUsedName",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Model load state, right where the user is looking after picking a model. A multi-GB
        // pack takes seconds to a minute to initialize; saying so beats an unexplained pause,
        // which is what the Gallery app gets right and this app did not.
        when (val w = warmup) {
            is GenerativeViewModel.Warmup.Loading -> {
                Spacer(Modifier.height(10.dp))
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = VestraColors.Accent,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Initializing ${w.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = VestraColors.Ink,
                            )
                            Text(
                                "First load only — this can take up to a minute.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VestraColors.InkMuted,
                            )
                        }
                    }
                }
            }
            is GenerativeViewModel.Warmup.Ready -> {
                Spacer(Modifier.height(10.dp))
                GlassPill(text = "${w.label} · loaded and ready", active = true)
            }
            is GenerativeViewModel.Warmup.Failed -> {
                Spacer(Modifier.height(10.dp))
                GlassErrorBanner(
                    message = "${w.label} could not load: ${w.reason}",
                    onRetry = { viewModel.warmUpLocal(effectiveCapability) },
                    retryLabel = "Retry load",
                    onDismiss = null,
                )
            }
            GenerativeViewModel.Warmup.Idle -> Unit
        }

        Spacer(Modifier.height(8.dp))
        AdvancedAssistSection(
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded },
            busy = busy,
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

        if (examples.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "EXAMPLES",
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.Accent,
            )
            Spacer(Modifier.height(6.dp))
            ExamplePromptRow(
                examples = examples,
                enabled = !busy,
                onPick = viewModel::setPrompt,
            )
        }

        if (preflight != null) {
            Spacer(Modifier.height(12.dp))
            GlassErrorBanner(
                message = preflight!!,
                onRetry = onOpenSettings ?: { showModelPicker = true },
                retryLabel = if (onOpenSettings != null) LookbookCopy.ACTION_OPEN_SETTINGS else "Choose model",
                onDismiss = { viewModel.clearResult() },
            )
        }

        Spacer(Modifier.height(12.dp))
        if (viewModel.resultBelongsTo(effectiveCapability) || viewModel.resultBelongsTo(capability)) {
            val failedMsg = (state as? GenerativeState.Failed)?.message.orEmpty()
            val quotaOrCredits = failedMsg.contains("ZeroGPU", ignoreCase = true) ||
                failedMsg.contains("monthly credits", ignoreCase = true) ||
                failedMsg.contains("Inference Providers", ignoreCase = true)
            ResultPane(
                state = state,
                liveLog = liveLog,
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
        Spacer(Modifier.height(12.dp))
        }

        // Docked composer — outside the scroll region so prompt, model pill, reference
        // picker and send stay reachable no matter how long the result gets.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp, top = 4.dp),
        ) {
            PromptComposer(
                prompt = prompt,
                onPromptChange = viewModel::setPrompt,
                modelLabel = when {
                    effectiveCapability == AiCapability.IMAGE_GEN && localImageReady && reference == null ->
                        "Local tiny-SD (offline)"
                    effectiveCapability == AiCapability.IMAGE_EDIT && localImageEditReady ->
                        "Local img2img (offline)"
                    effectiveCapability == AiCapability.CODE && localCodeReady ->
                        "Local Gemma (offline)"
                    effectiveCapability == AiCapability.VIDEO && localVideoReady ->
                        "Local still-clip (offline)"
                    else -> provider.displayName
                },
                assistCount = assistCount,
                busy = busy,
                enabled = true,
                onModelClick = { showModelPicker = true },
                onAssistsClick = { advancedExpanded = !advancedExpanded },
                onSend = ::onGenerate,
                onStop = { viewModel.forceStop() },
                placeholder = placeholder,
                referenceUri = if (capability == AiCapability.IMAGE_GEN) reference else null,
                onAddReference = if (capability == AiCapability.IMAGE_GEN) {
                    {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                } else {
                    null
                },
                onClearReference = if (capability == AiCapability.IMAGE_GEN) {
                    { viewModel.setReference(null) }
                } else {
                    null
                },
            )
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            title = when (effectiveCapability) {
                AiCapability.IMAGE_EDIT -> "Image edit models"
                AiCapability.IMAGE_GEN -> "Image models"
                AiCapability.VIDEO -> "Video models"
                AiCapability.CODE -> "Coding models"
                else -> "Models"
            } + if (cloudModelsEnabled) "" else " · on-device",
            models = pickerModels,
            selectedId = selectedId,
            onDeviceEntries = onDeviceEntries,
            health = viewModel.appSettings.modelHealth,
            onSelect = { chosen ->
                when (effectiveCapability) {
                    AiCapability.IMAGE_EDIT -> viewModel.appSettings.setImageEditProvider(chosen.id)
                    AiCapability.IMAGE_GEN -> viewModel.appSettings.setImageGenProvider(chosen.id)
                    AiCapability.VIDEO -> viewModel.appSettings.setVideoProvider(chosen.id)
                    AiCapability.CODE -> viewModel.appSettings.setCodeProvider(chosen.id)
                    else -> Unit
                }
            },
            onSelectDevice = { entry ->
                if (!entry.ready) return@ModelPickerSheet
                when (effectiveCapability) {
                    AiCapability.IMAGE_EDIT,
                    AiCapability.IMAGE_GEN,
                    AiCapability.VIDEO,
                    AiCapability.CODE,
                    -> viewModel.appSettings.setLocalGenerator(effectiveCapability, entry.id)
                    else -> Unit
                }
            },
            onDismiss = { showModelPicker = false },
        )
    }
}

@Composable
private fun AdvancedAssistSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    busy: Boolean,
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
    GlassCard(onClick = onToggle) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleMedium,
                color = VestraColors.Ink,
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse advanced options" else "Expand advanced options",
                tint = VestraColors.Accent,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                when (capability) {
                    AiCapability.CODE -> {
                        GlassOptionToggle(
                            text = LookbookCopy.ASSIST_PRAGMATIC,
                            active = pragmatic,
                            enabled = !busy,
                            onToggle = onPragmatic,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassOptionToggle(
                            text = LookbookCopy.ASSIST_CREATIVE,
                            active = creative,
                            enabled = !busy,
                            onToggle = onCreative,
                        )
                    }
                    AiCapability.AUDIO -> {
                        // Only fashion framing is applied to the spoken script.
                        GlassOptionToggle(
                            text = LookbookCopy.ASSIST_FASHION,
                            active = fashionContext,
                            enabled = !busy,
                            onToggle = onFashionContext,
                        )
                    }
                    AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT, AiCapability.VIDEO -> {
                        GlassOptionToggle(
                            text = LookbookCopy.ASSIST_EDITORIAL,
                            active = bypassFilter,
                            enabled = !busy,
                            onToggle = onBypassFilter,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassOptionToggle(
                            text = LookbookCopy.ASSIST_FASHION,
                            active = fashionContext,
                            enabled = !busy,
                            onToggle = onFashionContext,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassOptionToggle(
                            text = LookbookCopy.ASSIST_DETAIL,
                            active = detailBoost,
                            enabled = !busy,
                            onToggle = onDetailBoost,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassOptionToggle(
                            text = LookbookCopy.ASSIST_QUALITY,
                            active = qualityGuard,
                            enabled = !busy,
                            onToggle = onQualityGuard,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassOptionToggle(
                            text = "Analyze reference (offline vision)",
                            active = analyzeReference,
                            enabled = !busy && localVisionReady,
                            onToggle = onAnalyzeReference,
                        )
                        if (!localVisionReady) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Install local-gemma-4-e2b-v1 for offline reference analysis.",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.InkMuted,
                            )
                        }
                        // Steps / CFG / Seed are not exposed: cloud Space + HF Inference
                        // payloads ignore them; showing them lied about what the model receives.
                    }
                    else -> Unit
                }
            }
        }
    }
}
