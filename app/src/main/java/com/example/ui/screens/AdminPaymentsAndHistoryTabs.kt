package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DepositRecord
import com.example.data.model.LedgerRecord
import com.example.data.model.RequestStatus
import com.example.data.model.WithdrawalRecord
import com.example.ui.components.ConfirmSensitiveActionDialog
import com.example.ui.components.RequestStatusBadge
import com.example.ui.theme.LocalWinnerColors
import com.example.util.BangkokTimeFormatter
import com.example.util.CurrencyFormatter

private enum class PaymentQueueType { DEPOSITS, WITHDRAWALS }

private data class PendingStatusDecision(
    val isDeposit: Boolean,
    val requestId: String,
    val userDisplayName: String,
    val amountPaisa: Long,
    val targetStatus: RequestStatus,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPaymentsTabContent(
    deposits: List<DepositRecord>,
    withdrawals: List<WithdrawalRecord>,
    isOnline: Boolean,
    isBusy: Boolean,
    onUpdateDepositStatus: (requestId: String, status: RequestStatus) -> Unit,
    onUpdateWithdrawalStatus: (requestId: String, status: RequestStatus) -> Unit,
) {
    val winnerColors = LocalWinnerColors.current
    var queueType by rememberSaveable { mutableStateOf(PaymentQueueType.DEPOSITS) }
    var statusFilter by rememberSaveable { mutableStateOf<RequestStatus?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDecision by remember { mutableStateOf<PendingStatusDecision?>(null) }

    val pendingDepositsCount = remember(deposits) {
        deposits.count { !it.status.isTerminal }
    }
    val pendingWithdrawalsCount = remember(withdrawals) {
        withdrawals.count { !it.status.isTerminal }
    }

    val filteredDeposits by remember(deposits, statusFilter, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            deposits.filter { dep ->
                (statusFilter == null || dep.status == statusFilter) &&
                    (q.isEmpty() ||
                        dep.userDisplayName.lowercase().contains(q) ||
                        dep.requestId.lowercase().contains(q) ||
                        dep.transactionId.lowercase().contains(q) ||
                        dep.senderBkashNumber.lowercase().contains(q))
            }
        }
    }

    val filteredWithdrawals by remember(withdrawals, statusFilter, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            withdrawals.filter { wdr ->
                (statusFilter == null || wdr.status == statusFilter) &&
                    (q.isEmpty() ||
                        wdr.userDisplayName.lowercase().contains(q) ||
                        wdr.requestId.lowercase().contains(q) ||
                        wdr.bankName.lowercase().contains(q) ||
                        wdr.accountHolderName.lowercase().contains(q) ||
                        wdr.bankAccountNumber.lowercase().contains(q))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                Text(
                    text = "Payment Requests Queue",
                    style = MaterialTheme.typography.titleLarge,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = queueType == PaymentQueueType.DEPOSITS,
                        onClick = { queueType = PaymentQueueType.DEPOSITS },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text("bKash Deposits ($pendingDepositsCount open)") },
                        modifier = Modifier.testTag("admin_tab_deposits_chip"),
                    )

                    FilterChip(
                        selected = queueType == PaymentQueueType.WITHDRAWALS,
                        onClick = { queueType = PaymentQueueType.WITHDRAWALS },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text("Bank Withdrawals ($pendingWithdrawalsCount open)") },
                        modifier = Modifier.testTag("admin_tab_withdrawals_chip"),
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All Statuses") },
                    )
                    RequestStatus.entries.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st.label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = {
                        Text(
                            if (queueType == PaymentQueueType.DEPOSITS) {
                                "Search by user, request ID, bKash number, or TrxID"
                            } else {
                                "Search by user, request ID, bank, or account number"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search requests") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_payment_search_input"),
                )

                if (queueType == PaymentQueueType.DEPOSITS) {
                    if (filteredDeposits.isEmpty()) {
                        Text(
                            text = "No matching bKash deposit requests.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        filteredDeposits.forEachIndexed { idx, dep ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .testTag("admin_deposit_card_${dep.requestId}"),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${dep.userDisplayName} • ${CurrencyFormatter.formatBdt(dep.amountPaisa)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "Request ID: ${dep.requestId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    RequestStatusBadge(status = dep.status)
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "Sender bKash Number: ${dep.senderBkashNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Transaction ID: ${dep.transactionId}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Submitted: ${BangkokTimeFormatter.formatDateTime(dep.createdAtEpochMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Last Updated: ${BangkokTimeFormatter.formatDateTime(dep.updatedAtEpochMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (dep.status.isTerminal) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = if (dep.isCredited) {
                                                "Finalized — balance credited once (locked against duplicate approval)."
                                            } else {
                                                "Finalized as Rejected (no balance change)."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (dep.status != RequestStatus.PENDING) {
                                            OutlinedButton(
                                                onClick = { onUpdateDepositStatus(dep.requestId, RequestStatus.PENDING) },
                                                enabled = isOnline && !isBusy,
                                            ) {
                                                Text("Set Pending")
                                            }
                                        }
                                        if (dep.status != RequestStatus.PROCESSING) {
                                            OutlinedButton(
                                                onClick = { onUpdateDepositStatus(dep.requestId, RequestStatus.PROCESSING) },
                                                enabled = isOnline && !isBusy,
                                                modifier = Modifier.testTag("admin_deposit_processing_${dep.requestId}"),
                                            ) {
                                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Set Processing")
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                pendingDecision = PendingStatusDecision(
                                                    isDeposit = true,
                                                    requestId = dep.requestId,
                                                    userDisplayName = dep.userDisplayName,
                                                    amountPaisa = dep.amountPaisa,
                                                    targetStatus = RequestStatus.APPROVED,
                                                )
                                            },
                                            enabled = isOnline && !isBusy,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = winnerColors.approvedFg,
                                                contentColor = winnerColors.heroOnCard,
                                            ),
                                            modifier = Modifier.testTag("admin_deposit_approve_${dep.requestId}"),
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Approve & Credit")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pendingDecision = PendingStatusDecision(
                                                    isDeposit = true,
                                                    requestId = dep.requestId,
                                                    userDisplayName = dep.userDisplayName,
                                                    amountPaisa = dep.amountPaisa,
                                                    targetStatus = RequestStatus.REJECTED,
                                                )
                                            },
                                            enabled = isOnline && !isBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.testTag("admin_deposit_reject_${dep.requestId}"),
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Reject")
                                        }
                                    }
                                }

                                if (idx < filteredDeposits.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                } else {
                    if (filteredWithdrawals.isEmpty()) {
                        Text(
                            text = "No matching bank account withdrawal requests.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        filteredWithdrawals.forEachIndexed { idx, wdr ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .testTag("admin_withdrawal_card_${wdr.requestId}"),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${wdr.userDisplayName} • ${CurrencyFormatter.formatBdt(wdr.amountPaisa)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "Request ID: ${wdr.requestId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    RequestStatusBadge(status = wdr.status)
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "Bank Name: ${wdr.bankName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Account Holder: ${wdr.accountHolderName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Account Number: ${wdr.bankAccountNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Submitted: ${BangkokTimeFormatter.formatDateTime(wdr.createdAtEpochMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Last Updated: ${BangkokTimeFormatter.formatDateTime(wdr.updatedAtEpochMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (wdr.status.isTerminal) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = if (wdr.isDeducted) {
                                                "Finalized — balance deducted once (locked against duplicate deduction)."
                                            } else {
                                                "Finalized as Rejected — pending reservation released back to available balance."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (wdr.status != RequestStatus.PENDING) {
                                            OutlinedButton(
                                                onClick = { onUpdateWithdrawalStatus(wdr.requestId, RequestStatus.PENDING) },
                                                enabled = isOnline && !isBusy,
                                            ) {
                                                Text("Set Pending")
                                            }
                                        }
                                        if (wdr.status != RequestStatus.PROCESSING) {
                                            OutlinedButton(
                                                onClick = { onUpdateWithdrawalStatus(wdr.requestId, RequestStatus.PROCESSING) },
                                                enabled = isOnline && !isBusy,
                                                modifier = Modifier.testTag("admin_withdrawal_processing_${wdr.requestId}"),
                                            ) {
                                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Set Processing")
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                pendingDecision = PendingStatusDecision(
                                                    isDeposit = false,
                                                    requestId = wdr.requestId,
                                                    userDisplayName = wdr.userDisplayName,
                                                    amountPaisa = wdr.amountPaisa,
                                                    targetStatus = RequestStatus.APPROVED,
                                                )
                                            },
                                            enabled = isOnline && !isBusy,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = winnerColors.approvedFg,
                                                contentColor = winnerColors.heroOnCard,
                                            ),
                                            modifier = Modifier.testTag("admin_withdrawal_approve_${wdr.requestId}"),
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Approve & Deduct")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pendingDecision = PendingStatusDecision(
                                                    isDeposit = false,
                                                    requestId = wdr.requestId,
                                                    userDisplayName = wdr.userDisplayName,
                                                    amountPaisa = wdr.amountPaisa,
                                                    targetStatus = RequestStatus.REJECTED,
                                                )
                                            },
                                            enabled = isOnline && !isBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.testTag("admin_withdrawal_reject_${wdr.requestId}"),
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Reject & Release")
                                        }
                                    }
                                }

                                if (idx < filteredWithdrawals.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDecision?.let { decision ->
        val formattedAmt = CurrencyFormatter.formatBdt(decision.amountPaisa)
        val kind = if (decision.isDeposit) "bKash Deposit" else "Bank Withdrawal"
        val description = when {
            decision.isDeposit && decision.targetStatus == RequestStatus.APPROVED ->
                "Approve $kind (${decision.requestId}) and credit $formattedAmt to ${decision.userDisplayName}'s displayed balance once?"
            decision.isDeposit && decision.targetStatus == RequestStatus.REJECTED ->
                "Reject $kind (${decision.requestId}) for ${decision.userDisplayName}? Their balance will remain unchanged."
            !decision.isDeposit && decision.targetStatus == RequestStatus.APPROVED ->
                "Approve $kind (${decision.requestId}) and permanently deduct $formattedAmt from ${decision.userDisplayName}'s balance?"
            else ->
                "Reject $kind (${decision.requestId}) and release the $formattedAmt pending hold back to ${decision.userDisplayName}'s available balance?"
        }

        ConfirmSensitiveActionDialog(
            title = "${decision.targetStatus.label}: $kind",
            message = description,
            confirmLabel = decision.targetStatus.label,
            isDestructive = decision.targetStatus == RequestStatus.REJECTED,
            onConfirm = {
                if (decision.isDeposit) {
                    onUpdateDepositStatus(decision.requestId, decision.targetStatus)
                } else {
                    onUpdateWithdrawalStatus(decision.requestId, decision.targetStatus)
                }
                pendingDecision = null
            },
            onDismiss = { pendingDecision = null },
        )
    }
}

@Composable
fun AdminHistoryTabContent(
    ledgerEntries: List<LedgerRecord>,
) {
    val winnerColors = LocalWinnerColors.current
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredEntries by remember(ledgerEntries, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) ledgerEntries
            else ledgerEntries.filter {
                it.userDisplayName.lowercase().contains(q) ||
                    it.referenceId.lowercase().contains(q) ||
                    it.note.lowercase().contains(q) ||
                    it.actionType.label.lowercase().contains(q) ||
                    it.actorEmail.lowercase().contains(q)
            }
        }
    }

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
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "Transaction & Balance Audit History (${filteredEntries.size})",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "All timestamps formatted in Asia/Bangkok (Thailand Time)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search history by user, reference ID, or note") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search history") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_history_search_input"),
            )

            if (filteredEntries.isEmpty()) {
                Text(
                    text = "No transaction or balance adjustment records found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                filteredEntries.forEachIndexed { idx, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("admin_ledger_row_${entry.entryId}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${entry.userDisplayName} • ${entry.actionType.label}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )

                            val deltaPrefix = if (entry.deltaPaisa > 0) "+" else ""
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    entry.deltaPaisa > 0 -> winnerColors.approvedBg
                                    entry.deltaPaisa < 0 -> winnerColors.rejectedBg
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = when {
                                    entry.deltaPaisa > 0 -> winnerColors.approvedFg
                                    entry.deltaPaisa < 0 -> winnerColors.rejectedFg
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ) {
                                Text(
                                    text = "$deltaPrefix${CurrencyFormatter.formatBdt(entry.deltaPaisa)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Text(
                            text = "Balance: ${CurrencyFormatter.formatBdt(entry.balanceBeforePaisa)} → ${CurrencyFormatter.formatBdt(entry.balanceAfterPaisa)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = entry.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${BangkokTimeFormatter.formatDateTime(entry.createdAtEpochMs)} • Ref: ${entry.referenceId} • By: ${entry.actorEmail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (idx < filteredEntries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
