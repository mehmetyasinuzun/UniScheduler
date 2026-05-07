// Auto-generates username and initial password for imported lecturers.
// Username rule: lowercase(firstName)_lowercase(lastName), Turkish chars normalized.
// Password  rule: 6-char random alphanumeric.
package com.unischeduler.util

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

    fun generateUsername(firstName: String, lastName: String): String {
        val first = normalize(stripTitle(firstName))
        val last  = normalize(stripTitle(lastName))
        return "${first}_${last}"
    }

    fun generatePassword(): String {
        val chars = ('A'..'Z') + ('0'..'9')
        return (1..6).map { chars.random() }.joinToString("")
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
            .lowercase()
            .filter { it.isLetter() || it == '_' }
}
