// PasswordHasher — DEPRECATED. Kept for backward compatibility during migration.
// New authentication uses Supabase GoTrue (Auth) which handles hashing internally.
// This file should be removed once all users have been migrated to Supabase Auth.
package com.unischeduler.util

import java.security.MessageDigest

@Deprecated("Use Supabase GoTrue for authentication. This is kept only for migration.")
object PasswordHasher {

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun verify(plain: String, hash: String): Boolean =
        sha256(plain) == hash
}
