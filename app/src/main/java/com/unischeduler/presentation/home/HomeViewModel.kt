package com.unischeduler.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.model.User
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.DraftRepository
import com.unischeduler.domain.repository.RequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val user: User? = null,
    val pendingDraftCount: Int = 0,
    val pendingRequestCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val draftRepository: DraftRepository,
    private val requestRepository: RequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val user = authRepository.getCurrentUser()
                val pendingDrafts = draftRepository.getPendingDrafts().size
                val pendingRequests = requestRepository.getAllPendingRequests().size

                _uiState.value = HomeUiState(
                    user = user,
                    pendingDraftCount = pendingDrafts,
                    pendingRequestCount = pendingRequests,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun refresh() = loadHomeData()
}
