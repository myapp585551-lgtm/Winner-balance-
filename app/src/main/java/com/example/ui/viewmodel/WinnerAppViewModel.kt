package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.data.model.AdminSession
import com.example.data.model.AdminUserRecord
import com.example.data.model.DepositRecord
import com.example.data.model.LedgerRecord
import com.example.data.model.PublicPageState
import com.example.data.model.RequestStatus
import com.example.data.model.SiteSettingsModel
import com.example.data.model.WithdrawalRecord
import com.example.data.repository.WinnerDataSource
import com.example.data.repository.WinnerFirestoreRepository
import com.example.util.AndroidConnectivityObserver
import com.example.util.BangkokTimeFormatter
import com.example.util.MediaSlot
import com.example.util.MediaStorageHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiFeedbackMessage(
    val id: Long = System.nanoTime(),
    val message: String,
    val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WinnerAppViewModel(
    private val repository: WinnerDataSource,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val adminSession: StateFlow<AdminSession?> = repository.activeAdminSession

    val siteSettings: StateFlow<SiteSettingsModel> = repository.siteSettingsFlow
        .catch { err ->
            Log.e(VIEW_MODEL_TAG, "Failed to observe site settings", err)
            emit(SiteSettingsModel())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SiteSettingsModel())

    val adminUsers: StateFlow<List<AdminUserRecord>> = adminSession
        .flatMapLatest { session ->
            repository.observeAdminUsers(session)
                .catch { err ->
                    Log.e(VIEW_MODEL_TAG, "Failed to observe admin users", err)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val adminDeposits: StateFlow<List<DepositRecord>> = adminSession
        .flatMapLatest { session ->
            repository.observeAdminDeposits(session)
                .catch { err ->
                    Log.e(VIEW_MODEL_TAG, "Failed to observe admin deposits", err)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val adminWithdrawals: StateFlow<List<WithdrawalRecord>> = adminSession
        .flatMapLatest { session ->
            repository.observeAdminWithdrawals(session)
                .catch { err ->
                    Log.e(VIEW_MODEL_TAG, "Failed to observe admin withdrawals", err)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val adminLedger: StateFlow<List<LedgerRecord>> = adminSession
        .flatMapLatest { session ->
            repository.observeAdminLedger(session)
                .catch { err ->
                    Log.e(VIEW_MODEL_TAG, "Failed to observe admin ledger", err)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _bangkokClockText = MutableStateFlow(BangkokTimeFormatter.formatLiveHeaderClock())
    val bangkokClockText: StateFlow<String> = _bangkokClockText.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<UiFeedbackMessage?>(null)
    val feedbackMessage: StateFlow<UiFeedbackMessage?> = _feedbackMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _editingMediaSlot = MutableStateFlow<MediaSlot?>(null)
    val editingMediaSlot: StateFlow<MediaSlot?> = _editingMediaSlot.asStateFlow()

    private val _editingAvatarUserId = MutableStateFlow<String?>(null)
    val editingAvatarUser: StateFlow<AdminUserRecord?> = combine(
        _editingAvatarUserId,
        adminUsers,
    ) { targetId, users ->
        if (targetId == null) null else users.firstOrNull { it.id == targetId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                _bangkokClockText.value = BangkokTimeFormatter.formatLiveHeaderClock()
                delay(1_000L)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureColdBootstrapSeeded()
        }
    }

    fun observePublicUserPage(publicId: String): Flow<PublicPageState> {
        return repository.observePublicUserPage(publicId)
            .catch { err ->
                Log.e(VIEW_MODEL_TAG, "Failed to observe public user page for $publicId", err)
                emit(PublicPageState.Error(err.localizedMessage ?: "Failed to load user page."))
            }
    }

    fun showFeedback(message: String, isError: Boolean = false) {
        _feedbackMessage.value = UiFeedbackMessage(message = message, isError = isError)
    }

    fun dismissFeedback() {
        _feedbackMessage.value = null
    }

    fun openMediaEditor(slot: MediaSlot) {
        if (adminSession.value == null) {
            showFeedback("Administrator sign-in is required to edit images.", isError = true)
            return
        }
        _editingMediaSlot.value = slot
    }

    fun closeMediaEditor() {
        _editingMediaSlot.value = null
    }

    fun openUserAvatarEditor(userId: String) {
        if (adminSession.value == null) {
            showFeedback("Administrator sign-in is required to edit user photos.", isError = true)
            return
        }
        _editingAvatarUserId.value = userId
    }

    fun openUserAvatarEditorByPublicId(publicId: String) {
        if (adminSession.value == null) {
            showFeedback("Administrator sign-in is required to edit user photos.", isError = true)
            return
        }
        val matched = adminUsers.value.firstOrNull { it.publicId == publicId }
        if (matched != null) {
            _editingAvatarUserId.value = matched.id
        } else {
            showFeedback("Open the Admin Dashboard Users tab to edit this user's photo.", isError = true)
        }
    }

    fun closeUserAvatarEditor() {
        _editingAvatarUserId.value = null
    }

    // --- Admin Authentication (Google Sign-In via Credential Manager) ---

    fun onGoogleAdminAuthenticated() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureColdBootstrapSeeded()
        }
        showFeedback("Signed in to Winner Balance Admin Dashboard.")
    }

    fun signOutAdmin(onSignedOut: () -> Unit = {}) {
        repository.signOutAdmin()
        _editingMediaSlot.value = null
        _editingAvatarUserId.value = null
        showFeedback("Signed out of administrator session.")
        onSignedOut()
    }

    // --- Admin User & Balance Operations ---

    fun createUser(
        displayName: String,
        initialBalanceBdt: String,
        initialAvatarUri: Uri? = null,
        onSuccess: (AdminUserRecord) -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createUser(adminSession.value, displayName, initialBalanceBdt, initialAvatarUri)
            _isBusy.value = false
            result.fold(
                onSuccess = { created ->
                    showFeedback("Created user page for ${created.displayName}.")
                    onSuccess(created)
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to create user.", isError = true)
                }
            )
        }
    }

    fun updateUserDetailsAndBalance(
        userId: String,
        newName: String,
        newBalanceBdt: String,
        auditReason: String,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateUserDetailsAndBalance(
                session = adminSession.value,
                userId = userId,
                newDisplayNameRaw = newName,
                newBalanceBdtRaw = newBalanceBdt,
                auditReasonRaw = auditReason,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { updated ->
                    showFeedback("Saved changes for ${updated.displayName}.")
                    onSuccess()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to update user.", isError = true)
                }
            )
        }
    }

    fun toggleUserActive(userId: String, targetActive: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.setUserLinkActive(adminSession.value, userId, targetActive)
            _isBusy.value = false
            result.fold(
                onSuccess = { user ->
                    val stateLabel = if (user.isActive) "enabled" else "disabled"
                    showFeedback("Public link for ${user.displayName} is now $stateLabel.")
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to change link status.", isError = true)
                }
            )
        }
    }

    fun regenerateUserLink(userId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.regenerateUserPublicLink(adminSession.value, userId)
            _isBusy.value = false
            result.fold(
                onSuccess = { user ->
                    showFeedback("Regenerated public link for ${user.displayName}.")
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to regenerate link.", isError = true)
                }
            )
        }
    }

    fun deleteUser(userId: String, userName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteUserRecord(adminSession.value, userId)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    showFeedback("Deleted user record '$userName'.")
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to delete user record.", isError = true)
                }
            )
        }
    }

    // --- Admin Payment Processing ---

    fun updateDepositStatus(requestId: String, newStatus: RequestStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateDepositStatus(adminSession.value, requestId, newStatus)
            _isBusy.value = false
            result.fold(
                onSuccess = { updated ->
                    showFeedback("Deposit ${updated.requestId} marked as ${updated.status.label}.")
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to update deposit status.", isError = true)
                }
            )
        }
    }

    fun updateWithdrawalStatus(requestId: String, newStatus: RequestStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateWithdrawalStatus(adminSession.value, requestId, newStatus)
            _isBusy.value = false
            result.fold(
                onSuccess = { updated ->
                    showFeedback("Withdrawal ${updated.requestId} marked as ${updated.status.label}.")
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to update withdrawal status.", isError = true)
                }
            )
        }
    }

    // --- Admin Website Settings & Media Uploads ---

    fun saveBkashPaymentNumber(bkashNumber: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateBkashPaymentNumber(adminSession.value, bkashNumber)
            _isBusy.value = false
            result.fold(
                onSuccess = { settings ->
                    showFeedback("Official bKash number updated to ${settings.bkashNumber}.")
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Invalid bKash number.", isError = true)
                }
            )
        }
    }

    fun saveProductionBaseUrl(baseUrl: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProductionBaseUrl(adminSession.value, baseUrl)
            _isBusy.value = false
            result.fold(
                onSuccess = { settings ->
                    showFeedback("Production domain updated to ${settings.productionBaseUrl}.")
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Invalid production URL.", isError = true)
                }
            )
        }
    }

    fun saveCustomMedia(slot: MediaSlot, pickedUri: Uri, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveCustomMediaImage(adminSession.value, slot, pickedUri)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _editingMediaSlot.value = null
                    showFeedback("${slot.displayTitle} saved permanently.")
                    onCompleted()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to upload image.", isError = true)
                }
            )
        }
    }

    fun resetCustomMedia(slot: MediaSlot, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveMediaUriDirectly(adminSession.value, slot, null)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _editingMediaSlot.value = null
                    showFeedback("${slot.displayTitle} restored to default artwork.")
                    onCompleted()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to remove custom image.", isError = true)
                }
            )
        }
    }

    fun saveCustomUserAvatar(userId: String, pickedUri: Uri, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveUserAvatarImage(adminSession.value, userId, pickedUri)
            _isBusy.value = false
            result.fold(
                onSuccess = { updated ->
                    _editingAvatarUserId.value = null
                    showFeedback("Updated profile logo for ${updated.displayName}.")
                    onCompleted()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to upload user photo.", isError = true)
                }
            )
        }
    }

    fun resetCustomUserAvatar(userId: String, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveUserAvatarUriDirectly(adminSession.value, userId, null)
            _isBusy.value = false
            result.fold(
                onSuccess = { updated ->
                    _editingAvatarUserId.value = null
                    showFeedback("Removed custom profile logo for ${updated.displayName}.")
                    onCompleted()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to remove user photo.", isError = true)
                }
            )
        }
    }

    // --- Public User Deposit & Withdrawal Submissions ---

    fun submitPublicDeposit(
        publicId: String,
        amountBdt: String,
        senderBkash: String,
        trxId: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitBkashDepositRequest(
                publicIdRaw = publicId,
                amountBdtRaw = amountBdt,
                senderBkashRaw = senderBkash,
                transactionIdRaw = trxId,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { created ->
                    showFeedback("bKash deposit request (${created.requestId}) submitted for verification.")
                    onSuccess()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Deposit submission failed.", isError = true)
                }
            )
        }
    }

    fun submitPublicWithdrawal(
        publicId: String,
        amountBdt: String,
        bankName: String,
        accountHolderName: String,
        accountNumber: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitBankWithdrawalRequest(
                publicIdRaw = publicId,
                amountBdtRaw = amountBdt,
                bankNameRaw = bankName,
                accountHolderNameRaw = accountHolderName,
                bankAccountNumberRaw = accountNumber,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { created ->
                    showFeedback("Bank withdrawal request (${created.requestId}) submitted.")
                    onSuccess()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Withdrawal submission failed.", isError = true)
                }
            )
        }
    }

    companion object {
        private const val VIEW_MODEL_TAG = "WinnerAppViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                if (com.google.firebase.FirebaseApp.getApps(app).isEmpty()) {
                    com.google.firebase.FirebaseApp.initializeApp(app)
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val connectivity = AndroidConnectivityObserver(app)
                val mediaHelper = MediaStorageHelper(app)
                val repo = WinnerFirestoreRepository(
                    db = firestore,
                    auth = Firebase.auth,
                    connectivityChecker = connectivity,
                    mediaStorageHelper = mediaHelper,
                )
                return WinnerAppViewModel(repo) as T
            }
        }
    }
}
