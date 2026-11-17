package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ResourceListing
import com.example.data.model.Review
import com.example.data.model.UserPrivateContact
import com.example.data.model.UserProfile
import com.example.ui.components.AvatarBadge
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ReportDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.OnIndigoContainer
import java.util.Locale

@Composable
fun OwnProfileScreen(
    profile: UserProfile?,
    privateContact: UserPrivateContact?,
    reviews: List<Review>,
    isDarkMode: Boolean,
    unreadNotifications: Int,
    onToggleDarkMode: (Boolean) -> Unit,
    onUpdateProfile: (String, String, String, String, String, Boolean, String, () -> Unit) -> Unit,
    onOpenMyListings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onSignOut: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text("Loading profile...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AvatarBadge(
                            name = profile.displayName,
                            avatarKey = profile.avatarKey,
                            size = 64.dp,
                            verified = profile.campusVerified
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.displayName, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = "${profile.department} • ${profile.year}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(
                                    text = if (profile.role == "admin") "Campus Admin" else "Student",
                                    containerColor = IndigoContainer,
                                    contentColor = OnIndigoContainer
                                )
                                if (profile.campusVerified) {
                                    StatusPill(
                                        text = profile.campusDomain,
                                        containerColor = EmeraldContainer,
                                        contentColor = OnEmeraldContainer
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatBox("Rating", "★ ${String.format(Locale.US, "%.1f", profile.averageRating)} (${profile.reviewCount})")
                        ProfileStatBox("Listed", "${profile.itemsListedCount}")
                        ProfileStatBox("Borrowed", "${profile.itemsBorrowedCount}")
                    }

                    if (privateContact != null) {
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Campus Contact (Private)", style = MaterialTheme.typography.labelLarge)
                            Text("Email: ${privateContact.collegeEmail.ifBlank { privateContact.email }}", style = MaterialTheme.typography.bodySmall)
                            Text("Phone: ${privateContact.phone.ifBlank { "Not set" }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Quick Actions & Settings
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    ProfileActionRow(
                        icon = Icons.Default.Inventory2,
                        title = "My Listings",
                        subtitle = "Edit or remove items you've shared",
                        onClick = onOpenMyListings,
                        modifier = Modifier.testTag("profile_my_listings_row")
                    )
                    HorizontalDivider()
                    ProfileActionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notification Center",
                        subtitle = if (unreadNotifications > 0) "$unreadNotifications unread alerts" else "In-app & email alerts",
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("profile_notifications_row")
                    )
                    if (profile.role == "admin") {
                        HorizontalDivider()
                        ProfileActionRow(
                            icon = Icons.Default.AdminPanelSettings,
                            title = "Admin Moderation Dashboard",
                            subtitle = "Stats, reports, user bans & listing removal",
                            onClick = onOpenAdminDashboard,
                            modifier = Modifier.testTag("profile_admin_row")
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = "Dark Mode")
                            Column {
                                Text("Dark Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Easy on the eyes for late-night study",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onToggleDarkMode,
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }
            }

            // Recent Reviews Received
            Text("Reviews About You (${reviews.size})", style = MaterialTheme.typography.titleLarge)
            if (reviews.isEmpty()) {
                Text(
                    text = "Completed borrow & lend transactions earn ratings from peers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                reviews.forEach { rev ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(rev.reviewerName, style = MaterialTheme.typography.titleSmall)
                                Text("★ ${rev.rating}/5", style = MaterialTheme.typography.labelLarge, color = AmberAccent)
                            }
                            Text(rev.resourceTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(rev.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Sign Out")
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            contact = privateContact,
            onSave = { name, dept, year, collegeEmail, phone, emailNotifs ->
                onUpdateProfile(name, dept, year, collegeEmail, phone, emailNotifs, profile.avatarKey) {
                    showEditDialog = false
                }
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    profile: UserProfile?,
    listings: List<ResourceListing>,
    reviews: List<Review>,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    onReportUser: (String, String) -> Unit
) {
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.displayName ?: "Student Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (profile != null) {
                        IconButton(onClick = { showReportDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Report User")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (profile == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                EmptyStatePanel(
                    icon = Icons.Default.Verified,
                    title = "Student profile unavailable",
                    subtitle = "This profile could not be loaded."
                )
            }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AvatarBadge(name = profile.displayName, avatarKey = profile.avatarKey, size = 60.dp, verified = profile.campusVerified)
                            Column {
                                Text(profile.displayName, style = MaterialTheme.typography.headlineMedium)
                                Text("${profile.department} • ${profile.year}", style = MaterialTheme.typography.bodyMedium)
                                Text(profile.campusDomain, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ProfileStatBox("Rating", "★ ${String.format(Locale.US, "%.1f", profile.averageRating)}")
                            ProfileStatBox("Listed", "${listings.size}")
                            ProfileStatBox("Borrowed", "${profile.itemsBorrowedCount}")
                        }
                    }
                }

                Text("Active Listings (${listings.size})", style = MaterialTheme.typography.titleLarge)
                listings.forEach { item ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenListing(item.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text("${item.pickupLocation} • ${item.mode.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Text("Peer Reviews (${reviews.size})", style = MaterialTheme.typography.titleLarge)
                reviews.forEach { rev ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(rev.reviewerName, style = MaterialTheme.typography.titleSmall)
                                Text("★ ${rev.rating}/5", style = MaterialTheme.typography.labelLarge, color = AmberAccent)
                            }
                            Text(rev.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog && profile != null) {
        ReportDialog(
            targetTitle = profile.displayName,
            onSubmit = { reason, details ->
                onReportUser(reason, details)
                showReportDialog = false
            },
            onDismiss = { showReportDialog = false }
        )
    }
}

@Composable
private fun ProfileStatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    contact: UserPrivateContact?,
    onSave: (String, String, String, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile.displayName) }
    var department by remember { mutableStateOf(profile.department) }
    var year by remember { mutableStateOf(profile.year) }
    var collegeEmail by remember { mutableStateOf(contact?.collegeEmail ?: "") }
    var phone by remember { mutableStateOf(contact?.phone ?: "") }
    var emailNotifs by remember { mutableStateOf(contact?.emailNotificationsEnabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Campus Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Full Name") }, singleLine = true)
                OutlinedTextField(value = department, onValueChange = { department = it.take(80) }, label = { Text("Department") }, singleLine = true)
                OutlinedTextField(value = year, onValueChange = { year = it.take(40) }, label = { Text("Academic Year") }, singleLine = true)
                OutlinedTextField(value = collegeEmail, onValueChange = { collegeEmail = it.take(160) }, label = { Text("College Email (.edu)") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it.take(40) }, label = { Text("Phone (Private)") }, singleLine = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Email notifications", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = emailNotifs, onCheckedChange = { emailNotifs = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, department, year, collegeEmail, phone, emailNotifs) },
                enabled = name.isNotBlank() && department.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
