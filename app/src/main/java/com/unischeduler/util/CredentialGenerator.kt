package com.unischeduler.util

import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class Credentials(
    val username: String,
    val email: String,
    val password: String
)

@Singleton
class CredentialGenerator @Inject constructor() {

    private val random = SecureRandom()

    fun generate(fullName: String, emailDomain: String = "uni.edu.tr"): Credentials {
        val parts = fullName.trim().split(Regex("\\s+"))
        val normalized = parts.joinToString(".") { TurkishCharUtils.normalizeForUsername(it) }
        val username = normalized.ifBlank { "user.${random.nextInt(9999)}" }
        val email = "$username@$emailDomain"
        val password = generatePassword()

        return Credentials(
            username = username,
            email = email,
            password = password
        )
    }

    private fun generatePassword(): String {
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val digits = "0123456789"
        val all = upper + lower + digits

        return buildString {
            // Minimum 1 upper, 1 lower, 1 digit
            append(upper[random.nextInt(upper.length)])
            append(lower[random.nextInt(lower.length)])
            append(digits[random.nextInt(digits.length)])
            repeat(5) {
                append(all[random.nextInt(all.length)])
            }
        }.toList().shuffled(random).joinToString("")
    }
}
