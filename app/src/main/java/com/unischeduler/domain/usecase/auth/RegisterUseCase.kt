package com.unischeduler.domain.usecase.auth

import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        name: String,
        surname: String,
        role: UserRole,
        departmentId: Int?
    ): Result<User> {
        return runCatching {
            authRepository.signUp(email, password, name, surname, role, departmentId)
        }
    }
}
