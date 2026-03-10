package com.unischeduler.domain.repository

import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole

interface AuthRepository {
    suspend fun signIn(email: String, password: String): User
    suspend fun signUp(email: String, password: String, name: String, surname: String, role: UserRole, departmentId: Int?): User
    suspend fun signOut()
    suspend fun getCurrentUser(): User?
    suspend fun getSession(): Boolean
    suspend fun createLecturerAccount(email: String, password: String): String
    /** Davet koduyla öğretim üyesi kaydı: signup + DB claim */
    suspend fun registerWithInviteCode(email: String, password: String, name: String, surname: String, inviteCode: String): User
}
