package com.nexora.commandcenter.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.commandcenter.BuildConfig
import com.nexora.commandcenter.core.common.LocaleHelper
import com.nexora.commandcenter.core.common.NotificationHelper
import com.nexora.commandcenter.core.database.ActivityLogDao
import com.nexora.commandcenter.core.datastore.NexoraPreferencesDataStore
import com.nexora.commandcenter.core.designsystem.NexoraButton
import com.nexora.commandcenter.core.designsystem.NexoraCard
import com.nexora.commandcenter.core.designsystem.NexoraGlyph
import com.nexora.commandcenter.core.designsystem.NexoraSectionHeader
import com.nexora.commandcenter.core.designsystem.NexoraSuccessBanner
import com.nexora.commandcenter.core.designsystem.NexoraTextField
import com.nexora.commandcenter.data.AiRepository
import com.nexora.commandcenter.data.BackupRestoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: NexoraPreferencesDataStore,
    private val backupRestoreManager: BackupRestoreManager,
    private val aiRepository: AiRepository,
    private val activityLogDao: ActivityLogDao
) : ViewModel() {

    val prefs: StateFlow<NexoraPreferencesDataStore.UserPreferences> =
        preferencesDataStore.userPreferencesFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            NexoraPreferencesDataStore.UserPreferences()
        )

    private val _backupPayloadJson = MutableStateFlow("")
    val backupPayloadJson: StateFlow<String> = _backupPayloadJson.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            preferencesDataStore.setLanguageCode(code)
            LocaleHelper.applyLanguage(code)
            _statusMessage.value = if (code == "fa") "زبان فارسی (RTL) فعال شد." else "English (LTR) activated."
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setDarkMode(enabled)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setNotificationsEnabled(enabled)
        }
    }

    fun setPrivacyRedactIp(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setPrivacyRedactIp(enabled)
        }
    }

    fun setSmartMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setSmartMemoryEnabled(enabled)
            _statusMessage.value = if (enabled) "Smart Memory enabled." else "Smart Memory disabled."
        }
    }

    fun setProviderAndModel(provider: String, model: String) {
        viewModelScope.launch {
            preferencesDataStore.setAiProviderAndModel(provider, model)
            _statusMessage.value = "AI Provider updated: $provider ($model)"
        }
    }

    fun updateBackendUrl(url: String) {
        viewModelScope.launch {
            preferencesDataStore.setBackendBaseUrl(url)
            _statusMessage.value = "Backend endpoint saved to DataStore."
        }
    }

    fun testBackendHealth() {
        viewModelScope.launch {
            aiRepository.checkBackendHealth()
                .onSuccess { health ->
                    _statusMessage.value =
                        "Backend Health OK: ${health.service} v${health.version} (${health.provider}/${health.model})"
                }
                .onFailure { err ->
                    _statusMessage.value = "Backend Health Check Failed: ${err.message}"
                }
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            val json = backupRestoreManager.exportJsonBackup()
            _backupPayloadJson.value = json
            _statusMessage.value = "Full Room database JSON snapshot exported."
        }
    }

    fun restoreBackup(rawJson: String) {
        viewModelScope.launch {
            backupRestoreManager.restoreFromBackupJson(rawJson)
                .onSuccess { count ->
                    _statusMessage.value = "Restored $count entities into local Room database."
                }
                .onFailure { err ->
                    _statusMessage.value = "Restore error: ${err.message}"
                }
        }
    }

    fun clearAiAndTelemetryLogs() {
        viewModelScope.launch {
            aiRepository.clearAllConversations()
            aiRepository.clearAllMemories()
            activityLogDao.clearActivities()
            _statusMessage.value = "Cleared local AI history, Smart Memories & activity telemetry."
        }
    }
}

@Composable
fun SettingsScreen(
    isFa: Boolean,
    onOpenCommercialHub: () -> Unit = {},
    onOpenAiConnectionTest: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefs by viewModel.prefs.collectAsState()
    val backupJson by viewModel.backupPayloadJson.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    var backendUrlInput by remember(prefs.backendBaseUrl) { mutableStateOf(prefs.backendBaseUrl) }
    var modelInput by remember(prefs.aiModel) { mutableStateOf(prefs.aiModel) }
    var restoreInput by remember(backupJson) { mutableStateOf(backupJson) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            NexoraSuccessBanner(
                message = statusMessage,
                onDismiss = { viewModel.clearStatusMessage() }
            )
        }

        // 0. Phase 4 Commercial Account, Cloud Sync, Subscriptions & Privacy Hub
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "NEXORA COMMERCIAL PLATFORM (PHASE 4)",
                    title = if (isFa) "حساب کاربری، همگام‌سازی ابری و اشتراک پرمیوم" else "Account, Cloud Sync, Subscriptions & Privacy Center"
                )
                Spacer(modifier = Modifier.height(10.dp))
                NexoraButton(
                    text = if (isFa) "ورود به مرکز حساب و اشتراک پرمیوم" else "Open Account, Cloud Sync & Pro Plans",
                    leadingGlyph = NexoraGlyph.SHIELD,
                    onClick = onOpenCommercialHub,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 1. Language & RTL/LTR Selection
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "LOCALIZATION & DIRECTIONALITY",
                    title = if (isFa) "زبان و جهت رابط کاربری (RTL / LTR)" else "Language & Layout Direction (RTL / LTR)"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = prefs.languageCode == "en",
                        onClick = { viewModel.setLanguage("en") },
                        label = { Text("English (LTR)") }
                    )
                    FilterChip(
                        selected = prefs.languageCode == "fa",
                        onClick = { viewModel.setLanguage("fa") },
                        label = { Text("فارسی (Persian RTL)") }
                    )
                }
            }
        }

        // 2. Theme, Notifications, Smart Memory & Privacy Controls
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "APPEARANCE, MEMORY & PRIVACY",
                    title = if (isFa) "پوسته، حافظه هوشمند و حریم خصوصی" else "Theme, Smart Memory & Privacy Controls"
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingToggleRow(
                    title = if (isFa) "پوسته تاریک آینده‌نگر (Obsidian Dark)" else "Obsidian & Graphite Dark Theme",
                    checked = prefs.isDarkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
                SettingToggleRow(
                    title = if (isFa) "حافظه هوشمند با کنترل کاربر (Smart Memory)" else "User-Controlled Smart Memory Context",
                    checked = prefs.smartMemoryEnabled,
                    onCheckedChange = viewModel::setSmartMemoryEnabled
                )
                SettingToggleRow(
                    title = if (isFa) "اعلان‌های هوشمند WorkManager" else "WorkManager Task Reminder Notifications",
                    checked = prefs.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifications
                )
                SettingToggleRow(
                    title = if (isFa) "حفظ حریم خصوصی (پوشاندن آی‌پی در شبکه)" else "Privacy Mode (Redact IPv4/IPv6 in Network Center)",
                    checked = prefs.privacyRedactIp,
                    onCheckedChange = viewModel::setPrivacyRedactIp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            NotificationHelper.showTaskReminderNotification(
                                context = context,
                                taskId = 999L,
                                title = if (isFa) "تست اعلان نکسورا" else "NEXORA Diagnostic Notification",
                                category = "System Check"
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isFa) "تست اعلان سیستم" else "Send Test Notification")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearAiAndTelemetryLogs() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isFa) "پاکسازی تاریخچه و حافظه" else "Purge AI & Memory")
                    }
                }
            }
        }

        // 3. Cloudflare Workers AI, Model & Serverless Worker Endpoint Configuration
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "CLOUDFLARE WORKERS AI & D1 SERVERLESS INFRASTRUCTURE",
                    title = if (isFa) "تنظیمات موتور Cloudflare Workers AI و آدرس Worker" else "Cloudflare Workers AI Model & Worker URL"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Llama 3.3 70B" to "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                        "Gemma 3 12B" to "@cf/google/gemma-3-12b-it",
                        "Llama 3.1 8B" to "@cf/meta/llama-3.1-8b-instruct"
                    ).forEach { (labelText, cfModelId) ->
                        FilterChip(
                            selected = modelInput == cfModelId,
                            onClick = {
                                modelInput = cfModelId
                                viewModel.setProviderAndModel("cloudflare-workers-ai", cfModelId)
                            },
                            label = { Text(labelText) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                NexoraTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = if (isFa) "شناسه مدل Workers AI" else "Workers AI Model Identifier",
                    placeholder = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
                )
                Spacer(modifier = Modifier.height(8.dp))
                NexoraTextField(
                    value = backendUrlInput,
                    onValueChange = { backendUrlInput = it },
                    label = "Cloudflare Worker Base URL (HTTPS)",
                    placeholder = "https://nexora-ai-worker.workers.dev/"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexoraButton(
                        text = if (isFa) "ذخیره تنظیمات کلادفلر" else "Save Worker & Model",
                        onClick = {
                            viewModel.setProviderAndModel("cloudflare-workers-ai", modelInput)
                            viewModel.updateBackendUrl(backendUrlInput)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NexoraButton(
                        text = if (isFa) "تست سلامت Worker" else "Check /api/health",
                        onClick = { viewModel.testBackendHealth() },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                NexoraButton(
                    text = if (isFa) "باز کردن صفحه تست اتصال هوش مصنوعی" else "Open AI Connection Test Diagnostics",
                    leadingGlyph = NexoraGlyph.ORB,
                    onClick = onOpenAiConnectionTest,
                    modifier = Modifier.fillMaxWidth(),
                    isPrimary = false
                )
            }
        }

        // 4. Backup & Restore Local Room Database
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "LOCAL VAULT SNAPSHOT",
                    title = if (isFa) "پشتیبان‌گیری و بازیابی پایگاه داده Room" else "Backup & Restore Local Room Database",
                    actionLabel = if (backupJson.isNotBlank()) "Copy JSON" else null,
                    onAction = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("NEXORA Backup JSON", backupJson))
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexoraButton(
                        text = if (isFa) "خروجی JSON" else "Export Snapshot",
                        leadingGlyph = NexoraGlyph.SHIELD,
                        onClick = { viewModel.exportBackup() },
                        modifier = Modifier.weight(1f)
                    )
                    NexoraButton(
                        text = if (isFa) "بازیابی اطلاعات" else "Restore Snapshot",
                        onClick = {
                            if (restoreInput.isNotBlank()) {
                                viewModel.restoreBackup(restoreInput)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                NexoraTextField(
                    value = restoreInput,
                    onValueChange = { restoreInput = it },
                    label = if (isFa) "داده‌های پشتیبان JSON" else "Backup JSON Payload (Paste to Restore)",
                    placeholder = if (isFa) "ابتدا خروجی بگیرید یا JSON را اینجا جایگذاری کنید..." else "Click Export Snapshot or paste backup JSON here...",
                    minLines = 3,
                    maxLines = 6
                )
            }
        }

        // 5. About Application & Version Info
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = "BUILD & ARCHITECTURE METADATA",
                    title = if (isFa) "درباره نکسورا (NEXORA)" else "About NEXORA Command Center"
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Version: ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})")
                Text("Stack: Kotlin 2.0.21 · Jetpack Compose · Material 3 · MVVM Clean Architecture · Hilt DI · Room SQLite (v3) · Preferences DataStore · Retrofit + OkHttp SSE · CameraX + MLKit + ZXing · WorkManager")
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
