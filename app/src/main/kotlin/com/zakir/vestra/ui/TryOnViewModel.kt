package com.zakir.vestra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakir.vestra.shared.domain.Backdrop
import com.zakir.vestra.shared.domain.CastingProfile
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

    private var shootJob: Job? = null

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

        shootJob = viewModelScope.launch {
            val completed = mutableListOf<TryOnResult>()
            for ((shotIndex, person) in shots.withIndex()) {
                val shotResult = renderShot(person, layers, shotIndex, shots.size, completed)
                    ?: return@launch
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
            val terminal = engineRouter.generate(
                TryOnRequest(
                    garment = piece,
                    person = currentPerson,
                    tier = appSettings.engineTier.value,
                    backdrop = if (isLastLayer) _backdrop.value else Backdrop.ORIGINAL,
                    casting = _casting.value,
                ),
            ).onEachReport(shotIndex, totalShots, layerIndex, layers.size, completed).last()

            when (terminal) {
                is GenerationState.Complete -> {
                    lastResult = terminal.result
                    // Absolute path — LiteEngineIo reads app-private generation files directly.
                    currentPerson = PersonSource.UserPhoto(terminal.result.imagePath)
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
            is GenerationState.Preparing -> appendLive(inner.message)
            is GenerationState.Running -> appendLive(inner.stage)
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
