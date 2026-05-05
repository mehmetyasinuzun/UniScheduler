// CalendarViewModel — loads schedule entries for the logged-in lecturer (or all for admin).
// Supports both one-shot load and real-time Flow observation (Lab Task 2 pattern).
package com.unischeduler.ui.lecturer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.ScheduleRepository
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = ScheduleRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<List<ScheduleEntry>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ScheduleEntry>>> = _state

    // Lecturer sees only their own entries; admin sees all
    fun loadEntries() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    if (orgId <= 0) {
                        throw IllegalStateException("Organization is missing for this account.")
                    }
                    if (session.isLecturer) {
                        val lecturerId = session.lecturerId
                        if (lecturerId <= 0) {
                            throw IllegalStateException("Lecturer profile missing. Please log in again.")
                        }
                        repo.getEntriesForLecturer(lecturerId, orgId)
                    } else {
                        repo.getAllEntries(orgId)
                    }
                }
            }.onSuccess { _state.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadEntries", e)
            }
        }
    }

    // Admin-only: load all entries
    fun loadAllEntries() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    if (orgId <= 0) {
                        throw IllegalStateException("Organization is missing for this account.")
                    }
                    repo.getAllEntries(orgId)
                }
            }.onSuccess { _state.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadAllEntries", e)
            }
        }
    }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("CalendarViewModel", action, e)
        }
    }
}
