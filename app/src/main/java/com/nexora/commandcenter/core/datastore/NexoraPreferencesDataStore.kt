package com.nexora.commandcenter.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexora.commandcenter.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexora_settings")

@Singleton
class NexoraPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class UserPreferences(
        val languageCode: String = "en",
        val isDarkMode: Boolean = true,
        val notificationsEnabled: Boolean = true,
        val privacyRedactIp: Boolean = false,
        val biometricLockEnabled: Boolean = false,
        val smartMemoryEnabled: Boolean = true,
        val aiProvider: String = "gemini",
        val aiModel: String = "gemini-3.1-flash-lite",
        val backendBaseUrl: String = BuildConfig.NEXORA_BACKEND_BASE_URL,
        val sessionToken: String = "",
        val lastBackupTimestamp: Long = 0L
    )

    private object Keys {
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PRIVACY_REDACT_IP = booleanPreferencesKey("privacy_redact_ip")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val SMART_MEMORY_ENABLED = booleanPreferencesKey("smart_memory_enabled")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val storedModel = prefs[Keys.AI_MODEL] ?: "gemini-3.1-flash-lite"
        val normalizedModel = if (storedModel == "gemini-2.5-flash" || storedModel.isBlank()) {
            "gemini-3.1-flash-lite"
        } else {
            storedModel
        }
        UserPreferences(
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            privacyRedactIp = prefs[Keys.PRIVACY_REDACT_IP] ?: false,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            smartMemoryEnabled = prefs[Keys.SMART_MEMORY_ENABLED] ?: true,
            aiProvider = prefs[Keys.AI_PROVIDER] ?: "gemini",
            aiModel = normalizedModel,
            backendBaseUrl = prefs[Keys.BACKEND_BASE_URL] ?: BuildConfig.NEXORA_BACKEND_BASE_URL,
            sessionToken = prefs[Keys.SESSION_TOKEN] ?: "",
            lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TIMESTAMP] ?: 0L
        )
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setPrivacyRedactIp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY_REDACT_IP] = enabled }
    }

    suspend fun setSmartMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMART_MEMORY_ENABLED] = enabled }
    }

    suspend fun setAiProviderAndModel(provider: String, model: String) {
        context.dataStore.edit {
            it[Keys.AI_PROVIDER] = provider.trim().lowercase()
            it[Keys.AI_MODEL] = model.trim()
        }
    }

    suspend fun setBackendBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BACKEND_BASE_URL] = url.trim() }
    }

    suspend fun setSessionToken(token: String) {
        context.dataStore.edit { it[Keys.SESSION_TOKEN] = token.trim() }
    }

    suspend fun recordBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_TIMESTAMP] = timestamp }
    }
}
