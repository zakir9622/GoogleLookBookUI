package com.zakir.vestra.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.zakir.vestra.data.NewsFeedConfig
import com.zakir.vestra.diagnostics.CrashReporter
import com.zakir.vestra.shared.chat.ChatRepository
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.GenerativeCloudService
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.engine.EngineRouter
import com.zakir.vestra.shared.jobs.LocalJobStore
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.platformHttpClient
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.ui.components.VestraBottomNavBar
import com.zakir.vestra.ui.screens.capture.GarmentScreen
import com.zakir.vestra.ui.screens.casting.CastingStudioScreen
import com.zakir.vestra.ui.screens.generate.GenerationScreen
import com.zakir.vestra.ui.screens.help.HelpScreen
import com.zakir.vestra.ui.screens.home.HomeScreen
import com.zakir.vestra.ui.screens.home.IsolatedStudioScreen
import com.zakir.vestra.ui.screens.models.ModelConfigScreen
import com.zakir.vestra.ui.screens.news.IsolatedNewsScreen
import com.zakir.vestra.ui.screens.onboarding.OnboardingScreen
import com.zakir.vestra.ui.screens.packs.PacksScreen
import com.zakir.vestra.ui.screens.person.PersonSourceScreen
import com.zakir.vestra.ui.screens.privacy.PrivacyScreen
import com.zakir.vestra.ui.screens.result.ResultScreen
import com.zakir.vestra.ui.screens.settings.DiagnosticsScreen
import com.zakir.vestra.ui.screens.settings.SettingsScreen
import com.zakir.vestra.ui.screens.settings.SettingsSection
import com.zakir.vestra.ui.screens.studio.TryOnStudioScreen
import com.zakir.vestra.ui.screens.usage.UsageScreen
import com.zakir.vestra.ui.screens.wardrobe.WardrobeScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val STUDIO = "studio/{tab}"
    fun studioHome(tab: String = "home") = "home"

    const val LIBRARY = "library"
    const val WARDROBE = "wardrobe"

    const val SETTINGS = "settings"
    const val SETTINGS_CLOUD = "settings/cloud"
    const val SETTINGS_ENGINES = "settings/engines"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_DIAGNOSTICS = "settings/diagnostics"

    // Isolated model generator routes
    const val STUDIO_IMAGE = "studio/image"
    const val STUDIO_VIDEO = "studio/video"
    const val STUDIO_CODE = "studio/code"
    const val STUDIO_AUDIO = "studio/audio"
    const val STUDIO_NEWS = "studio/news"

    const val GARMENT = "garment"
    const val CASTING = "casting"
    const val PERSON = "person"
    const val GENERATE = "generate"
    const val RESULT = "result"
    const val PACKS = "packs"
    const val CREATE = "create"
    const val CODE = "code"
    const val VIDEO = "video"
    const val AUDIO = "audio"
    const val USAGE = "usage"
    const val HELP = "help"
    const val PRIVACY = "privacy"
    const val MODEL_CONFIG = "model_config"

    fun deepLink(route: String) = "lookbook://screen/$route"
}

@Composable
fun VestraNavHost(
    appSettings: AppSettings,
    engineRouter: EngineRouter,
    wardrobe: WardrobeRepository,
    packManager: ModelPackManager,
    studioModels: com.zakir.vestra.data.StudioModelRepository,
    generative: GenerativeCloudService,
    usageLedger: UsageLedger,
    runDiagnostics: RunDiagnostics,
    localJobStore: LocalJobStore,
    chatRepository: ChatRepository,
    deviceRamMb: Long,
    freeCloudDiscovery: FreeCloudDiscovery,
    humanParsing: com.zakir.vestra.shared.engine.lite.HumanParsing,
    liteEngineIo: com.zakir.vestra.shared.engine.lite.LiteEngineIo,
    tryOnDiskCache: com.zakir.vestra.cache.TryOnDiskCache? = null,
    navController: NavHostController = rememberNavController(),
    pendingDeepLinkIntent: Intent? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val onboardingComplete by appSettings.onboardingComplete.collectAsState()
    val start = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING

    LaunchedEffect(pendingDeepLinkIntent, onboardingComplete) {
        val intent = pendingDeepLinkIntent ?: return@LaunchedEffect
        if (!onboardingComplete) return@LaunchedEffect
        val route = intent.data
            ?.takeIf { it.scheme == "lookbook" && it.host == "screen" }
            ?.pathSegments
            ?.firstOrNull()
        if (route.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        onDeepLinkHandled()
    }

    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    val tryOnViewModel: TryOnViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TryOnViewModel(
                    engineRouter,
                    appSettings,
                    wardrobe,
                    runDiagnostics,
                    deviceRamMb,
                    tryOnDiskCache,
                    appContext,
                ) as T
        },
    )

    val generativeViewModel: GenerativeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GenerativeViewModel(
                    generative,
                    appSettings,
                    usageLedger,
                    wardrobe,
                    runDiagnostics,
                    deviceRamMb,
                    localJobStore,
                    appContext,
                ) as T
        },
    )

    val newsRepository = remember(context) {
        NewsRepository(platformHttpClient(), NewsFeedConfig.load(context))
    }
    val chatViewModel: ChatViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    chatRepository,
                    newsRepository,
                    generative,
                    appSettings,
                    runDiagnostics,
                    deviceRamMb,
                ) as T
        },
    )

    val navEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navEntry?.destination?.route) {
        val route = navEntry?.destination?.route ?: "unknown"
        val tab = navEntry?.arguments?.getString("tab")
        CrashReporter.breadcrumb(if (tab != null) "$route#$tab" else route)
    }

    val currentRoute = navEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.HOME,
        Routes.LIBRARY,
        Routes.WARDROBE,
        Routes.SETTINGS,
        Routes.STUDIO,
        "home",
        "library",
        "wardrobe",
        "settings",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = start,
            enterTransition = {
                fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialOffsetX = { (it * 0.08f).toInt() },
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        targetOffsetX = { -(it * 0.08f).toInt() },
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialOffsetX = { -(it * 0.08f).toInt() },
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        targetOffsetX = { (it * 0.08f).toInt() },
                    )
            },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    appSettings = appSettings,
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.HOME,
                deepLinks = listOf(
                    navDeepLink { uriPattern = Routes.deepLink("home") },
                    navDeepLink { uriPattern = Routes.deepLink("studio") },
                ),
            ) {
                HomeScreen(
                    appSettings = appSettings,
                    wardrobe = wardrobe,
                    packManager = packManager,
                    generativeViewModel = generativeViewModel,
                    freeCloudDiscovery = freeCloudDiscovery,
                    engineRouter = engineRouter,
                    usageLedger = usageLedger,
                    localJobStore = localJobStore,
                    newsRepository = newsRepository,
                    chatViewModel = chatViewModel,
                    onNewLook = {
                        tryOnViewModel.resetSession()
                        navController.navigate(Routes.GARMENT)
                    },
                    onOpenWardrobe = {
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPacks = { navController.navigate(Routes.PACKS) },
                    onOpenHelp = { navController.navigate(Routes.HELP) },
                    onOpenNewsChat = {},
                    initialTabRoute = "home",
                    onOpenImageStudio = { navController.navigate(Routes.STUDIO_IMAGE) },
                    onOpenVideoStudio = { navController.navigate(Routes.STUDIO_VIDEO) },
                    onOpenCodeStudio = { navController.navigate(Routes.STUDIO_CODE) },
                    onOpenAudioStudio = { navController.navigate(Routes.STUDIO_AUDIO) },
                    onOpenNewsScreen = { navController.navigate(Routes.STUDIO_NEWS) },
                )
            }

            composable(
                route = Routes.STUDIO,
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = "home"
                    },
                ),
                deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink("studio/{tab}") }),
            ) { backStackEntry ->
                val tab = backStackEntry.arguments?.getString("tab")
                HomeScreen(
                    appSettings = appSettings,
                    wardrobe = wardrobe,
                    packManager = packManager,
                    generativeViewModel = generativeViewModel,
                    freeCloudDiscovery = freeCloudDiscovery,
                    engineRouter = engineRouter,
                    usageLedger = usageLedger,
                    localJobStore = localJobStore,
                    newsRepository = newsRepository,
                    chatViewModel = chatViewModel,
                    onNewLook = {
                        tryOnViewModel.resetSession()
                        navController.navigate(Routes.GARMENT)
                    },
                    onOpenWardrobe = {
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPacks = { navController.navigate(Routes.PACKS) },
                    onOpenHelp = { navController.navigate(Routes.HELP) },
                    onOpenNewsChat = {},
                    initialTabRoute = tab,
                    onOpenImageStudio = { navController.navigate(Routes.STUDIO_IMAGE) },
                    onOpenVideoStudio = { navController.navigate(Routes.STUDIO_VIDEO) },
                    onOpenCodeStudio = { navController.navigate(Routes.STUDIO_CODE) },
                    onOpenAudioStudio = { navController.navigate(Routes.STUDIO_AUDIO) },
                    onOpenNewsScreen = { navController.navigate(Routes.STUDIO_NEWS) },
                )
            }

        // Dedicated Isolated Studio Pages for each Modality
        composable(
            route = Routes.STUDIO_IMAGE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.CREATE) }),
        ) {
            IsolatedStudioScreen(
                capability = AiCapability.IMAGE_GEN,
                title = "Image Studio",
                subtitle = "Text-to-image & img2img couture diffusion",
                icon = Icons.Outlined.Image,
                accentColor = Color(0xFF38BDF8),
                viewModel = generativeViewModel,
                appSettings = appSettings,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }

        composable(
            route = Routes.STUDIO_VIDEO,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.VIDEO) }),
        ) {
            IsolatedStudioScreen(
                capability = AiCapability.VIDEO,
                title = "Video Studio",
                subtitle = "Cinematic fabric drape & motion sweeps",
                icon = Icons.Outlined.Videocam,
                accentColor = Color(0xFFF59E0B),
                viewModel = generativeViewModel,
                appSettings = appSettings,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }

        composable(
            route = Routes.STUDIO_CODE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.CODE) }),
        ) {
            IsolatedStudioScreen(
                capability = AiCapability.CODE,
                title = "Code Studio",
                subtitle = "LiteRT Gemma on-device fashion reasoning & UI",
                icon = Icons.Outlined.Code,
                accentColor = Color(0xFF10B981),
                viewModel = generativeViewModel,
                appSettings = appSettings,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }

        composable(
            route = Routes.STUDIO_AUDIO,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.AUDIO) }),
        ) {
            IsolatedStudioScreen(
                capability = AiCapability.AUDIO,
                title = "Audio Lab",
                subtitle = "Offline TTS & DSP voice pitch/formant shifting",
                icon = Icons.Outlined.GraphicEq,
                accentColor = Color(0xFFEC4899),
                viewModel = generativeViewModel,
                appSettings = appSettings,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }

        composable(
            route = Routes.STUDIO_NEWS,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink("news") }),
        ) {
            IsolatedNewsScreen(
                newsRepository = newsRepository,
                chatViewModel = chatViewModel,
                appSettings = appSettings,
                freeCloudDiscovery = freeCloudDiscovery,
                packManager = packManager,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }

        composable(
            route = Routes.GARMENT,
            deepLinks = listOf(
                navDeepLink { uriPattern = Routes.deepLink(Routes.GARMENT) },
                navDeepLink { uriPattern = Routes.deepLink("tryon") },
            ),
        ) {
            TryOnStudioScreen(
                viewModel = tryOnViewModel,
                appSettings = appSettings,
                wardrobe = wardrobe,
                studioModels = studioModels,
                humanParsing = humanParsing,
                liteEngineIo = liteEngineIo,
                freeCloudDiscovery = freeCloudDiscovery,
                packManager = packManager,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }
        composable(Routes.CASTING) {
            TryOnStudioScreen(
                viewModel = tryOnViewModel,
                appSettings = appSettings,
                wardrobe = wardrobe,
                studioModels = studioModels,
                humanParsing = humanParsing,
                liteEngineIo = liteEngineIo,
                freeCloudDiscovery = freeCloudDiscovery,
                packManager = packManager,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }
        composable(Routes.PERSON) {
            TryOnStudioScreen(
                viewModel = tryOnViewModel,
                appSettings = appSettings,
                wardrobe = wardrobe,
                studioModels = studioModels,
                humanParsing = humanParsing,
                liteEngineIo = liteEngineIo,
                freeCloudDiscovery = freeCloudDiscovery,
                packManager = packManager,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }
        composable(Routes.GENERATE) {
            TryOnStudioScreen(
                viewModel = tryOnViewModel,
                appSettings = appSettings,
                wardrobe = wardrobe,
                studioModels = studioModels,
                humanParsing = humanParsing,
                liteEngineIo = liteEngineIo,
                freeCloudDiscovery = freeCloudDiscovery,
                packManager = packManager,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }
        composable(Routes.RESULT) {
            TryOnStudioScreen(
                viewModel = tryOnViewModel,
                appSettings = appSettings,
                wardrobe = wardrobe,
                studioModels = studioModels,
                humanParsing = humanParsing,
                liteEngineIo = liteEngineIo,
                freeCloudDiscovery = freeCloudDiscovery,
                packManager = packManager,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }
        composable(
            route = Routes.CREATE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.CREATE) }),
        ) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.STUDIO_IMAGE) {
                    launchSingleTop = true
                    popUpTo(Routes.CREATE) { inclusive = true }
                }
            }
        }
        composable(
            route = Routes.CODE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.CODE) }),
        ) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.STUDIO_CODE) {
                    launchSingleTop = true
                    popUpTo(Routes.CODE) { inclusive = true }
                }
            }
        }
        composable(
            route = Routes.VIDEO,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.VIDEO) }),
        ) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.STUDIO_VIDEO) {
                    launchSingleTop = true
                    popUpTo(Routes.VIDEO) { inclusive = true }
                }
            }
        }
        composable(
            route = Routes.AUDIO,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.AUDIO) }),
        ) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.STUDIO_AUDIO) {
                    launchSingleTop = true
                    popUpTo(Routes.AUDIO) { inclusive = true }
                }
            }
        }
        composable(
            route = Routes.USAGE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.USAGE) }),
        ) {
            UsageScreen(
                usage = usageLedger,
                appSettings = appSettings,
                packManager = packManager,
                onBack = { navController.popBackStack() },
                onOpenCreate = {
                    generativeViewModel.prepareStudio(resetIfIdle = true)
                    navController.navigate(Routes.STUDIO_IMAGE)
                },
            )
        }
        composable(
            route = Routes.HELP,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.HELP) }),
        ) {
            HelpScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.PRIVACY,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.PRIVACY) }),
        ) {
            PrivacyScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.LIBRARY,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.LIBRARY) }),
        ) {
            WardrobeScreen(
                wardrobe = wardrobe,
                onBack = null,
                onStartTryOn = {
                    tryOnViewModel.resetSession()
                    navController.navigate(Routes.GARMENT)
                },
            )
        }
        composable(
            route = Routes.WARDROBE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.WARDROBE) }),
        ) {
            WardrobeScreen(
                wardrobe = wardrobe,
                onBack = null,
                onStartTryOn = {
                    tryOnViewModel.resetSession()
                    navController.navigate(Routes.GARMENT)
                },
            )
        }
        composable(
            route = Routes.SETTINGS,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.SETTINGS) }),
        ) {
            SettingsScreen(
                appSettings = appSettings,
                engineRouter = engineRouter,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                usageLedger = usageLedger,
                onOpenPacks = { navController.navigate(Routes.PACKS) },
                onOpenUsage = { navController.navigate(Routes.USAGE) },
                onOpenHelp = { navController.navigate(Routes.HELP) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenDiagnostics = { navController.navigate(Routes.SETTINGS_DIAGNOSTICS) },
                onOpenModelConfig = { navController.navigate(Routes.MODEL_CONFIG) },
                onBack = null,
                section = SettingsSection.HUB,
                onNavigateSection = { target ->
                    navController.navigate(
                        when (target) {
                            SettingsSection.CLOUD -> Routes.SETTINGS_CLOUD
                            SettingsSection.ENGINES -> Routes.SETTINGS_ENGINES
                            SettingsSection.APPEARANCE -> Routes.SETTINGS_APPEARANCE
                            else -> Routes.SETTINGS
                        },
                    )
                },
            )
        }
        composable(Routes.SETTINGS_CLOUD) {
            SettingsScreen(
                appSettings = appSettings,
                engineRouter = engineRouter,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                usageLedger = usageLedger,
                onOpenPacks = { navController.navigate(Routes.PACKS) },
                onOpenUsage = { navController.navigate(Routes.USAGE) },
                onOpenHelp = { navController.navigate(Routes.HELP) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenDiagnostics = { navController.navigate(Routes.SETTINGS_DIAGNOSTICS) },
                onBack = { navController.popBackStack() },
                section = SettingsSection.CLOUD,
            )
        }
        composable(Routes.SETTINGS_ENGINES) {
            SettingsScreen(
                appSettings = appSettings,
                engineRouter = engineRouter,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                usageLedger = usageLedger,
                onOpenPacks = { navController.navigate(Routes.PACKS) },
                onOpenUsage = { navController.navigate(Routes.USAGE) },
                onOpenHelp = { navController.navigate(Routes.HELP) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenDiagnostics = { navController.navigate(Routes.SETTINGS_DIAGNOSTICS) },
                onBack = { navController.popBackStack() },
                section = SettingsSection.ENGINES,
            )
        }
        composable(Routes.SETTINGS_APPEARANCE) {
            SettingsScreen(
                appSettings = appSettings,
                engineRouter = engineRouter,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                usageLedger = usageLedger,
                onOpenPacks = { navController.navigate(Routes.PACKS) },
                onOpenUsage = { navController.navigate(Routes.USAGE) },
                onOpenHelp = { navController.navigate(Routes.HELP) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenDiagnostics = { navController.navigate(Routes.SETTINGS_DIAGNOSTICS) },
                onBack = { navController.popBackStack() },
                section = SettingsSection.APPEARANCE,
            )
        }
        composable(Routes.SETTINGS_DIAGNOSTICS) {
            DiagnosticsScreen(
                diagnostics = runDiagnostics,
                usage = usageLedger,
                onBack = { navController.popBackStack() },
                onOpenHelp = { navController.navigate(Routes.HELP) },
            )
        }
        composable(
            route = Routes.PACKS,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.PACKS) }),
        ) {
            PacksScreen(
                packManager = packManager,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.MODEL_CONFIG,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.MODEL_CONFIG) }),
        ) {
            ModelConfigScreen(
                appSettings = appSettings,
                packManager = packManager,
                freeCloudDiscovery = freeCloudDiscovery,
                onOpenPacks = { navController.navigate(Routes.PACKS) },
                onBack = { navController.popBackStack() },
            )
        }
    }

    AnimatedVisibility(
        visible = showBottomBar,
        enter = fadeIn(animationSpec = tween(260)) + slideInVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetY = { it },
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            animationSpec = tween(200, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        VestraBottomNavBar(navController = navController)
    }
}
}
