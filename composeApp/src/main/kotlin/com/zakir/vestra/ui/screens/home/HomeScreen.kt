package com.zakir.vestra.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.wardrobe.WardrobeRepository
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.ChatViewModel
import com.zakir.vestra.ui.components.AtelierHero
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.InterruptedJobsBanner
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.screens.news.NewsChatScreen
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.rememberReduceMotion
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// `internal` rather than `private` so the pager-index math below is reachable from unit tests:
// filtering a tab out makes `ordinal` and the visible-list index diverge, which is exactly the
// kind of off-by-one that only shows up on a device.
internal enum class HomeTab(val label: String, val routeKey: String) {
    // Try-on is temporarily disabled app-wide — kept in the enum (not deleted) so the try-on
    // engines/routes/tests keep compiling and it's a one-line revert to bring the tab back.
    // To re-enable: flip TRY_ON_TAB_ENABLED to true below.
    TRY_ON("Try-on", "tryon"),
    IMAGE("Image", "image"),
    VIDEO("Video", "video"),
    AUDIO("Audio", "audio"),
    CODE("Code", "code"),
    NEWS("News", "news"),
    ;

    companion object {
        /** Temporarily off while try-on is disabled app-wide. Flip to bring the tab back. */
        const val TRY_ON_TAB_ENABLED = false

        val visible: List<HomeTab> = entries.filter { TRY_ON_TAB_ENABLED || it != TRY_ON }

        fun fromRouteKey(key: String?): HomeTab =
            visible.firstOrNull { it.routeKey.equals(key, ignoreCase = true) } ?: visible.first()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    appSettings: AppSettings,
    wardrobe: WardrobeRepository,
    packManager: ModelPackManager,
    generativeViewModel: GenerativeViewModel,
    localJobStore: com.zakir.vestra.shared.jobs.LocalJobStore? = null,
    freeCloudDiscovery: FreeCloudDiscovery? = null,
    newsRepository: NewsRepository? = null,
    chatViewModel: ChatViewModel? = null,
    onNewLook: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPacks: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenNewsChat: (headline: String?) -> Unit = {},
    initialTabRoute: String? = null,
) {
    val context = LocalContext.current
    val recent by wardrobe.entries.collectAsState()
    val packStates by packManager.states.collectAsState()
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
    val heroLift by animateFloatAsState(
        targetValue = if (appeared || reduceMotion) 0f else 18f,
        animationSpec = if (reduceMotion) tween(0) else spring(stiffness = Spring.StiffnessMediumLow),
        label = "heroLift",
    )

    val tryOnModel = appSettings.selectedProvider(AiCapability.TRY_ON).displayName
    val hfToken by appSettings.hfToken.collectAsState()
    val hfReady = !hfToken.isNullOrBlank()
    val liteReady = packStates["lite-v1"]?.isReady() == true
    val liteState = packStates["lite-v1"]
    val statusLine = buildString {
        append(
            when {
                proReady -> "Pro local try-on ready"
                liteReady -> "Fast local try-on ready"
                liteState?.status == PackStatus.INSTALLED -> "Fast try-on verifying…"
                else -> "Fast try-on needs download"
            },
        )
        append("  ·  ")
        append(if (hfReady) "Cloud token set" else "Cloud needs HF token")
        append("  ·  ")
        append(if (online) "Online" else "Offline")
        append("  ·  ")
        append(tryOnModel)
    }

    val tabs = HomeTab.visible
    val initialPage = tabs.indexOf(HomeTab.fromRouteKey(initialTabRoute)).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialTabRoute) {
        val target = tabs.indexOf(HomeTab.fromRouteKey(initialTabRoute)).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    fun openNewsChat(headline: String?) {
        // NewsChatScreen owns its own chat input locally and already fills it with the headline
        // before this callback runs — writing the headline into GenerativeViewModel.prompt here
        // too was a real bug: that flow is shared by every studio tab (Image/Video/Code/Audio),
        // so a headline tap silently overwrote whatever prompt was typed in the currently-bound
        // studio, which is exactly the "prompts leak between tabs" symptom this was causing.
        onOpenNewsChat(headline)
        scope.launch {
            pagerState.animateScrollToPage(tabs.indexOf(HomeTab.NEWS))
        }
    }

    SpatialBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .alpha(fade),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 18.dp)) {
                InterruptedJobsBanner(localJobStore)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        LookbookCopy.PRODUCT_NAME,
                        style = MaterialTheme.typography.titleLarge,
                        color = VestraColors.Ink,
                    )
                    Text(
                        LookbookCopy.STUDIO_HOME,
                        style = MaterialTheme.typography.labelMedium,
                        color = VestraColors.Accent,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(VestraColors.SaffronDeep, VestraColors.Accent),
                                ),
                            )
                            .clickable(onClick = onOpenPacks)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (proReady) "Pro" else "Fast",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                    IconButton(onClick = onOpenHelp) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = LookbookCopy.STUDIO_HELP,
                            tint = VestraColors.Ink,
                        )
                    }
                    IconButton(onClick = onOpenWardrobe) {
                        Icon(
                            Icons.Outlined.Checkroom,
                            contentDescription = LookbookCopy.STUDIO_WARDROBE,
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
                                .background(VestraColors.GlassFillStrong)
                                .border(1.5.dp, VestraColors.Accent.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = LookbookCopy.STUDIO_SETTINGS,
                                tint = VestraColors.Accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = VestraColors.Ink,
                edgePadding = 12.dp,
                divider = {},
                indicator = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier.testTag(TestTags.homeTab(tab.routeKey)),
                        text = {
                            Text(
                                tab.label,
                                color = if (selected) VestraColors.Accent else VestraColors.InkMuted,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                when (tabs[page]) {
                    HomeTab.TRY_ON -> TryOnPage(
                        statusLine = statusLine,
                        proReady = proReady,
                        heroLift = heroLift,
                        recent = recent,
                        onNewLook = onNewLook,
                        onOpenWardrobe = onOpenWardrobe,
                        onOpenPacks = onOpenPacks,
                    )
                    HomeTab.IMAGE -> UnifiedStudioPane(
                        capability = AiCapability.IMAGE_GEN,
                        viewModel = generativeViewModel,
                        onOpenSettings = onOpenSettings,
                        freeCloudDiscovery = freeCloudDiscovery,
                        packManager = packManager,
                    )
                    HomeTab.VIDEO -> UnifiedStudioPane(
                        capability = AiCapability.VIDEO,
                        viewModel = generativeViewModel,
                        onOpenSettings = onOpenSettings,
                        freeCloudDiscovery = freeCloudDiscovery,
                        packManager = packManager,
                    )
                    HomeTab.AUDIO -> AudioStudioPane(
                        viewModel = generativeViewModel,
                        onOpenSettings = onOpenSettings,
                        freeCloudDiscovery = freeCloudDiscovery,
                        packManager = packManager,
                    )
                    HomeTab.CODE -> UnifiedStudioPane(
                        capability = AiCapability.CODE,
                        viewModel = generativeViewModel,
                        onOpenSettings = onOpenSettings,
                        freeCloudDiscovery = freeCloudDiscovery,
                        packManager = packManager,
                    )
                    HomeTab.NEWS -> NewsChatScreen(
                        newsRepository = newsRepository,
                        chatViewModel = chatViewModel,
                        appSettings = appSettings,
                        freeCloudDiscovery = freeCloudDiscovery,
                        packManager = packManager,
                        onHeadlineSelected = ::openNewsChat,
                    )
                }
            }
        }
    }
}

@Composable
private fun TryOnPage(
    statusLine: String,
    proReady: Boolean,
    heroLift: Float,
    recent: List<com.zakir.vestra.shared.wardrobe.WardrobeEntry>,
    onNewLook: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenPacks: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 24.dp),
    ) {
        item(key = "hero") {
            GlassSectionLabel("CORE TRY-ON")
            Box(Modifier.padding(bottom = heroLift.dp)) {
                AtelierHero(
                    brand = LookbookCopy.PRODUCT_NAME,
                    headline = "Start try-on shoot",
                    support = "Abaya, hijab, and shalwar on-device with Fast or Pro local try-on.",
                    cta = LookbookCopy.ACTION_START_TRY_ON,
                    onCta = onNewLook,
                    statusLine = statusLine,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        if (!proReady) {
            item(key = "pro-cta") {
                GlassCard(onClick = onOpenPacks) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VestraColors.GlassFillStrong)
                                .border(1.dp, VestraColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = VestraColors.Accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Install Pro pack", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "One download. Fully offline after. Free cloud try-on stays in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        item(key = "recent-header") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassSectionLabel("RECENT LOOKS")
                if (recent.isNotEmpty()) {
                    Text(
                        "Open gallery",
                        style = MaterialTheme.typography.labelMedium,
                        color = VestraColors.Accent,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable(onClick = onOpenWardrobe),
                    )
                }
            }
        }

        item(key = "recent") {
            if (recent.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent.take(12), key = { it.id }) { entry ->
                        val file = File(entry.imagePath)
                        Box(
                            Modifier
                                .width(148.dp)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            VestraColors.GlassHighlight,
                                            VestraColors.Accent.copy(alpha = 0.35f),
                                        ),
                                    ),
                                    RoundedCornerShape(24.dp),
                                )
                                .combinedClickable(
                                    onClick = onOpenWardrobe,
                                    onLongClick = {
                                        if (file.exists()) {
                                            MediaExport.share(context, file, "Share look")
                                        }
                                    },
                                ),
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Recent look ${entry.personLabel}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                VestraColors.AtelierCanvas.copy(alpha = 0.8f),
                                            ),
                                        ),
                                    ),
                            )
                            Text(
                                entry.personLabel.ifBlank { "Look" },
                                style = MaterialTheme.typography.labelMedium,
                                color = VestraColors.Ivory,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 12.dp, end = 44.dp, bottom = 12.dp),
                            )
                            IconButton(
                                onClick = {
                                    if (file.exists()) {
                                        MediaExport.share(context, file, LookbookCopy.ACTION_SHARE)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .semantics {
                                        contentDescription = LookbookCopy.ACTION_SHARE
                                    },
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = null,
                                    tint = VestraColors.Ivory,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(VestraColors.GlassFill)
                        .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(24.dp))
                        .clickable(onClick = onNewLook),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Tap to cast your first look",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
