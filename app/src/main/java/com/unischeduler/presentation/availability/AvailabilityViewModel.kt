package com.unischeduler.presentation.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.LecturerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

@HiltViewModel
class AvailabilityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val lecturerRepository: LecturerRepository,
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _gridState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val gridState: StateFlow<Map<String, Boolean>> = _gridState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentLecturerId: Int? = null

    init {
        loadAvailability()
    }

    private fun loadAvailability() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = authRepository.getCurrentUser() ?: return@launch
                val lecturer = lecturerRepository.getLecturerByProfileId(user.id) ?: return@launch
                currentLecturerId = lecturer.id

                // Burada normalde 'lecturer_availability' tablosundan cekilir
                // Şimdilik test amaçlı bütün kutuları dolu (yeşil) varsayıyoruz:
                val mockMap = mutableMapOf<String, Boolean>()
                
                // TODO: Supabase postgrest sorgusu burada olacak
                // val data = supabase.postgrest.from("availability").select() vb...
                
                _gridState.value = mockMap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleAvailability(key: String, isAvailable: Boolean) {
        val lecturerId = currentLecturerId ?: return
        viewModelScope.launch {
            val map = _gridState.value.toMutableMap()
            map[key] = isAvailable
            _gridState.value = map
            
            // TODO: Anlık olarak veya Save butonuyla Supabase'e yazılacak
        }
    }
}
