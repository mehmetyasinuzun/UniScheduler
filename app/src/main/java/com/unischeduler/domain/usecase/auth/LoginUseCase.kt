package com.unischeduler.domain.usecase.auth

import com.unischeduler.domain.model.User
import com.unischeduler.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        return runCatching { authRepository.signIn(email, password) }
    }
}
