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
    fun `generatePassword uses only A-Z and 1-6`() {
        val allowed = (('A'..'Z') + ('1'..'6')).toSet()
        repeat(200) {
            val pwd = CredentialGenerator.generatePassword()
            for (c in pwd) {
                assertTrue("'$pwd' contains illegal char '$c'", c in allowed)
            }
        }
    }

    @Test
    fun `generatePassword excludes 0 7 8 9 and lowercase`() {
        val forbidden = setOf('0', '7', '8', '9') + ('a'..'z').toSet()
        repeat(100) {
            val pwd = CredentialGenerator.generatePassword()
            for (c in pwd) {
                assertFalse("'$pwd' contains forbidden char '$c'", c in forbidden)
            }
        }
    }

    @Test
    fun `generatePassword produces varied values across calls`() {
        // 32^6 ≈ 1 billion possible passwords. 200 calls should give
        // close-to-distinct results; we accept up to 1 collision.
        val passwords = (1..200).map { CredentialGenerator.generatePassword() }.toSet()
        assertTrue("Suspicious collision rate: ${200 - passwords.size} duplicates",
            passwords.size >= 199)
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
