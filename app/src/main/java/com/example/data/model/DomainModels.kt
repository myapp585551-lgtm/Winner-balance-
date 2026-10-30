package com.example.data.model

import com.example.BuildConfig

enum class RequestStatus(val label: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    val isTerminal: Boolean
        get() = this == APPROVED || this == REJECTED

    companion object {
        fun fromStored(value: String): RequestStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}

enum class LedgerActionType(val label: String) {
    USER_CREATED("User Account Created"),
    ADMIN_BALANCE_ADJUSTED("Manual Balance Update"),
    DEPOSIT_APPROVED("bKash Deposit Approved"),
    WITHDRAWAL_RESERVED("Withdrawal Reserved"),
    WITHDRAWAL_APPROVED("Bank Withdrawal Approved"),
    WITHDRAWAL_RELEASED("Withdrawal Reservation Released");

    companion object {
        fun fromStored(value: String): LedgerActionType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ADMIN_BALANCE_ADJUSTED
        }
    }
}

data class AdminSession(
    val email: String,
    val sessionToken: String,
    val signedInAtEpochMs: Long,
)

/**
 * Admin-only full user record (includes internal database ID and administrative flags).
 */
data class AdminUserRecord(
    val id: String,
    val publicId: String,
    val displayName: String,
    val balancePaisa: Long,
    val reservedPaisa: Long,
    val isActive: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val avatarUri: String? = null,
) {
    val availablePaisa: Long
        get() = (balancePaisa - reservedPaisa).coerceAtLeast(0L)
}

/**
 * Strictly scoped public projection of a user page (omits internal database ID).
 */
data class PublicUserView(
    val publicId: String,
    val displayName: String,
    val balancePaisa: Long,
    val reservedPaisa: Long,
    val updatedAtEpochMs: Long,
    val avatarUri: String? = null,
) {
    val availablePaisa: Long
        get() = (balancePaisa - reservedPaisa).coerceAtLeast(0L)
}

data class SiteSettingsModel(
    val bkashNumber: String = "01700000000",
    val productionBaseUrl: String = BuildConfig.DEFAULT_PRODUCTION_BASE_URL,
    val logoUri: String? = null,
    val mainBannerUri: String? = null,
    val promoBanner1Uri: String? = null,
    val promoBanner2Uri: String? = null,
    val promoBanner3Uri: String? = null,
    val updatedAtEpochMs: Long = 0L,
)

data class DepositRecord(
    val requestId: String,
    val userId: String,
    val userDisplayName: String,
    val amountPaisa: Long,
    val senderBkashNumber: String,
    val transactionId: String,
    val status: RequestStatus,
    val isCredited: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class WithdrawalRecord(
    val requestId: String,
    val userId: String,
    val userDisplayName: String,
    val amountPaisa: Long,
    val bankName: String,
    val accountHolderName: String,
    val bankAccountNumber: String,
    val status: RequestStatus,
    val isReserved: Boolean,
    val isDeducted: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class LedgerRecord(
    val entryId: String,
    val userId: String,
    val userDisplayName: String,
    val actionType: LedgerActionType,
    val deltaPaisa: Long,
    val balanceBeforePaisa: Long,
    val balanceAfterPaisa: Long,
    val referenceId: String,
    val note: String,
    val actorEmail: String,
    val createdAtEpochMs: Long,
)

sealed interface PublicPageState {
    data object Loading : PublicPageState
    data class Active(
        val user: PublicUserView,
        val settings: SiteSettingsModel,
        val deposits: List<DepositRecord>,
        val withdrawals: List<WithdrawalRecord>,
    ) : PublicPageState
    data class Disabled(val settings: SiteSettingsModel) : PublicPageState
    data class NotFound(val settings: SiteSettingsModel) : PublicPageState
    data class Error(val message: String) : PublicPageState
}
