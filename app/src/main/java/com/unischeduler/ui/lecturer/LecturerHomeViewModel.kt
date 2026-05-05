// LecturerHomeViewModel — loads lecturer profile + weekly course count from session.
package com.unischeduler.ui.lecturer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.repository.AuthRepository
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

data class LecturerHomeData(val lecturer: Lecturer, val weeklyCount: Int)

class LecturerHomeViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepo     = AuthRepository()
    private val scheduleRepo = ScheduleRepository()
    private val session      = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<LecturerHomeData>>(UiState.Idle)
    val state: StateFlow<UiState<LecturerHomeData>> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val orgId = session.orgId
                        if (orgId <= 0) {
                            throw IllegalStateException("Organization is missing for this account.")
                        }
                        val lecturerDeferred = async { authRepo.getLecturerByUserId(session.userId, orgId) }
                        val entriesDeferred  = async { scheduleRepo.getEntriesForLecturer(session.lecturerId, orgId) }
                        val lecturer = lecturerDeferred.await()
                            ?: throw IllegalStateException("Lecturer profile not found.")
                        LecturerHomeData(lecturer = lecturer, weeklyCount = entriesDeferred.await().size)
                    }
                }
            }.onSuccess { _state.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("load", e)
            }
        }
    }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("LecturerHomeViewModel", action, e)
        }
    }
}
