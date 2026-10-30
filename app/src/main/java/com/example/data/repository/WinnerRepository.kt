package com.example.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AdminAccountEntity
import com.example.data.local.DepositRequestEntity
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.UserEntity
import com.example.data.local.WinnerDatabase
import com.example.data.local.WithdrawalRequestEntity
import com.example.data.model.AdminSession
import com.example.data.model.AdminUserRecord
import com.example.data.model.DepositRecord
import com.example.data.model.LedgerActionType
import com.example.data.model.LedgerRecord
import com.example.data.model.PublicPageState
import com.example.data.model.PublicUserView
import com.example.data.model.RequestStatus
import com.example.data.model.SiteSettingsModel
import com.example.data.model.WithdrawalRecord
import com.example.util.ConnectivityChecker
import com.example.util.CurrencyFormatter
import com.example.util.MediaSlot
import com.example.util.MediaStorageHelper
import com.example.util.OfflineOperationException
import com.example.util.PasswordHasher
import com.example.util.ValidationUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Transactional repository enforcing:
 * 1. Online-only operation (`ConnectivityChecker`) — blocks all writes/approvals when offline.
 * 2. Strict administrator session authorization (`AdminSession`) — blocks unauthorized admin reads/writes.
 * 3. Atomic ACID balance transactions (`withTransaction`) — prevents duplicate deposit credits,
 *    duplicate withdrawal deductions, concurrent overdrafts, and handles pending withdrawal reservations.
 */
class WinnerRepository(
    private val database: WinnerDatabase,
    private val connectivityChecker: ConnectivityChecker,
    private val mediaStorageHelper: MediaStorageHelper?,
) : WinnerDataSource {
    private val dao = database.winnerDao()

    private val _activeAdminSession = MutableStateFlow<AdminSession?>(null)
    override val activeAdminSession: StateFlow<AdminSession?> = _activeAdminSession.asStateFlow()

    override val isOnlineFlow: Flow<Boolean> = connectivityChecker.isOnlineFlow

    val isAdminInitializedFlow: Flow<Boolean> = dao.observeAdminCount().map { count -> count > 0 }

    override val siteSettingsFlow: Flow<SiteSettingsModel> = dao.observeSiteSettings().map { entity ->
        entity?.toDomain() ?: SiteSettingsModel()
    }

    private fun requireOnline() {
        if (!connectivityChecker.isCurrentlyOnline()) {
            throw OfflineOperationException("Internet connection required.")
        }
    }

    private fun requireAuthorizedAdmin(session: AdminSession?): AdminSession {
        val current = _activeAdminSession.value
        if (session == null || current == null || session.sessionToken != current.sessionToken) {
            throw SecurityException("Authorized administrator sign-in is required for this action.")
        }
        return current
    }

    // ------------------------------------------------------------------------
    // Administrator Authentication (PBKDF2-HMAC-SHA256, zero hardcoded secrets)
    // ------------------------------------------------------------------------

    suspend fun initializeAdminAccount(emailRaw: String, passwordRaw: String, confirmPasswordRaw: String): Result<AdminSession> {
        return runCatching {
            requireOnline()
            val email = ValidationUtils.validateEmail(emailRaw).getOrThrow()
            if (passwordRaw.length < 8) {
                throw IllegalArgumentException("Administrator password must be at least 8 characters.")
            }
            if (passwordRaw != confirmPasswordRaw) {
                throw IllegalArgumentException("Passwords do not match.")
            }

            val now = System.currentTimeMillis()
            val hashed = PasswordHasher.hashPassword(passwordRaw.toCharArray())

            database.withTransaction {
                val existing = dao.getAdminAccount()
                if (existing != null) {
                    throw IllegalStateException("Administrator account is already configured. Please sign in.")
                }
                dao.insertAdminAccount(
                    AdminAccountEntity(
                        email = email,
                        passwordHashBase64 = hashed.hashBase64,
                        passwordSaltBase64 = hashed.saltBase64,
                        createdAtEpochMs = now,
                    )
                )
                if (dao.getSiteSettingsOnce() == null) {
                    dao.upsertSiteSettings(SiteSettingsEntity(updatedAtEpochMs = now))
                }
            }

            val session = AdminSession(
                email = email,
                sessionToken = UUID.randomUUID().toString(),
                signedInAtEpochMs = now,
            )
            _activeAdminSession.value = session
            session
        }
    }

    suspend fun signInAdmin(emailRaw: String, passwordRaw: String): Result<AdminSession> {
        return runCatching {
            requireOnline()
            val email = ValidationUtils.validateEmail(emailRaw).getOrThrow()
            if (passwordRaw.isBlank()) {
                throw IllegalArgumentException("Enter your administrator password.")
            }

            val account = dao.getAdminAccount()
                ?: throw IllegalStateException("No administrator account exists yet. Create one first.")

            val emailMatches = account.email.equals(email, ignoreCase = true)
            val passwordValid = PasswordHasher.verifyPassword(
                password = passwordRaw.toCharArray(),
                expectedHashBase64 = account.passwordHashBase64,
                saltBase64 = account.passwordSaltBase64,
            )

            if (!emailMatches || !passwordValid) {
                throw SecurityException("Invalid administrator email or password.")
            }

            val session = AdminSession(
                email = account.email,
                sessionToken = UUID.randomUUID().toString(),
                signedInAtEpochMs = System.currentTimeMillis(),
            )
            _activeAdminSession.value = session
            session
        }
    }

    override fun signOutAdmin() {
        _activeAdminSession.value = null
    }

    // ------------------------------------------------------------------------
    // Public User Page Observation & Public Submissions (No Login Required)
    // ------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePublicUserPage(publicIdRaw: String): Flow<PublicPageState> {
        val validIdResult = ValidationUtils.extractPublicIdFromLinkOrInput(publicIdRaw)
        val cleanPublicId = validIdResult.getOrNull()
            ?: return siteSettingsFlow.map { settings -> PublicPageState.NotFound(settings) }

        return dao.observeUserByPublicId(cleanPublicId).flatMapLatest { userEntity ->
            if (userEntity == null) {
                siteSettingsFlow.map { settings -> PublicPageState.NotFound(settings) }
            } else if (!userEntity.isActive) {
                siteSettingsFlow.map { settings -> PublicPageState.Disabled(settings) }
            } else {
                combine(
                    siteSettingsFlow,
                    dao.observeDepositsForUser(userEntity.id),
                    dao.observeWithdrawalsForUser(userEntity.id),
                ) { settings, deposits, withdrawals ->
                    PublicPageState.Active(
                        user = PublicUserView(
                            publicId = userEntity.publicId,
                            displayName = userEntity.displayName,
                            balancePaisa = userEntity.balancePaisa,
                            reservedPaisa = userEntity.reservedPaisa,
                            updatedAtEpochMs = userEntity.updatedAtEpochMs,
                            avatarUri = userEntity.avatarUri,
                        ),
                        settings = settings,
                        deposits = deposits.map { it.toDomain() },
                        withdrawals = withdrawals.map { it.toDomain() },
                    )
                }
            }
        }
    }

    /**
     * Submits a bKash-only deposit request for a valid active public user.
     * Does NOT automatically credit balance — administrator verification is mandatory.
     */
    override suspend fun submitBkashDepositRequest(
        publicIdRaw: String,
        amountBdtRaw: String,
        senderBkashRaw: String,
        transactionIdRaw: String,
    ): Result<DepositRecord> {
        return runCatching {
            requireOnline()
            val publicId = ValidationUtils.extractPublicIdFromLinkOrInput(publicIdRaw).getOrThrow()
            val amountPaisa = CurrencyFormatter.parseBdtToPaisa(amountBdtRaw, allowZero = false).getOrThrow()
            val senderNumber = ValidationUtils.validateAndNormalizeBkashNumber(senderBkashRaw).getOrThrow()
            val trxId = ValidationUtils.validateTransactionId(transactionIdRaw).getOrThrow()

            val now = System.currentTimeMillis()
            val requestId = ValidationUtils.generateRequestId("DEP")

            val createdEntity = database.withTransaction {
                val user = dao.getUserByPublicIdOnce(publicId)
                    ?: throw IllegalArgumentException("Invalid public user page.")
                if (!user.isActive) {
                    throw IllegalStateException("This user page is currently disabled.")
                }
                val duplicateTrx = dao.getDepositByTrxIdOnce(trxId)
                if (duplicateTrx != null) {
                    throw IllegalArgumentException("Transaction ID $trxId has already been submitted.")
                }

                val entity = DepositRequestEntity(
                    requestId = requestId,
                    userId = user.id,
                    userDisplayName = user.displayName,
                    amountPaisa = amountPaisa,
                    senderBkashNumber = senderNumber,
                    transactionId = trxId,
                    status = RequestStatus.PENDING.name,
                    isCredited = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                )
                dao.insertDeposit(entity)
                entity
            }
            createdEntity.toDomain()
        }
    }

    /**
     * Submits a bank-account-only withdrawal request.
     * Atomically validates against available balance (`balancePaisa - reservedPaisa`) and reserves
     * the requested amount so concurrent withdrawal requests cannot exceed available funds.
     */
    override suspend fun submitBankWithdrawalRequest(
        publicIdRaw: String,
        amountBdtRaw: String,
        bankNameRaw: String,
        accountHolderNameRaw: String,
        bankAccountNumberRaw: String,
    ): Result<WithdrawalRecord> {
        return runCatching {
            requireOnline()
            val publicId = ValidationUtils.extractPublicIdFromLinkOrInput(publicIdRaw).getOrThrow()
            val amountPaisa = CurrencyFormatter.parseBdtToPaisa(amountBdtRaw, allowZero = false).getOrThrow()
            val bankName = ValidationUtils.validateBankName(bankNameRaw).getOrThrow()
            val holderName = ValidationUtils.validateAccountHolderName(accountHolderNameRaw).getOrThrow()
            val accountNumber = ValidationUtils.validateBankAccountNumber(bankAccountNumberRaw).getOrThrow()

            val now = System.currentTimeMillis()
            val requestId = ValidationUtils.generateRequestId("WDR")

            val createdEntity = database.withTransaction {
                val user = dao.getUserByPublicIdOnce(publicId)
                    ?: throw IllegalArgumentException("Invalid public user page.")
                if (!user.isActive) {
                    throw IllegalStateException("This user page is currently disabled.")
                }

                val availablePaisa = (user.balancePaisa - user.reservedPaisa).coerceAtLeast(0L)
                if (amountPaisa > availablePaisa) {
                    throw IllegalArgumentException(
                        "Requested amount exceeds available balance (${CurrencyFormatter.formatBdt(availablePaisa)})."
                    )
                }

                val updatedUser = user.copy(
                    reservedPaisa = user.reservedPaisa + amountPaisa,
                    updatedAtEpochMs = now,
                )
                dao.updateUser(updatedUser)

                val entity = WithdrawalRequestEntity(
                    requestId = requestId,
                    userId = user.id,
                    userDisplayName = user.displayName,
                    amountPaisa = amountPaisa,
                    bankName = bankName,
                    accountHolderName = holderName,
                    bankAccountNumber = accountNumber,
                    status = RequestStatus.PENDING.name,
                    isReserved = true,
                    isDeducted = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                )
                dao.insertWithdrawal(entity)

                dao.insertLedgerEntry(
                    LedgerEntryEntity(
                        entryId = ValidationUtils.generateRequestId("LOG"),
                        userId = user.id,
                        userDisplayName = user.displayName,
                        actionType = LedgerActionType.WITHDRAWAL_RESERVED.name,
                        deltaPaisa = 0L,
                        balanceBeforePaisa = user.balancePaisa,
                        balanceAfterPaisa = user.balancePaisa,
                        referenceId = requestId,
                        note = "Reserved ${CurrencyFormatter.formatBdt(amountPaisa)} for pending bank withdrawal ($bankName)",
                        actorEmail = "Public User Request",
                        createdAtEpochMs = now,
                    )
                )
                entity
            }
            createdEntity.toDomain()
        }
    }

    // ------------------------------------------------------------------------
    // Admin-Only Streams (Strictly Gated Behind Active AdminSession)
    // ------------------------------------------------------------------------

    override fun observeAdminUsers(session: AdminSession?): Flow<List<AdminUserRecord>> {
        if (_activeAdminSession.value == null || session?.sessionToken != _activeAdminSession.value?.sessionToken) {
            return flowOf(emptyList())
        }
        return dao.observeAllUsers().map { list -> list.map { it.toAdminDomain() } }
    }

    override fun observeAdminDeposits(session: AdminSession?): Flow<List<DepositRecord>> {
        if (_activeAdminSession.value == null || session?.sessionToken != _activeAdminSession.value?.sessionToken) {
            return flowOf(emptyList())
        }
        return dao.observeAllDeposits().map { list -> list.map { it.toDomain() } }
    }

    override fun observeAdminWithdrawals(session: AdminSession?): Flow<List<WithdrawalRecord>> {
        if (_activeAdminSession.value == null || session?.sessionToken != _activeAdminSession.value?.sessionToken) {
            return flowOf(emptyList())
        }
        return dao.observeAllWithdrawals().map { list -> list.map { it.toDomain() } }
    }

    override fun observeAdminLedger(session: AdminSession?): Flow<List<LedgerRecord>> {
        if (_activeAdminSession.value == null || session?.sessionToken != _activeAdminSession.value?.sessionToken) {
            return flowOf(emptyList())
        }
        return dao.observeAllLedgerEntries().map { list -> list.map { it.toDomain() } }
    }

    // ------------------------------------------------------------------------
    // Admin-Only Mutations (Online-Only + Atomic Transactions + Audit Trail)
    // ------------------------------------------------------------------------

    override suspend fun createUser(
        session: AdminSession?,
        displayNameRaw: String,
        initialBalanceBdtRaw: String,
        initialAvatarUri: Uri?,
    ): Result<AdminUserRecord> {
        return runCatching {
            requireOnline()
            val admin = requireAuthorizedAdmin(session)
            val displayName = ValidationUtils.validateDisplayName(displayNameRaw).getOrThrow()
            val initialPaisa = CurrencyFormatter.parseBdtToPaisa(initialBalanceBdtRaw, allowZero = true).getOrThrow()
            val now = System.currentTimeMillis()
            val internalId = UUID.randomUUID().toString()
            val persistedAvatarUri = if (initialAvatarUri != null && mediaStorageHelper != null) {
                mediaStorageHelper.persistUserAvatarImage(internalId, initialAvatarUri, null).getOrThrow()
            } else {
                initialAvatarUri?.toString()
            }

            val entity = database.withTransaction {
                var publicId = ValidationUtils.generateSecurePublicId()
                while (dao.getUserByPublicIdOnce(publicId) != null) {
                    publicId = ValidationUtils.generateSecurePublicId()
                }
                val newUser = UserEntity(
                    id = internalId,
                    publicId = publicId,
                    displayName = displayName,
                    balancePaisa = initialPaisa,
                    reservedPaisa = 0L,
                    isActive = true,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    avatarUri = persistedAvatarUri,
                )
                dao.insertUser(newUser)
                dao.insertLedgerEntry(
                    LedgerEntryEntity(
                        entryId = ValidationUtils.generateRequestId("LOG"),
                        userId = internalId,
                        userDisplayName = displayName,
                        actionType = LedgerActionType.USER_CREATED.name,
                        deltaPaisa = initialPaisa,
                        balanceBeforePaisa = 0L,
                        balanceAfterPaisa = initialPaisa,
                        referenceId = publicId,
                        note = "Created user page with initial balance ${CurrencyFormatter.formatBdt(initialPaisa)}",
                        actorEmail = admin.email,
                        createdAtEpochMs = now,
                    )
                )
                newUser
            }
            entity.toAdminDomain()
        }
    }

    override suspend fun updateUserDetailsAndBalance(
        session: AdminSession?,
        userId: String,
        newDisplayNameRaw: String,
        newBalanceBdtRaw: String,
        auditReasonRaw: String,
    ): Result<AdminUserRecord> {
        return runCatching {
            requireOnline()
            val admin = requireAuthorizedAdmin(session)
            val newName = ValidationUtils.validateDisplayName(newDisplayNameRaw).getOrThrow()
            val newBalancePaisa = CurrencyFormatter.parseBdtToPaisa(newBalanceBdtRaw, allowZero = true).getOrThrow()
            val now = System.currentTimeMillis()

            val updated = database.withTransaction {
                val existing = dao.getUserByIdOnce(userId)
                    ?: throw IllegalArgumentException("User record not found.")

                if (newBalancePaisa < existing.reservedPaisa) {
                    throw IllegalArgumentException(
                        "New balance cannot be lower than pending reserved withdrawals (${CurrencyFormatter.formatBdt(existing.reservedPaisa)})."
                    )
                }

                val nameChanged = existing.displayName != newName
                val balanceChanged = existing.balancePaisa != newBalancePaisa

                val savedUser = existing.copy(
                    displayName = newName,
                    balancePaisa = newBalancePaisa,
                    updatedAtEpochMs = now,
                )
                dao.updateUser(savedUser)

                if (nameChanged) {
                    dao.updateDepositUserNames(userId, newName)
                    dao.updateWithdrawalUserNames(userId, newName)
                    dao.updateLedgerUserNames(userId, newName)
                }

                if (balanceChanged || nameChanged) {
                    val reasonSuffix = auditReasonRaw.trim().takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()
                    val summary = buildString {
                        if (nameChanged) append("Renamed '${existing.displayName}' to '$newName'. ")
                        if (balanceChanged) {
                            append(
                                "Adjusted balance from ${CurrencyFormatter.formatBdt(existing.balancePaisa)} " +
                                    "to ${CurrencyFormatter.formatBdt(newBalancePaisa)}."
                            )
                        }
                        append(reasonSuffix)
                    }.trim()

                    dao.insertLedgerEntry(
                        LedgerEntryEntity(
                            entryId = ValidationUtils.generateRequestId("ADJ"),
                            userId = userId,
                            userDisplayName = newName,
                            actionType = LedgerActionType.ADMIN_BALANCE_ADJUSTED.name,
                            deltaPaisa = newBalancePaisa - existing.balancePaisa,
                            balanceBeforePaisa = existing.balancePaisa,
                            balanceAfterPaisa = newBalancePaisa,
                            referenceId = existing.publicId,
                            note = summary,
                            actorEmail = admin.email,
                            createdAtEpochMs = now,
                        )
                    )
                }
                savedUser
            }
            updated.toAdminDomain()
        }
    }

    override suspend fun setUserLinkActive(
        session: AdminSession?,
        userId: String,
        isActive: Boolean,
    ): Result<AdminUserRecord> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val now = System.currentTimeMillis()
            val updated = database.withTransaction {
                val existing = dao.getUserByIdOnce(userId)
                    ?: throw IllegalArgumentException("User record not found.")
                val next = existing.copy(isActive = isActive, updatedAtEpochMs = now)
                dao.updateUser(next)
                next
            }
            updated.toAdminDomain()
        }
    }

    override suspend fun regenerateUserPublicLink(
        session: AdminSession?,
        userId: String,
    ): Result<AdminUserRecord> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val now = System.currentTimeMillis()
            val updated = database.withTransaction {
                val existing = dao.getUserByIdOnce(userId)
                    ?: throw IllegalArgumentException("User record not found.")
                var nextPublicId = ValidationUtils.generateSecurePublicId()
                while (dao.getUserByPublicIdOnce(nextPublicId) != null) {
                    nextPublicId = ValidationUtils.generateSecurePublicId()
                }
                val next = existing.copy(publicId = nextPublicId, updatedAtEpochMs = now)
                dao.updateUser(next)
                next
            }
            updated.toAdminDomain()
        }
    }

    override suspend fun deleteUserRecord(
        session: AdminSession?,
        userId: String,
    ): Result<Unit> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val existingAvatar = database.withTransaction {
                val existing = dao.getUserByIdOnce(userId)
                dao.deleteUserById(userId)
                existing?.avatarUri
            }
            mediaStorageHelper?.deleteStoredImage(existingAvatar)
        }
    }

    /**
     * Updates a bKash deposit status.
     * Credits the user's displayed BDT balance EXACTLY ONCE when approved.
     * Terminal states (`APPROVED` / `REJECTED`) are permanently locked against duplicate processing.
     */
    override suspend fun updateDepositStatus(
        session: AdminSession?,
        requestId: String,
        targetStatus: RequestStatus,
    ): Result<DepositRecord> {
        return runCatching {
            requireOnline()
            val admin = requireAuthorizedAdmin(session)
            val now = System.currentTimeMillis()

            val updatedDeposit = database.withTransaction {
                val deposit = dao.getDepositByIdOnce(requestId)
                    ?: throw IllegalArgumentException("Deposit request not found.")
                val currentStatus = RequestStatus.fromStored(deposit.status)

                if (currentStatus.isTerminal || deposit.isCredited) {
                    throw IllegalStateException(
                        "Deposit ${deposit.requestId} is already finalized (${currentStatus.label}) and cannot be processed again."
                    )
                }

                when (targetStatus) {
                    RequestStatus.PENDING, RequestStatus.PROCESSING -> {
                        val next = deposit.copy(
                            status = targetStatus.name,
                            updatedAtEpochMs = now,
                        )
                        dao.updateDeposit(next)
                        next
                    }

                    RequestStatus.REJECTED -> {
                        val next = deposit.copy(
                            status = RequestStatus.REJECTED.name,
                            isCredited = false,
                            updatedAtEpochMs = now,
                        )
                        dao.updateDeposit(next)
                        next
                    }

                    RequestStatus.APPROVED -> {
                        val user = dao.getUserByIdOnce(deposit.userId)
                            ?: throw IllegalStateException("Associated user record no longer exists.")

                        val beforePaisa = user.balancePaisa
                        val afterPaisa = beforePaisa + deposit.amountPaisa

                        dao.updateUser(
                            user.copy(
                                balancePaisa = afterPaisa,
                                updatedAtEpochMs = now,
                            )
                        )

                        val approved = deposit.copy(
                            status = RequestStatus.APPROVED.name,
                            isCredited = true,
                            updatedAtEpochMs = now,
                        )
                        dao.updateDeposit(approved)

                        dao.insertLedgerEntry(
                            LedgerEntryEntity(
                                entryId = ValidationUtils.generateRequestId("LOG"),
                                userId = user.id,
                                userDisplayName = user.displayName,
                                actionType = LedgerActionType.DEPOSIT_APPROVED.name,
                                deltaPaisa = deposit.amountPaisa,
                                balanceBeforePaisa = beforePaisa,
                                balanceAfterPaisa = afterPaisa,
                                referenceId = deposit.requestId,
                                note = "Approved bKash deposit (TrxID: ${deposit.transactionId}, Sender: ${deposit.senderBkashNumber})",
                                actorEmail = admin.email,
                                createdAtEpochMs = now,
                            )
                        )
                        approved
                    }
                }
            }
            updatedDeposit.toDomain()
        }
    }

    /**
     * Updates a bank withdrawal status.
     * - `APPROVED`: Deducts the approved amount from `balancePaisa` EXACTLY ONCE and clears the reservation.
     * - `REJECTED`: Releases the pending reservation so available balance is restored without deducting funds.
     */
    override suspend fun updateWithdrawalStatus(
        session: AdminSession?,
        requestId: String,
        targetStatus: RequestStatus,
    ): Result<WithdrawalRecord> {
        return runCatching {
            requireOnline()
            val admin = requireAuthorizedAdmin(session)
            val now = System.currentTimeMillis()

            val updatedWithdrawal = database.withTransaction {
                val withdrawal = dao.getWithdrawalByIdOnce(requestId)
                    ?: throw IllegalArgumentException("Withdrawal request not found.")
                val currentStatus = RequestStatus.fromStored(withdrawal.status)

                if (currentStatus.isTerminal || withdrawal.isDeducted) {
                    throw IllegalStateException(
                        "Withdrawal ${withdrawal.requestId} is already finalized (${currentStatus.label}) and cannot be processed again."
                    )
                }

                when (targetStatus) {
                    RequestStatus.PENDING, RequestStatus.PROCESSING -> {
                        val next = withdrawal.copy(
                            status = targetStatus.name,
                            updatedAtEpochMs = now,
                        )
                        dao.updateWithdrawal(next)
                        next
                    }

                    RequestStatus.REJECTED -> {
                        val user = dao.getUserByIdOnce(withdrawal.userId)
                        if (user != null && withdrawal.isReserved) {
                            val releasedReserved = (user.reservedPaisa - withdrawal.amountPaisa).coerceAtLeast(0L)
                            dao.updateUser(
                                user.copy(
                                    reservedPaisa = releasedReserved,
                                    updatedAtEpochMs = now,
                                )
                            )
                            dao.insertLedgerEntry(
                                LedgerEntryEntity(
                                    entryId = ValidationUtils.generateRequestId("LOG"),
                                    userId = user.id,
                                    userDisplayName = user.displayName,
                                    actionType = LedgerActionType.WITHDRAWAL_RELEASED.name,
                                    deltaPaisa = 0L,
                                    balanceBeforePaisa = user.balancePaisa,
                                    balanceAfterPaisa = user.balancePaisa,
                                    referenceId = withdrawal.requestId,
                                    note = "Rejected bank withdrawal & released reservation of ${CurrencyFormatter.formatBdt(withdrawal.amountPaisa)}",
                                    actorEmail = admin.email,
                                    createdAtEpochMs = now,
                                )
                            )
                        }
                        val rejected = withdrawal.copy(
                            status = RequestStatus.REJECTED.name,
                            isReserved = false,
                            isDeducted = false,
                            updatedAtEpochMs = now,
                        )
                        dao.updateWithdrawal(rejected)
                        rejected
                    }

                    RequestStatus.APPROVED -> {
                        val user = dao.getUserByIdOnce(withdrawal.userId)
                            ?: throw IllegalStateException("Associated user record no longer exists.")

                        val unreservedRequired = if (withdrawal.isReserved) 0L else withdrawal.amountPaisa
                        val availableForUnreserved = (user.balancePaisa - user.reservedPaisa).coerceAtLeast(0L)
                        if (user.balancePaisa < withdrawal.amountPaisa || availableForUnreserved < unreservedRequired) {
                            throw IllegalStateException("Insufficient user balance to finalize withdrawal.")
                        }

                        val beforePaisa = user.balancePaisa
                        val afterPaisa = beforePaisa - withdrawal.amountPaisa
                        val nextReserved = if (withdrawal.isReserved) {
                            (user.reservedPaisa - withdrawal.amountPaisa).coerceAtLeast(0L)
                        } else {
                            user.reservedPaisa
                        }

                        dao.updateUser(
                            user.copy(
                                balancePaisa = afterPaisa,
                                reservedPaisa = nextReserved,
                                updatedAtEpochMs = now,
                            )
                        )

                        val approved = withdrawal.copy(
                            status = RequestStatus.APPROVED.name,
                            isReserved = false,
                            isDeducted = true,
                            updatedAtEpochMs = now,
                        )
                        dao.updateWithdrawal(approved)

                        dao.insertLedgerEntry(
                            LedgerEntryEntity(
                                entryId = ValidationUtils.generateRequestId("LOG"),
                                userId = user.id,
                                userDisplayName = user.displayName,
                                actionType = LedgerActionType.WITHDRAWAL_APPROVED.name,
                                deltaPaisa = -withdrawal.amountPaisa,
                                balanceBeforePaisa = beforePaisa,
                                balanceAfterPaisa = afterPaisa,
                                referenceId = withdrawal.requestId,
                                note = "Approved bank withdrawal to ${withdrawal.bankName} (${withdrawal.accountHolderName} • ${withdrawal.bankAccountNumber})",
                                actorEmail = admin.email,
                                createdAtEpochMs = now,
                            )
                        )
                        approved
                    }
                }
            }
            updatedWithdrawal.toDomain()
        }
    }

    override suspend fun updateBkashPaymentNumber(
        session: AdminSession?,
        bkashNumberRaw: String,
    ): Result<SiteSettingsModel> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val validBkash = ValidationUtils.validateAndNormalizeBkashNumber(bkashNumberRaw).getOrThrow()
            val now = System.currentTimeMillis()
            val saved = database.withTransaction {
                val current = dao.getSiteSettingsOnce() ?: SiteSettingsEntity(updatedAtEpochMs = now)
                val updated = current.copy(bkashNumber = validBkash, updatedAtEpochMs = now)
                dao.upsertSiteSettings(updated)
                updated
            }
            saved.toDomain()
        }
    }

    override suspend fun updateProductionBaseUrl(
        session: AdminSession?,
        baseUrlRaw: String,
    ): Result<SiteSettingsModel> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val validBaseUrl = ValidationUtils.validateProductionBaseUrl(baseUrlRaw).getOrThrow()
            val now = System.currentTimeMillis()
            val saved = database.withTransaction {
                val current = dao.getSiteSettingsOnce() ?: SiteSettingsEntity(updatedAtEpochMs = now)
                val updated = current.copy(productionBaseUrl = validBaseUrl, updatedAtEpochMs = now)
                dao.upsertSiteSettings(updated)
                updated
            }
            saved.toDomain()
        }
    }

    override suspend fun saveCustomMediaImage(
        session: AdminSession?,
        slot: MediaSlot,
        pickedUri: Uri,
    ): Result<SiteSettingsModel> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val helper = mediaStorageHelper
                ?: throw IllegalStateException("Media storage helper is unavailable in this environment.")

            val current = dao.getSiteSettingsOnce() ?: SiteSettingsEntity()
            val previousUri = current.uriForSlot(slot)
            val persistedUri = helper.persistPickedImage(slot, pickedUri, previousUri).getOrThrow()
            val now = System.currentTimeMillis()

            val updated = current.withSlotUri(slot, persistedUri).copy(updatedAtEpochMs = now)
            dao.upsertSiteSettings(updated)
            updated.toDomain()
        }
    }

    override suspend fun saveMediaUriDirectly(
        session: AdminSession?,
        slot: MediaSlot,
        uriString: String?,
    ): Result<SiteSettingsModel> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val now = System.currentTimeMillis()
            val current = dao.getSiteSettingsOnce() ?: SiteSettingsEntity(updatedAtEpochMs = now)
            if (uriString == null) {
                mediaStorageHelper?.deleteStoredImage(current.uriForSlot(slot))
            }
            val updated = current.withSlotUri(slot, uriString).copy(updatedAtEpochMs = now)
            dao.upsertSiteSettings(updated)
            updated.toDomain()
        }
    }

    override suspend fun saveUserAvatarImage(
        session: AdminSession?,
        userId: String,
        pickedUri: Uri,
    ): Result<AdminUserRecord> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val helper = mediaStorageHelper
                ?: throw IllegalStateException("Media storage helper is unavailable in this environment.")

            val existing = dao.getUserByIdOnce(userId)
                ?: throw IllegalArgumentException("User record not found.")

            val persistedUri = helper.persistUserAvatarImage(userId, pickedUri, existing.avatarUri).getOrThrow()
            val now = System.currentTimeMillis()

            val updated = database.withTransaction {
                val latest = dao.getUserByIdOnce(userId)
                    ?: throw IllegalArgumentException("User record not found.")
                val saved = latest.copy(
                    avatarUri = persistedUri,
                    updatedAtEpochMs = now,
                )
                dao.updateUser(saved)
                saved
            }
            updated.toAdminDomain()
        }
    }

    override suspend fun saveUserAvatarUriDirectly(
        session: AdminSession?,
        userId: String,
        avatarUri: String?,
    ): Result<AdminUserRecord> {
        return runCatching {
            requireOnline()
            requireAuthorizedAdmin(session)
            val now = System.currentTimeMillis()
            var removedPreviousUri: String? = null

            val updated = database.withTransaction {
                val existing = dao.getUserByIdOnce(userId)
                    ?: throw IllegalArgumentException("User record not found.")
                if (avatarUri == null) {
                    removedPreviousUri = existing.avatarUri
                }
                val saved = existing.copy(
                    avatarUri = avatarUri,
                    updatedAtEpochMs = now,
                )
                dao.updateUser(saved)
                saved
            }
            if (removedPreviousUri != null) {
                mediaStorageHelper?.deleteStoredImage(removedPreviousUri)
            }
            updated.toAdminDomain()
        }
    }
}

private fun SiteSettingsEntity.uriForSlot(slot: MediaSlot): String? = when (slot) {
    MediaSlot.APP_LOGO -> logoUri
    MediaSlot.MAIN_BANNER -> mainBannerUri
    MediaSlot.PROMO_BANNER_1 -> promoBanner1Uri
    MediaSlot.PROMO_BANNER_2 -> promoBanner2Uri
    MediaSlot.PROMO_BANNER_3 -> promoBanner3Uri
}

private fun SiteSettingsEntity.withSlotUri(slot: MediaSlot, uri: String?): SiteSettingsEntity = when (slot) {
    MediaSlot.APP_LOGO -> copy(logoUri = uri)
    MediaSlot.MAIN_BANNER -> copy(mainBannerUri = uri)
    MediaSlot.PROMO_BANNER_1 -> copy(promoBanner1Uri = uri)
    MediaSlot.PROMO_BANNER_2 -> copy(promoBanner2Uri = uri)
    MediaSlot.PROMO_BANNER_3 -> copy(promoBanner3Uri = uri)
}

private fun SiteSettingsEntity.toDomain(): SiteSettingsModel = SiteSettingsModel(
    bkashNumber = bkashNumber,
    productionBaseUrl = ValidationUtils.sanitizeConfiguredBaseUrl(productionBaseUrl),
    logoUri = logoUri,
    mainBannerUri = mainBannerUri,
    promoBanner1Uri = promoBanner1Uri,
    promoBanner2Uri = promoBanner2Uri,
    promoBanner3Uri = promoBanner3Uri,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun UserEntity.toAdminDomain(): AdminUserRecord = AdminUserRecord(
    id = id,
    publicId = publicId,
    displayName = displayName,
    balancePaisa = balancePaisa,
    reservedPaisa = reservedPaisa,
    isActive = isActive,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    avatarUri = avatarUri,
)

private fun DepositRequestEntity.toDomain(): DepositRecord = DepositRecord(
    requestId = requestId,
    userId = userId,
    userDisplayName = userDisplayName,
    amountPaisa = amountPaisa,
    senderBkashNumber = senderBkashNumber,
    transactionId = transactionId,
    status = RequestStatus.fromStored(status),
    isCredited = isCredited,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun WithdrawalRequestEntity.toDomain(): WithdrawalRecord = WithdrawalRecord(
    requestId = requestId,
    userId = userId,
    userDisplayName = userDisplayName,
    amountPaisa = amountPaisa,
    bankName = bankName,
    accountHolderName = accountHolderName,
    bankAccountNumber = bankAccountNumber,
    status = RequestStatus.fromStored(status),
    isReserved = isReserved,
    isDeducted = isDeducted,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun LedgerEntryEntity.toDomain(): LedgerRecord = LedgerRecord(
    entryId = entryId,
    userId = userId,
    userDisplayName = userDisplayName,
    actionType = LedgerActionType.fromStored(actionType),
    deltaPaisa = deltaPaisa,
    balanceBeforePaisa = balanceBeforePaisa,
    balanceAfterPaisa = balanceAfterPaisa,
    referenceId = referenceId,
    note = note,
    actorEmail = actorEmail,
    createdAtEpochMs = createdAtEpochMs,
)
