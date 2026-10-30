package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AdminSession
import com.example.data.model.AdminUserRecord
import com.example.data.model.DepositRecord
import com.example.data.model.LedgerRecord
import com.example.data.model.RequestStatus
import com.example.data.model.SiteSettingsModel
import com.example.data.model.WithdrawalRecord
import com.example.ui.components.ConfirmSensitiveActionDialog
import com.example.ui.components.CustomizableBannerCard
import com.example.ui.components.FeedbackNotificationBanner
import com.example.ui.components.OfflineRequiredBanner
import com.example.ui.components.WinnerBrandTopBar
import com.example.ui.viewmodel.UiFeedbackMessage
import com.example.util.MediaSlot

enum class AdminSection(val label: String, val icon: ImageVector, val testTag: String) {
    USERS("Users", Icons.Filled.People, "admin_nav_users"),
    PAYMENTS("Payments", Icons.Filled.Payments, "admin_nav_payments"),
    HISTORY("History", Icons.Filled.History, "admin_nav_history"),
    SETTINGS("Settings", Icons.Filled.Settings, "admin_nav_settings"),
}

@Composable
fun AdminDashboardScreen(
    adminSession: AdminSession,
    settings: SiteSettingsModel,
    users: List<AdminUserRecord>,
    deposits: List<DepositRecord>,
    withdrawals: List<WithdrawalRecord>,
    ledgerEntries: List<LedgerRecord>,
    bangkokClockText: String,
    isOnline: Boolean,
    isBusy: Boolean,
    feedback: UiFeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onCreateUser: (name: String, initialBalance: String, initialAvatarUri: Uri?, onDone: () -> Unit) -> Unit,
    onUpdateUser: (userId: String, name: String, balance: String, reason: String, onDone: () -> Unit) -> Unit,
    onEditUserAvatar: (userId: String) -> Unit,
    onToggleUserActive: (userId: String, targetActive: Boolean) -> Unit,
    onRegenerateUserLink: (userId: String) -> Unit,
    onDeleteUser: (userId: String, userName: String) -> Unit,
    onUpdateDepositStatus: (requestId: String, status: RequestStatus) -> Unit,
    onUpdateWithdrawalStatus: (requestId: String, status: RequestStatus) -> Unit,
    onSaveBkashNumber: (String) -> Unit,
    onSaveProductionBaseUrl: (String) -> Unit,
    onEditMediaSlot: (MediaSlot) -> Unit,
    onOpenPublicUserPage: (String) -> Unit,
    onCopiedLink: (String) -> Unit,
    onSignOut: () -> Unit,
    onBackToPortal: () -> Unit,
) {
    BackHandler(onBack = onBackToPortal)

    var selectedSection by rememberSaveable { mutableStateOf(AdminSection.USERS) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val openPaymentsCount = remember(deposits, withdrawals) {
        deposits.count { !it.status.isTerminal } + withdrawals.count { !it.status.isTerminal }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    WinnerBrandTopBar(
                        logoUri = settings.logoUri,
                        bangkokClockText = bangkokClockText,
                        isAdminAuthenticated = true,
                        showBackButton = true,
                        onBackClick = onBackToPortal,
                        onEditLogoClick = { onEditMediaSlot(MediaSlot.APP_LOGO) },
                        trailingAction = {
                            FilledTonalButton(
                                onClick = { showSignOutConfirm = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("admin_sign_out_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = stringResource(R.string.admin_sign_out_button),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.admin_sign_out_button))
                            }
                        }
                    )
                    OfflineRequiredBanner(isOnline = isOnline)
                    FeedbackNotificationBanner(feedback = feedback, onDismiss = onDismissFeedback)
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        AdminSection.entries.forEach { section ->
                            NavigationBarItem(
                                selected = selectedSection == section,
                                onClick = { selectedSection = section },
                                icon = {
                                    if (section == AdminSection.PAYMENTS && openPaymentsCount > 0) {
                                        BadgedBox(badge = { Badge { Text(openPaymentsCount.toString()) } }) {
                                            Icon(section.icon, contentDescription = section.label)
                                        }
                                    } else {
                                        Icon(section.icon, contentDescription = section.label)
                                    }
                                },
                                label = { Text(section.label) },
                                modifier = Modifier.testTag(section.testTag),
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AdminSection.entries.forEach { section ->
                            NavigationRailItem(
                                selected = selectedSection == section,
                                onClick = { selectedSection = section },
                                icon = {
                                    if (section == AdminSection.PAYMENTS && openPaymentsCount > 0) {
                                        BadgedBox(badge = { Badge { Text(openPaymentsCount.toString()) } }) {
                                            Icon(section.icon, contentDescription = section.label)
                                        }
                                    } else {
                                        Icon(section.icon, contentDescription = section.label)
                                    }
                                },
                                label = { Text(section.label) },
                                modifier = Modifier.testTag(section.testTag),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 860.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Admin Console • ${adminSession.email}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        when (selectedSection) {
                            AdminSection.USERS -> AdminUsersTabContent(
                                users = users,
                                productionBaseUrl = settings.productionBaseUrl,
                                isOnline = isOnline,
                                isBusy = isBusy,
                                onCreateUser = onCreateUser,
                                onUpdateUser = onUpdateUser,
                                onEditUserAvatar = onEditUserAvatar,
                                onToggleActive = onToggleUserActive,
                                onRegenerateLink = onRegenerateUserLink,
                                onDeleteUser = onDeleteUser,
                                onOpenPublicPage = onOpenPublicUserPage,
                                onCopiedLink = onCopiedLink,
                            )

                            AdminSection.PAYMENTS -> AdminPaymentsTabContent(
                                deposits = deposits,
                                withdrawals = withdrawals,
                                isOnline = isOnline,
                                isBusy = isBusy,
                                onUpdateDepositStatus = onUpdateDepositStatus,
                                onUpdateWithdrawalStatus = onUpdateWithdrawalStatus,
                            )

                            AdminSection.HISTORY -> AdminHistoryTabContent(
                                ledgerEntries = ledgerEntries,
                            )

                            AdminSection.SETTINGS -> AdminWebsiteSettingsTabContent(
                                settings = settings,
                                isOnline = isOnline,
                                isBusy = isBusy,
                                onSaveBkashNumber = onSaveBkashNumber,
                                onSaveProductionBaseUrl = onSaveProductionBaseUrl,
                                onEditMediaSlot = onEditMediaSlot,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showSignOutConfirm) {
        ConfirmSensitiveActionDialog(
            title = "Sign Out of Admin Dashboard?",
            message = "You will need to sign in again with your Google administrator account to manage users or approve transactions.",
            confirmLabel = "Sign Out",
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showSignOutConfirm = false },
        )
    }
}

@Composable
private fun AdminWebsiteSettingsTabContent(
    settings: SiteSettingsModel,
    isOnline: Boolean,
    isBusy: Boolean,
    onSaveBkashNumber: (String) -> Unit,
    onSaveProductionBaseUrl: (String) -> Unit,
    onEditMediaSlot: (MediaSlot) -> Unit,
) {
    var bkashInput by rememberSaveable { mutableStateOf(settings.bkashNumber) }
    var domainInput by rememberSaveable { mutableStateOf(settings.productionBaseUrl) }

    LaunchedEffect(settings.bkashNumber) {
        bkashInput = settings.bkashNumber
    }
    LaunchedEffect(settings.productionBaseUrl) {
        domainInput = settings.productionBaseUrl
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // 1. Official bKash Payment Number Configuration
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "bKash Payment Number",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Displayed automatically on every public user Deposit form.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = bkashInput,
                    onValueChange = { bkashInput = it },
                    label = { Text("Official bKash Number (11 digits)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_bkash_number_input"),
                )

                Button(
                    onClick = { onSaveBkashNumber(bkashInput) },
                    enabled = isOnline && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_save_bkash_button"),
                ) {
                    Text("Save bKash Number")
                }
            }
        }

        // 2. Production Base URL Configuration
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "Deployed Public Link URL",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Automatically uses your live deployed URL for all ?u=UNIQUE_PUBLIC_ID public user links.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    label = { Text("Active Deployed HTTPS URL") },
                    placeholder = { Text(BuildConfig.DEFAULT_PRODUCTION_BASE_URL) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_production_url_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            domainInput = BuildConfig.DEFAULT_PRODUCTION_BASE_URL
                            onSaveProductionBaseUrl(BuildConfig.DEFAULT_PRODUCTION_BASE_URL)
                        },
                        enabled = isOnline && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_reset_deployed_url_button"),
                    ) {
                        Text("Use Deployed URL")
                    }

                    Button(
                        onClick = { onSaveProductionBaseUrl(domainInput) },
                        enabled = isOnline && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_save_url_button"),
                    ) {
                        Text("Save Deployed URL")
                    }
                }
            }
        }

        // 3. Logo & All 4 Banners Management
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "Logo & Banner Customization",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Tap any pencil button or control below to upload, preview, replace, or remove images.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onEditMediaSlot(MediaSlot.APP_LOGO) },
                    enabled = isOnline && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_edit_logo_button"),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (settings.logoUri != null) "Replace or Remove Custom Logo"
                        else "Upload Custom Application Logo"
                    )
                }

                HorizontalDivider()

                Text("Main Top Website Banner", style = MaterialTheme.typography.titleSmall)
                CustomizableBannerCard(
                    customUri = settings.mainBannerUri,
                    defaultDrawableRes = R.drawable.img_main_hero_banner,
                    contentDescription = "Main Top Banner",
                    isAdminAuthenticated = true,
                    editButtonTestTag = "settings_edit_main_banner_button",
                    bannerTestTag = "settings_main_banner_preview",
                    onEditClick = { onEditMediaSlot(MediaSlot.MAIN_BANNER) },
                )

                Text("Promotional Banner 1", style = MaterialTheme.typography.titleSmall)
                CustomizableBannerCard(
                    customUri = settings.promoBanner1Uri,
                    defaultDrawableRes = R.drawable.img_promo_banner_1,
                    contentDescription = "Promotional Banner 1",
                    isAdminAuthenticated = true,
                    editButtonTestTag = "settings_edit_promo_1_button",
                    bannerTestTag = "settings_promo_1_preview",
                    onEditClick = { onEditMediaSlot(MediaSlot.PROMO_BANNER_1) },
                )

                Text("Promotional Banner 2", style = MaterialTheme.typography.titleSmall)
                CustomizableBannerCard(
                    customUri = settings.promoBanner2Uri,
                    defaultDrawableRes = R.drawable.img_promo_banner_2,
                    contentDescription = "Promotional Banner 2",
                    isAdminAuthenticated = true,
                    editButtonTestTag = "settings_edit_promo_2_button",
                    bannerTestTag = "settings_promo_2_preview",
                    onEditClick = { onEditMediaSlot(MediaSlot.PROMO_BANNER_2) },
                )

                Text("Promotional Banner 3", style = MaterialTheme.typography.titleSmall)
                CustomizableBannerCard(
                    customUri = settings.promoBanner3Uri,
                    defaultDrawableRes = R.drawable.img_promo_banner_3,
                    contentDescription = "Promotional Banner 3",
                    isAdminAuthenticated = true,
                    editButtonTestTag = "settings_edit_promo_3_button",
                    bannerTestTag = "settings_promo_3_preview",
                    onEditClick = { onEditMediaSlot(MediaSlot.PROMO_BANNER_3) },
                )
            }
        }

        // 4. Transparent Backend & Cloud Status Disclosure
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Backend & Multi-Device Sync Configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val databaseId = stringResource(R.string.firestore_database_id)
                Text(
                    text = "Live Cloud Sync Active: Connected to Firebase Cloud Firestore (gen-lang-client-0530252854 • $databaseId). Public user links (?u=UNIQUE_PUBLIC_ID) resolve live balances, bKash deposits, and bank withdrawals across Android devices and web browsers without admin login.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
