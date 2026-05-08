package com.unischeduler

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        runMigrations()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val mode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)

        val lang = prefs.getString(KEY_LANGUAGE, null)
        if (lang != null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
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
    }
}
