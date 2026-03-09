package com.unischeduler.data.repository

import com.unischeduler.data.remote.dto.ProfileDto
import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AuthRepository {

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
            role = role.name.lowercase(),
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
                ?: UserRole.STUDENT,
            departmentId = profile.departmentId,
            email = authUser.email
        )
    }

    override suspend fun getSession(): Boolean {
        return supabase.auth.currentSessionOrNull() != null
    }

    override suspend fun createLecturerAccount(email: String, password: String): String {
        // Mevcut oturumu sakla
        val currentSession = supabase.auth.currentSessionOrNull()

        // Yeni kullanıcı oluştur
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val newUserId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Öğretim üyesi hesabı oluşturulamadı")

        // Önceki oturumu geri yükle
        if (currentSession != null) {
            supabase.auth.importSession(currentSession)
        }

        return newUserId
    }
}
