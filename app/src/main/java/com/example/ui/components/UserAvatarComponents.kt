package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AdminUserRecord

/**
 * Displays a user's circular logo / profile image beside their display name.
 * When [showEditPencil] is true (strictly restricted to authorized Admin views),
 * renders a pencil-shaped edit button anchored to the avatar corner.
 */
@Composable
fun UserAvatarBadge(
    avatarUri: Any?,
    displayName: String,
    avatarTestTag: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    showEditPencil: Boolean = false,
    editButtonTestTag: String = "edit_user_avatar_pencil_button",
    onEditClick: (() -> Unit)? = null,
) {
    val hasCustomImage = avatarUri != null && avatarUri.toString().isNotBlank()
    val initials = remember(displayName) { extractInitials(displayName) }

    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            modifier = Modifier
                .padding(
                    end = if (showEditPencil && onEditClick != null) 4.dp else 0.dp,
                    bottom = if (showEditPencil && onEditClick != null) 4.dp else 0.dp,
                )
                .size(size)
                .testTag(avatarTestTag),
        ) {
            if (hasCustomImage) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "${displayName.ifBlank { "User" }} ${stringResource(R.string.user_avatar_cd)}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (initials.isNotEmpty()) {
                        Text(
                            text = initials,
                            style = if (size >= 72.dp) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = stringResource(R.string.user_avatar_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(size * 0.52f),
                        )
                    }
                }
            }
        }

        if (showEditPencil && onEditClick != null) {
            FilledIconButton(
                onClick = onEditClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(28.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .testTag(editButtonTestTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit_user_avatar_cd),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAvatarUploadModalSheet(
    user: AdminUserRecord,
    isOnline: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSaveSelectedUri: (Uri) -> Unit,
    onRemoveCustomAvatar: () -> Unit,
) {
    var selectedPreviewUri by remember(user.id) { mutableStateOf<Uri?>(null) }

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
        modifier = Modifier.testTag("user_avatar_upload_modal_sheet"),
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
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "Customize User Logo Photo",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "User: ${user.displayName} • Displayed beside their name on their public page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UserAvatarBadge(
                        avatarUri = selectedPreviewUri ?: user.avatarUri,
                        displayName = user.displayName,
                        avatarTestTag = "user_avatar_modal_preview",
                        size = 104.dp,
                        showEditPencil = false,
                    )
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (selectedPreviewUri != null) {
                Text(
                    text = "New user logo photo selected and ready to save.",
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
                        .testTag("user_avatar_choose_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!user.avatarUri.isNullOrBlank() || selectedPreviewUri != null) {
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
                        .testTag("user_avatar_save_button"),
                ) {
                    Text(stringResource(R.string.media_upload_save_button))
                }
            }

            if (!user.avatarUri.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onRemoveCustomAvatar,
                    enabled = isOnline && !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_avatar_remove_button"),
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

private fun extractInitials(displayName: String): String {
    val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    return if (parts.size == 1) {
        parts[0].take(2).uppercase()
    } else {
        "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}
