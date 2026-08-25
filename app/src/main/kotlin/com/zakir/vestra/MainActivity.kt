package com.zakir.vestra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import com.zakir.vestra.shared.settings.AppearanceMode
import com.zakir.vestra.ui.VestraNavHost
import com.zakir.vestra.ui.theme.VestraTheme

/**
 * Deep links for local visual verification:
 * `adb shell am start -a android.intent.action.VIEW -d lookbook://screen/settings -n com.zakir.vestra/.MainActivity`
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as VestraApp
        setContent {
            val appearance by app.appSettings.appearanceMode.collectAsState()
            val dark = when (appearance) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            val navController = rememberNavController()
            var pendingIntent by remember { mutableStateOf(intent) }

            DisposableEffect(navController) {
                val listener = Consumer<Intent> { pendingIntent = it }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }

            VestraTheme(darkTheme = dark) {
                // Compose's testTag is invisible to UiAutomator/Appium unless the app opts in
                // here — without this, every Modifier.testTag(...) in the UI exists only for
                // Compose's own UI-test framework, not for external automation tools.
                Box(
                    modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                ) {
                    VestraNavHost(
                        appSettings = app.appSettings,
                        engineRouter = app.engineRouter,
                        wardrobe = app.wardrobe,
                        packManager = app.packManager,
                        studioModels = app.studioModels,
                        generative = app.generative,
                        usageLedger = app.usageLedger,
                        runDiagnostics = app.runDiagnostics,
                        localJobStore = app.localJobStore,
                        chatRepository = app.chatRepository,
                        deviceRamMb = app.deviceProbe.totalRamMb(),
                        freeCloudDiscovery = app.freeCloudDiscovery,
                        humanParsing = app.humanParsing,
                        liteEngineIo = app.liteEngineIo,
                        navController = navController,
                        pendingDeepLinkIntent = pendingIntent,
                        onDeepLinkHandled = { pendingIntent = null },
                    )
                }
            }
        }
    }
}
