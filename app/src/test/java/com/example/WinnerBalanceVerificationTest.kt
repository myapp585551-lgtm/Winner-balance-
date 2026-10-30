package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WinnerDatabase
import com.example.data.model.PublicPageState
import com.example.data.model.RequestStatus
import com.example.data.repository.WinnerRepository
import com.example.util.BangkokTimeFormatter
import com.example.util.ControllableConnectivityChecker
import com.example.util.MediaSlot
import com.example.util.OfflineOperationException
import com.example.util.ValidationUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WinnerBalanceVerificationTest {

    private lateinit var database: WinnerDatabase
    private lateinit var connectivity: ControllableConnectivityChecker
    private lateinit var repository: WinnerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WinnerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        connectivity = ControllableConnectivityChecker(initialOnline = true)
        repository = WinnerRepository(
            database = database,
            connectivityChecker = connectivity,
            mediaStorageHelper = null,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun adminAuth_initializeSignInRejectWrongPasswordAndSignOut() = runBlocking {
        val initResult = repository.initializeAdminAccount(
            emailRaw = "admin@winnerportal.org",
            passwordRaw = "StrongPass#2026",
            confirmPasswordRaw = "StrongPass#2026",
        )
        assertTrue(initResult.isSuccess)

        // Duplicate bootstrap must fail
        val secondInit = repository.initializeAdminAccount(
            emailRaw = "other@winnerportal.org",
            passwordRaw = "StrongPass#2026",
            confirmPasswordRaw = "StrongPass#2026",
        )
        assertTrue(secondInit.isFailure)

        repository.signOutAdmin()
        assertEquals(null, repository.activeAdminSession.value)

        // Wrong password must fail
        val badLogin = repository.signInAdmin("admin@winnerportal.org", "WrongPassword99")
        assertTrue(badLogin.isFailure)

        // Valid sign-in succeeds
        val goodLogin = repository.signInAdmin("admin@winnerportal.org", "StrongPass#2026")
        assertTrue(goodLogin.isSuccess)
    }

    @Test
    fun publicUserLinks_resolveActiveDisabledAndInvalidWithoutLogin() = runBlocking {
        val session = repository.initializeAdminAccount(
            "admin@winnerportal.org",
            "StrongPass#2026",
            "StrongPass#2026"
        ).getOrThrow()

        val createdUser = repository.createUser(session, "Rahim Uddin", "1500.75").getOrThrow()
        assertNotEquals("Internal ID must be separate from public ID", createdUser.id, createdUser.publicId)

        // Sign out admin completely: public user page must still load without login
        repository.signOutAdmin()

        val activePage = repository.observePublicUserPage(createdUser.publicId).first()
        assertTrue(activePage is PublicPageState.Active)
        val activeData = activePage as PublicPageState.Active
        assertEquals("Rahim Uddin", activeData.user.displayName)
        assertEquals(150_075L, activeData.user.balancePaisa)

        // Invalid link must return NotFound and NEVER show Rahim's record
        val missingPage = repository.observePublicUserPage("NonExistentPublicId999").first()
        assertTrue(missingPage is PublicPageState.NotFound)

        // Re-login admin and disable link
        val relogin = repository.signInAdmin("admin@winnerportal.org", "StrongPass#2026").getOrThrow()
        repository.setUserLinkActive(relogin, createdUser.id, false).getOrThrow()

        val disabledPage = repository.observePublicUserPage(createdUser.publicId).first()
        assertTrue(disabledPage is PublicPageState.Disabled)
    }

    @Test
    fun depositWorkflow_creditsBalanceOnce_andPreventsDuplicateProcessing() = runBlocking {
        val session = repository.initializeAdminAccount(
            "admin@winnerportal.org",
            "StrongPass#2026",
            "StrongPass#2026"
        ).getOrThrow()

        val user = repository.createUser(session, "Karim Ahmed", "1000.00").getOrThrow()

        // Submit bKash deposit as public user
        val deposit = repository.submitBkashDepositRequest(
            publicIdRaw = user.publicId,
            amountBdtRaw = "500.00",
            senderBkashRaw = "01712345678",
            transactionIdRaw = "trx998877ab",
        ).getOrThrow()

        // Balance must NOT auto-credit before admin approval
        val beforeApproval = (repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active).user
        assertEquals(100_000L, beforeApproval.balancePaisa)

        // Duplicate TrxID must be rejected
        val dupTrx = repository.submitBkashDepositRequest(
            publicIdRaw = user.publicId,
            amountBdtRaw = "500.00",
            senderBkashRaw = "01712345678",
            transactionIdRaw = "TRX998877AB",
        )
        assertTrue(dupTrx.isFailure)

        // Admin moves to Processing, then Approved
        repository.updateDepositStatus(session, deposit.requestId, RequestStatus.PROCESSING).getOrThrow()
        repository.updateDepositStatus(session, deposit.requestId, RequestStatus.APPROVED).getOrThrow()

        val afterApproval = (repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active).user
        assertEquals(150_000L, afterApproval.balancePaisa)

        // Attempting to approve the same deposit a second time MUST fail and NOT credit again
        val duplicateApprove = repository.updateDepositStatus(session, deposit.requestId, RequestStatus.APPROVED)
        assertTrue(duplicateApprove.isFailure)

        val afterDuplicateAttempt = (repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active).user
        assertEquals(150_000L, afterDuplicateAttempt.balancePaisa)
    }

    @Test
    fun withdrawalWorkflow_reservesPendingHold_deductsOnceOnApprove_andReleasesOnReject() = runBlocking {
        val session = repository.initializeAdminAccount(
            "admin@winnerportal.org",
            "StrongPass#2026",
            "StrongPass#2026"
        ).getOrThrow()

        val user = repository.createUser(session, "Farhana Islam", "2000.00").getOrThrow()

        // Exceeding balance must fail
        val overdraft = repository.submitBankWithdrawalRequest(
            publicIdRaw = user.publicId,
            amountBdtRaw = "2500.00",
            bankNameRaw = "BRAC Bank",
            accountHolderNameRaw = "Farhana Islam",
            bankAccountNumberRaw = "1501204455667",
        )
        assertTrue(overdraft.isFailure)

        // First valid withdrawal of 1,200 BDT reserves 1,200 BDT (leaving 800 BDT available)
        val w1 = repository.submitBankWithdrawalRequest(
            publicIdRaw = user.publicId,
            amountBdtRaw = "1200.00",
            bankNameRaw = "BRAC Bank",
            accountHolderNameRaw = "Farhana Islam",
            bankAccountNumberRaw = "1501204455667",
        ).getOrThrow()

        // Concurrent second withdrawal of 1,000 BDT must fail because only 800 BDT is unreserved!
        val concurrentOverdraft = repository.submitBankWithdrawalRequest(
            publicIdRaw = user.publicId,
            amountBdtRaw = "1000.00",
            bankNameRaw = "BRAC Bank",
            accountHolderNameRaw = "Farhana Islam",
            bankAccountNumberRaw = "1501204455667",
        )
        assertTrue(concurrentOverdraft.isFailure)

        // Rejecting w1 releases the 1,200 BDT reservation without deducting balance!
        repository.updateWithdrawalStatus(session, w1.requestId, RequestStatus.REJECTED).getOrThrow()
        val stateAfterReject = (repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active).user
        assertEquals(200_000L, stateAfterReject.balancePaisa)
        assertEquals(0L, stateAfterReject.reservedPaisa)

        // Now submitting a 1,500 BDT withdrawal succeeds and approving deducts strictly once
        val w2 = repository.submitBankWithdrawalRequest(
            publicIdRaw = user.publicId,
            amountBdtRaw = "1500.00",
            bankNameRaw = "City Bank",
            accountHolderNameRaw = "Farhana Islam",
            bankAccountNumberRaw = "230998877665",
        ).getOrThrow()

        repository.updateWithdrawalStatus(session, w2.requestId, RequestStatus.APPROVED).getOrThrow()
        val stateAfterApprove = (repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active).user
        assertEquals(50_000L, stateAfterApprove.balancePaisa) // 500.00 BDT remaining
        assertEquals(0L, stateAfterApprove.reservedPaisa)

        // Duplicate approval attempt fails
        val dupWdrApprove = repository.updateWithdrawalStatus(session, w2.requestId, RequestStatus.APPROVED)
        assertTrue(dupWdrApprove.isFailure)
        val stateFinal = (repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active).user
        assertEquals(50_000L, stateFinal.balancePaisa)
    }

    @Test
    fun securityAndMediaAndDomainSettings_enforcedAndPersisted() = runBlocking {
        val session = repository.initializeAdminAccount(
            "admin@winnerportal.org",
            "StrongPass#2026",
            "StrongPass#2026"
        ).getOrThrow()

        // Localhost and unconfigured winnerbalance.com domains must be rejected; actual deployed run.app URLs must succeed
        assertTrue(ValidationUtils.validateProductionBaseUrl("http://localhost:3000").isFailure)
        assertTrue(ValidationUtils.validateProductionBaseUrl("https://localhost").isFailure)
        assertTrue(ValidationUtils.validateProductionBaseUrl("https://winnerbalance.com").isFailure)
        assertTrue(ValidationUtils.validateProductionBaseUrl("https://www.winnerbalance.com").isFailure)
        assertTrue(ValidationUtils.validateProductionBaseUrl(BuildConfig.DEFAULT_PRODUCTION_BASE_URL).isSuccess)
        assertTrue(ValidationUtils.validateProductionBaseUrl(BuildConfig.DEV_APP_BASE_URL).isSuccess)

        // Generated links must use the actual deployed URL with 404-free /?u= format, and parser must accept both ?u= and /u/
        val generatedLink = ValidationUtils.buildPublicUserUrl("https://winnerbalance.com", "WB7x9Km2Pq4Rt8Vz3N")
        assertEquals("${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}/?u=WB7x9Km2Pq4Rt8Vz3N", generatedLink)
        assertEquals(
            "WB7x9Km2Pq4Rt8Vz3N",
            ValidationUtils.extractPublicIdFromLinkOrInput(generatedLink).getOrThrow()
        )
        assertEquals(
            "WB7x9Km2Pq4Rt8Vz3N",
            ValidationUtils.extractPublicIdFromLinkOrInput("${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}/u/WB7x9Km2Pq4Rt8Vz3N").getOrThrow()
        )

        // Persist logo and all 4 banners
        repository.saveMediaUriDirectly(session, MediaSlot.APP_LOGO, "file:///data/logo.png").getOrThrow()
        repository.saveMediaUriDirectly(session, MediaSlot.MAIN_BANNER, "file:///data/main.jpg").getOrThrow()
        repository.saveMediaUriDirectly(session, MediaSlot.PROMO_BANNER_1, "file:///data/p1.jpg").getOrThrow()
        repository.saveMediaUriDirectly(session, MediaSlot.PROMO_BANNER_2, "file:///data/p2.jpg").getOrThrow()
        repository.saveMediaUriDirectly(session, MediaSlot.PROMO_BANNER_3, "file:///data/p3.jpg").getOrThrow()

        val settings = repository.siteSettingsFlow.first()
        assertEquals("file:///data/logo.png", settings.logoUri)
        assertEquals("file:///data/main.jpg", settings.mainBannerUri)
        assertEquals("file:///data/p1.jpg", settings.promoBanner1Uri)
        assertEquals("file:///data/p2.jpg", settings.promoBanner2Uri)
        assertEquals("file:///data/p3.jpg", settings.promoBanner3Uri)

        // Unauthenticated caller cannot mutate balances, settings, or list users
        repository.signOutAdmin()
        val unauthCreate = repository.createUser(null, "Attacker", "99999")
        assertTrue(unauthCreate.isFailure)
        assertTrue(unauthCreate.exceptionOrNull() is SecurityException)
        assertEquals(emptyList<Any>(), repository.observeAdminUsers(null).first())
    }

    @Test
    fun offlineMode_blocksSensitiveMutationsWithExactMessage() = runBlocking {
        val session = repository.initializeAdminAccount(
            "admin@winnerportal.org",
            "StrongPass#2026",
            "StrongPass#2026"
        ).getOrThrow()
        val user = repository.createUser(session, "Sadia Akter", "1000.00").getOrThrow()

        // Simulate internet connection loss
        connectivity.setOnline(false)
        assertFalse(connectivity.isCurrentlyOnline())

        val offlineDeposit = repository.submitBkashDepositRequest(
            user.publicId, "200.00", "01812345678", "BKASH12345"
        )
        assertTrue(offlineDeposit.exceptionOrNull() is OfflineOperationException)
        assertEquals("Internet connection required.", offlineDeposit.exceptionOrNull()?.message)

        val offlineBalanceEdit = repository.updateUserDetailsAndBalance(
            session, user.id, "Sadia Akter", "5000.00"
        )
        assertTrue(offlineBalanceEdit.exceptionOrNull() is OfflineOperationException)
        assertEquals("Internet connection required.", offlineBalanceEdit.exceptionOrNull()?.message)
    }

    @Test
    fun bangkokTimezone_formatsExclusivelyInAsiaBangkokRegardlessOfDeviceTimezone() {
        val originalTz = TimeZone.getDefault()
        try {
            // Set host JVM timezone to New York (UTC-4/UTC-5)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            // 2026-10-29T00:00:00Z == 2026-10-29 07:00:00 AM ICT (Asia/Bangkok, UTC+7)
            val epochUtcMidnight = 1_793_232_000_000L
            val formatted = BangkokTimeFormatter.formatDateTime(epochUtcMidnight)
            assertEquals("29 Oct 2026, 07:00:00 AM ICT", formatted)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun userAvatarCustomization_persistsOnPublicUserPageAndRequiresAdminAuth() = runBlocking {
        val session = repository.initializeAdminAccount(
            "admin@winnerportal.org",
            "StrongPass#2026",
            "StrongPass#2026"
        ).getOrThrow()

        val user = repository.createUser(session, "Mahmudul Hasan", "4500.00").getOrThrow()
        assertEquals(null, user.avatarUri)

        // Admin saves custom user logo photo URI
        repository.saveUserAvatarUriDirectly(
            session = session,
            userId = user.id,
            avatarUri = "file:///data/user_avatar_mahmudul.png",
        ).getOrThrow()

        val updatedAdminUser = repository.observeAdminUsers(session).first().first { it.id == user.id }
        assertEquals("file:///data/user_avatar_mahmudul.png", updatedAdminUser.avatarUri)

        // Public user page immediately reflects the customized user logo without login
        repository.signOutAdmin()
        val publicState = repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active
        assertEquals("file:///data/user_avatar_mahmudul.png", publicState.user.avatarUri)

        // Unauthenticated caller cannot modify a user's logo photo
        val unauthAttempt = repository.saveUserAvatarUriDirectly(
            session = null,
            userId = user.id,
            avatarUri = "file:///data/attacker.png",
        )
        assertTrue(unauthAttempt.isFailure)
        assertTrue(unauthAttempt.exceptionOrNull() is SecurityException)

        // Re-authenticated admin can remove/reset custom user logo photo
        val relogin = repository.signInAdmin("admin@winnerportal.org", "StrongPass#2026").getOrThrow()
        repository.saveUserAvatarUriDirectly(
            session = relogin,
            userId = user.id,
            avatarUri = null,
        ).getOrThrow()

        val resetPublicState = repository.observePublicUserPage(user.publicId).first() as PublicPageState.Active
        assertEquals(null, resetPublicState.user.avatarUri)
    }
}
