package com.unischeduler.presentation.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.ChangeRequest
import com.unischeduler.domain.model.User
import com.unischeduler.domain.model.UserRole
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.LecturerRepository
import com.unischeduler.domain.repository.RequestRepository
import com.unischeduler.domain.usecase.request.ApproveRequestUseCase
import com.unischeduler.domain.usecase.request.CreateRequestUseCase
import com.unischeduler.domain.usecase.request.RejectRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequestUiState(
    val requests: List<ChangeRequest> = emptyList(),
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class RequestViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val requestRepository: RequestRepository,
    private val lecturerRepository: LecturerRepository,
    private val createRequestUseCase: CreateRequestUseCase,
    private val approveRequestUseCase: ApproveRequestUseCase,
    private val rejectRequestUseCase: RejectRequestUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestUiState())
    val uiState: StateFlow<RequestUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow<ChangeRequest?>(null)
    val detailState: StateFlow<ChangeRequest?> = _detailState.asStateFlow()

    fun loadRequests() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val user = authRepository.getCurrentUser()
                val requests = when (user?.role) {
                    UserRole.ADMIN -> requestRepository.getAllPendingRequests()
                    UserRole.LECTURER -> {
                        val lecturer = lecturerRepository.getLecturerByProfileId(user.id)
                        if (lecturer != null) requestRepository.getRequestsByLecturer(lecturer.id)
                        else emptyList()
                    }
                    else -> emptyList()
                }
                _uiState.value = RequestUiState(
                    requests = requests,
                    user = user,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadRequestDetail(requestId: Int) {
        viewModelScope.launch {
            try {
                _detailState.value = requestRepository.getRequestById(requestId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun createRequest(request: ChangeRequest) {
        viewModelScope.launch {
            try {
                createRequestUseCase(request).onSuccess {
                    _uiState.value = _uiState.value.copy(message = "Talep oluşturuldu")
                    loadRequests()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun approveAsAdmin(requestId: Int, note: String?) {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            approveRequestUseCase.approveAsAdmin(requestId, note, user?.id ?: "")
            loadRequests()
        }
    }

    fun rejectAsAdmin(requestId: Int, note: String) {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            rejectRequestUseCase.rejectAsAdmin(requestId, note, user?.id ?: "")
            loadRequests()
        }
    }
}
