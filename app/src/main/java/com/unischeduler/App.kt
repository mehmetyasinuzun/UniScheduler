package com.unischeduler

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.unischeduler.notif.NotificationHelper
import com.unischeduler.notif.ReminderScheduler
import com.unischeduler.util.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Native/uncaught crash'leri yakala — coroutine içindeki
        // exception'lar runCatching ile yakalanıyor, ama UI thread'de
        // veya callback'lerde yakalanmamış bir hata varsa proses
        // sessizce kapanıyor ve hiçbir log düşmüyordu. Handler diske
        // yazar, MainActivity login sonrası DB'ye flush eder.
        // (App.onCreate'te flush ÇAĞIRMAYIZ — JWT henüz yok ve
        // EncryptedSharedPreferences/AndroidKeyStore Robolectric
        // testlerinde mevcut değil; bu çağrı hem test'i hem login
        // öncesi crash raporlamayı bozuyordu.)
        CrashHandler.install(this)
        runMigrations()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val mode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)

        val lang = prefs.getString(KEY_LANGUAGE, null)
        if (lang != null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }

        // Local-reminder infrastructure (Sprint B2).
        // ensureChannels: creates the "Class reminders" notification channel
        //   so it shows up in system Settings → Notifications even before
        //   the first reminder fires (lets users mute us per-channel).
        // ensureDailyWorker: idempotently schedules the once-a-night
        //   WorkManager job that refreshes alarms.
        NotificationHelper.ensureChannels(this)
        ReminderScheduler.ensureDailyWorker(this)
    }

    /**
     * Schema migration runner.
     *
     * Android keeps `data/data/<pkg>/` across APK upgrades, so a brand-new
     * APK can find old SharedPreferences / encrypted session files written
     * by a previous version. When the on-disk format changes (e.g. we
     * rename a key or change EncryptedSharedPreferences key scheme) the old
     * state silently misbehaves.
     *
     * The runner checks the persisted `schema_version` against the current
     * `BuildConfig.VERSION_CODE` and runs each step's clear/transform on
     * the way up. Steps must be idempotent — they may run again if a user
     * skips releases.
     */
    private fun runMigrations() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVersion = prefs.getInt(KEY_SCHEMA_VERSION, 0)
        val currentVersion = BuildConfig.VERSION_CODE
        if (savedVersion >= currentVersion) return

        // ── v0 → v2: pre-multitenant. Old session prefs are missing org_id.
        // Wipe the encrypted session so the next launch routes through Login,
        // forcing a clean re-auth that populates orgId properly.
        if (savedVersion < 2) {
            deleteSharedPreferences("uni_scheduler_session")
        }

        // ── v2 → v3: reserved for future transformations. Add a new `if`
        // block here when bumping versionCode. Example:
        //   if (savedVersion < 3) {
        //       prefs.edit().remove("legacy_key").apply()
        //   }

        prefs.edit().putInt(KEY_SCHEMA_VERSION, currentVersion).apply()
    }

    companion object {
        const val PREFS_NAME = "app_prefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language_pref"
        const val KEY_SCHEMA_VERSION = "schema_version"

        // Process-wide scope that OUTLIVES Activity/Fragment/ViewModel lifecycles.
        // Use ONLY for fire-and-forget side effects whose completion the user
        // must not wait for AND that must not be killed if the screen that
        // triggered them gets destroyed.
        //
        // Concrete case: CTI login_attempts insert. We trigger it from
        // LoginViewModel.onSuccess, but the very next line Fragment navigates
        // away → viewModelScope cancels → the IO call is killed before reaching
        // the network. An app-scoped supervisor survives that navigation.
        //
        // SupervisorJob: a failure in one child does NOT cancel siblings.
        // Dispatchers.IO: network/disk friendly, doesn't block the main thread.
        val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
