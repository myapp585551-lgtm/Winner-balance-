package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.PublicPageState
import com.example.ui.components.MediaUploadModalSheet
import com.example.ui.components.UserAvatarUploadModalSheet
import com.example.ui.screens.AdminAuthScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.GatewayPortalScreen
import com.example.ui.screens.PublicUserPageScreen
import com.example.ui.theme.WinnerBalanceTheme
import com.example.ui.viewmodel.WinnerAppViewModel
import com.example.util.GoogleAuthHelper
import com.example.util.ValidationUtils

object WinnerRoutes {
    const val PORTAL_GATEWAY = "portal_gateway"
    const val PUBLIC_USER_ROUTE = "u/{publicId}"
    const val ADMIN_AUTH = "admin_auth"
    const val ADMIN_DASHBOARD = "admin_dashboard"

    fun publicUser(publicId: String): String = "u/${Uri.encode(publicId)}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
            ),
        )

        val initialDeepLinkPublicId = extractPublicIdFromIntent(intent)

        setContent {
            WinnerBalanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var incomingDeepLinkId by rememberSaveable { mutableStateOf(initialDeepLinkPublicId) }

                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { newIntent ->
                            extractPublicIdFromIntent(newIntent)?.let { publicId ->
                                incomingDeepLinkId = publicId
                            }
                        }
                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    WinnerBalanceApp(
                        deepLinkPublicId = incomingDeepLinkId,
                        onDeepLinkConsumed = { incomingDeepLinkId = null },
                    )
                }
            }
        }
    }

    internal fun extractPublicIdFromIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val queryUserId = uri.getQueryParameter("u")?.trim()
        if (!queryUserId.isNullOrEmpty()) {
            ValidationUtils.extractPublicIdFromLinkOrInput(queryUserId).getOrNull()?.let { return it }
        }
        return ValidationUtils.extractPublicIdFromLinkOrInput(uri.toString()).getOrNull()
    }
}

@Composable
fun WinnerBalanceApp(
    appViewModel: WinnerAppViewModel = viewModel(factory = WinnerAppViewModel.Factory),
    deepLinkPublicId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val startDestination = remember {
        if (!deepLinkPublicId.isNullOrBlank()) {
            WinnerRoutes.publicUser(deepLinkPublicId)
        } else {
            WinnerRoutes.PORTAL_GATEWAY
        }
    }

    val isOnline by appViewModel.isOnline.collectAsStateWithLifecycle()
    val adminSession by appViewModel.adminSession.collectAsStateWithLifecycle()
    val siteSettings by appViewModel.siteSettings.collectAsStateWithLifecycle()
    val adminUsers by appViewModel.adminUsers.collectAsStateWithLifecycle()
    val adminDeposits by appViewModel.adminDeposits.collectAsStateWithLifecycle()
    val adminWithdrawals by appViewModel.adminWithdrawals.collectAsStateWithLifecycle()
    val adminLedger by appViewModel.adminLedger.collectAsStateWithLifecycle()
    val bangkokClockText by appViewModel.bangkokClockText.collectAsStateWithLifecycle()
    val feedback by appViewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isBusy by appViewModel.isBusy.collectAsStateWithLifecycle()
    val editingMediaSlot by appViewModel.editingMediaSlot.collectAsStateWithLifecycle()
    val editingAvatarUser by appViewModel.editingAvatarUser.collectAsStateWithLifecycle()

    LaunchedEffect(deepLinkPublicId) {
        if (!deepLinkPublicId.isNullOrBlank()) {
            val currentPublicId = navController.currentBackStackEntry?.arguments?.getString("publicId")
            if (currentPublicId != deepLinkPublicId) {
                navController.navigate(WinnerRoutes.publicUser(deepLinkPublicId)) {
                    launchSingleTop = true
                }
            }
            onDeepLinkConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(WinnerRoutes.PORTAL_GATEWAY) {
            GatewayPortalScreen(
                settings = siteSettings,
                bangkokClockText = bangkokClockText,
                isOnline = isOnline,
                adminSession = adminSession,
                adminUsers = adminUsers,
                feedback = feedback,
                onDismissFeedback = appViewModel::dismissFeedback,
                onOpenUserPublicPage = { publicId ->
                    appViewModel.dismissFeedback()
                    navController.navigate(WinnerRoutes.publicUser(publicId))
                },
                onNavigateToAdmin = {
                    appViewModel.dismissFeedback()
                    if (adminSession != null) {
                        navController.navigate(WinnerRoutes.ADMIN_DASHBOARD)
                    } else {
                        navController.navigate(WinnerRoutes.ADMIN_AUTH)
                    }
                },
                onEditMediaSlot = appViewModel::openMediaEditor,
            )
        }

        composable(
            route = WinnerRoutes.PUBLIC_USER_ROUTE,
            arguments = listOf(navArgument("publicId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val publicId = backStackEntry.arguments?.getString("publicId").orEmpty()
            val pageStateFlow = remember(publicId) { appViewModel.observePublicUserPage(publicId) }
            val pageState by pageStateFlow.collectAsStateWithLifecycle(initialValue = PublicPageState.Loading)

            PublicUserPageScreen(
                publicId = publicId,
                pageState = pageState,
                bangkokClockText = bangkokClockText,
                isOnline = isOnline,
                isBusy = isBusy,
                isAdminAuthenticated = adminSession != null,
                feedback = feedback,
                onDismissFeedback = appViewModel::dismissFeedback,
                onSubmitDeposit = { amountBdt, senderBkash, trxId, onDone ->
                    appViewModel.submitPublicDeposit(publicId, amountBdt, senderBkash, trxId, onDone)
                },
                onSubmitWithdrawal = { amountBdt, bankName, holderName, acctNum, onDone ->
                    appViewModel.submitPublicWithdrawal(publicId, amountBdt, bankName, holderName, acctNum, onDone)
                },
                onEditMediaSlot = appViewModel::openMediaEditor,
                onCopiedMessage = { msg -> appViewModel.showFeedback(msg) },
                onNavigateToAdminDashboard = {
                    navController.navigate(WinnerRoutes.ADMIN_DASHBOARD) {
                        launchSingleTop = true
                    }
                },
                onReturnToPortal = {
                    if (!navController.popBackStack()) {
                        navController.navigate(WinnerRoutes.PORTAL_GATEWAY) {
                            popUpTo(0)
                        }
                    }
                },
                onEditUserAvatar = {
                    appViewModel.openUserAvatarEditorByPublicId(publicId)
                },
            )
        }

        composable(WinnerRoutes.ADMIN_AUTH) {
            LaunchedEffect(adminSession) {
                if (adminSession != null) {
                    navController.navigate(WinnerRoutes.ADMIN_DASHBOARD) {
                        popUpTo(WinnerRoutes.ADMIN_AUTH) { inclusive = true }
                    }
                }
            }

            AdminAuthScreen(
                settings = siteSettings,
                bangkokClockText = bangkokClockText,
                isOnline = isOnline,
                isBusy = isBusy,
                feedback = feedback,
                onDismissFeedback = appViewModel::dismissFeedback,
                onAuthSuccess = {
                    appViewModel.onGoogleAdminAuthenticated()
                    navController.navigate(WinnerRoutes.ADMIN_DASHBOARD) {
                        popUpTo(WinnerRoutes.ADMIN_AUTH) { inclusive = true }
                    }
                },
                onAuthError = { errorMsg ->
                    appViewModel.showFeedback(errorMsg, isError = true)
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(WinnerRoutes.PORTAL_GATEWAY)
                    }
                },
            )
        }

        composable(WinnerRoutes.ADMIN_DASHBOARD) {
            val currentAdmin = adminSession
            if (currentAdmin == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(WinnerRoutes.ADMIN_AUTH) {
                        popUpTo(WinnerRoutes.ADMIN_DASHBOARD) { inclusive = true }
                    }
                }
            } else {
                AdminDashboardScreen(
                    adminSession = currentAdmin,
                    settings = siteSettings,
                    users = adminUsers,
                    deposits = adminDeposits,
                    withdrawals = adminWithdrawals,
                    ledgerEntries = adminLedger,
                    bangkokClockText = bangkokClockText,
                    isOnline = isOnline,
                    isBusy = isBusy,
                    feedback = feedback,
                    onDismissFeedback = appViewModel::dismissFeedback,
                    onCreateUser = { name, balance, avatarUri, onDone ->
                        appViewModel.createUser(name, balance, avatarUri) { onDone() }
                    },
                    onUpdateUser = { id, name, balance, reason, onDone ->
                        appViewModel.updateUserDetailsAndBalance(id, name, balance, reason, onDone)
                    },
                    onEditUserAvatar = appViewModel::openUserAvatarEditor,
                    onToggleUserActive = appViewModel::toggleUserActive,
                    onRegenerateUserLink = appViewModel::regenerateUserLink,
                    onDeleteUser = appViewModel::deleteUser,
                    onUpdateDepositStatus = appViewModel::updateDepositStatus,
                    onUpdateWithdrawalStatus = appViewModel::updateWithdrawalStatus,
                    onSaveBkashNumber = appViewModel::saveBkashPaymentNumber,
                    onSaveProductionBaseUrl = appViewModel::saveProductionBaseUrl,
                    onEditMediaSlot = appViewModel::openMediaEditor,
                    onOpenPublicUserPage = { publicId ->
                        navController.navigate(WinnerRoutes.publicUser(publicId))
                    },
                    onCopiedLink = { msg -> appViewModel.showFeedback(msg) },
                    onSignOut = {
                        GoogleAuthHelper.signOut(
                            credentialManager = credentialManager,
                            scope = authScope,
                        ) {
                            appViewModel.signOutAdmin {
                                navController.navigate(WinnerRoutes.PORTAL_GATEWAY) {
                                    popUpTo(0)
                                }
                            }
                        }
                    },
                    onBackToPortal = {
                        navController.navigate(WinnerRoutes.PORTAL_GATEWAY) {
                            popUpTo(WinnerRoutes.PORTAL_GATEWAY) { inclusive = true }
                        }
                    },
                )
            }
        }
    }

    // Global Admin-Only Media Upload & Preview Modal Sheet
    if (adminSession != null && editingMediaSlot != null) {
        val activeSlot = editingMediaSlot!!
        MediaUploadModalSheet(
            slot = activeSlot,
            settings = siteSettings,
            isOnline = isOnline,
            isBusy = isBusy,
            onDismiss = appViewModel::closeMediaEditor,
            onSaveSelectedUri = { pickedUri ->
                appViewModel.saveCustomMedia(activeSlot, pickedUri)
            },
            onRemoveCustomImage = {
                appViewModel.resetCustomMedia(activeSlot)
            },
        )
    }

    // Global Admin-Only User Avatar Upload & Preview Modal Sheet
    if (adminSession != null && editingAvatarUser != null) {
        val targetUser = editingAvatarUser!!
        UserAvatarUploadModalSheet(
            user = targetUser,
            isOnline = isOnline,
            isBusy = isBusy,
            onDismiss = appViewModel::closeUserAvatarEditor,
            onSaveSelectedUri = { pickedUri ->
                appViewModel.saveCustomUserAvatar(targetUser.id, pickedUri)
            },
            onRemoveCustomAvatar = {
                appViewModel.resetCustomUserAvatar(targetUser.id)
            },
        )
    }
}
