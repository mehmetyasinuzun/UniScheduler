package com.unischeduler.ui.lecturer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.AvailabilityRepository
import com.unischeduler.data.repository.OrgSettingsRepository
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

data class AvailabilityData(
    val busySlots: List<LecturerAvailability>,
    val scheduleEntries: List<ScheduleEntry>,
    val settings: OrgSettings
)

class AvailabilityViewModel(app: Application) : AndroidViewModel(app) {

    private val repo         = AvailabilityRepository()
    private val scheduleRepo = ScheduleRepository()
    private val settingsRepo = OrgSettingsRepository()
    private val session      = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<AvailabilityData>>(UiState.Idle)
    val state: StateFlow<UiState<AvailabilityData>> = _state

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    val lecturerId = session.lecturerId
                    // Without a positive lecturer id (= account never linked
                    // to a lecturer profile, or session was cleared) any
                    // repo.getForLecturer/getEntriesForLecturer call hits
                    // the server with id=-1, which Postgres rejects with a
                    // constraint violation. Fail fast with a friendly message
                    // instead of leaking the raw 500.
                    if (lecturerId <= 0) {
                        throw IllegalStateException(
                            "Hocaya ait bir profil bulunamadı. Yöneticiyle iletişime geçin veya yeniden giriş yapın."
                        )
                    }
                    coroutineScope {
                        val slotsDeferred = async { repo.getForLecturer(lecturerId, orgId) }
                        val entriesDeferred = async { scheduleRepo.getEntriesForLecturer(lecturerId, orgId) }
                        val settingsDeferred = async { settingsRepo.getSettings(orgId) }
                        AvailabilityData(
                            busySlots = slotsDeferred.await(),
                            scheduleEntries = entriesDeferred.await(),
                            settings = settingsDeferred.await()
                        )
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

    fun addSlot(day: String, startTime: String, endTime: String) {
        if (startTime.isBlank() || endTime.isBlank()) {
            _saveState.value = UiState.Error(getApplication<android.app.Application>().getString(com.unischeduler.R.string.err_availability_times_required), retryable = false)
            return
        }
        if (toMinutes(startTime) >= toMinutes(endTime)) {
            _saveState.value = UiState.Error(getApplication<android.app.Application>().getString(com.unischeduler.R.string.err_availability_end_after_start), retryable = false)
            return
        }
        val lecturerId = session.lecturerId
        if (lecturerId <= 0) {
            _saveState.value = UiState.Error(
                "Hocaya ait bir profil bulunamadı. Yöneticiyle iletişime geçin.",
                retryable = false
            )
            return
        }
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    repo.insert(lecturerId, day, startTime, endTime, session.orgId)
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

    private fun toMinutes(v: String): Int {
        val p = v.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
