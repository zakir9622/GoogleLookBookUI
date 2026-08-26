package com.zakir.vestra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakir.vestra.cache.TryOnCacheStats
import com.zakir.vestra.cache.TryOnDiskCache
import com.zakir.vestra.shared.domain.Backdrop
import com.zakir.vestra.shared.domain.CastingProfile
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.Ethnicity
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.GarmentColor
import com.zakir.vestra.shared.domain.GarmentImage
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.PersonSource
import com.zakir.vestra.shared.domain.ShootState
import com.zakir.vestra.shared.domain.TryOnError
import com.zakir.vestra.shared.domain.TryOnRequest
import com.zakir.vestra.shared.domain.TryOnResult
import com.zakir.vestra.shared.domain.BodyType
import com.zakir.vestra.shared.domain.HairCoverage
import com.zakir.vestra.shared.domain.Scenario
import com.zakir.vestra.shared.domain.SkinTone
import com.zakir.vestra.shared.domain.layerRank
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.engine.EngineRouter
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.wardrobe.WardrobeEntry
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TryOnViewModel(
    private val engineRouter: EngineRouter,
    private val appSettings: AppSettings,
    private val wardrobe: WardrobeRepository,
    private val runDiagnostics: RunDiagnostics? = null,
    private val deviceRamMb: Long? = null,
    private val tryOnDiskCache: TryOnDiskCache? = null,
    private val context: android.content.Context? = null,
) : ViewModel() {

    private val _outfit = MutableStateFlow<List<GarmentImage>>(emptyList())
    val outfit: StateFlow<List<GarmentImage>> = _outfit

    private val _casting = MutableStateFlow(CastingProfile())
    val casting: StateFlow<CastingProfile> = _casting

    private val _shots = MutableStateFlow<List<PersonSource>>(emptyList())
    val shots: StateFlow<List<PersonSource>> = _shots

    private val _backdrop = MutableStateFlow(Backdrop.STUDIO_WHITE)
    val backdrop: StateFlow<Backdrop> = _backdrop

    private val _shoot = MutableStateFlow<ShootState?>(null)
    val shoot: StateFlow<ShootState?> = _shoot

    private val _liveLog = MutableStateFlow<List<String>>(emptyList())
    val liveLog: StateFlow<List<String>> = _liveLog

    private val _generationStartedAtMs = MutableStateFlow<Long?>(null)
    val generationStartedAtMs: StateFlow<Long?> = _generationStartedAtMs

    // Precision Parameters for All Cloud & Local Engines (with sensible defaults)
    private val _steps = MutableStateFlow(30)
    val steps: StateFlow<Int> = _steps

    private val _cfg = MutableStateFlow(2.5)
    val cfg: StateFlow<Double> = _cfg

    private val _seed = MutableStateFlow<Int?>(null)
    val seed: StateFlow<Int?> = _seed

    private val _garmentDesc = MutableStateFlow("")
    val garmentDesc: StateFlow<String> = _garmentDesc

    private val _autoMask = MutableStateFlow(true)
    val autoMask: StateFlow<Boolean> = _autoMask

    private val _autoCrop = MutableStateFlow(false)
    val autoCrop: StateFlow<Boolean> = _autoCrop

    private val _customEngineTier = MutableStateFlow<EngineTier?>(null)
    val customEngineTier: StateFlow<EngineTier?> = _customEngineTier

    // Disk Caching Layer & API Consumption Reducer
    private val _bypassCache = MutableStateFlow(false)
    val bypassCache: StateFlow<Boolean> = _bypassCache

    private val _cacheStats = MutableStateFlow<TryOnCacheStats?>(null)
    val cacheStats: StateFlow<TryOnCacheStats?> = _cacheStats

    private var shootJob: Job? = null

    init {
        refreshCacheStats()
    }

    fun setBypassCache(bypass: Boolean) {
        _bypassCache.value = bypass
    }

    fun refreshCacheStats() {
        _cacheStats.value = tryOnDiskCache?.getStats()
    }

    fun clearCache() {
        viewModelScope.launch {
            tryOnDiskCache?.clear()
            refreshCacheStats()
            appendLive("🧹 Disk cache cleared")
        }
    }

    fun setSteps(value: Int) {
        _steps.value = value.coerceIn(10, 60)
    }

    fun setCfg(value: Double) {
        _cfg.value = (value * 10).toInt() / 10.0
    }

    fun setSeed(value: Int?) {
        _seed.value = value
    }

    fun randomizeSeed() {
        _seed.value = (10000..999999).random()
    }

    fun setGarmentDesc(value: String) {
        _garmentDesc.value = value
    }

    fun setAutoMask(value: Boolean) {
        _autoMask.value = value
    }

    fun setAutoCrop(value: Boolean) {
        _autoCrop.value = value
    }

    fun setCustomEngineTier(tier: EngineTier?) {
        _customEngineTier.value = tier
        if (tier != null) {
            appSettings.setEngineTier(tier)
        }
    }

    fun selectCloudProvider(id: String) {
        appSettings.setCloudProvider(id)
        appSettings.setEngineTier(EngineTier.CLOUD)
        _customEngineTier.value = EngineTier.CLOUD
    }

    fun setSinglePerson(source: PersonSource) {
        _shots.value = listOf(source)
    }

    fun setSingleGarment(uri: String, category: GarmentCategory? = null) {
        _outfit.value = listOf(GarmentImage(uri = uri, category = category))
    }

    fun resetParameters() {
        _steps.value = 30
        _cfg.value = 2.5
        _seed.value = null
        _garmentDesc.value = ""
        _autoMask.value = true
        _autoCrop.value = false
        _backdrop.value = Backdrop.STUDIO_WHITE
    }

    fun addGarment(uri: String) {
        _outfit.value = _outfit.value + GarmentImage(uri = uri)
    }

    fun removeGarment(index: Int) {
        _outfit.value = _outfit.value.filterIndexed { i, _ -> i != index }
    }

    fun setGarmentCategory(index: Int, category: GarmentCategory?) {
        _outfit.value = _outfit.value.mapIndexed { i, piece ->
            if (i == index) piece.copy(category = category) else piece
        }
    }

    fun setGarmentUri(index: Int, uri: String) {
        _outfit.value = _outfit.value.mapIndexed { i, piece ->
            if (i == index) piece.copy(uri = uri) else piece
        }
    }

    fun setCasting(profile: CastingProfile) {
        _casting.value = profile
    }

    fun applyPreset(preset: CastingProfile) {
        _casting.value = preset
    }

    fun setEthnicity(ethnicity: Ethnicity) {
        _casting.value = _casting.value.copy(ethnicity = ethnicity)
    }

    fun setSkinTone(skinTone: SkinTone) {
        _casting.value = _casting.value.copy(skinTone = skinTone)
    }

    fun setBodyType(bodyType: BodyType) {
        _casting.value = _casting.value.copy(bodyType = bodyType)
    }

    fun setHairCoverage(hairCoverage: HairCoverage) {
        _casting.value = _casting.value.copy(hairCoverage = hairCoverage)
    }

    fun setGarmentColor(color: GarmentColor?) {
        _casting.value = _casting.value.copy(garmentColor = color)
    }

    fun setScenario(scenario: Scenario) {
        _casting.value = _casting.value.copy(scenario = scenario)
    }

    fun setShots(sources: List<PersonSource>) {
        _shots.value = sources
    }

    fun toggleShot(source: PersonSource) {
        val current = _shots.value
        _shots.value = when {
            current.contains(source) -> current - source
            source is PersonSource.UserPhoto -> listOf(source)
            else -> current.filterIsInstance<PersonSource.AiModel>() + source
        }
    }

    fun setBackdrop(backdrop: Backdrop) {
        _backdrop.value = backdrop
    }

    @OptIn(ExperimentalUuidApi::class)
    fun startShoot() {
        val outfit = _outfit.value.ifEmpty { return }
        val shots = _shots.value.ifEmpty { return }
        val layers = outfit.sortedBy { it.category.layerRank() }
        shootJob?.cancel()
        val shootId = Uuid.random().toString()
        _liveLog.value = emptyList()
        val startedAt = System.currentTimeMillis()
        _generationStartedAtMs.value = startedAt
        appendLive("Start · ${shots.size} look(s) · ${layers.size} layer(s)")
        _shoot.value = ShootState(0, shots.size, GenerationState.Idle, emptyList())

        context?.let {
            com.zakir.vestra.service.GenerationForegroundService.start(
                it,
                "Virtual Try-On in Progress",
                "Rendering ${shots.size} look(s) · ${layers.size} layer(s)...",
            )
        }

        shootJob = viewModelScope.launch {
            val completed = mutableListOf<TryOnResult>()
            for ((shotIndex, person) in shots.withIndex()) {
                val shotResult = renderShot(person, layers, shotIndex, shots.size, completed)
                if (shotResult == null) {
                    context?.let {
                        com.zakir.vestra.service.GenerationForegroundService.complete(
                            it,
                            "Try-On Could Not Complete",
                            "Generation encountered an issue. Tap to retry.",
                            isFailure = true,
                            deepLinkRoute = "tryon",
                        )
                    }
                    return@launch
                }
                completed += shotResult
                runCatching {
                    wardrobe.add(
                        WardrobeEntry(
                            id = Uuid.random().toString(),
                            createdAtEpochMillis = System.currentTimeMillis(),
                            imagePath = shotResult.imagePath,
                            garmentUri = outfit.first().uri,
                            personLabel = person.label(),
                            tier = shotResult.executedTier,
                            shootId = shootId,
                        ),
                    )
                }
                _shoot.value = ShootState(shotIndex, shots.size, GenerationState.Complete(shotResult), completed.toList())
            }
            context?.let {
                com.zakir.vestra.service.GenerationForegroundService.complete(
                    it,
                    "Virtual Try-On Complete ✨",
                    "Your stylish fitting is ready to view!",
                    completed.lastOrNull()?.imagePath,
                    isFailure = false,
                    deepLinkRoute = "tryon",
                )
            }
        }
    }

    private suspend fun renderShot(
        person: PersonSource,
        layers: List<GarmentImage>,
        shotIndex: Int,
        totalShots: Int,
        completed: List<TryOnResult>,
    ): TryOnResult? {
        var currentPerson = person
        var lastResult: TryOnResult? = null
        layers.forEachIndexed { layerIndex, piece ->
            val isLastLayer = layerIndex == layers.lastIndex
            val reqTier = _customEngineTier.value ?: appSettings.engineTier.value
            val request = TryOnRequest(
                garment = piece,
                person = currentPerson,
                tier = reqTier,
                backdrop = if (isLastLayer) _backdrop.value else Backdrop.ORIGINAL,
                casting = _casting.value,
                seed = _seed.value?.toLong(),
                customSteps = _steps.value,
                customCfg = _cfg.value,
                customGarmentDesc = _garmentDesc.value.ifBlank { null },
                autoCrop = _autoCrop.value,
                autoMask = _autoMask.value,
                clothType = when (piece.category) {
                    GarmentCategory.LOWER_BODY -> "lower"
                    GarmentCategory.ABAYA, GarmentCategory.JILBAB, GarmentCategory.KAFTAN,
                    GarmentCategory.DRESS, GarmentCategory.LEHENGA, GarmentCategory.FULL_COVERAGE,
                    GarmentCategory.SHALWAR_KAMEEZ -> "overall"
                    else -> "upper"
                },
            )

            val activeProviderId = if (reqTier == EngineTier.CLOUD) appSettings.selectedCloudProvider().id else null

            // Check Disk-Based Cache to avoid redundant API consumption and inference
            if (!_bypassCache.value && tryOnDiskCache != null) {
                val cached = tryOnDiskCache.get(request, activeProviderId)
                if (cached != null) {
                    appendLive("⚡ Loaded from Disk Cache (0 API calls used)")
                    lastResult = cached
                    currentPerson = PersonSource.UserPhoto(cached.imagePath)
                    refreshCacheStats()
                    val completeState = GenerationState.Complete(cached)
                    _shoot.value = ShootState(shotIndex, totalShots, completeState, completed + cached)
                    return@forEachIndexed
                }
            }

            val terminal = engineRouter.generate(request)
                .onEachReport(shotIndex, totalShots, layerIndex, layers.size, completed)
                .last()

            when (terminal) {
                is GenerationState.Complete -> {
                    lastResult = terminal.result
                    // Absolute path — LiteEngineIo reads app-private generation files directly.
                    currentPerson = PersonSource.UserPhoto(terminal.result.imagePath)

                    // Persist newly rendered trial into disk cache
                    if (tryOnDiskCache != null) {
                        tryOnDiskCache.put(request, activeProviderId, terminal.result)
                        refreshCacheStats()
                    }
                }
                is GenerationState.Failed -> {
                    _shoot.value = ShootState(shotIndex, totalShots, terminal, completed)
                    return null
                }
                else -> {
                    _shoot.value = ShootState(
                        shotIndex,
                        totalShots,
                        GenerationState.Failed(
                            TryOnError.Internal("Generation interrupted — tap Retry to try again."),
                        ),
                        completed,
                    )
                    return null
                }
            }
        }
        return lastResult
    }

    private fun Flow<GenerationState>.onEachReport(
        shotIndex: Int,
        totalShots: Int,
        layerIndex: Int,
        layerCount: Int,
        completed: List<TryOnResult>,
    ): Flow<GenerationState> = onEach { state ->
        val labelled = if (layerCount > 1 && state is GenerationState.Running) {
            state.copy(stage = "Layer ${layerIndex + 1}/$layerCount · ${state.stage}")
        } else {
            state
        }
        val forShoot = if (labelled is GenerationState.Complete && layerIndex < layerCount - 1) {
            GenerationState.Running(1f, "Layer ${layerIndex + 1} done")
        } else {
            labelled
        }
        _shoot.value = ShootState(shotIndex, totalShots, forShoot, completed)
        when (val inner = forShoot) {
            is GenerationState.Preparing -> {
                appendLive(inner.message)
                context?.let { ctx ->
                    com.zakir.vestra.service.GenerationForegroundService.updateProgress(
                        ctx,
                        "Virtual Try-On (Look ${shotIndex + 1}/$totalShots)",
                        inner.message,
                        progress = ((shotIndex.toFloat() / totalShots.coerceAtLeast(1)) * 100).toInt(),
                    )
                }
            }
            is GenerationState.Running -> {
                appendLive(inner.stage)
                context?.let { ctx ->
                    val overallProgress = (((shotIndex + inner.fraction) / totalShots.coerceAtLeast(1)) * 100).toInt()
                    com.zakir.vestra.service.GenerationForegroundService.updateProgress(
                        ctx,
                        "Virtual Try-On (Look ${shotIndex + 1}/$totalShots)",
                        inner.stage,
                        progress = overallProgress.coerceIn(0, 100),
                    )
                }
            }
            is GenerationState.Complete -> appendLive("Complete · ${inner.result.executedTier.name}")
            is GenerationState.Failed -> appendLive("Failed · ${inner.error.userMessage()}")
            GenerationState.Idle -> Unit
        }
    }

    private fun appendLive(line: String) {
        val stamped = line.take(160)
        _liveLog.value = (_liveLog.value + stamped).takeLast(40)
    }

    fun resetSession() {
        shootJob?.cancel()
        context?.let { com.zakir.vestra.service.GenerationForegroundService.stop(it) }
        _outfit.value = emptyList()
        _shots.value = emptyList()
        _casting.value = CastingProfile()
        _shoot.value = null
        _liveLog.value = emptyList()
        _generationStartedAtMs.value = null
    }

    fun cancelShoot() {
        shootJob?.cancel()
        shootJob = null
        context?.let { com.zakir.vestra.service.GenerationForegroundService.stop(it) }
        _shoot.value = null
        _liveLog.value = emptyList()
        _generationStartedAtMs.value = null
    }
}

private fun TryOnError.userMessage(): String = when (this) {
    TryOnError.ModelPackMissing ->
        "Model pack not installed"
    TryOnError.DeviceNotCapable ->
        "Device not capable"
    TryOnError.NetworkUnavailable ->
        "Network unavailable"
    is TryOnError.SafetyBlocked -> reason
    is TryOnError.Internal -> message.ifBlank { "Generation failed" }
}

private fun PersonSource.label(): String = when (this) {
    is PersonSource.UserPhoto -> "Your photo"
    is PersonSource.AiModel -> "Studio model"
}
