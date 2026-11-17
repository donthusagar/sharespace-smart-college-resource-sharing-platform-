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
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.example.data.model.BorrowRequest
import com.example.data.model.BorrowStatus
import com.example.ui.components.BorrowStatusPill
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ResourcePhoto
import com.example.ui.components.ReviewDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCountdownLabel
import com.example.ui.components.formatDateShort
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnTealContainer
import com.example.ui.theme.TealContainer
import kotlin.math.roundToInt

@Composable
fun BorrowedItemsScreen(
    currentUserId: String,
    allRequests: List<BorrowRequest>,
    isBusy: Boolean,
    onOpenListing: (String) -> Unit,
    onRequestExtension: (BorrowRequest, Int) -> Unit,
    onConfirmReturned: (BorrowRequest) -> Unit,
    onSubmitReview: (BorrowRequest, Int, String) -> Unit
) {
    val relevantItems = remember(allRequests, currentUserId) {
        allRequests.filter { req ->
            req.status in setOf(
                BorrowStatus.BORROWED.key,
                BorrowStatus.OVERDUE.key,
                BorrowStatus.RETURNED.key
            )
        }.sortedBy { req ->
            when (req.status) {
                BorrowStatus.OVERDUE.key -> 0
                BorrowStatus.BORROWED.key -> 1
                else -> 2
            }
        }
    }

    var extensionTarget by remember { mutableStateOf<BorrowRequest?>(null) }
    var reviewTarget by remember { mutableStateOf<BorrowRequest?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (relevantItems.isEmpty()) {
            Box(modifier = Modifier.padding(20.dp)) {
                EmptyStatePanel(
                    icon = Icons.AutoMirrored.Filled.AssignmentReturn,
                    title = "No active borrowed items",
                    subtitle = "Items you've borrowed or lent out will appear here with live due-date countdowns."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(relevantItems, key = { it.id }) { req ->
                    val status = BorrowStatus.fromKey(req.status)
                    val isBorrower = req.borrowerId == currentUserId
                    val (countdownText, isOverdueTime) = formatCountdownLabel(req.dueDateMillis)

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
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        text = if (isBorrower) "Borrowed from ${req.ownerName}" else "Lent to ${req.borrowerName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Due: ${formatDateShort(req.dueDateMillis)}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            if (status == BorrowStatus.BORROWED || status == BorrowStatus.OVERDUE) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = countdownText,
                                        containerColor = if (isOverdueTime || status == BorrowStatus.OVERDUE) CoralContainer else TealContainer,
                                        contentColor = if (isOverdueTime || status == BorrowStatus.OVERDUE) OnCoralContainer else OnTealContainer
                                    )
                                    if (req.reminderDayBeforeSent || req.reminderDueDaySent) {
                                        StatusPill(
                                            text = if (req.reminderDueDaySent) "Due-day reminder sent" else "1d reminder sent",
                                            containerColor = AmberContainer,
                                            contentColor = OnAmberContainer
                                        )
                                    }
                                }
                            }

                            if (req.extensionRequested && req.extensionStatus == "pending") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Extension of +${req.requestedExtensionDays} day(s) awaiting ${req.ownerName}'s approval.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            if (isBorrower && (status == BorrowStatus.BORROWED || status == BorrowStatus.OVERDUE) && !req.extensionRequested) {
                                OutlinedButton(
                                    onClick = { extensionTarget = req },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("request_extension_${req.id}")
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Request Due-Date Extension")
                                }
                            }

                            if (!isBorrower && (status == BorrowStatus.BORROWED || status == BorrowStatus.OVERDUE)) {
                                Button(
                                    onClick = { onConfirmReturned(req) },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Confirm Item Returned")
                                }
                            }

                            if (status == BorrowStatus.RETURNED) {
                                val alreadyReviewed = if (isBorrower) req.borrowerReviewed else req.ownerReviewed
                                if (!alreadyReviewed) {
                                    Button(
                                        onClick = { reviewTarget = req },
                                        modifier = Modifier.fillMaxWidth().testTag("borrowed_review_${req.id}")
                                    ) {
                                        Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(if (isBorrower) "Rate ${req.ownerName} & Item" else "Rate ${req.borrowerName}")
                                    }
                                } else {
                                    Text(
                                        text = "✓ Review submitted",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    extensionTarget?.let { req ->
        var days by remember { mutableIntStateOf(3) }
        AlertDialog(
            onDismissRequest = { extensionTarget = null },
            title = { Text("Request Extension") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Extend \"${req.resourceTitle}\" by $days day(s)?")
                    Slider(
                        value = days.toFloat(),
                        onValueChange = { days = it.roundToInt().coerceIn(1, 14) },
                        valueRange = 1f..14f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRequestExtension(req, days)
                        extensionTarget = null
                    },
                    modifier = Modifier.testTag("confirm_extension_button")
                ) {
                    Text("Send Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { extensionTarget = null }) { Text("Cancel") }
            }
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
