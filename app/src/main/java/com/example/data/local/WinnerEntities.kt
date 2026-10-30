package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.BuildConfig

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["publicId"], unique = true),
        Index(value = ["displayName"]),
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val publicId: String,
    val displayName: String,
    val balancePaisa: Long,
    val reservedPaisa: Long = 0L,
    val isActive: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val avatarUri: String? = null,
)

@Entity(
    tableName = "deposit_requests",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["transactionId"], unique = true),
        Index(value = ["status"]),
    ]
)
data class DepositRequestEntity(
    @PrimaryKey val requestId: String,
    val userId: String,
    val userDisplayName: String,
    val amountPaisa: Long,
    val senderBkashNumber: String,
    val transactionId: String,
    val status: String,
    val isCredited: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "withdrawal_requests",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["status"]),
    ]
)
data class WithdrawalRequestEntity(
    @PrimaryKey val requestId: String,
    val userId: String,
    val userDisplayName: String,
    val amountPaisa: Long,
    val bankName: String,
    val accountHolderName: String,
    val bankAccountNumber: String,
    val status: String,
    val isReserved: Boolean = true,
    val isDeducted: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["createdAtEpochMs"]),
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey val entryId: String,
    val userId: String,
    val userDisplayName: String,
    val actionType: String,
    val deltaPaisa: Long,
    val balanceBeforePaisa: Long,
    val balanceAfterPaisa: Long,
    val referenceId: String,
    val note: String,
    val actorEmail: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "site_settings")
data class SiteSettingsEntity(
    @PrimaryKey val id: String = GLOBAL_ID,
    val bkashNumber: String = "01700000000",
    val productionBaseUrl: String = BuildConfig.DEFAULT_PRODUCTION_BASE_URL,
    val logoUri: String? = null,
    val mainBannerUri: String? = null,
    val promoBanner1Uri: String? = null,
    val promoBanner2Uri: String? = null,
    val promoBanner3Uri: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        const val GLOBAL_ID = "global"
    }
}

@Entity(tableName = "admin_accounts")
data class AdminAccountEntity(
    @PrimaryKey val id: String = PRIMARY_ADMIN_ID,
    val email: String,
    val passwordHashBase64: String,
    val passwordSaltBase64: String,
    val createdAtEpochMs: Long,
) {
    companion object {
        const val PRIMARY_ADMIN_ID = "primary_admin"
    }
}
