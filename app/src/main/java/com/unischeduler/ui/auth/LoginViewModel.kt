// LoginViewModel — authenticates user via Supabase GoTrue, updates SessionManager, emits UiState.
// Flow: signIn → fetch profile → save session → emit role.
package com.unischeduler.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.repository.AuthRepository
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = AuthRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<LoginResult>>(UiState.Idle)
    val state: StateFlow<UiState<LoginResult>> = _state

    fun login(username: String, password: String) {
        val attemptedUsername = username.trim()
        if (username.isBlank() || password.isBlank()) {
            _state.value = UiState.Error("Please fill in all fields.", retryable = false)
            reportValidationError("login", "Please fill in all fields.", attemptedUsername)
            return
        }

        // Wipe any persisted session before attempting a new login. Without this,
        // a failed login (or one that throws after JWT issue) could leave the old
        // session key/orgId in place and subsequent screens would query the wrong
        // org.
        session.clear()

        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    // 1. Sign in via Supabase GoTrue — obtains JWT.
                    //    AuthRepository.signIn() also tears down stale Realtime
                    //    subscriptions and the previous JWT before authenticating.
                    try {
                        repo.signIn(attemptedUsername, password)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid username or password.")
                    }

                    // 2. Fetch user profile from public.users
                    val user = repo.getCurrentUserProfile()
                        ?: throw IllegalStateException("User profile not found. Contact admin.")

                    val orgId = user.orgId
                    if (orgId <= 0) {
                        throw IllegalStateException("Organization is missing for this account.")
                    }

                    // 3. If lecturer, fetch lecturer profile
                    val lecturer = if (user.role == "lecturer") {
                        repo.getLecturerByUserId(user.id, orgId)
                            ?: throw IllegalStateException("Lecturer profile not found. Please contact admin.")
                    } else null

                    // 4. Save session
                    session.userId     = user.id
                    session.orgId      = orgId
                    session.username   = user.username
                    session.role       = user.role
                    session.lecturerId = lecturer?.id ?: -1

                    LoginResult(role = user.role, mustChangePassword = user.mustChangePassword)
                }
            }.onSuccess { result ->
                _state.value = UiState.Success(result)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("login", e, attemptedUsername)
            }
        }
    }

    private fun reportError(action: String, e: Throwable, username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("LoginViewModel", action, e, usernameOverride = username)
        }
    }

    private fun reportValidationError(action: String, message: String, username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportMessage("LoginViewModel", action, message, usernameOverride = username)
        }
    }
}

data class LoginResult(val role: String, val mustChangePassword: Boolean)
