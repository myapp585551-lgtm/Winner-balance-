package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WinnerDao {
    // --- Admin Account ---
    @Query("SELECT * FROM admin_accounts WHERE id = :id LIMIT 1")
    suspend fun getAdminAccount(id: String = AdminAccountEntity.PRIMARY_ADMIN_ID): AdminAccountEntity?

    @Query("SELECT COUNT(*) FROM admin_accounts")
    fun observeAdminCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdminAccount(admin: AdminAccountEntity)

    // --- Site Settings ---
    @Query("SELECT * FROM site_settings WHERE id = :id LIMIT 1")
    fun observeSiteSettings(id: String = SiteSettingsEntity.GLOBAL_ID): Flow<SiteSettingsEntity?>

    @Query("SELECT * FROM site_settings WHERE id = :id LIMIT 1")
    suspend fun getSiteSettingsOnce(id: String = SiteSettingsEntity.GLOBAL_ID): SiteSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSiteSettings(settings: SiteSettingsEntity)

    // --- Users ---
    @Query("SELECT * FROM users ORDER BY createdAtEpochMs DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE publicId = :publicId LIMIT 1")
    fun observeUserByPublicId(publicId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE publicId = :publicId LIMIT 1")
    suspend fun getUserByPublicIdOnce(publicId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserByIdOnce(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)

    // --- Deposits (bKash Only) ---
    @Query("SELECT * FROM deposit_requests ORDER BY createdAtEpochMs DESC")
    fun observeAllDeposits(): Flow<List<DepositRequestEntity>>

    @Query("SELECT * FROM deposit_requests WHERE userId = :userId ORDER BY createdAtEpochMs DESC")
    fun observeDepositsForUser(userId: String): Flow<List<DepositRequestEntity>>

    @Query("SELECT * FROM deposit_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getDepositByIdOnce(requestId: String): DepositRequestEntity?

    @Query("SELECT * FROM deposit_requests WHERE transactionId = :trxId LIMIT 1")
    suspend fun getDepositByTrxIdOnce(trxId: String): DepositRequestEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeposit(deposit: DepositRequestEntity)

    @Update
    suspend fun updateDeposit(deposit: DepositRequestEntity)

    @Query("UPDATE deposit_requests SET userDisplayName = :newName WHERE userId = :userId")
    suspend fun updateDepositUserNames(userId: String, newName: String)

    // --- Withdrawals (Bank Account Only) ---
    @Query("SELECT * FROM withdrawal_requests ORDER BY createdAtEpochMs DESC")
    fun observeAllWithdrawals(): Flow<List<WithdrawalRequestEntity>>

    @Query("SELECT * FROM withdrawal_requests WHERE userId = :userId ORDER BY createdAtEpochMs DESC")
    fun observeWithdrawalsForUser(userId: String): Flow<List<WithdrawalRequestEntity>>

    @Query("SELECT * FROM withdrawal_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getWithdrawalByIdOnce(requestId: String): WithdrawalRequestEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWithdrawal(withdrawal: WithdrawalRequestEntity)

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalRequestEntity)

    @Query("UPDATE withdrawal_requests SET userDisplayName = :newName WHERE userId = :userId")
    suspend fun updateWithdrawalUserNames(userId: String, newName: String)

    // --- Ledger & Audit History ---
    @Query("SELECT * FROM ledger_entries ORDER BY createdAtEpochMs DESC")
    fun observeAllLedgerEntries(): Flow<List<LedgerEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity)

    @Query("UPDATE ledger_entries SET userDisplayName = :newName WHERE userId = :userId")
    suspend fun updateLedgerUserNames(userId: String, newName: String)
}
