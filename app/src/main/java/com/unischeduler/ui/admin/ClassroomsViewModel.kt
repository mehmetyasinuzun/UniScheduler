package com.unischeduler.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Department
import com.unischeduler.data.repository.ClassroomRepository
import com.unischeduler.data.repository.DepartmentRepository
import com.unischeduler.util.CsvImporter
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClassroomsViewModel(app: Application) : AndroidViewModel(app) {

    private val classroomRepo = ClassroomRepository()
    private val departmentRepo = DepartmentRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<List<Classroom>>>(UiState.Idle)
    val state: StateFlow<UiState<List<Classroom>>> = _state

    private val _addState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val addState: StateFlow<UiState<Unit>> = _addState

    private val _importState = MutableStateFlow<UiState<Int>>(UiState.Idle)
    val importState: StateFlow<UiState<Int>> = _importState

    private val _departments = MutableStateFlow<List<Department>>(emptyList())
    val departments: StateFlow<List<Department>> = _departments

    init {
        loadDepartmentsSilently()
    }

    private fun loadDepartmentsSilently() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { departmentRepo.getAllDepartments(session.orgId) }
            }.onSuccess { _departments.value = it }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                reportError("loadDepartments", e)
            }
        }
    }

    fun loadClassrooms() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { classroomRepo.getAllClassrooms(session.orgId) }
            }.onSuccess { _state.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadClassrooms", e)
            }
        }
    }

    fun addClassroom(roomCode: String, capacity: String, departmentId: Int?, type: String = "theory") {
        val cap = capacity.toIntOrNull()
        if (roomCode.isBlank() || cap == null || cap <= 0) {
            _addState.value = UiState.Error("Enter a valid room code and capacity.", retryable = false)
            reportValidationError("addClassroom", "Enter a valid room code and capacity.")
            return
        }
        viewModelScope.launch {
            _addState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    classroomRepo.insertClassroom(roomCode.trim(), cap, departmentId, session.orgId, type)
                }
            }.onSuccess {
                _addState.value = UiState.Success(Unit)
                loadClassrooms()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _addState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("addClassroom", e)
            }
        }
    }

    fun importClassrooms(rows: List<CsvImporter.ClassroomRow>, departmentId: Int?) {
        if (rows.isEmpty()) {
            _importState.value = UiState.Error("No valid rows to import.", retryable = false)
            reportValidationError("importClassrooms", "No valid rows to import.")
            return
        }
        viewModelScope.launch {
            _importState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId   = session.orgId
                    var imported = 0
                    for (row in rows) {
                        runCatching {
                            classroomRepo.insertClassroom(row.roomCode, row.capacity, departmentId, orgId)
                            imported++
                        }
                        // Skip duplicates silently
                    }
                    imported
                }
            }.onSuccess { count ->
                _importState.value = UiState.Success(count)
                loadClassrooms()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _importState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("importClassrooms", e)
            }
        }
    }

    fun deleteClassroom(id: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { classroomRepo.deleteClassroom(id, session.orgId) }
            }.onSuccess { loadClassrooms() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _addState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("deleteClassroom", e)
            }
        }
    }

    fun resetAddState()    { _addState.value    = UiState.Idle }
    fun resetImportState() { _importState.value = UiState.Idle }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("ClassroomsViewModel", action, e)
        }
    }

    private fun reportValidationError(action: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportMessage("ClassroomsViewModel", action, message)
        }
    }
}
