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
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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

    suspend fun getLecturersByDepartment(departmentId: Int, orgId: Int): List<Lecturer> =
        client.postgrest["lecturers"]
            .select(joinColumns) {
                filter {
                    eq("department_id", departmentId)
                    eq("org_id", orgId)
                }
                order(column = "last_name", order = Order.ASCENDING)
                limit(10000)
            }
            .decodeList<Lecturer>()

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

        val adminSession = client.auth.currentSessionOrNull()
            ?: throw IllegalStateException("Admin oturumu bulunamadı. Lütfen tekrar giriş yapın.")

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
            runCatching { client.auth.importSession(adminSession) }
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Auth kullanıcı oluşturma hatası: ${e.message}", e)
        }

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
            runCatching { client.auth.importSession(adminSession) }
            throw IllegalStateException("Kullanıcı profili oluşturma hatası: ${e.message}", e)
        }

        client.auth.importSession(adminSession)

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
            runCatching {
                client.postgrest["users"].delete { filter { eq("id", authUserId) } }
            }
            throw IllegalStateException("Öğretim üyesi kaydı oluşturma hatası: ${e.message}", e)
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
        return Json.decodeFromString<List<Map<String, JsonElement>>>(raw)
            .mapNotNull { row ->
                val el = row[column]
                if (el == null || el is JsonNull) null
                else el.jsonPrimitive.intOrNull
            }
            .toSet()
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
