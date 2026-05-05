package com.unischeduler.util

import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `sha256 produces consistent 64-char hex hash`() {
        val hash = PasswordHasher.sha256("Admin123")
        assertEquals(64, hash.length)
        // Same input must always produce same output
        assertEquals(hash, PasswordHasher.sha256("Admin123"))
    }

    @Test
    fun `sha256 produces different hashes for different inputs`() {
        val h1 = PasswordHasher.sha256("password1")
        val h2 = PasswordHasher.sha256("password2")
        assertNotEquals(h1, h2)
    }

    @Test
    fun `verify returns true for matching password`() {
        val hash = PasswordHasher.sha256("TestPass")
        assertTrue(PasswordHasher.verify("TestPass", hash))
    }

    @Test
    fun `verify returns false for wrong password`() {
        val hash = PasswordHasher.sha256("correct")
        assertFalse(PasswordHasher.verify("wrong", hash))
    }

    @Test
    fun `sha256 handles empty string`() {
        val hash = PasswordHasher.sha256("")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `sha256 handles Turkish characters`() {
        val hash = PasswordHasher.sha256("şifre123")
        assertEquals(64, hash.length)
        // Consistent hashing with Turkish chars
        assertEquals(hash, PasswordHasher.sha256("şifre123"))
    }

    @Test
    fun `sha256 is case-sensitive`() {
        assertNotEquals(
            PasswordHasher.sha256("admin"),
            PasswordHasher.sha256("Admin")
        )
    }
}
