package com.zakir.vestra.ui.screens.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelCatalog
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.news.NewsItem
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.ChatViewModel
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassErrorBanner
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.components.ModelPickerSheet
import com.zakir.vestra.ui.components.OnDevicePickerEntry
import com.zakir.vestra.ui.components.PromptComposer
import com.zakir.vestra.ui.components.ShimmerRows
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
    val newsItems by newsRepository?.items?.collectAsState()
        ?: remember { mutableStateOf(emptyList<NewsItem>()) }
    val newsError by newsRepository?.error?.collectAsState()
        ?: remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val chatMessages by chatViewModel?.messages?.collectAsState()
        ?: remember { mutableStateOf(emptyList<com.zakir.vestra.shared.chat.ChatMessage>()) }
    val chatBusy by chatViewModel?.busy?.collectAsState() ?: remember { mutableStateOf(false) }
    val chatError by chatViewModel?.error?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    var chatInput by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }

    val codeId by appSettings?.codeProviderId?.collectAsState()
        ?: remember { mutableStateOf(CloudModelCatalog.defaultFor(AiCapability.CODE).id) }
    val cloudEnabled by appSettings?.cloudModelsEnabled?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val packStates by packManager?.states?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }
    val chatProvider = appSettings?.selectedProvider(AiCapability.CODE)
        ?: CloudModelCatalog.defaultFor(AiCapability.CODE)

    // Cloud rows only exist once the master toggle is on — otherwise the sheet is
    // on-device only, matching what preflight() will actually allow to run.
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

    LaunchedEffect(newsRepository) {
        if (newsRepository != null) {
            refreshing = true
            newsRepository.refresh()
            refreshing = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassSectionLabel("NEWS & CHAT")
            if (newsRepository != null) {
                IconButton(
                    onClick = {
                        refreshing = true
                        scope.launch {
                            try {
                                newsRepository.refresh()
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    enabled = !refreshing,
                    modifier = Modifier.testTag(TestTags.CHAT_REFRESH_BUTTON),
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = VestraColors.Accent,
                        )
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh news", tint = VestraColors.Accent)
                    }
                }
            }
        }
        Text(
            "Fashion and AI headlines — discuss any story below.",
            style = MaterialTheme.typography.bodySmall,
            color = VestraColors.InkMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (newsRepository == null) {
            GlassCard(onClick = { onHeadlineSelected(null) }) {
                Text("News feed unavailable.", style = MaterialTheme.typography.bodyMedium, color = VestraColors.InkMuted)
            }
            return@Column
        }

        if (newsError != null && newsItems.isEmpty()) {
            GlassCard {
                Text(newsError ?: "Could not load headlines.", style = MaterialTheme.typography.bodyMedium, color = VestraColors.InkMuted)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (newsItems.isEmpty() && refreshing) {
            // Was blank space while the first refresh was in flight — nothing told the user
            // headlines were actually loading rather than just missing.
            ShimmerRows(count = 3, rowHeight = 44.dp)
        } else if (newsItems.isEmpty() && !refreshing) {
            GlassCard(onClick = { onHeadlineSelected(null) }) {
                Text("No headlines yet — refresh or start a chat below.", style = MaterialTheme.typography.bodyMedium, color = VestraColors.InkMuted)
            }
        } else {
            newsItems.take(5).forEachIndexed { index, item ->
                Spacer(Modifier.height(6.dp))
                GlassCard(
                    modifier = Modifier.testTag(TestTags.chatHeadlineCard(index)),
                    onClick = {
                        chatInput = "Discuss this headline for modest fashion and on-device AI: ${item.title}"
                        onHeadlineSelected(item.title)
                    },
                ) {
                    Text(
                        "${item.source} · ${item.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VestraColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (chatViewModel != null) {
            Spacer(Modifier.height(20.dp))
            GlassSectionLabel("CHAT")
            chatMessages.takeLast(6).forEachIndexed { index, msg ->
                val isUser = msg.role == "user"
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .testTag(TestTags.chatMessageBubble(index, msg.role)),
                    ) {
                        Text(
                            if (isUser) "YOU" else "ASSISTANT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) VestraColors.InkMuted else VestraColors.Accent,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = VestraColors.Ink)
                    }
                }
            }
            if (chatError != null) {
                Spacer(Modifier.height(8.dp))
                GlassErrorBanner(
                    message = chatError!!,
                    onRetry = { chatViewModel.clearError() },
                    retryLabel = "Dismiss",
                    onDismiss = { chatViewModel.clearError() },
                )
            }
            Spacer(Modifier.height(12.dp))
            PromptComposer(
                prompt = chatInput,
                onPromptChange = { chatInput = it },
                modelLabel = chatModelLabel,
                assistCount = 0,
                busy = chatBusy,
                enabled = true,
                onModelClick = { if (appSettings != null) showModelPicker = true },
                onSend = {
                    val text = chatInput
                    chatInput = ""
                    chatViewModel.send(text)
                },
                onStop = { chatViewModel.cancel() },
                placeholder = "Ask about headlines, local packs, or cloud models…",
            )
        }
        Spacer(Modifier.height(24.dp))
    }

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
