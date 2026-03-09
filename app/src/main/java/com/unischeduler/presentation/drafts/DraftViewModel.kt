package com.unischeduler.presentation.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleDraft
import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.DraftRepository
import com.unischeduler.domain.repository.ScheduleRepository
import com.unischeduler.domain.usecase.draft.ApproveDraftUseCase
import com.unischeduler.domain.usecase.draft.CreateDraftUseCase
import com.unischeduler.domain.usecase.draft.RejectDraftUseCase
import com.unischeduler.domain.usecase.draft.SubmitDraftUseCase
import com.unischeduler.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DraftListUiState(
    val drafts: List<ScheduleDraft> = emptyList(),
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val draftRepository: DraftRepository,
    private val createDraftUseCase: CreateDraftUseCase,
    private val submitDraftUseCase: SubmitDraftUseCase,
    private val approveDraftUseCase: ApproveDraftUseCase,
    private val rejectDraftUseCase: RejectDraftUseCase
) : ViewModel() {

    private val _listState = MutableStateFlow(DraftListUiState())
    val listState: StateFlow<DraftListUiState> = _listState.asStateFlow()

    private val _editState = MutableStateFlow<UiState<ScheduleDraft>>(UiState.Loading)
    val editState: StateFlow<UiState<ScheduleDraft>> = _editState.asStateFlow()

    private val _currentAssignments = MutableStateFlow<List<ScheduleAssignment>>(emptyList())
    val currentAssignments: StateFlow<List<ScheduleAssignment>> = _currentAssignments.asStateFlow()

    init {
        loadDrafts()
    }

    private fun loadDrafts() {
        viewModelScope.launch {
            _listState.value = _listState.value.copy(isLoading = true, error = null)
            try {
                val user = authRepository.getCurrentUser()
                val deptId = user?.departmentId ?: 1
                val drafts = when (user?.role) {
                    UserRole.ADMIN -> draftRepository.getPendingDrafts()
                    else -> draftRepository.getDraftsByDepartment(deptId)
                }
                _listState.value = DraftListUiState(
                    drafts = drafts,
                    user = user,
                    isLoading = false
                )
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadDraft(draftId: Int) {
        viewModelScope.launch {
            _editState.value = UiState.Loading
            try {
                if (draftId == 0) {
                    val user = authRepository.getCurrentUser()
                    val deptId = user?.departmentId ?: 1
                    val assignments = scheduleRepository.getAssignmentsByDepartment(deptId)
                    _currentAssignments.value = assignments
                    _editState.value = UiState.Success(
                        ScheduleDraft(
                            departmentId = deptId,
                            createdBy = user?.id ?: "",
                            title = "",
                            assignments = assignments
                        )
                    )
                } else {
                    val draft = draftRepository.getDraftById(draftId)
                    if (draft != null) {
                        _currentAssignments.value = draft.assignments
                        _editState.value = UiState.Success(draft)
                    } else {
                        _editState.value = UiState.Error("Taslak bulunamadı")
                    }
                }
            } catch (e: Exception) {
                _editState.value = UiState.Error(e.message ?: "Hata")
            }
        }
    }

    fun saveDraft(title: String) {
        viewModelScope.launch {
            try {
                val current = (_editState.value as? UiState.Success)?.data ?: return@launch
                createDraftUseCase(
                    departmentId = current.departmentId,
                    createdBy = current.createdBy,
                    title = title,
                    assignments = _currentAssignments.value,
                    softScore = current.softScore
                ).onSuccess { saved ->
                    _editState.value = UiState.Success(saved)
                }.onFailure { e ->
                    _editState.value = UiState.Error(e.message ?: "Kayıt başarısız")
                }
            } catch (e: Exception) {
                _editState.value = UiState.Error(e.message ?: "Kayıt başarısız")
            }
        }
    }

    fun submitDraft(draftId: Int) {
        viewModelScope.launch {
            try {
                submitDraftUseCase(draftId)
                loadDrafts()
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(error = e.message)
            }
        }
    }

    fun approveDraft(draftId: Int, note: String?) {
        viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser()
                approveDraftUseCase(draftId, note, user?.id ?: "")
                loadDrafts()
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(error = e.message)
            }
        }
    }

    fun rejectDraft(draftId: Int, note: String?) {
        viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser()
                rejectDraftUseCase(draftId, note ?: "", user?.id ?: "")
                loadDrafts()
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(error = e.message)
            }
        }
    }
}
