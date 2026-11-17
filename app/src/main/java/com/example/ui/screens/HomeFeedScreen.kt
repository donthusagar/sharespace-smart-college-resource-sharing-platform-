package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AvailabilityStatus
import com.example.data.model.BorrowRequest
import com.example.data.model.BorrowStatus
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceCondition
import com.example.data.model.ResourceListing
import com.example.data.model.ShareMode
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ErrorStatePanel
import com.example.ui.components.LoadingSkeletonList
import com.example.ui.components.ResourceListingCard
import com.example.ui.components.categoryIcon
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.TealSecondary
import com.example.ui.viewmodel.FeedFilterState
import com.example.ui.viewmodel.SortOption
import com.example.ui.viewmodel.UiState

@Composable
fun HomeFeedScreen(
    userProfile: UserProfile?,
    resourcesState: UiState<List<ResourceListing>>,
    filterState: FeedFilterState,
    wishlistIds: Set<String>,
    borrowRequests: List<BorrowRequest>,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (ResourceCategory?) -> Unit,
    onConditionSelect: (ResourceCondition?) -> Unit,
    onModeSelect: (ShareMode?) -> Unit,
    onAvailabilitySelect: (AvailabilityStatus?) -> Unit,
    onDepartmentSelect: (String?) -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onResetFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleWishlist: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenCreateListing: () -> Unit,
    onOpenBorrowed: () -> Unit,
    isWideScreen: Boolean
) {
    var showAdvancedFilters by remember { mutableStateOf(false) }

    val departments = remember(resourcesState) {
        (resourcesState as? UiState.Success)?.data
            ?.map { it.department }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }

    val activeBorrowedCount = remember(borrowRequests, userProfile) {
        borrowRequests.count {
            it.borrowerId == userProfile?.uid &&
                it.status in setOf(BorrowStatus.BORROWED.key, BorrowStatus.OVERDUE.key)
        }
    }

    val columns = if (isWideScreen) GridCells.Adaptive(minSize = 280.dp) else GridCells.Fixed(1)

    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(IndigoDeep, IndigoPrimary, TealSecondary))
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Welcome back, ${userProfile?.displayName?.substringBefore(" ") ?: "Scholar"}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "${userProfile?.campusDomain ?: "campus.edu"} • ${userProfile?.department ?: "Campus"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Button(
                                onClick = onOpenCreateListing,
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = IndigoDeep
                                ),
                                modifier = Modifier.testTag("hero_add_listing_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("List Item", fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val totalActive = (resourcesState as? UiState.Success)?.data?.size ?: 0
                            StatMiniPill(
                                label = "Available Now",
                                value = "${(resourcesState as? UiState.Success)?.data?.count { it.availability == AvailabilityStatus.AVAILABLE.key } ?: 0} / $totalActive",
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniPill(
                                label = "Active Borrows",
                                value = "$activeBorrowedCount",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(onClick = onOpenBorrowed)
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search textbooks, calculators, lab gear...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (filterState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_search_input")
                    )
                    IconButton(
                        onClick = { showAdvancedFilters = !showAdvancedFilters },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (showAdvancedFilters) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("toggle_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = if (showAdvancedFilters) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterState.category == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text("All") },
                        modifier = Modifier.testTag("category_chip_all")
                    )
                    ResourceCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = filterState.category == cat,
                            onClick = {
                                onCategorySelect(if (filterState.category == cat) null else cat)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = categoryIcon(cat),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(cat.label) },
                            modifier = Modifier.testTag("category_chip_${cat.key}")
                        )
                    }
                }

                // Sort & Active Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOption.entries.forEach { sort ->
                            FilterChip(
                                selected = filterState.sortBy == sort,
                                onClick = { onSortSelect(sort) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(sort.label) }
                            )
                        }
                    }
                    if (filterState.condition != null || filterState.mode != null ||
                        filterState.availability != null || filterState.department != null ||
                        filterState.category != null || filterState.searchQuery.isNotBlank()
                    ) {
                        TextButton(onClick = onResetFilters) {
                            Text("Reset")
                        }
                    }
                }

                AnimatedVisibility(visible = showAdvancedFilters) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Mode", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ShareMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = filterState.mode == mode,
                                        onClick = { onModeSelect(if (filterState.mode == mode) null else mode) },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }

                            Text("Condition", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ResourceCondition.entries.forEach { cond ->
                                    FilterChip(
                                        selected = filterState.condition == cond,
                                        onClick = { onConditionSelect(if (filterState.condition == cond) null else cond) },
                                        label = { Text(cond.label) }
                                    )
                                }
                            }

                            Text("Availability", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AvailabilityStatus.entries.forEach { avail ->
                                    FilterChip(
                                        selected = filterState.availability == avail,
                                        onClick = { onAvailabilitySelect(if (filterState.availability == avail) null else avail) },
                                        label = { Text(avail.label) }
                                    )
                                }
                            }

                            if (departments.isNotEmpty()) {
                                Text("Department", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    departments.forEach { dept ->
                                        FilterChip(
                                            selected = filterState.department == dept,
                                            onClick = { onDepartmentSelect(if (filterState.department == dept) null else dept) },
                                            label = { Text(dept) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        when (resourcesState) {
            is UiState.Loading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadingSkeletonList(count = 3)
                }
            }
            is UiState.Error -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorStatePanel(message = resourcesState.message, onRetry = onResetFilters)
                }
            }
            is UiState.Success -> {
                val allMatches = resourcesState.data
                if (allMatches.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyStatePanel(
                            icon = Icons.Default.SearchOff,
                            title = "No matching resources",
                            subtitle = "Try clearing filters or be the first in your department to list this item.",
                            actionLabel = "Clear Filters",
                            onAction = onResetFilters
                        )
                    }
                } else {
                    val visibleItems = allMatches.take(filterState.pageSize)
                    items(visibleItems, key = { it.id }) { listing ->
                        ResourceListingCard(
                            item = listing,
                            isSaved = listing.id in wishlistIds,
                            onToggleWishlist = { onToggleWishlist(listing.id) },
                            onClick = { onOpenListing(listing.id) }
                        )
                    }
                    if (allMatches.size > visibleItems.size) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = onLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .testTag("load_more_button")
                            ) {
                                Text("Load More (${allMatches.size - visibleItems.size} remaining)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}
