package com.zakir.vestra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakir.vestra.shared.local.LocalModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelContracts
import com.zakir.vestra.shared.cloud.GenerativeAssists
import com.zakir.vestra.shared.cloud.GenerativeCloudService
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.diagnostics.RunCapability
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.engine.local.LocalStudioToolBridge
import com.zakir.vestra.shared.jobs.LocalJobStore
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.settings.PreflightResult
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.wardrobe.WardrobeEntry
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalUuidApi::class)
class GenerativeViewModel(
    private val generative: GenerativeCloudService,
    val appSettings: AppSettings,
    val usage: UsageLedger,
    private val wardrobe: WardrobeRepository,
    private val runDiagnostics: RunDiagnostics? = null,
    private val deviceRamMb: Long? = null,
    private val localJobStore: LocalJobStore? = null,
) : ViewModel() {

    /** The in-flight [LocalJobStore] job id, if the current generation is local — null otherwise. */
    private var activeLocalJobId: String? = null

    private fun completeLocalJob(success: Boolean) {
        activeLocalJobId?.let { localJobStore?.complete(it, success) }
        activeLocalJobId = null
    }

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _referenceUri = MutableStateFlow<String?>(null)
    val referenceUri: StateFlow<String?> = _referenceUri

    private val _state = MutableStateFlow<GenerativeState?>(null)
    val state: StateFlow<GenerativeState?> = _state

    /** Rolling live console lines for the current generation (newest last). */
    private val _liveLog = MutableStateFlow<List<String>>(emptyList())
    val liveLog: StateFlow<List<String>> = _liveLog

    /** Wall-clock start of the active generation (for elapsed timer in ResultPane). */
    private val _generationStartedAtMs = MutableStateFlow<Long?>(null)
    val generationStartedAtMs: StateFlow<Long?> = _generationStartedAtMs

    private val _preflightMessage = MutableStateFlow<String?>(null)
    val preflightMessage: StateFlow<String?> = _preflightMessage

    private val _lastUsedProviderId = MutableStateFlow<String?>(null)
    val lastUsedProviderId: StateFlow<String?> = _lastUsedProviderId

    private val _creativeMode = MutableStateFlow(false)
    val creativeMode: StateFlow<Boolean> = _creativeMode

    private val _pragmaticMode = MutableStateFlow(true)
    val pragmaticMode: StateFlow<Boolean> = _pragmaticMode

    private val _detailBoost = MutableStateFlow(true)
    val detailBoost: StateFlow<Boolean> = _detailBoost

    private val _fashionContext = MutableStateFlow(true)
    val fashionContext: StateFlow<Boolean> = _fashionContext

    private val _bypassFilter = MutableStateFlow(true)
    val bypassFilter: StateFlow<Boolean> = _bypassFilter

    private val _qualityGuard = MutableStateFlow(true)
    val qualityGuard: StateFlow<Boolean> = _qualityGuard

    private val _analyzeReference = MutableStateFlow(false)
    val analyzeReference: StateFlow<Boolean> = _analyzeReference

    private val _inferenceSteps = MutableStateFlow(22)
    val inferenceSteps: StateFlow<Int> = _inferenceSteps

    private val _guidanceScale = MutableStateFlow(7.0f)
    val guidanceScale: StateFlow<Float> = _guidanceScale

    private val _seed = MutableStateFlow<Long?>(null)
    val seed: StateFlow<Long?> = _seed

    private val _voicePersonaId = MutableStateFlow(com.zakir.vestra.shared.audio.VoiceCatalog.defaultId)
    val voicePersonaId: StateFlow<String> = _voicePersonaId

    private val _voiceKnobs = MutableStateFlow(com.zakir.vestra.shared.audio.VoiceKnobs.Default)
    val voiceKnobs: StateFlow<com.zakir.vestra.shared.audio.VoiceKnobs> = _voiceKnobs

    private var job: Job? = null
    private var generationEpoch = 0

    init {
        LocalStudioToolBridge.onAppendPrompt = { clause ->
            if (clause.isNotBlank()) {
                val current = _prompt.value.trim()
                _prompt.value = if (current.isBlank()) clause else "$current $clause"
            }
        }
        LocalStudioToolBridge.onSetEngineTier = { tierName ->
            runCatching { EngineTier.valueOf(tierName.trim().uppercase()) }
                .getOrNull()
                ?.let { appSettings.setEngineTier(it) }
        }
        LocalStudioToolBridge.onSetBackdrop = { backdrop ->
            if (backdrop.isNotBlank()) {
                val current = _prompt.value.trim()
                _prompt.value = if (current.isBlank()) {
                    "Backdrop: $backdrop"
                } else {
                    "$current · backdrop: $backdrop"
                }
            }
        }
    }

    /** Which studio produced the current [state] — panes hide foreign results. */
    private val _resultCapability = MutableStateFlow<AiCapability?>(null)
    val resultCapability: StateFlow<AiCapability?> = _resultCapability

    /** Per-studio prompt/result bags so pager tabs do not wipe each other. */
    private data class StudioBag(
        var prompt: String = "",
        var referenceUri: String? = null,
        var state: GenerativeState? = null,
        var liveLog: List<String> = emptyList(),
        var preflightMessage: String? = null,
        var lastUsedProviderId: String? = null,
        var resultCapability: AiCapability? = null,
        var job: Job? = null,
        var generationEpoch: Int = 0,
        var generationStartedAtMs: Long? = null,
    )

    private val bags = mutableMapOf<AiCapability, StudioBag>()
    private var boundKey: AiCapability = AiCapability.IMAGE_GEN

    private fun studioKey(capability: AiCapability): AiCapability =
        if (capability == AiCapability.IMAGE_EDIT) AiCapability.IMAGE_GEN else capability

    private fun bag(key: AiCapability = boundKey): StudioBag =
        bags.getOrPut(key) { StudioBag() }

    /**
     * Switch the visible studio session. Does **not** clear sibling tabs.
     * Call from pager pages instead of [prepareStudio].
     */
    fun bindStudio(capability: AiCapability) {
        val key = studioKey(capability)
        if (key == boundKey) return
        val cur = bag()
        cur.prompt = _prompt.value
        cur.referenceUri = _referenceUri.value
        cur.state = _state.value
        cur.liveLog = _liveLog.value
        cur.preflightMessage = _preflightMessage.value
        cur.lastUsedProviderId = _lastUsedProviderId.value
        cur.resultCapability = _resultCapability.value
        cur.job = job
        cur.generationEpoch = generationEpoch
        cur.generationStartedAtMs = _generationStartedAtMs.value

        boundKey = key
        val next = bag()
        job = next.job
        generationEpoch = next.generationEpoch
        _prompt.value = next.prompt
        _referenceUri.value = next.referenceUri
        _state.value = next.state
        _liveLog.value = next.liveLog
        _preflightMessage.value = next.preflightMessage
        _lastUsedProviderId.value = next.lastUsedProviderId
        _resultCapability.value = next.resultCapability
        _generationStartedAtMs.value = next.generationStartedAtMs
    }

    val isBusy: Boolean
        get() {
            val s = _state.value
            return s is GenerativeState.Preparing ||
                s is GenerativeState.Running ||
                s is GenerativeState.CodeStreaming
        }

    fun setPrompt(value: String) {
        _prompt.value = value.take(MAX_PROMPT)
        _preflightMessage.value = null
    }

    fun setReference(uri: String?) {
        _referenceUri.value = uri
        _preflightMessage.value = null
    }

    fun setCreativeMode(enabled: Boolean) {
        _creativeMode.value = enabled
    }

    fun setPragmaticMode(enabled: Boolean) {
        _pragmaticMode.value = enabled
    }

    fun setDetailBoost(enabled: Boolean) {
        _detailBoost.value = enabled
    }

    fun setFashionContext(enabled: Boolean) {
        _fashionContext.value = enabled
    }

    fun setBypassFilter(enabled: Boolean) {
        _bypassFilter.value = enabled
    }

    fun setQualityGuard(enabled: Boolean) {
        _qualityGuard.value = enabled
    }

    fun setAnalyzeReference(enabled: Boolean) {
        _analyzeReference.value = enabled
    }

    fun setInferenceSteps(value: Int) {
        _inferenceSteps.value = value.coerceIn(4, 50)
    }

    fun setGuidanceScale(value: Float) {
        _guidanceScale.value = value.coerceIn(1f, 15f)
    }

    fun setSeed(value: Long?) {
        _seed.value = value?.coerceAtLeast(0L)
    }

    fun currentAssists(): GenerativeAssists = GenerativeAssists(
        pragmatic = _pragmaticMode.value,
        creative = _creativeMode.value,
        fashionContext = _fashionContext.value,
        detailBoost = _detailBoost.value,
        bypassFilter = _bypassFilter.value,
        qualityGuard = _qualityGuard.value,
        analyzeReference = _analyzeReference.value,
        inferenceSteps = _inferenceSteps.value.takeIf { it != 22 },
        guidanceScale = _guidanceScale.value.takeIf { it != 7.0f },
        seed = _seed.value,
    )

    fun prepareStudio(resetIfIdle: Boolean = true) {
        if (!resetIfIdle || isBusy) return
        _state.value = null
        _liveLog.value = emptyList()
        _preflightMessage.value = null
        _prompt.value = ""
        _referenceUri.value = null
        _resultCapability.value = null
        val cur = bag()
        cur.prompt = ""
        cur.referenceUri = null
        cur.state = null
        cur.liveLog = emptyList()
        cur.preflightMessage = null
        cur.resultCapability = null
    }

    /** True when [state] belongs to this studio (or shared Image Create/Edit). */
    fun resultBelongsTo(capability: AiCapability): Boolean {
        val owned = _resultCapability.value ?: return _state.value != null
        val want = studioKey(capability)
        val have = studioKey(owned)
        return want == have
    }

    fun preflightLabel(capability: AiCapability): String? {
        if (capability == AiCapability.IMAGE_GEN &&
            (appSettings.prefersLocal(capability) || !appSettings.networkLikelyAvailable()) &&
            generative.localImageReady()
        ) {
            return "Local SD-Turbo · Ready offline"
        }
        if (capability == AiCapability.IMAGE_EDIT &&
            (appSettings.prefersLocal(capability) || !appSettings.networkLikelyAvailable()) &&
            generative.localImageEditReady()
        ) {
            return "Local SD-Turbo edit · Ready offline"
        }
        if (capability == AiCapability.AUDIO && generative.localAudioReady()) {
            return "Device TTS · Ready offline"
        }
        if (capability == AiCapability.CODE &&
            (appSettings.prefersLocal(capability) || !appSettings.networkLikelyAvailable()) &&
            generative.localCodeReady()
        ) {
            val label = com.zakir.vestra.shared.local.LocalModelCatalog
                .byId(generative.localCodeProviderId())?.displayName ?: "Local Gemma"
            return "$label · Ready offline"
        }
        if (capability == AiCapability.VIDEO &&
            (appSettings.prefersLocal(capability) || !appSettings.networkLikelyAvailable()) &&
            generative.localVideoReady()
        ) {
            return "Local still-clip · Ready offline"
        }
        return when (val check = appSettings.preflight(capability)) {
            is PreflightResult.Blocked -> check.reason
            is PreflightResult.Ok -> "${check.provider.displayName} · ${CloudModelContracts.liveStatusLabel(check.provider, appSettings.modelHealth)}"
        }
    }

    /** Cold-load state for the selected on-device model. */
    sealed interface Warmup {
        data object Idle : Warmup
        data class Loading(val label: String) : Warmup
        data class Ready(val label: String) : Warmup
        data class Failed(val label: String, val reason: String) : Warmup
    }

    private val _warmup = MutableStateFlow<Warmup>(Warmup.Idle)
    val warmup: StateFlow<Warmup> = _warmup

    private var warmupJob: Job? = null

    /**
     * Loads the selected on-device model so it is ready before the first prompt.
     *
     * LiteRT-LM has no documented preload call, so this drives a one-token inference: that is
     * the only way to prove the model genuinely loads rather than merely that its files exist —
     * the gap that let a pack read "Ready offline" and then fail at generate time.
     */
    fun warmUpLocal(capability: AiCapability) {
        if (!appSettings.prefersLocal(capability)) {
            _warmup.value = Warmup.Idle
            return
        }
        val id = appSettings.selectionId(capability)
        val label = LocalModelCatalog.byId(id)?.displayName ?: "On-device model"
        if ((_warmup.value as? Warmup.Ready)?.label == label) return
        warmupJob?.cancel()
        warmupJob = viewModelScope.launch {
            _warmup.value = Warmup.Loading(label)
            val failure: String? = withContext(Dispatchers.IO) {
                try {
                    generative.warmUpLocal(capability)
                } catch (e: Exception) {
                    e.message ?: "Model failed to load"
                }
            }
            _warmup.value = if (failure == null) {
                Warmup.Ready(label)
            } else {
                Warmup.Failed(label, failure)
            }
        }
    }

    fun localImageOfflineReady(): Boolean = generative.localImageReady()

    fun localImageEditOfflineReady(): Boolean = generative.localImageEditReady()

    fun localAudioOfflineReady(): Boolean = generative.localAudioReady()

    fun localCodeOfflineReady(): Boolean = generative.localCodeReady()

    fun localTranscribeOfflineReady(): Boolean = generative.localTranscribeReady()

    fun localVisionOfflineReady(): Boolean = generative.localVisionReady()

    fun transcribeAudio() {
        val clip = _referenceUri.value
        if (clip.isNullOrBlank()) {
            _preflightMessage.value = "Record or attach audio first, then tap Transcribe."
            return
        }
        if (!generative.localTranscribeReady()) {
            _preflightMessage.value = "Offline transcription isn't available yet — the published " +
                "Gemma 4 pack doesn't include audio support. Downloading it again won't change that."
            return
        }
        _preflightMessage.value = null
        startGeneration(
            capability = RunCapability.AUDIO,
            modelLabel = "Local audio scribe (offline)",
            local = true,
            studio = AiCapability.AUDIO,
        ) {
            generative.generateTranscribe(clip)
        }
    }

    fun localVideoOfflineReady(): Boolean = generative.localVideoReady()

    fun generateImage() {
        val p = sanitizePrompt(_prompt.value)
        if (p.isEmpty()) {
            _preflightMessage.value = "Enter a prompt describing the image."
            return
        }
        _prompt.value = p
        val capability = if (_referenceUri.value == null) AiCapability.IMAGE_GEN else AiCapability.IMAGE_EDIT
        val offline = !appSettings.networkLikelyAvailable()
        val localReady = when {
            _referenceUri.value == null -> generative.localImageReady()
            else -> generative.localImageEditReady()
        }
        val bypassPreflight = when {
            appSettings.prefersLocal(capability) && localReady -> true
            offline && localReady -> true
            else -> false
        }
        if (!bypassPreflight) {
            when (val check = appSettings.preflight(capability)) {
                is PreflightResult.Blocked -> {
                    _preflightMessage.value = check.reason
                    return
                }
                is PreflightResult.Ok -> Unit
            }
        }
        // Edit always uses tiny-SD (Bonsai has no reference-image conditioning); Create reflects
        // whichever engine the user actually selected, matching GenerativeCloudService's own
        // routing check — was hardcoded to "Local SD-Turbo (offline)" regardless, mislabeling
        // every Bonsai-selected run in the diagnostics run history the same way local Code runs
        // were mislabeled "Local Gemma (offline)" regardless of which local model actually ran.
        val localLabel = if (_referenceUri.value != null) {
            "Local SD-Turbo edit (offline)"
        } else if (appSettings.selectionId(AiCapability.IMAGE_GEN) == "local-bonsai-image-v1") {
            "Bonsai Image 4B (LiteRT, offline)"
        } else {
            "Local SD-Turbo (offline)"
        }
        startGeneration(
            capability = if (_referenceUri.value == null) RunCapability.IMAGE_GEN else RunCapability.IMAGE_EDIT,
            modelLabel = if (bypassPreflight) localLabel else appSettings.selectedProvider(capability).displayName,
            local = bypassPreflight,
            studio = capability,
        ) {
            generative.generateImage(p, _referenceUri.value, currentAssists())
        }
    }

    fun generateCode() {
        val p = sanitizePrompt(_prompt.value)
        if (p.isEmpty()) {
            _preflightMessage.value = "Enter a coding prompt."
            return
        }
        _prompt.value = p
        val bypassPreflight = generative.localCodeReady() &&
            (!appSettings.networkLikelyAvailable() || appSettings.prefersLocal(AiCapability.CODE))
        if (!bypassPreflight) {
            when (val check = appSettings.preflight(AiCapability.CODE)) {
                is PreflightResult.Blocked -> {
                    _preflightMessage.value = check.reason
                    return
                }
                is PreflightResult.Ok -> Unit
            }
        }
        // Reflects whichever engine actually runs (Qwen3, Gemma 3, Gemma 4 E2B, FunctionGemma —
        // RoutingLocalCodeGenerator picks per the user's selection) — was hardcoded to "Local
        // Gemma (offline)" regardless, mislabeling every non-Gemma local code run the same way
        // local image gen used to mislabel every Bonsai output as local-sdturbo-v1. Confirmed
        // live in a user's diagnostics export: modelLabel said "Local Gemma (offline)" while the
        // run's own note field said local-qwen3-06b-v1 actually ran.
        val localCodeLabel = LocalModelCatalog.byId(generative.localCodeProviderId())?.displayName
            ?: "Local on-device"
        startGeneration(
            capability = RunCapability.CODE,
            modelLabel = if (bypassPreflight) localCodeLabel else appSettings.selectedProvider(AiCapability.CODE).displayName,
            local = bypassPreflight,
            studio = AiCapability.CODE,
        ) {
            generative.generateCode(p, currentAssists())
        }
    }

    fun setVoicePersona(id: String) {
        _voicePersonaId.value = id
    }

    fun setVoiceKnobs(knobs: com.zakir.vestra.shared.audio.VoiceKnobs) {
        _voiceKnobs.value = knobs.sanitized()
    }

    fun generateAudio() {
        val p = sanitizePrompt(_prompt.value)
        if (p.isEmpty()) {
            _preflightMessage.value = "Enter text to speak, or record audio and tap Apply voice change."
            return
        }
        _prompt.value = p
        val voiceChange = _referenceUri.value != null && p.equals("voice-change", ignoreCase = true)
        val bypassPreflight = voiceChange || generative.localAudioReady()
        if (!bypassPreflight) {
            when (val check = appSettings.preflight(AiCapability.AUDIO)) {
                is PreflightResult.Blocked -> {
                    _preflightMessage.value = check.reason
                    return
                }
                is PreflightResult.Ok -> Unit
            }
        }
        val persona = com.zakir.vestra.shared.audio.VoiceCatalog.byId(_voicePersonaId.value)
        val modelLabel = when {
            voiceChange -> "Local voice changer"
            generative.localAudioReady() -> "Device TTS (offline)"
            else -> appSettings.selectedProvider(AiCapability.AUDIO).displayName
        }
        startGeneration(
            capability = RunCapability.AUDIO,
            modelLabel = modelLabel,
            local = bypassPreflight,
            studio = AiCapability.AUDIO,
        ) {
            generative.generateAudio(
                prompt = p,
                persona = persona,
                knobs = _voiceKnobs.value,
                referenceAudioUri = _referenceUri.value,
                assists = currentAssists(),
            )
        }
    }

    /** Offline path: apply local DSP knobs to a recorded / attached WAV clip. */
    fun applyVoiceChange() {
        val clip = _referenceUri.value
        if (clip.isNullOrBlank()) {
            _preflightMessage.value = "Record or attach audio first, then apply voice knobs."
            return
        }
        _prompt.value = "voice-change"
        _preflightMessage.value = null
        startGeneration(
            capability = RunCapability.AUDIO,
            modelLabel = "Local voice changer",
            local = true,
            studio = AiCapability.AUDIO,
        ) {
            generative.generateAudio(
                prompt = "voice-change",
                persona = com.zakir.vestra.shared.audio.VoiceCatalog.byId(_voicePersonaId.value),
                knobs = _voiceKnobs.value,
                referenceAudioUri = clip,
            )
        }
    }

    fun generateVideo() {
        val p = sanitizePrompt(_prompt.value)
        if (p.isEmpty()) {
            _preflightMessage.value = "Enter a video prompt."
            return
        }
        _prompt.value = p
        val bypassPreflight = generative.localVideoReady() &&
            (!appSettings.networkLikelyAvailable() || appSettings.prefersLocal(AiCapability.VIDEO))
        if (!bypassPreflight) {
            when (val check = appSettings.preflight(AiCapability.VIDEO)) {
                is PreflightResult.Blocked -> {
                    _preflightMessage.value = check.reason
                    return
                }
                is PreflightResult.Ok -> Unit
            }
        }
        startGeneration(
            capability = RunCapability.VIDEO,
            modelLabel = if (bypassPreflight) {
                "Local still-clip (offline)"
            } else {
                appSettings.selectedProvider(AiCapability.VIDEO).displayName
            },
            local = bypassPreflight,
            studio = AiCapability.VIDEO,
        ) {
            generative.generateVideo(p, currentAssists())
        }
    }

    fun cancel() {
        forceStop(showStopped = false)
    }

    fun forceStop(showStopped: Boolean = true) {
        job?.cancel(CancellationException("force_stop"))
        job = null
        generationEpoch++
        _generationStartedAtMs.value = null
        bag(boundKey).generationStartedAtMs = null
        activeLocalJobId?.let { localJobStore?.cancel(it) }
        activeLocalJobId = null
        appendLive("Stopped by user")
        _state.value = if (showStopped) {
            GenerativeState.Failed("Stopped. Tap Generate to run again.")
        } else {
            null
        }
    }

    fun clearResult() {
        forceStop(showStopped = false)
        _liveLog.value = emptyList()
        _preflightMessage.value = null
    }

    private fun appendLive(line: String) {
        val stamped = line.take(160)
        _liveLog.value = (_liveLog.value + stamped).takeLast(40)
        runCatching {
            com.zakir.vestra.diagnostics.CrashReporter.i("Gen", stamped)
        }
    }

    private fun startGeneration(
        capability: RunCapability,
        modelLabel: String?,
        studio: AiCapability,
        local: Boolean,
        block: () -> kotlinx.coroutines.flow.Flow<GenerativeState>,
    ) {
        job?.cancel()
        val epoch = ++generationEpoch
        val studioKey = studioKey(studio)
        _preflightMessage.value = null
        _liveLog.value = emptyList()
        _generationStartedAtMs.value = null
        _resultCapability.value = studio
        _state.value = GenerativeState.Preparing("Starting…")
        val startedAt = System.currentTimeMillis()
        _generationStartedAtMs.value = startedAt
        bag(studioKey).generationStartedAtMs = startedAt
        appendLive("Start · ${capability.name} · ${modelLabel ?: "model"}")
        // EngineTier is really a try-on-specific concept (AUTO/LITE/PRO/CLOUD); reused loosely
        // here as an on-device/cloud signal for the other capabilities since RunDiagnostics has
        // no dedicated field for it. Was hardcoded to CLOUD unconditionally — the Diagnostics
        // screen renders this ("Tier: $it"), so every local Image/Code/Video/Audio run showed
        // "Tier: CLOUD" even when fully offline, confirmed in a user's diagnostics export.
        val builder = runDiagnostics?.startRun(
            capability = capability,
            tier = if (local) EngineTier.LITE else EngineTier.CLOUD,
            modelId = null,
            modelLabel = modelLabel,
            deviceRamMb = deviceRamMb,
        )
        activeLocalJobId = if (local) localJobStore?.start(capability, _prompt.value) else null
        job = viewModelScope.launch {
            var lastStageAt = System.currentTimeMillis()
            try {
                block().collect { rawNext ->
                    if (epoch != generationEpoch) return@collect
                    // Local-generation failures had no way to correlate the message on screen to
                    // its full record in Settings → Diagnostics — the record itself always had a
                    // stable id, it just never reached the user-facing string. Cloud failures
                    // don't need this: CloudFailure already carries enough context in its message.
                    val next = if (local && rawNext is GenerativeState.Failed && builder != null) {
                        rawNext.copy(message = "${rawNext.message} (ref ${builder.id})")
                    } else {
                        rawNext
                    }
                    if (boundKey != studioKey) {
                        // User switched tabs — keep updating the owning bag only.
                        val owner = bag(studioKey)
                        owner.state = next
                        when (next) {
                            is GenerativeState.ImageReady,
                            is GenerativeState.VideoReady,
                            is GenerativeState.AudioReady,
                            is GenerativeState.CodeReady,
                            -> owner.lastUsedProviderId = when (next) {
                                is GenerativeState.ImageReady -> next.providerId
                                is GenerativeState.VideoReady -> next.providerId
                                is GenerativeState.AudioReady -> next.providerId
                                is GenerativeState.CodeReady -> next.providerId
                                else -> owner.lastUsedProviderId
                            }
                            else -> Unit
                        }
                        return@collect
                    }
                    _state.value = next
                    when (next) {
                        is GenerativeState.Preparing -> appendLive(next.message)
                        is GenerativeState.Running -> {
                            appendLive(next.stage)
                            val now = System.currentTimeMillis()
                            builder?.stage(next.stage, now - lastStageAt)
                            lastStageAt = now
                        }
                        is GenerativeState.ImageReady -> {
                            appendLive("Image ready")
                            _lastUsedProviderId.value = next.providerId
                            ingestCreateImage(next.path, label = "Create", studioKey = studioKey, local = local)
                            builder?.complete(success = true, note = next.providerId)
                            completeLocalJob(success = true)
                        }
                        is GenerativeState.VideoReady -> {
                            appendLive("Video ready")
                            _lastUsedProviderId.value = next.providerId
                            ingestCreateImage(next.path, label = "Video", studioKey = studioKey, local = local)
                            builder?.complete(success = true, note = next.providerId)
                            completeLocalJob(success = true)
                        }
                        is GenerativeState.AudioReady -> {
                            appendLive("Audio ready")
                            _lastUsedProviderId.value = next.providerId
                            builder?.complete(success = true, note = next.providerId)
                            completeLocalJob(success = true)
                        }
                        is GenerativeState.CodeReady -> {
                            appendLive("Code ready · ${next.tokensIn}+${next.tokensOut} tokens")
                            _lastUsedProviderId.value = next.providerId
                            builder?.complete(
                                success = true,
                                note = "${next.providerId} · ${next.tokensIn}+${next.tokensOut} tokens",
                            )
                            completeLocalJob(success = true)
                        }
                        is GenerativeState.CodeStreaming -> {
                            // _state.value is already updated above — ResultPane renders the
                            // growing text live. Not appended to the live log: a line per token
                            // chunk would flood its bounded 40-line window.
                        }
                        is GenerativeState.TranscribeReady -> {
                            appendLive("Transcription ready")
                            _lastUsedProviderId.value = next.providerId
                            builder?.complete(success = true, note = next.providerId)
                            completeLocalJob(success = true)
                        }
                        is GenerativeState.Failed -> {
                            appendLive("Failed · ${next.message.take(120)}")
                            builder?.complete(success = false, error = next.message)
                            completeLocalJob(success = false)
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Expected on force stop / clear
            } catch (e: Exception) {
                if (epoch == generationEpoch) {
                    val rawMsg = e.message?.take(280)?.ifBlank { null } ?: "Generation failed. Tap Retry."
                    val msg = if (local && builder != null) "$rawMsg (ref ${builder.id})" else rawMsg
                    completeLocalJob(success = false)
                    if (boundKey == studioKey) {
                        appendLive("Error · $msg")
                        _state.value = GenerativeState.Failed(msg)
                    } else {
                        bag(studioKey).state = GenerativeState.Failed(msg)
                    }
                    builder?.complete(success = false, error = rawMsg)
                }
            } finally {
                if (boundKey == studioKey) {
                    bag().job = null
                    bag().generationEpoch = generationEpoch
                    bag().state = _state.value
                    bag().liveLog = _liveLog.value
                    bag().lastUsedProviderId = _lastUsedProviderId.value
                    bag().resultCapability = _resultCapability.value
                } else {
                    bag(studioKey).job = null
                }
                if (job?.isActive != true) job = null
            }
        }
        bag(studioKey).job = job
        bag(studioKey).generationEpoch = epoch
    }

    /** Previous Wardrobe entry generated in each studio tab — chains consecutive retries. */
    private val lastEntryIdByStudioKey = mutableMapOf<AiCapability, String>()

    private fun ingestCreateImage(path: String, label: String, studioKey: AiCapability, local: Boolean) {
        val promptSnippet = _prompt.value.trim().take(80).ifBlank { label.lowercase() }
        val id = Uuid.random().toString()
        runCatching {
            wardrobe.add(
                WardrobeEntry(
                    id = id,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    imagePath = path,
                    garmentUri = "${label.lowercase()}:$promptSnippet",
                    personLabel = label,
                    // Was hardcoded to CLOUD regardless of how the image was actually generated
                    // — the same class of mislabeling bug already fixed for diagnostics/live-log
                    // text, just at a different call site that was missed then.
                    tier = if (local) EngineTier.LITE else EngineTier.CLOUD,
                    shootId = null,
                    parentGenerationId = lastEntryIdByStudioKey[studioKey],
                ),
            )
        }
        lastEntryIdByStudioKey[studioKey] = id
    }

    private fun sanitizePrompt(raw: String): String =
        raw.trim()
            .replace("\u0000", "")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .take(MAX_PROMPT)

    private companion object {
        const val MAX_PROMPT = 4000
    }
}
