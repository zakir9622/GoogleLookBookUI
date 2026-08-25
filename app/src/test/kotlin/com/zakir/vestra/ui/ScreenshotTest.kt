package com.zakir.vestra.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.components.PromptComposer
import com.zakir.vestra.ui.screens.settings.settingsCloudMasterToggleSection
import com.zakir.vestra.ui.theme.VestraTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real pixel screenshots of the UI, rendered on the JVM.
 *
 * GraphicsMode.NATIVE makes Robolectric rasterise for real, so drawing the view yields actual
 * pixels rather than a blank buffer. That gives a way to *look* at the UI in an environment with
 * no device, emulator or KVM — which is how the vertical-text regression reached a release build
 * unnoticed.
 *
 * PNGs land in composeApp/build/screenshots/.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class MemorySettings : Settings {
        private val map = mutableMapOf<String, Any?>()
        override val keys: Set<String> get() = map.keys
        override val size: Int get() = map.size
        override fun clear() = map.clear()
        override fun remove(key: String) { map.remove(key) }
        override fun hasKey(key: String): Boolean = map.containsKey(key)
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
        override fun getIntOrNull(key: String): Int? = map[key] as? Int
        override fun putLong(key: String, value: Long) { map[key] = value }
        override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
        override fun getLongOrNull(key: String): Long? = map[key] as? Long
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
        override fun getStringOrNull(key: String): String? = map[key] as? String
        override fun putFloat(key: String, value: Float) { map[key] = value }
        override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
        override fun getFloatOrNull(key: String): Float? = map[key] as? Float
        override fun putDouble(key: String, value: Double) { map[key] = value }
        override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
        override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
        override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        // Deliberately NOT captureToImage(): that goes through forceRedraw(), which blocks on a
        // real window draw callback that never fires without a surface, so it always times out
        // under Robolectric. Drawing the decor view straight onto a software Canvas produces the
        // same pixels with no window involved.
        //
        // The clock is driven manually because the UI runs infinite animations (accent glow), so
        // the composition never reports idle and any wait-for-idle would hang.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VestraTheme(darkTheme = true) {
                Box(
                    androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                ) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(750)

        val view = compose.activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            val w = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
            val h = android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY)
            view.measure(w, h)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(android.graphics.Canvas(bitmap))

        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("screenshot: ${File(dir, "$name.png").absolutePath} (${bitmap.width}x${bitmap.height})")
    }

    @Test
    fun cloudToggleOff() {
        val settings = AppSettings(MemorySettings())
        shoot("01-settings-cloud-toggle-off") {
            LazyColumn { settingsCloudMasterToggleSection(appSettings = settings) }
        }
    }

    @Test
    fun cloudToggleOn() {
        val settings = AppSettings(MemorySettings()).apply { setCloudModelsEnabled(true) }
        shoot("02-settings-cloud-toggle-on") {
            LazyColumn { settingsCloudMasterToggleSection(appSettings = settings) }
        }
    }

    /**
     * The studio shape that regressed: a long provider string beside chips, and the composer
     * docked at the bottom. Before the fix the header chips rendered as one-character-wide
     * columns of vertical text ~1000dp tall, which also blew the layout apart. This is the
     * screenshot that would have caught it.
     */
    @Test
    fun studioHeaderAndDock() {
        val longEstimate =
            "FLUX.1 Schnell · Ready · verified just now · Hugging Face Space (free) · Free " +
                "infer · prompt + seed/randomize/size/steps"
        shoot("03-studio-header-and-dock") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize(),
            ) {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    com.zakir.vestra.ui.components.GlassSectionLabel("IMAGE STUDIO")
                    androidx.compose.material3.Text(
                        "Local tiny-SD ready offline — Create Studio runs on-device.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = com.zakir.vestra.ui.theme.VestraColors.InkMuted,
                    )
                    androidx.compose.foundation.layout.Spacer(
                        androidx.compose.ui.Modifier.height(4.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    ) {
                        androidx.compose.material3.Text(
                            longEstimate,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = com.zakir.vestra.ui.theme.VestraColors.InkMuted,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        com.zakir.vestra.ui.components.GlassPill(
                            text = "Local SD-Turbo · Ready offline",
                            active = true,
                        )
                        androidx.compose.material3.Text(
                            "Last: InstructPix2Pix",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = com.zakir.vestra.ui.theme.VestraColors.Accent,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp, top = 4.dp),
                ) {
                    PromptComposer(
                        prompt = "i want a russian girl riding a horse",
                        onPromptChange = {},
                        modelLabel = "Local tiny-SD (offline)",
                        assistCount = 1,
                        busy = false,
                        enabled = true,
                        onModelClick = {},
                        onAssistsClick = {},
                        onSend = {},
                        onStop = {},
                    )
                }
            }
        }
    }

    /** Code output: prose separated from fenced blocks, each block copyable. */
    @Test
    fun codeOutputBlocks() {
        val answer = """
            Use a frosted card like this:

            ```kotlin
            @Composable
            fun GlassCard(content: @Composable () -> Unit) {
                Surface(shape = RoundedCornerShape(24.dp)) { content() }
            }
            ```

            Then build it:

            ```bash
            ./gradlew :composeApp:assembleSideloadDebug
            ```
        """.trimIndent()
        shoot("07-code-output-blocks") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(18.dp),
            ) {
                com.zakir.vestra.ui.components.GlassSectionLabel("CODE · 412 free tokens")
                com.zakir.vestra.ui.components.CodeOutput(text = answer)
            }
        }
    }

    /** Produced-audio list with inline playback controls. */
    @Test
    fun audioClipList() {
        val clips = listOf(
            com.zakir.vestra.audio.AudioClip(
                path = "/tmp/voice_1787500000000.wav",
                kind = com.zakir.vestra.audio.AudioClipKind.CONVERTED,
                savedAtMs = 1787500000000L,
                bytes = 1_482_112,
                durationMs = 14_000,
            ),
            com.zakir.vestra.audio.AudioClip(
                path = "/tmp/mic_1787499000000.wav",
                kind = com.zakir.vestra.audio.AudioClipKind.RECORDING,
                savedAtMs = 1787499000000L,
                bytes = 962_560,
                durationMs = 9_000,
            ),
            com.zakir.vestra.audio.AudioClip(
                path = "/tmp/sys_tts_1787498000000.wav",
                kind = com.zakir.vestra.audio.AudioClipKind.SPEECH,
                savedAtMs = 1787498000000L,
                bytes = 331_776,
                durationMs = 3_000,
            ),
        )
        shoot("08-audio-clip-list") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(18.dp),
            ) {
                com.zakir.vestra.ui.components.GlassSectionLabel("CLIPS")
                com.zakir.vestra.ui.components.AudioClipList(
                    clips = clips,
                    onShare = {},
                    onDelete = {},
                )
            }
        }
    }

    /** Warm-up states — written but never seen rendered until now. */
    @Test
    fun warmupLoading() {
        shoot("09-warmup-loading") {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(18.dp)) {
                com.zakir.vestra.ui.components.GlassCard {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = androidx.compose.ui.Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = com.zakir.vestra.ui.theme.VestraColors.Accent,
                        )
                        androidx.compose.foundation.layout.Spacer(
                            androidx.compose.ui.Modifier.width(10.dp),
                        )
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text(
                                "Initializing Local Qwen3 0.6B (fast)",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                color = com.zakir.vestra.ui.theme.VestraColors.Ink,
                            )
                            androidx.compose.material3.Text(
                                "First load only — this can take up to a minute.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = com.zakir.vestra.ui.theme.VestraColors.InkMuted,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun warmupReadyAndFailed() {
        shoot("10-warmup-ready-failed") {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(18.dp)) {
                com.zakir.vestra.ui.components.GlassPill(
                    text = "Local Qwen3 0.6B (fast) · loaded and ready",
                    active = true,
                )
                androidx.compose.foundation.layout.Spacer(
                    androidx.compose.ui.Modifier.height(12.dp),
                )
                com.zakir.vestra.ui.components.GlassErrorBanner(
                    message = "Local image gen (tiny-SD) could not load: Local SD-Turbo weights " +
                        "incomplete (unet.onnx). Re-download local-sdturbo-v1.",
                    onRetry = {},
                    retryLabel = "Retry load",
                    onDismiss = null,
                )
            }
        }
    }

    /** The composer as it renders docked at the bottom of the studio. */
    @Test
    fun composerDock() {
        shoot("05-composer-dock") {
            PromptComposer(
                prompt = "Emerald abaya in a Lahore bazaar, soft afternoon light",
                onPromptChange = {},
                modelLabel = "Local tiny-SD (offline)",
                assistCount = 2,
                busy = false,
                enabled = true,
                onModelClick = {},
                onAssistsClick = {},
                onSend = {},
                onStop = {},
                placeholder = "Describe the image…",
            )
        }
    }

    @Test
    fun composerDockBusy() {
        shoot("06-composer-dock-busy") {
            PromptComposer(
                prompt = "Emerald abaya in a Lahore bazaar",
                onPromptChange = {},
                modelLabel = "Local Qwen3 0.6B (offline)",
                assistCount = 0,
                busy = true,
                enabled = true,
                onModelClick = {},
                onSend = {},
                onStop = {},
            )
        }
    }
}
