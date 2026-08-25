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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zakir.vestra.audio.AudioClip
import com.zakir.vestra.audio.AudioClipLibrary
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.shared.audio.AndroidMicRecorder
import com.zakir.vestra.shared.audio.VoiceCatalog
import com.zakir.vestra.shared.audio.VoiceKnobs
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
    val packStates by packManager?.states?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    val provider = viewModel.appSettings.selectedProvider(AiCapability.AUDIO)
    val busy = state is GenerativeState.Running || state is GenerativeState.Preparing
    var showModelPicker by remember { mutableStateOf(false) }
    var showKnobsPanel by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val micRecorder = remember {
        AndroidMicRecorder(File(context.cacheDir, "audio_recordings").also { it.mkdirs() })
    }
    var isRecording by remember { mutableStateOf(false) }
    var recordHint by remember { mutableStateOf<String?>(null) }

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
                recordHint = "Clip saved — adjust knobs, then Apply voice change"
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

    val pickerModels = remember(freeCloudDiscovery) {
        freeCloudDiscovery?.selectable(viewModel.appSettings, AiCapability.AUDIO)
            ?: CloudModelCatalog.forCapability(AiCapability.AUDIO)
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

    val modelDisplayLabel = if (localAudioReady) "Device TTS (offline)" else provider.displayName
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VestraColors.Canvas),
    ) {
        // Scrollable Middle Canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            // TOP SECTION MODULE: Header & Model Initialization Card
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
                            text = "AUDIO STUDIO MODULE",
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
                    text = "Generates studio narration and speech waveforms with local DSP pitch/formant shifting and offline device TTS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VestraColors.InkMuted,
                )

                Spacer(Modifier.height(8.dp))
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
                        text = "$modelDisplayLabel · initialized & ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.Ink,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (preflight != null) {
                GlassErrorBanner(
                    message = preflight!!,
                    onRetry = onOpenSettings ?: { showModelPicker = true },
                    retryLabel = "Configure audio",
                    onDismiss = { viewModel.clearResult() },
                )
                Spacer(Modifier.height(10.dp))
            }

            // MIDDLE SECTION: Voice Personas & Real-Time DSP Knobs Card
            GlassCard {
                Text(
                    "VOICE PERSONAS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = VestraColors.Accent,
                )
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
                            "DSP VOICE CHANGING KNOBS",
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
                            viewModel.setVoiceKnobs(knobs.copy(pitchSemitones = it))
                        }
                        KnobSlider("Speed", knobs.speed, 0.5f..2f, "%.2f×") {
                            viewModel.setVoiceKnobs(knobs.copy(speed = it))
                        }
                        KnobSlider("Formant", knobs.formant, 0.5f..1.5f, "%.2f") {
                            viewModel.setVoiceKnobs(knobs.copy(formant = it))
                        }
                        KnobSlider("Warmth", knobs.warmth, 0f..1f, "%.2f") {
                            viewModel.setVoiceKnobs(knobs.copy(warmth = it))
                        }
                        KnobSlider("Clarity", knobs.clarity, 0f..1f, "%.2f") {
                            viewModel.setVoiceKnobs(knobs.copy(clarity = it))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AtelierFilterChip(
                                selected = false,
                                onClick = { viewModel.setVoiceKnobs(VoiceKnobs.Default) },
                                label = { Text("Reset knobs") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AtelierFilterChip(
                        selected = isRecording,
                        onClick = { toggleMic() },
                        label = { Text(if (isRecording) "Stop mic" else "Record mic") },
                    )
                    if (reference != null) {
                        AtelierFilterChip(
                            selected = false,
                            onClick = {
                                if (!busy) viewModel.applyVoiceChange()
                            },
                            label = { Text("Apply voice change") },
                        )
                        AtelierFilterChip(
                            selected = false,
                            onClick = {
                                viewModel.setReference(null)
                                micRecorder.clear()
                                recordHint = null
                            },
                            label = { Text("Clear clip") },
                        )
                    }
                }
                if (recordHint != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        recordHint!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.Accent,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Generation Result Deliverable Stream
            if (viewModel.resultBelongsTo(AiCapability.AUDIO) && state != null) {
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
                    GlassSectionLabel("GENERATED CLIPS")
                    Spacer(Modifier.height(6.dp))
                    AudioClipList(
                        clips = clips,
                        onShare = { clip ->
                            MediaExport.share(context, File(clip.path), "Share audio")
                        },
                        onDelete = { clip ->
                            if (AudioClipLibrary.delete(clip)) clips = clips.filterNot { it.path == clip.path }
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
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            title = "Audio models",
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
