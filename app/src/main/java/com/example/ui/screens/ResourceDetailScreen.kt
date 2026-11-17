package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AvailabilityStatus
import com.example.data.model.BorrowStatus
import com.example.data.model.ResourceBookingSlot
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceCondition
import com.example.data.model.ResourceListing
import com.example.data.model.Review
import com.example.data.model.ShareMode
import com.example.ui.components.AvailabilityPill
import com.example.ui.components.AvatarBadge
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ModePill
import com.example.ui.components.ReportDialog
import com.example.ui.components.ResourcePhoto
import com.example.ui.components.formatDateShort
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.OnCoralContainer
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDetailScreen(
    resource: ResourceListing?,
    bookings: List<ResourceBookingSlot>,
    reviews: List<Review>,
    currentUserId: String,
    isSaved: Boolean,
    isBusy: Boolean,
    onBack: () -> Unit,
    onToggleWishlist: () -> Unit,
    onOpenOwnerProfile: (String) -> Unit,
    onEditListing: (String) -> Unit,
    onSubmitBorrowRequest: (Long, Long, String, () -> Unit) -> Unit,
    onSubmitReport: (String, String) -> Unit
) {
    var showBorrowDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resource Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (resource != null) {
                        IconButton(onClick = onToggleWishlist, modifier = Modifier.testTag("detail_wishlist_button")) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Toggle Wishlist",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (resource.ownerId == currentUserId) {
                            IconButton(onClick = { onEditListing(resource.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Listing")
                            }
                        } else {
                            IconButton(onClick = { showReportDialog = true }, modifier = Modifier.testTag("detail_report_button")) {
                                Icon(Icons.Default.Flag, contentDescription = "Report Listing")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (resource == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStatePanel(
                    icon = Icons.Default.CalendarMonth,
                    title = "Listing not found",
                    subtitle = "This resource may have been removed by the owner or an admin."
                )
            }
            return@Scaffold
        }

        val isOwner = resource.ownerId == currentUserId
        val activeBookings = remember(bookings) {
            bookings.filter {
                it.status in setOf(BorrowStatus.APPROVED.key, BorrowStatus.BORROWED.key, BorrowStatus.OVERDUE.key)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Photo Gallery
                val photos = resource.photoUrls.ifEmpty { listOf("") }
                val pagerState = rememberPagerState(pageCount = { photos.size })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        ResourcePhoto(
                            photoRef = photos[page],
                            contentDescription = "${resource.title} photo ${page + 1}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (photos.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(photos.size) { idx ->
                                Box(
                                    modifier = Modifier
                                        .size(if (pagerState.currentPage == idx) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (pagerState.currentPage == idx) Color.White else Color.White.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }

                // Header Badges & Title
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModePill(ShareMode.fromKey(resource.mode))
                    AvailabilityPill(AvailabilityStatus.fromKey(resource.availability))
                    Spacer(Modifier.weight(1f))
                    if (resource.reviewCount > 0) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f (%d)", resource.averageRating, resource.reviewCount),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                Text(text = resource.title, style = MaterialTheme.typography.headlineMedium)

                Text(
                    text = "${ResourceCategory.fromKey(resource.category).label} • ${ResourceCondition.fromKey(resource.condition).label} • ${resource.department}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = resource.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Pickup & Borrowing Details
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Campus Pickup Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(resource.pickupLocation, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text("Max Borrow Window", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Up to ${resource.maxBorrowDays} days per transaction", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }

                // Owner Card (Navigates to Public Profile)
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_profile_card")
                        .clickable { onOpenOwnerProfile(resource.ownerId) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AvatarBadge(name = resource.ownerName, avatarKey = resource.ownerId, size = 46.dp, verified = true)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isOwner) "${resource.ownerName} (You)" else resource.ownerName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${resource.ownerDepartment} • ★ ${String.format(Locale.US, "%.1f", resource.ownerRating)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onOpenOwnerProfile(resource.ownerId) }) {
                            Text("View Profile")
                        }
                    }
                }

                // Booked Dates Notice
                if (activeBookings.isNotEmpty()) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Reserved Date Ranges", style = MaterialTheme.typography.titleSmall)
                            activeBookings.forEach { slot ->
                                Text(
                                    text = "• ${formatDateShort(slot.startDateMillis)} – ${formatDateShort(slot.endDateMillis)} (${slot.status})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Primary Action CTA
                if (isOwner) {
                    OutlinedButton(
                        onClick = { onEditListing(resource.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Edit Your Listing")
                    }
                } else {
                    val isUnavailable = resource.availability == AvailabilityStatus.UNAVAILABLE.key
                    Button(
                        onClick = { showBorrowDialog = true },
                        enabled = !isUnavailable && !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("request_borrow_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = when {
                                isUnavailable -> "Currently Unavailable"
                                resource.mode == ShareMode.FREE.key -> "Request to Claim (Free)"
                                resource.mode == ShareMode.EXCHANGE.key -> "Request Exchange"
                                else -> "Request to Borrow"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                // Reviews Section
                Text("Student Reviews (${reviews.size})", style = MaterialTheme.typography.titleLarge)
                if (reviews.isEmpty()) {
                    Text(
                        text = "No reviews yet for this listing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    reviews.forEach { review ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(review.reviewerName, style = MaterialTheme.typography.titleSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                                        Text(" ${review.rating}/5", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBorrowDialog && resource != null) {
        BorrowRequestDialog(
            resource = resource,
            activeBookings = bookings.filter {
                it.status in setOf(BorrowStatus.APPROVED.key, BorrowStatus.BORROWED.key, BorrowStatus.OVERDUE.key)
            },
            onSubmit = { startMs, endMs, msg ->
                onSubmitBorrowRequest(startMs, endMs, msg) {
                    showBorrowDialog = false
                }
            },
            onDismiss = { showBorrowDialog = false }
        )
    }

    if (showReportDialog && resource != null) {
        ReportDialog(
            targetTitle = resource.title,
            onSubmit = { reason, details ->
                onSubmitReport(reason, details)
                showReportDialog = false
            },
            onDismiss = { showReportDialog = false }
        )
    }
}

@Composable
private fun BorrowRequestDialog(
    resource: ResourceListing,
    activeBookings: List<ResourceBookingSlot>,
    onSubmit: (Long, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val dayMs = 86_400_000L
    val todayStart = remember {
        val now = System.currentTimeMillis()
        now - (now % dayMs) + dayMs // start tomorrow by default
    }
    var offsetDays by remember { mutableIntStateOf(0) }
    var durationDays by remember { mutableIntStateOf(resource.maxBorrowDays.toInt().coerceAtMost(5).coerceAtLeast(1)) }
    var message by remember { mutableStateOf("") }

    val startMillis = todayStart + (offsetDays * dayMs)
    val endMillis = startMillis + ((durationDays - 1).coerceAtLeast(0) * dayMs)

    val overlapsBooking = remember(startMillis, endMillis, activeBookings) {
        activeBookings.any { slot ->
            startMillis <= slot.endDateMillis && endMillis >= slot.startDateMillis
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request \"${resource.title}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Start Date: ${formatDateShort(startMillis)} (in ${offsetDays + 1} day(s))",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = offsetDays.toFloat(),
                    onValueChange = { offsetDays = it.roundToInt() },
                    valueRange = 0f..21f
                )

                Text(
                    text = "Duration: $durationDays day(s) (until ${formatDateShort(endMillis)})",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = durationDays.toFloat(),
                    onValueChange = { durationDays = it.roundToInt().coerceAtLeast(1) },
                    valueRange = 1f..resource.maxBorrowDays.toFloat().coerceAtLeast(1f)
                )

                if (overlapsBooking) {
                    Card(colors = CardDefaults.cardColors(containerColor = CoralContainer)) {
                        Text(
                            text = "These dates overlap an active booking. Shift start date or duration.",
                            color = OnCoralContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(400) },
                    label = { Text("Message to ${resource.ownerName}") },
                    placeholder = { Text("Course name, pickup time preference...") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("borrow_message_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        startMillis,
                        endMillis,
                        message.ifBlank { "Hi ${resource.ownerName}, I'd love to borrow this for class!" }
                    )
                },
                enabled = !overlapsBooking,
                modifier = Modifier.testTag("confirm_borrow_request_button")
            ) {
                Text("Send Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
