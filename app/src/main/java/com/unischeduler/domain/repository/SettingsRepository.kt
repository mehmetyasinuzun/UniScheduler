package com.unischeduler.domain.repository

import com.unischeduler.domain.model.AppLanguage
import com.unischeduler.domain.model.AppSettings
import com.unischeduler.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: AppTheme)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotificationAdvanceMinutes(minutes: Int)
}
