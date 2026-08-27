package com.zakir.vestra.ui.screens.home

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.components.ShimmerAsyncImage
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.engine.EngineRouter
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.wardrobe.WardrobeEntry
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.ui.ChatViewModel
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.InterruptedJobsBanner
import com.zakir.vestra.ui.components.QuickCreateSheet
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.screens.settings.SettingsSection
import com.zakir.vestra.ui.screens.settings.SettingsScreen
import com.zakir.vestra.ui.screens.wardrobe.WardrobeScreen
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.theme.VestraShapes
import com.zakir.vestra.ui.theme.VestraSpacing
import com.zakir.vestra.ui.util.rememberReduceMotion
import java.io.File
import kotlinx.coroutines.delay

// Preserved for compatibility with existing tests and enum references
internal enum class HomeTab(val label: String, val routeKey: String) {
    TRY_ON("Try-on", "tryon"),
    IMAGE("Image", "image"),
    VIDEO("Video", "video"),
    AUDIO("Audio", "audio"),
    CODE("Code", "code"),
    NEWS("News", "news"),
    ;

    companion object {
        const val TRY_ON_TAB_ENABLED = false
        val visible: List<HomeTab> = entries.filter { TRY_ON_TAB_ENABLED || it != TRY_ON }
        fun fromRouteKey(key: String?): HomeTab =
            visible.firstOrNull { it.routeKey.equals(key, ignoreCase = true) } ?: visible.first()
    }
}

/** Bottom Dock Navigation Bar 3-Item Destinations */
enum class MainDockTab(val title: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Outlined.AutoAwesome, "dock_tab_home"),
    LIBRARY("Library", Icons.Outlined.Collections, "dock_tab_library"),
    SETTINGS("Settings", Icons.Outlined.Settings, "dock_tab_settings"),
}

/** Generator Pill Item on the Home Dashboard */
data class GeneratorPill(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    appSettings: AppSettings,
    wardrobe: WardrobeRepository,
    packManager: ModelPackManager,
    generativeViewModel: GenerativeViewModel,
    freeCloudDiscovery: FreeCloudDiscovery,
    engineRouter: EngineRouter,
    usageLedger: UsageLedger,
    localJobStore: com.zakir.vestra.shared.jobs.LocalJobStore? = null,
    newsRepository: NewsRepository? = null,
    chatViewModel: ChatViewModel? = null,
    onNewLook: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPacks: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenNewsChat: (headline: String?) -> Unit = {},
    initialTabRoute: String? = null,
    // Direct isolated studio callbacks
    onOpenImageStudio: () -> Unit = {},
    onOpenVideoStudio: () -> Unit = {},
    onOpenCodeStudio: () -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    onOpenNewsScreen: () -> Unit = {},
) {
    val recent by wardrobe.entries.collectAsState()
    val packStates by packManager.states.collectAsState()

    var showQuickCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        packManager.refresh()
    }

    val proReady = listOf("pro-v2-int8", "pro-v1").any { id ->
        packStates[id]?.isReady() == true
    }

    var online by remember { mutableStateOf(appSettings.networkLikelyAvailable()) }
    LaunchedEffect(Unit) {
        while (true) {
            online = appSettings.networkLikelyAvailable()
            delay(2_500)
        }
    }

    var appeared by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(Unit) { appeared = true }
    val fade by animateFloatAsState(
        targetValue = if (appeared || reduceMotion) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else tween(640),
        label = "homeFade",
    )

    val hfToken by appSettings.hfToken.collectAsState()
    val hfReady = !hfToken.isNullOrBlank()
    val liteReady = packStates["lite-v1"]?.isReady() == true
    val liteState = packStates["lite-v1"]
    val statusLine = buildString {
        append(
            when {
                proReady -> "Pro local ready"
                liteReady -> "Fast local ready"
                liteState?.status == PackStatus.INSTALLED -> "Engine verifying…"
                else -> "Local engines active"
            },
        )
        append("  ·  ")
        append(if (hfReady) "Cloud token set" else "Offline-first")
        append("  ·  ")
        append(if (online) "Online" else "Offline")
    }

    // Generator model pills list for isolated page navigation
    val generatorPills = remember(
        onOpenImageStudio,
        onOpenVideoStudio,
        onOpenCodeStudio,
        onOpenAudioStudio,
        onOpenNewsScreen,
        onNewLook,
    ) {
        listOf(
            GeneratorPill(
                id = "tryon",
                title = "Virtual Fitting Studio",
                subtitle = "Photorealistic try-on on customizable fashion models",
                badge = "Core Atelier",
                icon = Icons.Outlined.AutoAwesome,
                accentColor = VestraColors.Accent,
                onClick = onNewLook,
            ),
            GeneratorPill(
                id = "image",
                title = "Image Studio",
                subtitle = "Text-to-image & couture prompt diffusion",
                badge = "Tiny-SD / Cloud",
                icon = Icons.Outlined.Image,
                accentColor = VestraColors.ModalityImage,
                onClick = onOpenImageStudio,
            ),
            GeneratorPill(
                id = "video",
                title = "Video Studio",
                subtitle = "Motion camera sweeps & cinematic runway clips",
                badge = "Motion Pipeline",
                icon = Icons.Outlined.Videocam,
                accentColor = VestraColors.ModalityVideo,
                onClick = onOpenVideoStudio,
            ),
            GeneratorPill(
                id = "code",
                title = "Code Studio",
                subtitle = "LiteRT Gemma on-device architecture & UI generation",
                badge = "100% On-Device",
                icon = Icons.Outlined.Code,
                accentColor = VestraColors.ModalityCode,
                onClick = onOpenCodeStudio,
            ),
            GeneratorPill(
                id = "audio",
                title = "Audio Lab",
                subtitle = "Native TTS & DSP voice pitch/formant shifting",
                badge = "DSP Engine",
                icon = Icons.Outlined.GraphicEq,
                accentColor = VestraColors.ModalityAudio,
                onClick = onOpenAudioStudio,
            ),
            GeneratorPill(
                id = "news",
                title = "Fashion Intel & Chat",
                subtitle = "Live trend curation & on-device AI reasoning",
                badge = "Live Intel",
                icon = Icons.Outlined.Newspaper,
                accentColor = VestraColors.AccentSoft,
                onClick = onOpenNewsScreen,
            ),
        )
    }

    SpatialBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .alpha(fade)
                .padding(bottom = 80.dp),
        ) {
            HomeDashboardContent(
                statusLine = statusLine,
                proReady = proReady,
                generatorPills = generatorPills,
                recentLooks = recent,
                localJobStore = localJobStore,
                onOpenPacks = onOpenPacks,
                onOpenHelp = onOpenHelp,
                onOpenWardrobe = onOpenWardrobe,
                onOpenSettings = onOpenSettings,
                onSelectRecent = { onOpenWardrobe() },
            )
        }
    }

    // Quick Create Sheet (if triggered)
    if (showQuickCreate) {
        QuickCreateSheet(
            onSelectTab = { tab ->
                showQuickCreate = false
                when (tab) {
                    HomeTab.IMAGE -> onOpenImageStudio()
                    HomeTab.VIDEO -> onOpenVideoStudio()
                    HomeTab.CODE -> onOpenCodeStudio()
                    HomeTab.AUDIO -> onOpenAudioStudio()
                    HomeTab.NEWS -> onOpenNewsScreen()
                    HomeTab.TRY_ON -> onNewLook()
                }
            },
            onStartTryOn = {
                showQuickCreate = false
                onNewLook()
            },
            onDismiss = { showQuickCreate = false },
        )
    }
}

/**
 * Main Home Dashboard: Displays Header, Status, and Separate Model Generator Button Pills
 */
@Composable
private fun HomeDashboardContent(
    statusLine: String,
    proReady: Boolean,
    generatorPills: List<GeneratorPill>,
    recentLooks: List<WardrobeEntry>,
    localJobStore: com.zakir.vestra.shared.jobs.LocalJobStore?,
    onOpenPacks: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectRecent: (WardrobeEntry) -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                    start = VestraSpacing.md,
                    top = VestraSpacing.xs,
                    end = VestraSpacing.md,
                    bottom = VestraSpacing.dockClearance,
                ),

    ) {
        // Interrupted jobs banner if any
        if (localJobStore != null) {
            item(key = "interrupted_jobs") {
                InterruptedJobsBanner(localJobStore)
                Spacer(Modifier.height(8.dp))
            }
        }

        // Top App Header
        item(key = "header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                                                    "Your creative studio",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.1).sp,
                            ),

                        color = VestraColors.Ink,
                    )
                    Text(
                        "Make, refine, and keep your best looks",
                        style = MaterialTheme.typography.bodySmall,
                        color = VestraColors.InkMuted,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                                                                .background(VestraColors.Accent.copy(alpha = 0.14f))

                            .clickable(onClick = onOpenPacks)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (proReady) "Pro Engine" else "Fast Engine",
                            style = MaterialTheme.typography.labelMedium,
                            color = VestraColors.Accent,
                        )
                    }

                    IconButton(onClick = onOpenHelp) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = LookbookCopy.STUDIO_HELP,
                            tint = VestraColors.Ink,
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(TestTags.OPEN_SETTINGS_BUTTON),
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                                                    .background(VestraColors.SurfaceRaised)
                                    .border(1.dp, VestraColors.GlassBorder, CircleShape),

                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = LookbookCopy.STUDIO_SETTINGS,
                                tint = VestraColors.Ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        // Engine Status Card
        item(key = "status_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VestraShapes.feature))
                    .background(VestraColors.SurfaceRaised)
                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(VestraShapes.feature))
                    .padding(VestraSpacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(VestraColors.Accent),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ENGINE STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                ),
                                color = VestraColors.Ivory,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.IvoryMuted,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(VestraColors.Accent.copy(alpha = 0.15f))
                            .border(1.dp, VestraColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .clickable(onClick = onOpenPacks)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "Model Packs",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = VestraColors.Accent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // Section Title: AI Model Generators
        item(key = "generators_label") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                text = "Choose a starting point",
                style = MaterialTheme.typography.titleLarge,
                color = VestraColors.Ink,
                modifier = Modifier.padding(bottom = 6.dp),
            )
                Text(
                    text = "${generatorPills.size} Studios",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.InkMuted,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // Distinct Button Pills for Each Model Generator
        items(generatorPills, key = { it.id }) { pill ->
            GeneratorPillCard(
                pill = pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
            )
        }

        // Section: Recent Creations / Library Quick Access
        if (recentLooks.isNotEmpty()) {
            item(key = "recent_header") {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassSectionLabel("RECENT CREATIONS")
                    Text(
                        "View all",
                        style = MaterialTheme.typography.labelMedium,
                        color = VestraColors.Accent,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable(onClick = onOpenWardrobe),
                    )
                }
            }

            item(key = "recent_gallery") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentLooks.take(8), key = { it.id }) { entry ->
                        val file = File(entry.imagePath)
                        Box(
                            Modifier
                                .width(130.dp)
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    1.dp,
                                    VestraColors.GlassBorder,
                                    RoundedCornerShape(18.dp),
                                )
                                .clickable { onSelectRecent(entry) },
                        ) {
                            ShimmerAsyncImage(
                                model = file,
                                contentDescription = entry.personLabel,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                shape = RoundedCornerShape(18.dp),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                VestraColors.AtelierCanvas.copy(alpha = 0.85f),
                                            ),
                                        ),
                                    ),
                            )
                            Text(
                                entry.personLabel.ifBlank { "Creation" },
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.Ivory,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 10.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visual Model Generator Pill Button Card
 */
@Composable
private fun GeneratorPillCard(
    pill: GeneratorPill,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.78f else 1f,
        animationSpec = tween(120),
        label = "pill_press_alpha",
    )

    Surface(
        modifier = modifier
            .testTag("generator_pill_${pill.id}")
            .graphicsLayer { alpha = pressAlpha }
            .clip(RoundedCornerShape(VestraShapes.card))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = pill.onClick,
            ),
        shape = RoundedCornerShape(VestraShapes.card),
        color = VestraColors.SurfaceRaised,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    pill.accentColor.copy(alpha = 0.45f),
                    VestraColors.GlassBorder.copy(alpha = 0.3f),
                ),
            ),
        ),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VestraSpacing.md, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Icon Bubble
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pill.accentColor.copy(alpha = 0.15f))
                        .border(1.dp, pill.accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = pill.icon,
                        contentDescription = pill.title,
                        tint = pill.accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pill.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            ),
                            color = VestraColors.Ink,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(pill.accentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = pill.badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = pill.accentColor,
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = pill.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = VestraColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Chevron Forward Indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(VestraColors.GlassFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open ${pill.title}",
                    tint = pill.accentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 3-Button Spatial Dock Navigation Bar (Home, Library, Settings)
 */
@Composable
private fun ThreeButtonSpatialDock(
    activeTab: MainDockTab,
    onSelectTab: (MainDockTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(RadiusTokens.xl))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            VestraColors.AtelierContainer.copy(alpha = 0.94f),
                            VestraColors.AtelierCanvas.copy(alpha = 0.98f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            VestraColors.AccentSoft.copy(alpha = 0.5f),
                            VestraColors.GlassBorder.copy(alpha = 0.25f),
                        ),
                    ),
                    shape = RoundedCornerShape(RadiusTokens.xl),
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainDockTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    DockPillItem(
                        tab = tab,
                        isSelected = isSelected,
                        onClick = { onSelectTab(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockPillItem(
    tab: MainDockTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dock_item_scale",
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) VestraColors.Accent else VestraColors.IvoryMuted.copy(alpha = 0.65f),
        label = "dock_content_color",
    )

    Column(
        modifier = modifier
            .testTag(tab.tag)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.title,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = tab.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = contentColor,
        )

        // Animated Active Dot
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(VestraColors.Accent),
            )
        }
    }
}
