package com.unischeduler.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.AppLanguage
import com.unischeduler.domain.model.AppSettings
import com.unischeduler.domain.model.AppTheme
import com.unischeduler.domain.model.Department

import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.LecturerRepository
import com.unischeduler.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val departments: List<Department> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val lecturerRepository: LecturerRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        loadUserAndDepartments()
    }

    private fun observeSettings() {
        settingsRepository.settings
            .onEach { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
            .launchIn(viewModelScope)
    }

    private fun loadUserAndDepartments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val user = authRepository.getCurrentUser()
                val departments = if (user?.role == UserRole.ADMIN) {
                    lecturerRepository.getDepartments()
                } else emptyList()
                _uiState.value = _uiState.value.copy(
                    user = user,
                    departments = departments,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = e.message
                )
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setNotificationAdvanceMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setNotificationAdvanceMinutes(minutes) }
    }

    fun updateDeptPermission(departmentId: Int, permission: DeptHeadPermission) {
        viewModelScope.launch {
            try {
                val dept = _uiState.value.departments.find { it.id == departmentId } ?: return@launch
                lecturerRepository.upsertDepartment(dept.copy())
                // Optimistic local update — önce local state'i güncelle, DB'ye gönder
                val updated = _uiState.value.departments.map {
                    if (it.id == departmentId) it.copy() else it
                }
                _uiState.value = _uiState.value.copy(
                    departments = updated,
                    message = "permission_updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = e.message)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun logout() {
        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) { }
        }
    }
}
