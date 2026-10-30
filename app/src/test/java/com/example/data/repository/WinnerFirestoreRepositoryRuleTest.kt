package com.example.data.repository

import com.example.BuildConfig
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.PublicPageState
import com.example.data.model.RequestStatus
import com.example.util.ControllableConnectivityChecker
import com.example.util.ValidationUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class WinnerFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun createRepository(): WinnerFirestoreRepository {
        val connectivity = ControllableConnectivityChecker(initialOnline = true)
        return WinnerFirestoreRepository(
            db = firestore,
            auth = auth,
            connectivityChecker = connectivity,
            mediaStorageHelper = null,
        )
    }

    @Test
    fun coldBootstrapAndQueryParamUrl_resolvesVerifiedUserFromFirebaseWithoutAdminLogin() = runBlocking {
        auth.signOut()
        val repo = createRepository()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureColdBootstrapSeeded().getOrThrow()
        }

        val fullUrl = "${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}/?u=WB7x9Km2Pq4Rt8Vz3N"
        val extractedId = ValidationUtils.extractPublicIdFromLinkOrInput(fullUrl).getOrThrow()
        assertEquals("WB7x9Km2Pq4Rt8Vz3N", extractedId)

        val publicState = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePublicUserPage(fullUrl).first { it is PublicPageState.Active }
        } as PublicPageState.Active

        assertEquals("WB7x9Km2Pq4Rt8Vz3N", publicState.user.publicId)
        assertEquals("Tanvir Rahman", publicState.user.displayName)
        assertEquals(125_000L, publicState.user.balancePaisa)
    }

    @Test
    fun adminCreateDepositApproveAndWithdrawWorkflow_executesAtomicallyAgainstRules() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID().toString().take(8)}@test.com"
        signInTestUser(aliceEmail)
        val repo = createRepository()
        val session = repo.activeAdminSession.value
        assertNotNull(session)

        val createdUser = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createUser(session, "Nusrat Jahan", "2000.00").getOrThrow()
        }
        assertEquals(200_000L, createdUser.balancePaisa)

        // Sign out admin completely: public user opens page via ?u= and submits bKash deposit & bank withdrawal
        auth.signOut()
        val publicUrl = "${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}/?u=${createdUser.publicId}"
        val uniqueTrx = "TRX" + UUID.randomUUID().toString().replace("-", "").uppercase().take(10)

        val dep = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitBkashDepositRequest(
                publicIdRaw = publicUrl,
                amountBdtRaw = "500.00",
                senderBkashRaw = "01712345678",
                transactionIdRaw = uniqueTrx,
            ).getOrThrow()
        }

        // Duplicate TrxID replay must be rejected
        val dupResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitBkashDepositRequest(
                publicIdRaw = publicUrl,
                amountBdtRaw = "500.00",
                senderBkashRaw = "01712345678",
                transactionIdRaw = uniqueTrx,
            )
        }
        assertTrue(dupResult.isFailure)

        // Overdraft (> 2000 BDT) must be rejected
        val overdraft = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitBankWithdrawalRequest(
                publicIdRaw = publicUrl,
                amountBdtRaw = "2500.00",
                bankNameRaw = "BRAC Bank",
                accountHolderNameRaw = "Nusrat Jahan",
                bankAccountNumberRaw = "150120998877",
            )
        }
        assertTrue(overdraft.isFailure)

        // Valid 800 BDT withdrawal reserves funds immediately
        val wdr = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitBankWithdrawalRequest(
                publicIdRaw = publicUrl,
                amountBdtRaw = "800.00",
                bankNameRaw = "City Bank",
                accountHolderNameRaw = "Nusrat Jahan",
                bankAccountNumberRaw = "23011223344",
            ).getOrThrow()
        }

        val stateDuringHold = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePublicUserPage(publicUrl).first {
                it is PublicPageState.Active && it.user.reservedPaisa == 80_000L
            }
        } as PublicPageState.Active
        assertEquals(120_000L, stateDuringHold.user.availablePaisa)

        // Admin signs back in and approves deposit (+500 BDT) and withdrawal (-800 BDT) -> final 1,700 BDT
        signInTestUser(aliceEmail)
        val resumedSession = repo.activeAdminSession.first { it != null }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateDepositStatus(resumedSession, dep.requestId, RequestStatus.APPROVED).getOrThrow()
            repo.updateWithdrawalStatus(resumedSession, wdr.requestId, RequestStatus.APPROVED).getOrThrow()
        }

        val finalPublicState = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePublicUserPage(publicUrl).first {
                it is PublicPageState.Active && it.user.balancePaisa == 170_000L && it.user.reservedPaisa == 0L
            }
        } as PublicPageState.Active
        assertEquals(170_000L, finalPublicState.user.balancePaisa)
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceEmail = "alice_iso_${UUID.randomUUID().toString().take(6)}@test.com"
        val bobEmail = "bob_iso_${UUID.randomUUID().toString().take(6)}@test.com"

        signInTestUser(aliceEmail)
        val aliceRepo = createRepository()
        val aliceUser = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createUser(aliceRepo.activeAdminSession.value, "Alice Managed User", "1000.00").getOrThrow()
        }

        signInTestUser(bobEmail)
        val bobRepo = createRepository()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getAdminUserByIdDirect(aliceUser.id)
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's admin_users document")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun unauthenticatedAdminQuery_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = createRepository()

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeAdminUsersRawUnfilteredForRuleTest().first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated caller queries admin_users")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
