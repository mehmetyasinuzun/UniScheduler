package com.unischeduler.util

import org.junit.Assert.*
import org.junit.Test

class UiStateTest {

    @Test
    fun `Idle is singleton`() {
        assertSame(UiState.Idle, UiState.Idle)
    }

    @Test
    fun `Loading is singleton`() {
        assertSame(UiState.Loading, UiState.Loading)
    }

    @Test
    fun `Success holds data`() {
        val state = UiState.Success("hello")
        assertEquals("hello", state.data)
    }

    @Test
    fun `Success with list data`() {
        val state = UiState.Success(listOf(1, 2, 3))
        assertEquals(3, state.data.size)
    }

    @Test
    fun `Error holds message and retryable defaults to true`() {
        val state = UiState.Error("Network failure.")
        assertEquals("Network failure.", state.message)
        assertTrue(state.retryable)
    }

    @Test
    fun `Error retryable can be set to false`() {
        val state = UiState.Error("Validation error.", retryable = false)
        assertFalse(state.retryable)
    }

    @Test
    fun `UiState sealed class hierarchy check`() {
        val states: List<UiState<String>> = listOf(
            UiState.Idle,
            UiState.Loading,
            UiState.Success("data"),
            UiState.Error("error")
        )
        assertEquals(4, states.size)
        assertTrue(states[0] is UiState.Idle)
        assertTrue(states[1] is UiState.Loading)
        assertTrue(states[2] is UiState.Success)
        assertTrue(states[3] is UiState.Error)
    }

    @Test
    fun `Success with Unit data for void operations`() {
        val state = UiState.Success(Unit)
        assertEquals(Unit, state.data)
    }
}
