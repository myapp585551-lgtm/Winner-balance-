package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.R
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
import com.example.util.AndroidConnectivityObserver
import com.example.util.ConnectivityChecker
import com.example.util.CurrencyFormatter
import com.example.util.MediaSlot
import com.example.util.MediaStorageHelper
import com.example.util.OfflineOperationException
import com.example.util.OperationType
import com.example.util.ValidationUtils
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Cloud-backed Firebase Firestore repository for Winner Balance.
 *
 * Security & Integrity Guarantees:
 * - Connects exclusively to the provisioned named Firestore database (`R.string.firestore_database_id`).
 * - Public user pages (`/public_users/{publicId}`) load in real time via `?u=PUBLIC_ID` without admin login.
 * - Admin streams (`observeAdminUsers`, `observeAdminDeposits`, `observeAdminWithdrawals`, `observeAdminLedger`)
 *   are strictly gated behind Firebase Authentication (Google Sign-In) and scoped to `ownerUid`.
 * - Atomic `WriteBatch` commits enforce exact-once deposit credits, withdrawal reservations, and audit ledger trails.
 */
class WinnerFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val connectivityChecker: ConnectivityChecker,
    private val mediaStorageHelper: MediaStorageHelper? = null,
) : WinnerDataSource {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        connectivityChecker = AndroidConnectivityObserver(context.applicationContext),
        mediaStorageHelper = MediaStorageHelper(context.applicationContext),
    )

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeAdminSession = MutableStateFlow(auth.currentUser?.toAdminSession())
    override val activeAdminSession: StateFlow<AdminSession?> = _activeAdminSession.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _activeAdminSession.value = user?.toAdminSession()
        if (user != null && connectivityChecker.isCurrentlyOnline()) {
            backgroundScope.launch {
                runCatching { claimBootstrapRecordsIfNeeded(user) }
            }
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    override val isOnlineFlow: Flow<Boolean> = connectivityChecker.isOnlineFlow

    override val siteSettingsFlow: Flow<SiteSettingsModel> =
        db.collection(COL_SITE_SETTINGS).document(DOC_GLOBAL_SETTINGS)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toSiteSettingsModel() else SiteSettingsModel()
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, "$COL_SITE_SETTINGS/$DOC_GLOBAL_SETTINGS")
                }
                emit(SiteSettingsModel())
            }

    private fun requireOnline() {
        if (!connectivityChecker.isCurrentlyOnline()) {
            throw OfflineOperationException("Internet connection required.")
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw SecurityException("Authorized Google administrator sign-in is required for this action.")
    }

    private fun requireAdminActorEmail(): String {
        val user = auth.currentUser
            ?: throw SecurityException("Authorized Google administrator sign-in is required for this action.")
        return user.email?.takeIf { it.isNotBlank() } ?: "${user.uid}@google-auth.local"
    }

    /**
     * Ensures the global settings document and verified test public user (`WB7x9Km2Pq4Rt8Vz3N`)
     * exist in Firestore on cold launch so `?u=WB7x9Km2Pq4Rt8Vz3N` resolves live Firebase data immediately.
     */
    override suspend fun ensureColdBootstrapSeeded(): Result<Unit> = runCatching {
        if (!connectivityChecker.isCurrentlyOnline()) return@runCatching

        val settingsRef = db.collection(COL_SITE_SETTINGS).document(DOC_GLOBAL_SETTINGS)
        try {
            val settingsSnap = settingsRef.get().await()
            if (!settingsSnap.exists()) {
                val ownerUid = auth.currentUser?.uid ?: BOOTSTRAP_OWNER_UID
                settingsRef.set(
                    mapOf(
                        "ownerUid" to ownerUid,
                        "bkashNumber" to "01700000000",
                        "productionBaseUrl" to ValidationUtils.sanitizeConfiguredBaseUrl(BuildConfig.DEFAULT_PRODUCTION_BASE_URL),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, settingsRef.path)
        }

        val verifiedRef = db.collection(COL_PUBLIC_USERS).document(VERIFIED_PUBLIC_ID)
        try {
            val verifiedSnap = verifiedRef.get().await()
            if (!verifiedSnap.exists()) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val batch = db.batch()
                    val uid = currentUser.uid
                    val adminRef = db.collection(COL_ADMIN_USERS).document(VERIFIED_USER_ID)
                    batch.set(
                        adminRef,
                        mapOf(
                            "userId" to VERIFIED_USER_ID,
                            "publicId" to VERIFIED_PUBLIC_ID,
                            "ownerUid" to uid,
                            "displayName" to VERIFIED_USER_NAME,
                            "balancePaisa" to VERIFIED_USER_BALANCE_PAISA,
                            "reservedPaisa" to 0L,
                            "isActive" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    batch.set(
                        verifiedRef,
                        mapOf(
                            "publicId" to VERIFIED_PUBLIC_ID,
                            "userId" to VERIFIED_USER_ID,
                            "ownerUid" to uid,
                            "displayName" to VERIFIED_USER_NAME,
                            "balancePaisa" to VERIFIED_USER_BALANCE_PAISA,
                            "reservedPaisa" to 0L,
                            "isActive" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    batch.commit().await()
                } else {
                    verifiedRef.set(
                        mapOf(
                            "publicId" to VERIFIED_PUBLIC_ID,
                            "userId" to VERIFIED_USER_ID,
                            "ownerUid" to BOOTSTRAP_OWNER_UID,
                            "displayName" to VERIFIED_USER_NAME,
                            "balancePaisa" to VERIFIED_USER_BALANCE_PAISA,
                            "reservedPaisa" to 0L,
                            "isActive" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            } else if (auth.currentUser != null) {
                claimBootstrapRecordsIfNeeded(auth.currentUser!!)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, verifiedRef.path)
        }
    }

    private suspend fun claimBootstrapRecordsIfNeeded(user: FirebaseUser) {
        val uid = user.uid
        val settingsRef = db.collection(COL_SITE_SETTINGS).document(DOC_GLOBAL_SETTINGS)
        try {
            val settingsSnap = settingsRef.get().await()
            if (settingsSnap.exists() && settingsSnap.getString("ownerUid") == BOOTSTRAP_OWNER_UID) {
                settingsRef.update(
                    mapOf(
                        "ownerUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, settingsRef.path)
        }

        val publicRef = db.collection(COL_PUBLIC_USERS).document(VERIFIED_PUBLIC_ID)
        try {
            val pubSnap = publicRef.get().await()
            if (pubSnap.exists() && pubSnap.getString("ownerUid") == BOOTSTRAP_OWNER_UID) {
                val userId = pubSnap.getString("userId") ?: VERIFIED_USER_ID
                val displayName = pubSnap.getString("displayName") ?: VERIFIED_USER_NAME
                val balancePaisa = pubSnap.getLong("balancePaisa") ?: VERIFIED_USER_BALANCE_PAISA
                val reservedPaisa = pubSnap.getLong("reservedPaisa") ?: 0L
                val isActive = pubSnap.getBoolean("isActive") ?: true
                val avatarUri = pubSnap.getString("avatarUri")?.takeIf { it.isNotBlank() }

                val batch = db.batch()
                batch.update(
                    publicRef,
                    mapOf(
                        "ownerUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                val adminPayload = mutableMapOf<String, Any>(
                    "userId" to userId,
                    "publicId" to VERIFIED_PUBLIC_ID,
                    "ownerUid" to uid,
                    "displayName" to displayName,
                    "balancePaisa" to balancePaisa,
                    "reservedPaisa" to reservedPaisa,
                    "isActive" to isActive,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (avatarUri != null) {
                    adminPayload["avatarUri"] = avatarUri
                }
                batch.set(db.collection(COL_ADMIN_USERS).document(userId), adminPayload)
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, publicRef.path)
        }
    }

    override fun signOutAdmin() {
        auth.signOut()
        _activeAdminSession.value = null
    }

    // ------------------------------------------------------------------------
    // Public User Page Observation & Public Submissions (No Login Required)
    // ------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePublicUserPage(publicIdRaw: String): Flow<PublicPageState> {
        val cleanPublicId = ValidationUtils.extractPublicIdFromLinkOrInput(publicIdRaw).getOrNull()
            ?: return siteSettingsFlow.map { settings -> PublicPageState.NotFound(settings) }

        val publicDocPath = "$COL_PUBLIC_USERS/$cleanPublicId"
        val statusColPath = "$publicDocPath/$SUBCOL_REQUEST_STATUSES"

        return db.collection(COL_PUBLIC_USERS).document(cleanPublicId)
            .snapshots()
            .flatMapLatest { userSnap ->
                if (!userSnap.exists()) {
                    siteSettingsFlow.map { settings -> PublicPageState.NotFound(settings) }
                } else if (userSnap.getBoolean("isActive") == false) {
                    siteSettingsFlow.map { settings -> PublicPageState.Disabled(settings) }
                } else {
                    val userView = userSnap.toPublicUserView()
                    val statusesFlow = db.collection(COL_PUBLIC_USERS)
                        .document(cleanPublicId)
                        .collection(SUBCOL_REQUEST_STATUSES)
                        .snapshots()
                        .map { querySnap ->
                            val deposits = mutableListOf<DepositRecord>()
                            val withdrawals = mutableListOf<WithdrawalRecord>()
                            for (doc in querySnap.documents) {
                                val requestType = doc.getString("requestType").orEmpty()
                                val reqId = doc.getString("requestId") ?: doc.id
                                val amountPaisa = doc.getLong("amountPaisa") ?: 0L
                                val summary = doc.getString("summaryLabel").orEmpty()
                                val status = RequestStatus.fromStored(doc.getString("status").orEmpty())
                                val createdMs = doc.readEpochMs("createdAt")
                                val updatedMs = doc.readEpochMs("updatedAt", fallbackMs = createdMs)
                                if (requestType == "DEPOSIT") {
                                    val trxLabel = summary.removePrefix("TrxID:").trim().ifEmpty { reqId }
                                    deposits.add(
                                        DepositRecord(
                                            requestId = reqId,
                                            userId = "",
                                            userDisplayName = userView.displayName,
                                            amountPaisa = amountPaisa,
                                            senderBkashNumber = "bKash Payment",
                                            transactionId = trxLabel,
                                            status = status,
                                            isCredited = status == RequestStatus.APPROVED,
                                            createdAtEpochMs = createdMs,
                                            updatedAtEpochMs = updatedMs,
                                        )
                                    )
                                } else if (requestType == "WITHDRAWAL") {
                                    val bankLabel = summary.removePrefix("Bank:").trim().ifEmpty { "Bank Transfer" }
                                    withdrawals.add(
                                        WithdrawalRecord(
                                            requestId = reqId,
                                            userId = "",
                                            userDisplayName = userView.displayName,
                                            amountPaisa = amountPaisa,
                                            bankName = bankLabel,
                                            accountHolderName = userView.displayName,
                                            bankAccountNumber = "Protected",
                                            status = status,
                                            isReserved = !status.isTerminal,
                                            isDeducted = status == RequestStatus.APPROVED,
                                            createdAtEpochMs = createdMs,
                                            updatedAtEpochMs = updatedMs,
                                        )
                                    )
                                }
                            }
                            Pair(
                                deposits.sortedByDescending { it.createdAtEpochMs },
                                withdrawals.sortedByDescending { it.createdAtEpochMs },
                            )
                        }
                        .catch { err ->
                            if (err is Exception) handleFirestoreError(err, OperationType.LIST, statusColPath)
                            emit(Pair(emptyList(), emptyList()))
                        }

                    combine(siteSettingsFlow, statusesFlow) { settings, (deposits, withdrawals) ->
                        PublicPageState.Active(
                            user = userView,
                            settings = settings,
                            deposits = deposits,
                            withdrawals = withdrawals,
                        )
                    }
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, publicDocPath)
                }
                emit(PublicPageState.Error(error.localizedMessage ?: "Failed to load user page from Firebase."))
            }
    }

    override suspend fun submitBkashDepositRequest(
        publicIdRaw: String,
        amountBdtRaw: String,
        senderBkashRaw: String,
        transactionIdRaw: String,
    ): Result<DepositRecord> = runCatching {
        requireOnline()
        val publicId = ValidationUtils.extractPublicIdFromLinkOrInput(publicIdRaw).getOrThrow()
        val amountPaisa = CurrencyFormatter.parseBdtToPaisa(amountBdtRaw, allowZero = false).getOrThrow()
        val senderNumber = ValidationUtils.validateAndNormalizeBkashNumber(senderBkashRaw).getOrThrow()
        val trxId = ValidationUtils.validateTransactionId(transactionIdRaw).getOrThrow()

        val publicRef = db.collection(COL_PUBLIC_USERS).document(publicId)
        val userSnap = try {
            publicRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, publicRef.path)
            throw e
        }

        if (!userSnap.exists()) {
            throw IllegalArgumentException("Invalid public user page.")
        }
        if (userSnap.getBoolean("isActive") == false) {
            throw IllegalStateException("This user page is currently disabled.")
        }

        val trxLockRef = db.collection(COL_DEPOSIT_TRX_LOCKS).document(trxId)
        val existingLock = try {
            trxLockRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, trxLockRef.path)
            throw e
        }
        if (existingLock.exists()) {
            throw IllegalArgumentException("Transaction ID $trxId has already been submitted.")
        }

        val userId = userSnap.getString("userId") ?: throw IllegalStateException("Malformed user record.")
        val ownerUid = userSnap.getString("ownerUid") ?: throw IllegalStateException("Malformed user owner.")
        val displayName = userSnap.getString("displayName") ?: "User"
        val requestId = ValidationUtils.generateRequestId("DEP")
        val nowMs = System.currentTimeMillis()

        val depositRef = db.collection(COL_DEPOSIT_REQUESTS).document(requestId)
        val statusRef = publicRef.collection(SUBCOL_REQUEST_STATUSES).document(requestId)

        try {
            val batch = db.batch()
            batch.set(
                depositRef,
                mapOf(
                    "requestId" to requestId,
                    "userId" to userId,
                    "publicId" to publicId,
                    "ownerUid" to ownerUid,
                    "userDisplayName" to displayName,
                    "amountPaisa" to amountPaisa,
                    "senderBkashNumber" to senderNumber,
                    "transactionId" to trxId,
                    "status" to RequestStatus.PENDING.name,
                    "isCredited" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.set(
                trxLockRef,
                mapOf(
                    "transactionId" to trxId,
                    "requestId" to requestId,
                    "publicId" to publicId,
                    "ownerUid" to ownerUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.set(
                statusRef,
                mapOf(
                    "requestId" to requestId,
                    "publicId" to publicId,
                    "ownerUid" to ownerUid,
                    "requestType" to "DEPOSIT",
                    "amountPaisa" to amountPaisa,
                    "summaryLabel" to "TrxID: $trxId",
                    "status" to RequestStatus.PENDING.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.CREATE, depositRef.path)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw IllegalArgumentException("Transaction ID $trxId has already been submitted or page is inactive.")
            }
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, depositRef.path)
            throw e
        }

        DepositRecord(
            requestId = requestId,
            userId = userId,
            userDisplayName = displayName,
            amountPaisa = amountPaisa,
            senderBkashNumber = senderNumber,
            transactionId = trxId,
            status = RequestStatus.PENDING,
            isCredited = false,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
        )
    }

    override suspend fun submitBankWithdrawalRequest(
        publicIdRaw: String,
        amountBdtRaw: String,
        bankNameRaw: String,
        accountHolderNameRaw: String,
        bankAccountNumberRaw: String,
    ): Result<WithdrawalRecord> = runCatching {
        requireOnline()
        val publicId = ValidationUtils.extractPublicIdFromLinkOrInput(publicIdRaw).getOrThrow()
        val amountPaisa = CurrencyFormatter.parseBdtToPaisa(amountBdtRaw, allowZero = false).getOrThrow()
        val bankName = ValidationUtils.validateBankName(bankNameRaw).getOrThrow()
        val holderName = ValidationUtils.validateAccountHolderName(accountHolderNameRaw).getOrThrow()
        val accountNumber = ValidationUtils.validateBankAccountNumber(bankAccountNumberRaw).getOrThrow()

        val publicRef = db.collection(COL_PUBLIC_USERS).document(publicId)
        val userSnap = try {
            publicRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, publicRef.path)
            throw e
        }

        if (!userSnap.exists()) {
            throw IllegalArgumentException("Invalid public user page.")
        }
        if (userSnap.getBoolean("isActive") == false) {
            throw IllegalStateException("This user page is currently disabled.")
        }

        val balancePaisa = userSnap.getLong("balancePaisa") ?: 0L
        val currentReservedPaisa = userSnap.getLong("reservedPaisa") ?: 0L
        val availablePaisa = (balancePaisa - currentReservedPaisa).coerceAtLeast(0L)
        if (amountPaisa > availablePaisa) {
            throw IllegalArgumentException(
                "Requested amount exceeds available balance (${CurrencyFormatter.formatBdt(availablePaisa)})."
            )
        }

        val userId = userSnap.getString("userId") ?: throw IllegalStateException("Malformed user record.")
        val ownerUid = userSnap.getString("ownerUid") ?: throw IllegalStateException("Malformed user owner.")
        val displayName = userSnap.getString("displayName") ?: "User"
        val requestId = ValidationUtils.generateRequestId("WDR")
        val nowMs = System.currentTimeMillis()
        val nextReservedPaisa = currentReservedPaisa + amountPaisa

        val withdrawalRef = db.collection(COL_WITHDRAWAL_REQUESTS).document(requestId)
        val statusRef = publicRef.collection(SUBCOL_REQUEST_STATUSES).document(requestId)

        try {
            val batch = db.batch()
            batch.update(
                publicRef,
                mapOf(
                    "reservedPaisa" to nextReservedPaisa,
                    "lastWithdrawalRequestId" to requestId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.set(
                withdrawalRef,
                mapOf(
                    "requestId" to requestId,
                    "userId" to userId,
                    "publicId" to publicId,
                    "ownerUid" to ownerUid,
                    "userDisplayName" to displayName,
                    "amountPaisa" to amountPaisa,
                    "bankName" to bankName,
                    "accountHolderName" to holderName,
                    "bankAccountNumber" to accountNumber,
                    "status" to RequestStatus.PENDING.name,
                    "isReserved" to true,
                    "isDeducted" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.set(
                statusRef,
                mapOf(
                    "requestId" to requestId,
                    "publicId" to publicId,
                    "ownerUid" to ownerUid,
                    "requestType" to "WITHDRAWAL",
                    "amountPaisa" to amountPaisa,
                    "summaryLabel" to "Bank: $bankName",
                    "status" to RequestStatus.PENDING.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, withdrawalRef.path)
            throw e
        }

        WithdrawalRecord(
            requestId = requestId,
            userId = userId,
            userDisplayName = displayName,
            amountPaisa = amountPaisa,
            bankName = bankName,
            accountHolderName = holderName,
            bankAccountNumber = accountNumber,
            status = RequestStatus.PENDING,
            isReserved = true,
            isDeducted = false,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
        )
    }

    // ------------------------------------------------------------------------
    // Admin-Only Realtime Streams (Strictly Auth-Gated & Scoped to ownerUid)
    // ------------------------------------------------------------------------

    override fun observeAdminUsers(session: AdminSession?): Flow<List<AdminUserRecord>> {
        val uid = auth.currentUser?.uid
        if (uid == null || session == null) return flowOf(emptyList())

        // Synchronize live reservedPaisa from public_users while scoping strictly to ownerUid
        val adminFlow = db.collection(COL_ADMIN_USERS)
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap -> snap.documents.map { it.toAdminUserRecord() } }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, COL_ADMIN_USERS)
                throw err
            }

        val publicMirrorFlow = db.collection(COL_PUBLIC_USERS)
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents.associate { doc ->
                    doc.id to (doc.getLong("reservedPaisa") ?: 0L)
                }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, COL_PUBLIC_USERS)
                emit(emptyMap())
            }

        return combine(adminFlow, publicMirrorFlow) { adminUsers, publicReservedByPublicId ->
            adminUsers.map { record ->
                val liveReserved = publicReservedByPublicId[record.publicId] ?: record.reservedPaisa
                record.copy(reservedPaisa = liveReserved)
            }.sortedByDescending { it.createdAtEpochMs }
        }
    }

    override fun observeAdminDeposits(session: AdminSession?): Flow<List<DepositRecord>> {
        val uid = auth.currentUser?.uid
        if (uid == null || session == null) return flowOf(emptyList())

        return db.collection(COL_DEPOSIT_REQUESTS)
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents
                    .map { it.toDepositRecord() }
                    .sortedByDescending { it.createdAtEpochMs }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, COL_DEPOSIT_REQUESTS)
                throw err
            }
    }

    override fun observeAdminWithdrawals(session: AdminSession?): Flow<List<WithdrawalRecord>> {
        val uid = auth.currentUser?.uid
        if (uid == null || session == null) return flowOf(emptyList())

        return db.collection(COL_WITHDRAWAL_REQUESTS)
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents
                    .map { it.toWithdrawalRecord() }
                    .sortedByDescending { it.createdAtEpochMs }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, COL_WITHDRAWAL_REQUESTS)
                throw err
            }
    }

    override fun observeAdminLedger(session: AdminSession?): Flow<List<LedgerRecord>> {
        val uid = auth.currentUser?.uid
        if (uid == null || session == null) return flowOf(emptyList())

        return db.collection(COL_LEDGER_ENTRIES)
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents
                    .map { it.toLedgerRecord() }
                    .sortedByDescending { it.createdAtEpochMs }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, COL_LEDGER_ENTRIES)
                throw err
            }
    }

    /**
     * Direct Firestore query helper used in security rule verification tests (`*RuleTest.kt`)
     * to verify that Firestore security rules actively reject unauthenticated or cross-user reads
     * with `PERMISSION_DENIED`.
     */
    suspend fun getAdminUserByIdDirect(userId: String): AdminUserRecord {
        val ref = db.collection(COL_ADMIN_USERS).document(userId)
        try {
            val snap = ref.get().await()
            if (!snap.exists()) throw IllegalArgumentException("User record not found.")
            return snap.toAdminUserRecord()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path)
            throw e
        }
    }

    fun observeAdminUsersRawUnfilteredForRuleTest(): Flow<List<AdminUserRecord>> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return db.collection(COL_ADMIN_USERS)
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap -> snap.documents.map { it.toAdminUserRecord() } }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, COL_ADMIN_USERS)
                throw err
            }
    }

    // ------------------------------------------------------------------------
    // Admin-Only Mutations (Online-Only + Atomic WriteBatch + Audit Trail)
    // ------------------------------------------------------------------------

    override suspend fun createUser(
        session: AdminSession?,
        displayNameRaw: String,
        initialBalanceBdtRaw: String,
        initialAvatarUri: Uri?,
    ): Result<AdminUserRecord> = runCatching {
        requireOnline()
        val uid = requireUserId()
        val actorEmail = requireAdminActorEmail()
        val displayName = ValidationUtils.validateDisplayName(displayNameRaw).getOrThrow()
        val initialPaisa = CurrencyFormatter.parseBdtToPaisa(initialBalanceBdtRaw, allowZero = true).getOrThrow()

        val internalId = "usr-${UUID.randomUUID()}"
        val persistedAvatarUri = if (initialAvatarUri != null && mediaStorageHelper != null) {
            mediaStorageHelper.persistUserAvatarImage(internalId, initialAvatarUri, null).getOrThrow()
        } else {
            initialAvatarUri?.toString()
        }?.takeIf { it.isNotBlank() && it.length <= 2048 }

        var publicId = ValidationUtils.generateSecurePublicId()
        while (db.collection(COL_PUBLIC_USERS).document(publicId).get().await().exists()) {
            publicId = ValidationUtils.generateSecurePublicId()
        }

        val nowMs = System.currentTimeMillis()
        val ledgerId = ValidationUtils.generateRequestId("LOG")
        val adminRef = db.collection(COL_ADMIN_USERS).document(internalId)
        val publicRef = db.collection(COL_PUBLIC_USERS).document(publicId)
        val ledgerRef = db.collection(COL_LEDGER_ENTRIES).document(ledgerId)

        val adminPayload = mutableMapOf<String, Any>(
            "userId" to internalId,
            "publicId" to publicId,
            "ownerUid" to uid,
            "displayName" to displayName,
            "balancePaisa" to initialPaisa,
            "reservedPaisa" to 0L,
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val publicPayload = mutableMapOf<String, Any>(
            "publicId" to publicId,
            "userId" to internalId,
            "ownerUid" to uid,
            "displayName" to displayName,
            "balancePaisa" to initialPaisa,
            "reservedPaisa" to 0L,
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (persistedAvatarUri != null) {
            adminPayload["avatarUri"] = persistedAvatarUri
            publicPayload["avatarUri"] = persistedAvatarUri
        }

        try {
            val batch = db.batch()
            batch.set(adminRef, adminPayload)
            batch.set(publicRef, publicPayload)
            batch.set(
                ledgerRef,
                mapOf(
                    "entryId" to ledgerId,
                    "userId" to internalId,
                    "ownerUid" to uid,
                    "userDisplayName" to displayName,
                    "actionType" to LedgerActionType.USER_CREATED.name,
                    "deltaPaisa" to initialPaisa,
                    "balanceBeforePaisa" to 0L,
                    "balanceAfterPaisa" to initialPaisa,
                    "referenceId" to publicId,
                    "note" to "Created user page with initial balance ${CurrencyFormatter.formatBdt(initialPaisa)}",
                    "actorEmail" to actorEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, adminRef.path)
            throw e
        }

        AdminUserRecord(
            id = internalId,
            publicId = publicId,
            displayName = displayName,
            balancePaisa = initialPaisa,
            reservedPaisa = 0L,
            isActive = true,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            avatarUri = persistedAvatarUri,
        )
    }

    override suspend fun updateUserDetailsAndBalance(
        session: AdminSession?,
        userId: String,
        newDisplayNameRaw: String,
        newBalanceBdtRaw: String,
        auditReasonRaw: String,
    ): Result<AdminUserRecord> = runCatching {
        requireOnline()
        val uid = requireUserId()
        val actorEmail = requireAdminActorEmail()
        val newName = ValidationUtils.validateDisplayName(newDisplayNameRaw).getOrThrow()
        val newBalancePaisa = CurrencyFormatter.parseBdtToPaisa(newBalanceBdtRaw, allowZero = true).getOrThrow()

        val adminRef = db.collection(COL_ADMIN_USERS).document(userId)
        val adminSnap = try {
            adminRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, adminRef.path)
            throw e
        }
        if (!adminSnap.exists()) throw IllegalArgumentException("User record not found.")

        val existing = adminSnap.toAdminUserRecord()
        val publicRef = db.collection(COL_PUBLIC_USERS).document(existing.publicId)
        val publicSnap = publicRef.get().await()
        val liveReserved = publicSnap.getLong("reservedPaisa") ?: existing.reservedPaisa

        if (newBalancePaisa < liveReserved) {
            throw IllegalArgumentException(
                "New balance cannot be lower than pending reserved withdrawals (${CurrencyFormatter.formatBdt(liveReserved)})."
            )
        }

        val nameChanged = existing.displayName != newName
        val balanceChanged = existing.balancePaisa != newBalancePaisa
        val nowMs = System.currentTimeMillis()

        try {
            val batch = db.batch()
            batch.update(
                adminRef,
                mapOf(
                    "displayName" to newName,
                    "balancePaisa" to newBalancePaisa,
                    "reservedPaisa" to liveReserved,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            if (publicSnap.exists()) {
                batch.update(
                    publicRef,
                    mapOf(
                        "displayName" to newName,
                        "balancePaisa" to newBalancePaisa,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
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
                }.trim().take(500)

                val entryId = ValidationUtils.generateRequestId("ADJ")
                batch.set(
                    db.collection(COL_LEDGER_ENTRIES).document(entryId),
                    mapOf(
                        "entryId" to entryId,
                        "userId" to userId,
                        "ownerUid" to uid,
                        "userDisplayName" to newName,
                        "actionType" to LedgerActionType.ADMIN_BALANCE_ADJUSTED.name,
                        "deltaPaisa" to (newBalancePaisa - existing.balancePaisa),
                        "balanceBeforePaisa" to existing.balancePaisa,
                        "balanceAfterPaisa" to newBalancePaisa,
                        "referenceId" to existing.publicId,
                        "note" to summary,
                        "actorEmail" to actorEmail,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, adminRef.path)
            throw e
        }

        existing.copy(
            displayName = newName,
            balancePaisa = newBalancePaisa,
            reservedPaisa = liveReserved,
            updatedAtEpochMs = nowMs,
        )
    }

    override suspend fun setUserLinkActive(
        session: AdminSession?,
        userId: String,
        isActive: Boolean,
    ): Result<AdminUserRecord> = runCatching {
        requireOnline()
        requireUserId()

        val adminRef = db.collection(COL_ADMIN_USERS).document(userId)
        val adminSnap = try {
            adminRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, adminRef.path)
            throw e
        }
        if (!adminSnap.exists()) throw IllegalArgumentException("User record not found.")

        val existing = adminSnap.toAdminUserRecord()
        val publicRef = db.collection(COL_PUBLIC_USERS).document(existing.publicId)

        try {
            val batch = db.batch()
            batch.update(
                adminRef,
                mapOf(
                    "isActive" to isActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.update(
                publicRef,
                mapOf(
                    "isActive" to isActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, adminRef.path)
            throw e
        }

        existing.copy(isActive = isActive, updatedAtEpochMs = System.currentTimeMillis())
    }

    override suspend fun regenerateUserPublicLink(
        session: AdminSession?,
        userId: String,
    ): Result<AdminUserRecord> = runCatching {
        requireOnline()
        val uid = requireUserId()

        val adminRef = db.collection(COL_ADMIN_USERS).document(userId)
        val adminSnap = try {
            adminRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, adminRef.path)
            throw e
        }
        if (!adminSnap.exists()) throw IllegalArgumentException("User record not found.")

        val existing = adminSnap.toAdminUserRecord()
        val oldPublicRef = db.collection(COL_PUBLIC_USERS).document(existing.publicId)
        val oldPublicSnap = oldPublicRef.get().await()
        val liveReserved = oldPublicSnap.getLong("reservedPaisa") ?: existing.reservedPaisa

        var nextPublicId = ValidationUtils.generateSecurePublicId()
        while (db.collection(COL_PUBLIC_USERS).document(nextPublicId).get().await().exists()) {
            nextPublicId = ValidationUtils.generateSecurePublicId()
        }

        val nextPublicRef = db.collection(COL_PUBLIC_USERS).document(nextPublicId)
        val newPublicPayload = mutableMapOf<String, Any>(
            "publicId" to nextPublicId,
            "userId" to existing.id,
            "ownerUid" to uid,
            "displayName" to existing.displayName,
            "balancePaisa" to existing.balancePaisa,
            "reservedPaisa" to liveReserved,
            "isActive" to existing.isActive,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!existing.avatarUri.isNullOrBlank()) {
            newPublicPayload["avatarUri"] = existing.avatarUri
        }

        try {
            val batch = db.batch()
            if (oldPublicSnap.exists()) {
                batch.delete(oldPublicRef)
            }
            batch.set(nextPublicRef, newPublicPayload)
            batch.update(
                adminRef,
                mapOf(
                    "publicId" to nextPublicId,
                    "reservedPaisa" to liveReserved,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, nextPublicRef.path)
            throw e
        }

        existing.copy(
            publicId = nextPublicId,
            reservedPaisa = liveReserved,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteUserRecord(
        session: AdminSession?,
        userId: String,
    ): Result<Unit> = runCatching {
        requireOnline()
        requireUserId()

        val adminRef = db.collection(COL_ADMIN_USERS).document(userId)
        val adminSnap = try {
            adminRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, adminRef.path)
            throw e
        }
        if (!adminSnap.exists()) return@runCatching

        val existing = adminSnap.toAdminUserRecord()
        val publicRef = db.collection(COL_PUBLIC_USERS).document(existing.publicId)

        try {
            val batch = db.batch()
            batch.delete(adminRef)
            batch.delete(publicRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, adminRef.path)
            throw e
        }

        mediaStorageHelper?.deleteStoredImage(existing.avatarUri)
    }

    override suspend fun updateDepositStatus(
        session: AdminSession?,
        requestId: String,
        targetStatus: RequestStatus,
    ): Result<DepositRecord> = runCatching {
        requireOnline()
        val uid = requireUserId()
        val actorEmail = requireAdminActorEmail()

        val depositRef = db.collection(COL_DEPOSIT_REQUESTS).document(requestId)
        val depSnap = try {
            depositRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, depositRef.path)
            throw e
        }
        if (!depSnap.exists()) throw IllegalArgumentException("Deposit request not found.")

        val deposit = depSnap.toDepositRecord()
        val publicId = depSnap.getString("publicId") ?: throw IllegalStateException("Deposit missing publicId.")

        if (deposit.status.isTerminal || deposit.isCredited) {
            throw IllegalStateException(
                "Deposit ${deposit.requestId} is already finalized (${deposit.status.label}) and cannot be processed again."
            )
        }

        val statusRef = db.collection(COL_PUBLIC_USERS)
            .document(publicId)
            .collection(SUBCOL_REQUEST_STATUSES)
            .document(requestId)
        val statusSnap = statusRef.get().await()
        val nowMs = System.currentTimeMillis()

        val batch = db.batch()
        val nextCredited = targetStatus == RequestStatus.APPROVED

        batch.update(
            depositRef,
            mapOf(
                "status" to targetStatus.name,
                "isCredited" to nextCredited,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        if (statusSnap.exists()) {
            batch.update(
                statusRef,
                mapOf(
                    "status" to targetStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        if (targetStatus == RequestStatus.APPROVED) {
            val adminUserRef = db.collection(COL_ADMIN_USERS).document(deposit.userId)
            val publicUserRef = db.collection(COL_PUBLIC_USERS).document(publicId)
            val adminUserSnap = adminUserRef.get().await()
            val publicUserSnap = publicUserRef.get().await()

            if (!publicUserSnap.exists() && !adminUserSnap.exists()) {
                throw IllegalStateException("Associated user record no longer exists.")
            }

            val beforePaisa = publicUserSnap.getLong("balancePaisa")
                ?: adminUserSnap.getLong("balancePaisa")
                ?: 0L
            val afterPaisa = beforePaisa + deposit.amountPaisa

            if (adminUserSnap.exists()) {
                batch.update(
                    adminUserRef,
                    mapOf(
                        "balancePaisa" to afterPaisa,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            if (publicUserSnap.exists()) {
                batch.update(
                    publicUserRef,
                    mapOf(
                        "balancePaisa" to afterPaisa,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            val entryId = ValidationUtils.generateRequestId("LOG")
            batch.set(
                db.collection(COL_LEDGER_ENTRIES).document(entryId),
                mapOf(
                    "entryId" to entryId,
                    "userId" to deposit.userId,
                    "ownerUid" to uid,
                    "userDisplayName" to deposit.userDisplayName,
                    "actionType" to LedgerActionType.DEPOSIT_APPROVED.name,
                    "deltaPaisa" to deposit.amountPaisa,
                    "balanceBeforePaisa" to beforePaisa,
                    "balanceAfterPaisa" to afterPaisa,
                    "referenceId" to deposit.requestId,
                    "note" to "Approved bKash deposit (TrxID: ${deposit.transactionId}, Sender: ${deposit.senderBkashNumber})",
                    "actorEmail" to actorEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, depositRef.path)
            throw e
        }

        deposit.copy(
            status = targetStatus,
            isCredited = nextCredited,
            updatedAtEpochMs = nowMs,
        )
    }

    override suspend fun updateWithdrawalStatus(
        session: AdminSession?,
        requestId: String,
        targetStatus: RequestStatus,
    ): Result<WithdrawalRecord> = runCatching {
        requireOnline()
        val uid = requireUserId()
        val actorEmail = requireAdminActorEmail()

        val withdrawalRef = db.collection(COL_WITHDRAWAL_REQUESTS).document(requestId)
        val wdrSnap = try {
            withdrawalRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, withdrawalRef.path)
            throw e
        }
        if (!wdrSnap.exists()) throw IllegalArgumentException("Withdrawal request not found.")

        val withdrawal = wdrSnap.toWithdrawalRecord()
        val publicId = wdrSnap.getString("publicId") ?: throw IllegalStateException("Withdrawal missing publicId.")

        if (withdrawal.status.isTerminal || withdrawal.isDeducted) {
            throw IllegalStateException(
                "Withdrawal ${withdrawal.requestId} is already finalized (${withdrawal.status.label}) and cannot be processed again."
            )
        }

        val statusRef = db.collection(COL_PUBLIC_USERS)
            .document(publicId)
            .collection(SUBCOL_REQUEST_STATUSES)
            .document(requestId)
        val statusSnap = statusRef.get().await()
        val nowMs = System.currentTimeMillis()
        val batch = db.batch()

        when (targetStatus) {
            RequestStatus.PENDING, RequestStatus.PROCESSING -> {
                batch.update(
                    withdrawalRef,
                    mapOf(
                        "status" to targetStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                if (statusSnap.exists()) {
                    batch.update(
                        statusRef,
                        mapOf(
                            "status" to targetStatus.name,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
            }

            RequestStatus.REJECTED -> {
                val adminUserRef = db.collection(COL_ADMIN_USERS).document(withdrawal.userId)
                val publicUserRef = db.collection(COL_PUBLIC_USERS).document(publicId)
                val adminUserSnap = adminUserRef.get().await()
                val publicUserSnap = publicUserRef.get().await()

                val currentBalance = publicUserSnap.getLong("balancePaisa")
                    ?: adminUserSnap.getLong("balancePaisa")
                    ?: 0L
                val currentReserved = publicUserSnap.getLong("reservedPaisa")
                    ?: adminUserSnap.getLong("reservedPaisa")
                    ?: 0L

                if (withdrawal.isReserved) {
                    val releasedReserved = (currentReserved - withdrawal.amountPaisa).coerceAtLeast(0L)
                    if (adminUserSnap.exists()) {
                        batch.update(
                            adminUserRef,
                            mapOf(
                                "reservedPaisa" to releasedReserved,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }
                    if (publicUserSnap.exists()) {
                        batch.update(
                            publicUserRef,
                            mapOf(
                                "reservedPaisa" to releasedReserved,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }

                    val entryId = ValidationUtils.generateRequestId("LOG")
                    batch.set(
                        db.collection(COL_LEDGER_ENTRIES).document(entryId),
                        mapOf(
                            "entryId" to entryId,
                            "userId" to withdrawal.userId,
                            "ownerUid" to uid,
                            "userDisplayName" to withdrawal.userDisplayName,
                            "actionType" to LedgerActionType.WITHDRAWAL_RELEASED.name,
                            "deltaPaisa" to 0L,
                            "balanceBeforePaisa" to currentBalance,
                            "balanceAfterPaisa" to currentBalance,
                            "referenceId" to withdrawal.requestId,
                            "note" to "Rejected bank withdrawal & released reservation of ${CurrencyFormatter.formatBdt(withdrawal.amountPaisa)}",
                            "actorEmail" to actorEmail,
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }

                batch.update(
                    withdrawalRef,
                    mapOf(
                        "status" to RequestStatus.REJECTED.name,
                        "isReserved" to false,
                        "isDeducted" to false,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                if (statusSnap.exists()) {
                    batch.update(
                        statusRef,
                        mapOf(
                            "status" to RequestStatus.REJECTED.name,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
            }

            RequestStatus.APPROVED -> {
                val adminUserRef = db.collection(COL_ADMIN_USERS).document(withdrawal.userId)
                val publicUserRef = db.collection(COL_PUBLIC_USERS).document(publicId)
                val adminUserSnap = adminUserRef.get().await()
                val publicUserSnap = publicUserRef.get().await()

                if (!publicUserSnap.exists() && !adminUserSnap.exists()) {
                    throw IllegalStateException("Associated user record no longer exists.")
                }

                val beforePaisa = publicUserSnap.getLong("balancePaisa")
                    ?: adminUserSnap.getLong("balancePaisa")
                    ?: 0L
                val currentReserved = publicUserSnap.getLong("reservedPaisa")
                    ?: adminUserSnap.getLong("reservedPaisa")
                    ?: 0L

                val unreservedRequired = if (withdrawal.isReserved) 0L else withdrawal.amountPaisa
                val availableForUnreserved = (beforePaisa - currentReserved).coerceAtLeast(0L)
                if (beforePaisa < withdrawal.amountPaisa || availableForUnreserved < unreservedRequired) {
                    throw IllegalStateException("Insufficient user balance to finalize withdrawal.")
                }

                val afterPaisa = beforePaisa - withdrawal.amountPaisa
                val nextReserved = if (withdrawal.isReserved) {
                    (currentReserved - withdrawal.amountPaisa).coerceAtLeast(0L)
                } else {
                    currentReserved
                }

                if (adminUserSnap.exists()) {
                    batch.update(
                        adminUserRef,
                        mapOf(
                            "balancePaisa" to afterPaisa,
                            "reservedPaisa" to nextReserved,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
                if (publicUserSnap.exists()) {
                    batch.update(
                        publicUserRef,
                        mapOf(
                            "balancePaisa" to afterPaisa,
                            "reservedPaisa" to nextReserved,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }

                batch.update(
                    withdrawalRef,
                    mapOf(
                        "status" to RequestStatus.APPROVED.name,
                        "isReserved" to false,
                        "isDeducted" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                if (statusSnap.exists()) {
                    batch.update(
                        statusRef,
                        mapOf(
                            "status" to RequestStatus.APPROVED.name,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }

                val entryId = ValidationUtils.generateRequestId("LOG")
                batch.set(
                    db.collection(COL_LEDGER_ENTRIES).document(entryId),
                    mapOf(
                        "entryId" to entryId,
                        "userId" to withdrawal.userId,
                        "ownerUid" to uid,
                        "userDisplayName" to withdrawal.userDisplayName,
                        "actionType" to LedgerActionType.WITHDRAWAL_APPROVED.name,
                        "deltaPaisa" to -withdrawal.amountPaisa,
                        "balanceBeforePaisa" to beforePaisa,
                        "balanceAfterPaisa" to afterPaisa,
                        "referenceId" to withdrawal.requestId,
                        "note" to "Approved bank withdrawal to ${withdrawal.bankName} (${withdrawal.accountHolderName} • ${withdrawal.bankAccountNumber})",
                        "actorEmail" to actorEmail,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, withdrawalRef.path)
            throw e
        }

        withdrawal.copy(
            status = targetStatus,
            isReserved = if (targetStatus.isTerminal) false else withdrawal.isReserved,
            isDeducted = targetStatus == RequestStatus.APPROVED,
            updatedAtEpochMs = nowMs,
        )
    }

    override suspend fun updateBkashPaymentNumber(
        session: AdminSession?,
        bkashNumberRaw: String,
    ): Result<SiteSettingsModel> = runCatching {
        requireOnline()
        val uid = requireUserId()
        val validBkash = ValidationUtils.validateAndNormalizeBkashNumber(bkashNumberRaw).getOrThrow()
        val settingsRef = db.collection(COL_SITE_SETTINGS).document(DOC_GLOBAL_SETTINGS)

        try {
            val snap = settingsRef.get().await()
            if (!snap.exists()) {
                val baseUrl = ValidationUtils.sanitizeConfiguredBaseUrl(BuildConfig.DEFAULT_PRODUCTION_BASE_URL)
                settingsRef.set(
                    mapOf(
                        "ownerUid" to uid,
                        "bkashNumber" to validBkash,
                        "productionBaseUrl" to baseUrl,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                return@runCatching SiteSettingsModel(
                    bkashNumber = validBkash,
                    productionBaseUrl = baseUrl,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            } else {
                val updates = mutableMapOf<String, Any>(
                    "bkashNumber" to validBkash,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (snap.getString("ownerUid") == BOOTSTRAP_OWNER_UID) {
                    updates["ownerUid"] = uid
                }
                settingsRef.update(updates).await()
                return@runCatching snap.toSiteSettingsModel().copy(
                    bkashNumber = validBkash,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, settingsRef.path)
            throw e
        }
    }

    override suspend fun updateProductionBaseUrl(
        session: AdminSession?,
        baseUrlRaw: String,
    ): Result<SiteSettingsModel> = runCatching {
        requireOnline()
        val uid = requireUserId()
        val validBaseUrl = ValidationUtils.validateProductionBaseUrl(baseUrlRaw).getOrThrow()
        val settingsRef = db.collection(COL_SITE_SETTINGS).document(DOC_GLOBAL_SETTINGS)

        try {
            val snap = settingsRef.get().await()
            if (!snap.exists()) {
                settingsRef.set(
                    mapOf(
                        "ownerUid" to uid,
                        "bkashNumber" to "01700000000",
                        "productionBaseUrl" to validBaseUrl,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                return@runCatching SiteSettingsModel(
                    productionBaseUrl = validBaseUrl,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            } else {
                val updates = mutableMapOf<String, Any>(
                    "productionBaseUrl" to validBaseUrl,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (snap.getString("ownerUid") == BOOTSTRAP_OWNER_UID) {
                    updates["ownerUid"] = uid
                }
                settingsRef.update(updates).await()
                return@runCatching snap.toSiteSettingsModel().copy(
                    productionBaseUrl = validBaseUrl,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, settingsRef.path)
            throw e
        }
    }

    override suspend fun saveCustomMediaImage(
        session: AdminSession?,
        slot: MediaSlot,
        pickedUri: Uri,
    ): Result<SiteSettingsModel> = runCatching {
        requireOnline()
        requireUserId()
        val helper = mediaStorageHelper
            ?: throw IllegalStateException("Media storage helper is unavailable in this environment.")

        val settingsRef = db.collection(COL_SITE_SETTINGS).document(DOC_GLOBAL_SETTINGS)
        val snap = settingsRef.get().await()
        val current = if (snap.exists()) snap.toSiteSettingsModel() else SiteSettingsModel()
        val previousUri = current.uriForSlot(slot)
        val persistedUri = helper.persistPickedImage(slot, pickedUri, previousUri).getOrThrow()
        saveMediaUriDirectly(session, slot, persistedUri).getOrThrow()
    }

    override suspend fun saveMediaUriDirectly(
        session: AdminSession?,
        slot: MediaSlot,
        uriString: String?,
    ): Result<SiteSettingsModel> = runCatching {
        requireOnline()
        val uid = requireUserId()
        val cleanUri = uriString?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 }
        val fieldName = slot.firestoreFieldName()
        val settingsRef = db.collection(COL_SITE_SETTINGS).document(DOC_GLOBAL_SETTINGS)

        try {
            val snap = settingsRef.get().await()
            if (!snap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "ownerUid" to uid,
                    "bkashNumber" to "01700000000",
                    "productionBaseUrl" to ValidationUtils.sanitizeConfiguredBaseUrl(BuildConfig.DEFAULT_PRODUCTION_BASE_URL),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (cleanUri != null) {
                    payload[fieldName] = cleanUri
                }
                settingsRef.set(payload).await()
                return@runCatching SiteSettingsModel().withSlotUri(slot, cleanUri)
            } else {
                if (cleanUri == null) {
                    mediaStorageHelper?.deleteStoredImage(snap.getString(fieldName))
                }
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (snap.getString("ownerUid") == BOOTSTRAP_OWNER_UID) {
                    updates["ownerUid"] = uid
                }
                if (cleanUri != null) {
                    updates[fieldName] = cleanUri
                } else if (snap.contains(fieldName)) {
                    updates[fieldName] = FieldValue.delete()
                }
                settingsRef.update(updates).await()
                return@runCatching snap.toSiteSettingsModel()
                    .withSlotUri(slot, cleanUri)
                    .copy(updatedAtEpochMs = System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, settingsRef.path)
            throw e
        }
    }

    override suspend fun saveUserAvatarImage(
        session: AdminSession?,
        userId: String,
        pickedUri: Uri,
    ): Result<AdminUserRecord> = runCatching {
        requireOnline()
        requireUserId()
        val helper = mediaStorageHelper
            ?: throw IllegalStateException("Media storage helper is unavailable in this environment.")

        val adminRef = db.collection(COL_ADMIN_USERS).document(userId)
        val snap = adminRef.get().await()
        if (!snap.exists()) throw IllegalArgumentException("User record not found.")
        val existing = snap.toAdminUserRecord()
        val persistedUri = helper.persistUserAvatarImage(userId, pickedUri, existing.avatarUri).getOrThrow()
        saveUserAvatarUriDirectly(session, userId, persistedUri).getOrThrow()
    }

    override suspend fun saveUserAvatarUriDirectly(
        session: AdminSession?,
        userId: String,
        avatarUri: String?,
    ): Result<AdminUserRecord> = runCatching {
        requireOnline()
        requireUserId()
        val cleanUri = avatarUri?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 }

        val adminRef = db.collection(COL_ADMIN_USERS).document(userId)
        val adminSnap = try {
            adminRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, adminRef.path)
            throw e
        }
        if (!adminSnap.exists()) throw IllegalArgumentException("User record not found.")

        val existing = adminSnap.toAdminUserRecord()
        val publicRef = db.collection(COL_PUBLIC_USERS).document(existing.publicId)
        val publicSnap = publicRef.get().await()

        try {
            val batch = db.batch()
            val adminUpdates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (cleanUri != null) {
                adminUpdates["avatarUri"] = cleanUri
            } else if (adminSnap.contains("avatarUri")) {
                adminUpdates["avatarUri"] = FieldValue.delete()
            }
            batch.update(adminRef, adminUpdates)

            if (publicSnap.exists()) {
                val publicUpdates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                if (cleanUri != null) {
                    publicUpdates["avatarUri"] = cleanUri
                } else if (publicSnap.contains("avatarUri")) {
                    publicUpdates["avatarUri"] = FieldValue.delete()
                }
                batch.update(publicRef, publicUpdates)
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, adminRef.path)
            throw e
        }

        if (cleanUri == null && existing.avatarUri != null) {
            mediaStorageHelper?.deleteStoredImage(existing.avatarUri)
        }

        existing.copy(avatarUri = cleanUri, updatedAtEpochMs = System.currentTimeMillis())
    }

    companion object {
        const val COL_SITE_SETTINGS = "site_settings"
        const val DOC_GLOBAL_SETTINGS = "global"
        const val COL_ADMIN_USERS = "admin_users"
        const val COL_PUBLIC_USERS = "public_users"
        const val SUBCOL_REQUEST_STATUSES = "request_statuses"
        const val COL_DEPOSIT_REQUESTS = "deposit_requests"
        const val COL_DEPOSIT_TRX_LOCKS = "deposit_trx_locks"
        const val COL_WITHDRAWAL_REQUESTS = "withdrawal_requests"
        const val COL_LEDGER_ENTRIES = "ledger_entries"

        const val BOOTSTRAP_OWNER_UID = "bootstrap_system"
        const val VERIFIED_PUBLIC_ID = "WB7x9Km2Pq4Rt8Vz3N"
        const val VERIFIED_USER_ID = "usr-verified-wb01"
        const val VERIFIED_USER_NAME = "Tanvir Rahman"
        const val VERIFIED_USER_BALANCE_PAISA = 125_000L // ৳ 1,250.00 BDT
    }
}

private fun FirebaseUser.toAdminSession(): AdminSession = AdminSession(
    email = email?.takeIf { it.isNotBlank() } ?: "$uid@google-auth.local",
    sessionToken = uid,
    signedInAtEpochMs = metadata?.lastSignInTimestamp ?: System.currentTimeMillis(),
)

private fun DocumentSnapshot.readEpochMs(field: String, fallbackMs: Long = System.currentTimeMillis()): Long {
    val ts = getTimestamp(field, ESTIMATE)
    return ts?.toDate()?.time ?: fallbackMs
}

private fun DocumentSnapshot.toSiteSettingsModel(): SiteSettingsModel = SiteSettingsModel(
    bkashNumber = getString("bkashNumber")?.takeIf { it.isNotBlank() } ?: "01700000000",
    productionBaseUrl = ValidationUtils.sanitizeConfiguredBaseUrl(getString("productionBaseUrl")),
    logoUri = getString("logoUri")?.takeIf { it.isNotBlank() },
    mainBannerUri = getString("mainBannerUri")?.takeIf { it.isNotBlank() },
    promoBanner1Uri = getString("promoBanner1Uri")?.takeIf { it.isNotBlank() },
    promoBanner2Uri = getString("promoBanner2Uri")?.takeIf { it.isNotBlank() },
    promoBanner3Uri = getString("promoBanner3Uri")?.takeIf { it.isNotBlank() },
    updatedAtEpochMs = readEpochMs("updatedAt", fallbackMs = 0L),
)

private fun DocumentSnapshot.toPublicUserView(): PublicUserView = PublicUserView(
    publicId = getString("publicId") ?: id,
    displayName = getString("displayName") ?: "User",
    balancePaisa = getLong("balancePaisa") ?: 0L,
    reservedPaisa = getLong("reservedPaisa") ?: 0L,
    updatedAtEpochMs = readEpochMs("updatedAt"),
    avatarUri = getString("avatarUri")?.takeIf { it.isNotBlank() },
)

private fun DocumentSnapshot.toAdminUserRecord(): AdminUserRecord {
    val createdMs = readEpochMs("createdAt")
    return AdminUserRecord(
        id = getString("userId") ?: id,
        publicId = getString("publicId").orEmpty(),
        displayName = getString("displayName").orEmpty(),
        balancePaisa = getLong("balancePaisa") ?: 0L,
        reservedPaisa = getLong("reservedPaisa") ?: 0L,
        isActive = getBoolean("isActive") ?: true,
        createdAtEpochMs = createdMs,
        updatedAtEpochMs = readEpochMs("updatedAt", fallbackMs = createdMs),
        avatarUri = getString("avatarUri")?.takeIf { it.isNotBlank() },
    )
}

private fun DocumentSnapshot.toDepositRecord(): DepositRecord {
    val createdMs = readEpochMs("createdAt")
    return DepositRecord(
        requestId = getString("requestId") ?: id,
        userId = getString("userId").orEmpty(),
        userDisplayName = getString("userDisplayName").orEmpty(),
        amountPaisa = getLong("amountPaisa") ?: 0L,
        senderBkashNumber = getString("senderBkashNumber").orEmpty(),
        transactionId = getString("transactionId").orEmpty(),
        status = RequestStatus.fromStored(getString("status").orEmpty()),
        isCredited = getBoolean("isCredited") ?: false,
        createdAtEpochMs = createdMs,
        updatedAtEpochMs = readEpochMs("updatedAt", fallbackMs = createdMs),
    )
}

private fun DocumentSnapshot.toWithdrawalRecord(): WithdrawalRecord {
    val createdMs = readEpochMs("createdAt")
    return WithdrawalRecord(
        requestId = getString("requestId") ?: id,
        userId = getString("userId").orEmpty(),
        userDisplayName = getString("userDisplayName").orEmpty(),
        amountPaisa = getLong("amountPaisa") ?: 0L,
        bankName = getString("bankName").orEmpty(),
        accountHolderName = getString("accountHolderName").orEmpty(),
        bankAccountNumber = getString("bankAccountNumber").orEmpty(),
        status = RequestStatus.fromStored(getString("status").orEmpty()),
        isReserved = getBoolean("isReserved") ?: false,
        isDeducted = getBoolean("isDeducted") ?: false,
        createdAtEpochMs = createdMs,
        updatedAtEpochMs = readEpochMs("updatedAt", fallbackMs = createdMs),
    )
}

private fun DocumentSnapshot.toLedgerRecord(): LedgerRecord = LedgerRecord(
    entryId = getString("entryId") ?: id,
    userId = getString("userId").orEmpty(),
    userDisplayName = getString("userDisplayName").orEmpty(),
    actionType = LedgerActionType.fromStored(getString("actionType").orEmpty()),
    deltaPaisa = getLong("deltaPaisa") ?: 0L,
    balanceBeforePaisa = getLong("balanceBeforePaisa") ?: 0L,
    balanceAfterPaisa = getLong("balanceAfterPaisa") ?: 0L,
    referenceId = getString("referenceId").orEmpty(),
    note = getString("note").orEmpty(),
    actorEmail = getString("actorEmail").orEmpty(),
    createdAtEpochMs = readEpochMs("createdAt"),
)

private fun MediaSlot.firestoreFieldName(): String = when (this) {
    MediaSlot.APP_LOGO -> "logoUri"
    MediaSlot.MAIN_BANNER -> "mainBannerUri"
    MediaSlot.PROMO_BANNER_1 -> "promoBanner1Uri"
    MediaSlot.PROMO_BANNER_2 -> "promoBanner2Uri"
    MediaSlot.PROMO_BANNER_3 -> "promoBanner3Uri"
}

private fun SiteSettingsModel.uriForSlot(slot: MediaSlot): String? = when (slot) {
    MediaSlot.APP_LOGO -> logoUri
    MediaSlot.MAIN_BANNER -> mainBannerUri
    MediaSlot.PROMO_BANNER_1 -> promoBanner1Uri
    MediaSlot.PROMO_BANNER_2 -> promoBanner2Uri
    MediaSlot.PROMO_BANNER_3 -> promoBanner3Uri
}

private fun SiteSettingsModel.withSlotUri(slot: MediaSlot, uri: String?): SiteSettingsModel = when (slot) {
    MediaSlot.APP_LOGO -> copy(logoUri = uri)
    MediaSlot.MAIN_BANNER -> copy(mainBannerUri = uri)
    MediaSlot.PROMO_BANNER_1 -> copy(promoBanner1Uri = uri)
    MediaSlot.PROMO_BANNER_2 -> copy(promoBanner2Uri = uri)
    MediaSlot.PROMO_BANNER_3 -> copy(promoBanner3Uri = uri)
}
