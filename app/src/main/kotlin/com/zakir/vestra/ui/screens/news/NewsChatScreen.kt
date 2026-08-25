package com.zakir.vestra.ui.screens.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.zakir.vestra.shared.chat.ChatMessage
import com.zakir.vestra.shared.chat.ChatRepository
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.news.NewsItem
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.ChatViewModel
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassErrorBanner
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.components.QuickPromptItem
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.launch

@Composable
fun NewsChatScreen(
    newsRepository: NewsRepository?,
    chatViewModel: ChatViewModel?,
    appSettings: AppSettings? = null,
    freeCloudDiscovery: FreeCloudDiscovery? = null,
    packManager: ModelPackManager? = null,
    onHeadlineSelected: (String?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val newsItems by newsRepository?.items?.collectAsState()
        ?: remember { mutableStateOf(emptyList<NewsItem>()) }
    val newsError by newsRepository?.error?.collectAsState()
        ?: remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val chatMessages by chatViewModel?.messages?.collectAsState()
        ?: remember { mutableStateOf(emptyList<ChatMessage>()) }
    val activeModule by chatViewModel?.activeModule?.collectAsState()
        ?: remember { mutableStateOf(ChatRepository.DEFAULT_MODULE) }
    val chatBusy by chatViewModel?.busy?.collectAsState() ?: remember { mutableStateOf(false) }
    val chatError by chatViewModel?.error?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val chatLogs by chatViewModel?.formattedLogs?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    var chatInput by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }

    val chatModules = remember {
        listOf(
            Triple(ChatRepository.DEFAULT_MODULE, "News & Trends", Icons.Outlined.Newspaper),
            Triple(ChatRepository.MODULE_CODE, "Code Studio", Icons.Outlined.Code),
            Triple(ChatRepository.MODULE_IMAGE, "Image Studio", Icons.Outlined.Image),
            Triple(ChatRepository.MODULE_VIDEO, "Video Studio", Icons.Outlined.Videocam),
            Triple(ChatRepository.MODULE_AUDIO, "Audio Studio", Icons.Outlined.GraphicEq),
        )
    }

    val codeId by appSettings?.codeProviderId?.collectAsState()
        ?: remember { mutableStateOf(CloudModelCatalog.defaultFor(AiCapability.CODE).id) }
    val cloudEnabled by appSettings?.cloudModelsEnabled?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val packStates by packManager?.states?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }
    val chatProvider = appSettings?.selectedProvider(AiCapability.CODE)
        ?: CloudModelCatalog.defaultFor(AiCapability.CODE)

    val pickerModels = remember(freeCloudDiscovery, appSettings, cloudEnabled) {
        when {
            !cloudEnabled -> emptyList()
            appSettings != null && freeCloudDiscovery != null ->
                freeCloudDiscovery.selectable(appSettings, AiCapability.CODE)
            else -> CloudModelCatalog.forCapability(AiCapability.CODE)
        }
    }

    val onDeviceEntries = remember(packStates) {
        LocalModelCatalog.forStudioPicker(AiCapability.CODE).map { entry ->
            val packReady = entry.packId?.let { packStates[it]?.isReady() == true } == true ||
                packStates[entry.id]?.isReady() == true
            OnDevicePickerEntry(
                id = entry.id,
                displayName = entry.displayName,
                detail = entry.testingNote,
                ready = LocalModelCatalog.studioEntryReady(entry, packReady),
                statusLabel = LocalModelCatalog.studioStatusLabel(entry, packReady),
            )
        }
    }

    val localChatSelected = LocalModelCatalog.isSelectableStudioId(codeId, AiCapability.CODE)
    val chatModelLabel = if (localChatSelected) {
        (LocalModelCatalog.byId(codeId)?.displayName ?: "Local on-device") + " (offline)"
    } else {
        chatProvider.displayName
    }

    // Refresh headlines on mount
    LaunchedEffect(newsRepository) {
        if (newsRepository != null) {
            refreshing = true
            newsRepository.refresh()
            refreshing = false
        }
    }

    // Auto-scroll to bottom on new message or when generation starts
    LaunchedEffect(chatMessages.size, chatBusy) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VestraColors.Canvas),
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = VestraColors.Accent,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "LOOKBOOK CONVERSATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        ),
                        color = VestraColors.Ink,
                    )
                    Text(
                        text = if (localChatSelected) "Gemma On-Device AI" else chatProvider.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = VestraColors.Accent,
                    )
                }
            }

            if (chatMessages.isNotEmpty() && chatViewModel != null) {
                IconButton(
                    onClick = { chatViewModel.clearHistory(activeModule) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear conversation",
                        modifier = Modifier.size(18.dp),
                        tint = VestraColors.InkMuted,
                    )
                }
            }
        }

        // Module Context Selector Strip (Local cache partitioning)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(chatModules) { (modId, modLabel, modIcon) ->
                val isSelected = activeModule == modId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) VestraColors.Accent.copy(alpha = 0.15f) else VestraColors.GlassFill,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) VestraColors.Accent else VestraColors.GlassBorder.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { chatViewModel?.setModule(modId) }
                        .testTag(TestTags.chatModuleTab(modId)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = modIcon,
                            contentDescription = modLabel,
                            modifier = Modifier.size(13.dp),
                            tint = if (isSelected) VestraColors.Accent else VestraColors.InkMuted,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = modLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                            ),
                            color = if (isSelected) VestraColors.Accent else VestraColors.InkMuted,
                        )
                    }
                }
            }
        }

        // Live News Dispatches Horizontal Carousel Strip
        if (newsRepository != null) {
            NewsHeadlinesBar(
                newsItems = newsItems,
                refreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        try {
                            newsRepository.refresh()
                        } finally {
                            refreshing = false
                        }
                    }
                },
                onHeadlineClick = { item, _ ->
                    chatInput = "Discuss this headline for modest fashion and on-device AI: ${item.title}"
                    onHeadlineSelected(item.title)
                },
            )
        }

        // News Error Banner
        if (newsError != null && newsItems.isEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                GlassCard {
                    Text(
                        text = newsError ?: "Could not load headlines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VestraColors.InkMuted,
                    )
                }
            }
        }

        // Chat Error Banner
        if (chatError != null && chatViewModel != null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                GlassErrorBanner(
                    message = chatError!!,
                    onRetry = { chatViewModel.clearError() },
                    retryLabel = "Dismiss",
                    onDismiss = { chatViewModel.clearError() },
                )
            }
        }

        // Primary Messages Stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (chatMessages.isEmpty()) {
                ChatEmptyState(
                    onPromptSelected = { selectedPrompt ->
                        chatInput = selectedPrompt
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = chatMessages,
                        key = { _, msg -> msg.id },
                    ) { index, msg ->
                        ChatMessageBubble(
                            message = msg,
                            index = index,
                            modelDisplayName = if (msg.role != "user") {
                                msg.providerId?.let { id ->
                                    LocalModelCatalog.byId(id)?.displayName ?: id
                                } ?: chatModelLabel
                            } else null,
                        )
                    }

                    // Active typing indicator if busy and no streaming chunk has been appended yet
                    if (chatBusy && (chatMessages.isEmpty() || chatMessages.last().role == "user")) {
                        item(key = "typing_indicator") {
                            ChatTypingIndicator(modelLabel = chatModelLabel)
                        }
                    }

                    // Bottom spacing above input bar
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // Contextual Quick Prompts for Chat
        val chatQuickPrompts = remember(newsItems) {
            val list = mutableListOf(
                QuickPromptItem("What modest fabrics breathe best in summer?", "Fabrics"),
                QuickPromptItem("Summarize latest industry headlines", "News"),
                QuickPromptItem("Compare silk vs linen drape for an abaya", "Style"),
                QuickPromptItem("Explain on-device LLM offline capabilities", "AI"),
            )
            if (newsItems.isNotEmpty()) {
                list.add(0, QuickPromptItem("Discuss '${newsItems.first().title}'", "Latest"))
            }
            list
        }

        // Persistent Bottom Input Bar
        if (chatViewModel != null) {
            ChatPersistentInputBar(
                prompt = chatInput,
                onPromptChange = { chatInput = it },
                modelLabel = chatModelLabel,
                busy = chatBusy,
                enabled = true,
                logs = chatLogs,
                quickPrompts = chatQuickPrompts,
                onSelectQuickPrompt = { selectedPrompt ->
                    chatInput = selectedPrompt
                },
                onModelClick = { if (appSettings != null) showModelPicker = true },
                onSend = {
                    val text = chatInput
                    chatInput = ""
                    chatViewModel.send(text)
                },
                onStop = { chatViewModel.cancel() },
            )
        }
    }

    // Model Selector Sheet Dialog
    if (showModelPicker && appSettings != null) {
        ModelPickerSheet(
            title = if (cloudEnabled) "Chat models" else "Chat models · on-device",
            models = pickerModels,
            selectedId = codeId,
            onDeviceEntries = onDeviceEntries,
            health = appSettings.modelHealth,
            onSelect = { chosen -> appSettings.setCodeProvider(chosen.id) },
            onSelectDevice = { entry ->
                if (entry.ready) appSettings.setLocalGenerator(AiCapability.CODE, entry.id)
            },
            onDismiss = { showModelPicker = false },
        )
    }
}
