// SessionManager — persists logged-in user data using EncryptedSharedPreferences.
// Stores: userId, username, role, lecturerId (if role=lecturer)
// Keys are never exposed in plaintext on disk.
package com.unischeduler.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "uni_scheduler_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_USER_ID     = "user_id"
        private const val KEY_ORG_ID      = "org_id"
        private const val KEY_USERNAME    = "username"
        private const val KEY_ROLE        = "role"
        private const val KEY_LECTURER_ID = "lecturer_id"
    }
}
