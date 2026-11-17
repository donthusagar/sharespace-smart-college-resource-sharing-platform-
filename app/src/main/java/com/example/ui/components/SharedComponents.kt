package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AvailabilityStatus
import com.example.data.model.BorrowStatus
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceCondition
import com.example.data.model.ResourceListing
import com.example.data.model.ShareMode
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.OnIndigoContainer
import com.example.ui.theme.OnTealContainer
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealSecondary
import com.example.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResourcePhoto(
    photoRef: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val drawableRes = ImageUtils.resolveDrawableRes(photoRef)
    when {
        drawableRes != null -> {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        photoRef.startsWith("data:image") -> {
            val bytes = remember(photoRef) { ImageUtils.decodeBase64DataUri(photoRef) }
            AsyncImage(
                model = ImageRequest.Builder(context).data(bytes).crossfade(true).build(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        photoRef.isNotBlank() -> {
            AsyncImage(
                model = ImageRequest.Builder(context).data(photoRef).crossfade(true).build(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        else -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun AvatarBadge(
    name: String,
    avatarKey: String,
    size: Dp = 40.dp,
    verified: Boolean = false
) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "S" }
    val bg = when (avatarKey.hashCode().let { kotlin.math.abs(it) % 4 }) {
        0 -> IndigoPrimary
        1 -> TealSecondary
        2 -> AmberAccent
        else -> Color(0xFF4F46E5)
    }
    Box(modifier = Modifier.size(size)) {
        if (avatarKey.startsWith("http") || avatarKey.startsWith("data:image")) {
            AsyncImage(
                model = avatarKey,
                contentDescription = "$name avatar",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (verified) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified campus student",
                tint = TealSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(1.dp)
            )
        }
    }
}

fun categoryIcon(category: ResourceCategory): ImageVector = when (category) {
    ResourceCategory.TEXTBOOKS -> Icons.AutoMirrored.Filled.MenuBook
    ResourceCategory.CALCULATORS -> Icons.Default.Calculate
    ResourceCategory.LAB_EQUIPMENT -> Icons.Default.Biotech
    ResourceCategory.NOTES -> Icons.Default.Description
    ResourceCategory.OTHER -> Icons.Default.Category
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ModePill(mode: ShareMode) {
    val (bg, fg) = when (mode) {
        ShareMode.LEND -> IndigoContainer to OnIndigoContainer
        ShareMode.EXCHANGE -> AmberContainer to OnAmberContainer
        ShareMode.FREE -> EmeraldContainer to OnEmeraldContainer
    }
    StatusPill(text = mode.label, containerColor = bg, contentColor = fg)
}

@Composable
fun AvailabilityPill(availability: AvailabilityStatus) {
    val (bg, fg) = when (availability) {
        AvailabilityStatus.AVAILABLE -> TealContainer to OnTealContainer
        AvailabilityStatus.BORROWED -> AmberContainer to OnAmberContainer
        AvailabilityStatus.UNAVAILABLE -> CoralContainer to OnCoralContainer
    }
    StatusPill(text = availability.label, containerColor = bg, contentColor = fg)
}

@Composable
fun BorrowStatusPill(status: BorrowStatus) {
    val (bg, fg) = when (status) {
        BorrowStatus.PENDING -> AmberContainer to OnAmberContainer
        BorrowStatus.APPROVED -> IndigoContainer to OnIndigoContainer
        BorrowStatus.BORROWED -> TealContainer to OnTealContainer
        BorrowStatus.RETURNED -> EmeraldContainer to OnEmeraldContainer
        BorrowStatus.REJECTED, BorrowStatus.OVERDUE -> CoralContainer to OnCoralContainer
        BorrowStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(text = status.label, containerColor = bg, contentColor = fg)
}

@Composable
fun ResourceListingCard(
    item: ResourceListing,
    isSaved: Boolean,
    onToggleWishlist: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cat = ResourceCategory.fromKey(item.category)
    val cond = ResourceCondition.fromKey(item.condition)
    val mode = ShareMode.fromKey(item.mode)
    val avail = AvailabilityStatus.fromKey(item.availability)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("listing_card_${item.id}")
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                ResourcePhoto(
                    photoRef = item.photoUrls.firstOrNull().orEmpty(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModePill(mode = mode)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        IconButton(
                            onClick = onToggleWishlist,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("wishlist_btn_${item.id}")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isSaved) "Remove from wishlist" else "Save to wishlist",
                                tint = if (isSaved) IndigoPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    AvailabilityPill(availability = avail)
                }
            }

            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon(cat),
                        contentDescription = cat.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${cat.label} • ${cond.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    if (item.reviewCount > 0) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = AmberAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f (%d)", item.averageRating, item.reviewCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Pickup location",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${item.pickupLocation} • Max ${item.maxBorrowDays}d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    AvatarBadge(name = item.ownerName, avatarKey = item.ownerId, size = 26.dp, verified = true)
                    Text(
                        text = "${item.ownerName} • ${item.department}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingSkeletonList(count: Int = 3) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "alpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.padding(top = 6.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ErrorStatePanel(message: String, onRetry: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CoralContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = OnCoralContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = OnCoralContainer
            )
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CoralError)
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ReviewDialog(
    targetName: String,
    resourceTitle: String,
    onSubmit: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Share feedback on \"$resourceTitle\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { rating = star },
                            modifier = Modifier.testTag("star_rating_$star")
                        ) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star stars",
                                tint = if (star <= rating) AmberAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500) },
                    label = { Text("Review comment") },
                    placeholder = { Text("Punctual pickup, great item condition...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment.ifBlank { "Smooth campus exchange!" }) },
                modifier = Modifier.testTag("submit_review_button")
            ) {
                Text("Submit Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ReportDialog(
    targetTitle: String,
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf("Misleading description", "Inappropriate behavior", "Damaged/unsafe item", "Spam or prohibited")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report \"$targetTitle\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    val selected = selectedReason == reason
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable { selectedReason = reason }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = { Text("Additional details (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatDateShort(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))
}

fun formatCountdownLabel(dueDateMillis: Long): Pair<String, Boolean> {
    val diff = dueDateMillis - System.currentTimeMillis()
    val dayMs = 86_400_000L
    return when {
        diff < 0 -> {
            val daysOver = ((-diff) / dayMs) + 1
            "Overdue by ${daysOver}d" to true
        }
        diff <= 6 * 3_600_000L -> "Due Today" to false
        diff <= dayMs -> "Due Tomorrow" to false
        else -> {
            val daysLeft = (diff / dayMs) + 1
            "Due in ${daysLeft}d" to false
        }
    }
}
