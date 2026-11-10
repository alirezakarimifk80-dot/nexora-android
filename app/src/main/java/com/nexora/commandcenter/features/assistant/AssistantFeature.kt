package com.nexora.commandcenter.features.assistant

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.commandcenter.BuildConfig
import com.nexora.commandcenter.core.common.AppResult
import com.nexora.commandcenter.core.common.NetworkMonitor
import com.nexora.commandcenter.core.database.ChatMessageEntity
import com.nexora.commandcenter.core.database.ConversationEntity
import com.nexora.commandcenter.core.database.QueuedAiRequestEntity
import com.nexora.commandcenter.core.database.SmartMemoryEntity
import com.nexora.commandcenter.core.datastore.NexoraPreferencesDataStore
import com.nexora.commandcenter.core.designsystem.NexoraAiOrb
import com.nexora.commandcenter.core.designsystem.NexoraButton
import com.nexora.commandcenter.core.designsystem.NexoraCard
import com.nexora.commandcenter.core.designsystem.NexoraColors
import com.nexora.commandcenter.core.designsystem.NexoraDialog
import com.nexora.commandcenter.core.designsystem.NexoraEmptyState
import com.nexora.commandcenter.core.designsystem.NexoraErrorState
import com.nexora.commandcenter.core.designsystem.NexoraGlyph
import com.nexora.commandcenter.core.designsystem.NexoraLoading
import com.nexora.commandcenter.core.designsystem.NexoraSectionHeader
import com.nexora.commandcenter.core.designsystem.NexoraSuccessBanner
import com.nexora.commandcenter.core.designsystem.NexoraTextField
import com.nexora.commandcenter.data.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias AIViewModel = AssistantViewModel
typealias ChatViewModel = AssistantViewModel

data class ChatUiState(
    val isOnline: Boolean = true,
    val isStreaming: Boolean = false,
    val uploadProgress: Float = 0f,
    val smartMemoryEnabled: Boolean = true,
    val activeProvider: String = "gemini",
    val activeModel: String = "gemini-3.1-flash-lite",
    val errorBanner: String? = null
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val preferencesDataStore: NexoraPreferencesDataStore,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnlineFlow: Flow<Boolean> = networkMonitor.isOnline

    val diagnosticsState = aiRepository.diagnosticsStateFlow

    val prefs = preferencesDataStore.userPreferencesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NexoraPreferencesDataStore.UserPreferences()
    )

    private val _conversationSearchQuery = MutableStateFlow("")
    val conversationSearchQuery: StateFlow<String> = _conversationSearchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationEntity>> = _conversationSearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) aiRepository.conversationsFlow
            else aiRepository.searchConversations(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<SmartMemoryEntity>> = aiRepository.memoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedRequests: StateFlow<List<QueuedAiRequestEntity>> = aiRepository.queuedRequestsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedConversationId = MutableStateFlow<Long?>(null)
    val selectedConversationId: StateFlow<Long?> = _selectedConversationId.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _lastFailedPrompt = MutableStateFlow<Pair<String, String>?>(null)
    private var activeGenerationJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = _selectedConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else aiRepository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            aiRepository.conversationsFlow.collect { list ->
                if (_selectedConversationId.value == null && list.isNotEmpty()) {
                    _selectedConversationId.value = list.first().id
                } else if (list.isEmpty()) {
                    val newId = aiRepository.createConversation("NEXORA Neural Session")
                    _selectedConversationId.value = newId
                }
            }
        }
        // Automatically flush queued offline requests & check backend health when connectivity returns
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    aiRepository.checkBackendHealth()
                    aiRepository.flushOfflineQueue()
                }
            }
        }
    }

    fun runConnectionDiagnostics(
        testPrompt: String = AiRepository.DEFAULT_DIAGNOSTIC_PROMPT
    ) {
        viewModelScope.launch {
            aiRepository.runAiConnectionDiagnostics(testPrompt)
        }
    }

    fun updateProviderModelAndUrl(provider: String, model: String, baseUrl: String) {
        viewModelScope.launch {
            preferencesDataStore.setAiProviderAndModel(provider, model)
            if (baseUrl.isNotBlank()) {
                preferencesDataStore.setBackendBaseUrl(baseUrl)
            }
            aiRepository.checkBackendHealth()
        }
    }

    fun setConversationSearchQuery(query: String) {
        _conversationSearchQuery.value = query
    }

    fun selectConversation(id: Long) {
        _selectedConversationId.value = id
        _errorBanner.value = null
    }

    fun saveDraft(draftText: String) {
        val convId = _selectedConversationId.value ?: return
        viewModelScope.launch {
            aiRepository.saveDraft(convId, draftText)
        }
    }

    fun createNewConversation(defaultTitle: String) {
        viewModelScope.launch {
            val id = aiRepository.createConversation(defaultTitle.ifBlank { "New Neural Session" })
            _selectedConversationId.value = id
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            aiRepository.deleteConversation(id)
            if (_selectedConversationId.value == id) {
                _selectedConversationId.value = conversations.value.firstOrNull { it.id != id }?.id
            }
        }
    }

    fun clearAllConversationHistory() {
        viewModelScope.launch {
            aiRepository.clearAllConversations()
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isTyping.value = false
    }

    fun sendPrompt(prompt: String, mode: String) {
        val convId = _selectedConversationId.value ?: return
        if (prompt.isBlank()) return

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isTyping.value = true
            _errorBanner.value = null
            _lastFailedPrompt.value = null
            try {
                when (val res = aiRepository.sendMessageWithStreaming(convId, prompt.trim(), mode)) {
                    is AppResult.Error -> {
                        _lastFailedPrompt.value = prompt.trim() to mode
                        _errorBanner.value = res.message
                    }
                    else -> {}
                }
            } catch (_: CancellationException) {
                // Generation stopped by user
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun regenerateLastResponse() {
        val convId = _selectedConversationId.value ?: return
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isTyping.value = true
            _errorBanner.value = null
            try {
                when (val res = aiRepository.regenerateLastResponse(convId)) {
                    is AppResult.Error -> _errorBanner.value = res.message
                    else -> {}
                }
            } catch (_: CancellationException) {
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun retryLastPrompt() {
        val last = _lastFailedPrompt.value ?: return
        sendPrompt(last.first, last.second)
    }

    fun flushOfflineQueueNow() {
        viewModelScope.launch {
            _isTyping.value = true
            aiRepository.flushOfflineQueue()
            _isTyping.value = false
        }
    }

    fun removeQueuedRequest(id: Long) {
        viewModelScope.launch {
            aiRepository.removeQueuedRequest(id)
        }
    }

    fun setSmartMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setSmartMemoryEnabled(enabled)
        }
    }

    fun saveMemoryFact(title: String, factContent: String, category: String = "Context") {
        viewModelScope.launch {
            aiRepository.saveUserMemory(title, factContent, category)
        }
    }

    fun deleteMemoryFact(memoryId: Long) {
        viewModelScope.launch {
            aiRepository.deleteMemory(memoryId)
        }
    }

    fun analyzeImage(prompt: String, base64Image: String, mimeType: String) {
        val convId = _selectedConversationId.value ?: return
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isTyping.value = true
            _uploadProgress.value = 0.1f
            _errorBanner.value = null
            when (
                val res = aiRepository.analyzeImage(
                    conversationId = convId,
                    prompt = prompt,
                    imageBase64 = base64Image,
                    mimeType = mimeType,
                    onProgress = { progress -> _uploadProgress.value = progress }
                )
            ) {
                is AppResult.Error -> _errorBanner.value =
                    "Vision Notice (${BuildConfig.NEXORA_BACKEND_BASE_URL}): ${res.message}"
                else -> {}
            }
            _uploadProgress.value = 0f
            _isTyping.value = false
        }
    }
}

@Composable
fun AssistantScreen(
    isFa: Boolean,
    initialPrompt: String = "",
    onOpenConnectionTest: () -> Unit = {},
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isOnline by viewModel.isOnlineFlow.collectAsState(initial = true)
    val prefs by viewModel.prefs.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.conversationSearchQuery.collectAsState()
    val selectedId by viewModel.selectedConversationId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val queuedRequests by viewModel.queuedRequests.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val errorBanner by viewModel.errorBanner.collectAsState()

    val currentConv = remember(conversations, selectedId) {
        conversations.find { it.id == selectedId }
    }

    var inputText by remember(selectedId) { mutableStateOf(currentConv?.draftText.orEmpty()) }
    var activeMode by remember { mutableStateOf("chat") }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var newSessionTitle by remember { mutableStateOf("") }
    var memorySearchQuery by remember { mutableStateOf("") }
    var newMemoryTitle by remember { mutableStateOf("") }
    var newMemoryFact by remember { mutableStateOf("") }
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    var consumedInitialPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(initialPrompt, selectedId) {
        if (initialPrompt.isNotBlank() && !consumedInitialPrompt && selectedId != null) {
            consumedInitialPrompt = true
            viewModel.sendPrompt(initialPrompt, "chat")
        }
    }

    val suggestedPrompts = if (isFa) {
        listOf(
            "معماری MVVM + Clean Architecture را توضیح بده",
            "این متن را به ۳ نکته کلیدی خلاصه کن",
            "بهترین روش بهینه‌سازی Jetpack Compose چیست؟"
        )
    } else {
        listOf(
            "Explain NEXORA Clean Architecture & Hilt DI",
            "Generate a Kotlin Coroutines Flow retry pattern",
            "Summarize key security rules for Android APK builds"
        )
    }

    // Voice Input Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val updated = if (inputText.isBlank()) spoken else "$inputText $spoken"
                inputText = updated
                viewModel.saveDraft(updated)
            }
        }
    }

    // Image Attachment Launcher with MIME & size validation
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
            if (bytes != null) {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val prompt = inputText.ifBlank {
                    if (isFa) "این تصویر را به صورت دقیق تحلیل کن" else "Analyze this image in detail"
                }
                viewModel.analyzeImage(prompt, base64, mime)
                inputText = ""
                viewModel.saveDraft("")
            }
        }
    }

    // New Conversation Dialog
    if (showNewSessionDialog) {
        NexoraDialog(
            title = if (isFa) "ایجاد نشست هوشمند جدید" else "New AI Command Thread",
            onDismiss = { showNewSessionDialog = false },
            confirmText = if (isFa) "ایجاد" else "Create",
            onConfirm = {
                viewModel.createNewConversation(
                    newSessionTitle.ifBlank {
                        if (isFa) "گفتگوی جدید" else "New Neural Thread"
                    }
                )
                newSessionTitle = ""
                showNewSessionDialog = false
            }
        ) {
            NexoraTextField(
                value = newSessionTitle,
                onValueChange = { newSessionTitle = it },
                placeholder = if (isFa) "عنوان گفتگو..." else "Thread title (e.g., Security Audit)..."
            )
        }
    }

    // User-Controlled Smart Memory Dialog
    if (showMemoryDialog) {
        val filteredMemories = remember(memories, memorySearchQuery) {
            if (memorySearchQuery.isBlank()) memories
            else memories.filter {
                it.title.contains(memorySearchQuery, ignoreCase = true) ||
                    it.factContent.contains(memorySearchQuery, ignoreCase = true)
            }
        }
        NexoraDialog(
            title = if (isFa) "مدیریت حافظه هوشمند (Smart Memory)" else "User-Controlled Smart Memory Vault",
            onDismiss = { showMemoryDialog = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFa) "فعال‌سازی حافظه هوشمند در گفتگوها" else "Enable Smart Memory Context",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = prefs.smartMemoryEnabled,
                    onCheckedChange = { viewModel.setSmartMemoryEnabled(it) }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isFa) {
                    "هیچ اطلاعاتی بدون تأیید شما ذخیره نمی‌شود. موارد ذخیره‌شده برای شخصی‌سازی پاسخ‌ها استفاده می‌شوند."
                } else {
                    "Zero silent tracking: Only facts you explicitly save are included in AI context."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NexoraTextField(
                value = newMemoryTitle,
                onValueChange = { newMemoryTitle = it },
                placeholder = if (isFa) "عنوان نکته (مثلاً استک فنی من)..." else "Memory title (e.g., Preferred Tech Stack)..."
            )
            Spacer(modifier = Modifier.height(6.dp))
            NexoraTextField(
                value = newMemoryFact,
                onValueChange = { newMemoryFact = it },
                placeholder = if (isFa) "متن نکته برای به خاطر سپردن..." else "Fact to remember (e.g., Kotlin + Jetpack Compose + Hilt)..."
            )
            Spacer(modifier = Modifier.height(8.dp))
            NexoraButton(
                text = if (isFa) "افزودن به حافظه هوشمند" else "Save Fact to Memory",
                onClick = {
                    if (newMemoryFact.isNotBlank()) {
                        viewModel.saveMemoryFact(newMemoryTitle, newMemoryFact)
                        newMemoryTitle = ""
                        newMemoryFact = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            NexoraTextField(
                value = memorySearchQuery,
                onValueChange = { memorySearchQuery = it },
                placeholder = if (isFa) "جستجو در حافظه..." else "Search saved memories..."
            )
            Spacer(modifier = Modifier.height(8.dp))
            filteredMemories.take(5).forEach { mem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mem.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            mem.factContent,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.deleteMemoryFact(mem.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Memory")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    viewModel.clearAllConversationHistory()
                    showMemoryDialog = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isFa) "پاکسازی کامل تاریخچه گفتگوها" else "Clear All Conversation History")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 1. Offline Status & Queued Requests Banner
        if (!isOnline || queuedRequests.isNotEmpty()) {
            NexoraCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                borderColor = NexoraColors.AmberSignal,
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!isOnline) {
                                if (isFa) "حالت آفلاین · ذخیره پیش‌نویس و صف ارسال فعال است" else "OFFLINE MODE · Drafts & Request Queue Active"
                            } else {
                                if (isFa) "${queuedRequests.size} درخواست در صف انتظار ارسال" else "${queuedRequests.size} Queued Offline Requests Ready"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = NexoraColors.AmberSignal
                        )
                        Text(
                            text = if (isFa) {
                                "گفتگوهای قبلی در دسترس هستند. پیام‌های جدید در صف ذخیره شده و پس از اتصال ارسال می‌شوند."
                            } else {
                                "Local threads remain accessible. Queued prompts dispatch automatically when online."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOnline && queuedRequests.isNotEmpty()) {
                        NexoraButton(
                            text = if (isFa) "ارسال صف (${queuedRequests.size})" else "Sync (${queuedRequests.size})",
                            onClick = { viewModel.flushOfflineQueueNow() }
                        )
                    }
                }
            }
        }

        // 2. Conversation Search + Memory Button + New Thread Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NexoraAiOrb(modifier = Modifier.size(38.dp), isActive = isTyping)
            Box(modifier = Modifier.weight(1f)) {
                NexoraTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setConversationSearchQuery(it) },
                    placeholder = if (isFa) "جستجو در گفتگوها..." else "Search threads...",
                    maxLines = 1
                )
            }
            IconButton(onClick = { showMemoryDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Smart Memory Vault",
                    tint = if (prefs.smartMemoryEnabled) NexoraColors.CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showNewSessionDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Conversation")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Conversation Selector Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations) { conv ->
                FilterChip(
                    selected = conv.id == selectedId,
                    onClick = { viewModel.selectConversation(conv.id) },
                    label = { Text(conv.title, maxLines = 1) },
                    trailingIcon = {
                        if (conversations.size > 1) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.clickable {
                                    viewModel.deleteConversation(conv.id)
                                }
                            )
                        }
                    }
                )
            }
        }

        // 4. Mode Switcher + AI Connection Test + Regenerate & Stop Generation Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeMode == "chat",
                    onClick = { activeMode = "chat" },
                    label = { Text(if (isFa) "چت جریانی (SSE)" else "AI Stream") }
                )
                FilterChip(
                    selected = activeMode == "summarize",
                    onClick = { activeMode = "summarize" },
                    label = { Text(if (isFa) "خلاصه‌ساز" else "Summarizer") }
                )
                FilterChip(
                    selected = false,
                    onClick = onOpenConnectionTest,
                    label = { Text(if (isFa) "تست اتصال AI" else "AI Connection Test") }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isTyping) {
                    OutlinedButton(onClick = { viewModel.stopGeneration() }) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Generation",
                            tint = NexoraColors.CrimsonSignal
                        )
                        Text(
                            text = if (isFa) "توقف" else "Stop",
                            color = NexoraColors.CrimsonSignal
                        )
                    }
                } else if (messages.any { it.role == "user" }) {
                    IconButton(onClick = { viewModel.regenerateLastResponse() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate Response",
                            tint = NexoraColors.CyberCyan
                        )
                    }
                }
            }
        }

        // 5. Suggested Prompts
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(suggestedPrompts) { prompt ->
                OutlinedButton(
                    onClick = { viewModel.sendPrompt(prompt, activeMode) }
                ) {
                    Text(prompt, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        NexoraSuccessBanner(
            message = copiedNotice,
            onDismiss = { copiedNotice = null }
        )

        // Vision Upload Progress Bar
        if (uploadProgress > 0f && uploadProgress < 1f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (isFa) "در حال بارگذاری و تحلیل تصویر..." else "Uploading & analyzing image (${(uploadProgress * 100).toInt()}%)...",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexoraColors.CyberCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isTyping) {
            NexoraLoading(
                modifier = Modifier.padding(vertical = 6.dp),
                label = if (isFa) "نکسورا در حال تولید پاسخ جریانی است..." else "NEXORA Neural Core streaming response..."
            )
        }

        errorBanner?.let { err ->
            NexoraErrorState(
                title = if (isFa) "وضعیت درخواست هوش مصنوعی" else "AI Gateway & Offline Queue Notice",
                message = err,
                retryLabel = if (isFa) "تلاش مجدد" else "Retry",
                onRetry = { viewModel.retryLastPrompt() },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // 6. Messages List or Empty State
        if (messages.isEmpty() && !isTyping) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                NexoraEmptyState(
                    title = if (isFa) "نشست هوشمند آماده است" else "NEXORA Neural Session Ready",
                    subtitle = if (isFa) {
                        "پیام ارسال کنید، تصویر پیوست نمایید یا نکات مهم را در حافظه هوشمند (${memories.size} مورد) ذخیره کنید."
                    } else {
                        "Send a prompt, attach a JPEG/PNG/WEBP image for Vision analysis, or manage ${memories.size} saved Smart Memory facts."
                    },
                    glyph = NexoraGlyph.ORB,
                    actionLabel = if (isFa) "ارسال دستور نمونه" else "Run Sample Architecture Audit",
                    onAction = {
                        viewModel.sendPrompt(suggestedPrompts.first(), activeMode)
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    val isUser = msg.role == "user"
                    NexoraCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (isUser) {
                            NexoraColors.ElectricViolet.copy(alpha = 0.45f)
                        } else {
                            NexoraColors.CyberCyan.copy(alpha = 0.35f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isUser && isFa -> "شما · COMMANDER"
                                    isUser -> "COMMANDER"
                                    isFa -> "هسته هوشمند نکسورا · ${msg.mode.uppercase()}"
                                    else -> "NEXORA CORE · ${msg.mode.uppercase()}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUser) NexoraColors.LuminousPurple else NexoraColors.CyberCyan
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        viewModel.saveMemoryFact(
                                            title = msg.content.take(28),
                                            factContent = msg.content.take(240),
                                            category = if (isUser) "User Fact" else "AI Insight"
                                        )
                                        copiedNotice = if (isFa) {
                                            "در حافظه هوشمند (Smart Memory) ذخیره شد."
                                        } else {
                                            "Saved to User-Controlled Smart Memory."
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "Save to Smart Memory"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, msg.content)
                                        copiedNotice = if (isFa) "پاسخ در کلیپ‌بورد کپی شد." else "Message copied to clipboard."
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy response"
                                    )
                                }
                                IconButton(
                                    onClick = { shareMessage(context, msg.content) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share response"
                                    )
                                }
                            }
                        }
                        MarkdownFormattedText(content = msg.content)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 7. Composer Row with Image Attachment + Voice Input + Draft Persistence + Send
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { imagePickerLauncher.launch("image/*") }
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach Image",
                    tint = NexoraColors.CyberCyan
                )
            }
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isFa) "fa-IR" else "en-US")
                    }
                    runCatching { voiceLauncher.launch(intent) }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = NexoraColors.LuminousPurple
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                NexoraTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        viewModel.saveDraft(it)
                    },
                    placeholder = if (!isOnline) {
                        if (isFa) "نوشتن پیش‌نویس یا افزودن به صف آفلاین..." else "Write draft or queue for when online..."
                    } else {
                        if (isFa) "پیام یا فرمان صوتی..." else "Message NEXORA AI..."
                    }
                )
            }
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendPrompt(inputText, activeMode)
                        inputText = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = NexoraColors.CyberCyan
                )
            }
        }
    }
}

@Composable
fun AiConnectionTestScreen(
    isFa: Boolean,
    onBackToAssistant: () -> Unit = {},
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val diagnostics by viewModel.diagnosticsState.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    var testPromptInput by remember {
        mutableStateOf(AiRepository.DEFAULT_DIAGNOSTIC_PROMPT)
    }
    var baseUrlInput by remember(prefs.backendBaseUrl) {
        mutableStateOf(prefs.backendBaseUrl)
    }
    var modelInput by remember(prefs.aiModel) {
        mutableStateOf(prefs.aiModel)
    }

    LaunchedEffect(Unit) {
        if (diagnostics.backendConnection == "UNTESTED") {
            viewModel.runConnectionDiagnostics(testPromptInput)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "AI PIPELINE DIAGNOSTICS (ZERO SECRETS EXPOSED)",
                    title = if (isFa) "تست اتصال موتور هوش مصنوعی نکسورا" else "NEXORA AI Connection Test",
                    actionLabel = if (isFa) "بازگشت به چت" else "Back to Chat",
                    onAction = onBackToAssistant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isFa) {
                        "بررسی کامل خط لوله: Android -> FastAPI -> AI Provider -> FastAPI -> Android بدون نمایش کلیدهای محرمانه."
                    } else {
                        "End-to-end verification of Android -> FastAPI -> AI Provider -> FastAPI -> Android. Server API keys are never exposed."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 7 Required Diagnostic Status Cards
        item {
            NexoraCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (diagnostics.lastError == null && diagnostics.providerConfigured) {
                    NexoraColors.EmeraldPulse
                } else {
                    NexoraColors.AmberSignal
                }
            ) {
                NexoraSectionHeader(
                    eyebrow = "LIVE PIPELINE TELEMETRY",
                    title = if (isFa) "وضعیت اتصال و پیکربندی سرور" else "Connection & Provider Diagnostics"
                )
                Spacer(modifier = Modifier.height(10.dp))

                DiagnosticMetricRow(
                    label = if (isFa) "۱. اتصال سرور بک‌اند" else "1. Backend Connection",
                    value = diagnostics.backendConnection
                )
                DiagnosticMetricRow(
                    label = if (isFa) "۲. وضعیت اندپوینت API" else "2. API Endpoint Status",
                    value = diagnostics.apiEndpointStatus
                )
                DiagnosticMetricRow(
                    label = if (isFa) "۳. پیکربندی ارائه‌دهنده AI" else "3. Provider Configuration",
                    value = diagnostics.providerConfiguration
                )
                DiagnosticMetricRow(
                    label = if (isFa) "۴. پیکربندی مدل هوش مصنوعی" else "4. Model Configuration",
                    value = "${diagnostics.modelConfiguration} (Fallbacks: ${diagnostics.fallbackModels.joinToString(", ")})"
                )
                DiagnosticMetricRow(
                    label = if (isFa) "۵. وضعیت آخرین درخواست" else "5. Last Request Status",
                    value = diagnostics.lastRequestStatus
                )
                DiagnosticMetricRow(
                    label = if (isFa) "۶. زمان پاسخ‌دهی (Latency)" else "6. Response Time",
                    value = if (diagnostics.responseTimeMs > 0) "${diagnostics.responseTimeMs} ms" else "N/A"
                )
                DiagnosticMetricRow(
                    label = if (isFa) "۷. آخرین خطا" else "7. Last Error",
                    value = diagnostics.lastError ?: "None (0 Errors)"
                )
            }
        }

        // Endpoint & Model Quick Switcher
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "CLOUDFLARE WORKER & WORKERS AI MODEL",
                    title = if (isFa) "تنظیم آدرس Cloudflare Worker و مدل" else "Configure Cloudflare Worker URL & Model"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Llama 3.3 70B" to "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                        "Gemma 3 12B" to "@cf/google/gemma-3-12b-it",
                        "Llama 3.1 8B" to "@cf/meta/llama-3.1-8b-instruct"
                    ).forEach { (labelText, defModel) ->
                        FilterChip(
                            selected = modelInput == defModel,
                            onClick = {
                                modelInput = defModel
                                viewModel.updateProviderModelAndUrl("cloudflare-workers-ai", defModel, baseUrlInput)
                            },
                            label = { Text(labelText) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                NexoraTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = if (isFa) "شناسه مدل Workers AI" else "Active Workers AI Model",
                    placeholder = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
                )
                Spacer(modifier = Modifier.height(8.dp))
                NexoraTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = if (isFa) "آدرس پایه Cloudflare Worker" else "Cloudflare Worker Base URL",
                    placeholder = "https://nexora-ai-worker.workers.dev/"
                )
                Spacer(modifier = Modifier.height(8.dp))
                NexoraButton(
                    text = if (isFa) "اعمال تنظیمات کلادفلر" else "Apply Cloudflare Worker Configuration",
                    onClick = {
                        viewModel.updateProviderModelAndUrl("cloudflare-workers-ai", modelInput, baseUrlInput)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isPrimary = false
                )
            }
        }

        // Live Real Request Verification
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "END-TO-END VERIFICATION REQUEST",
                    title = if (isFa) "اجرای تست واقعی خط لوله هوش مصنوعی" else "Execute Real AI Pipeline Test"
                )
                Spacer(modifier = Modifier.height(8.dp))
                NexoraTextField(
                    value = testPromptInput,
                    onValueChange = { testPromptInput = it },
                    label = if (isFa) "پیام تست واقعی" else "Real Verification Prompt",
                    placeholder = if (isFa) "پیام تست را وارد کنید (مثلاً Hello NEXORA.)..." else "Enter test prompt (e.g., Hello NEXORA.)...",
                    minLines = 2,
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(10.dp))
                NexoraButton(
                    text = if (diagnostics.isRunningTest) {
                        if (isFa) "در حال ارسال درخواست واقعی..." else "Running Live AI Pipeline Test..."
                    } else {
                        if (isFa) "ارسال درخواست تست واقعی" else "Send Real Test Request"
                    },
                    leadingGlyph = NexoraGlyph.ORB,
                    onClick = { viewModel.runConnectionDiagnostics(testPromptInput) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (diagnostics.lastTestResponse.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isFa) "پاسخ واقعی دریافت شده از ارائه‌دهنده هوش مصنوعی:" else "VERIFIED REAL AI PROVIDER RESPONSE:",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexoraColors.CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MarkdownFormattedText(content = diagnostics.lastTestResponse)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MarkdownFormattedText(content: String) {
    val segments = content.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEachIndexed { index, block ->
            if (index % 2 == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = block.trim(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (block.isNotBlank()) {
                Text(
                    text = block.trim(),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NEXORA AI Response", text))
}

private fun shareMessage(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share NEXORA Response"))
}
