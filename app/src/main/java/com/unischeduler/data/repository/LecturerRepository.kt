// Lecturer repository — CRUD + credential creation via Supabase Auth on import
// insertLecturerWithUser: creates auth user + users row + lecturers row
package com.unischeduler.data.repository

import android.util.Log
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.LecturerInsert
import com.unischeduler.data.model.User
import com.unischeduler.data.model.UserInsert
import com.unischeduler.data.remote.SupabaseClient.client
import com.unischeduler.util.CredentialGenerator
import com.unischeduler.util.JsonUtil
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class LecturerRepository {

    private val joinColumns = Columns.raw("*, departments(*), users(*)")

    suspend fun getAllLecturers(orgId: Int): List<Lecturer> {
        val response = client.postgrest["lecturers"]
            .select(joinColumns) {
                filter { eq("org_id", orgId) }
                order(column = "last_name", order = Order.ASCENDING)
                limit(10000)
            }
        val raw = response.data
        Log.d("LecturerRepo", "getAllLecturers orgId=$orgId rawLength=${raw.length} first200=${raw.take(200)}")
        val result = response.decodeList<Lecturer>()
        Log.d("LecturerRepo", "getAllLecturers decoded=${result.size}")
        return result
    }

    /**
     * Creates a new lecturer end-to-end: auth user + public.users row +
     * lecturers row. All three inserts run in the ADMIN's session — the
     * old design let signUpWith flip the JWT to the new user mid-flow,
     * which made RLS context unpredictable and produced the
     * "Hocaya ait bir profil bulunamadı" symptom on first login.
     *
     * The schema policy `users_insert_admin` is what makes this work:
     * the admin can insert the new user's profile row directly.
     */
    suspend fun insertLecturerWithUser(
        title: String,
        firstName: String,
        lastName: String,
        departmentId: Int,
        orgId: Int,
        email: String? = null
    ): Pair<String, String> {
        val baseUsername   = CredentialGenerator.generateUsername(firstName, lastName)
        val username       = generateUniqueUsername(baseUsername)
        val plainPassword  = CredentialGenerator.generatePassword()
        val syntheticEmail = AuthRepository.usernameToEmail(username)

        val adminSession = client.auth.currentSessionOrNull()
            ?: throw com.unischeduler.util.UniSchedulerException(com.unischeduler.R.string.err_auth_no_admin_session)

        // ── Step 1: Create the Auth user (Supabase will switch our
        //           current session to the new user as a side-effect). ──
        val authUserId: String
        try {
            val signUpResult = client.auth.signUpWith(Email) {
                this.email = syntheticEmail
                this.password = plainPassword
            }
            authUserId = signUpResult?.id
                ?: client.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException(
                    "Kullanıcı oluşturulamadı (email: $syntheticEmail). " +
                    "Supabase Auth ayarlarında 'Enable email confirmations' kapalı olmalı."
                )
        } catch (e: Exception) {
            // Best-effort: restore admin session even on failure so the
            // app is usable afterward.
            runCatching { client.auth.importSession(adminSession) }
            if (e is IllegalStateException) throw e
            if (e is com.unischeduler.util.UniSchedulerException) throw e
            throw com.unischeduler.util.UniSchedulerException(
                com.unischeduler.R.string.err_auth_create_user,
                arrayOf(e.message ?: e::class.java.simpleName),
                e
            )
        }

        // ── Step 2: IMMEDIATELY restore the admin session BEFORE any
        //           Postgrest call. From this point onward every insert
        //           is evaluated against the admin's RLS context, which
        //           is stable and known-correct. ──
        client.auth.importSession(adminSession)

        // ── Step 3: Insert the public.users profile (admin RLS:
        //           users_insert_admin policy). ──
        try {
            client.postgrest["users"].insert(
                UserInsert(
                    id = authUserId,
                    orgId = orgId,
                    username = username,
                    role = "lecturer",
                    mustChangePassword = true
                )
            )
        } catch (e: Exception) {
            // The auth user already exists — ideally we'd
            // auth.admin.deleteUser() here, but that requires service_role
            // which the mobile app intentionally doesn't carry. Best
            // effort: log + surface so the panel admin can clean up the
            // orphan auth user manually.
            android.util.Log.e("LecturerRepo",
                "users insert failed (auth orphan: $syntheticEmail). " +
                "Use the panel to delete this user.", e)
            throw com.unischeduler.util.UniSchedulerException(
                com.unischeduler.R.string.err_user_profile_create,
                arrayOf(e.message ?: e::class.java.simpleName),
                e
            )
        }

        // ── Step 4: Insert lecturers profile (still admin session). ──
        try {
            client.postgrest["lecturers"].insert(
                LecturerInsert(
                    orgId = orgId,
                    userId = authUserId,
                    title = title,
                    firstName = firstName,
                    lastName = lastName,
                    departmentId = departmentId,
                    email = email?.takeIf { it.isNotBlank() }
                )
            )
        } catch (e: Exception) {
            // Roll back: delete the just-inserted users row so we don't
            // leave a profile with no lecturer record (which would make
            // login fail with "profile not found"). Auth orphan still
            // needs panel cleanup.
            runCatching {
                client.postgrest["users"].delete { filter { eq("id", authUserId) } }
            }
            throw com.unischeduler.util.UniSchedulerException(
                com.unischeduler.R.string.err_lecturer_create,
                arrayOf(e.message ?: e::class.java.simpleName),
                e
            )
        }

        return username to plainPassword
    }

    /**
     * Update lecturer profile fields.
     */
    suspend fun updateLecturer(
        id: Int,
        title: String,
        firstName: String,
        lastName: String,
        departmentId: Int,
        email: String?,
        orgId: Int
    ) {
        client.postgrest["lecturers"]
            .update({
                set("title", title)
                set("first_name", firstName)
                set("last_name", lastName)
                set("department_id", departmentId)
                set("email", email)
            }) {
                filter {
                    eq("id", id)
                    eq("org_id", orgId)
                }
            }
    }

    /**
     * Yeni rastgele şifre üretip Supabase Auth'taki şifreyi sıfırlar.
     * Mobil admin'in service_role'ü olmadığı için doğrudan auth.users'a
     * yazamayız; iş schema'daki `admin_reset_lecturer_password` SECURITY
     * DEFINER fonksiyonuna devredilir. Üretilen düz şifreyi geri döner ki
     * UI bunu admin'e bir kez gösterip kopyalama imkânı sunsun.
     */
    suspend fun resetLecturerPassword(lecturerId: Int): String {
        val newPassword = CredentialGenerator.generatePassword()
        client.postgrest.rpc(
            function = "admin_reset_lecturer_password",
            parameters = kotlinx.serialization.json.buildJsonObject {
                put("p_lecturer_id", kotlinx.serialization.json.JsonPrimitive(lecturerId))
                put("p_new_password", kotlinx.serialization.json.JsonPrimitive(newPassword))
            }
        )
        return newPassword
    }

    suspend fun deleteLecturerUser(userId: String, orgId: Int) {
        client.postgrest["users"]
            .delete {
                filter {
                    eq("id", userId)
                    eq("org_id", orgId)
                }
            }
    }

    suspend fun getUnassignedLecturers(orgId: Int): List<Lecturer> {
        val all = getAllLecturers(orgId)
        val offeringIds = extractNullableIds("offerings", "lecturer_id", orgId)
        val scheduleIds = extractNullableIds("schedule_entries", "lecturer_id", orgId)
        val assignedIds = offeringIds + scheduleIds
        return all.filter { it.id !in assignedIds }
    }

    private suspend fun extractNullableIds(table: String, column: String, orgId: Int): Set<Int> {
        val raw = client.postgrest[table]
            .select(Columns.raw(column)) { filter { eq("org_id", orgId) } }
            .data
        return JsonUtil.extractIntsFromColumn(raw, column)
    }

    private suspend fun generateUniqueUsername(base: String): String {
        var candidate = base
        var suffix = 2
        while (usernameExists(candidate)) {
            candidate = "$base$suffix"
            suffix++
        }
        return candidate
    }

    private suspend fun usernameExists(username: String): Boolean {
        val raw = client.postgrest["users"]
            .select(Columns.raw("id")) {
                filter { eq("username", username) }
            }
            .data
        return JsonUtil.rowCount(raw) > 0
    }
}
