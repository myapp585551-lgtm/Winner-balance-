package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AdminSession
import com.example.data.model.AdminUserRecord
import com.example.data.model.SiteSettingsModel
import com.example.ui.components.CustomizableBannerCard
import com.example.ui.components.FeedbackNotificationBanner
import com.example.ui.components.OfflineRequiredBanner
import com.example.ui.components.UserAvatarBadge
import com.example.ui.components.WinnerBrandTopBar
import com.example.ui.viewmodel.UiFeedbackMessage
import com.example.util.CurrencyFormatter
import com.example.util.MediaSlot
import com.example.util.ValidationUtils

@Composable
fun GatewayPortalScreen(
    settings: SiteSettingsModel,
    bangkokClockText: String,
    isOnline: Boolean,
    adminSession: AdminSession?,
    adminUsers: List<AdminUserRecord>,
    feedback: UiFeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onOpenUserPublicPage: (String) -> Unit,
    onNavigateToAdmin: () -> Unit,
    onEditMediaSlot: (MediaSlot) -> Unit,
) {
    var linkInput by rememberSaveable { mutableStateOf("") }
    var linkError by rememberSaveable { mutableStateOf<String?>(null) }
    val isAdminAuthenticated = adminSession != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        WinnerBrandTopBar(
            logoUri = settings.logoUri,
            bangkokClockText = bangkokClockText,
            isAdminAuthenticated = isAdminAuthenticated,
            onEditLogoClick = { onEditLogoSlot(onEditMediaSlot) },
            trailingAction = {
                FilledTonalButton(
                    onClick = onNavigateToAdmin,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("top_bar_admin_button"),
                ) {
                    Icon(
                        imageVector = if (isAdminAuthenticated) Icons.Filled.AdminPanelSettings else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAdminAuthenticated) {
                            stringResource(R.string.gateway_admin_portal_button)
                        } else {
                            stringResource(R.string.gateway_admin_login_button)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        )

        OfflineRequiredBanner(isOnline = isOnline)
        FeedbackNotificationBanner(feedback = feedback, onDismiss = onDismissFeedback)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Main Top Website Banner (Pencil edit visible ONLY to authenticated admin)
                CustomizableBannerCard(
                    customUri = settings.mainBannerUri,
                    defaultDrawableRes = R.drawable.img_main_hero_banner,
                    contentDescription = "Winner Balance Bangkok Financial District Banner",
                    isAdminAuthenticated = isAdminAuthenticated,
                    editButtonTestTag = "edit_main_banner_pencil_button",
                    bannerTestTag = "gateway_main_banner_card",
                    badgeLabel = "WINNER BALANCE • BANGKOK EXECUTIVE PORTAL",
                    onEditClick = { onEditMediaSlot(MediaSlot.MAIN_BANNER) },
                )

                // Open Shared User Link Card
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
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.gateway_open_user_title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "Direct public user access — no login or registration required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.gateway_open_user_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = linkInput,
                            onValueChange = {
                                linkInput = it
                                linkError = null
                            },
                            label = { Text(stringResource(R.string.gateway_link_input_label)) },
                            placeholder = { Text("${settings.productionBaseUrl}/?u=...") },
                            singleLine = true,
                            isError = linkError != null,
                            supportingText = {
                                if (linkError != null) {
                                    Text(text = linkError!!)
                                } else {
                                    Text(text = "Paste a full ?u=UNIQUE_PUBLIC_ID link or enter the public ID directly.")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gateway_public_link_input"),
                        )

                        Button(
                            onClick = {
                                val parsed = ValidationUtils.extractPublicIdFromLinkOrInput(linkInput)
                                parsed.fold(
                                    onSuccess = { publicId -> onOpenUserPublicPage(publicId) },
                                    onFailure = { err -> linkError = err.message ?: "Invalid link format." }
                                )
                            },
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gateway_open_page_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.gateway_open_page_button),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                // If Admin is signed in, show quick launch shortcuts for created user pages
                if (isAdminAuthenticated && adminUsers.isNotEmpty()) {
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Admin Quick Preview: Active User Pages",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${adminUsers.size} total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                            adminUsers.take(5).forEach { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    UserAvatarBadge(
                                        avatarUri = user.avatarUri,
                                        displayName = user.displayName,
                                        size = 42.dp,
                                        showEditPencil = false,
                                        avatarTestTag = "gateway_user_avatar_${user.publicId}",
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatBdt(user.balancePaisa),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onOpenUserPublicPage(user.publicId) },
                                        modifier = Modifier.testTag("gateway_preview_user_${user.publicId}"),
                                    ) {
                                        Text("Open Page")
                                    }
                                }
                            }
                        }
                    }
                }

                // Administrator Management Card on Clean White Surface
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
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
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = if (isAdminAuthenticated) {
                                    "Signed in as ${adminSession?.email}"
                                } else {
                                    "Authorized Administrator Console"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "Manage individual user BDT balances, customize user logo photos and banners, verify bKash deposits, and process bank withdrawals from the private Admin Dashboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onNavigateToAdmin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gateway_admin_console_button"),
                        ) {
                            Text(
                                text = if (isAdminAuthenticated) {
                                    "Open Admin Dashboard"
                                } else {
                                    "Administrator Sign In"
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private fun onEditLogoSlot(callback: (MediaSlot) -> Unit) {
    callback(MediaSlot.APP_LOGO)
}
