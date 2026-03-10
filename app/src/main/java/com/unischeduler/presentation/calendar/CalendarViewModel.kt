package com.unischeduler.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.domain.model.ScheduleSolution
import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.CourseRepository
import com.unischeduler.domain.repository.LecturerRepository
import com.unischeduler.domain.repository.ScheduleRepository
import com.unischeduler.domain.usecase.availability.UpdateAvailabilityUseCase
import com.unischeduler.domain.usecase.schedule.GenerateScheduleUseCase
import com.unischeduler.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarUiState(
    val assignments: List<ScheduleAssignment> = emptyList(),
    val config: ScheduleConfig? = null,
    val availability: Map<Int, List<AvailabilitySlot>> = emptyMap(),
    val myAvailability: List<AvailabilitySlot> = emptyList(),
    val myLecturerId: Int? = null,
    val courses: List<Course> = emptyList(),
    val lecturers: List<Lecturer> = emptyList(),
    val alternatives: List<ScheduleSolution> = emptyList(),
    val user: User? = null,
    val activeDepartmentId: Int? = null,
    val isLoading: Boolean = true,
    val isSavingAvailability: Boolean = false,
    val infoMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val courseRepository: CourseRepository,
    private val lecturerRepository: LecturerRepository,
    private val generateScheduleUseCase: GenerateScheduleUseCase,
    private val updateAvailabilityUseCase: UpdateAvailabilityUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _configState = MutableStateFlow<UiState<ScheduleConfig>>(UiState.Loading)
    val configState: StateFlow<UiState<ScheduleConfig>> = _configState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val user = authRepository.getCurrentUser()
                    ?: throw IllegalStateException("Oturum bulunamadi")
                val deptId = resolveDepartmentId(user)
                    ?: throw IllegalStateException("Bolum bilgisi bulunamadi")

                val assignments = scheduleRepository.getAssignmentsByDepartment(deptId)
                val config = sanitizeScheduleConfig(
                    scheduleRepository.getScheduleConfig(deptId)
                        ?: ScheduleConfig(departmentId = deptId),
                    deptId
                )
                val courses = courseRepository.getCoursesByDepartment(deptId)
                val lecturers = lecturerRepository.getLecturersByDepartment(deptId)

                val availabilityMap = mutableMapOf<Int, List<AvailabilitySlot>>()
                for (l in lecturers) {
                    availabilityMap[l.id] = scheduleRepository.getAvailability(l.id)
                }

                var myLecturerId: Int? = null
                val myAvailability = if (user.role == UserRole.LECTURER) {
                    val lecturer = lecturerRepository.getLecturerByProfileId(user.id)
                    myLecturerId = lecturer?.id
                    lecturer?.let { scheduleRepository.getAvailability(it.id) } ?: emptyList()
                } else emptyList()

                val warning = if (user.role == UserRole.LECTURER && myLecturerId == null) {
                    "Hoca profil eslesmesi bulunamadi. Lutfen yoneticiye davet koduyla kayit baglantisini kontrol ettirin."
                } else {
                    null
                }

                _uiState.value = CalendarUiState(
                    assignments = assignments,
                    config = config,
                    availability = availabilityMap,
                    myAvailability = myAvailability,
                    myLecturerId = myLecturerId,
                    courses = courses,
                    lecturers = lecturers,
                    user = user,
                    activeDepartmentId = deptId,
                    isLoading = false,
                    isSavingAvailability = false,
                    infoMessage = null,
                    error = warning
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Veri yüklenirken hata oluştu"
                )
            }
        }
    }

    fun loadConfig(departmentId: Int) {
        viewModelScope.launch {
            _configState.value = UiState.Loading
            try {
                val config = scheduleRepository.getScheduleConfig(departmentId)
                _configState.value = if (config != null) UiState.Success(config)
                else UiState.Success(ScheduleConfig(departmentId = departmentId))
            } catch (e: Exception) {
                _configState.value = UiState.Error(e.message ?: "Hata")
            }
        }
    }

    fun saveConfig(config: ScheduleConfig) {
        viewModelScope.launch {
            try {
                val saved = scheduleRepository.upsertScheduleConfig(config)
                _uiState.value = _uiState.value.copy(config = saved)
                _configState.value = UiState.Success(saved)
            } catch (e: Exception) {
                _configState.value = UiState.Error(e.message ?: "Kayıt başarısız")
            }
        }
    }

    fun generateAlternatives() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val state = _uiState.value
                val config = state.config ?: return@launch
                val courseLecturerMap = mutableMapOf<Int, Int>()
                for (l in state.lecturers) {
                    for (c in l.courses) {
                        courseLecturerMap[c.id] = l.id
                    }
                }
                val locked = state.assignments.filter { it.isLocked }

                val solutions = generateScheduleUseCase(
                    courses = state.courses,
                    lecturers = state.lecturers,
                    courseLecturerMap = courseLecturerMap,
                    availability = state.availability,
                    config = config,
                    lockedAssignments = locked,
                    alternativeCount = 3
                )
                _uiState.value = _uiState.value.copy(
                    alternatives = solutions,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Program oluşturulamadı"
                )
            }
        }
    }

    fun selectAlternative(solution: ScheduleSolution) {
        viewModelScope.launch {
            try {
                scheduleRepository.upsertAssignments(solution.assignments)
                _uiState.value = _uiState.value.copy(
                    assignments = solution.assignments,
                    alternatives = emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateMyAvailability(slots: List<AvailabilitySlot>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingAvailability = true,
                infoMessage = null,
                error = null
            )
            val result = updateAvailabilityUseCase(slots)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    myAvailability = slots,
                    isSavingAvailability = false,
                    infoMessage = "Musaitlik plani kaydedildi. Bu islem icin admin onayi gerekmez."
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSavingAvailability = false,
                    error = e.message ?: "Musaitlik kaydedilemedi"
                )
            }
        }
    }

    fun toggleAssignmentLock(assignmentId: Int) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.assignments.find { it.id == assignmentId } ?: return@launch
                scheduleRepository.lockAssignment(assignmentId, !current.isLocked)
                val updated = _uiState.value.assignments.map {
                    if (it.id == assignmentId) it.copy(isLocked = !it.isLocked) else it
                }
                _uiState.value = _uiState.value.copy(assignments = updated)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun refresh() = loadData()

    private fun sanitizeScheduleConfig(config: ScheduleConfig, departmentId: Int): ScheduleConfig {
        val validDays = config.activeDays
            .filter { it in 1..7 }
            .distinct()
            .sorted()
            .ifEmpty { listOf(1, 2, 3, 4, 5) }

        val validDuration = config.slotDurationMinutes
            .takeIf { it > 0 }
            ?: 60

        val start = config.dayStartTime.takeIf { isValidTime(it) } ?: "08:00"
        val end = config.dayEndTime.takeIf { isValidTime(it) } ?: "17:00"
        val normalizedEnd = if (toMinutes(end) <= toMinutes(start)) "17:00" else end

        return config.copy(
            departmentId = departmentId,
            slotDurationMinutes = validDuration,
            dayStartTime = start,
            dayEndTime = normalizedEnd,
            activeDays = validDays
        )
    }

    private fun isValidTime(value: String): Boolean {
        val parts = value.split(":")
        if (parts.size != 2) return false
        val h = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        return (parts[0].toInt() * 60) + parts[1].toInt()
    }

    private suspend fun resolveDepartmentId(user: User): Int? {
        if (user.departmentId != null) return user.departmentId
        if (user.role != UserRole.ADMIN) return null
        return lecturerRepository.getDepartments().firstOrNull()?.id
    }
}
