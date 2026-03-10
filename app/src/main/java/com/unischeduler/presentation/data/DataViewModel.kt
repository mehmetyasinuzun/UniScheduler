package com.unischeduler.presentation.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Department
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.model.User
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.CourseRepository
import com.unischeduler.domain.repository.LecturerRepository
import com.unischeduler.domain.repository.ScheduleRepository
import com.unischeduler.domain.usecase.export.ExportCredentialsUseCase
import com.unischeduler.domain.usecase.export.ExportScheduleUseCase
import com.unischeduler.domain.usecase.import_data.ImportExcelUseCase
import com.unischeduler.domain.usecase.import_data.ImportResult
import com.unischeduler.domain.usecase.import_data.ImportRow
import com.unischeduler.presentation.common.UiState
import com.unischeduler.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataUiState(
    val user: User? = null,
    val departments: List<Department> = emptyList(),
    val courses: List<Course> = emptyList(),
    val lecturers: List<Lecturer> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class ImportPreviewState(
    val rows: List<ImportRow> = emptyList(),
    val fileBytes: ByteArray? = null,
    val fileName: String = "",
    val isImporting: Boolean = false,
    val importResult: ImportResult? = null,
    val error: String? = null
)

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val courseRepository: CourseRepository,
    private val lecturerRepository: LecturerRepository,
    private val scheduleRepository: ScheduleRepository,
    private val importExcelUseCase: ImportExcelUseCase,
    private val exportScheduleUseCase: ExportScheduleUseCase,
    private val exportCredentialsUseCase: ExportCredentialsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private val _importState = MutableStateFlow(ImportPreviewState())
    val importState: StateFlow<ImportPreviewState> = _importState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            AppLogger.d("DataVM", "Veri yükleme başlatılıyor...")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val user = authRepository.getCurrentUser()
                val deptId = user?.departmentId ?: 1
                AppLogger.d("DataVM", "Kullanıcı: ${user?.email}, role=${user?.role}, deptId=$deptId")
                val departments = lecturerRepository.getDepartments()
                val courses = courseRepository.getCoursesByDepartment(deptId)
                val lecturers = lecturerRepository.getLecturersByDepartment(deptId)

                AppLogger.i("DataVM", "Veri yüklendi: ${departments.size} bölüm, ${courses.size} ders, ${lecturers.size} hoca")
                _uiState.value = DataUiState(
                    user = user,
                    departments = departments,
                    courses = courses,
                    lecturers = lecturers,
                    isLoading = false
                )
            } catch (e: Exception) {
                AppLogger.e("DataVM", "Veri yüklenirken hata: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Veri yüklenirken hata"
                )
            }
        }
    }

    fun parseExcelFile(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            try {
                AppLogger.i("DataVM", "Excel parse başlatılıyor: $fileName (${bytes.size} byte)")
                val rows = importExcelUseCase.parseExcel(bytes)
                AppLogger.i("DataVM", "Excel parse tamamlandı: ${rows.size} satır bulundu")
                _importState.value = ImportPreviewState(
                    rows = rows,
                    fileBytes = bytes,
                    fileName = fileName
                )
            } catch (e: Exception) {
                AppLogger.e("DataVM", "Excel parse hatası: ${e.message}", e)
                _importState.value = _importState.value.copy(
                    error = "Excel dosyası okunamadı: ${e.message}"
                )
            }
        }
    }

    fun executeImport() {
        viewModelScope.launch {
            val state = _importState.value
            val fileBytes = state.fileBytes ?: return@launch

            val role = _uiState.value.user?.role
            val deptId = _uiState.value.user?.departmentId ?: if (role == com.unischeduler.domain.model.UserRole.ADMIN) 1 else return@launch

            AppLogger.i("DataVM", "Import başlatılıyor: ${state.rows.size} satır, deptId=$deptId, role=$role")
            _importState.value = _importState.value.copy(isImporting = true, error = null)

            importExcelUseCase(
                rows = state.rows,
                departmentId = deptId,
                fileBytes = fileBytes,
                fileName = state.fileName
            ).onSuccess { result ->
                AppLogger.i("DataVM", "Import başarılı: ${result.courses.size} ders, ${result.lecturers.size} hoca, ${result.errors.size} hata")
                _importState.value = _importState.value.copy(
                    isImporting = false,
                    importResult = result
                )
                loadData()
            }.onFailure { e ->
                AppLogger.e("DataVM", "Import başarısız: ${e.message}", e)
                _importState.value = _importState.value.copy(
                    isImporting = false,
                    error = e.message ?: "Import başarısız"
                )
            }
        }
    }

    suspend fun exportSchedule(): String? {
        return try {
            val state = _uiState.value
            val deptId = state.user?.departmentId ?: 1
            val assignments = scheduleRepository.getAssignmentsByDepartment(deptId)
            val config = scheduleRepository.getScheduleConfig(deptId) ?: return null
            exportScheduleUseCase(
                context = context,
                assignments = assignments,
                config = config,
                courses = state.courses,
                lecturers = state.lecturers,
                departmentCode = state.departments.find { it.id == deptId }?.code ?: "DEPT",
                semester = "2024-2025"
            ).getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun exportCredentials(): String? {
        return try {
            val state = _uiState.value
            val deptCode = state.departments.find { it.id == state.user?.departmentId }?.code ?: "DEPT"
            exportCredentialsUseCase(
                context = context,
                lecturers = state.lecturers,
                departmentCode = deptCode
            ).getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun refresh() = loadData()

    fun clearImportState() {
        _importState.value = ImportPreviewState()
    }

    fun addLecturer(fullName: String, title: String, departmentId: Int) {
        viewModelScope.launch {
            try {
                AppLogger.i("DataVM", "Hoca ekleniyor: $fullName, title=$title, deptId=$departmentId")
                val lecturer = lecturerRepository.upsertLecturer(
                    Lecturer(
                        fullName = fullName,
                        title = title,
                        departmentId = departmentId
                    )
                )
                AppLogger.i("DataVM", "Hoca eklendi: ${lecturer.fullName} (id=${lecturer.id})")
                loadData()
            } catch (e: Exception) {
                AppLogger.e("DataVM", "Hoca eklenemedi: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = "Hoca eklenemedi: ${e.message}")
            }
        }
    }

    fun updateLecturer(lecturer: Lecturer) {
        viewModelScope.launch {
            try {
                AppLogger.i("DataVM", "Hoca güncelleniyor: ${lecturer.fullName} (id=${lecturer.id})")
                lecturerRepository.upsertLecturer(lecturer)
                AppLogger.i("DataVM", "Hoca güncellendi: ${lecturer.fullName}")
                loadData()
            } catch (e: Exception) {
                AppLogger.e("DataVM", "Hoca güncellenemedi: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = "Hoca güncellenemedi: ${e.message}")
            }
        }
    }

    fun deleteLecturer(lecturerId: Int) {
        viewModelScope.launch {
            try {
                AppLogger.i("DataVM", "Hoca siliniyor: id=$lecturerId")
                lecturerRepository.deleteLecturer(lecturerId)
                AppLogger.i("DataVM", "Hoca silindi: id=$lecturerId")
                loadData()
            } catch (e: Exception) {
                AppLogger.e("DataVM", "Hoca silinemedi: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = "Hoca silinemedi: ${e.message}")
            }
        }
    }
}
