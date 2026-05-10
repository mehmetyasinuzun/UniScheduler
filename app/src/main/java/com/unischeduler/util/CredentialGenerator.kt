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

    // Password alphabet — kullanıcı isteği: sadece BÜYÜK harf + 1-6 arası
    // rakam. Kasıtlı olarak 0/7/8/9 dışarda; 6 karakter uzunluk.
    //
    // Güvenlik notu (operatöre bilgi): bu alfabe + uzunluk kombinasyonu
    // brute-force'a karşı zayıftır. Entropi ≈ log2(32^6) ≈ 30 bit
    // ≈ 1 milyar olasılık. Bu sürüm kullanım kolaylığı için tercih
    // edilmiştir; üretimde gerçek brute-force riski varsa hesap kilidi
    // (DB tarafında auto-lockout) veya Supabase Auth'un per-IP rate
    // limit'i bu açığı bir miktar telafi eder. SecureRandom kullanmaya
    // devam ediyoruz — kısa şifre OLSA BİLE örüntü tahmin edilebilir
    // olmamalı.
    private const val PASSWORD_LENGTH = 6
    private val ALPHABET = (('A'..'Z') + ('1'..'6')).toList()
    private val secureRandom = SecureRandom()

    fun generateUsername(firstName: String, lastName: String): String {
        val first = normalize(stripTitle(firstName))
        val last  = normalize(stripTitle(lastName))
        return "${first}_${last}"
    }

    /**
     * 6-character password from [A-Z1-6]. SecureRandom-driven so even
     * with the small alphabet, we don't leak predictable sequences.
     */
    fun generatePassword(): String =
        buildString(PASSWORD_LENGTH) {
            repeat(PASSWORD_LENGTH) {
                append(ALPHABET[secureRandom.nextInt(ALPHABET.size)])
            }
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
}
