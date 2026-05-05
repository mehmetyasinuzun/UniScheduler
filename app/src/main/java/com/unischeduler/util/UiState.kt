// Sealed UiState — covers Lab Task 1, 3 patterns
// retryable flag on Error enables "Retry" button in fragments (Task 3)
package com.unischeduler.util

sealed class UiState<out T> {
    object Idle    : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val retryable: Boolean = true) : UiState<Nothing>()
}
