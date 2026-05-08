package com.unischeduler.ui.lecturer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
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

data class CalendarData(
    val entries: List<ScheduleEntry>,
    val settings: OrgSettings
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo         = ScheduleRepository()
    private val settingsRepo = OrgSettingsRepository()
    private val session      = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<CalendarData>>(UiState.Idle)
    val state: StateFlow<UiState<CalendarData>> = _state

    fun loadEntries() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    if (orgId <= 0) throw IllegalStateException("Organization is missing for this account.")

                    coroutineScope {
                        val settingsDeferred = async { settingsRepo.getSettings(orgId) }
                        val entriesDeferred = async {
                            if (session.isLecturer) {
                                val lecturerId = session.lecturerId
                                if (lecturerId <= 0) {
                                    throw IllegalStateException(
                                        "Hocaya ait bir profil bulunamadı. Yöneticiyle iletişime geçin veya yeniden giriş yapın."
                                    )
                                }
                                repo.getEntriesForLecturer(lecturerId, orgId)
                            } else {
                                repo.getAllEntries(orgId)
                            }
                        }
                        CalendarData(entriesDeferred.await(), settingsDeferred.await())
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

    fun loadAllEntries() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    if (orgId <= 0) throw IllegalStateException("Organization is missing for this account.")

                    coroutineScope {
                        val settingsDeferred = async { settingsRepo.getSettings(orgId) }
                        val entriesDeferred = async { repo.getAllEntries(orgId) }
                        CalendarData(entriesDeferred.await(), settingsDeferred.await())
                    }
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
