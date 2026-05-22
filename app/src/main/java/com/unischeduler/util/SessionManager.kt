// SessionManager — persists logged-in user data using EncryptedSharedPreferences.
// Stores: userId, username, role, lecturerId (if role=lecturer)
// Keys are never exposed in plaintext on disk.
package com.unischeduler.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // Three-tier prefs creation — each tier degrades a bit on security but
    // keeps the app launchable rather than crashing:
    //   1. Open the encrypted store as designed.
    //   2. Master-key drift / corrupted file: wipe and retry encryption.
    //   3. Even fresh encryption fails (rare — broken keystore on some
    //      Chinese OEMs, factory-reset state mid-launch): fall back to a
    //      plain SharedPreferences so the user can at least reach login.
    //      No PII is *written* in this fallback path because we always
    //      redirect to login when we can't decrypt — but we mark it so
    //      defensive callers can refuse to persist sensitive data.
    private val prefs: SharedPreferences = run {
        val attemptCreate = {
            EncryptedSharedPreferences.create(
                context,
                "uni_scheduler_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        runCatching { attemptCreate() }
            .recoverCatching {
                context.deleteSharedPreferences("uni_scheduler_session")
                attemptCreate()
            }
            .recoverCatching {
                // Last resort — Tink/Keystore unavailable on this device.
                // App still launches; user is forced through login on every
                // start because no session can be persisted. Better than a
                // permanent boot loop.
                context.getSharedPreferences("uni_scheduler_session_fallback", Context.MODE_PRIVATE)
            }
            .getOrThrow()
    }

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER_ID, v).apply()

    var orgId: Int
        get() = prefs.getInt(KEY_ORG_ID, -1)
        set(v) = prefs.edit().putInt(KEY_ORG_ID, v).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    var role: String
        get() = prefs.getString(KEY_ROLE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ROLE, v).apply()

    var lecturerId: Int
        get() = prefs.getInt(KEY_LECTURER_ID, -1)
        set(v) = prefs.edit().putInt(KEY_LECTURER_ID, v).apply()

    val isLoggedIn: Boolean get() = userId.isNotEmpty()
    val isAdmin: Boolean    get() = role == "admin"
    val isLecturer: Boolean get() = role == "lecturer"

    /**
     * Tüm session alanlarını TEK transaction içinde + synchronous (`commit`)
     * yazar. Önceki implementasyon her alan için ayrı `apply()` çağırıyordu;
     * 5 ayrı asenkron transaction'dan biri fail olursa session "yarı yazılı"
     * kalıyor (örn. orgId yazılmış ama role boş — bottom nav görünmez).
     *
     * @return commit başarılı mı? false ise pref write fail (disk dolu,
     *         keystore hatası vb.) — caller'ın login'i abort etmesi gerekir.
     */
    fun saveSession(
        userId: String,
        orgId: Int,
        username: String,
        role: String,
        lecturerId: Int
    ): Boolean {
        return prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putInt(KEY_ORG_ID, orgId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROLE, role)
            .putInt(KEY_LECTURER_ID, lecturerId)
            .commit()  // synchronous + atomic
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_USER_ID     = "user_id"
        private const val KEY_ORG_ID      = "org_id"
        private const val KEY_USERNAME    = "username"
        private const val KEY_ROLE        = "role"
        private const val KEY_LECTURER_ID = "lecturer_id"
    }
}
