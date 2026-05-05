// AdminHome ViewModel — loads 3 summary panels in parallel (Lab Task 5 pattern).
// Uses async/await inside coroutineScope for parallel Supabase queries.
package com.unischeduler.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.repository.ClassroomRepository
import com.unischeduler.data.repository.CourseRepository
import com.unischeduler.data.repository.LecturerRepository
import com.unischeduler.data.repository.OrgSettingsRepository
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

data class AdminDashboard(
    val unassignedLecturers: List<Lecturer>,
    val unassignedCourses: List<Course>,
    val classrooms: List<Classroom>
)

class AdminHomeViewModel(app: Application) : AndroidViewModel(app) {

    private val lecturerRepo    = LecturerRepository()
    private val courseRepo      = CourseRepository()
    private val classroomRepo   = ClassroomRepository()
    private val orgSettingsRepo = OrgSettingsRepository()
    private val session         = SessionManager(app)
    private val errorReporter   = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<AdminDashboard>>(UiState.Idle)
    val state: StateFlow<UiState<AdminDashboard>> = _state

    fun loadDashboard() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val orgId = session.orgId
                        if (orgId <= 0) throw IllegalStateException("Organization is missing for this account.")
                        val lecturers  = async { lecturerRepo.getUnassignedLecturers(orgId) }
                        val courses    = async { courseRepo.getUnassignedCourses(orgId) }
                        val settings   = async { orgSettingsRepo.getSettings(orgId) }
                        val classrooms = async { classroomRepo.getAvailableClassrooms(orgId, settings.await()) }
                        AdminDashboard(
                            unassignedLecturers = lecturers.await(),
                            unassignedCourses   = courses.await(),
                            classrooms          = classrooms.await()
                        )
                    }
                }
            }.onSuccess { _state.value = UiState.Success(it) }
             .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                reportError("loadDashboard", e)
            }
        }
    }

    private fun reportError(action: String, e: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("AdminHomeViewModel", action, e)
        }
    }
}
