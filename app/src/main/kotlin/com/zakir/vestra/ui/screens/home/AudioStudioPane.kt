package com.zakir.vestra.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zakir.vestra.shared.audio.AndroidMicRecorder
import com.zakir.vestra.audio.AudioClip
import com.zakir.vestra.audio.AudioClipLibrary
import com.zakir.vestra.ui.components.AudioClipList
import com.zakir.vestra.media.MediaExport
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
import com.zakir.vestra.ui.components.ExamplePromptRow
import com.zakir.vestra.ui.components.GlassPill
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.components.PromptComposer
import com.zakir.vestra.ui.components.ResultPane
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File

/**
 * Audio Studio — TTS with voice personas + local voice-changer knobs + mic record.
 * Cloud TTS by default; local TTS pack scaffolded; DSP knobs always on-device.
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
    // Produced-clip list. Rescanned whenever generation state changes so a new recording,
    // conversion or TTS result appears without the user leaving the tab. The scan reads file
    // metadata, so it stays off the UI thread.
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

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        GlassSectionLabel(LookbookCopy.STUDIO_AUDIO.uppercase())
        Text(
            "Device TTS works offline (system voices + knobs). Offline transcription isn't available yet " +
                "— the published Gemma 4 pack doesn't include audio support. Cloud TTS optional.",
            style = MaterialTheme.typography.bodySmall,
            color = VestraColors.InkMuted,
        )
        if (preflight != null) {
            Spacer(Modifier.height(6.dp))
            GlassPill(text = preflight!!, active = true)
        }
        if (recordHint != null) {
            Spacer(Modifier.height(6.dp))
            GlassPill(text = recordHint!!, active = isRecording || reference != null)
        }

        Spacer(Modifier.height(12.dp))
        Text("VOICE PERSONA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
        Text("LIVE VOICE CHANGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Record → apply knobs → play. Continuous streaming DSP is not in this build.",
            style = MaterialTheme.typography.labelSmall,
            color = VestraColors.InkMuted,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AtelierFilterChip(
                selected = isRecording,
                onClick = { toggleMic() },
                label = { Text(if (isRecording) "Stop mic" else "Record mic") },
            )
            AtelierFilterChip(
                selected = false,
                onClick = {
                    if (!busy && reference != null) viewModel.applyVoiceChange()
                },
                label = { Text("Apply voice change") },
            )
            AtelierFilterChip(
                selected = false,
                onClick = {
                    if (!busy && reference != null) viewModel.transcribeAudio()
                },
                label = { Text("Transcribe (offline)") },
            )
            if (reference != null) {
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
        if (reference != null) {
            Text(
                "Clip ready · ${reference!!.substringAfterLast('/')}",
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.Accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("VOICE CHANGER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Local DSP · pitch · speed · formant · warmth · clarity",
            style = MaterialTheme.typography.labelSmall,
            color = VestraColors.InkMuted,
        )
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

        Spacer(Modifier.height(12.dp))
        ExamplePromptRow(
            examples = listOf(
                "Welcome to The Lookbook atelier.",
                "This abaya drapes in soft black silk.",
                "Shop the new hijab collection today.",
            ),
            enabled = !busy,
            onPick = viewModel::setPrompt,
        )
        Spacer(Modifier.height(8.dp))
        PromptComposer(
            prompt = prompt,
            onPromptChange = viewModel::setPrompt,
            modelLabel = if (localAudioReady) "Device TTS (offline)" else provider.displayName,
            onModelClick = { showModelPicker = true },
            busy = busy,
            enabled = prompt.isNotBlank() || reference != null,
            onSend = {
                if (reference != null && (prompt.isBlank() || prompt.equals("voice-change", true))) {
                    viewModel.applyVoiceChange()
                } else {
                    viewModel.generateAudio()
                }
            },
            onStop = viewModel::cancel,
            placeholder = "Script for ${VoiceCatalog.byId(personaId).displayName}…",
        )
        Spacer(Modifier.height(8.dp))
        ResultPane(
            state = state,
            liveLog = liveLog,
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

        Spacer(Modifier.height(18.dp))
        GlassSectionLabel("CLIPS")
        Text(
            "Recordings, voice-changed results and generated speech — play them here to compare " +
                "an original against its conversion.",
            style = MaterialTheme.typography.bodySmall,
            color = VestraColors.InkMuted,
        )
        Spacer(Modifier.height(8.dp))
        AudioClipList(
            clips = clips,
            onShare = { clip ->
                MediaExport.share(context, File(clip.path), "Share audio")
            },
            onDelete = { clip ->
                if (AudioClipLibrary.delete(clip)) clips = clips.filterNot { it.path == clip.path }
            },
        )
        Spacer(Modifier.height(24.dp))
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
    Column(Modifier.padding(top = 6.dp)) {
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
