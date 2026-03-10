package com.unischeduler.data.repository

import com.unischeduler.data.remote.dto.ProfileDto
import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AuthRepository {

    private suspend fun isInviteCodeValid(inviteCode: String): Boolean {
        val result = supabase.postgrest.rpc(
            function = "validate_lecturer_invite_code",
            parameters = buildJsonObject {
                put("p_invite_code", inviteCode.uppercase().trim())
            }
        ).decodeAs<JsonObject>()

        return result["valid"]?.toString()?.trim('"')?.equals("true", ignoreCase = true) == true
    }

    override suspend fun signIn(email: String, password: String): User {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return getCurrentUser() ?: throw IllegalStateException("Login failed")
    }

    override suspend fun signUp(
        email: String,
        password: String,
        name: String,
        surname: String,
        role: UserRole,
        departmentId: Int?
    ): User {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Signup failed")

        val profile = ProfileDto(
            id = userId,
            name = name,
            surname = surname,
            role = role.name,
            departmentId = departmentId
        )
        supabase.postgrest.from("profiles").upsert(profile)

        return User(
            id = userId,
            name = name,
            surname = surname,
            role = role,
            departmentId = departmentId,
            email = email
        )
    }

    /**
     * Davet koduyla öğretim üyesi kaydı:
     * 1. Supabase Auth'a normal signup
     * 2. DB fonksiyonu claim_lecturer_invite: profili LECTURER yapar + lecturer.profile_id bağlar
     */
    override suspend fun registerWithInviteCode(
        email: String,
        password: String,
        name: String,
        surname: String,
        inviteCode: String
    ): User {
        val normalizedCode = inviteCode.uppercase().trim()
        if (!isInviteCodeValid(normalizedCode)) {
            throw IllegalStateException("Geçersiz veya kullanılmış davet kodu")
        }

        // 1. Auth hesabı oluştur. Hesap zaten varsa aynı bilgilerle giriş yap.
        try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (e: Exception) {
            val msg = e.message?.lowercase().orEmpty()
            if (msg.contains("already") && msg.contains("registered")) {
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                } catch (_: Exception) {
                    throw IllegalStateException(
                        "Bu e-posta zaten kayıtlı. Lütfen aynı şifreyle giriş yapın veya şifrenizi sıfırlayın."
                    )
                }
            } else {
                throw e
            }
        }

        // Signup sonrası oturum açık olmalı
        supabase.auth.currentUserOrNull()
            ?: throw IllegalStateException("Kayıt başarısız, lütfen tekrar deneyin")

        // 2. DB fonksiyonu: davet kodunu talep et + profili LECTURER yap
        val result = supabase.postgrest.rpc(
            function = "claim_lecturer_invite",
            parameters = buildJsonObject {
                put("p_invite_code", normalizedCode)
                put("p_name", name.trim())
                put("p_surname", surname.trim())
            }
        ).decodeAs<JsonObject>()

        val success = result["success"]?.toString()?.trim('"') == "true"
        if (!success) {
            val error = result["error"]?.toString()?.trim('"') ?: "Davet kodu geçersiz"
            // Claim başarısızsa oturumu kapat (hesap daha sonra tekrar claim edilebilir)
            supabase.auth.signOut()
            throw IllegalStateException(error)
        }

        return getCurrentUser() ?: throw IllegalStateException("Profil yüklenemedi")
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }

    override suspend fun getCurrentUser(): User? {
        val authUser = supabase.auth.currentUserOrNull() ?: return null
        val profile = supabase.postgrest.from("profiles")
            .select { filter { eq("id", authUser.id) } }
            .decodeSingleOrNull<ProfileDto>() ?: return null

        return User(
            id = profile.id,
            name = profile.name,
            surname = profile.surname,
            role = UserRole.entries.find { it.name.equals(profile.role, ignoreCase = true) }
                ?: UserRole.LECTURER,
            departmentId = profile.departmentId,
            email = authUser.email
        )
    }

    override suspend fun getSession(): Boolean {
        return supabase.auth.currentSessionOrNull() != null
    }

    override suspend fun createLecturerAccount(email: String, password: String): String {
        throw UnsupportedOperationException(
            "createLecturerAccount devre dışı. Hoca kaydı yalnızca davet kodu + self-signup akışı ile yapılmalıdır."
        )
    }
}

