package com.zakir.vestra

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.russhwolf.settings.SharedPreferencesSettings
import com.zakir.vestra.data.StudioModelRepository
import com.zakir.vestra.shared.cloud.AndroidCloudIo
import com.zakir.vestra.shared.cloud.CloudEngine
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.GenerativeCloudService
import com.zakir.vestra.shared.domain.effectiveCategory
import com.zakir.vestra.shared.engine.EngineRouter
import com.zakir.vestra.shared.engine.lite.HumanParsing
import com.zakir.vestra.shared.engine.lite.LiteEngine
import com.zakir.vestra.shared.engine.lite.LiteEngineIo
import com.zakir.vestra.shared.engine.pro.DiffusionEngine
import com.zakir.vestra.shared.packs.AndroidDeviceProbe
import com.zakir.vestra.shared.packs.AndroidPackFileSystem
import com.zakir.vestra.shared.packs.AndroidPackIntegrityChecker
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.packs.PackDownloadWorker
import com.zakir.vestra.shared.platformHttpClient
import com.zakir.vestra.shared.quality.createQualityPostProcessor
import com.zakir.vestra.shared.chat.ChatRepository
import com.zakir.vestra.shared.diagnostics.DiagnosticsHook
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.wardrobe.AndroidTextFileStore
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.storage.DurableStorage
import com.zakir.vestra.storage.TokenSidecar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VestraApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var appSettings: AppSettings
        private set

    lateinit var engineRouter: EngineRouter
        private set

    lateinit var wardrobe: WardrobeRepository
        private set

    lateinit var packManager: ModelPackManager
        private set

    lateinit var studioModels: StudioModelRepository
        private set

    lateinit var usageLedger: UsageLedger
        private set

    lateinit var runDiagnostics: RunDiagnostics
        private set

    lateinit var localJobStore: com.zakir.vestra.shared.jobs.LocalJobStore
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var deviceProbe: AndroidDeviceProbe
        private set

    lateinit var generative: GenerativeCloudService
        private set

    lateinit var freeCloudDiscovery: FreeCloudDiscovery
        private set

    lateinit var humanParsing: HumanParsing
        private set

    lateinit var liteEngineIo: LiteEngineIo
        private set

    override fun onCreate() {
        super.onCreate()
        // Install BEFORE other init so early crashes are captured and logs append forever.
        com.zakir.vestra.diagnostics.CrashReporter.install(this)
        val prefs = SharedPreferencesSettings(getSharedPreferences("vestra_settings", MODE_PRIVATE))
        appSettings = AppSettings(prefs)
        // CPU-only ORT by default; Engines toggle can opt into NNAPI.
        com.zakir.vestra.shared.engine.lite.OrtEpPolicy.preferNnapi = appSettings.preferNnapi.value
        appScope.launch {
            appSettings.preferNnapi.collect { enabled ->
                com.zakir.vestra.shared.engine.lite.OrtEpPolicy.preferNnapi = enabled
                com.zakir.vestra.shared.engine.lite.OrtSessionCache.clearAll()
            }
        }
        appSettings.networkProbe = { isNetworkAvailable(this) }
        registerNetworkCallback(this) {
            // Probe is re-read on each call; callback keeps Home status from going sticky-offline.
        }
        usageLedger = UsageLedger(prefs)
        deviceProbe = AndroidDeviceProbe(this)
        runDiagnostics = RunDiagnostics(prefs) { encoded ->
            runCatching {
                val dir = java.io.File(filesDir, "diagnostics").apply { mkdirs() }
                java.io.File(dir, "run_history.json").writeText(encoded)
            }
        }
        DiagnosticsHook.store = runDiagnostics
        DiagnosticsHook.deviceRamMb = deviceProbe.totalRamMb()
        localJobStore = com.zakir.vestra.shared.jobs.LocalJobStore(prefs)
        chatRepository = ChatRepository(prefs)
        wardrobe = WardrobeRepository(AndroidTextFileStore(filesDir))

        val http = platformHttpClient()
        freeCloudDiscovery = FreeCloudDiscovery(http)
        // Restore tokens from Documents/TheLookbook/tokens.json after reinstall.
        TokenSidecar.restoreIntoPrefsIfEmpty(this, appSettings)
        // Optional sideload seed from local.properties / LOOKBOOK_HF_TOKEN (gitignored).
        TokenSidecar.applyDefaultHfIfBlank(appSettings, BuildConfig.DEFAULT_HF_TOKEN)
        TokenSidecar.applyDefaultOpenRouterIfBlank(appSettings, BuildConfig.DEFAULT_OPENROUTER_TOKEN)
        TokenSidecar.applyDefaultGroqIfBlank(appSettings, BuildConfig.DEFAULT_GROQ_TOKEN)
        if (DurableStorage.hasAllFilesAccess()) {
            TokenSidecar.autoFetchFromDocuments(appSettings, overwriteExisting = false)
        }
        packManager = ModelPackManager(
            fs = AndroidPackFileSystem(this) { DurableStorage.resolvePacksRoot(this) },
            device = deviceProbe,
            http = http,
            manifestUrl = PACKS_MANIFEST_URL,
            integrityChecker = AndroidPackIntegrityChecker(),
            onPackFilesChanging = { packRoot ->
                com.zakir.vestra.shared.engine.lite.OrtSessionCache.invalidateContaining(packRoot)
            },
        )
        PackDownloadWorker.dependencies = { packManager }
        appScope.launch {
            packManager.refresh(networkAllowed = isNetworkAvailable(this@VestraApp))
            // Seed bundled lite pack before verification so we never ONNX-load a half-written copy.
            withContext(Dispatchers.IO) {
                DebugPackBootstrap.seedLitePack(this@VestraApp, DurableStorage.resolvePacksRoot(this@VestraApp))
            }
            packManager.refresh(networkAllowed = isNetworkAvailable(this@VestraApp))
            packManager.verifyAllInstalled()
        }
        appScope.launch {
            if (!appSettings.hfToken.value.isNullOrBlank()) {
                runCatching { freeCloudDiscovery.refreshRouterDiscovery(appSettings) }
            }
        }
        studioModels = StudioModelRepository(this, packManager)

        liteEngineIo = LiteEngineIo(this) { modelId -> studioModels.resolveBitmap(modelId) }
        humanParsing = HumanParsing(packManager)
        val quality = createQualityPostProcessor(packManager)
        val cloudIo = AndroidCloudIo(
            this,
            liteEngineIo,
            http,
            applyVisibleWatermark = true, // always stamp AI provenance on cloud outputs
        )
        val generationsDir = java.io.File(filesDir, "generations").also { it.mkdirs() }
        val localImageGen = com.zakir.vestra.shared.engine.local.AndroidLocalImageGenerator(
            packManager,
            outputDir = generationsDir,
            loadReferenceBitmap = { uriString ->
                runCatching {
                    val uri = android.net.Uri.parse(uriString)
                    contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            },
            quality = quality,
        )
        val bonsaiImageGen = com.zakir.vestra.shared.engine.local.BonsaiImageEngine(
            packManager,
            outputDir = generationsDir,
            quality = quality,
        )
        val routedLocalImageGen = com.zakir.vestra.shared.engine.local.RoutingLocalImageGenerator(
            appSettings,
            sdturbo = localImageGen,
            bonsai = bonsaiImageGen,
        )
        generative = GenerativeCloudService(
            http,
            cloudIo,
            appSettings,
            usageLedger,
            localImage = routedLocalImageGen,
            localAudio = com.zakir.vestra.shared.engine.local.AndroidLocalAudioGenerator(
                this,
                packManager,
                outputDir = generationsDir,
            ),
            localVoiceChanger = com.zakir.vestra.shared.engine.local.AndroidLocalVoiceChanger(
                outputDir = generationsDir,
            ),
            localCode = com.zakir.vestra.shared.engine.local.RoutingLocalCodeGenerator(
                appSettings,
                gemma4 = com.zakir.vestra.shared.engine.local.AndroidLiteRtLmCodeGenerator(
                    this,
                    packManager,
                    useGpu = { appSettings.preferLiteRtLmGpu.value },
                ),
                qwen3 = com.zakir.vestra.shared.engine.local.AndroidLiteRtLmCodeGenerator(
                    this,
                    packManager,
                    packId = com.zakir.vestra.shared.engine.local.LiteRtLmPacks.QWEN3_CODE,
                    useGpu = { appSettings.preferLiteRtLmGpu.value },
                    primaryFile = com.zakir.vestra.shared.engine.local.LiteRtLmPacks.QWEN3_FILE,
                    minBytes = com.zakir.vestra.shared.engine.local.LiteRtLmPackLimits.MIN_QWEN3_BYTES,
                    downloadHint = "~331 MB",
                ),
                legacyGemma3 = com.zakir.vestra.shared.engine.local.AndroidLegacyMediaPipeCodeGenerator(
                    this,
                    packManager,
                ),
                functionGemma = com.zakir.vestra.shared.engine.local.AndroidFunctionGemmaTools(
                    this,
                    packManager,
                    useGpu = { appSettings.preferLiteRtLmGpu.value },
                    toolSet = com.zakir.vestra.shared.engine.local.LookbookStudioToolSet(
                        onAppendPrompt = com.zakir.vestra.shared.engine.local.LocalStudioToolBridge.onAppendPrompt,
                        onSetEngineTier = com.zakir.vestra.shared.engine.local.LocalStudioToolBridge.onSetEngineTier,
                        onSetBackdrop = com.zakir.vestra.shared.engine.local.LocalStudioToolBridge.onSetBackdrop,
                    ),
                ),
            ),
            localVideo = com.zakir.vestra.shared.engine.local.AndroidLocalVideoGenerator(
                localImageGen,
                outputDir = generationsDir,
                packs = packManager,
            ),
            localVision = com.zakir.vestra.shared.engine.local.AndroidLocalVisionAssist(
                this,
                packManager,
                useGpu = { appSettings.preferLiteRtLmGpu.value },
            ),
            localTranscriber = com.zakir.vestra.shared.engine.local.AndroidLocalAudioTranscriber(
                this,
                packManager,
                useGpu = { appSettings.preferLiteRtLmGpu.value },
            ),
        )

        engineRouter = EngineRouter(
            listOf(
                LiteEngine(packManager, liteEngineIo, humanParsing, quality),
                DiffusionEngine(
                    packs = packManager,
                    device = deviceProbe,
                    io = liteEngineIo,
                    masker = { person, category -> humanParsing.analyze(person, category.effectiveCategory())?.mask },
                    parsing = humanParsing,
                    applyWatermark = BuildConfig.APPLY_WATERMARK,
                    quality = quality,
                ),
                CloudEngine(http, cloudIo, appSettings, usageLedger),
            ),
        )
    }

    companion object {
        const val PACKS_MANIFEST_URL =
            "https://huggingface.co/datasets/Iamzakirzr/vestra-packs/resolve/main/manifest.json"

        @Suppress("DEPRECATION")
        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
            fun capable(network: android.net.Network): Boolean {
                val caps = cm.getNetworkCapabilities(network) ?: return false
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true
                // Some carriers/VPN handoffs leave INTERNET unset briefly while transport is up.
                return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
            cm.activeNetwork?.let { if (capable(it)) return true }
            return cm.allNetworks.any { capable(it) }
        }

        /** Keep AppSettings probe fresh when connectivity changes (avoids sticky Offline). */
        fun registerNetworkCallback(app: Application, onChange: () -> Unit) {
            val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
            val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching {
                cm.registerNetworkCallback(
                    request,
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: android.net.Network) = onChange()
                        override fun onLost(network: android.net.Network) = onChange()
                        override fun onCapabilitiesChanged(
                            network: android.net.Network,
                            networkCapabilities: NetworkCapabilities,
                        ) = onChange()
                    },
                )
            }
        }
    }
}
