// Lecturer repository — CRUD + credential creation via Supabase Auth on import
// insertLecturerWithUser: creates auth user + users row + lecturers row
package com.unischeduler.data.repository

import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.LecturerInsert
import com.unischeduler.data.model.User
import com.unischeduler.data.model.UserInsert
import com.unischeduler.data.remote.SupabaseClient.client
import com.unischeduler.util.CredentialGenerator
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class LecturerRepository {

    suspend fun getAllLecturers(orgId: Int): List<Lecturer> =
        client.postgrest["lecturers"]
            .select(Columns.raw("*, departments(*), users(*)")) {
                filter { eq("org_id", orgId) }
            }
            .decodeList<Lecturer>()

    suspend fun getLecturersByDepartment(departmentId: Int, orgId: Int): List<Lecturer> =
        client.postgrest["lecturers"]
            .select(Columns.raw("*, departments(*), users(*)")) {
                filter {
                    eq("department_id", departmentId)
                    eq("org_id", orgId)
                }
            }
            .decodeList<Lecturer>()

    /**
     * Creates a Supabase Auth user + public.users profile + lecturers row.
     * Returns generated (username, plainPassword) so Admin can display/export them.
     *
     * Flow:
     * 1. Generate unique username
     * 2. Sign up via Supabase Auth (creates auth.users entry)
     * 3. Save current admin session, re-login as admin after signup
     * 4. Insert public.users profile with the auth user's UUID
     * 5. Insert lecturers row
     */
    suspend fun insertLecturerWithUser(
        title: String,
        firstName: String,
        lastName: String,
        departmentId: Int,
        orgId: Int,
        email: String? = null
    ): Pair<String, String> {
        val baseUsername  = CredentialGenerator.generateUsername(firstName, lastName)
        val username      = generateUniqueUsername(baseUsername)
        val plainPassword = CredentialGenerator.generatePassword()
        val syntheticEmail = AuthRepository.usernameToEmail(username)

        // Save current admin session info
        val currentSession = client.auth.currentSessionOrNull()

        // Create auth user via sign-up
        val signUpResult = client.auth.signUpWith(Email) {
            this.email = syntheticEmail
            this.password = plainPassword
        }

        val authUserId = signUpResult?.id
            ?: throw IllegalStateException("Failed to create auth user for lecturer.")

        // Re-authenticate as admin (signUp may switch session)
        if (currentSession != null) {
            try {
                client.auth.refreshCurrentSession()
            } catch (e: Exception) {
                // If refresh fails, the admin needs to re-login
            }
        }

        // Insert public.users profile
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
            // Rollback: can't easily delete auth user from client side
            throw e
        }

        // Insert lecturer
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
            // Best-effort rollback if lecturer insert fails
            runCatching {
                client.postgrest["users"].delete { filter { eq("id", authUserId) } }
            }
            throw e
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

    suspend fun deleteLecturerUser(userId: String, orgId: Int) {
        client.postgrest["users"]
            .delete {
                filter {
                    eq("id", userId)
                    eq("org_id", orgId)
                }
            }
    }

    // Returns lecturers with no entry in schedule_entries (unassigned)
    suspend fun getUnassignedLecturers(orgId: Int): List<Lecturer> {
        val all = getAllLecturers(orgId)
        val assignedIds = client.postgrest["schedule_entries"]
            .select(Columns.raw("lecturer_id")) {
                filter { eq("org_id", orgId) }
            }
            .decodeList<Map<String, Int>>()
            .mapNotNull { it["lecturer_id"] }
            .toSet()
        return all.filter { it.id !in assignedIds }
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
        val rows = client.postgrest["users"]
            .select(Columns.raw("id")) {
                filter { eq("username", username) }
            }
            .decodeList<Map<String, String>>()
        return rows.isNotEmpty()
    }
}
