package com.unischeduler.util

object TurkishCharUtils {

    private val charMap = mapOf(
        'ç' to 'c', 'Ç' to 'C',
        'ğ' to 'g', 'Ğ' to 'G',
        'ı' to 'i', 'I' to 'I',
        'İ' to 'I',
        'ö' to 'o', 'Ö' to 'O',
        'ş' to 's', 'Ş' to 'S',
        'ü' to 'u', 'Ü' to 'U'
    )

    fun toAscii(text: String): String {
        return text.map { charMap[it] ?: it }.joinToString("")
    }

    fun normalizeForUsername(text: String): String {
        return toAscii(text.trim().lowercase())
            .replace(Regex("[^a-z0-9.]"), ".")
            .replace(Regex("\\.{2,}"), ".")
            .trim('.')
    }
}
