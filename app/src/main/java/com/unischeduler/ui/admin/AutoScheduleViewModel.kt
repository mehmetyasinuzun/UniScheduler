package com.unischeduler.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.repository.AvailabilityRepository
import com.unischeduler.data.repository.ClassroomRepository
import com.unischeduler.data.repository.LecturerRepository
import com.unischeduler.data.repository.OfferingRepository
import com.unischeduler.data.repository.OrgSettingsRepository
import com.unischeduler.data.repository.ScheduleRepository
import com.unischeduler.scheduler.ProposedEntry
import com.unischeduler.scheduler.ScheduleGenerator
import com.unischeduler.scheduler.SchedulePreferences
import com.unischeduler.scheduler.ScheduleResult
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val offeringRepo = OfferingRepository()
    private val classroomRepo = ClassroomRepository()
    private val lecturerRepo = LecturerRepository()
    private val availabilityRepo = AvailabilityRepository()
    private val scheduleRepo = ScheduleRepository()
    private val settingsRepo = OrgSettingsRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<ScheduleResult>>(UiState.Idle)
    val state: StateFlow<UiState<ScheduleResult>> = _state

    private val _alternatives = MutableStateFlow<List<ScheduleResult>>(emptyList())
    val alternatives: StateFlow<List<ScheduleResult>> = _alternatives

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex

    private val _saveState = MutableStateFlow<UiState<Int>>(UiState.Idle)
    val saveState: StateFlow<UiState<Int>> = _saveState

    private var lastResult: ScheduleResult? = null
    private val pinnedEntries = mutableListOf<ProposedEntry>()

    private val _preferences = MutableStateFlow(SchedulePreferences())
    val preferences: StateFlow<SchedulePreferences> = _preferences

    fun updatePreferences(prefs: SchedulePreferences) {
        _preferences.value = prefs
    }

    fun generateSchedule() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    coroutineScope {
                        val settingsD = async { settingsRepo.getSettings(orgId) }
                        val offeringsD = async { offeringRepo.getUnassignedOfferings(orgId) }
                        val classroomsD = async { classroomRepo.getAllClassrooms(orgId) }
                        val lecturersD = async { lecturerRepo.getAllLecturers(orgId) }
                        val existingD = async { scheduleRepo.getAllEntries(orgId) }

                        val settings = settingsD.await()
                        val offerings = offeringsD.await()
                        val classrooms = classroomsD.await()
                        val lecturers = lecturersD.await()
                        val existing = existingD.await()

                        val busySlots = mutableMapOf<Int, List<LecturerAvailability>>()
                        for (lecturer in lecturers) {
                            val slots = availabilityRepo.getForLecturer(lecturer.id, orgId)
                            if (slots.isNotEmpty()) {
                                busySlots[lecturer.id] = slots
                            }
                        }

                        val pinnedAsEntries = pinnedEntries.map { p ->
                            com.unischeduler.data.model.ScheduleEntry(
                                id = -1, orgId = orgId, offeringId = p.offering.id,
                                lecturerId = p.lecturerId, classroomId = p.classroom.id,
                                day = p.day, startTime = p.startTime, endTime = p.endTime,
                                offerings = p.offering, classrooms = p.classroom,
                                lecturers = p.offering.lecturers
                            )
                        }

                        val pinnedOfferingIds = pinnedEntries.map { it.offering.id }.toSet()
                        val remainingOfferings = offerings.filter { it.id !in pinnedOfferingIds }

                        val prefs = _preferences.value
                        val allResults = ScheduleGenerator.generateAlternatives(
                            count = prefs.alternativeCount.coerceIn(1, 10),
                            settings = settings,
                            classrooms = classrooms,
                            busySlots = busySlots,
                            existingEntries = existing + pinnedAsEntries,
                            offerings = remainingOfferings,
                            preferences = prefs
                        ).map { result ->
                            result.copy(assigned = pinnedEntries + result.assigned)
                        }

                        allResults
                    }
                }
            }.onSuccess { results ->
                _alternatives.value = results
                _selectedIndex.value = 0
                lastResult = results.firstOrNull()
                _state.value = if (lastResult != null) UiState.Success(lastResult!!) else UiState.Error("Hiç program oluşturulamadı", retryable = true)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorReporter.reportException("AutoSchedule", "generate", e)
                _state.value = UiState.Error("Program oluşturma hatası: ${e.message}", retryable = true)
            }
        }
    }

    fun selectAlternative(index: Int) {
        val alts = _alternatives.value
        if (index in alts.indices) {
            _selectedIndex.value = index
            lastResult = alts[index]
            _state.value = UiState.Success(alts[index])
        }
    }

    fun pinEntry(entry: ProposedEntry) {
        if (pinnedEntries.none { it.offering.id == entry.offering.id }) {
            pinnedEntries.add(entry)
            _state.value = _state.value
        }
    }

    fun unpinEntry(entry: ProposedEntry) {
        pinnedEntries.removeAll { it.offering.id == entry.offering.id }
        _state.value = _state.value
    }

    fun isPinned(entry: ProposedEntry): Boolean =
        pinnedEntries.any { it.offering.id == entry.offering.id }

    fun removeEntry(entry: ProposedEntry) {
        val current = lastResult ?: return
        val newAssigned = current.assigned.toMutableList()
        newAssigned.remove(entry)
        val newUnassigned = current.unassigned.toMutableList()
        newUnassigned.add(entry.offering)
        val newFailures = current.failures.toMutableList()
        newFailures.add(com.unischeduler.scheduler.FailureReason(entry.offering, listOf("Admin tarafından kaldırıldı")))
        lastResult = current.copy(assigned = newAssigned, unassigned = newUnassigned, failures = newFailures)
        _state.value = UiState.Success(lastResult!!)
    }

    fun saveSchedule() {
        val result = lastResult ?: return
        if (result.assigned.isEmpty()) return

        viewModelScope.launch {
            _saveState.value = UiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val orgId = session.orgId
                    var count = 0
                    for (entry in result.assigned) {
                        scheduleRepo.insertEntry(
                            offeringId = entry.offering.id,
                            lecturerId = entry.lecturerId,
                            classroomId = entry.classroom.id,
                            day = entry.day,
                            startTime = entry.startTime,
                            endTime = entry.endTime,
                            orgId = orgId
                        )
                        count++
                    }
                    count
                }
            }.onSuccess {
                _saveState.value = UiState.Success(it)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorReporter.reportException("AutoSchedule", "save", e)
                _saveState.value = UiState.Error("Kaydetme hatası: ${e.message}", retryable = true)
            }
        }
    }
}
