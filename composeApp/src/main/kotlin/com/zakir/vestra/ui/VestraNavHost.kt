package com.zakir.vestra.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import android.content.Intent
import com.zakir.vestra.diagnostics.CrashReporter
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.GenerativeCloudService
import com.zakir.vestra.shared.engine.EngineRouter
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.ui.screens.capture.GarmentScreen
import com.zakir.vestra.ui.screens.casting.CastingStudioScreen
import com.zakir.vestra.ui.screens.onboarding.OnboardingScreen
import com.zakir.vestra.ui.screens.packs.PacksScreen
import com.zakir.vestra.ui.screens.generate.GenerationScreen
import com.zakir.vestra.ui.screens.person.PersonSourceScreen
import com.zakir.vestra.ui.screens.result.ResultScreen
import com.zakir.vestra.shared.chat.ChatRepository
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.jobs.LocalJobStore
import com.zakir.vestra.ui.screens.settings.DiagnosticsScreen
import com.zakir.vestra.ui.screens.settings.SettingsSection
import com.zakir.vestra.ui.screens.settings.SettingsScreen
import com.zakir.vestra.ui.screens.home.HomeScreen
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.platformHttpClient
import com.zakir.vestra.ui.screens.usage.UsageScreen
import com.zakir.vestra.ui.screens.help.HelpScreen
import com.zakir.vestra.ui.screens.privacy.PrivacyScreen
import com.zakir.vestra.ui.screens.wardrobe.WardrobeScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val STUDIO = "studio/{tab}"
    fun studioHome(tab: String = "tryon") = "studio/$tab"
    const val GARMENT = "garment"
    const val CASTING = "casting"
    const val PERSON = "person"
    const val GENERATE = "generate"
    const val RESULT = "result"
    const val WARDROBE = "wardrobe"
    const val SETTINGS = "settings"
    const val SETTINGS_CLOUD = "settings/cloud"
    const val SETTINGS_ENGINES = "settings/engines"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_DIAGNOSTICS = "settings/diagnostics"
    const val PACKS = "packs"
    const val CREATE = "create"
    const val CODE = "code"
    const val VIDEO = "video"
    const val USAGE = "usage"
    const val HELP = "help"
    const val PRIVACY = "privacy"

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
    navController: NavHostController = rememberNavController(),
    pendingDeepLinkIntent: Intent? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val onboardingComplete by appSettings.onboardingComplete.collectAsState()
    val start = if (onboardingComplete) Routes.studioHome() else Routes.ONBOARDING

    LaunchedEffect(pendingDeepLinkIntent, onboardingComplete) {
        val intent = pendingDeepLinkIntent ?: return@LaunchedEffect
        if (!onboardingComplete) return@LaunchedEffect
        val route = intent.data
            ?.takeIf { it.scheme == "lookbook" && it.host == "screen" }
            ?.pathSegments
            ?.firstOrNull()
        if (route.isNullOrBlank()) return@LaunchedEffect
        // Prefer explicit navigate — handleDeepLink is flaky when the graph
        // is already composed on a warm singleTop Activity.
        runCatching {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        onDeepLinkHandled()
    }

    val tryOnViewModel: TryOnViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TryOnViewModel(engineRouter, appSettings, wardrobe, runDiagnostics, deviceRamMb) as T
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
                ) as T
        },
    )

    // Instant transitions — AnimatedContent measure of heavy screens (Settings)
    // was ANRing the main thread on mid/low devices and looking like a crash.
    val navEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navEntry?.destination?.route) {
        val route = navEntry?.destination?.route ?: "unknown"
        val tab = navEntry?.arguments?.getString("tab")
        CrashReporter.breadcrumb(if (tab != null) "$route#$tab" else route)
    }
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                appSettings = appSettings,
                onDone = {
                    navController.navigate(Routes.studioHome()) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.STUDIO,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "tryon"
                },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink("studio") }),
        ) { backStackEntry ->
            val context = LocalContext.current
            val tab = backStackEntry.arguments?.getString("tab")
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
            HomeScreen(
                appSettings = appSettings,
                wardrobe = wardrobe,
                packManager = packManager,
                generativeViewModel = generativeViewModel,
                localJobStore = localJobStore,
                freeCloudDiscovery = freeCloudDiscovery,
                newsRepository = newsRepository,
                chatViewModel = chatViewModel,
                onNewLook = {
                    tryOnViewModel.resetSession()
                    navController.navigate(Routes.GARMENT)
                },
                onOpenWardrobe = { navController.navigate(Routes.WARDROBE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPacks = { navController.navigate(Routes.PACKS) },
                onOpenHelp = { navController.navigate(Routes.HELP) },
                // NewsChatScreen fills its own local chat input with the headline before this
                // fires — this used to also push the headline into GenerativeViewModel.prompt,
                // which every studio tab (Image/Video/Code/Audio) shares, silently overwriting
                // whatever the user had typed in the currently-bound studio.
                onOpenNewsChat = {},
                initialTabRoute = tab,
            )
        }
        composable(
            route = Routes.GARMENT,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.GARMENT) }),
        ) {
            GarmentScreen(
                viewModel = tryOnViewModel,
                humanParsing = humanParsing,
                liteEngineIo = liteEngineIo,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.CASTING) },
            )
        }
        composable(Routes.CASTING) {
            CastingStudioScreen(
                viewModel = tryOnViewModel,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.PERSON) },
            )
        }
        composable(Routes.PERSON) {
            PersonSourceScreen(
                viewModel = tryOnViewModel,
                appSettings = appSettings,
                studioModels = studioModels,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.GENERATE) },
            )
        }
        composable(Routes.GENERATE) {
            GenerationScreen(
                viewModel = tryOnViewModel,
                onComplete = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.studioHome())
                    }
                },
                onAbort = { navController.popBackStack(Routes.studioHome(), inclusive = false) },
                onOpenHelp = { navController.navigate(Routes.HELP) },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                viewModel = tryOnViewModel,
                wardrobe = wardrobe,
                onNewLook = {
                    tryOnViewModel.resetSession()
                    navController.navigate(Routes.GARMENT) {
                        popUpTo(Routes.studioHome())
                    }
                },
                onBackToStudio = {
                    navController.popBackStack(Routes.studioHome(), inclusive = false)
                },
                onOpenWardrobe = { navController.navigate(Routes.WARDROBE) },
            )
        }
        composable(
            route = Routes.CREATE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.CREATE) }),
        ) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.studioHome("image")) {
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
                navController.navigate(Routes.studioHome("code")) {
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
                navController.navigate(Routes.studioHome("video")) {
                    launchSingleTop = true
                    popUpTo(Routes.VIDEO) { inclusive = true }
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
                    navController.navigate(Routes.studioHome("image"))
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
            route = Routes.WARDROBE,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.deepLink(Routes.WARDROBE) }),
        ) {
            WardrobeScreen(
                wardrobe = wardrobe,
                onBack = { navController.popBackStack() },
                // onStartTryOn omitted (defaults to null) while try-on is temporarily
                // disabled app-wide — restores the empty-state CTA when re-added.
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
                onBack = { navController.popBackStack() },
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
    }
}
