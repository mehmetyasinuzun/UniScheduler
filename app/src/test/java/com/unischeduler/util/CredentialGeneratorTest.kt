package com.unischeduler.util

import org.junit.Assert.*
import org.junit.Test

class CredentialGeneratorTest {

    @Test
    fun `generateUsername creates lowercase first_last format`() {
        val username = CredentialGenerator.generateUsername("Ahmet", "Yilmaz")
        assertEquals("ahmet_yilmaz", username)
    }

    @Test
    fun `generateUsername normalizes Turkish characters`() {
        val username = CredentialGenerator.generateUsername("Ömer", "Şahin")
        assertEquals("omer_sahin", username)
    }

    @Test
    fun `generateUsername handles capital Turkish I`() {
        val username = CredentialGenerator.generateUsername("İbrahim", "Güneş")
        assertEquals("ibrahim_gunes", username)
    }

    @Test
    fun `generateUsername handles dotless i`() {
        val username = CredentialGenerator.generateUsername("Sıla", "Çağlar")
        assertEquals("sila_caglar", username)
    }

    @Test
    fun `generateUsername strips academic title prefix`() {
        val username = CredentialGenerator.generateUsername("Prof. Ahmet", "Yilmaz")
        assertEquals("ahmet_yilmaz", username)
    }

    @Test
    fun `generateUsername handles Dr prefix`() {
        val username = CredentialGenerator.generateUsername("Dr. Fatma", "Kaya")
        assertEquals("fatma_kaya", username)
    }

    @Test
    fun `generatePassword produces 6-character string`() {
        val password = CredentialGenerator.generatePassword()
        assertEquals(6, password.length)
    }

    @Test
    fun `generatePassword contains only alphanumeric chars`() {
        repeat(100) {
            val password = CredentialGenerator.generatePassword()
            assertTrue("Password '$password' contains invalid chars",
                password.all { it.isLetterOrDigit() })
        }
    }

    @Test
    fun `generatePassword produces different values each call`() {
        val passwords = (1..50).map { CredentialGenerator.generatePassword() }.toSet()
        // With 62^6 possibilities, 50 calls should almost never produce duplicates
        assertTrue("Too many duplicate passwords", passwords.size > 40)
    }

    @Test
    fun `stripTitle removes Prof prefix`() {
        assertEquals("Ahmet", CredentialGenerator.stripTitle("Prof. Ahmet"))
    }

    @Test
    fun `stripTitle removes Lect prefix`() {
        assertEquals("Elif", CredentialGenerator.stripTitle("Lect. Elif"))
    }

    @Test
    fun `stripTitle leaves name without title unchanged`() {
        assertEquals("Hasan", CredentialGenerator.stripTitle("Hasan"))
    }

    @Test
    fun `stripTitle handles trimming`() {
        assertEquals("Zeynep", CredentialGenerator.stripTitle("  Zeynep  "))
    }

    @Test
    fun `generateUsername filters non-letter characters except underscore`() {
        val username = CredentialGenerator.generateUsername("Ali-123", "Gök çe")
        // Non-letters filtered, Turkish chars normalized
        assertEquals("ali_gokce", username)
    }
}
