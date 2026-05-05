package com.unischeduler.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMessagesTest {

    @Test
    fun `network errors return friendly message`() {
        val msg1 = ErrorMessages.map(UnknownHostException("Unable to resolve host"))
        assertEquals("No internet connection. Please check your network.", msg1)

        val msg2 = ErrorMessages.map(ConnectException("Connection refused"))
        assertEquals("No internet connection. Please check your network.", msg2)

        val msg3 = ErrorMessages.map(SocketTimeoutException("Read timed out"))
        assertEquals("Connection timed out. Please try again.", msg3)
    }

    @Test
    fun `auth errors return friendly message`() {
        val msg = ErrorMessages.map(Exception("Invalid login credentials"))
        assertEquals("Invalid username or password.", msg)
    }

    @Test
    fun `validation errors pass through`() {
        val msg = ErrorMessages.map(IllegalArgumentException("First and last name are required."))
        assertEquals("First and last name are required.", msg)
    }

    @Test
    fun `state errors pass through`() {
        val msg = ErrorMessages.map(IllegalStateException("Session error. Please log in again."))
        assertEquals("Session error. Please log in again.", msg)
    }

    @Test
    fun `duplicate key returns friendly message`() {
        val msg = ErrorMessages.map(Exception("duplicate key value violates unique constraint"))
        assertEquals("This record already exists. Please use a different value.", msg)
    }

    @Test
    fun `foreign key violation returns friendly message`() {
        val msg = ErrorMessages.map(Exception("violates foreign key constraint"))
        assertEquals("Cannot complete this action — related records exist.", msg)
    }

    @Test
    fun `RLS error returns friendly message`() {
        val msg = ErrorMessages.map(Exception("new row violates row-level security policy"))
        assertEquals("You don't have permission to perform this action.", msg)
    }

    @Test
    fun `JWT expired returns friendly message`() {
        val msg = ErrorMessages.map(Exception("JWT expired"))
        assertEquals("Your session has expired. Please log in again.", msg)
    }

    @Test
    fun `unknown error falls back to exception message`() {
        val msg = ErrorMessages.map(Exception("something weird happened"))
        assertEquals("something weird happened", msg)
    }

    @Test
    fun `null message returns generic fallback`() {
        val msg = ErrorMessages.map(Exception())
        assertTrue(msg.contains("unexpected error", ignoreCase = true))
    }
}
