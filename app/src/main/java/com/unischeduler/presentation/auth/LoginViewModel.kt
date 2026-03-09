package com.unischeduler.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.User
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<User>>(UiState.Empty)
    val uiState: StateFlow<UiState<User>> = _uiState.asStateFlow()

    suspend fun checkSession(): Boolean {
        return try {
            val hasSession = authRepository.getSession()
            if (hasSession) {
                _currentUser.value = authRepository.getCurrentUser()
            }
            hasSession
        } catch (_: Exception) {
            false
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val user = authRepository.signIn(email, password)
                _currentUser.value = user
                _uiState.value = UiState.Success(user)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Giriş başarısız")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                _currentUser.value = null
                _uiState.value = UiState.Empty
            } catch (_: Exception) { }
        }
    }
}
