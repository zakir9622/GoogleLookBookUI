package com.zakir.vestra.ui.screens.settings

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.engine.EngineRouter
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.packs.PackHandshakeWires
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.settings.TokenPortals
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.storage.DurableStorage
import com.zakir.vestra.storage.TokenSidecar
import com.zakir.vestra.ui.components.GlassTopBar
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.util.rememberPackDownloadStarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsSection {
    HUB,
    CLOUD,
    ENGINES,
    APPEARANCE,
    ALL,
}

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    engineRouter: EngineRouter,
    packManager: ModelPackManager,
    freeCloudDiscovery: FreeCloudDiscovery,
    usageLedger: UsageLedger,
    onOpenPacks: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
    onBack: () -> Unit,
    section: SettingsSection = SettingsSection.ALL,
    onNavigateSection: ((SettingsSection) -> Unit)? = null,
) {
    if (section == SettingsSection.HUB) {
        SettingsHubScreen(
            onBack = onBack,
            onOpenCloud = { onNavigateSection?.invoke(SettingsSection.CLOUD) },
            onOpenEngines = { onNavigateSection?.invoke(SettingsSection.ENGINES) },
            onOpenAppearance = { onNavigateSection?.invoke(SettingsSection.APPEARANCE) },
            onOpenUsage = onOpenUsage,
            onOpenHelp = onOpenHelp,
            onOpenPrivacy = onOpenPrivacy,
            onOpenDiagnostics = onOpenDiagnostics,
        )
        return
    }

    val showCloud = section == SettingsSection.ALL || section == SettingsSection.CLOUD
    val showEngines = section == SettingsSection.ALL || section == SettingsSection.ENGINES
    val showAppearance = section == SettingsSection.ALL || section == SettingsSection.APPEARANCE
    val showGeneral = section == SettingsSection.ALL
    val sectionTitle = when (section) {
        SettingsSection.CLOUD -> "Cloud models & keys"
        SettingsSection.ENGINES -> "Engines & packs"
        SettingsSection.APPEARANCE -> "Appearance & privacy"
        else -> LookbookCopy.STUDIO_SETTINGS
    }
    val sectionSubtitle = when (section) {
        SettingsSection.CLOUD -> "HF · Groq · OpenRouter"
        SettingsSection.ENGINES -> "Lite · Pro · Cloud tier"
        SettingsSection.APPEARANCE -> "Theme · storage · permissions"
        else -> "API keys · engines · models · help"
    }
    val context = LocalContext.current
    val selectedTier by appSettings.engineTier.collectAsState()
    val appearance by appSettings.appearanceMode.collectAsState()
    val packStates by packManager.states.collectAsState()
    val packCatalogError by packManager.lastError.collectAsState()
    val startDownload = rememberPackDownloadStarter(showToast = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { packManager.refresh() }

    val tryOnId by appSettings.cloudProviderId.collectAsState()
    val imageGenId by appSettings.imageGenProviderId.collectAsState()
    val imageEditId by appSettings.imageEditProviderId.collectAsState()
    val codeId by appSettings.codeProviderId.collectAsState()
    val videoId by appSettings.videoProviderId.collectAsState()
    val audioId by appSettings.audioProviderId.collectAsState()

    val hfToken by appSettings.hfToken.collectAsState()
    val groqKey by appSettings.groqApiKey.collectAsState()
    val openRouterKey by appSettings.openRouterApiKey.collectAsState()
    val cloudModelsEnabled by appSettings.cloudModelsEnabled.collectAsState()

    var hfInput by remember(hfToken) { mutableStateOf(hfToken.orEmpty()) }
    var groqInput by remember(groqKey) { mutableStateOf(groqKey.orEmpty()) }
    var openRouterInput by remember(openRouterKey) { mutableStateOf(openRouterKey.orEmpty()) }
    var keysSavedFlash by remember { mutableStateOf(false) }
    var showTokenWizard by remember { mutableStateOf(false) }
    var confirmClearTokens by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var durableReady by remember { mutableStateOf(DurableStorage.hasAllFilesAccess()) }
    var clipboardHint by remember { mutableStateOf<String?>(null) }
    var permissionEpoch by remember { mutableStateOf(0) }

    val importTokensLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                TokenSidecar.importFromUri(context, uri, appSettings)
            }
            hfInput = appSettings.hfToken.value.orEmpty()
            groqInput = appSettings.groqApiKey.value.orEmpty()
            openRouterInput = appSettings.openRouterApiKey.value.orEmpty()
            keysSavedFlash = count > 0
            Toast.makeText(
                context,
                if (count > 0) "Imported $count key(s) from file" else "No HF/Groq/OpenRouter keys found in file",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun applyClipboardToken(): Boolean {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
        val raw = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        val detected = TokenSidecar.detectClipboardToken(raw) ?: run {
            clipboardHint = null
            return false
        }
        when (detected.first) {
            TokenPortals.Kind.HF -> hfInput = detected.second
            TokenPortals.Kind.GROQ -> groqInput = detected.second
            TokenPortals.Kind.OPENROUTER -> openRouterInput = detected.second
        }
        clipboardHint = "Detected ${detected.first.name} key from clipboard — tap Save API keys"
        return true
    }

    fun openPortal(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveTokens() {
        appSettings.setHfToken(hfInput.trim().ifBlank { null })
        appSettings.setGroqApiKey(groqInput.trim().ifBlank { null })
        appSettings.setOpenRouterApiKey(openRouterInput.trim().ifBlank { null })
        keysSavedFlash = true
        clipboardHint = null
        val saved = TokenSidecar.persist(context, appSettings)
        if (!saved && !DurableStorage.hasAllFilesAccess()) {
            Toast.makeText(
                context,
                "Tokens saved in-app. Download a model pack to enable durable storage so keys survive reinstall.",
                Toast.LENGTH_LONG,
            ).show()
        }
        scope.launch {
            runCatching { freeCloudDiscovery.refreshRouterDiscovery(appSettings) }
        }
        if (hfInput.isNotBlank()) showTokenWizard = true
    }

    if (showTokenWizard) {
        TokenSetupWizard(
            onDismiss = { showTokenWizard = false },
            onOpenPortal = { openPortal(it) },
            hfConfigured = hfInput.isNotBlank(),
            groqConfigured = groqInput.isNotBlank(),
            openRouterConfigured = openRouterInput.isNotBlank(),
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                durableReady = DurableStorage.hasAllFilesAccess()
                permissionEpoch += 1
                applyClipboardToken()
                if (durableReady) {
                    scope.launch { packManager.refresh() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val localPackChoices = remember { LocalModelCatalog.entries.filter { it.packId != null && it.runnable } }
    var selectedPackId by remember {
        val preferred = when (appSettings.engineTier.value) {
            EngineTier.LITE -> localPackChoices.firstOrNull { it.engineTier == EngineTier.LITE }?.packId
            EngineTier.PRO -> localPackChoices.firstOrNull { it.engineTier == EngineTier.PRO }?.packId
            EngineTier.AUTO, EngineTier.CLOUD -> null
        }
        mutableStateOf(preferred ?: localPackChoices.firstOrNull()?.packId.orEmpty())
    }
    var handshakeBusy by remember { mutableStateOf(false) }
    var handshakeDetail by remember { mutableStateOf<String?>(null) }
    var handshakeOk by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(selectedTier) {
        val match = when (selectedTier) {
            EngineTier.LITE -> localPackChoices.firstOrNull { it.engineTier == EngineTier.LITE }?.packId
            EngineTier.PRO -> localPackChoices.firstOrNull { it.engineTier == EngineTier.PRO }?.packId
            else -> null
        }
        if (match != null && selectedPackId != match) {
            selectedPackId = match
        }
    }

    if (confirmClearTokens) {
        AlertDialog(
            onDismissRequest = { confirmClearTokens = false },
            title = { Text("Clear API keys?") },
            text = { Text("Removes Hugging Face, Groq, and OpenRouter keys from this device. Cloud models will lock until you paste keys again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appSettings.clearApiTokens()
                        TokenSidecar.clearFile()
                        hfInput = ""
                        groqInput = ""
                        openRouterInput = ""
                        keysSavedFlash = false
                        confirmClearTokens = false
                        Toast.makeText(context, "API keys cleared", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearTokens = false }) { Text("Cancel") }
            },
        )
    }

    SpatialBackground {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "top") {
                GlassTopBar(
                    title = sectionTitle,
                    subtitle = sectionSubtitle,
                    navigation = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            if (showGeneral) {
                settingsGeneralSection(
                    onOpenHelp = onOpenHelp,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenDiagnostics = onOpenDiagnostics,
                )
            }

            if (showCloud) {
                settingsCloudMasterToggleSection(appSettings = appSettings)
            }

            if (showCloud && cloudModelsEnabled) {
                settingsCloudKeysSection(
                    appSettings = appSettings,
                    hfTokenSaved = !hfToken.isNullOrBlank(),
                    hfInput = hfInput,
                    groqInput = groqInput,
                    openRouterInput = openRouterInput,
                    onHfInput = { hfInput = it },
                    onGroqInput = { groqInput = it },
                    onOpenRouterInput = { openRouterInput = it },
                    keysSavedFlash = keysSavedFlash,
                    clipboardHint = clipboardHint,
                    durableReady = durableReady,
                    onApplyClipboard = { applyClipboardToken() },
                    onOpenPortal = ::openPortal,
                    onSaveTokens = ::saveTokens,
                    importTokensLauncher = importTokensLauncher,
                    onKeysLoadedFromDocuments = { count ->
                        hfInput = appSettings.hfToken.value.orEmpty()
                        groqInput = appSettings.groqApiKey.value.orEmpty()
                        openRouterInput = appSettings.openRouterApiKey.value.orEmpty()
                        keysSavedFlash = count > 0
                    },
                )
            }

            if (showCloud || showAppearance) {
                settingsDurableStatusSection(
                    appSettings = appSettings,
                    durableReady = durableReady,
                )
            }

            if (showAppearance) {
                settingsThemeSection(
                    appSettings = appSettings,
                    appearance = appearance,
                )
            }

            if (showEngines) {
                settingsEnginesSection(
                    appSettings = appSettings,
                    engineRouter = engineRouter,
                    selectedTier = selectedTier,
                    selectedPackId = selectedPackId,
                    onSelectPackId = { selectedPackId = it },
                    localPackChoices = localPackChoices,
                    packStates = packStates,
                    packCatalogError = packCatalogError,
                    startDownload = startDownload,
                    onOpenPacks = onOpenPacks,
                    onOpenUsage = onOpenUsage,
                    handshakeBusy = handshakeBusy,
                    handshakeDetail = handshakeDetail,
                    handshakeOk = handshakeOk,
                    onHandshakeSelected = {
                        if (handshakeBusy || selectedPackId.isBlank()) return@settingsEnginesSection
                        scope.launch {
                            handshakeBusy = true
                            handshakeDetail = "Handshaking ${selectedPackId}…"
                            handshakeOk = null
                            val result = withContext(Dispatchers.Default) {
                                packManager.handshake(selectedPackId)
                            }
                            handshakeBusy = false
                            handshakeOk = result.ok
                            handshakeDetail = PackHandshakeWires.formatDetail(result)
                            Toast.makeText(
                                context,
                                PackHandshakeWires.formatUserSummary(result),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onHandshakeAll = {
                        if (handshakeBusy) return@settingsEnginesSection
                        scope.launch {
                            handshakeBusy = true
                            handshakeDetail = "Handshaking all installed packs…"
                            handshakeOk = null
                            val report = withContext(Dispatchers.Default) {
                                packManager.handshakeAll()
                            }
                            handshakeBusy = false
                            handshakeOk = report.allOk && report.results.isNotEmpty()
                            handshakeDetail = buildString {
                                append(report.summary)
                                report.results.take(4).forEach { r ->
                                    append('\n')
                                    append(PackHandshakeWires.formatUserSummary(r))
                                }
                                if (report.results.size > 4) {
                                    append("\n… +${report.results.size - 4} more")
                                }
                            }
                            Toast.makeText(context, report.summary, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }

            if (showCloud && cloudModelsEnabled) {
                settingsCloudCapabilitiesSection(
                    appSettings = appSettings,
                    freeCloudDiscovery = freeCloudDiscovery,
                    tryOnId = tryOnId,
                    imageGenId = imageGenId,
                    imageEditId = imageEditId,
                    codeId = codeId,
                    videoId = videoId,
                    audioId = audioId,
                )
            }

            if (showAppearance) {
                settingsStoragePermissionsSection(
                    clearingCache = clearingCache,
                    onClearingCache = { clearingCache = it },
                    usageLedger = usageLedger,
                    permissionEpoch = permissionEpoch,
                    onConfirmClearTokens = { confirmClearTokens = true },
                )
            }
        }
    }
}
