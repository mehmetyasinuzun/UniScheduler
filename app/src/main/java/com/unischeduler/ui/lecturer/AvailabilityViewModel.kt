// AvailabilityViewModel — manages lecturer's BUSY time blocks (default = available).
package com.unischeduler.ui.lecturer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.repository.AvailabilityRepository
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AvailabilityViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = AvailabilityRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<List<LecturerAvailability>>>(UiState.Idle)
    val state: StateFlow<UiState<List<LecturerAvailability>>> = _state

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    repo.getForLecturer(session.lecturerId, session.orgId)
                }
            }.onSuccess { _state.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("load", e)
            }
        }
    }

    fun addSlot(day: String, startTime: String, endTime: String) {
        if (startTime.isBlank() || endTime.isBlank()) {
            _saveState.value = UiState.Error("Start and end time required.", retryable = false)
            reportValidationError("addSlot", "Start and end time required.")
            return
        }
        if (toMinutes(startTime) >= toMinutes(endTime)) {
            _saveState.value = UiState.Error("End time must be after start time.", retryable = false)
            reportValidationError("addSlot", "End time must be after start time.")
            return
        }
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    repo.insert(session.lecturerId, day, startTime, endTime, session.orgId)
                }
            }.onSuccess {
                _saveState.value = UiState.Success(Unit)
                load()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _saveState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("addSlot", e)
            }
        }
    }

    fun deleteSlot(id: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repo.delete(id, session.orgId) }
            }.onSuccess { load() }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _saveState.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("deleteSlot", e)
            }
        }
    }

    fun resetSaveState() { _saveState.value = UiState.Idle }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("AvailabilityViewModel", action, e)
        }
    }

    private fun reportValidationError(action: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportMessage("AvailabilityViewModel", action, message)
        }
    }

    private fun toMinutes(v: String): Int {
        val p = v.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
