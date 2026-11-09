package com.nexora.commandcenter.data

import android.content.Context
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nexora.commandcenter.core.common.AppResult
import com.nexora.commandcenter.core.common.NetworkMonitor
import com.nexora.commandcenter.core.common.NotificationHelper
import com.nexora.commandcenter.core.database.ActivityLogDao
import com.nexora.commandcenter.core.database.ActivityLogEntity
import com.nexora.commandcenter.core.database.ChatDao
import com.nexora.commandcenter.core.database.ChatMessageEntity
import com.nexora.commandcenter.core.database.ConversationEntity
import com.nexora.commandcenter.core.database.NoteDao
import com.nexora.commandcenter.core.database.NoteEntity
import com.nexora.commandcenter.core.database.QueuedAiRequestDao
import com.nexora.commandcenter.core.database.QueuedAiRequestEntity
import com.nexora.commandcenter.core.database.SmartMemoryDao
import com.nexora.commandcenter.core.database.SmartMemoryEntity
import com.nexora.commandcenter.core.database.TaskDao
import com.nexora.commandcenter.core.database.TaskEntity
import com.nexora.commandcenter.core.datastore.NexoraPreferencesDataStore
import com.nexora.commandcenter.core.network.ApiErrorEnvelopeDto
import com.nexora.commandcenter.core.network.BackendHealthDto
import com.nexora.commandcenter.core.network.ChatHistoryItemDto
import com.nexora.commandcenter.core.network.ChatRequestDto
import com.nexora.commandcenter.core.network.ImageAnalysisRequestDto
import com.nexora.commandcenter.core.network.MemoryContextItemDto
import com.nexora.commandcenter.core.network.NexoraBackendApi
import com.nexora.commandcenter.core.network.ProviderInfoDto
import com.nexora.commandcenter.core.network.StreamChunkDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

typealias AIRepository = AiRepository

data class AiConnectionDiagnosticsState(
    val isRunningTest: Boolean = false,
    val backendConnection: String = "UNTESTED",
    val apiEndpointStatus: String = "UNTESTED",
    val baseUrl: String = "",
    val providerConfiguration: String = "gemini (Unverified)",
    val providerConfigured: Boolean = false,
    val configuredProviders: List<String> = emptyList(),
    val modelConfiguration: String = "gemini-3.1-flash-lite",
    val fallbackModels: List<String> = listOf("gemini-3.1-flash-lite", "gemini-3.8-flash"),
    val configurationNotice: String = "Run connection test to verify backend and AI provider.",
    val lastRequestStatus: String = "IDLE",
    val responseTimeMs: Long = 0L,
    val lastError: String? = null,
    val lastTestPrompt: String = "Hello NEXORA. Explain what you can do in three sentences.",
    val lastTestResponse: String = "",
    val lastCheckedAtMs: Long = 0L
)

@Singleton
class AiRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val smartMemoryDao: SmartMemoryDao,
    private val queuedAiRequestDao: QueuedAiRequestEntityDaoAlias,
    private val activityLogDao: ActivityLogDao,
    private val backendApi: NexoraBackendApi,
    private val networkMonitor: NetworkMonitor,
    private val preferencesDataStore: NexoraPreferencesDataStore,
    private val json: Json
) {
    companion object {
        val SUPPORTED_IMAGE_MIMES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024 // 4 MB
        const val DEFAULT_DIAGNOSTIC_PROMPT =
            "Hello NEXORA. Explain what you can do in three sentences."
    }

    private val _diagnosticsState = MutableStateFlow(AiConnectionDiagnosticsState())
    val diagnosticsStateFlow: StateFlow<AiConnectionDiagnosticsState> = _diagnosticsState.asStateFlow()

    val conversationsFlow: Flow<List<ConversationEntity>> = chatDao.observeConversations()
    val memoriesFlow: Flow<List<SmartMemoryEntity>> = smartMemoryDao.observeMemories()
    val queuedRequestsFlow: Flow<List<QueuedAiRequestEntity>> = queuedAiRequestDao.observeQueuedRequests()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        chatDao.searchConversations(query)

    fun observeMessages(conversationId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(conversationId)

    private fun extractReadableError(throwable: Throwable): String {
        if (throwable is HttpException) {
            val code = throwable.code()
            val rawBody = runCatching { throwable.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
            if (rawBody.isNotBlank()) {
                val parsed = runCatching { json.decodeFromString<ApiErrorEnvelopeDto>(rawBody) }.getOrNull()
                val msg = parsed?.readableMessage.orEmpty()
                if (msg.isNotBlank()) {
                    return "HTTP $code: $msg"
                }
            }
            return "HTTP $code (${throwable.message()})"
        }
        return throwable.localizedMessage ?: throwable.javaClass.simpleName
    }

    suspend fun createConversation(title: String): Long {
        val id = chatDao.insertConversation(ConversationEntity(title = title))
        activityLogDao.insertActivity(
            ActivityLogEntity(
                module = "AI",
                actionTitle = "Created AI Thread",
                detail = title
            )
        )
        return id
    }

    suspend fun saveDraft(conversationId: Long, draftText: String) {
        chatDao.updateConversationDraft(conversationId, draftText)
    }

    suspend fun deleteConversation(conversationId: Long) {
        chatDao.deleteConversation(conversationId)
    }

    suspend fun clearAllConversations() {
        chatDao.clearAllConversations()
        queuedAiRequestDao.clearQueuedRequests()
    }

    // -----------------------------------------------------------------------
    // User-Controlled Smart Memory Operations
    // -----------------------------------------------------------------------
    suspend fun saveUserMemory(title: String, factContent: String, category: String = "Preference") {
        if (factContent.isBlank()) return
        smartMemoryDao.insertMemory(
            SmartMemoryEntity(
                title = title.ifBlank { factContent.take(32) },
                factContent = factContent.trim(),
                category = category
            )
        )
        activityLogDao.insertActivity(
            ActivityLogEntity(
                module = "AI",
                actionTitle = "Saved Smart Memory Fact",
                detail = title.take(50)
            )
        )
    }

    suspend fun deleteMemory(memoryId: Long) {
        smartMemoryDao.deleteMemoryById(memoryId)
    }

    suspend fun clearAllMemories() {
        smartMemoryDao.clearAllMemories()
    }

    suspend fun checkBackendHealth(): Result<BackendHealthDto> = runCatching {
        val prefs = preferencesDataStore.userPreferencesFlow.first()
        val elapsedMs: Long
        var health: BackendHealthDto
        elapsedMs = measureTimeMillis {
            health = backendApi.checkHealth()
        }
        _diagnosticsState.value = _diagnosticsState.value.copy(
            backendConnection = if (health.configured) "CONNECTED (OK)" else "DEGRADED (CONFIG REQUIRED)",
            apiEndpointStatus = "/api/v1/health · 200 OK",
            baseUrl = prefs.backendBaseUrl,
            providerConfiguration = "${health.activeProvider.uppercase()} · ${if (health.configured) "Server Key Configured" else "Key Missing"}",
            providerConfigured = health.configured,
            configuredProviders = health.configuredProviders,
            modelConfiguration = health.activeModel.ifBlank { health.model },
            fallbackModels = health.fallbackModels,
            configurationNotice = health.configurationNotice.ifBlank {
                if (health.configured) "Server-side AI credentials verified." else "Server-side API key required."
            },
            responseTimeMs = elapsedMs,
            lastCheckedAtMs = System.currentTimeMillis()
        )
        health
    }.onFailure { err ->
        val readable = extractReadableError(err)
        _diagnosticsState.value = _diagnosticsState.value.copy(
            backendConnection = "UNREACHABLE",
            apiEndpointStatus = "/api/v1/health · FAILED",
            lastRequestStatus = "FAILED",
            lastError = readable,
            lastCheckedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun runAiConnectionDiagnostics(
        testPrompt: String = DEFAULT_DIAGNOSTIC_PROMPT
    ): AppResult<AiConnectionDiagnosticsState> = withContext(Dispatchers.IO) {
        val prefs = preferencesDataStore.userPreferencesFlow.first()
        val promptToSend = testPrompt.trim().ifBlank { DEFAULT_DIAGNOSTIC_PROMPT }

        _diagnosticsState.value = _diagnosticsState.value.copy(
            isRunningTest = true,
            baseUrl = prefs.backendBaseUrl,
            lastTestPrompt = promptToSend,
            lastRequestStatus = "TESTING PIPELINE...",
            lastError = null
        )

        if (!networkMonitor.checkCurrentConnectivity()) {
            val offlineErr = "Device has no active internet connection."
            val failedState = _diagnosticsState.value.copy(
                isRunningTest = false,
                backendConnection = "OFFLINE",
                apiEndpointStatus = "NETWORK DISCONNECTED",
                lastRequestStatus = "FAILED (OFFLINE)",
                lastError = offlineErr,
                lastCheckedAtMs = System.currentTimeMillis()
            )
            _diagnosticsState.value = failedState
            return@withContext AppResult.Error(offlineErr)
        }

        try {
            // Step 1: Check /api/v1/health
            val health = backendApi.checkHealth()
            // Step 2: Check /api/v1/ai/providers
            val providerInfo: ProviderInfoDto? = runCatching { backendApi.getProvidersInfo() }.getOrNull()

            val activeProvider = providerInfo?.activeProvider ?: health.activeProvider.ifBlank { health.provider }
            val activeModel = providerInfo?.activeModel ?: health.activeModel.ifBlank { health.model }
            val isConfigured = providerInfo?.configured ?: health.configured

            if (!isConfigured) {
                val missingKeyMsg = providerInfo?.configurationNotice?.ifBlank { null }
                    ?: health.configurationNotice.ifBlank {
                        "AI provider '$activeProvider' is not configured on the backend server. Configure GEMINI_API_KEY in server environment variables."
                    }
                val degradedState = _diagnosticsState.value.copy(
                    isRunningTest = false,
                    backendConnection = "CONNECTED (200 OK)",
                    apiEndpointStatus = "/api/v1/health & /api/v1/ai/providers · REACHABLE",
                    providerConfiguration = "${activeProvider.uppercase()} · MISSING SERVER API KEY",
                    providerConfigured = false,
                    configuredProviders = providerInfo?.configuredProviders ?: health.configuredProviders,
                    modelConfiguration = activeModel,
                    fallbackModels = providerInfo?.fallbackModels ?: health.fallbackModels,
                    configurationNotice = missingKeyMsg,
                    lastRequestStatus = "CONFIGURATION REQUIRED (503)",
                    lastError = missingKeyMsg,
                    lastCheckedAtMs = System.currentTimeMillis()
                )
                _diagnosticsState.value = degradedState
                return@withContext AppResult.Error(missingKeyMsg)
            }

            // Step 3: Execute real end-to-end AI chat request
            var chatResponseReply = ""
            var usedModel = activeModel
            var usedProvider = activeProvider
            val roundTripMs = measureTimeMillis {
                val chatRes = backendApi.sendChatMessage(
                    ChatRequestDto(
                        message = promptToSend,
                        history = emptyList(),
                        memories = emptyList(),
                        language = prefs.languageCode,
                        mode = "chat",
                        provider = prefs.aiProvider,
                        model = prefs.aiModel
                    )
                )
                chatResponseReply = chatRes.reply
                usedModel = chatRes.model
                usedProvider = chatRes.provider
            }

            val successState = _diagnosticsState.value.copy(
                isRunningTest = false,
                backendConnection = "CONNECTED (${health.service} v${health.version})",
                apiEndpointStatus = "/api/v1/health, /api/v1/ai/providers, /api/v1/ai/chat · 200 OK",
                baseUrl = prefs.backendBaseUrl,
                providerConfiguration = "${usedProvider.uppercase()} · Server Key Active (Verified)",
                providerConfigured = true,
                configuredProviders = providerInfo?.configuredProviders ?: health.configuredProviders,
                modelConfiguration = usedModel,
                fallbackModels = providerInfo?.fallbackModels ?: health.fallbackModels,
                configurationNotice = providerInfo?.configurationNotice ?: health.configurationNotice,
                lastRequestStatus = "SUCCESS (200 OK)",
                responseTimeMs = roundTripMs,
                lastError = null,
                lastTestPrompt = promptToSend,
                lastTestResponse = chatResponseReply,
                lastCheckedAtMs = System.currentTimeMillis()
            )
            _diagnosticsState.value = successState

            activityLogDao.insertActivity(
                ActivityLogEntity(
                    module = "AI",
                    actionTitle = "AI Connection Test Passed",
                    detail = "$usedProvider/$usedModel (${roundTripMs} ms)"
                )
            )
            AppResult.Success(successState)
        } catch (e: Exception) {
            val readable = extractReadableError(e)
            val errState = _diagnosticsState.value.copy(
                isRunningTest = false,
                backendConnection = if (e is HttpException) "CONNECTED (HTTP ${e.code()})" else "CONNECTION ERROR",
                apiEndpointStatus = "ERROR · $readable",
                lastRequestStatus = "FAILED",
                lastError = readable,
                lastCheckedAtMs = System.currentTimeMillis()
            )
            _diagnosticsState.value = errState
            AppResult.Error(readable, e)
        }
    }

    // -----------------------------------------------------------------------
    // Offline Request Queue Management
    // -----------------------------------------------------------------------
    suspend fun removeQueuedRequest(id: Long) {
        queuedAiRequestDao.deleteQueuedRequest(id)
    }

    suspend fun flushOfflineQueue(): Int {
        if (!networkMonitor.checkCurrentConnectivity()) return 0
        val pending = queuedAiRequestDao.getQueuedRequestsSnapshot()
        var processed = 0
        for (item in pending) {
            queuedAiRequestDao.deleteQueuedRequest(item.id)
            val res = sendMessageWithStreaming(
                conversationId = item.conversationId,
                userText = item.prompt,
                mode = item.mode,
                skipUserInsert = true
            )
            if (res is AppResult.Success) {
                processed++
            }
        }
        return processed
    }

    // -----------------------------------------------------------------------
    // AI Chat Streaming, Regeneration & Vision Analysis
    // -----------------------------------------------------------------------
    suspend fun regenerateLastResponse(
        conversationId: Long,
        onPartialToken: (String) -> Unit = {}
    ): AppResult<ChatMessageEntity> {
        val currentMessages = chatDao.getMessagesSnapshot(conversationId)
        val lastUserMsg = currentMessages.lastOrNull { it.role == "user" }
            ?: return AppResult.Error("No previous user prompt found to regenerate.")

        val lastAssistantMsg = currentMessages.lastOrNull { it.role == "assistant" }
        if (lastAssistantMsg != null && lastAssistantMsg.timestamp >= lastUserMsg.timestamp) {
            chatDao.deleteMessageById(lastAssistantMsg.id)
        }

        return sendMessageWithStreaming(
            conversationId = conversationId,
            userText = lastUserMsg.content,
            mode = lastUserMsg.mode,
            skipUserInsert = true,
            onPartialToken = onPartialToken
        )
    }

    suspend fun sendMessageWithStreaming(
        conversationId: Long,
        userText: String,
        mode: String = "chat",
        skipUserInsert: Boolean = false,
        onPartialToken: (String) -> Unit = {}
    ): AppResult<ChatMessageEntity> {
        val requestStartMs = System.currentTimeMillis()
        // Capture conversation history BEFORE inserting the current user message so it is never duplicated
        val priorHistorySnapshot = chatDao.getMessagesSnapshot(conversationId)
            .filter { !(skipUserInsert && it.role == "user" && it.content == userText) }
            .takeLast(10)
            .map { ChatHistoryItemDto(role = it.role, content = it.content) }

        if (!skipUserInsert) {
            val userMsg = ChatMessageEntity(
                conversationId = conversationId,
                role = "user",
                content = userText,
                mode = mode
            )
            chatDao.insertMessage(userMsg)
            chatDao.updateConversationDraft(conversationId, "")
            activityLogDao.insertActivity(
                ActivityLogEntity(
                    module = "AI",
                    actionTitle = if (mode == "summarize") "Executive Summarization" else "AI Prompt Sent",
                    detail = userText.take(60)
                )
            )
        }

        val prefs = preferencesDataStore.userPreferencesFlow.first()

        // OFFLINE HANDLING: Never pretend an AI response was generated offline!
        // Instead, queue the request in Room for later execution when online.
        if (!networkMonitor.checkCurrentConnectivity()) {
            queuedAiRequestDao.insertQueuedRequest(
                QueuedAiRequestEntity(
                    conversationId = conversationId,
                    prompt = userText,
                    mode = mode
                )
            )
            val queueMessage = if (prefs.languageCode == "fa") {
                "دستگاه آفلاین است. درخواست شما در «صف ارسال آفلاین» ذخیره شد و به محض اتصال به اینترنت به سرور هوش مصنوعی ارسال خواهد شد. (هیچ پاسخ ساختگی در حالت آفلاین تولید نمی‌شود)."
            } else {
                "Device is offline. Your request has been queued in the local Room Offline Queue and will be dispatched when internet connectivity resumes. (No fake offline AI responses are generated)."
            }
            _diagnosticsState.value = _diagnosticsState.value.copy(
                backendConnection = "OFFLINE",
                lastRequestStatus = "QUEUED (OFFLINE)",
                lastError = queueMessage,
                lastCheckedAtMs = System.currentTimeMillis()
            )
            return AppResult.Error(queueMessage)
        }

        val activeMemories = if (prefs.smartMemoryEnabled) {
            smartMemoryDao.getMemoriesSnapshot().take(15).map {
                MemoryContextItemDto(
                    title = it.title,
                    factContent = it.factContent,
                    category = it.category
                )
            }
        } else {
            emptyList()
        }

        val request = ChatRequestDto(
            message = userText,
            history = priorHistorySnapshot,
            memories = activeMemories,
            language = prefs.languageCode,
            mode = mode,
            provider = prefs.aiProvider,
            model = prefs.aiModel
        )

        val placeholderMsg = ChatMessageEntity(
            conversationId = conversationId,
            role = "assistant",
            content = "▋",
            mode = mode
        )
        val assistantMsgId = chatDao.insertMessage(placeholderMsg)

        if (mode == "chat") {
            try {
                val streamedText = withContext(Dispatchers.IO) {
                    val responseBody = backendApi.streamChatMessage(request)
                    val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                    val accumulated = StringBuilder()
                    try {
                        var line: String? = reader.readLine()
                        while (line != null) {
                            currentCoroutineContext().ensureActive()
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data:")) {
                                val payload = trimmed.removePrefix("data:").trim()
                                if (payload.isNotEmpty() && payload != "[DONE]") {
                                    val chunk = runCatching {
                                        json.decodeFromString<StreamChunkDto>(payload)
                                    }.getOrNull()
                                    if (chunk != null) {
                                        if (!chunk.error.isNullOrBlank()) {
                                            throw IllegalStateException(chunk.error)
                                        }
                                        val token = chunk.resolvedText
                                        if (token.isNotEmpty()) {
                                            accumulated.append(token)
                                            val currentText = accumulated.toString()
                                            chatDao.updateMessageContent(assistantMsgId, currentText)
                                            onPartialToken(currentText)
                                        }
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                    } finally {
                        runCatching { reader.close() }
                    }
                    accumulated.toString()
                }

                if (streamedText.isNotBlank()) {
                    val elapsed = System.currentTimeMillis() - requestStartMs
                    chatDao.updateConversationMeta(
                        id = conversationId,
                        title = userText.take(36),
                        updatedAt = System.currentTimeMillis()
                    )
                    _diagnosticsState.value = _diagnosticsState.value.copy(
                        backendConnection = "CONNECTED",
                        apiEndpointStatus = "/api/v1/ai/chat/stream · 200 OK",
                        baseUrl = prefs.backendBaseUrl,
                        providerConfiguration = "${prefs.aiProvider.uppercase()} · Active",
                        providerConfigured = true,
                        modelConfiguration = prefs.aiModel,
                        lastRequestStatus = "SUCCESS (SSE STREAM)",
                        responseTimeMs = elapsed,
                        lastError = null,
                        lastCheckedAtMs = System.currentTimeMillis()
                    )
                    return AppResult.Success(
                        placeholderMsg.copy(id = assistantMsgId, content = streamedText)
                    )
                }
            } catch (ce: CancellationException) {
                val partialSnapshot = chatDao.getMessagesSnapshot(conversationId)
                    .firstOrNull { it.id == assistantMsgId }?.content.orEmpty()
                val stoppedContent = if (partialSnapshot == "▋" || partialSnapshot.isBlank()) {
                    "[Generation stopped by user]"
                } else {
                    "$partialSnapshot\n\n_[Generation stopped by user]_"
                }
                chatDao.updateMessageContent(assistantMsgId, stoppedContent)
                throw ce
            } catch (_: Exception) {
                // Fall through to standard REST endpoint (/api/v1/ai/chat) for deterministic completion or error reporting
            }
        }

        return try {
            val response = if (mode == "summarize") {
                backendApi.summarizeText(request)
            } else {
                backendApi.sendChatMessage(request)
            }
            val elapsed = System.currentTimeMillis() - requestStartMs

            chatDao.updateMessageContent(assistantMsgId, response.reply)
            chatDao.updateConversationMeta(
                id = conversationId,
                title = userText.take(36),
                updatedAt = System.currentTimeMillis()
            )
            _diagnosticsState.value = _diagnosticsState.value.copy(
                backendConnection = "CONNECTED",
                apiEndpointStatus = "/api/v1/ai/chat · 200 OK",
                baseUrl = prefs.backendBaseUrl,
                providerConfiguration = "${response.provider.uppercase()} · Active",
                providerConfigured = true,
                modelConfiguration = response.model,
                lastRequestStatus = "SUCCESS (200 OK)",
                responseTimeMs = if (response.latencyMs > 0) response.latencyMs else elapsed,
                lastError = null,
                lastCheckedAtMs = System.currentTimeMillis()
            )
            AppResult.Success(
                placeholderMsg.copy(id = assistantMsgId, content = response.reply)
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            chatDao.deleteMessageById(assistantMsgId)
            val readable = extractReadableError(e)
            _diagnosticsState.value = _diagnosticsState.value.copy(
                backendConnection = if (e is HttpException) "CONNECTED (HTTP ${e.code()})" else "UNREACHABLE",
                apiEndpointStatus = "/api/v1/ai/chat · FAILED",
                baseUrl = prefs.backendBaseUrl,
                lastRequestStatus = "FAILED",
                lastError = readable,
                lastCheckedAtMs = System.currentTimeMillis()
            )
            AppResult.Error(readable, e)
        }
    }

    suspend fun sendMessage(
        conversationId: Long,
        userText: String,
        mode: String = "chat"
    ): AppResult<ChatMessageEntity> = sendMessageWithStreaming(conversationId, userText, mode)

    suspend fun analyzeImage(
        conversationId: Long,
        prompt: String,
        imageBase64: String,
        mimeType: String,
        onProgress: (Float) -> Unit = {}
    ): AppResult<ChatMessageEntity> {
        val normalizedMime = mimeType.trim().lowercase()
        if (normalizedMime !in SUPPORTED_IMAGE_MIMES) {
            return AppResult.Error(
                "Unsupported image format '$mimeType'. Supported formats: JPEG, PNG, WEBP."
            )
        }

        val decodedBytes = runCatching { Base64.decode(imageBase64, Base64.DEFAULT) }.getOrNull()
            ?: return AppResult.Error("Invalid base64 image encoding.")

        if (decodedBytes.isEmpty()) {
            return AppResult.Error("Selected image file is empty.")
        }
        if (decodedBytes.size > MAX_IMAGE_BYTES) {
            return AppResult.Error(
                "Image size (${decodedBytes.size / 1024} KB) exceeds maximum limit of 4096 KB."
            )
        }

        val prefs = preferencesDataStore.userPreferencesFlow.first()
        if (!networkMonitor.checkCurrentConnectivity()) {
            return AppResult.Error(
                if (prefs.languageCode == "fa") {
                    "تحلیل تصویر نیازمند اتصال اینترنت و سرور هوش مصنوعی است."
                } else {
                    "AI Vision analysis requires an active internet connection to the backend."
                }
            )
        }

        onProgress(0.25f)
        chatDao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                role = "user",
                content = "[Vision Attachment · ${decodedBytes.size / 1024} KB] $prompt",
                mode = "vision"
            )
        )
        activityLogDao.insertActivity(
            ActivityLogEntity(
                module = "AI",
                actionTitle = "Multimodal Vision Upload",
                detail = "${normalizedMime.uppercase()} (${decodedBytes.size / 1024} KB)"
            )
        )

        return try {
            onProgress(0.65f)
            val response = backendApi.analyzeImage(
                ImageAnalysisRequestDto(
                    prompt = prompt,
                    imageBase64 = imageBase64,
                    mimeType = normalizedMime,
                    language = prefs.languageCode,
                    provider = prefs.aiProvider,
                    model = prefs.aiModel
                )
            )
            onProgress(1.0f)
            val assistantMsg = ChatMessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = response.reply,
                mode = "vision"
            )
            val id = chatDao.insertMessage(assistantMsg)
            AppResult.Success(assistantMsg.copy(id = id))
        } catch (e: Exception) {
            onProgress(0f)
            val readable = extractReadableError(e)
            AppResult.Error(readable, e)
        }
    }
}

typealias QueuedAiRequestEntityDaoAlias = QueuedAiRequestDao

@Serializable
data class BackupNoteItem(
    val title: String,
    val content: String,
    val tag: String,
    val isPinned: Boolean
)

@Serializable
data class BackupTaskItem(
    val title: String,
    val category: String,
    val priority: Int,
    val isCompleted: Boolean,
    val dueDateEpochDay: Long
)

@Serializable
data class BackupMemoryItem(
    val title: String,
    val factContent: String,
    val category: String
)

@Serializable
data class NexoraBackupPayload(
    val exportedAt: Long,
    val appVersion: String = "3.0.0",
    val notesCount: Int,
    val tasksCount: Int,
    val conversationsCount: Int,
    val memoriesCount: Int = 0,
    val notes: List<BackupNoteItem> = emptyList(),
    val tasks: List<BackupTaskItem> = emptyList(),
    val memories: List<BackupMemoryItem> = emptyList()
)

@Singleton
class BackupRestoreManager @Inject constructor(
    private val chatDao: ChatDao,
    private val smartMemoryDao: SmartMemoryDao,
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val activityLogDao: ActivityLogDao,
    private val preferencesDataStore: NexoraPreferencesDataStore,
    private val json: Json
) {
    suspend fun exportJsonBackup(): String {
        val notes = noteDao.getAllNotesSnapshot()
        val tasks = taskDao.getAllTasksSnapshot()
        val conversations = chatDao.getAllConversationsSnapshot()
        val memories = smartMemoryDao.getMemoriesSnapshot()
        val now = System.currentTimeMillis()
        preferencesDataStore.recordBackupTimestamp(now)

        activityLogDao.insertActivity(
            ActivityLogEntity(
                module = "SYSTEM",
                actionTitle = "Room Database Backup Exported",
                detail = "${notes.size} notes, ${tasks.size} tasks, ${memories.size} memories"
            )
        )

        val payload = NexoraBackupPayload(
            exportedAt = now,
            notesCount = notes.size,
            tasksCount = tasks.size,
            conversationsCount = conversations.size,
            memoriesCount = memories.size,
            notes = notes.map {
                BackupNoteItem(
                    title = it.title,
                    content = it.content,
                    tag = it.tag,
                    isPinned = it.isPinned
                )
            },
            tasks = tasks.map {
                BackupTaskItem(
                    title = it.title,
                    category = it.category,
                    priority = it.priority,
                    isCompleted = it.isCompleted,
                    dueDateEpochDay = it.dueDateEpochDay
                )
            },
            memories = memories.map {
                BackupMemoryItem(
                    title = it.title,
                    factContent = it.factContent,
                    category = it.category
                )
            }
        )
        return json.encodeToString(payload)
    }

    suspend fun restoreFromBackupJson(rawJson: String): Result<Int> = runCatching {
        val payload = json.decodeFromString<NexoraBackupPayload>(rawJson)
        var restoredItems = 0
        payload.notes.forEach { item ->
            noteDao.upsertNote(
                NoteEntity(
                    title = item.title,
                    content = item.content,
                    tag = item.tag,
                    isPinned = item.isPinned
                )
            )
            restoredItems++
        }
        payload.tasks.forEach { item ->
            taskDao.upsertTask(
                TaskEntity(
                    title = item.title,
                    category = item.category,
                    priority = item.priority,
                    isCompleted = item.isCompleted,
                    dueDateEpochDay = item.dueDateEpochDay
                )
            )
            restoredItems++
        }
        payload.memories.forEach { item ->
            smartMemoryDao.insertMemory(
                SmartMemoryEntity(
                    title = item.title,
                    factContent = item.factContent,
                    category = item.category
                )
            )
            restoredItems++
        }
        activityLogDao.insertActivity(
            ActivityLogEntity(
                module = "SYSTEM",
                actionTitle = "Room Snapshot Restored",
                detail = "Restored $restoredItems entities"
            )
        )
        restoredItems
    }
}

@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong("task_id", 1L)
        val title = inputData.getString("task_title") ?: "Scheduled Task"
        val category = inputData.getString("task_category") ?: "General"

        NotificationHelper.showTaskReminderNotification(
            context = applicationContext,
            taskId = taskId,
            title = title,
            category = category
        )
        return Result.success()
    }

    companion object {
        fun scheduleReminder(
            context: Context,
            task: TaskEntity,
            delayMinutes: Long = 15L
        ) {
            val data = workDataOf(
                "task_id" to task.id,
                "task_title" to task.title,
                "task_category" to task.category
            )
            val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "nexora_task_reminder_${task.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
