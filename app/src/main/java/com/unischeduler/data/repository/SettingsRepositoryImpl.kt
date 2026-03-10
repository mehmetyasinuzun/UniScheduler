package com.unischeduler.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.unischeduler.domain.model.AppLanguage
import com.unischeduler.domain.model.AppSettings
import com.unischeduler.domain.model.AppTheme
import com.unischeduler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFICATION_ADVANCE = intPreferencesKey("notification_advance_minutes")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[KEY_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrDefault(AppTheme.SYSTEM) } ?: AppTheme.SYSTEM,
            language = prefs[KEY_LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrDefault(AppLanguage.SYSTEM) } ?: AppLanguage.SYSTEM,
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            notificationAdvanceMinutes = prefs[KEY_NOTIFICATION_ADVANCE] ?: 15
        )
    }

    override suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.name }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun setNotificationAdvanceMinutes(minutes: Int) {
        dataStore.edit { it[KEY_NOTIFICATION_ADVANCE] = minutes }
    }
}
