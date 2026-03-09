package com.unischeduler.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.Department
import com.unischeduler.domain.model.DeptHeadPermission
import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.LecturerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val departments: List<Department> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val lecturerRepository: LecturerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val user = authRepository.getCurrentUser()
                val departments = if (user?.role == UserRole.ADMIN) {
                    lecturerRepository.getDepartments()
                } else emptyList()
                _uiState.value = SettingsUiState(
                    user = user,
                    departments = departments,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = e.message)
            }
        }
    }

    fun updateDeptPermission(departmentId: Int, permission: DeptHeadPermission) {
        viewModelScope.launch {
            try {
                val dept = _uiState.value.departments.find { it.id == departmentId } ?: return@launch
                lecturerRepository.upsertDepartment(dept.copy(deptHeadPermission = permission))
                loadSettings()
                _uiState.value = _uiState.value.copy(message = "İzin güncellendi")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = e.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (_: Exception) { }
        }
    }
}
