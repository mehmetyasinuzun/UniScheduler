package com.unischeduler.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.Offering
import com.unischeduler.data.repository.CourseRepository
import com.unischeduler.data.repository.DepartmentRepository
import com.unischeduler.data.repository.LecturerRepository
import com.unischeduler.data.repository.OfferingRepository
import com.unischeduler.util.CsvImporter
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreatedCredentials(val username: String, val password: String)

data class LecturerImportResult(
    val imported: Int,
    val credentials: List<Pair<String, String>>,
    val errors: List<String>
)

class DataViewModel(app: Application) : AndroidViewModel(app) {

    private val deptRepo     = DepartmentRepository()
    private val lecturerRepo = LecturerRepository()
    private val courseRepo   = CourseRepository()
    private val offeringRepo = OfferingRepository()
    private val session      = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    // ── States ────────────────────────────────────────────────────────────────
    private val _deptState = MutableStateFlow<UiState<List<Department>>>(UiState.Idle)
    val deptState: StateFlow<UiState<List<Department>>> = _deptState

    private val _lecturerAddState = MutableStateFlow<UiState<CreatedCredentials>>(UiState.Idle)
    val lecturerAddState: StateFlow<UiState<CreatedCredentials>> = _lecturerAddState

    private val _lecturerListState = MutableStateFlow<UiState<List<Lecturer>>>(UiState.Idle)
    val lecturerListState: StateFlow<UiState<List<Lecturer>>> = _lecturerListState

    private val _lecturerDeleteState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val lecturerDeleteState: StateFlow<UiState<Unit>> = _lecturerDeleteState

    private val _lecturerEditState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val lecturerEditState: StateFlow<UiState<Unit>> = _lecturerEditState

    private val _courseAddState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val courseAddState: StateFlow<UiState<Unit>> = _courseAddState

    private val _courseListState = MutableStateFlow<UiState<List<Course>>>(UiState.Idle)
    val courseListState: StateFlow<UiState<List<Course>>> = _courseListState

    private val _offeringAddState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val offeringAddState: StateFlow<UiState<Unit>> = _offeringAddState

    private val _offeringListState = MutableStateFlow<UiState<List<Offering>>>(UiState.Idle)
    val offeringListState: StateFlow<UiState<List<Offering>>> = _offeringListState

    private val _offeringEditState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val offeringEditState: StateFlow<UiState<Unit>> = _offeringEditState

    // Import states
    private val _courseImportState = MutableStateFlow<UiState<Int>>(UiState.Idle)
    val courseImportState: StateFlow<UiState<Int>> = _courseImportState

    private val _lecturerImportState = MutableStateFlow<UiState<LecturerImportResult>>(UiState.Idle)
    val lecturerImportState: StateFlow<UiState<LecturerImportResult>> = _lecturerImportState

    // ── Department ────────────────────────────────────────────────────────────
    fun loadDepartments() {
        viewModelScope.launch {
            _deptState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { deptRepo.getAllDepartments(session.orgId) }
            }.onSuccess { _deptState.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _deptState.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadDepartments", e)
            }
        }
    }

    // ── Lecturer (manual) ─────────────────────────────────────────────────────
    fun addLecturer(title: String, firstName: String, lastName: String, departmentId: Int) {
        if (firstName.isBlank() || lastName.isBlank()) {
            _lecturerAddState.value = UiState.Error("First and last name are required.", retryable = false)
            return
        }
        viewModelScope.launch {
            _lecturerAddState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val (username, password) = lecturerRepo.insertLecturerWithUser(
                        title = title, firstName = firstName.trim(),
                        lastName = lastName.trim(), departmentId = departmentId, orgId = session.orgId
                    )
                    CreatedCredentials(username, password)
                }
            }.onSuccess { _lecturerAddState.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _lecturerAddState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("addLecturer", e)
            }
        }
    }

    fun editLecturer(id: Int, title: String, firstName: String, lastName: String, departmentId: Int, email: String?) {
        if (firstName.isBlank() || lastName.isBlank()) {
            _lecturerEditState.value = UiState.Error("First and last name are required.", retryable = false)
            return
        }
        viewModelScope.launch {
            _lecturerEditState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    lecturerRepo.updateLecturer(id, title.trim(), firstName.trim(), lastName.trim(), departmentId, email?.trim(), session.orgId)
                }
            }.onSuccess {
                _lecturerEditState.value = UiState.Success(Unit)
                loadLecturers()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _lecturerEditState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("editLecturer", e)
            }
        }
    }

    fun loadLecturers() {
        viewModelScope.launch {
            _lecturerListState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { lecturerRepo.getAllLecturers(session.orgId) }
            }.onSuccess { _lecturerListState.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _lecturerListState.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadLecturers", e)
            }
        }
    }

    fun deleteLecturer(lecturer: Lecturer) {
        if (lecturer.userId.isBlank()) {
            _lecturerDeleteState.value = UiState.Error("Lecturer user record is missing.", retryable = false)
            return
        }
        viewModelScope.launch {
            _lecturerDeleteState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { lecturerRepo.deleteLecturerUser(lecturer.userId, session.orgId) }
            }.onSuccess {
                _lecturerDeleteState.value = UiState.Success(Unit)
                loadLecturers()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _lecturerDeleteState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("deleteLecturer", e)
            }
        }
    }

    // ── Lecturer (import) ─────────────────────────────────────────────────────
    fun importLecturers(rows: List<CsvImporter.LecturerRow>, departmentId: Int) {
        if (rows.isEmpty()) {
            _lecturerImportState.value = UiState.Error("No valid rows to import.", retryable = false)
            return
        }
        viewModelScope.launch {
            _lecturerImportState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    val credentials = mutableListOf<Pair<String, String>>()
                    val errors = mutableListOf<String>()
                    for (row in rows) {
                        runCatching {
                            val (u, p) = lecturerRepo.insertLecturerWithUser(
                                title = row.title, firstName = row.firstName,
                                lastName = row.lastName, departmentId = departmentId,
                                orgId = orgId, email = row.email
                            )
                            credentials.add(u to p)
                        }.onFailure { e -> errors.add("${row.firstName} ${row.lastName}: ${e.message}") }
                    }
                    LecturerImportResult(imported = credentials.size, credentials = credentials, errors = errors)
                }
            }.onSuccess { _lecturerImportState.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _lecturerImportState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("importLecturers", e)
            }
        }
    }

    // ── Course ────────────────────────────────────────────────────────────────
    fun addCourse(code: String, name: String, departmentId: Int, theoryHours: Int = 0, labHours: Int = 0, credits: Int = 0) {
        if (code.isBlank() || name.isBlank()) {
            _courseAddState.value = UiState.Error("Course code and name are required.", retryable = false)
            return
        }
        viewModelScope.launch {
            _courseAddState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    courseRepo.insertCourse(code.trim().uppercase(), name.trim(), departmentId, session.orgId, theoryHours, labHours, credits)
                }
            }.onSuccess { _courseAddState.value = UiState.Success(Unit) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _courseAddState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("addCourse", e)
            }
        }
    }

    fun updateCourse(id: Int, code: String, name: String, theoryHours: Int, labHours: Int, credits: Int) {
        if (code.isBlank() || name.isBlank()) {
            _courseAddState.value = UiState.Error("Course code and name are required.", retryable = false)
            return
        }
        viewModelScope.launch {
            _courseAddState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { courseRepo.updateCourse(id, code.trim().uppercase(), name.trim(), theoryHours, labHours, credits, session.orgId) }
            }.onSuccess { _courseAddState.value = UiState.Success(Unit); loadCourses() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _courseAddState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("updateCourse", e)
            }
        }
    }

    fun deleteCourse(courseId: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { courseRepo.deleteCourse(courseId, session.orgId) }
            }.onSuccess { loadCourses() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _courseAddState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("deleteCourse", e)
            }
        }
    }

    fun loadCourses() {
        viewModelScope.launch {
            _courseListState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { courseRepo.getAllCourses(session.orgId) }
            }.onSuccess { _courseListState.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _courseListState.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadCourses", e)
            }
        }
    }

    // ── Course (import) ───────────────────────────────────────────────────────
    fun importCourses(rows: List<CsvImporter.CourseRow>, departmentId: Int) {
        if (rows.isEmpty()) {
            _courseImportState.value = UiState.Error("No valid rows to import.", retryable = false)
            return
        }
        viewModelScope.launch {
            _courseImportState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    var imported = 0
                    for (row in rows) {
                        runCatching {
                            courseRepo.insertCourse(row.code, row.name, departmentId, orgId,
                                theoryHours = row.theoryHours, labHours = row.labHours, credits = row.credits)
                            imported++
                        }
                    }
                    imported
                }
            }.onSuccess { count ->
                _courseImportState.value = UiState.Success(count)
                loadCourses()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _courseImportState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("importCourses", e)
            }
        }
    }

    // ── Offering ──────────────────────────────────────────────────────────────
    fun addOffering(courseId: Int, lecturerId: Int?, academicYear: String, term: String, classYear: Int, section: String, capacity: String) {
        val cap = capacity.toIntOrNull() ?: 0
        if (academicYear.isBlank() || term.isBlank() || section.isBlank()) {
            _offeringAddState.value = UiState.Error("Akademik yıl, dönem ve şube gerekli.", retryable = false)
            return
        }
        if (classYear !in 1..4) {
            _offeringAddState.value = UiState.Error("Sınıf yılı 1-4 arasında olmalı.", retryable = false)
            return
        }
        viewModelScope.launch {
            _offeringAddState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    offeringRepo.insertOffering(courseId, lecturerId, academicYear.trim(), term, classYear, section.trim().uppercase(), cap, session.orgId)
                }
            }.onSuccess { _offeringAddState.value = UiState.Success(Unit); loadOfferings() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _offeringAddState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("addOffering", e)
            }
        }
    }

    fun loadOfferings() {
        viewModelScope.launch {
            _offeringListState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { offeringRepo.getAllOfferings(session.orgId) }
            }.onSuccess { _offeringListState.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _offeringListState.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadOfferings", e)
            }
        }
    }

    fun editOffering(id: Int, lecturerId: Int?, academicYear: String, term: String, classYear: Int, section: String, capacity: Int) {
        viewModelScope.launch {
            _offeringEditState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) { offeringRepo.updateOffering(id, lecturerId, academicYear, term, classYear, section, capacity, session.orgId) }
            }.onSuccess { _offeringEditState.value = UiState.Success(Unit); loadOfferings() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _offeringEditState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("editOffering", e)
            }
        }
    }

    fun deleteOffering(id: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { offeringRepo.deleteOffering(id, session.orgId) }
            }.onSuccess { loadOfferings() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _offeringEditState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("deleteOffering", e)
            }
        }
    }

    // ── Resets ────────────────────────────────────────────────────────────────
    fun resetLecturerAddState()    { _lecturerAddState.value    = UiState.Idle }
    fun resetCourseAddState()      { _courseAddState.value      = UiState.Idle }
    fun resetLecturerDeleteState() { _lecturerDeleteState.value = UiState.Idle }
    fun resetLecturerEditState()   { _lecturerEditState.value   = UiState.Idle }
    fun resetOfferingAddState()    { _offeringAddState.value    = UiState.Idle }
    fun resetOfferingEditState()   { _offeringEditState.value   = UiState.Idle }
    fun resetCourseImportState()   { _courseImportState.value   = UiState.Idle }
    fun resetLecturerImportState() { _lecturerImportState.value = UiState.Idle }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) { errorReporter.reportException("DataViewModel", action, e) }
    }

    private fun reportValidationError(action: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) { errorReporter.reportMessage("DataViewModel", action, message) }
    }
}
