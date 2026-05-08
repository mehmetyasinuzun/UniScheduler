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

    private val _editState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val editState: StateFlow<UiState<Unit>> = _editState

    private val _importState = MutableStateFlow<UiState<Int>>(UiState.Idle)
    val importState: StateFlow<UiState<Int>> = _importState

    private val _departments = MutableStateFlow<List<Department>>(emptyList())
    val departments: StateFlow<List<Department>> = _departments

    init {
        loadDepartmentsSilently()
    }

    fun loadDepartmentsSilently() {
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

    /**
     * Import classrooms. Each row's [CsvImporter.ClassroomRow.departmentName] takes
     * precedence over [fallbackDepartmentId] when matched by case-insensitive
     * name; otherwise the fallback (or null) is used.
     */
    fun importClassrooms(rows: List<CsvImporter.ClassroomRow>, fallbackDepartmentId: Int?) {
        if (rows.isEmpty()) {
            _importState.value = UiState.Error("İçe aktarılacak geçerli satır yok.", retryable = false)
            reportValidationError("importClassrooms", "İçe aktarılacak geçerli satır yok.")
            return
        }
        viewModelScope.launch {
            _importState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId    = session.orgId
                    val depts    = departmentRepo.getAllDepartments(orgId)
                    val byName   = depts.associateBy { it.name.lowercase().trim() }
                    var imported = 0
                    for (row in rows) {
                        val deptId = row.departmentName?.lowercase()?.trim()
                            ?.let { byName[it]?.id } ?: fallbackDepartmentId
                        runCatching {
                            classroomRepo.insertClassroom(row.roomCode, row.capacity, deptId, orgId, type = row.type)
                            imported++
                        }
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

    fun updateClassroom(id: Int, roomCode: String, capacity: String, departmentId: Int?, type: String) {
        val cap = capacity.toIntOrNull()
        if (roomCode.isBlank() || cap == null || cap <= 0) {
            _editState.value = UiState.Error("Geçerli bir derslik kodu ve kapasite girin.", retryable = false)
            reportValidationError("updateClassroom", "Geçersiz girdi.")
            return
        }
        viewModelScope.launch {
            _editState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    classroomRepo.updateClassroom(id, roomCode.trim(), cap, departmentId, type, session.orgId)
                }
            }.onSuccess {
                _editState.value = UiState.Success(Unit)
                loadClassrooms()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _editState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("updateClassroom", e)
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
    fun resetEditState()   { _editState.value   = UiState.Idle }
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
