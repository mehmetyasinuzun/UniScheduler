// Auto-generates username and initial password for imported lecturers.
// Username rule: lowercase(firstName)_lowercase(lastName), Turkish chars normalized.
// Password  rule: 12-char cryptographically secure random — at least one of
//                 each: uppercase, lowercase, digit, special char. Generated
//                 with java.security.SecureRandom (NOT kotlin.random.Random).
package com.unischeduler.util

import java.security.SecureRandom
import java.util.Locale

object CredentialGenerator {

    private val TURKISH_MAP = mapOf(
        'ş' to 's', 'Ş' to 's',
        'ç' to 'c', 'Ç' to 'c',
        'ğ' to 'g', 'Ğ' to 'g',
        'ü' to 'u', 'Ü' to 'u',
        'ö' to 'o', 'Ö' to 'o',
        'ı' to 'i', 'İ' to 'i'
    )

    private val ACADEMIC_TITLES = setOf(
        "Prof.", "Assoc. Prof.", "Assist. Prof.", "Dr.", "Res. Asst.", "Lect."
    )

    private const val PASSWORD_LENGTH = 12
    private val UPPER   = ('A'..'Z').toList()
    private val LOWER   = ('a'..'z').toList()
    private val DIGIT   = ('0'..'9').toList()
    // Görsel karışıklık yapan karakterleri (backtick, quote, ters slash) dışarda
    // bıraktık; "şifremi yanlış mı yazdım?" desteğini azaltır.
    private val SPECIAL = listOf('!', '@', '#', '$', '%', '&', '*', '?', '+', '-', '_', '=')
    private val ALL     = UPPER + LOWER + DIGIT + SPECIAL

    private val secureRandom = SecureRandom()

    fun generateUsername(firstName: String, lastName: String): String {
        val first = normalize(stripTitle(firstName))
        val last  = normalize(stripTitle(lastName))
        return "${first}_${last}"
    }

    /**
     * Cryptographically strong 12-char password. Includes at least one
     * uppercase, lowercase, digit and special. Order is shuffled with
     * SecureRandom so the position of each category is unpredictable.
     */
    fun generatePassword(): String {
        val chars = mutableListOf<Char>()
        chars += UPPER.random(secureRandom)
        chars += LOWER.random(secureRandom)
        chars += DIGIT.random(secureRandom)
        chars += SPECIAL.random(secureRandom)
        repeat(PASSWORD_LENGTH - chars.size) {
            chars += ALL[secureRandom.nextInt(ALL.size)]
        }
        // Fisher-Yates shuffle with SecureRandom
        for (i in chars.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return chars.joinToString("")
    }

    // Strips academic title prefix from a name string if present
    fun stripTitle(name: String): String {
        var result = name.trim()
        for (title in ACADEMIC_TITLES) {
            if (result.startsWith(title, ignoreCase = true)) {
                result = result.removePrefix(title).trim()
                break
            }
        }
        return result
    }

    private fun normalize(text: String): String =
        text.map { TURKISH_MAP[it] ?: it }
            .joinToString("")
            .lowercase(Locale.ROOT)
            .filter { it.isLetter() || it == '_' }

    private fun <T> List<T>.random(random: SecureRandom): T = this[random.nextInt(size)]
}
