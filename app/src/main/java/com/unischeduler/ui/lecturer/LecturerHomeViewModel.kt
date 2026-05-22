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
                            throw com.unischeduler.util.UniSchedulerException(
                                com.unischeduler.R.string.err_lecturer_home_no_org
                            )
                        }
                        val lecturerId = session.lecturerId
                        if (lecturerId <= 0) {
                            throw IllegalStateException(
                                "Hocaya ait bir profil bulunamadı. Yönetici sizi öğretim üyesi listesine eklemiş olmalı; lütfen yöneticiyle iletişime geçin."
                            )
                        }
                        val lecturerDeferred = async { authRepo.getLecturerByUserId(session.userId, orgId) }
                        val entriesDeferred  = async { scheduleRepo.getEntriesForLecturer(lecturerId, orgId) }
                        // Önce user_id üzerinden çekiyoruz; RLS/embed timing
                        // sorunu null döndürürse session'da zaten saklı olan
                        // lecturer.id ile yeniden deniyoruz. İki sorgudan
                        // birinin başarılı olması yeterli — login'i tamamlamış
                        // bir kullanıcının profili kesin DB'de var.
                        val lecturer = lecturerDeferred.await()
                            ?: authRepo.getLecturerById(lecturerId, orgId)
                            ?: throw IllegalStateException(
                                "Hocaya ait kayıt bulunamadı (user_id=${session.userId.take(8)}…, " +
                                "org_id=$orgId, lecturer_id=$lecturerId). " +
                                "Yöneticinizin sizi öğretim üyesi listesine eklediğinden emin olun. " +
                                "Hesap aktifse bir kez çıkış yapıp tekrar giriş deneyin."
                            )
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
