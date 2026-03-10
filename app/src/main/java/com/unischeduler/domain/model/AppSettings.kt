package com.unischeduler.domain.model

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val code: String) { SYSTEM(""), TURKISH("tr"), ENGLISH("en") }

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val notificationAdvanceMinutes: Int = 15
)
