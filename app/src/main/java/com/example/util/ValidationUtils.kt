package com.example.util

import com.example.BuildConfig
import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Locale

object ValidationUtils {
    private val secureRandom = SecureRandom()
    // Unambiguous URL-safe alphanumeric alphabet for unpredictable public user IDs
    private const val PUBLIC_ID_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val PUBLIC_ID_LENGTH = 18

    private val bkashNumberRegex = Regex("^01[3-9][0-9]{8}$")
    private val bkashIntlRegex = Regex("^\\+?8801[3-9][0-9]{8}$")
    private val transactionIdRegex = Regex("^[A-Za-z0-9]{6,24}$")
    private val publicIdFormatRegex = Regex("^[A-Za-z0-9_\\-]{8,64}$")
    private val bankAccountRegex = Regex("^[A-Za-z0-9\\- ]{6,34}$")
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private val unconfiguredPlaceholderHosts = setOf(
        "winnerbalance.com",
        "www.winnerbalance.com",
        "your-domain.com",
        "www.your-domain.com",
        "example.com",
        "www.example.com",
    )

    private val loopbackHosts = setOf(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "10.0.2.2",
    )

    fun generateSecurePublicId(): String {
        val sb = StringBuilder(PUBLIC_ID_LENGTH)
        repeat(PUBLIC_ID_LENGTH) {
            sb.append(PUBLIC_ID_ALPHABET[secureRandom.nextInt(PUBLIC_ID_ALPHABET.length)])
        }
        return sb.toString()
    }

    fun generateRequestId(prefix: String): String {
        val token = StringBuilder(10)
        val chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        repeat(10) {
            token.append(chars[secureRandom.nextInt(chars.length)])
        }
        return "$prefix-$token"
    }

    fun validateDisplayName(name: String): Result<String> {
        val trimmed = name.trim()
        if (trimmed.length < 2) {
            return Result.failure(IllegalArgumentException("User name must be at least 2 characters."))
        }
        if (trimmed.length > 80) {
            return Result.failure(IllegalArgumentException("User name must be 80 characters or fewer."))
        }
        return Result.success(trimmed)
    }

    fun validateAndNormalizeBkashNumber(raw: String): Result<String> {
        val compact = raw.trim().replace(" ", "").replace("-", "")
        val normalized = when {
            bkashNumberRegex.matches(compact) -> compact
            bkashIntlRegex.matches(compact) -> "0" + compact.takeLast(10)
            else -> return Result.failure(
                IllegalArgumentException("Enter a valid 11-digit bKash number starting with 013–019 (e.g., 01712345678).")
            )
        }
        return Result.success(normalized)
    }

    fun validateTransactionId(raw: String): Result<String> {
        val normalized = raw.trim().uppercase(Locale.ENGLISH)
        if (!transactionIdRegex.matches(normalized)) {
            return Result.failure(
                IllegalArgumentException("Transaction ID must be 6–24 alphanumeric characters (no spaces or symbols).")
            )
        }
        return Result.success(normalized)
    }

    fun validateBankName(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.length < 2 || trimmed.length > 80) {
            return Result.failure(IllegalArgumentException("Bank name must be between 2 and 80 characters."))
        }
        return Result.success(trimmed)
    }

    fun validateAccountHolderName(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.length < 2 || trimmed.length > 80) {
            return Result.failure(IllegalArgumentException("Account holder name must be between 2 and 80 characters."))
        }
        return Result.success(trimmed)
    }

    fun validateBankAccountNumber(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (!bankAccountRegex.matches(trimmed)) {
            return Result.failure(
                IllegalArgumentException("Bank account number must be 6–34 digits or alphanumeric characters.")
            )
        }
        return Result.success(trimmed)
    }

    fun validateEmail(raw: String): Result<String> {
        val trimmed = raw.trim().lowercase(Locale.ENGLISH)
        if (!emailRegex.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Enter a valid administrator email address."))
        }
        return Result.success(trimmed)
    }

    /**
     * Validates a deployed HTTPS base URL for shareable user links.
     * Allows the actual deployed Cloud Run URLs (`*.run.app`) as well as configured HTTPS domains,
     * while rejecting localhost, loopback IPs, and unconfigured placeholder domains (`winnerbalance.com`).
     */
    fun validateProductionBaseUrl(raw: String): Result<String> {
        val trimmed = raw.trim().trimEnd('/')
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return Result.failure(
                IllegalArgumentException("Enter a valid HTTPS deployed URL (e.g., ${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}).")
            )
        }

        val scheme = uri.scheme?.lowercase(Locale.ENGLISH)
        val host = uri.host?.lowercase(Locale.ENGLISH)
        if (scheme != "https" || host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Base URL must start with https:// and include a valid deployed domain."))
        }

        if (host in loopbackHosts || host.endsWith(".local") || host.endsWith(".localhost") || !host.contains(".")) {
            return Result.failure(
                IllegalArgumentException("Public user links cannot use localhost or loopback addresses.")
            )
        }

        if (host in unconfiguredPlaceholderHosts || host.endsWith(".winnerbalance.com")) {
            return Result.failure(
                IllegalArgumentException("Do not use unconfigured domain winnerbalance.com. Use your active deployed URL (${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}).")
            )
        }

        return Result.success("$scheme://$host${if (uri.port != -1) ":${uri.port}" else ""}")
    }

    /**
     * Ensures any legacy or unconfigured stored base URL automatically resolves
     * to the actual working deployed URL (`BuildConfig.DEFAULT_PRODUCTION_BASE_URL`).
     */
    fun sanitizeConfiguredBaseUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return BuildConfig.DEFAULT_PRODUCTION_BASE_URL
        return validateProductionBaseUrl(raw).getOrElse { BuildConfig.DEFAULT_PRODUCTION_BASE_URL }
    }

    /**
     * Extracts a clean `publicId` from:
     * - Root query URL (`https://<deployed-host>/?u=ABC123`)
     * - Path URL (`https://<deployed-host>/u/ABC123`)
     * - Hash route (`https://<deployed-host>/#/u/ABC123`)
     * - Custom app deep-link (`winnerbalance://u/ABC123`)
     * - Relative query/path (`/?u=ABC123`, `/u/ABC123`) or bare public ID (`ABC123`)
     */
    fun extractPublicIdFromLinkOrInput(rawInput: String): Result<String> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a public user link or public ID."))
        }

        val queryCandidate = extractQueryUserParam(trimmed)
        val candidate = when {
            !queryCandidate.isNullOrBlank() -> queryCandidate
            trimmed.contains("#/u/") -> {
                trimmed.substringAfter("#/u/")
                    .substringBefore("?")
                    .substringBefore("&")
                    .trim('/')
            }
            trimmed.contains("/u/") -> {
                trimmed.substringAfter("/u/")
                    .substringBefore("?")
                    .substringBefore("#")
                    .trim('/')
            }
            trimmed.startsWith("winnerbalance://u/") -> {
                trimmed.removePrefix("winnerbalance://u/")
                    .substringBefore("?")
                    .substringBefore("#")
                    .trim('/')
            }
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> {
                return Result.failure(IllegalArgumentException("Link does not contain a user ID parameter (?u=...)."))
            }
            else -> trimmed.removePrefix("?u=").trim('/')
        }

        if (!publicIdFormatRegex.matches(candidate)) {
            return Result.failure(IllegalArgumentException("Invalid public user link format."))
        }
        return Result.success(candidate)
    }

    private fun extractQueryUserParam(input: String): String? {
        val queryPart = input.substringAfter("?", missingDelimiterValue = "")
            .substringBefore("#")
        if (queryPart.isEmpty()) return null
        for (pair in queryPart.split("&")) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val key = pair.substring(0, idx).lowercase(Locale.ENGLISH)
            if (key == "u" || key == "user" || key == "publicid") {
                val rawVal = pair.substring(idx + 1)
                return runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal).trim('/')
            }
        }
        return null
    }

    /**
     * Generates a direct, refresh-safe, 404-free public user link using the actual deployed URL:
     * `https://<deployed-host>/?u=<publicId>`
     */
    fun buildPublicUserUrl(productionBaseUrl: String, publicId: String): String {
        val cleanBase = sanitizeConfiguredBaseUrl(productionBaseUrl).trimEnd('/')
        return "$cleanBase/?u=$publicId"
    }
}
