package com.unischeduler.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.Department
import com.unischeduler.data.repository.DepartmentRepository
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DepartmentRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<List<Department>>>(UiState.Idle)
    val state: StateFlow<UiState<List<Department>>> = _state

    private val _addState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val addState: StateFlow<UiState<Unit>> = _addState

    private val _editState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val editState: StateFlow<UiState<Unit>> = _editState

    private val _deleteState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteState: StateFlow<UiState<Unit>> = _deleteState

    fun loadDepartments() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { repo.getAllDepartments(session.orgId) }
            }
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                    reportError("loadDepartments", e)
                }
        }
    }

    fun addDepartment(name: String) {
        if (name.isBlank()) {
            _addState.value = UiState.Error("Department name is required.", retryable = false)
            reportValidationError("addDepartment", "Department name is required.")
            return
        }
        viewModelScope.launch {
            _addState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { repo.insertDepartment(name.trim(), session.orgId) }
            }
                .onSuccess {
                    _addState.value = UiState.Success(Unit)
                    loadDepartments()
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _addState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                    reportError("addDepartment", e)
                }
        }
    }

    fun editDepartment(id: Int, name: String) {
        if (name.isBlank()) {
            _editState.value = UiState.Error("Department name is required.", retryable = false)
            return
        }
        viewModelScope.launch {
            _editState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { repo.updateDepartment(id, name.trim(), session.orgId) }
            }
                .onSuccess {
                    _editState.value = UiState.Success(Unit)
                    loadDepartments()
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _editState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                    reportError("editDepartment", e)
                }
        }
    }

    fun deleteDepartment(id: Int) {
        viewModelScope.launch {
            _deleteState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { repo.deleteDepartment(id, session.orgId) }
            }
                .onSuccess {
                    _deleteState.value = UiState.Success(Unit)
                    loadDepartments()
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _deleteState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                    reportError("deleteDepartment", e)
                }
        }
    }

    fun resetAddState() { _addState.value = UiState.Idle }
    fun resetEditState() { _editState.value = UiState.Idle }
    fun resetDeleteState() { _deleteState.value = UiState.Idle }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("SettingsViewModel", action, e)
        }
    }

    private fun reportValidationError(action: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportMessage("SettingsViewModel", action, message)
        }
    }
}
