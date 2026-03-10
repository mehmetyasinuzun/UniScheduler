package com.unischeduler.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.unischeduler.domain.model.AppLanguage
import com.unischeduler.presentation.navigation.AppNavHost
import com.unischeduler.presentation.settings.SettingsViewModel
import com.unischeduler.presentation.theme.UniSchedulerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val settings = settingsState.settings

            // Dil uygulama (Compose recomposition ile)
            applyLanguage(settings.language)

            UniSchedulerTheme(appTheme = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }

    private fun applyLanguage(language: AppLanguage) {
        val locale = when (language) {
            AppLanguage.TURKISH -> Locale("tr")
            AppLanguage.ENGLISH -> Locale("en")
            AppLanguage.SYSTEM -> return
        }
        val config = resources.configuration
        if (config.locales[0].language != locale.language) {
            Locale.setDefault(locale)
            val newConfig = resources.configuration
            newConfig.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(newConfig, resources.displayMetrics)
        }
    }
}
