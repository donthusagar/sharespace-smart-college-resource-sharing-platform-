package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ModerationReport
import com.example.data.model.ResourceListing
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnEmeraldContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    users: List<UserProfile>,
    listings: List<ResourceListing>,
    reports: List<ModerationReport>,
    currentUserId: String,
    isBusy: Boolean,
    onBack: () -> Unit,
    onToggleBanUser: (String, Boolean, String) -> Unit,
    onSetListingRemoved: (String, Boolean) -> Unit,
    onResolveReport: (String, String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var banTarget by remember { mutableStateOf<UserProfile?>(null) }

    val openReportsCount = reports.count { it.status == "open" }
    val activeListingsCount = listings.count { it.status == "active" }
    val bannedUsersCount = users.count { it.banned }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Moderation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stats Strip
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminStatCard("Users", "${users.size}", "$bannedUsersCount banned", Modifier.weight(1f))
                AdminStatCard("Listings", "$activeListingsCount", "${listings.size} total", Modifier.weight(1f))
                AdminStatCard("Reports", "$openReportsCount", "open", Modifier.weight(1f))
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Reports (${reports.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Users (${users.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Listings (${listings.size})") })
            }

            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (reports.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Default.Shield,
                                    title = "No reports filed",
                                    subtitle = "Reported listings and users will appear here for review."
                                )
                            }
                        } else {
                            items(reports, key = { it.id }) { rep ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${rep.targetType.uppercase()}: ${rep.targetName}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                            StatusPill(
                                                text = rep.status.uppercase(),
                                                containerColor = if (rep.status == "open") AmberContainer else EmeraldContainer,
                                                contentColor = if (rep.status == "open") OnAmberContainer else OnEmeraldContainer
                                            )
                                        }
                                        Text("Reason: ${rep.reason}", style = MaterialTheme.typography.labelLarge)
                                        if (rep.details.isNotBlank()) {
                                            Text(rep.details, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text("Reported by ${rep.reporterName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (rep.status == "open") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        if (rep.targetType == "listing") {
                                                            onSetListingRemoved(rep.targetId, true)
                                                        } else {
                                                            onToggleBanUser(rep.targetId, true, rep.reason)
                                                        }
                                                        onResolveReport(rep.id, "resolved", "Enforcement action applied by admin")
                                                    },
                                                    enabled = !isBusy,
                                                    colors = ButtonDefaults.buttonColors(containerColor = CoralError)
                                                ) {
                                                    Text(if (rep.targetType == "listing") "Remove & Resolve" else "Ban & Resolve")
                                                }
                                                OutlinedButton(
                                                    onClick = { onResolveReport(rep.id, "dismissed", "Reviewed — no violation") },
                                                    enabled = !isBusy
                                                ) {
                                                    Text("Dismiss")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        items(users, key = { it.uid }) { user ->
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text("${user.department} • ${user.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (user.banned) {
                                            StatusPill("BANNED", CoralContainer, OnCoralContainer)
                                        }
                                    }
                                    if (user.uid != currentUserId) {
                                        OutlinedButton(
                                            onClick = {
                                                if (user.banned) onToggleBanUser(user.uid, false, "")
                                                else banTarget = user
                                            },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = if (user.banned) MaterialTheme.colorScheme.primary else CoralError
                                            ),
                                            modifier = Modifier.testTag("admin_ban_btn_${user.uid}")
                                        ) {
                                            Text(if (user.banned) "Unban" else "Ban")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        items(listings, key = { it.id }) { item ->
                            val isRemoved = item.status == "removed"
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                                        Text("By ${item.ownerName} • ${item.status.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(
                                        onClick = { onSetListingRemoved(item.id, !isRemoved) },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (isRemoved) MaterialTheme.colorScheme.primary else CoralError
                                        )
                                    ) {
                                        Text(if (isRemoved) "Restore" else "Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    banTarget?.let { target ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { banTarget = null },
            title = { Text("Ban ${target.displayName}?") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = { Text("Ban reason") },
                    placeholder = { Text("Repeated overdue non-returns or policy violation") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleBanUser(target.uid, true, reason)
                        banTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError)
                ) {
                    Text("Confirm Ban")
                }
            },
            dismissButton = {
                TextButton(onClick = { banTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
