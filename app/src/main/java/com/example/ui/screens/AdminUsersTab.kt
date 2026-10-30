package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminUserRecord
import com.example.ui.components.ConfirmSensitiveActionDialog
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.LocalWinnerColors
import com.example.util.BangkokTimeFormatter
import com.example.util.CurrencyFormatter
import com.example.util.ValidationUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminUsersTabContent(
    users: List<AdminUserRecord>,
    productionBaseUrl: String,
    isOnline: Boolean,
    isBusy: Boolean,
    onCreateUser: (name: String, initialBalanceBdt: String, initialAvatarUri: Uri?, onDone: () -> Unit) -> Unit,
    onUpdateUser: (userId: String, name: String, balanceBdt: String, reason: String, onDone: () -> Unit) -> Unit,
    onEditUserAvatar: (userId: String) -> Unit,
    onToggleActive: (userId: String, targetActive: Boolean) -> Unit,
    onRegenerateLink: (userId: String) -> Unit,
    onDeleteUser: (userId: String, userName: String) -> Unit,
    onOpenPublicPage: (publicId: String) -> Unit,
    onCopiedLink: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val winnerColors = LocalWinnerColors.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var newUserName by rememberSaveable { mutableStateOf("") }
    var newUserInitialBalance by rememberSaveable { mutableStateOf("0.00") }
    var newUserAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val newUserPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            newUserAvatarUri = uri
        }
    }

    var editingUser by remember { mutableStateOf<AdminUserRecord?>(null) }
    var regeneratingUser by remember { mutableStateOf<AdminUserRecord?>(null) }
    var deletingUser by remember { mutableStateOf<AdminUserRecord?>(null) }

    val filteredUsers by remember(users, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) users
            else users.filter {
                it.displayName.lowercase().contains(q) || it.publicId.lowercase().contains(q)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Create New User Card
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_create_user_card"),
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
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "Create Individual User Page",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Generates a valid public user link on your deployed URL with customizable user logo photo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UserAvatarBadge(
                        avatarUri = newUserAvatarUri,
                        displayName = newUserName,
                        size = 56.dp,
                        showEditPencil = true,
                        avatarTestTag = "admin_new_user_avatar_preview",
                        editButtonTestTag = "admin_new_user_avatar_pencil_button",
                        onEditClick = {
                            if (isOnline && !isBusy) {
                                newUserPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                    )
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        label = { Text("User Name") },
                        placeholder = { Text("e.g. Tanvir Rahman") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("admin_new_user_name_input"),
                    )
                    OutlinedTextField(
                        value = newUserInitialBalance,
                        onValueChange = { newUserInitialBalance = it },
                        label = { Text("Initial BDT Balance") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_new_user_balance_input"),
                    )
                }

                Button(
                    onClick = {
                        onCreateUser(newUserName, newUserInitialBalance, newUserAvatarUri) {
                            newUserName = ""
                            newUserInitialBalance = "0.00"
                            newUserAvatarUri = null
                        }
                    },
                    enabled = isOnline && !isBusy,
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_create_user_button"),
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create User & Generate Link")
                }
            }
        }

        // Searchable User Management Table / Cards
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "User Management (${filteredUsers.size})",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search users by name or public ID") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = "Search users")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_user_search_input"),
                )

                if (filteredUsers.isEmpty()) {
                    Text(
                        text = if (users.isEmpty()) {
                            "No user accounts yet. Create your first user above to generate a shareable public link."
                        } else {
                            "No users match \"$searchQuery\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    filteredUsers.forEachIndexed { index, user ->
                        val fullPublicUrl = ValidationUtils.buildPublicUserUrl(productionBaseUrl, user.publicId)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("admin_user_row_${user.publicId}"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // User Logo / Avatar beside User Name + Pencil Edit Button for Admin
                                UserAvatarBadge(
                                    avatarUri = user.avatarUri,
                                    displayName = user.displayName,
                                    size = 56.dp,
                                    showEditPencil = true,
                                    avatarTestTag = "admin_user_avatar_${user.publicId}",
                                    editButtonTestTag = "admin_edit_user_avatar_${user.publicId}",
                                    onEditClick = { onEditUserAvatar(user.id) },
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatBdt(user.balancePaisa),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.testTag("admin_user_balance_${user.publicId}"),
                                    )
                                    if (user.reservedPaisa > 0L) {
                                        Text(
                                            text = "Reserved for pending withdrawal: ${CurrencyFormatter.formatBdt(user.reservedPaisa)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (user.isActive) winnerColors.approvedBg else winnerColors.rejectedBg,
                                    contentColor = if (user.isActive) winnerColors.approvedFg else winnerColors.rejectedFg,
                                ) {
                                    Text(
                                        text = if (user.isActive) "Active" else "Disabled",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                            }

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = fullPublicUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }

                            Text(
                                text = "Updated: ${BangkokTimeFormatter.formatDateTime(user.updatedAtEpochMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(fullPublicUrl))
                                        onCopiedLink("Copied public link: $fullPublicUrl")
                                    },
                                    modifier = Modifier.testTag("admin_copy_link_${user.publicId}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Link")
                                }

                                OutlinedButton(
                                    onClick = { onOpenPublicPage(user.publicId) },
                                    modifier = Modifier.testTag("admin_open_page_${user.publicId}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Page")
                                }

                                OutlinedButton(
                                    onClick = { editingUser = user },
                                    enabled = isOnline && !isBusy,
                                    modifier = Modifier.testTag("admin_edit_user_${user.publicId}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit")
                                }

                                OutlinedButton(
                                    onClick = { onToggleActive(user.id, !user.isActive) },
                                    enabled = isOnline && !isBusy,
                                    modifier = Modifier.testTag("admin_toggle_active_${user.publicId}"),
                                ) {
                                    Icon(
                                        imageVector = if (user.isActive) Icons.Filled.Block else Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (user.isActive) "Deactivate" else "Activate")
                                }

                                OutlinedButton(
                                    onClick = { regeneratingUser = user },
                                    enabled = isOnline && !isBusy,
                                    modifier = Modifier.testTag("admin_regen_link_${user.publicId}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Regenerate Link")
                                }

                                OutlinedButton(
                                    onClick = { deletingUser = user },
                                    enabled = isOnline && !isBusy,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("admin_delete_user_${user.publicId}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete")
                                }
                            }

                            if (index < filteredUsers.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Name & Manual Balance Dialog
    editingUser?.let { target ->
        var editName by remember(target.id) { mutableStateOf(target.displayName) }
        var editBalance by remember(target.id) {
            mutableStateOf(CurrencyFormatter.formatPlainDecimal(target.balancePaisa))
        }
        var auditNote by remember(target.id) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Edit User & Displayed BDT Balance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        UserAvatarBadge(
                            avatarUri = target.avatarUri,
                            displayName = editName,
                            size = 56.dp,
                            showEditPencil = true,
                            avatarTestTag = "edit_dialog_user_avatar",
                            editButtonTestTag = "edit_dialog_user_avatar_pencil_button",
                            onEditClick = {
                                editingUser = null
                                onEditUserAvatar(target.id)
                            },
                        )
                        Text(
                            text = "Tap the pencil icon to upload or replace this user's logo photo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_user_name_dialog_input"),
                    )
                    OutlinedTextField(
                        value = editBalance,
                        onValueChange = { editBalance = it },
                        label = { Text("Displayed Balance (BDT)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_user_balance_dialog_input"),
                    )
                    OutlinedTextField(
                        value = auditNote,
                        onValueChange = { auditNote = it },
                        label = { Text("Adjustment Note (Optional)") },
                        placeholder = { Text("Recorded in transaction history") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_user_note_dialog_input"),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateUser(target.id, editName, editBalance, auditNote) {
                            editingUser = null
                        }
                    },
                    enabled = isOnline && !isBusy,
                    modifier = Modifier.testTag("edit_user_save_dialog_button"),
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    regeneratingUser?.let { target ->
        ConfirmSensitiveActionDialog(
            title = "Regenerate Public User Link?",
            message = "Generating a new link for ${target.displayName} will permanently invalidate their previous /u/${target.publicId} link.",
            confirmLabel = "Regenerate Link",
            onConfirm = {
                onRegenerateLink(target.id)
                regeneratingUser = null
            },
            onDismiss = { regeneratingUser = null },
        )
    }

    deletingUser?.let { target ->
        ConfirmSensitiveActionDialog(
            title = "Delete User Record?",
            message = "Permanently delete ${target.displayName} (${CurrencyFormatter.formatBdt(target.balancePaisa)})? Their public link will immediately stop resolving.",
            confirmLabel = "Delete User",
            isDestructive = true,
            onConfirm = {
                onDeleteUser(target.id, target.displayName)
                deletingUser = null
            },
            onDismiss = { deletingUser = null },
        )
    }
}
