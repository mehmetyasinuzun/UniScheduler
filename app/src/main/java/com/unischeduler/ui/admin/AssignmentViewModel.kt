// AssignmentViewModel — loads dropdown data in parallel, validates + saves assignment.
// Conflict detection: lecturer overlap + classroom overlap + availability check.
package com.unischeduler.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.DAYS
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.AvailabilityRepository
import com.unischeduler.data.repository.ClassroomRepository
import com.unischeduler.data.repository.LecturerRepository
import com.unischeduler.data.repository.OfferingRepository
import com.unischeduler.data.repository.ScheduleRepository
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AssignmentFormData(
    val offerings: List<Offering>,
    val lecturers: List<Lecturer>,
    val classrooms: List<Classroom>,
    val entries: List<ScheduleEntry>,
    val days: List<String> = DAYS
)

data class AssignmentWarnings(
    val conflicts: ScheduleRepository.ScheduleConflicts?,
    val availabilityWarning: String?   // null = lecturer is available
) {
    val hasIssues: Boolean get() = (conflicts?.hasConflicts == true) || (availabilityWarning != null)
}

class AssignmentViewModel(app: Application) : AndroidViewModel(app) {

    private val lecturerRepo     = LecturerRepository()
    private val classroomRepo    = ClassroomRepository()
    private val offeringRepo     = OfferingRepository()
    private val scheduleRepo     = ScheduleRepository()
    private val availabilityRepo = AvailabilityRepository()
    private val session          = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _formState = MutableStateFlow<UiState<AssignmentFormData>>(UiState.Idle)
    val formState: StateFlow<UiState<AssignmentFormData>> = _formState

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

    private val _warningState = MutableStateFlow<AssignmentWarnings?>(null)
    val warningState: StateFlow<AssignmentWarnings?> = _warningState

    // Keep backward compat for Fragment
    val conflictState: StateFlow<AssignmentWarnings?> = _warningState

    fun loadForm() {
        viewModelScope.launch {
            _formState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val orgId = session.orgId
                        val offerings  = async { offeringRepo.getAllOfferings(orgId) }
                        val lecturers  = async { lecturerRepo.getAllLecturers(orgId) }
                        val classrooms = async { classroomRepo.getAllClassrooms(orgId) }
                        val entries    = async { scheduleRepo.getAllEntries(orgId) }
                        AssignmentFormData(
                            offerings  = offerings.await(),
                            lecturers  = lecturers.await(),
                            classrooms = classrooms.await(),
                            entries    = entries.await()
                        )
                    }
                }
            }.onSuccess { _formState.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _formState.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadForm", e)
            }
        }
    }

    fun assign(
        offeringId: Int,
        lecturerId: Int?,
        classroomId: Int,
        day: String,
        startTime: String,
        endTime: String,
        force: Boolean = false
    ) {
        if (startTime.isBlank() || endTime.isBlank()) {
            _saveState.value = UiState.Error("Başlangıç ve bitiş saati gerekli.", retryable = false)
            reportValidationError("assign", "Start and end time are required.")
            return
        }
        if (toMinutes(startTime) >= toMinutes(endTime)) {
            _saveState.value = UiState.Error("Bitiş saati başlangıçtan sonra olmalı.", retryable = false)
            reportValidationError("assign", "End time must be after start time.")
            return
        }
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId

                    if (!force) {
                        val conflicts = scheduleRepo.findConflicts(
                            lecturerId  = lecturerId,
                            classroomId = classroomId,
                            day         = day,
                            startTime   = startTime,
                            endTime     = endTime,
                            orgId       = orgId
                        )

                        val availWarning = if (lecturerId != null) {
                            val available = availabilityRepo.isAvailable(
                                lecturerId = lecturerId,
                                day        = day,
                                startTime  = startTime,
                                endTime    = endTime,
                                orgId      = orgId
                            )
                            if (!available) "Öğretim üyesi bu saati müsait olarak işaretlememiş." else null
                        } else null

                        val warnings = AssignmentWarnings(
                            conflicts           = if (conflicts.hasConflicts) conflicts else null,
                            availabilityWarning = availWarning
                        )

                        if (warnings.hasIssues) {
                            _warningState.value = warnings
                            _saveState.value    = UiState.Idle
                            return@withContext
                        }
                    }

                    scheduleRepo.insertEntry(
                        offeringId  = offeringId,
                        lecturerId  = lecturerId,
                        classroomId = classroomId,
                        day         = day,
                        startTime   = startTime,
                        endTime     = endTime,
                        orgId       = orgId
                    )
                }
            }.onSuccess {
                if (_warningState.value == null) {
                    _saveState.value = UiState.Success(Unit)
                    loadForm()
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _saveState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("assign", e)
            }
        }
    }

    fun deleteEntry(entryId: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { scheduleRepo.deleteEntry(entryId, session.orgId) }
            }.onSuccess { loadForm() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _saveState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("deleteEntry", e)
            }
        }
    }

    fun resetSaveState()   { _saveState.value = UiState.Idle }
    fun clearWarnings()    { _warningState.value = null }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("AssignmentViewModel", action, e)
        }
    }

    private fun reportValidationError(action: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportMessage("AssignmentViewModel", action, message)
        }
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
