package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic PBKDF2-HMAC-SHA256 password hashing for administrator authentication.
 * Compatible across Android API 24+ and never stores plaintext passwords.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private val secureRandom = SecureRandom()

    data class HashedPassword(
        val hashBase64: String,
        val saltBase64: String,
    )

    fun hashPassword(password: CharArray): HashedPassword {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = deriveKey(password, salt)
        return HashedPassword(
            hashBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
        )
    }

    fun verifyPassword(password: CharArray, expectedHashBase64: String, saltBase64: String): Boolean {
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            val actual = deriveKey(password, salt)
            MessageDigest.isEqual(actual, expected)
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
