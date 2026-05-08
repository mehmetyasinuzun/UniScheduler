// Auth repository — login via Supabase GoTrue, password change, fetch logged-in user.
// All DB calls run on Dispatchers.IO via withContext in ViewModel layer.
// Uses Supabase Auth (GoTrue) for secure JWT-based authentication.
// Usernames are mapped to emails: {username}@unischeduler.app
package com.unischeduler.data.repository

import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.User
import com.unischeduler.data.remote.SupabaseClient
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class AuthRepository {

    companion object {
        /**
         * Converts username to synthetic email for Supabase Auth.
         * Uses dots instead of underscores — mirrors server.js usernameToEmail().
         */
        fun usernameToEmail(username: String): String {
            val local = username.trim().lowercase().replace("_", ".")
            return "${local}@unischeduler.app"
        }
    }

    /**
     * Sign in via Supabase GoTrue. Returns a JWT that the SDK stores automatically.
     * After this call, every Postgrest request carries the JWT → RLS is enforced.
     */
    suspend fun signIn(username: String, password: String) {
        // Tear down any leftover Realtime channels and the old JWT before
        // issuing a new login. Without this, callbackFlow subscriptions from
        // the previous session keep emitting against the old org_id.
        SupabaseClient.resetForLogout()

        val email = usernameToEmail(username)
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** Sign out — clears session, JWT token, and all live subscriptions. */
    suspend fun signOut() {
        SupabaseClient.resetForLogout()
    }

    /**
     * Fetch the public.users profile for the currently authenticated user.
     * auth.uid() in RLS matches this row.
     */
    suspend fun getCurrentUserProfile(): User? {
        val session = client.auth.currentSessionOrNull() ?: return null
        val authUid = session.user?.id ?: return null
        return client.postgrest["users"]
            .select { filter { eq("id", authUid) } }
            .decodeSingleOrNull<User>()
    }

    /** Fetch user profile by username (used for legacy lookup). */
    suspend fun findUserByUsername(username: String): User? =
        client.postgrest["users"]
            .select { filter { eq("username", username) } }
            .decodeSingleOrNull<User>()

    suspend fun getLecturerByUserId(userId: String, orgId: Int? = null): Lecturer? =
        client.postgrest["lecturers"]
            .select(Columns.raw("*, departments(*), users(*)")) {
                filter {
                    eq("user_id", userId)
                    if (orgId != null) eq("org_id", orgId)
                }
            }
            .decodeSingleOrNull<Lecturer>()

    /**
     * Change password via Supabase Auth and update public.users to clear must_change_password.
     */
    suspend fun updatePassword(userId: String, newPassword: String) {
        // Update in Supabase Auth (GoTrue)
        client.auth.updateUser {
            password = newPassword
        }
        // Update profile flag
        client.postgrest["users"]
            .update({
                set("must_change_password", false)
            }) {
                filter { eq("id", userId) }
            }
    }

    /**
     * Create a new auth user via Supabase Auth sign-up.
     * Returns the auth user UUID.
     * Note: Email confirmation must be disabled in the Supabase dashboard.
     */
    suspend fun createAuthUser(username: String, password: String): String {
        val email = usernameToEmail(username)
        // Sign up creates the auth.users entry
        val result = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // The signup returns user info; extract the UUID
        // After signup, we need to re-login as the admin because signUp may switch session
        return result?.id ?: throw IllegalStateException("Failed to create auth user.")
    }

}
