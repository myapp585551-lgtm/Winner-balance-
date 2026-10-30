package com.example.data.repository

import android.net.Uri
import com.example.data.model.AdminSession
import com.example.data.model.AdminUserRecord
import com.example.data.model.DepositRecord
import com.example.data.model.LedgerRecord
import com.example.data.model.PublicPageState
import com.example.data.model.RequestStatus
import com.example.data.model.SiteSettingsModel
import com.example.data.model.WithdrawalRecord
import com.example.util.MediaSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WinnerDataSource {
    val isOnlineFlow: Flow<Boolean>
    val activeAdminSession: StateFlow<AdminSession?>
    val siteSettingsFlow: Flow<SiteSettingsModel>

    suspend fun ensureColdBootstrapSeeded(): Result<Unit> = Result.success(Unit)

    fun signOutAdmin()

    fun observePublicUserPage(publicIdRaw: String): Flow<PublicPageState>

    suspend fun submitBkashDepositRequest(
        publicIdRaw: String,
        amountBdtRaw: String,
        senderBkashRaw: String,
        transactionIdRaw: String,
    ): Result<DepositRecord>

    suspend fun submitBankWithdrawalRequest(
        publicIdRaw: String,
        amountBdtRaw: String,
        bankNameRaw: String,
        accountHolderNameRaw: String,
        bankAccountNumberRaw: String,
    ): Result<WithdrawalRecord>

    fun observeAdminUsers(session: AdminSession?): Flow<List<AdminUserRecord>>
    fun observeAdminDeposits(session: AdminSession?): Flow<List<DepositRecord>>
    fun observeAdminWithdrawals(session: AdminSession?): Flow<List<WithdrawalRecord>>
    fun observeAdminLedger(session: AdminSession?): Flow<List<LedgerRecord>>

    suspend fun createUser(
        session: AdminSession?,
        displayNameRaw: String,
        initialBalanceBdtRaw: String,
        initialAvatarUri: Uri? = null,
    ): Result<AdminUserRecord>

    suspend fun updateUserDetailsAndBalance(
        session: AdminSession?,
        userId: String,
        newDisplayNameRaw: String,
        newBalanceBdtRaw: String,
        auditReasonRaw: String = "",
    ): Result<AdminUserRecord>

    suspend fun setUserLinkActive(
        session: AdminSession?,
        userId: String,
        isActive: Boolean,
    ): Result<AdminUserRecord>

    suspend fun regenerateUserPublicLink(
        session: AdminSession?,
        userId: String,
    ): Result<AdminUserRecord>

    suspend fun deleteUserRecord(
        session: AdminSession?,
        userId: String,
    ): Result<Unit>

    suspend fun updateDepositStatus(
        session: AdminSession?,
        requestId: String,
        targetStatus: RequestStatus,
    ): Result<DepositRecord>

    suspend fun updateWithdrawalStatus(
        session: AdminSession?,
        requestId: String,
        targetStatus: RequestStatus,
    ): Result<WithdrawalRecord>

    suspend fun updateBkashPaymentNumber(
        session: AdminSession?,
        bkashNumberRaw: String,
    ): Result<SiteSettingsModel>

    suspend fun updateProductionBaseUrl(
        session: AdminSession?,
        baseUrlRaw: String,
    ): Result<SiteSettingsModel>

    suspend fun saveCustomMediaImage(
        session: AdminSession?,
        slot: MediaSlot,
        pickedUri: Uri,
    ): Result<SiteSettingsModel>

    suspend fun saveMediaUriDirectly(
        session: AdminSession?,
        slot: MediaSlot,
        uriString: String?,
    ): Result<SiteSettingsModel>

    suspend fun saveUserAvatarImage(
        session: AdminSession?,
        userId: String,
        pickedUri: Uri,
    ): Result<AdminUserRecord>

    suspend fun saveUserAvatarUriDirectly(
        session: AdminSession?,
        userId: String,
        avatarUri: String?,
    ): Result<AdminUserRecord>
}
