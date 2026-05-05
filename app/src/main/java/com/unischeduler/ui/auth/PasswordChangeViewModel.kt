// PasswordChangeViewModel — validates and saves new password via Supabase GoTrue.
// Verifies current password by attempting re-login, then updates via GoTrue modifyUser.
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

class PasswordChangeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = AuthRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val state: StateFlow<UiState<Unit>> = _state

    fun changePassword(current: String, newPass: String, confirm: String) {
        when {
            current.isBlank() || newPass.isBlank() || confirm.isBlank() ->
                _state.value = UiState.Error("All fields are required.", retryable = false).also {
                    reportValidationError("changePassword", "All fields are required.")
                }
            newPass != confirm ->
                _state.value = UiState.Error("Passwords do not match.", retryable = false).also {
                    reportValidationError("changePassword", "Passwords do not match.")
                }
            newPass.length < 6 ->
                _state.value = UiState.Error("Password must be at least 6 characters.", retryable = false).also {
                    reportValidationError("changePassword", "Password must be at least 6 characters.")
                }
            else -> doChange(current, newPass)
        }
    }

    private fun doChange(current: String, newPass: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    // Verify current password by attempting re-login
                    try {
                        repo.signIn(session.username, current)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Current password is incorrect.")
                    }

                    // Update password via Supabase Auth
                    repo.updatePassword(session.userId, newPass)
                }
            }.onSuccess {
                _state.value = UiState.Success(Unit)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("changePassword", e)
            }
        }
    }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("PasswordChangeViewModel", action, e)
        }
    }

    private fun reportValidationError(action: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportMessage("PasswordChangeViewModel", action, message)
        }
    }
}
