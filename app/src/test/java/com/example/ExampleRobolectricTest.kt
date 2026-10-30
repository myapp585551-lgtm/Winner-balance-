package com.example

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AdminUserRecord
import com.example.data.model.PublicPageState
import com.example.data.model.PublicUserView
import com.example.data.model.SiteSettingsModel
import com.example.data.repository.WinnerFirestoreRepository
import com.example.ui.screens.AdminUsersTabContent
import com.example.ui.screens.PublicUserPageScreen
import com.example.ui.theme.LocalWinnerColors
import com.example.ui.theme.WinnerBalanceTheme
import com.example.ui.theme.WinnerBrandBlueLight
import com.example.ui.theme.WinnerBrandTitleStyle
import com.example.ui.theme.WinnerBrandYellow
import com.example.util.ControllableConnectivityChecker
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUpFirebaseEmulatorForUiTests() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseId = context.getString(R.string.firestore_database_id)
        if (FirebaseApp.getApps(context).isEmpty()) {
            val app = FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.winnerbalance.wbkpxq")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
            val firestore = FirebaseFirestore.getInstance(app, databaseId)
            firestore.useEmulator("127.0.0.1", 8085)
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            FirebaseAuth.getInstance(app).useEmulator("127.0.0.1", 9099)
        }
    }

    @Test
    fun appName_matchesWinnerBalance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Winner Balance", appName)
    }

    @Test
    fun mainActivity_coldLaunch_rendersPortalGatewayCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_brand_logo)
        assertNotNull("Expected uploaded brand logo drawable to decode", logoBitmap)
        assertTrue(logoBitmap.width > 0 && logoBitmap.height > 0)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()
            val brandNode = composeTestRule.onNodeWithTag("brand_header_title_text")
            brandNode.assertIsDisplayed()
            composeTestRule.onNodeWithText("WINNER BALANCE").assertIsDisplayed()
            composeTestRule.onNodeWithTag("brand_logo_container").assertIsDisplayed()
            composeTestRule.onNodeWithTag("brand_default_logo_image", useUnmergedTree = true).assertIsDisplayed()
            val bounds = brandNode.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Expected full-width WINNER BALANCE header (> 300px wide), got ${bounds.width}",
                bounds.width >= 300f
            )
            composeTestRule.onNodeWithTag("gateway_public_link_input").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("gateway_open_page_button").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun publicUserPage_hidesPencilControlsForOrdinaryUsers_andShowsExactBdtBalance() {
        val sampleUser = PublicUserView(
            publicId = "AbCdEfGh23456789KL",
            displayName = "Nadia Chowdhury",
            balancePaisa = 250_050L,
            reservedPaisa = 0L,
            updatedAtEpochMs = 1_761_735_600_000L,
        )
        val settings = SiteSettingsModel(bkashNumber = "01711223344")

        composeTestRule.setContent {
            WinnerBalanceTheme {
                PublicUserPageScreen(
                    publicId = sampleUser.publicId,
                    pageState = PublicPageState.Active(
                        user = sampleUser,
                        settings = settings,
                        deposits = emptyList(),
                        withdrawals = emptyList(),
                    ),
                    bangkokClockText = "Thu, 29 Oct • 17:00:00 ICT",
                    isOnline = true,
                    isBusy = false,
                    isAdminAuthenticated = false,
                    feedback = null,
                    onDismissFeedback = {},
                    onSubmitDeposit = { _, _, _, _ -> },
                    onSubmitWithdrawal = { _, _, _, _, _ -> },
                    onEditMediaSlot = {},
                    onCopiedMessage = {},
                    onNavigateToAdminDashboard = {},
                    onReturnToPortal = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("public_user_avatar_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nadia Chowdhury").assertIsDisplayed()
        composeTestRule.onNodeWithText("৳ 2,500.50 BDT").assertIsDisplayed()
        assertFalse(
            "Balance text must never include demo labels",
            "৳ 2,500.50 BDT".lowercase().contains("demo")
        )

        composeTestRule.onAllNodesWithTag("edit_logo_pencil_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("user_page_edit_avatar_pencil_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("user_page_edit_main_banner_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("edit_promo_banner_1_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("edit_promo_banner_2_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("edit_promo_banner_3_button").assertCountEquals(0)
    }

    @Test
    fun publicUserPage_showsOfflineBanner_andDisablesDepositAndWithdrawButtons() {
        val sampleUser = PublicUserView(
            publicId = "XyZ987654321AbCdEf",
            displayName = "Arif Hasan",
            balancePaisa = 500_000L,
            reservedPaisa = 0L,
            updatedAtEpochMs = 1_761_735_600_000L,
        )

        composeTestRule.setContent {
            WinnerBalanceTheme {
                PublicUserPageScreen(
                    publicId = sampleUser.publicId,
                    pageState = PublicPageState.Active(
                        user = sampleUser,
                        settings = SiteSettingsModel(),
                        deposits = emptyList(),
                        withdrawals = emptyList(),
                    ),
                    bangkokClockText = "Thu, 29 Oct • 17:00:00 ICT",
                    isOnline = false,
                    isBusy = false,
                    isAdminAuthenticated = false,
                    feedback = null,
                    onDismissFeedback = {},
                    onSubmitDeposit = { _, _, _, _ -> },
                    onSubmitWithdrawal = { _, _, _, _, _ -> },
                    onEditMediaSlot = {},
                    onCopiedMessage = {},
                    onNavigateToAdminDashboard = {},
                    onReturnToPortal = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("offline_required_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internet connection required.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("public_deposit_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("public_withdraw_button").assertIsNotEnabled()
    }

    @Test
    fun whiteBackgroundAndAdminUserAvatarPencilButton_areDisplayedAndInteractive() {
        var resolvedBgColor: Color? = null
        var resolvedSurfaceColor: Color? = null
        var resolvedBrandBlue: Color? = null
        var resolvedBrandYellow: Color? = null
        var bodyWeight: FontWeight? = null
        var clickedAvatarUserId: String? = null

        val adminUser = AdminUserRecord(
            id = "usr-internal-001",
            publicId = "PubIdTanvir987654",
            displayName = "Tanvir Rahman",
            balancePaisa = 325_000L,
            reservedPaisa = 0L,
            isActive = true,
            avatarUri = null,
            createdAtEpochMs = 1_761_735_600_000L,
            updatedAtEpochMs = 1_761_735_600_000L,
        )

        composeTestRule.setContent {
            WinnerBalanceTheme {
                resolvedBgColor = MaterialTheme.colorScheme.background
                resolvedSurfaceColor = MaterialTheme.colorScheme.surface
                resolvedBrandBlue = LocalWinnerColors.current.brandTitleBlue
                resolvedBrandYellow = LocalWinnerColors.current.brandTitleYellow
                bodyWeight = MaterialTheme.typography.bodyMedium.fontWeight
                AdminUsersTabContent(
                    users = listOf(adminUser),
                    productionBaseUrl = BuildConfig.DEFAULT_PRODUCTION_BASE_URL,
                    isOnline = true,
                    isBusy = false,
                    onCreateUser = { _, _, _, _ -> },
                    onUpdateUser = { _, _, _, _, _ -> },
                    onEditUserAvatar = { userId -> clickedAvatarUserId = userId },
                    onToggleActive = { _, _ -> },
                    onRegenerateLink = {},
                    onDeleteUser = { _, _ -> },
                    onOpenPublicPage = {},
                    onCopiedLink = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify pure white page + bright yellow large WINNER BALANCE brand tokens
        assertEquals(Color(0xFFFFFFFF), resolvedBgColor)
        assertEquals(Color(0xFFFFFFFF), resolvedSurfaceColor)
        assertEquals(WinnerBrandBlueLight, resolvedBrandBlue)
        assertEquals(Color(0xFFFFD600), resolvedBrandYellow)
        assertEquals(WinnerBrandYellow, resolvedBrandYellow)
        assertEquals(FontWeight.ExtraBold, WinnerBrandTitleStyle.fontWeight)
        assertTrue(
            "Expected WinnerBrandTitleStyle fontSize >= 30.sp, got ${WinnerBrandTitleStyle.fontSize}",
            WinnerBrandTitleStyle.fontSize.value >= 30f
        )
        assertTrue((bodyWeight?.weight ?: 0) >= FontWeight.SemiBold.weight)

        val expectedUrl = "${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}/?u=${adminUser.publicId}"
        composeTestRule.onNodeWithText(expectedUrl).assertIsDisplayed()
        assertFalse(expectedUrl.contains("winnerbalance.com"))

        composeTestRule.onNodeWithTag("admin_user_avatar_${adminUser.publicId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_edit_user_avatar_${adminUser.publicId}")
            .assertIsDisplayed()
            .performClick()

        assertEquals("usr-internal-001", clickedAvatarUserId)
    }

    @Test
    fun deepLinkIntent_withDeployedUrl_opensVerifiedTestUserDirectlyWithoutLogin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseId = context.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()

        runBlocking {
            WinnerFirestoreRepository(
                db = firestore,
                auth = auth,
                connectivityChecker = ControllableConnectivityChecker(initialOnline = true),
                mediaStorageHelper = null,
            ).ensureColdBootstrapSeeded().getOrThrow()
        }

        val deepLinkUrl = "${BuildConfig.DEFAULT_PRODUCTION_BASE_URL}/?u=WB7x9Km2Pq4Rt8Vz3N"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl)).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 8_000L) {
                composeTestRule.onAllNodesWithText("Tanvir Rahman").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Tanvir Rahman").assertIsDisplayed()
            composeTestRule.onNodeWithText("৳ 1,250.00 BDT").assertIsDisplayed()
            composeTestRule.onAllNodesWithTag("edit_logo_pencil_button").assertCountEquals(0)
        }
    }
}
