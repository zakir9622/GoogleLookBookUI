package com.zakir.vestra.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zakir.vestra.audio.AudioClip
import com.zakir.vestra.audio.AudioClipLibrary
import com.zakir.vestra.audio.AudioImportHelper
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.shared.audio.AndroidMicRecorder
import com.zakir.vestra.shared.audio.VoiceCatalog
import com.zakir.vestra.shared.audio.VoiceEffectPreset
import com.zakir.vestra.shared.audio.VoiceKnobs
import com.zakir.vestra.shared.audio.VoicePresets
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.components.AtelierFilterChip
import com.zakir.vestra.ui.components.AudioClipList
import com.zakir.vestra.ui.components.ExamplePromptRow
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassErrorBanner
import com.zakir.vestra.ui.components.GlassPill
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.components.PromptComposer
import com.zakir.vestra.ui.components.QuickPromptItem
import com.zakir.vestra.ui.components.ResultPane
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Audio Studio Module — TTS with voice personas + local voice-changer DSP knobs + mic record.
 */
@Composable
fun AudioStudioPane(
    viewModel: GenerativeViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    freeCloudDiscovery: FreeCloudDiscovery? = null,
    packManager: ModelPackManager? = null,
) {
    LaunchedEffect(Unit) {
        viewModel.bindStudio(AiCapability.AUDIO)
    }

    val prompt by viewModel.prompt.collectAsState()
    val state by viewModel.state.collectAsState()
    val liveLog by viewModel.liveLog.collectAsState()
    val generationStartedAtMs by viewModel.generationStartedAtMs.collectAsState()
    val preflight by viewModel.preflightMessage.collectAsState()
    val personaId by viewModel.voicePersonaId.collectAsState()
    val knobs by viewModel.voiceKnobs.collectAsState()
    val reference by viewModel.referenceUri.collectAsState()
    val audioId by viewModel.appSettings.audioProviderId.collectAsState()
    val warmup by viewModel.warmup.collectAsState()
    val packStates by packManager?.states?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    val provider = viewModel.appSettings.selectedProvider(AiCapability.AUDIO)
    val busy = state is GenerativeState.Running || state is GenerativeState.Preparing
    var showModelPicker by remember { mutableStateOf(false) }
    var showKnobsPanel by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val micRecorder = remember {
        AndroidMicRecorder(File(context.cacheDir, "audio_recordings").also { it.mkdirs() })
    }
    var isRecording by remember { mutableStateOf(false) }
    var recordHint by remember { mutableStateOf<String?>(null) }
    var selectedPresetId by remember { mutableStateOf<String?>("natural") }

    val audioFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            recordHint = "Importing audio file…"
            scope.launch {
                val path = AudioImportHelper.copyUriToCache(context, uri)
                if (path != null) {
                    viewModel.setReference(path)
                    recordHint = "Audio imported: ${File(path).name} — select effect preset and tap Apply"
                } else {
                    recordHint = "Could not import audio file."
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (micRecorder.isRecording) micRecorder.stop()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (micRecorder.start()) {
                isRecording = true
                recordHint = "Recording… tap Stop when done (max 15s)"
            } else {
                recordHint = micRecorder.lastFailure ?: "Could not start recording"
            }
        } else {
            recordHint = "Microphone permission is required to record voice."
        }
    }

    fun toggleMic() {
        if (busy) return
        if (isRecording) {
            val path = micRecorder.stop()
            isRecording = false
            if (path != null) {
                viewModel.setReference(path)
                recordHint = "Recording saved — adjust knobs or preset, then tap Apply voice change"
            } else {
                recordHint = micRecorder.lastFailure ?: "Recording failed"
            }
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                if (micRecorder.start()) {
                    isRecording = true
                    recordHint = "Recording… tap Stop when done (max 15s)"
                } else {
                    recordHint = micRecorder.lastFailure ?: "Could not start recording"
                }
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val cloudModelsEnabled by viewModel.appSettings.cloudModelsEnabled.collectAsState()
    val pickerModels = remember(freeCloudDiscovery, cloudModelsEnabled) {
        if (!cloudModelsEnabled) {
            emptyList()
        } else {
            freeCloudDiscovery?.selectable(viewModel.appSettings, AiCapability.AUDIO)
                ?: CloudModelCatalog.forCapability(AiCapability.AUDIO)
        }
    }

    var clips by remember { mutableStateOf<List<AudioClip>>(emptyList()) }
    LaunchedEffect(state) {
        clips = withContext(Dispatchers.IO) {
            AudioClipLibrary.scan(
                listOf(
                    File(context.filesDir, "generations"),
                    File(context.cacheDir, "audio_recordings"),
                ),
            )
        }
    }

    var localAudioReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        localAudioReady = withContext(Dispatchers.Default) {
            viewModel.localAudioOfflineReady()
        }
    }
    val onDeviceEntries = remember(packStates, localAudioReady) {
        LocalModelCatalog.forStudioPicker(AiCapability.AUDIO).map { entry ->
            val packReady = entry.packId?.let { packStates[it]?.isReady() == true } == true
            OnDevicePickerEntry(
                id = entry.id,
                displayName = entry.displayName,
                detail = entry.testingNote,
                ready = LocalModelCatalog.studioEntryReady(entry, packReady),
                statusLabel = LocalModelCatalog.studioStatusLabel(entry, packReady),
            )
        }
    }

    val modelDisplayLabel = if (!cloudModelsEnabled || localAudioReady) "Device TTS (offline)" else provider.displayName
    val scrollState = rememberScrollState()
    val feedItems by viewModel.feedItems.collectAsState()

    // Auto-scroll to bottom on new audio generations
    LaunchedEffect(feedItems.size, (state as? GenerativeState.Running)?.stage, (state as? GenerativeState.AudioReady)?.path) {
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
                    text = "AUDIO FEED · ${feedItems.size} ${if (feedItems.size == 1) "turn" else "turns"}",
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

        // Scrollable Middle Generation Canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (preflight != null) {
                GlassErrorBanner(
                    message = preflight!!,
                    onRetry = onOpenSettings ?: { showModelPicker = true },
                    retryLabel = "Configure audio",
                    onDismiss = { viewModel.clearResult() },
                )
                Spacer(Modifier.height(10.dp))
            }

            // MIDDLE SECTION: Voice Personas, Presets & Real-Time DSP Knobs Card
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "VOICE PERSONAS (16 VOICES)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = VestraColors.Accent,
                    )
                    Text(
                        VoiceCatalog.byId(personaId).variety.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.InkMuted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VoiceCatalog.personas) { persona ->
                        AtelierFilterChip(
                            selected = personaId == persona.id,
                            onClick = { viewModel.setVoicePersona(persona.id) },
                            label = { Text(persona.displayName) },
                        )
                    }
                }
                Text(
                    VoiceCatalog.byId(personaId).description,
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.InkMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(14.dp))

                // VOICE EFFECTS & PRESETS
                Text(
                    "VOICE CHANGER PRESETS (12 EFFECTS)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = VestraColors.Accent,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VoicePresets.presets) { preset ->
                        AtelierFilterChip(
                            selected = selectedPresetId == preset.id,
                            onClick = {
                                selectedPresetId = preset.id
                                viewModel.setVoiceKnobs(preset.knobs)
                            },
                            label = { Text("${preset.iconEmoji} ${preset.displayName}") },
                        )
                    }
                }
                selectedPresetId?.let { id ->
                    val preset = VoicePresets.byId(id)
                    Text(
                        "${preset.displayName} — ${preset.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showKnobsPanel = !showKnobsPanel },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = VestraColors.Accent,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "FINE-TUNE DSP KNOBS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = VestraColors.Ink,
                        )
                    }
                    Icon(
                        if (showKnobsPanel) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = VestraColors.InkMuted,
                    )
                }

                AnimatedVisibility(
                    visible = showKnobsPanel,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(Modifier.padding(top = 8.dp)) {
                        KnobSlider("Pitch (semitones)", knobs.pitchSemitones, -12f..12f, "%.0f") {
                            selectedPresetId = null
                            viewModel.setVoiceKnobs(knobs.copy(pitchSemitones = it))
                        }
                        KnobSlider("Speed", knobs.speed, 0.5f..2f, "%.2f×") {
                            selectedPresetId = null
                            viewModel.setVoiceKnobs(knobs.copy(speed = it))
                        }
                        KnobSlider("Formant", knobs.formant, 0.5f..1.5f, "%.2f") {
                            selectedPresetId = null
                            viewModel.setVoiceKnobs(knobs.copy(formant = it))
                        }
                        KnobSlider("Warmth", knobs.warmth, 0f..1f, "%.2f") {
                            selectedPresetId = null
                            viewModel.setVoiceKnobs(knobs.copy(warmth = it))
                        }
                        KnobSlider("Clarity", knobs.clarity, 0f..1f, "%.2f") {
                            selectedPresetId = null
                            viewModel.setVoiceKnobs(knobs.copy(clarity = it))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AtelierFilterChip(
                                selected = false,
                                onClick = {
                                    selectedPresetId = "natural"
                                    viewModel.setVoiceKnobs(VoiceKnobs.Default)
                                },
                                label = { Text("Reset knobs") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // AUDIO INPUT & VOICE CHANGER DOCK
                Text(
                    "RECORDED AUDIO INPUT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = VestraColors.Accent,
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AtelierFilterChip(
                        selected = isRecording,
                        onClick = { toggleMic() },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isRecording) "Stop mic" else "Record mic")
                            }
                        },
                    )
                    AtelierFilterChip(
                        selected = false,
                        onClick = {
                            audioFilePickerLauncher.launch("audio/*")
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Select audio file")
                            }
                        },
                    )
                }

                // Active Audio Reference Card
                if (reference != null) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VestraColors.Canvas)
                            .border(1.dp, VestraColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = VestraColors.Accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "ACTIVE VOICE INPUT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 9.sp,
                                        ),
                                        color = VestraColors.Accent,
                                    )
                                    Text(
                                        File(reference!!).name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VestraColors.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    viewModel.setReference(null)
                                    micRecorder.clear()
                                    recordHint = null
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Clear clip",
                                    tint = VestraColors.InkMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AtelierFilterChip(
                                selected = true,
                                onClick = {
                                    if (!busy) viewModel.applyVoiceChange()
                                },
                                label = {
                                    Text(if (busy) "Processing…" else "✨ Apply voice changer")
                                },
                            )
                        }
                    }
                }

                if (recordHint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        recordHint!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.Accent,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Conversational Audio Stream Deliverables
            if (feedItems.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    feedItems.forEach { feedItem ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // User Message Bubble (Prompt or Voice Transform)
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
                                    feedItem.referenceUri?.let { uri ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.GraphicEq,
                                                contentDescription = null,
                                                tint = VestraColors.Accent,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = File(uri).name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VestraColors.Accent,
                                            )
                                        }
                                    }

                                    Text(
                                        text = feedItem.prompt.ifBlank { "Generate voice" },
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
                                            text = formatAudioFeedTime(feedItem.timestampMs),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = VestraColors.InkMuted,
                                        )
                                    }
                                }
                            }

                            // AI Audio Deliverable Pane for this turn
                            ResultPane(
                                state = feedItem.state,
                                liveLog = emptyList(),
                                generationStartedAtMs = feedItem.generationStartedAtMs,
                                onRetry = {
                                    if (feedItem.referenceUri != null && feedItem.prompt.equals("voice-change", true)) {
                                        viewModel.setReference(feedItem.referenceUri)
                                        viewModel.applyVoiceChange()
                                    } else {
                                        viewModel.setPrompt(feedItem.prompt)
                                        viewModel.generateAudio()
                                    }
                                },
                                onDismiss = { viewModel.removeFeedItem(feedItem.id) },
                                onCancel = { viewModel.forceStop() },
                                retryLabel = "Speak again",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else if (viewModel.resultBelongsTo(AiCapability.AUDIO) && state != null) {
                ResultPane(
                    state = state,
                    liveLog = emptyList(), // Live logs rendered in persistent bottom dock
                    generationStartedAtMs = generationStartedAtMs,
                    onRetry = {
                        if (reference != null && prompt.equals("voice-change", true)) {
                            viewModel.applyVoiceChange()
                        } else {
                            viewModel.generateAudio()
                        }
                    },
                    onDismiss = { viewModel.forceStop(showStopped = false) },
                    onCancel = { viewModel.forceStop() },
                    retryLabel = "Speak again",
                )
                Spacer(Modifier.height(10.dp))
            }

            // Produced Clips Audio Library
            if (clips.isNotEmpty()) {
                GlassCard {
                    GlassSectionLabel("GENERATED & RECORDED AUDIO")
                    Spacer(Modifier.height(6.dp))
                    AudioClipList(
                        clips = clips,
                        onShare = { clip ->
                            MediaExport.share(context, File(clip.path), "Share audio")
                        },
                        onDelete = { clip ->
                            if (AudioClipLibrary.delete(clip)) clips = clips.filterNot { it.path == clip.path }
                        },
                        onSelectForVoiceChanger = { clip ->
                            viewModel.setReference(clip.path)
                            recordHint = "Selected ${clip.fileName} for voice changer"
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        val audioQuickPrompts = remember(personaId, reference) {
            if (reference != null) {
                listOf(
                    QuickPromptItem("voice-change", "DSP", "Transform recorded mic sample"),
                    QuickPromptItem("Runway introduction with rich bass tone", "Narration"),
                    QuickPromptItem("Atelier couture showcase whisper", "Whisper"),
                )
            } else {
                listOf(
                    QuickPromptItem("Welcome to the Vestra Autumn Lookbook preview.", "Lookbook"),
                    QuickPromptItem("Handcrafted emerald silk abaya with gold zardozi stitching.", "Couture"),
                    QuickPromptItem("Introducing the modest atelier collection for summer.", "Editorial"),
                    QuickPromptItem("On-device generative fashion atelier powered by local AI.", "AI"),
                )
            }
        }

        // BOTTOM PERSISTENT DOCK: Attached Chatbox + Live Telemetry & Countdown Box
        PromptComposer(
            prompt = prompt,
            onPromptChange = viewModel::setPrompt,
            modelLabel = modelDisplayLabel,
            busy = busy,
            enabled = prompt.isNotBlank() || reference != null,
            onModelClick = { showModelPicker = true },
            onSend = {
                if (reference != null && (prompt.isBlank() || prompt.equals("voice-change", true))) {
                    viewModel.applyVoiceChange()
                } else {
                    viewModel.generateAudio()
                }
            },
            onStop = viewModel::cancel,
            placeholder = "Script for ${VoiceCatalog.byId(personaId).displayName}…",
            referenceUri = reference,
            onClearReference = {
                viewModel.setReference(null)
                micRecorder.clear()
            },
            liveLog = liveLog,
            generationStartedAtMs = generationStartedAtMs,
            deadlineEpochMs = (state as? GenerativeState.Running)?.deadlineEpochMs,
            showLiveDock = true,
            quickPrompts = audioQuickPrompts,
            onSelectQuickPrompt = viewModel::setPrompt,
            modelLoading = warmup is GenerativeViewModel.Warmup.Loading,
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            title = if (cloudModelsEnabled) "Audio models" else "Audio models · on-device",
            models = pickerModels,
            selectedId = audioId.ifBlank { provider.id },
            onSelect = {
                viewModel.appSettings.setAudioProvider(it.id)
                showModelPicker = false
            },
            onSelectDevice = { entry ->
                if (!entry.ready) return@ModelPickerSheet
                viewModel.appSettings.setLocalGenerator(AiCapability.AUDIO, entry.id)
            },
            onDismiss = { showModelPicker = false },
            onDeviceEntries = onDeviceEntries,
            health = viewModel.appSettings.modelHealth,
        )
    }
}

@Composable
private fun KnobSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = VestraColors.Ink)
            Text(format.format(value), style = MaterialTheme.typography.labelSmall, color = VestraColors.Accent)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatAudioFeedTime(epochMs: Long): String {
    val date = java.util.Date(epochMs)
    val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}

