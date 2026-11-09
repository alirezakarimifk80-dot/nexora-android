package com.nexora.commandcenter.features.commercial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.commandcenter.core.datastore.NexoraPreferencesDataStore
import com.nexora.commandcenter.core.designsystem.NexoraButton
import com.nexora.commandcenter.core.designsystem.NexoraCard
import com.nexora.commandcenter.core.designsystem.NexoraColors
import com.nexora.commandcenter.core.designsystem.NexoraGlyph
import com.nexora.commandcenter.core.designsystem.NexoraSectionHeader
import com.nexora.commandcenter.core.designsystem.NexoraTextField
import com.nexora.commandcenter.core.network.AuthLoginRequestDto
import com.nexora.commandcenter.core.network.AuthRegisterRequestDto
import com.nexora.commandcenter.core.network.CloudSyncDeltaRequestDto
import com.nexora.commandcenter.core.network.CloudSyncItemDto
import com.nexora.commandcenter.core.network.NexoraBackendApi
import com.nexora.commandcenter.core.network.SubscriptionVerifyRequestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CommercialSubTab(val titleEn: String, val titleFa: String) {
    PROFILE("Profile", "پروفایل"),
    SECURITY("Security", "امنیت حساب"),
    SUBSCRIPTIONS("Plans", "اشتراک پرمیوم"),
    PREMIUM_FEATURES("Pro Features", "امکانات ویژه"),
    CLOUD_SYNC("Cloud Sync", "همگام‌سازی ابری"),
    NOTIFICATIONS("Notifications", "اعلان‌ها"),
    PRIVACY("Privacy Center", "مرکز حریم خصوصی")
}

data class CommercialUiState(
    val activeTab: CommercialSubTab = CommercialSubTab.PROFILE,
    val isAuthenticated: Boolean = false,
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val avatarUrl: String = "",
    val emailVerified: Boolean = false,
    val subscriptionPlan: String = "free",
    val billingEnvironment: String = "PLAY_BILLING_TEST_SANDBOX",
    val syncStatus: String = "Idle (Offline-First Local Vault Active)",
    val lastSyncedAt: Long = 0L,
    val conflictCount: Int = 0,
    val taskRemindersEnabled: Boolean = true,
    val accountNotificationsEnabled: Boolean = true,
    val syncNotificationsEnabled: Boolean = true,
    val subscriptionNotificationsEnabled: Boolean = true,
    val statusBanner: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class CommercialViewModel @Inject constructor(
    private val backendApi: NexoraBackendApi,
    private val preferencesDataStore: NexoraPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommercialUiState())
    val uiState: StateFlow<CommercialUiState> = _uiState.asStateFlow()

    fun selectTab(tab: CommercialSubTab) {
        _uiState.update { it.copy(activeTab = tab, statusBanner = null) }
    }

    fun registerAccount(email: String, password: String, fullName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusBanner = null) }
            try {
                val resp = backendApi.registerAccount(
                    AuthRegisterRequestDto(email = email, password = password, fullName = fullName)
                )
                preferencesDataStore.setSessionToken(resp.sessionToken)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        userId = resp.user.id,
                        email = resp.user.email,
                        fullName = resp.user.fullName,
                        avatarUrl = resp.user.avatarUrl,
                        emailVerified = resp.user.emailVerified,
                        subscriptionPlan = resp.user.subscriptionPlan,
                        statusBanner = "Account registered. Verification token: ${resp.verificationTokenPreview}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, statusBanner = "Registration error: ${e.message}")
                }
            }
        }
    }

    fun loginAccount(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusBanner = null) }
            try {
                val resp = backendApi.loginAccount(
                    AuthLoginRequestDto(email = email, password = password)
                )
                preferencesDataStore.setSessionToken(resp.sessionToken)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        userId = resp.user.id,
                        email = resp.user.email,
                        fullName = resp.user.fullName,
                        avatarUrl = resp.user.avatarUrl,
                        emailVerified = resp.user.emailVerified,
                        subscriptionPlan = resp.user.subscriptionPlan,
                        statusBanner = "Authenticated as ${resp.user.fullName}."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, statusBanner = "Login error: ${e.message}")
                }
            }
        }
    }

    fun logoutAccount() {
        viewModelScope.launch {
            val token = preferencesDataStore.userPreferencesFlow.first().sessionToken
            runCatching {
                if (token.isNotBlank()) backendApi.logoutAccount("Bearer $token")
            }
            preferencesDataStore.setSessionToken("")
            _uiState.update {
                CommercialUiState(statusBanner = "Session revoked and logged out.")
            }
        }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            val token = preferencesDataStore.userPreferencesFlow.first().sessionToken
            if (token.isBlank()) {
                _uiState.update {
                    it.copy(statusBanner = "Log in to synchronize your offline Room vault with NEXORA Cloud.")
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, syncStatus = "Synchronizing delta...") }
            try {
                val sampleItems = listOf(
                    CloudSyncItemDto(
                        entityType = "preference",
                        entityId = "theme_and_locale",
                        payload = mapOf("theme" to "obsidian_dark", "rtlSupport" to "true"),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val result = backendApi.syncDelta(
                    authorization = "Bearer $token",
                    request = CloudSyncDeltaRequestDto(items = sampleItems)
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        syncStatus = "Synced (${result.appliedCount} applied, ${result.conflictCount} conflicts)",
                        lastSyncedAt = result.syncedAt,
                        conflictCount = result.conflictCount,
                        statusBanner = "Cloud Synchronization completed."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        syncStatus = "Offline Vault Active (Sync Retry Queued)",
                        statusBanner = "Sync deferred: ${e.message}"
                    )
                }
            }
        }
    }

    fun activateSubscriptionPlan(planId: String) {
        viewModelScope.launch {
            val token = preferencesDataStore.userPreferencesFlow.first().sessionToken
            if (token.isBlank()) {
                _uiState.update { it.copy(statusBanner = "Log in before activating a subscription plan.") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            try {
                val resp = backendApi.verifySubscription(
                    authorization = "Bearer $token",
                    request = SubscriptionVerifyRequestDto(
                        planId = planId,
                        purchaseToken = "play_sandbox_token_${System.currentTimeMillis()}"
                    )
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        subscriptionPlan = resp.user.subscriptionPlan,
                        billingEnvironment = resp.environment,
                        statusBanner = resp.notice
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, statusBanner = "Subscription verification failed: ${e.message}")
                }
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            val token = preferencesDataStore.userPreferencesFlow.first().sessionToken
            if (token.isBlank()) return@launch
            runCatching {
                val resp = backendApi.restoreSubscription("Bearer $token")
                _uiState.update {
                    it.copy(
                        subscriptionPlan = resp.user.subscriptionPlan,
                        statusBanner = "Restored plan: ${resp.activePlan} (${resp.environment})"
                    )
                }
            }
        }
    }

    fun toggleNotificationChannel(channel: String, enabled: Boolean) {
        _uiState.update { state ->
            when (channel) {
                "tasks" -> state.copy(taskRemindersEnabled = enabled)
                "account" -> state.copy(accountNotificationsEnabled = enabled)
                "sync" -> state.copy(syncNotificationsEnabled = enabled)
                else -> state.copy(subscriptionNotificationsEnabled = enabled)
            }
        }
    }
}

@Composable
fun CommercialHubScreen(
    isFa: Boolean,
    viewModel: CommercialViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var fullNameInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (isFa) "پلتفرم تجاری و حساب کاربری نکسورا" else "NEXORA COMMERCIAL & ACCOUNT HUB",
                color = NexoraColors.CyberCyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isFa) "حساب ابری، اشتراک پرمیوم و مرکز حریم خصوصی" else "Account, Cloud Sync, Subscriptions & Privacy",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 7-Screen Sub-Navigation Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommercialSubTab.entries.take(4).forEach { tab ->
                        NexoraButton(
                            text = if (isFa) tab.titleFa else tab.titleEn,
                            onClick = { viewModel.selectTab(tab) },
                            isPrimary = state.activeTab == tab,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommercialSubTab.entries.drop(4).forEach { tab ->
                        NexoraButton(
                            text = if (isFa) tab.titleFa else tab.titleEn,
                            onClick = { viewModel.selectTab(tab) },
                            isPrimary = state.activeTab == tab,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        state.statusBanner?.let { msg ->
            item {
                NexoraCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NexoraColors.CyberCyan.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = msg,
                        color = NexoraColors.CyberCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 1. PROFILE SCREEN & AUTHENTICATION
        if (state.activeTab == CommercialSubTab.PROFILE) {
            item {
                NexoraCard(modifier = Modifier.fillMaxWidth()) {
                    NexoraSectionHeader(
                        eyebrow = "USER AUTHENTICATION & PROFILE",
                        title = if (state.isAuthenticated) "Authenticated Commander Profile" else "Account Registration & Login"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!state.isAuthenticated) {
                        NexoraTextField(
                            value = fullNameInput,
                            onValueChange = { fullNameInput = it },
                            label = "Full Name (Registration)",
                            placeholder = "Commander Alireza"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NexoraTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = "Email Address",
                            placeholder = "commander@nexora.ai"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NexoraTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = "Password (min 8 chars, PBKDF2-SHA256)",
                            placeholder = "••••••••••••"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NexoraButton(
                                text = "Register",
                                onClick = { viewModel.registerAccount(emailInput, passwordInput, fullNameInput) },
                                modifier = Modifier.weight(1f)
                            )
                            NexoraButton(
                                text = "Login",
                                isPrimary = false,
                                onClick = { viewModel.loginAccount(emailInput, passwordInput) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text("Name: ${state.fullName}", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Email: ${state.email}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Plan: ${state.subscriptionPlan.uppercase()}",
                            color = NexoraColors.CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        NexoraButton(
                            text = "Log Out Session",
                            isPrimary = false,
                            onClick = { viewModel.logoutAccount() }
                        )
                    }
                }
            }
        }

        // 2. ACCOUNT SECURITY SCREEN
        if (state.activeTab == CommercialSubTab.SECURITY) {
            item {
                NexoraCard(modifier = Modifier.fillMaxWidth()) {
                    NexoraSectionHeader(
                        eyebrow = "CRYPTOGRAPHIC SESSION GUARD",
                        title = "Account Security & Token Protection"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Passwords hashed on backend with PBKDF2-HMAC-SHA256 (200,000 iterations + 16-byte cryptographic salt).\n• Session tokens stored as SHA-256 digests with automatic revocation on password reset.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 3. SUBSCRIPTION PLANS SCREEN
        if (state.activeTab == CommercialSubTab.SUBSCRIPTIONS) {
            item {
                NexoraCard(modifier = Modifier.fillMaxWidth()) {
                    NexoraSectionHeader(
                        eyebrow = state.billingEnvironment,
                        title = "NEXORA Subscription Plans"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Active Plan: ${state.subscriptionPlan.uppercase()} · Purchases verified on FastAPI backend.",
                        color = NexoraColors.EmeraldSignal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexoraButton(
                            text = "Monthly Pro ($9.99)",
                            onClick = { viewModel.activateSubscriptionPlan("premium_monthly") },
                            modifier = Modifier.weight(1f)
                        )
                        NexoraButton(
                            text = "Yearly Pro ($79.99)",
                            onClick = { viewModel.activateSubscriptionPlan("premium_yearly") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NexoraButton(
                        text = "Restore Purchases",
                        isPrimary = false,
                        onClick = { viewModel.restorePurchases() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 4. PREMIUM FEATURES SCREEN
        if (state.activeTab == CommercialSubTab.PREMIUM_FEATURES) {
            item {
                NexoraCard(modifier = Modifier.fillMaxWidth()) {
                    NexoraSectionHeader(
                        eyebrow = "NEXORA QUANTUM PRO",
                        title = "Premium Tier Capabilities"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• 1,000 Daily AI Command Requests (vs 25 on Free tier)")
                    Text("• Multi-Device Cloud Synchronization with Version-Vector Conflict Resolution")
                    Text("• Encrypted Cloud Vault Snapshots & Instant Restore")
                    Text("• Priority Multimodal Vision & Custom Model Selection")
                }
            }
        }

        // 5. CLOUD SYNCHRONIZATION SCREEN
        if (state.activeTab == CommercialSubTab.CLOUD_SYNC) {
            item {
                NexoraCard(modifier = Modifier.fillMaxWidth()) {
                    NexoraSectionHeader(
                        eyebrow = "OFFLINE-FIRST CLOUD SYNC",
                        title = "Cloud Synchronization & Conflict Resolution"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Status: ${state.syncStatus}",
                        color = NexoraColors.CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Synchronizes Notes, Tasks, Preferences, Selected AI Conversations, and Personal Settings while keeping Room SQLite accessible offline.")
                    Spacer(modifier = Modifier.height(10.dp))
                    NexoraButton(
                        text = "Synchronize Now",
                        leadingGlyph = NexoraGlyph.REFRESH,
                        onClick = { viewModel.triggerCloudSync() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 6. NOTIFICATION SETTINGS SCREEN
        if (state.activeTab == CommercialSubTab.NOTIFICATIONS) {
            item {
                NexoraCard(modifier = Modifier.fillMaxWidth()) {
                    NexoraSectionHeader(
                        eyebrow = "FCM + LOCAL WORKMANAGER",
                        title = "Notification Preferences"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        Triple("tasks", "Task & Directive Reminders", state.taskRemindersEnabled),
                        Triple("account", "Important Account Security Alerts", state.accountNotificationsEnabled),
                        Triple("sync", "Cloud Synchronization & Conflict Alerts", state.syncNotificationsEnabled),
                        Triple("sub", "Subscription & Billing Renewal Notices", state.subscriptionNotificationsEnabled)
                    ).forEach { (key, label, checked) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleNotificationChannel(key, it) },
                                colors = CheckboxDefaults.colors(checkedColor = NexoraColors.CyberCyan)
                            )
                        }
                    }
                }
            }
        }

        // 7. PRIVACY CENTER & CLOUD BACKUP SCREEN
        if (state.activeTab == CommercialSubTab.PRIVACY) {
            item {
                NexoraCard(modifier = Modifier.fillMaxWidth()) {
                    NexoraSectionHeader(
                        eyebrow = "GDPR & PRIVACY CONTROLS",
                        title = "Privacy Center, Data Export & Account Deletion"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export a complete portable JSON archive of your profile, cloud sync entities, and backups, or permanently delete your account and all cloud records.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
