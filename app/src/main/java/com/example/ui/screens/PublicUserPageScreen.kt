package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DepositRecord
import com.example.data.model.PublicPageState
import com.example.data.model.PublicUserView
import com.example.data.model.SiteSettingsModel
import com.example.data.model.WithdrawalRecord
import com.example.ui.components.CustomizableBannerCard
import com.example.ui.components.FeedbackNotificationBanner
import com.example.ui.components.OfflineRequiredBanner
import com.example.ui.components.RequestStatusBadge
import com.example.ui.components.UserAvatarBadge
import com.example.ui.components.WinnerBrandTopBar
import com.example.ui.theme.LocalWinnerColors
import com.example.ui.viewmodel.UiFeedbackMessage
import com.example.util.BangkokTimeFormatter
import com.example.util.CurrencyFormatter
import com.example.util.MediaSlot
import com.example.util.ValidationUtils

private enum class ActiveActionForm { NONE, DEPOSIT, WITHDRAW }

@Composable
fun PublicUserPageScreen(
    publicId: String,
    pageState: PublicPageState,
    bangkokClockText: String,
    isOnline: Boolean,
    isBusy: Boolean,
    isAdminAuthenticated: Boolean,
    feedback: UiFeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onSubmitDeposit: (amountBdt: String, senderBkash: String, trxId: String, onDone: () -> Unit) -> Unit,
    onSubmitWithdrawal: (amountBdt: String, bankName: String, holderName: String, accountNumber: String, onDone: () -> Unit) -> Unit,
    onEditMediaSlot: (MediaSlot) -> Unit,
    onCopiedMessage: (String) -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onReturnToPortal: () -> Unit,
    onEditUserAvatar: () -> Unit = {},
) {
    BackHandler(onBack = onReturnToPortal)

    val topLogoUri = when (pageState) {
        is PublicPageState.Active -> pageState.settings.logoUri
        is PublicPageState.Disabled -> pageState.settings.logoUri
        is PublicPageState.NotFound -> pageState.settings.logoUri
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        WinnerBrandTopBar(
            logoUri = topLogoUri,
            bangkokClockText = bangkokClockText,
            isAdminAuthenticated = isAdminAuthenticated,
            showBackButton = true,
            onBackClick = onReturnToPortal,
            onEditLogoClick = { onEditMediaSlot(MediaSlot.APP_LOGO) },
            trailingAction = if (isAdminAuthenticated) {
                {
                    FilledTonalButton(
                        onClick = onNavigateToAdminDashboard,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("user_page_return_admin_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Admin")
                    }
                }
            } else {
                null
            }
        )

        OfflineRequiredBanner(isOnline = isOnline)
        FeedbackNotificationBanner(feedback = feedback, onDismiss = onDismissFeedback)

        when (pageState) {
            PublicPageState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("public_page_loading_indicator"))
                }
            }

            is PublicPageState.NotFound -> {
                PublicPageUnavailableCard(
                    title = stringResource(R.string.user_link_invalid_title),
                    message = stringResource(R.string.user_link_invalid_message),
                    isDisabled = false,
                    onReturnToPortal = onReturnToPortal,
                )
            }

            is PublicPageState.Disabled -> {
                PublicPageUnavailableCard(
                    title = stringResource(R.string.user_link_disabled_title),
                    message = stringResource(R.string.user_link_disabled_message),
                    isDisabled = true,
                    onReturnToPortal = onReturnToPortal,
                )
            }

            is PublicPageState.Error -> {
                PublicPageUnavailableCard(
                    title = "Unable to Load User Page",
                    message = pageState.message,
                    isDisabled = false,
                    onReturnToPortal = onReturnToPortal,
                )
            }

            is PublicPageState.Active -> {
                ActivePublicUserContent(
                    publicId = publicId,
                    user = pageState.user,
                    settings = pageState.settings,
                    deposits = pageState.deposits,
                    withdrawals = pageState.withdrawals,
                    isOnline = isOnline,
                    isBusy = isBusy,
                    isAdminAuthenticated = isAdminAuthenticated,
                    onSubmitDeposit = onSubmitDeposit,
                    onSubmitWithdrawal = onSubmitWithdrawal,
                    onEditMediaSlot = onEditMediaSlot,
                    onEditUserAvatar = onEditUserAvatar,
                    onCopiedMessage = onCopiedMessage,
                )
            }
        }
    }
}

@Composable
private fun PublicPageUnavailableCard(
    title: String,
    message: String,
    isDisabled: Boolean,
    onReturnToPortal: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .testTag("public_page_unavailable_card"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Icon(
                        imageVector = if (isDisabled) Icons.Filled.LockPerson else Icons.Filled.ErrorOutline,
                        contentDescription = title,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(32.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onReturnToPortal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unavailable_return_portal_button"),
                ) {
                    Text(stringResource(R.string.back_to_portal_button))
                }
            }
        }
    }
}

@Composable
private fun ActivePublicUserContent(
    publicId: String,
    user: PublicUserView,
    settings: SiteSettingsModel,
    deposits: List<DepositRecord>,
    withdrawals: List<WithdrawalRecord>,
    isOnline: Boolean,
    isBusy: Boolean,
    isAdminAuthenticated: Boolean,
    onSubmitDeposit: (amountBdt: String, senderBkash: String, trxId: String, onDone: () -> Unit) -> Unit,
    onSubmitWithdrawal: (amountBdt: String, bankName: String, holderName: String, accountNumber: String, onDone: () -> Unit) -> Unit,
    onEditMediaSlot: (MediaSlot) -> Unit,
    onEditUserAvatar: () -> Unit,
    onCopiedMessage: (String) -> Unit,
) {
    val winnerColors = LocalWinnerColors.current
    val clipboardManager = LocalClipboardManager.current
    var activeForm by rememberSaveable { mutableStateOf(ActiveActionForm.NONE) }

    // Deposit inputs
    var depositAmount by rememberSaveable { mutableStateOf("") }
    var senderBkash by rememberSaveable { mutableStateOf("") }
    var trxId by rememberSaveable { mutableStateOf("") }

    // Withdrawal inputs
    var withdrawAmount by rememberSaveable { mutableStateOf("") }
    var bankName by rememberSaveable { mutableStateOf("") }
    var holderName by rememberSaveable { mutableStateOf("") }
    var bankAccountNumber by rememberSaveable { mutableStateOf("") }

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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Large Customizable Top Banner
            CustomizableBannerCard(
                customUri = settings.mainBannerUri,
                defaultDrawableRes = R.drawable.img_main_hero_banner,
                contentDescription = "Main Top Banner",
                isAdminAuthenticated = isAdminAuthenticated,
                editButtonTestTag = "user_page_edit_main_banner_button",
                bannerTestTag = "user_page_main_banner",
                onEditClick = { onEditMediaSlot(MediaSlot.MAIN_BANNER) },
            )

            // 2. Executive User Logo, User Name & BDT Balance Card on Pure White Surface (Zero demo labels!)
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("public_user_balance_card"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            UserAvatarBadge(
                                avatarUri = user.avatarUri,
                                displayName = user.displayName,
                                size = 60.dp,
                                showEditPencil = isAdminAuthenticated,
                                avatarTestTag = "public_user_avatar_badge",
                                editButtonTestTag = "user_page_edit_avatar_pencil_button",
                                onEditClick = onEditUserAvatar,
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("public_user_name_text"),
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Verified account",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.user_balance_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.user_currency_bdt),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = CurrencyFormatter.formatBdt(user.balancePaisa),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("public_user_balance_text"),
                    )

                    if (user.reservedPaisa > 0L) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Available: ${CurrencyFormatter.formatBdt(user.availablePaisa)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Pending Hold: ${CurrencyFormatter.formatBdt(user.reservedPaisa)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    val publicShareUrl = ValidationUtils.buildPublicUserUrl(settings.productionBaseUrl, user.publicId)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Updated: ${BangkokTimeFormatter.formatDateTime(user.updatedAtEpochMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(publicShareUrl))
                                onCopiedMessage("Copied public user link: $publicShareUrl")
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("public_copy_page_link_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy Link",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 3. Large Touch-Friendly Deposit & Withdraw Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        activeForm = if (activeForm == ActiveActionForm.DEPOSIT) {
                            ActiveActionForm.NONE
                        } else {
                            ActiveActionForm.DEPOSIT
                        }
                    },
                    enabled = isOnline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = winnerColors.bkashAccent,
                        contentColor = winnerColors.onBkashAccent,
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("public_deposit_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.user_deposit_button),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Button(
                    onClick = {
                        activeForm = if (activeForm == ActiveActionForm.WITHDRAW) {
                            ActiveActionForm.NONE
                        } else {
                            ActiveActionForm.WITHDRAW
                        }
                    },
                    enabled = isOnline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("public_withdraw_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.user_withdraw_button),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // 3A. Deposit Form — bKash Only
            AnimatedVisibility(visible = activeForm == ActiveActionForm.DEPOSIT) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bkash_deposit_form_card"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.deposit_sheet_title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = stringResource(R.string.user_bkash_only_subtitle),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = winnerColors.bkashAccent,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = winnerColors.bkashContainer,
                                contentColor = winnerColors.onBkashContainer,
                            ) {
                                Text(
                                    text = "bKash Only",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }

                        // Official Admin bKash Payment Number Box
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = winnerColors.bkashContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.deposit_bkash_number_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = settings.bkashNumber,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("official_bkash_number_text"),
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(settings.bkashNumber))
                                        onCopiedMessage("Copied official bKash number: ${settings.bkashNumber}")
                                    },
                                    modifier = Modifier.testTag("copy_bkash_number_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy bKash number",
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy_number_button))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = depositAmount,
                            onValueChange = { depositAmount = it },
                            label = { Text(stringResource(R.string.deposit_amount_label)) },
                            placeholder = { Text("e.g. 2500.00") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("deposit_amount_input"),
                        )

                        OutlinedTextField(
                            value = senderBkash,
                            onValueChange = { senderBkash = it },
                            label = { Text(stringResource(R.string.deposit_sender_number_label)) },
                            placeholder = { Text("01XXXXXXXXX") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("deposit_sender_bkash_input"),
                        )

                        OutlinedTextField(
                            value = trxId,
                            onValueChange = { trxId = it },
                            label = { Text(stringResource(R.string.deposit_trx_id_label)) },
                            placeholder = { Text("e.g. BK9X82M4LQ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("deposit_transaction_id_input"),
                        )

                        Button(
                            onClick = {
                                onSubmitDeposit(depositAmount, senderBkash, trxId) {
                                    depositAmount = ""
                                    senderBkash = ""
                                    trxId = ""
                                    activeForm = ActiveActionForm.NONE
                                }
                            },
                            enabled = isOnline && !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = winnerColors.bkashAccent,
                                contentColor = winnerColors.onBkashAccent,
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_deposit_request_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.deposit_submit_button),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            // 3B. Withdrawal Form — Bank Account Only
            AnimatedVisibility(visible = activeForm == ActiveActionForm.WITHDRAW) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_withdrawal_form_card"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.withdraw_sheet_title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "Available to withdraw: ${CurrencyFormatter.formatBdt(user.availablePaisa)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Text(
                                    text = "Bank Only",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = withdrawAmount,
                            onValueChange = { withdrawAmount = it },
                            label = { Text(stringResource(R.string.withdraw_amount_label)) },
                            placeholder = { Text("e.g. 5000.00") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("withdraw_amount_input"),
                        )

                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text(stringResource(R.string.withdraw_bank_name_label)) },
                            placeholder = { Text("e.g. BRAC Bank / Dutch-Bangla Bank") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("withdraw_bank_name_input"),
                        )

                        OutlinedTextField(
                            value = holderName,
                            onValueChange = { holderName = it },
                            label = { Text(stringResource(R.string.withdraw_holder_name_label)) },
                            placeholder = { Text("Full name on bank account") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("withdraw_holder_name_input"),
                        )

                        OutlinedTextField(
                            value = bankAccountNumber,
                            onValueChange = { bankAccountNumber = it },
                            label = { Text(stringResource(R.string.withdraw_account_number_label)) },
                            placeholder = { Text("Enter bank account number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("withdraw_account_number_input"),
                        )

                        Button(
                            onClick = {
                                onSubmitWithdrawal(withdrawAmount, bankName, holderName, bankAccountNumber) {
                                    withdrawAmount = ""
                                    bankName = ""
                                    holderName = ""
                                    bankAccountNumber = ""
                                    activeForm = ActiveActionForm.NONE
                                }
                            },
                            enabled = isOnline && !isBusy,
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_withdrawal_request_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.withdraw_submit_button),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            // 4. Exactly THREE Large Customizable Promotional Banners underneath Deposit & Withdraw
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.user_promo_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                CustomizableBannerCard(
                    customUri = settings.promoBanner1Uri,
                    defaultDrawableRes = R.drawable.img_promo_banner_1,
                    contentDescription = "Promotional Banner 1",
                    isAdminAuthenticated = isAdminAuthenticated,
                    editButtonTestTag = "edit_promo_banner_1_button",
                    bannerTestTag = "promo_banner_1_card",
                    onEditClick = { onEditMediaSlot(MediaSlot.PROMO_BANNER_1) },
                )

                CustomizableBannerCard(
                    customUri = settings.promoBanner2Uri,
                    defaultDrawableRes = R.drawable.img_promo_banner_2,
                    contentDescription = "Promotional Banner 2",
                    isAdminAuthenticated = isAdminAuthenticated,
                    editButtonTestTag = "edit_promo_banner_2_button",
                    bannerTestTag = "promo_banner_2_card",
                    onEditClick = { onEditMediaSlot(MediaSlot.PROMO_BANNER_2) },
                )

                CustomizableBannerCard(
                    customUri = settings.promoBanner3Uri,
                    defaultDrawableRes = R.drawable.img_promo_banner_3,
                    contentDescription = "Promotional Banner 3",
                    isAdminAuthenticated = isAdminAuthenticated,
                    editButtonTestTag = "edit_promo_banner_3_button",
                    bannerTestTag = "promo_banner_3_card",
                    onEditClick = { onEditMediaSlot(MediaSlot.PROMO_BANNER_3) },
                )
            }

            // 5. Relevant Deposit and Withdrawal Request Statuses
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("public_user_requests_card"),
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
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.user_requests_section_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    if (deposits.isEmpty() && withdrawals.isEmpty()) {
                        Text(
                            text = stringResource(R.string.user_requests_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        if (deposits.isNotEmpty()) {
                            Text(
                                text = "bKash Deposit Requests",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            deposits.forEach { dep ->
                                PublicRequestRow(
                                    title = "bKash Deposit • ${CurrencyFormatter.formatBdt(dep.amountPaisa)}",
                                    subtitle = "ID: ${dep.requestId} • TrxID: ${dep.transactionId}",
                                    submittedAtMs = dep.createdAtEpochMs,
                                    updatedAtMs = dep.updatedAtEpochMs,
                                    status = dep.status,
                                    testTag = "user_deposit_status_${dep.requestId}",
                                )
                                HorizontalDivider()
                            }
                        }

                        if (withdrawals.isNotEmpty()) {
                            Text(
                                text = "Bank Account Withdrawal Requests",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            withdrawals.forEach { wdr ->
                                val maskedAcct = if (wdr.bankAccountNumber.length > 4) {
                                    "••••" + wdr.bankAccountNumber.takeLast(4)
                                } else {
                                    wdr.bankAccountNumber
                                }
                                PublicRequestRow(
                                    title = "Bank Withdrawal • ${CurrencyFormatter.formatBdt(wdr.amountPaisa)}",
                                    subtitle = "ID: ${wdr.requestId} • ${wdr.bankName} ($maskedAcct)",
                                    submittedAtMs = wdr.createdAtEpochMs,
                                    updatedAtMs = wdr.updatedAtEpochMs,
                                    status = wdr.status,
                                    testTag = "user_withdrawal_status_${wdr.requestId}",
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PublicRequestRow(
    title: String,
    subtitle: String,
    submittedAtMs: Long,
    updatedAtMs: Long,
    status: com.example.data.model.RequestStatus,
    testTag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            RequestStatusBadge(status = status)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Submitted: ${BangkokTimeFormatter.formatDateTime(submittedAtMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (updatedAtMs != submittedAtMs) {
            Text(
                text = "Status Updated: ${BangkokTimeFormatter.formatDateTime(updatedAtMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
