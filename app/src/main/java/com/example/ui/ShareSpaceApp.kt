package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.performSignOut
import com.example.data.ThemePreferences
import com.example.data.model.BorrowStatus
import com.example.data.repository.ShareSpaceRepository
import com.example.ui.screens.AddEditListingScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthLandingScreen
import com.example.ui.screens.BannedAccountScreen
import com.example.ui.screens.BorrowedItemsScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.MyListingsScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OwnProfileScreen
import com.example.ui.screens.PublicProfileScreen
import com.example.ui.screens.RequestsScreen
import com.example.ui.screens.ResourceDetailScreen
import com.example.ui.screens.WishlistScreen
import com.example.ui.theme.ShareSpaceTheme
import com.example.ui.viewmodel.ShareSpaceViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest

private object Routes {
    const val EXPLORE = "explore"
    const val REQUESTS = "requests"
    const val BORROWED = "borrowed"
    const val WISHLIST = "wishlist"
    const val PROFILE = "profile"
    const val DETAIL = "detail/{resourceId}"
    const val ADD_EDIT = "add_edit?resourceId={resourceId}"
    const val MY_LISTINGS = "my_listings"
    const val NOTIFICATIONS = "notifications"
    const val PUBLIC_PROFILE = "public_profile/{userId}"
    const val ADMIN = "admin"

    fun detail(resourceId: String) = "detail/$resourceId"
    fun addEdit(resourceId: String? = null) =
        if (resourceId == null) "add_edit" else "add_edit?resourceId=$resourceId"
    fun publicProfile(userId: String) = "public_profile/$userId"
}

private data class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val primaryTabs = listOf(
    TopTab(Routes.EXPLORE, "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    TopTab(Routes.REQUESTS, "Requests", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    TopTab(Routes.BORROWED, "Borrowed", Icons.AutoMirrored.Filled.AssignmentReturn, Icons.AutoMirrored.Outlined.AssignmentReturn),
    TopTab(Routes.WISHLIST, "Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    TopTab(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun ShareSpaceAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val themePreferences = remember(context) { ThemePreferences(context.applicationContext) }
    val isDarkModePref by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = false)

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    ShareSpaceTheme(darkTheme = isDarkModePref) {
        val user = currentUser
        if (user == null) {
            AuthLandingScreen(onAuthSuccess = { /* AuthStateListener updates automatically */ })
        } else {
            key(user.uid) {
                AuthenticatedShareSpaceRoot(
                    firebaseUser = user,
                    themePreferences = themePreferences
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShareSpaceRoot(
    firebaseUser: FirebaseUser,
    themePreferences: ThemePreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ShareSpaceViewModel = viewModel(
        key = firebaseUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ShareSpaceViewModel(
                    repository = ShareSpaceRepository(db),
                    themePreferences = themePreferences,
                    currentUserId = firebaseUser.uid,
                    currentUserEmail = firebaseUser.email ?: ""
                )
            }
        }
    )

    LaunchedEffect(firebaseUser.uid) {
        viewModel.initializeAccountAndDemoData(firebaseUser)
    }

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val profile = (profileState as? UiState.Success)?.data
    val privateContact by viewModel.privateContact.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val resourcesState by viewModel.resourcesState.collectAsStateWithLifecycle()
    val filteredResourcesState by viewModel.filteredResourcesState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val wishlistIds by viewModel.wishlistIds.collectAsStateWithLifecycle()
    val borrowRequests by viewModel.borrowRequests.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotifCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val adminResources by viewModel.adminResources.collectAsStateWithLifecycle()
    val adminReports by viewModel.adminReports.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    if (profile?.banned == true) {
        BannedAccountScreen(
            banReason = profile.banReason,
            onSignOut = { performSignOut(credentialManager, scope) }
        )
        return
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopTab = currentRoute in primaryTabs.map { it.route }

    val pendingReceivedCount = remember(borrowRequests, firebaseUser.uid) {
        borrowRequests.count { it.ownerId == firebaseUser.uid && it.status == BorrowStatus.PENDING.key }
    }
    val overdueCount = remember(borrowRequests, firebaseUser.uid) {
        borrowRequests.count { it.borrowerId == firebaseUser.uid && it.status == BorrowStatus.OVERDUE.key }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isTopTab) {
                    val tabTitle = primaryTabs.firstOrNull { it.route == currentRoute }?.label ?: "ShareSpace"
                    TopAppBar(
                        title = {
                            Text(
                                text = if (currentRoute == Routes.EXPLORE) "ShareSpace" else tabTitle,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            if (profile?.role == "admin") {
                                IconButton(
                                    onClick = { navController.navigate(Routes.ADMIN) },
                                    modifier = Modifier.testTag("topbar_admin_button")
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin Dashboard")
                                }
                            }
                            IconButton(
                                onClick = { navController.navigate(Routes.NOTIFICATIONS) },
                                modifier = Modifier.testTag("topbar_notifications_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotifCount > 0) {
                                            Badge { Text("$unreadNotifCount") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isTopTab && !isWideScreen) {
                    NavigationBar {
                        primaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.EXPLORE) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val badgeCount = when (tab.route) {
                                        Routes.REQUESTS -> pendingReceivedCount
                                        Routes.BORROWED -> overdueCount
                                        else -> 0
                                    }
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text("$badgeCount") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute == Routes.EXPLORE) {
                    FloatingActionButton(
                        onClick = { navController.navigate(Routes.addEdit()) },
                        modifier = Modifier.testTag("fab_add_listing")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Listing")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopTab && isWideScreen) {
                    NavigationRail {
                        primaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.EXPLORE) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.EXPLORE
                    ) {
                        composable(Routes.EXPLORE) {
                            HomeFeedScreen(
                                userProfile = profile,
                                resourcesState = filteredResourcesState,
                                filterState = filterState,
                                wishlistIds = wishlistIds,
                                borrowRequests = borrowRequests,
                                onSearchChange = viewModel::updateSearchQuery,
                                onCategorySelect = viewModel::updateCategory,
                                onConditionSelect = viewModel::updateCondition,
                                onModeSelect = viewModel::updateMode,
                                onAvailabilitySelect = viewModel::updateAvailability,
                                onDepartmentSelect = viewModel::updateDepartment,
                                onSortSelect = viewModel::updateSort,
                                onResetFilters = viewModel::resetFilters,
                                onLoadMore = viewModel::loadMoreListings,
                                onToggleWishlist = viewModel::toggleWishlist,
                                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                                onOpenCreateListing = { navController.navigate(Routes.addEdit()) },
                                onOpenBorrowed = { navController.navigate(Routes.BORROWED) },
                                isWideScreen = isWideScreen
                            )
                        }

                        composable(Routes.REQUESTS) {
                            RequestsScreen(
                                currentUserId = firebaseUser.uid,
                                allRequests = borrowRequests,
                                isBusy = isBusy,
                                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                                onApprove = viewModel::approveRequest,
                                onReject = viewModel::rejectRequest,
                                onMarkBorrowed = viewModel::markBorrowed,
                                onConfirmReturned = viewModel::confirmReturned,
                                onCancelRequest = viewModel::cancelRequest,
                                onResolveExtension = viewModel::resolveExtension,
                                onSubmitReview = { req, rating, comment ->
                                    viewModel.submitReview(req, rating, comment)
                                }
                            )
                        }

                        composable(Routes.BORROWED) {
                            BorrowedItemsScreen(
                                currentUserId = firebaseUser.uid,
                                allRequests = borrowRequests,
                                isBusy = isBusy,
                                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                                onRequestExtension = viewModel::requestExtension,
                                onConfirmReturned = viewModel::confirmReturned,
                                onSubmitReview = { req, rating, comment ->
                                    viewModel.submitReview(req, rating, comment)
                                }
                            )
                        }

                        composable(Routes.WISHLIST) {
                            val allRes = (resourcesState as? UiState.Success)?.data ?: emptyList()
                            WishlistScreen(
                                allResources = allRes,
                                wishlistIds = wishlistIds,
                                onToggleWishlist = viewModel::toggleWishlist,
                                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                                onExplore = { navController.navigate(Routes.EXPLORE) },
                                isWideScreen = isWideScreen
                            )
                        }

                        composable(Routes.PROFILE) {
                            val ownReviews by viewModel.observeReviewsForUser(firebaseUser.uid)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            OwnProfileScreen(
                                profile = profile,
                                privateContact = privateContact,
                                reviews = ownReviews,
                                isDarkMode = isDarkMode,
                                unreadNotifications = unreadNotifCount,
                                onToggleDarkMode = viewModel::toggleDarkMode,
                                onUpdateProfile = viewModel::updateProfile,
                                onOpenMyListings = { navController.navigate(Routes.MY_LISTINGS) },
                                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                                onOpenAdminDashboard = { navController.navigate(Routes.ADMIN) },
                                onSignOut = { performSignOut(credentialManager, scope) }
                            )
                        }

                        composable(
                            route = Routes.DETAIL,
                            arguments = listOf(navArgument("resourceId") { type = NavType.StringType })
                        ) { entry ->
                            val resourceId = entry.arguments?.getString("resourceId") ?: ""
                            val resource by viewModel.observeResource(resourceId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val bookings by viewModel.observeBookings(resourceId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val reviews by viewModel.observeReviewsForResource(resourceId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            ResourceDetailScreen(
                                resource = resource,
                                bookings = bookings,
                                reviews = reviews,
                                currentUserId = firebaseUser.uid,
                                isSaved = resourceId in wishlistIds,
                                isBusy = isBusy,
                                onBack = { navController.popBackStack() },
                                onToggleWishlist = { viewModel.toggleWishlist(resourceId) },
                                onOpenOwnerProfile = { ownerId ->
                                    if (ownerId == firebaseUser.uid) {
                                        navController.navigate(Routes.PROFILE)
                                    } else {
                                        navController.navigate(Routes.publicProfile(ownerId))
                                    }
                                },
                                onEditListing = { id -> navController.navigate(Routes.addEdit(id)) },
                                onSubmitBorrowRequest = { startMs, endMs, msg, onDone ->
                                    resource?.let {
                                        viewModel.submitBorrowRequest(it, startMs, endMs, msg, onDone)
                                    }
                                },
                                onSubmitReport = { reason, details ->
                                    resource?.let {
                                        viewModel.submitReport("listing", it.id, it.title, reason, details)
                                    }
                                }
                            )
                        }

                        composable(
                            route = Routes.ADD_EDIT,
                            arguments = listOf(
                                navArgument("resourceId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { entry ->
                            val resourceId = entry.arguments?.getString("resourceId")
                            val existing = if (resourceId != null) {
                                (resourcesState as? UiState.Success)?.data?.firstOrNull { it.id == resourceId }
                            } else null

                            AddEditListingScreen(
                                existingListing = existing,
                                defaultDepartment = profile?.department ?: "Computer Science",
                                isBusy = isBusy,
                                onBack = { navController.popBackStack() },
                                onSave = { title, desc, cat, cond, mode, avail, maxDays, loc, dept, photos ->
                                    viewModel.saveListing(
                                        existingId = resourceId,
                                        title = title,
                                        description = desc,
                                        category = cat,
                                        condition = cond,
                                        mode = mode,
                                        availability = avail,
                                        maxBorrowDays = maxDays,
                                        pickupLocation = loc,
                                        department = dept,
                                        photoUrls = photos,
                                        onSuccess = { savedId ->
                                            navController.popBackStack()
                                            if (resourceId == null) {
                                                navController.navigate(Routes.detail(savedId))
                                            }
                                        }
                                    )
                                }
                            )
                        }

                        composable(Routes.MY_LISTINGS) {
                            val myListings = ((resourcesState as? UiState.Success)?.data ?: emptyList())
                                .filter { it.ownerId == firebaseUser.uid }
                            MyListingsScreen(
                                myListings = myListings,
                                onBack = { navController.popBackStack() },
                                onCreateListing = { navController.navigate(Routes.addEdit()) },
                                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                                onEditListing = { id -> navController.navigate(Routes.addEdit(id)) },
                                onDeleteListing = { id -> viewModel.deleteListing(id) }
                            )
                        }

                        composable(Routes.NOTIFICATIONS) {
                            NotificationsScreen(
                                notifications = notifications,
                                onBack = { navController.popBackStack() },
                                onMarkRead = viewModel::markNotificationRead,
                                onMarkAllRead = viewModel::markAllNotificationsRead
                            )
                        }

                        composable(
                            route = Routes.PUBLIC_PROFILE,
                            arguments = listOf(navArgument("userId") { type = NavType.StringType })
                        ) { entry ->
                            val targetUid = entry.arguments?.getString("userId") ?: ""
                            val targetProfile by viewModel.observePublicProfile(targetUid)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val targetReviews by viewModel.observeReviewsForUser(targetUid)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val targetListings = ((resourcesState as? UiState.Success)?.data ?: emptyList())
                                .filter { it.ownerId == targetUid }

                            PublicProfileScreen(
                                profile = targetProfile,
                                listings = targetListings,
                                reviews = targetReviews,
                                onBack = { navController.popBackStack() },
                                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                                onReportUser = { reason, details ->
                                    targetProfile?.let {
                                        viewModel.submitReport("user", it.uid, it.displayName, reason, details)
                                    }
                                }
                            )
                        }

                        composable(Routes.ADMIN) {
                            AdminDashboardScreen(
                                users = allUsers,
                                listings = adminResources,
                                reports = adminReports,
                                currentUserId = firebaseUser.uid,
                                isBusy = isBusy,
                                onBack = { navController.popBackStack() },
                                onToggleBanUser = viewModel::adminToggleBan,
                                onSetListingRemoved = viewModel::adminSetListingRemoved,
                                onResolveReport = viewModel::adminResolveReport
                            )
                        }
                    }
                }
            }
        }
    }
}
