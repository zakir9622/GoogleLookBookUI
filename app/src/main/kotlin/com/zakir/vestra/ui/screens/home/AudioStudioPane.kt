package com.zakir.vestra.ui.screens.home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zakir.vestra.audio.AudioClip
import com.zakir.vestra.audio.AudioClipLibrary
import com.zakir.vestra.audio.AudioEditorEngine
import com.zakir.vestra.audio.AudioImportHelper
import com.zakir.vestra.audio.AudioOutputFormat
import com.zakir.vestra.audio.AudioTranscribeHelper
import com.zakir.vestra.audio.CustomVoiceProfile
import com.zakir.vestra.audio.CustomVoiceStorage
import com.zakir.vestra.audio.TranscribeState
import com.zakir.vestra.audio.VocalMode
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.shared.audio.AndroidMicRecorder
import com.zakir.vestra.shared.audio.VoiceCategory
import com.zakir.vestra.shared.audio.VoiceCatalog
import com.zakir.vestra.shared.audio.VoiceEffectPreset
import com.zakir.vestra.shared.audio.VoicePresets
import com.zakir.vestra.shared.audio.VoiceSampleManager
import com.zakir.vestra.shared.audio.VoiceSampleConfig
import com.zakir.vestra.shared.audio.VoiceSampleCaptureState
import com.zakir.vestra.shared.audio.VoiceSampleResult
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.components.AtelierFilterChip
import com.zakir.vestra.ui.components.AudioClipList
import com.zakir.vestra.ui.components.AudioPlayerView
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassErrorBanner
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AudioStudioTab(val title: String, val icon: @Composable () -> Unit) {
    VOICE_CHANGER("Voice Effects", { Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp)) }),
    CUTTER_TRIM("Audio Cutter", { Icon(Icons.Outlined.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp)) }),
    VOCAL_REMOVER("Vocal Remover", { Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) }),
    TRANSCRIBE("Transcribe", { Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) }),
    TTS("Text to Speech", { Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) }),
}

@OptIn(ExperimentalLayoutApi::class)
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(AudioStudioTab.VOICE_CHANGER) }

    val prompt by viewModel.prompt.collectAsState()
    val state by viewModel.state.collectAsState()
    val personaId by viewModel.voicePersonaId.collectAsState()
    val reference by viewModel.referenceUri.collectAsState()
    val audioId by viewModel.appSettings.audioProviderId.collectAsState()
    val provider = viewModel.appSettings.selectedProvider(AiCapability.AUDIO)
    val packStates by (packManager?.states ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    val preflight by viewModel.preflightMessage.collectAsState()

    var showModelPicker by remember { mutableStateOf(false) }
    val micRecorder = remember {
        AndroidMicRecorder(
            outputDir = File(context.cacheDir, "audio_recordings"),
            maxDurationMs = 60_000L,
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableIntStateOf(0) }
    var recordHint by remember { mutableStateOf<String?>(null) }

    // Active output result for instant auto-play
    var activeOutputAudio by remember { mutableStateOf<File?>(null) }
    var activeOutputTitle by remember { mutableStateOf<String?>(null) }
    var activeOutputBadge by remember { mutableStateOf<String?>(null) }
    var isProcessingDsp by remember { mutableStateOf(false) }

    // Transcribe Helper
    val transcribeHelper = remember { AudioTranscribeHelper(context) }
    val transcribeState by transcribeHelper.state.collectAsState()

    // Cutter State
    var cutterStartMs by remember { mutableFloatStateOf(0f) }
    var cutterEndMs by remember { mutableFloatStateOf(15000f) }
    var cutterTotalDurationMs by remember { mutableFloatStateOf(30000f) }
    var cutterFormat by remember { mutableStateOf(AudioOutputFormat.MP3) }
    var cutterCustomName by remember { mutableStateOf("") }
    var cutterWaveform by remember { mutableStateOf<List<Float>>(emptyList()) }

    // Vocal Remover State
    var vocalMode by remember { mutableStateOf(VocalMode.KARAOKE_INSTRUMENTAL) }

    // Voice Sample Manager for Raw PCM Voice Cloning
    val voiceSampleManager = remember {
        VoiceSampleManager(File(context.filesDir, "voice_samples"))
    }
    val sampleCaptureState by voiceSampleManager.state.collectAsState()
    val isSampleRecording by voiceSampleManager.isRecording.collectAsState()
    val sampleAmplitude by voiceSampleManager.amplitudeFlow.collectAsState()
    var sampleQualityReport by remember { mutableStateOf<com.zakir.vestra.shared.audio.VoiceSampleQualityReport?>(null) }

    // Custom Voices State
    var customVoices by remember { mutableStateOf(CustomVoiceStorage.loadProfiles(context.filesDir)) }
    var selectedVoiceCategory by remember { mutableStateOf(VoiceCategory.ALL) }
    var showCreateCustomVoiceDialog by remember { mutableStateOf(false) }
    var customSampleName by remember { mutableStateOf("") }
    var customSampleEmoji by remember { mutableStateOf("🎙️") }
    var customSampleFile by remember { mutableStateOf<File?>(null) }
    var analyzedCustomProfile by remember { mutableStateOf<CustomVoiceProfile?>(null) }
    var isAnalyzingSample by remember { mutableStateOf(false) }

    fun refreshCustomVoices() {
        customVoices = CustomVoiceStorage.loadProfiles(context.filesDir)
    }

    // TTS Script
    var ttsScript by remember { mutableStateOf("") }

    // Clips Library
    var clips by remember { mutableStateOf<List<AudioClip>>(emptyList()) }

    fun refreshClips() {
        scope.launch(Dispatchers.IO) {
            clips = AudioClipLibrary.scan(
                listOf(
                    File(context.filesDir, "generations"),
                    File(context.cacheDir, "audio_recordings"),
                    File(context.cacheDir, "audio_dsp"),
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        refreshClips()
    }

    // Refresh when state changes
    LaunchedEffect(state) {
        if (state is GenerativeState.AudioReady) {
            val audioPath = (state as GenerativeState.AudioReady).path
            activeOutputAudio = File(audioPath)
            activeOutputTitle = "TTS Speech (${VoiceCatalog.byId(personaId).displayName})"
            activeOutputBadge = "Voiceover"
            refreshClips()
        }
    }

    // Update cutter duration when reference changes
    LaunchedEffect(reference) {
        if (reference != null) {
            val file = File(reference!!)
            if (file.exists()) {
                val track = withContext(Dispatchers.IO) { AudioEditorEngine.decodeAudio(file) }
                if (track != null) {
                    cutterTotalDurationMs = track.durationMs.toFloat().coerceAtLeast(1000f)
                    cutterStartMs = 0f
                    cutterEndMs = cutterTotalDurationMs.coerceAtMost(30000f)
                    cutterWaveform = AudioEditorEngine.extractWaveform(file, barCount = 48)
                }
            }
        }
    }

    // Timer for mic recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSeconds++
                if (recordingDurationSeconds >= 60) {
                    // Auto stop after 60s
                    val path = micRecorder.stop()
                    isRecording = false
                    if (path != null) {
                        viewModel.setReference(path)
                        recordHint = "Recording completed (60s limit)"
                        refreshClips()
                    }
                    break
                }
            }
        }
    }

    // File picker launcher
    val audioFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val cachedPath = AudioImportHelper.copyUriToCache(context, uri)
                if (cachedPath != null) {
                    val file = File(cachedPath)
                    viewModel.setReference(file.absolutePath)
                    recordHint = "Loaded audio: ${file.name}"
                    refreshClips()
                } else {
                    recordHint = "Could not load audio file."
                }
            }
        }
    }

    // Audio recording permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (micRecorder.start()) {
                isRecording = true
                recordHint = "Recording… tap Stop when finished"
            } else {
                recordHint = micRecorder.lastFailure ?: "Could not start recording"
            }
        } else {
            recordHint = "Microphone permission is required to record voice."
        }
    }

    fun toggleMic() {
        if (isProcessingDsp) return
        if (isRecording) {
            val path = micRecorder.stop()
            isRecording = false
            if (path != null) {
                viewModel.setReference(path)
                        recordHint = "Recording saved. Choose an effect below to shape its sound."
                refreshClips()
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
                    recordHint = "Recording… tap Stop when finished"
                } else {
                    recordHint = micRecorder.lastFailure ?: "Could not start recording"
                }
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Voice Effects action:
     * Applies DSP effects to a source clip and immediately previews the result. This does not
     * claim to reproduce or clone a real person's identity.
     */
    fun selectAndAutoPlayVoice(preset: VoiceEffectPreset) {
        val inputPath = reference
        if (inputPath == null) {
            recordHint = "Please record your voice or select an audio file first."
            return
        }
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            recordHint = "Audio file missing. Please record again."
            return
        }

        isProcessingDsp = true
        recordHint = "Applying ${preset.displayName} voice effect…"

        scope.launch(Dispatchers.IO) {
            val outputDir = File(context.cacheDir, "audio_dsp")
            val result = AudioEditorEngine.transformVoicePreset(
                inputFile = inputFile,
                preset = preset,
                outputDir = outputDir,
                outputFormat = AudioOutputFormat.MP3,
            )
            withContext(Dispatchers.Main) {
                isProcessingDsp = false
                if (result != null && result.exists()) {
                    activeOutputAudio = result
                    activeOutputTitle = "${preset.iconEmoji} ${preset.displayName}"
                    activeOutputBadge = "Voice effect"
                    recordHint = "Previewing the ${preset.displayName} effect."
                    refreshClips()
                } else {
                    recordHint = "Voice effect processing failed."
                }
            }
        }
    }

    /**
     * Custom voice-effects action:
     * Uses the user's saved sound profile to tailor DSP effects and auto-plays the result.
     * It does not reproduce or clone a real person's vocal identity.
     */
    fun selectAndAutoPlayCustomVoice(profile: CustomVoiceProfile) {
        val inputPath = reference
        if (inputPath == null) {
            recordHint = "Please record your voice or select an audio file first."
            return
        }
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            recordHint = "Audio file missing. Please record again."
            return
        }

        isProcessingDsp = true
        recordHint = "Applying effects using ${profile.name} sound profile…"

        scope.launch(Dispatchers.IO) {
            val outputDir = File(context.cacheDir, "audio_dsp")
            val result = AudioEditorEngine.transformToCustomVoice(
                inputFile = inputFile,
                profile = profile,
                outputDir = outputDir,
                outputFormat = AudioOutputFormat.MP3,
            )
            withContext(Dispatchers.Main) {
                isProcessingDsp = false
                if (result != null && result.exists()) {
                    activeOutputAudio = result
                    activeOutputTitle = "${profile.emoji} ${profile.name}"
                    activeOutputBadge = "Custom effect (${profile.detectedPitchHz.toInt()} Hz)"
                    recordHint = "Previewing ${profile.name} voice effects."
                    refreshClips()
                } else {
                    recordHint = "Custom voice transformation failed."
                }
            }
        }
    }

    fun analyzeAndSetSample(file: File) {
        isAnalyzingSample = true
        scope.launch(Dispatchers.IO) {
            val profile = AudioEditorEngine.analyzeVoiceSample(
                sampleFile = file,
                name = customSampleName.ifBlank { "Custom Voice" },
                emoji = customSampleEmoji,
            )
            withContext(Dispatchers.Main) {
                isAnalyzingSample = false
                analyzedCustomProfile = profile
                if (profile != null && customSampleName.isBlank()) {
                    customSampleName = "Voice Sample (${profile.detectedPitchHz.toInt()} Hz)"
                }
            }
        }
    }

    /**
     * Audio Trimmer Action:
     * Trims segment and saves as MP3/WAV.
     */
    fun cutAndSaveAudio() {
        val inputPath = reference
        if (inputPath == null) {
            recordHint = "Please select or record an audio file to trim."
            return
        }
        val inputFile = File(inputPath)
        if (!inputFile.exists()) return

        isProcessingDsp = true
        recordHint = "Trimming audio and saving as ${cutterFormat.displayName}…"

        scope.launch(Dispatchers.IO) {
            val outputDir = File(context.filesDir, "generations")
            val result = AudioEditorEngine.trimAudio(
                inputFile = inputFile,
                startMs = cutterStartMs.toLong(),
                endMs = cutterEndMs.toLong(),
                outputDir = outputDir,
                outputFormat = cutterFormat,
                customName = cutterCustomName.ifBlank { null },
            )
            withContext(Dispatchers.Main) {
                isProcessingDsp = false
                if (result != null && result.exists()) {
                    activeOutputAudio = result
                    activeOutputTitle = result.name
                    activeOutputBadge = "Trimmed ${cutterFormat.extension.uppercase()}"
                    MediaExport.saveAudioToMusic(context, result, title = result.name, quiet = true)
                    Toast.makeText(context, "Trimmed audio saved to Music!", Toast.LENGTH_SHORT).show()
                    recordHint = "Audio trimmed & saved successfully!"
                    refreshClips()
                } else {
                    recordHint = "Audio trimming failed."
                }
            }
        }
    }

    /**
     * Vocal Remover Action:
     * Processes track with vocal cancellation / isolation.
     */
    fun processVocalRemoval() {
        val inputPath = reference
        if (inputPath == null) {
            recordHint = "Please upload or select a song/audio file first."
            return
        }
        val inputFile = File(inputPath)
        if (!inputFile.exists()) return

        isProcessingDsp = true
        recordHint = "Processing audio with ${vocalMode.title}…"

        scope.launch(Dispatchers.IO) {
            val outputDir = File(context.filesDir, "generations")
            val result = AudioEditorEngine.processVocals(
                inputFile = inputFile,
                outputDir = outputDir,
                mode = vocalMode,
                outputFormat = AudioOutputFormat.MP3,
            )
            withContext(Dispatchers.Main) {
                isProcessingDsp = false
                if (result != null && result.exists()) {
                    activeOutputAudio = result
                    activeOutputTitle = result.name
                    activeOutputBadge = vocalMode.emoji + " " + vocalMode.title
                    MediaExport.saveAudioToMusic(context, result, title = result.name, quiet = true)
                    recordHint = "Audio processed! Playing ${vocalMode.title}"
                    refreshClips()
                } else {
                    recordHint = "Vocal processing failed."
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (micRecorder.isRecording) micRecorder.stop()
            transcribeHelper.stop()
        }
    }

    val cloudModelsEnabled by viewModel.appSettings.cloudModelsEnabled.collectAsState()
    val pickerModels = remember(freeCloudDiscovery, cloudModelsEnabled) {
        freeCloudDiscovery?.selectable(viewModel.appSettings, AiCapability.AUDIO)
            ?: CloudModelCatalog.forCapability(AiCapability.AUDIO)
    }

    val onDeviceEntries = remember(packStates) {
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

    val scrollState = rememberScrollState()

    // Pulse animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VestraColors.Canvas),
    ) {
        // TOP STUDIO TAB NAVIGATION BAR
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AudioStudioTab.values()) { tab ->
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) VestraColors.Accent else VestraColors.SurfaceRaised)
                        .border(
                            1.dp,
                            if (selected) VestraColors.Accent else VestraColors.GlassBorder,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { activeTab = tab }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tab.icon()
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (selected) VestraColors.Canvas else VestraColors.Ink,
                        )
                    }
                }
            }
        }

        // MAIN SCROLLABLE STUDIO WORKSPACE
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

            // ==========================================
            // AUDIO INPUT / RECORDING DECK (COMMON HEADER)
            // ==========================================
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "AUDIO SOURCE & RECORDING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = VestraColors.Accent,
                    )
                    if (isRecording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(VestraColors.Danger),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "REC · ${formatRecordingTime(recordingDurationSeconds)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = VestraColors.Danger,
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Primary Record and Upload Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Big Record Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isRecording) VestraColors.Danger.copy(alpha = 0.15f)
                                else VestraColors.Accent.copy(alpha = 0.12f),
                            )
                            .border(
                                1.5.dp,
                                if (isRecording) VestraColors.Danger else VestraColors.Accent,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { toggleMic() }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Filled.Mic,
                                contentDescription = if (isRecording) "Stop Recording" else "Record Voice",
                                tint = if (isRecording) VestraColors.Danger else VestraColors.Accent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRecording) "Stop (${recordingDurationSeconds}s)" else "Record Mic",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isRecording) VestraColors.Danger else VestraColors.Accent,
                            )
                        }
                    }

                    // Import Audio File Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VestraColors.GlassFill)
                            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(14.dp))
                            .clickable { audioFilePickerLauncher.launch("audio/*") }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Outlined.UploadFile,
                                contentDescription = "Select Audio File",
                                tint = VestraColors.Ink,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Upload Audio",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = VestraColors.Ink,
                            )
                        }
                    }
                }

                // Active Clip Indicator Box
                if (reference != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VestraColors.Canvas)
                            .border(1.dp, VestraColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
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
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "LOADED AUDIO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
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
                                    contentDescription = "Clear audio",
                                    tint = VestraColors.InkMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                if (recordHint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        recordHint!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.Accent,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ==========================================
            // INSTANT AUDIO PLAYER (WHEN OUTPUT READY)
            // ==========================================
            if (activeOutputAudio != null && activeOutputAudio!!.exists()) {
                AudioPlayerView(
                    audioFile = activeOutputAudio!!,
                    title = activeOutputTitle,
                    badgeText = activeOutputBadge,
                    autoPlay = true,
                    showSaveButton = true,
                    showShareButton = true,
                    onSaved = { refreshClips() },
                )
                Spacer(Modifier.height(14.dp))
            } else if (isProcessingDsp) {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = VestraColors.Accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Processing voice transformation…",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.Ink,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ==========================================
            // TAB CONTENT DISPLAY
            // ==========================================
            when (activeTab) {
                // ----------------------------------------------------
                // 1. VOICE CHANGER (CLICK & AUTO-PLAY DIFFERENT VOICES)
                // ----------------------------------------------------
                AudioStudioTab.VOICE_CHANGER -> {
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "VOICE CHANGER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                ),
                                color = VestraColors.Accent,
                            )
                            Text(
                                "Tap voice to auto-play",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.InkMuted,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Category Filter Chips
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(VoiceCategory.values()) { category ->
                                val selected = selectedVoiceCategory == category
                                AtelierFilterChip(
                                    selected = selected,
                                    onClick = { selectedVoiceCategory = category },
                                    label = {
                                        Text("${category.emoji} ${category.displayName}")
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Saved sound-profile action
                        if (selectedVoiceCategory == VoiceCategory.CUSTOM || selectedVoiceCategory == VoiceCategory.ALL) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VestraColors.Accent.copy(alpha = 0.12f))
                                    .border(1.dp, VestraColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        customSampleName = ""
                                        customSampleEmoji = "🎙️"
                                        customSampleFile = null
                                        analyzedCustomProfile = null
                                        showCreateCustomVoiceDialog = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = VestraColors.Accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "Save Sound Profile from Sample",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = VestraColors.Ink,
                                        )
                                        Text(
                                            "Record or upload a short sample to tailor local voice effects",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = VestraColors.InkMuted,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Create Sound Profile",
                                    tint = VestraColors.Accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            // Saved sound-profile grid
                            if (customVoices.isNotEmpty()) {
                                Text(
                                    "SAVED SOUND PROFILES (${customVoices.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = VestraColors.Accent,
                                )
                                Spacer(Modifier.height(6.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    maxItemsInEachRow = 2,
                                ) {
                                    customVoices.forEach { profile ->
                                        val isCurrentOutput = activeOutputTitle?.contains(profile.name) == true
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isCurrentOutput) VestraColors.Accent.copy(alpha = 0.18f)
                                                    else VestraColors.SurfaceRaised,
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isCurrentOutput) VestraColors.Accent else VestraColors.GlassBorder,
                                                    RoundedCornerShape(14.dp),
                                                )
                                                .clickable {
                                                    selectAndAutoPlayCustomVoice(profile)
                                                }
                                                .padding(10.dp),
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        Text(profile.emoji, fontSize = 20.sp)
                                                        Text(
                                                            profile.name,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.SemiBold,
                                                            ),
                                                            color = VestraColors.Ink,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            val updated = customVoices.filterNot { it.id == profile.id }
                                                            customVoices = updated
                                                            CustomVoiceStorage.saveProfiles(context.filesDir, updated)
                                                        },
                                                        modifier = Modifier.size(24.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Delete,
                                                            contentDescription = "Delete profile",
                                                            tint = VestraColors.InkMuted,
                                                            modifier = Modifier.size(14.dp),
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(VestraColors.Accent.copy(alpha = 0.15f))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    ) {
                                                        Text(
                                                            "${profile.detectedPitchHz.toInt()} Hz F0",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            color = VestraColors.Accent,
                                                        )
                                                    }
                                                    Text(
                                                        "Clarity ${(profile.clarity * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = VestraColors.InkMuted,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // Presets Grid
                        if (selectedVoiceCategory != VoiceCategory.CUSTOM) {
                            val filteredPresets = remember(selectedVoiceCategory) {
                                if (selectedVoiceCategory == VoiceCategory.ALL) VoicePresets.presets
                                else VoicePresets.presets.filter { it.category == selectedVoiceCategory }
                            }

                            Text(
                                "${selectedVoiceCategory.displayName.uppercase()} (${filteredPresets.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = VestraColors.Accent,
                            )
                            Spacer(Modifier.height(6.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2,
                            ) {
                                filteredPresets.forEach { preset ->
                                    val isCurrentOutput = activeOutputTitle?.contains(preset.displayName) == true
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isCurrentOutput) VestraColors.Accent.copy(alpha = 0.18f)
                                                else VestraColors.SurfaceRaised,
                                            )
                                            .border(
                                                1.dp,
                                                if (isCurrentOutput) VestraColors.Accent else VestraColors.GlassBorder,
                                                RoundedCornerShape(14.dp),
                                            )
                                            .clickable {
                                                selectAndAutoPlayVoice(preset)
                                            }
                                            .padding(10.dp),
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    preset.iconEmoji,
                                                    fontSize = 20.sp,
                                                )
                                                Text(
                                                    preset.displayName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                    color = VestraColors.Ink,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                preset.description,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = VestraColors.InkMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------
                // 2. AUDIO CUTTER & TRIMMER
                // ----------------------------------------------------
                AudioStudioTab.CUTTER_TRIM -> {
                    GlassCard {
                        Text(
                            "AUDIO CUTTER & TRIMMER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = VestraColors.Accent,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Trim any recording or audio file and save directly as MP3.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.InkMuted,
                        )

                        Spacer(Modifier.height(12.dp))

                        // Waveform selection display
                        if (cutterWaveform.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VestraColors.Canvas)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                cutterWaveform.forEachIndexed { idx, amp ->
                                    val frac = idx.toFloat() / cutterWaveform.size.toFloat()
                                    val startFrac = (cutterStartMs / cutterTotalDurationMs).coerceIn(0f, 1f)
                                    val endFrac = (cutterEndMs / cutterTotalDurationMs).coerceIn(0f, 1f)
                                    val isInRange = frac in startFrac..endFrac

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 1.dp)
                                            .fillMaxSize(amp.coerceIn(0.15f, 1f))
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                if (isInRange) VestraColors.Accent
                                                else VestraColors.InkMuted.copy(alpha = 0.25f),
                                            ),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // Range Slider
                        RangeSlider(
                            value = cutterStartMs..cutterEndMs,
                            onValueChange = { range ->
                                cutterStartMs = range.start
                                cutterEndMs = range.endInclusive
                            },
                            valueRange = 0f..cutterTotalDurationMs,
                            colors = SliderDefaults.colors(
                                thumbColor = VestraColors.Accent,
                                activeTrackColor = VestraColors.Accent,
                                inactiveTrackColor = VestraColors.GlassBorder,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Duration readout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Start: ${formatDurationSeconds(cutterStartMs / 1000f)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ink,
                            )
                            Text(
                                "Trim Duration: ${formatDurationSeconds((cutterEndMs - cutterStartMs) / 1000f)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = VestraColors.Accent,
                                ),
                            )
                            Text(
                                "End: ${formatDurationSeconds(cutterEndMs / 1000f)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ink,
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Custom Name & Format selector
                        OutlinedTextField(
                            value = cutterCustomName,
                            onValueChange = { cutterCustomName = it },
                            label = { Text("Output file name (optional)") },
                            placeholder = { Text("my_trimmed_audio") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VestraColors.Accent,
                                unfocusedBorderColor = VestraColors.GlassBorder,
                            ),
                            singleLine = true,
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AudioOutputFormat.values().forEach { fmt ->
                                    AtelierFilterChip(
                                        selected = cutterFormat == fmt,
                                        onClick = { cutterFormat = fmt },
                                        label = { Text(fmt.extension.uppercase()) },
                                    )
                                }
                            }

                            // Trim Action Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VestraColors.Accent)
                                    .clickable { cutAndSaveAudio() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.ContentCut,
                                        contentDescription = null,
                                        tint = VestraColors.Canvas,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Cut & Save MP3",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = VestraColors.Canvas,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------
                // 3. VOCAL REMOVER (KARAOKE & ACAPELLA)
                // ----------------------------------------------------
                AudioStudioTab.VOCAL_REMOVER -> {
                    GlassCard {
                        Text(
                            "VOCAL REMOVER & KARAOKE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = VestraColors.Accent,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Extract instrumental backing tracks or isolate lead vocals from songs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.InkMuted,
                        )

                        Spacer(Modifier.height(12.dp))

                        // Mode Selector Cards
                        VocalMode.values().forEach { mode ->
                            val isSelected = vocalMode == mode
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) VestraColors.Accent.copy(alpha = 0.15f)
                                        else VestraColors.SurfaceRaised,
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) VestraColors.Accent else VestraColors.GlassBorder,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { vocalMode = mode }
                                    .padding(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(mode.emoji, fontSize = 22.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            mode.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = VestraColors.Ink,
                                        )
                                        Text(
                                            mode.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VestraColors.InkMuted,
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = VestraColors.Accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Execute Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VestraColors.Accent)
                                .clickable { processVocalRemoval() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.MusicNote,
                                    contentDescription = null,
                                    tint = VestraColors.Canvas,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Process Song & Remove Vocals",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = VestraColors.Canvas,
                                    ),
                                )
                            }
                        }
                    }
                }

                // ----------------------------------------------------
                // 4. SPEECH TRANSCRIBE (SPEECH TO TEXT)
                // ----------------------------------------------------
                AudioStudioTab.TRANSCRIBE -> {
                    GlassCard {
                        Text(
                            "SPEECH-TO-TEXT TRANSCRIBER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = VestraColors.Accent,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Generate text transcriptions from spoken voice or audio clips.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.InkMuted,
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VestraColors.Accent)
                                    .clickable {
                                        transcribeHelper.startListening()
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = VestraColors.Canvas,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Transcribe Live Speech",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = VestraColors.Canvas,
                                        ),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Transcription State Result Box
                        when (val trState = transcribeState) {
                            is TranscribeState.Listening -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VestraColors.Accent.copy(alpha = 0.12f))
                                        .border(1.dp, VestraColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = VestraColors.Accent,
                                                strokeWidth = 2.dp,
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                "Listening to voice… speak clearly",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = VestraColors.Accent,
                                            )
                                        }
                                        if (trState.partialText.isNotBlank()) {
                                            Text(
                                                "“${trState.partialText}”",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                                color = VestraColors.Ink,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                            is TranscribeState.Processing -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VestraColors.SurfaceRaised)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(trState.progressHint, color = VestraColors.Ink)
                                }
                            }
                            is TranscribeState.Success -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VestraColors.Canvas)
                                        .border(1.dp, VestraColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "GENERATED TRANSCRIPT · ${trState.wordCount} words",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = VestraColors.Accent,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        cm.setPrimaryClip(ClipData.newPlainText("Transcript", trState.text))
                                                        Toast.makeText(context, "Copied transcript to clipboard!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Filled.ContentCopy,
                                                        contentDescription = "Copy",
                                                        tint = VestraColors.Accent,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = trState.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = VestraColors.Ink,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            AtelierFilterChip(
                                                selected = true,
                                                onClick = {
                                                    ttsScript = trState.text
                                                    activeTab = AudioStudioTab.TTS
                                                },
                                                label = { Text("🗣️ Speak with TTS") },
                                            )
                                        }
                                    }
                                }
                            }
                            is TranscribeState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VestraColors.Danger.copy(alpha = 0.12f))
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        trState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VestraColors.Danger,
                                    )
                                }
                            }
                            is TranscribeState.Idle -> {
                                // Idle state prompt
                                Text(
                                    "Tap 'Transcribe Live Speech' to speak into the microphone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VestraColors.InkMuted,
                                )
                            }
                        }
                    }
                }

                // ----------------------------------------------------
                // 5. TEXT TO SPEECH (TTS)
                // ----------------------------------------------------
                AudioStudioTab.TTS -> {
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "TEXT-TO-SPEECH (TTS)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                ),
                                color = VestraColors.Accent,
                            )
                            Text(
                                if (!cloudModelsEnabled) "On-device TTS" else provider.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.InkMuted,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Voice Personas Selector
                        Text(
                            "VOICE PERSONAS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VestraColors.InkMuted,
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

                        // Script Field
                        OutlinedTextField(
                            value = ttsScript,
                            onValueChange = { ttsScript = it },
                            label = { Text("Script for ${VoiceCatalog.byId(personaId).displayName}…") },
                            placeholder = { Text("Welcome to the Lookbook couture collection.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VestraColors.Accent,
                                unfocusedBorderColor = VestraColors.GlassBorder,
                            ),
                        )

                        Spacer(Modifier.height(8.dp))

                        // Quick lookbook script chips
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(
                                listOf(
                                    "Welcome to the Autumn Couture Lookbook.",
                                    "Handcrafted emerald silk abaya with gold stitching.",
                                    "Introducing the modest atelier summer collection.",
                                ),
                            ) { phrase ->
                                AtelierFilterChip(
                                    selected = false,
                                    onClick = { ttsScript = phrase },
                                    label = { Text(phrase.take(24) + "…") },
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VestraColors.Accent)
                                .clickable {
                                    if (ttsScript.isNotBlank()) {
                                        viewModel.setPrompt(ttsScript)
                                        viewModel.generateAudio()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = VestraColors.Canvas,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Generate Voice Speech",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = VestraColors.Canvas,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ==========================================
            // RECENT AUDIO CREATIONS & LIBRARY
            // ==========================================
            if (clips.isNotEmpty()) {
                GlassCard {
                    GlassSectionLabel("AUDIO LIBRARY (${clips.size} CLIPS)")
                    Spacer(Modifier.height(6.dp))
                    AudioClipList(
                        clips = clips,
                        onShare = { clip ->
                            MediaExport.share(context, File(clip.path), "Share audio")
                        },
                        onDelete = { clip ->
                            if (AudioClipLibrary.delete(clip)) {
                                clips = clips.filterNot { it.path == clip.path }
                                if (activeOutputAudio?.absolutePath == clip.path) {
                                    activeOutputAudio = null
                                }
                            }
                        },
                        onSelectForVoiceChanger = { clip ->
                            viewModel.setReference(clip.path)
                            activeOutputAudio = File(clip.path)
                            activeOutputTitle = clip.fileName
                            activeOutputBadge = clip.kind.label
                            recordHint = "Selected ${clip.fileName} for studio actions"
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
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
            cloudGenerationEnabled = cloudModelsEnabled,
            hasCredential = { candidate ->
                !candidate.requiresApiKey || !viewModel.appSettings.apiKeyFor(candidate).isNullOrBlank()
            },
        )
    }

    // ====================================================
    // CREATE CUSTOM VOICE MODAL (SAMPLE ANALYSIS & EFFECT PROFILE)
    // ====================================================
    if (showCreateCustomVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showCreateCustomVoiceDialog = false },
            containerColor = VestraColors.SurfaceRaised,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = VestraColors.Accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Clone Custom Voice",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = VestraColors.Ink,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Provide a 3-10 second voice recording or sample file. The acoustic engine will extract true pitch (F0), vocal tract formants, and spectral harmonics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VestraColors.InkMuted,
                    )
                    Spacer(Modifier.height(14.dp))

                    // Name and Emoji
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customSampleName,
                            onValueChange = { customSampleName = it },
                            label = { Text("Voice Name") },
                            placeholder = { Text("e.g. Grandpa Arthur") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VestraColors.Accent,
                                unfocusedBorderColor = VestraColors.GlassBorder,
                            ),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Sample Emoji Selector
                    Text(
                        "CHOOSE ICON",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = VestraColors.Accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("👴", "👵", "👧", "👦", "👨", "👩", "👶", "🎙️", "🎭", "✨")) { emoji ->
                            val selected = customSampleEmoji == emoji
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) VestraColors.Accent.copy(alpha = 0.2f) else VestraColors.Canvas)
                                    .border(1.dp, if (selected) VestraColors.Accent else VestraColors.GlassBorder, CircleShape)
                                    .clickable { customSampleEmoji = emoji },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Step 2: Sample selection & Capture
                    Text(
                        "VOICE SAMPLE SOURCE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = VestraColors.Accent,
                    )
                    Spacer(Modifier.height(6.dp))

                    // Dedicated Raw PCM AudioRecord Sample Capture Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSampleRecording) VestraColors.Danger.copy(alpha = 0.15f) else VestraColors.Canvas)
                            .border(
                                1.dp,
                                if (isSampleRecording) VestraColors.Danger else VestraColors.Accent.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasAudioPermission) {
                                    Toast.makeText(context, "Please grant microphone permission to record sample", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (isSampleRecording) {
                                        val res = voiceSampleManager.stopRecording()
                                        if (res is VoiceSampleResult.Success) {
                                            val wavFile = File(res.sample.wavPath)
                                            customSampleFile = wavFile
                                            sampleQualityReport = res.sample.quality
                                            analyzeAndSetSample(wavFile)
                                        } else if (res is VoiceSampleResult.Error) {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        sampleQualityReport = null
                                        voiceSampleManager.startRecording(
                                            name = customSampleName.ifBlank { "Voice Sample" },
                                            config = VoiceSampleConfig(sampleRate = 44100),
                                        )
                                    }
                                }
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isSampleRecording) Icons.Outlined.Stop else Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = if (isSampleRecording) VestraColors.Danger else VestraColors.Accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isSampleRecording) "Stop & Analyze Raw PCM Sample" else "Record Live Sample (AudioRecord PCM)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSampleRecording) VestraColors.Danger else VestraColors.Ink,
                                )
                            }
                            if (isSampleRecording) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(VestraColors.SurfaceRaised),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(sampleAmplitude.coerceIn(0.05f, 1f))
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(VestraColors.Accent),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Use current recording if present
                        if (reference != null && File(reference!!).exists()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VestraColors.Canvas)
                                    .border(1.dp, VestraColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        val file = File(reference!!)
                                        customSampleFile = file
                                        analyzeAndSetSample(file)
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Mic, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Use Current Audio", style = MaterialTheme.typography.labelSmall, color = VestraColors.Ink)
                                }
                            }
                        }

                        // Pick any clip from library
                        if (clips.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VestraColors.Canvas)
                                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val firstClip = clips.firstOrNull()
                                        if (firstClip != null) {
                                            val file = File(firstClip.path)
                                            customSampleFile = file
                                            analyzeAndSetSample(file)
                                        }
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = VestraColors.Accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Use Recent Clip", style = MaterialTheme.typography.labelSmall, color = VestraColors.Ink)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Analysis Results Card
                    if (isAnalyzingSample) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = VestraColors.Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Analyzing vocal acoustics…", style = MaterialTheme.typography.bodySmall, color = VestraColors.InkMuted)
                        }
                    } else if (analyzedCustomProfile != null) {
                        val prof = analyzedCustomProfile!!
                        val report = sampleQualityReport
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VestraColors.Canvas)
                                .border(1.dp, VestraColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "ACOUSTIC PROFILE EXTRACTED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = VestraColors.Accent,
                                    )
                                    Text(
                                        "${prof.detectedPitchHz.toInt()} Hz F0",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = VestraColors.Accent,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "• Formant Resonance: ${(prof.formantScale * 100).toInt()}% tract scale\n• Warmth: ${(prof.warmth * 100).toInt()}% · Clarity: ${(prof.clarity * 100).toInt()}%\n• Micro-Tremor: ${if (prof.tremorDepth > 0) "%.1f Hz (Senescent)".format(prof.tremorRateHz) else "Smooth Vocal"}${if (report != null) "\n• SNR Quality: ${report.snrEstimateDb.toInt()} dB (Peak ${report.peakDbFs.toInt()} dBFS)" else ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = VestraColors.Ink,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val profile = analyzedCustomProfile ?: CustomVoiceProfile(
                            id = "custom_${System.currentTimeMillis()}",
                            name = customSampleName.ifBlank { "Custom Voice" },
                            samplePath = customSampleFile?.absolutePath ?: (reference ?: ""),
                            emoji = customSampleEmoji,
                            detectedPitchHz = 160f,
                            formantScale = 1.0f,
                            warmth = 0.5f,
                            clarity = 0.6f,
                        )
                        val updated = (listOf(profile) + customVoices).distinctBy { it.id }
                        customVoices = updated
                        CustomVoiceStorage.saveProfiles(context.filesDir, updated)
                        showCreateCustomVoiceDialog = false
                        Toast.makeText(context, "Cloned voice saved! Select it in the grid.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VestraColors.Accent),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Save & Clone Voice", color = VestraColors.Canvas)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCustomVoiceDialog = false }) {
                    Text("Cancel", color = VestraColors.InkMuted)
                }
            },
        )
    }
}

private fun formatRecordingTime(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}

private fun formatDurationSeconds(seconds: Float): String {
    val totalSecs = seconds.toInt().coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%02d:%02d".format(mins, secs)
}
