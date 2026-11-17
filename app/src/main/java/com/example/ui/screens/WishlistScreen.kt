package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.ResourceListing
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ResourceListingCard

@Composable
fun WishlistScreen(
    allResources: List<ResourceListing>,
    wishlistIds: Set<String>,
    onToggleWishlist: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    onExplore: () -> Unit,
    isWideScreen: Boolean
) {
    val savedItems = remember(allResources, wishlistIds) {
        allResources.filter { it.id in wishlistIds }
    }

    if (savedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            EmptyStatePanel(
                icon = Icons.Default.BookmarkBorder,
                title = "Your wishlist is empty",
                subtitle = "Tap the bookmark icon on any textbook, calculator, or lab kit to save it for later.",
                actionLabel = "Browse Campus Feed",
                onAction = onExplore
            )
        }
    } else {
        LazyVerticalGrid(
            columns = if (isWideScreen) GridCells.Adaptive(minSize = 280.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(savedItems, key = { it.id }) { listing ->
                ResourceListingCard(
                    item = listing,
                    isSaved = true,
                    onToggleWishlist = { onToggleWishlist(listing.id) },
                    onClick = { onOpenListing(listing.id) }
                )
            }
        }
    }
}
