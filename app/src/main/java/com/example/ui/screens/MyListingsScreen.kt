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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AvailabilityStatus
import com.example.data.model.ResourceListing
import com.example.data.model.ShareMode
import com.example.ui.components.AvailabilityPill
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ModePill
import com.example.ui.components.ResourcePhoto
import com.example.ui.theme.CoralError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    myListings: List<ResourceListing>,
    onBack: () -> Unit,
    onCreateListing: () -> Unit,
    onOpenListing: (String) -> Unit,
    onEditListing: (String) -> Unit,
    onDeleteListing: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ResourceListing?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Listings (${myListings.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateListing,
                modifier = Modifier.testTag("my_listings_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Listing")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (myListings.isEmpty()) {
                Box(modifier = Modifier.padding(20.dp)) {
                    EmptyStatePanel(
                        icon = Icons.Default.Inventory2,
                        title = "You haven't listed any resources yet",
                        subtitle = "Share textbooks, calculators, lab gear, or notes with peers in your department.",
                        actionLabel = "Create First Listing",
                        onAction = onCreateListing
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(myListings, key = { it.id }) { item ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenListing(item.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ResourcePhoto(
                                    photoRef = item.photoUrls.firstOrNull().orEmpty(),
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ModePill(ShareMode.fromKey(item.mode))
                                        AvailabilityPill(AvailabilityStatus.fromKey(item.availability))
                                    }
                                    Text(
                                        text = "${item.pickupLocation} • Max ${item.maxBorrowDays}d",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column {
                                    IconButton(onClick = { onEditListing(item.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { pendingDelete = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralError)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.size(72.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { listing ->
        ConfirmActionDialog(
            title = "Delete Listing?",
            message = "Remove \"${listing.title}\" permanently from ShareSpace?",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteListing(listing.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
