package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.RequestStatus
import com.example.data.model.SiteSettingsModel
import com.example.ui.theme.BrandDisplayFontFamily
import com.example.ui.theme.LocalWinnerColors
import com.example.ui.theme.WinnerBrandTitleStyle
import com.example.ui.viewmodel.UiFeedbackMessage
import com.example.util.MediaSlot

@Composable
fun OfflineRequiredBanner(isOnline: Boolean) {
    AnimatedVisibility(visible = !isOnline) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("offline_required_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = stringResource(R.string.offline_banner_message),
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.offline_banner_message),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.offline_reconnecting_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun FeedbackNotificationBanner(
    feedback: UiFeedbackMessage?,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = feedback != null) {
        val current = feedback ?: return@AnimatedVisibility
        val colors = LocalWinnerColors.current
        val bg = if (current.isError) MaterialTheme.colorScheme.errorContainer else colors.approvedBg
        val fg = if (current.isError) MaterialTheme.colorScheme.onErrorContainer else colors.approvedFg

        Surface(
            color = bg,
            contentColor = fg,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("feedback_notification_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (current.isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                    contentDescription = if (current.isError) "Error notification" else "Success notification",
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dismiss_feedback_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss message",
                    )
                }
            }
        }
    }
}

@Composable
fun WinnerBrandTopBar(
    logoUri: String?,
    bangkokClockText: String,
    isAdminAuthenticated: Boolean,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onEditLogoClick: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val winnerColors = LocalWinnerColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Dedicated Full-Width Brand Showcase Plaque:
                // Gives "WINNER BALANCE" 100% of the screen width with zero competing buttons,
                // rendered much larger in vivid Bright Yellow on a high-contrast deep navy banner.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    winnerColors.brandPlaqueStart,
                                    winnerColors.brandPlaqueMid,
                                    winnerColors.brandPlaqueEnd,
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = winnerColors.brandTitleBorderYellow,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    val initialTargetSp = remember(maxWidth, fontScale) {
                        val rawSp = when {
                            maxWidth >= 520.dp -> 40f
                            maxWidth >= 380.dp -> 33f
                            maxWidth >= 340.dp -> 29f
                            maxWidth >= 300.dp -> 25f
                            else -> 22f
                        }
                        if (fontScale > 1.05f) rawSp / fontScale else rawSp
                    }
                    var fittedFontSizeSp by remember(initialTargetSp) {
                        mutableFloatStateOf(initialTargetSp)
                    }

                    Text(
                        text = stringResource(R.string.brand_header_title),
                        style = WinnerBrandTitleStyle.copy(
                            fontSize = fittedFontSizeSp.sp,
                            lineHeight = (fittedFontSizeSp * 1.18f).sp,
                        ),
                        color = winnerColors.brandTitleYellow,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { layoutResult ->
                            if (layoutResult.didOverflowWidth && fittedFontSizeSp > 16f) {
                                fittedFontSizeSp = (fittedFontSizeSp - 1f).coerceAtLeast(16f)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("brand_header_title_text"),
                    )
                }

                // 2. Clean Utility Row: Back Button, Customizable Logo + Admin Pencil, Bangkok ICT Clock, Trailing Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (showBackButton && onBackClick != null) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("top_bar_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate back",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Customizable Logo + Admin-Only Pencil Button
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = winnerColors.heroCardStart,
                                modifier = Modifier
                                    .size(56.dp)
                                    .border(1.5.dp, winnerColors.brandTitleBorderYellow, RoundedCornerShape(14.dp))
                                    .testTag("brand_logo_container")
                            ) {
                                if (!logoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = logoUri,
                                        contentDescription = "Winner Balance logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("brand_custom_logo_image"),
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_brand_logo),
                                        contentDescription = "Winner Balance logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("brand_default_logo_image"),
                                    )
                                }
                            }

                            if (isAdminAuthenticated) {
                                FilledIconButton(
                                    onClick = onEditLogoClick,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(28.dp)
                                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .testTag("edit_logo_pencil_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.edit_logo_cd),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = "Thailand time Asia/Bangkok",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = bangkokClockText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("bangkok_clock_label"),
                                )
                            }
                        }
                    }

                    if (trailingAction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        trailingAction()
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun CustomizableBannerCard(
    customUri: String?,
    @DrawableRes defaultDrawableRes: Int,
    contentDescription: String,
    isAdminAuthenticated: Boolean,
    editButtonTestTag: String,
    bannerTestTag: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null,
) {
    val winnerColors = LocalWinnerColors.current
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(bannerTestTag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (!customUri.isNullOrBlank()) {
                AsyncImage(
                    model = customUri,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(id = defaultDrawableRes),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Subtle vignette bottom gradient for polish
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                winnerColors.heroCardStart.copy(alpha = 0.05f),
                                winnerColors.heroCardStart.copy(alpha = 0.45f),
                            )
                        )
                    )
            )

            if (badgeLabel != null) {
                Surface(
                    color = winnerColors.brandPlaqueStart.copy(alpha = 0.92f),
                    contentColor = winnerColors.brandTitleYellow,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .border(1.dp, winnerColors.brandTitleBorderYellow, CircleShape)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = BrandDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            // Pencil-shaped edit button STRICTLY visible only to authenticated administrator
            if (isAdminAuthenticated) {
                FilledIconButton(
                    onClick = onEditClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = winnerColors.heroCardStart.copy(alpha = 0.88f),
                        contentColor = winnerColors.heroGold,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(editButtonTestTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit $contentDescription",
                    )
                }
            }
        }
    }
}

@Composable
fun RequestStatusBadge(status: RequestStatus, modifier: Modifier = Modifier) {
    val colors = LocalWinnerColors.current
    val (bg, fg) = when (status) {
        RequestStatus.PENDING -> colors.pendingBg to colors.pendingFg
        RequestStatus.PROCESSING -> colors.processingBg to colors.processingFg
        RequestStatus.APPROVED -> colors.approvedBg to colors.approvedFg
        RequestStatus.REJECTED -> colors.rejectedBg to colors.rejectedFg
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaUploadModalSheet(
    slot: MediaSlot,
    settings: SiteSettingsModel,
    isOnline: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSaveSelectedUri: (Uri) -> Unit,
    onRemoveCustomImage: () -> Unit,
) {
    val existingCustomUri = when (slot) {
        MediaSlot.APP_LOGO -> settings.logoUri
        MediaSlot.MAIN_BANNER -> settings.mainBannerUri
        MediaSlot.PROMO_BANNER_1 -> settings.promoBanner1Uri
        MediaSlot.PROMO_BANNER_2 -> settings.promoBanner2Uri
        MediaSlot.PROMO_BANNER_3 -> settings.promoBanner3Uri
    }

    val defaultDrawable = when (slot) {
        MediaSlot.APP_LOGO -> R.drawable.img_brand_logo
        MediaSlot.MAIN_BANNER -> R.drawable.img_main_hero_banner
        MediaSlot.PROMO_BANNER_1 -> R.drawable.img_promo_banner_1
        MediaSlot.PROMO_BANNER_2 -> R.drawable.img_promo_banner_2
        MediaSlot.PROMO_BANNER_3 -> R.drawable.img_promo_banner_3
    }

    var selectedPreviewUri by remember(slot) { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedPreviewUri = uri
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("media_upload_modal_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "Customize ${slot.displayTitle}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Select an image to preview, replace, or restore the default graphic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Preview Area
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val previewModifier = if (slot == MediaSlot.APP_LOGO) {
                    Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(20.dp))
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }

                val activePreviewModel: Any? = selectedPreviewUri ?: existingCustomUri
                if (activePreviewModel != null) {
                    AsyncImage(
                        model = activePreviewModel,
                        contentDescription = "Preview of ${slot.displayTitle}",
                        contentScale = ContentScale.Crop,
                        modifier = previewModifier,
                    )
                } else {
                    Image(
                        painter = painterResource(id = defaultDrawable),
                        contentDescription = "Default ${slot.displayTitle}",
                        contentScale = ContentScale.Crop,
                        modifier = previewModifier,
                    )
                }
            }

            if (selectedPreviewUri != null) {
                Text(
                    text = "New image selected and ready to save.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = isOnline && !isBusy,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("media_choose_image_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (existingCustomUri != null || selectedPreviewUri != null) {
                            "Replace Image"
                        } else {
                            stringResource(R.string.media_upload_select_button)
                        }
                    )
                }

                Button(
                    onClick = {
                        selectedPreviewUri?.let { uri -> onSaveSelectedUri(uri) }
                    },
                    enabled = isOnline && !isBusy && selectedPreviewUri != null,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("media_save_image_button"),
                ) {
                    Text(stringResource(R.string.media_upload_save_button))
                }
            }

            if (!existingCustomUri.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onRemoveCustomImage,
                    enabled = isOnline && !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_remove_custom_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.media_upload_reset_button))
                }
            }

            if (!isOnline) {
                Text(
                    text = stringResource(R.string.offline_banner_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun ConfirmSensitiveActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.testTag("confirm_dialog_confirm_button"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_dialog_cancel_button"),
            ) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}
