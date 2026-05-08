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
    fun `generatePassword produces 12-character string`() {
        val password = CredentialGenerator.generatePassword()
        assertEquals(12, password.length)
    }

    @Test
    fun `generatePassword always contains at least one of each category`() {
        val specials = setOf('!', '@', '#', '$', '%', '&', '*', '?', '+', '-', '_', '=')
        repeat(200) {
            val pwd = CredentialGenerator.generatePassword()
            assertTrue("'$pwd' missing uppercase", pwd.any { it.isUpperCase() })
            assertTrue("'$pwd' missing lowercase", pwd.any { it.isLowerCase() })
            assertTrue("'$pwd' missing digit",     pwd.any { it.isDigit() })
            assertTrue("'$pwd' missing special",   pwd.any { it in specials })
        }
    }

    @Test
    fun `generatePassword produces unique values`() {
        val passwords = (1..200).map { CredentialGenerator.generatePassword() }.toSet()
        // 12 char alphabet ~70+ → trivially distinct
        assertEquals("Duplicate passwords detected", 200, passwords.size)
    }

    @Test
    fun `generatePassword has no whitespace or ambiguous chars`() {
        repeat(100) {
            val pwd = CredentialGenerator.generatePassword()
            assertFalse("'$pwd' has whitespace",   pwd.any { it.isWhitespace() })
            assertFalse("'$pwd' has backtick",     pwd.contains('`'))
            assertFalse("'$pwd' has quote",        pwd.contains('"'))
        }
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
