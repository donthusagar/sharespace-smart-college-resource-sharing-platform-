package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BorrowRequest
import com.example.data.model.BorrowStatus
import com.example.ui.components.BorrowStatusPill
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ResourcePhoto
import com.example.ui.components.ReviewDialog
import com.example.ui.components.formatDateShort
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.TealSecondary

@Composable
fun RequestsScreen(
    currentUserId: String,
    allRequests: List<BorrowRequest>,
    isBusy: Boolean,
    onOpenListing: (String) -> Unit,
    onApprove: (BorrowRequest) -> Unit,
    onReject: (BorrowRequest, String) -> Unit,
    onMarkBorrowed: (BorrowRequest) -> Unit,
    onConfirmReturned: (BorrowRequest) -> Unit,
    onCancelRequest: (BorrowRequest) -> Unit,
    onResolveExtension: (BorrowRequest, Boolean) -> Unit,
    onSubmitReview: (BorrowRequest, Int, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val received = remember(allRequests, currentUserId) {
        allRequests.filter { it.ownerId == currentUserId }
    }
    val sent = remember(allRequests, currentUserId) {
        allRequests.filter { it.borrowerId == currentUserId }
    }

    var rejectTarget by remember { mutableStateOf<BorrowRequest?>(null) }
    var cancelTarget by remember { mutableStateOf<BorrowRequest?>(null) }
    var reviewTarget by remember { mutableStateOf<BorrowRequest?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Received (${received.size})") },
                modifier = Modifier.testTag("requests_tab_received")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Sent (${sent.size})") },
                modifier = Modifier.testTag("requests_tab_sent")
            )
        }

        val activeList = if (selectedTab == 0) received else sent
        if (activeList.isEmpty()) {
            Box(modifier = Modifier.padding(20.dp)) {
                EmptyStatePanel(
                    icon = Icons.Default.SwapHoriz,
                    title = if (selectedTab == 0) "No incoming requests yet" else "No outgoing requests yet",
                    subtitle = if (selectedTab == 0) {
                        "Requests from students wanting to borrow your items will appear here."
                    } else {
                        "Browse the home feed and request a resource to get started."
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activeList, key = { it.id }) { req ->
                    val status = BorrowStatus.fromKey(req.status)
                    val isOwnerView = selectedTab == 0

                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onOpenListing(req.resourceId) }
                            ) {
                                ResourcePhoto(
                                    photoRef = req.resourcePhoto,
                                    contentDescription = req.resourceTitle,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = req.resourceTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        BorrowStatusPill(status)
                                    }
                                    Text(
                                        text = if (isOwnerView) {
                                            "From ${req.borrowerName} (${req.borrowerDepartment})"
                                        } else {
                                            "Owner: ${req.ownerName}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${formatDateShort(req.startDateMillis)} → ${formatDateShort(req.dueDateMillis)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (req.message.isNotBlank()) {
                                Text(
                                    text = "\"${req.message}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (status == BorrowStatus.REJECTED && req.rejectionReason.isNotBlank()) {
                                Card(colors = CardDefaults.cardColors(containerColor = CoralContainer.copy(alpha = 0.7f))) {
                                    Text(
                                        text = "Reason: ${req.rejectionReason}",
                                        color = OnCoralContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            if (req.extensionStatus == "pending" && isOwnerView) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "${req.borrowerName} requested +${req.requestedExtensionDays} day(s) extension.",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { onResolveExtension(req, true) },
                                                enabled = !isBusy
                                            ) { Text("Approve +${req.requestedExtensionDays}d") }
                                            OutlinedButton(
                                                onClick = { onResolveExtension(req, false) },
                                                enabled = !isBusy
                                            ) { Text("Decline") }
                                        }
                                    }
                                }
                            }

                            // Action Row
                            if (isOwnerView) {
                                when (status) {
                                    BorrowStatus.PENDING -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { onApprove(req) },
                                                enabled = !isBusy,
                                                modifier = Modifier.weight(1f).testTag("approve_req_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Approve")
                                            }
                                            OutlinedButton(
                                                onClick = { rejectTarget = req },
                                                enabled = !isBusy,
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                                                modifier = Modifier.weight(1f).testTag("reject_req_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Reject")
                                            }
                                        }
                                    }
                                    BorrowStatus.APPROVED -> {
                                        Button(
                                            onClick = { onMarkBorrowed(req) },
                                            enabled = !isBusy,
                                            modifier = Modifier.fillMaxWidth().testTag("mark_borrowed_${req.id}"),
                                            colors = ButtonDefaults.buttonColors(containerColor = TealSecondary)
                                        ) {
                                            Text("Confirm Handed Over (Borrowed)", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    BorrowStatus.BORROWED, BorrowStatus.OVERDUE -> {
                                        Button(
                                            onClick = { onConfirmReturned(req) },
                                            enabled = !isBusy,
                                            modifier = Modifier.fillMaxWidth().testTag("confirm_returned_${req.id}")
                                        ) {
                                            Text("Mark as Returned")
                                        }
                                    }
                                    BorrowStatus.RETURNED -> {
                                        if (!req.ownerReviewed) {
                                            OutlinedButton(
                                                onClick = { reviewTarget = req },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.size(6.dp))
                                                Text("Rate Borrower (${req.borrowerName})")
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                            } else {
                                when (status) {
                                    BorrowStatus.PENDING, BorrowStatus.APPROVED -> {
                                        OutlinedButton(
                                            onClick = { cancelTarget = req },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                                            modifier = Modifier.fillMaxWidth().testTag("cancel_req_${req.id}")
                                        ) {
                                            Text("Cancel Request")
                                        }
                                    }
                                    BorrowStatus.RETURNED -> {
                                        if (!req.borrowerReviewed) {
                                            OutlinedButton(
                                                onClick = { reviewTarget = req },
                                                modifier = Modifier.fillMaxWidth().testTag("review_owner_${req.id}")
                                            ) {
                                                Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.size(6.dp))
                                                Text("Rate Owner & Item")
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    rejectTarget?.let { req ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Decline Request") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = { Text("Reason for ${req.borrowerName}") },
                    placeholder = { Text("Need it for my own exam this week...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("reject_reason_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReject(req, reason)
                        rejectTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                    modifier = Modifier.testTag("confirm_reject_button")
                ) {
                    Text("Decline")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("Cancel") }
            }
        )
    }

    cancelTarget?.let { req ->
        ConfirmActionDialog(
            title = "Cancel Request?",
            message = "Withdraw your request for \"${req.resourceTitle}\"?",
            confirmLabel = "Cancel Request",
            isDestructive = true,
            onConfirm = {
                onCancelRequest(req)
                cancelTarget = null
            },
            onDismiss = { cancelTarget = null }
        )
    }

    reviewTarget?.let { req ->
        val isOwnerReviewing = req.ownerId == currentUserId
        ReviewDialog(
            targetName = if (isOwnerReviewing) req.borrowerName else req.ownerName,
            resourceTitle = req.resourceTitle,
            onSubmit = { rating, comment ->
                onSubmitReview(req, rating, comment)
                reviewTarget = null
            },
            onDismiss = { reviewTarget = null }
        )
    }
}
