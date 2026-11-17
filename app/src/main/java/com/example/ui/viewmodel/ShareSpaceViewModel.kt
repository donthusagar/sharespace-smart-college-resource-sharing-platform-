package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ThemePreferences
import com.example.data.model.AppNotification
import com.example.data.model.AvailabilityStatus
import com.example.data.model.BorrowRequest
import com.example.data.model.ModerationReport
import com.example.data.model.ResourceBookingSlot
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceCondition
import com.example.data.model.ResourceListing
import com.example.data.model.Review
import com.example.data.model.ShareMode
import com.example.data.model.UserPrivateContact
import com.example.data.model.UserProfile
import com.example.data.repository.ShareSpaceRepository
import com.example.data.unwrapFirestoreException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SortOption(val label: String) {
    NEWEST("Newest"),
    TOP_RATED("Top Rated")
}

data class FeedFilterState(
    val searchQuery: String = "",
    val category: ResourceCategory? = null,
    val condition: ResourceCondition? = null,
    val mode: ShareMode? = null,
    val availability: AvailabilityStatus? = null,
    val department: String? = null,
    val sortBy: SortOption = SortOption.NEWEST,
    val pageSize: Int = 8
)

class ShareSpaceViewModel(
    private val repository: ShareSpaceRepository,
    private val themePreferences: ThemePreferences,
    private val currentUserId: String,
    private val currentUserEmail: String
) : ViewModel() {

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val _filterState = MutableStateFlow(FeedFilterState())
    val filterState: StateFlow<FeedFilterState> = _filterState.asStateFlow()

    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { e -> emitError("profile", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val privateContact: StateFlow<UserPrivateContact?> = repository.observePrivateContact(currentUserId)
        .catch { e ->
            handleStreamWarning("privateContact", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val allUsers: StateFlow<List<UserProfile>> = repository.observeAllUsers()
        .catch { e ->
            handleStreamWarning("allUsers", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val resourcesState: StateFlow<UiState<List<ResourceListing>>> = repository.observeActiveResources()
        .map<List<ResourceListing>, UiState<List<ResourceListing>>> { UiState.Success(it) }
        .catch { e -> emitError("resources", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val filteredResourcesState: StateFlow<UiState<List<ResourceListing>>> =
        combine(resourcesState, _filterState) { resState, filter ->
            when (resState) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> resState
                is UiState.Success -> {
                    val q = filter.searchQuery.trim().lowercase()
                    val filtered = resState.data.filter { item ->
                        (q.isEmpty() ||
                            item.title.lowercase().contains(q) ||
                            item.description.lowercase().contains(q) ||
                            item.department.lowercase().contains(q) ||
                            item.pickupLocation.lowercase().contains(q) ||
                            item.ownerName.lowercase().contains(q)) &&
                            (filter.category == null || item.category == filter.category.key) &&
                            (filter.condition == null || item.condition == filter.condition.key) &&
                            (filter.mode == null || item.mode == filter.mode.key) &&
                            (filter.availability == null || item.availability == filter.availability.key) &&
                            (filter.department == null || item.department.equals(filter.department, ignoreCase = true))
                    }
                    val sorted = when (filter.sortBy) {
                        SortOption.NEWEST -> filtered
                        SortOption.TOP_RATED -> filtered.sortedWith(
                            compareByDescending<ResourceListing> { it.averageRating }
                                .thenByDescending { it.reviewCount }
                        )
                    }
                    UiState.Success(sorted)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val wishlistIds: StateFlow<Set<String>> = repository.observeWishlistIds(currentUserId)
        .catch { e ->
            handleStreamWarning("wishlist", e)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    val borrowRequests: StateFlow<List<BorrowRequest>> = repository.observeUserBorrowRequests(currentUserId)
        .catch { e ->
            handleStreamWarning("borrowRequests", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val notifications: StateFlow<List<AppNotification>> = repository.observeNotifications(currentUserId)
        .catch { e ->
            handleStreamWarning("notifications", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val adminResources: StateFlow<List<ResourceListing>> = userProfileState
        .flatMapLatest { state ->
            val isAdmin = (state as? UiState.Success)?.data?.role == "admin"
            if (isAdmin) repository.observeAllResourcesAdmin() else flowOf(emptyList())
        }
        .catch { e ->
            handleStreamWarning("adminResources", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val adminReports: StateFlow<List<ModerationReport>> = userProfileState
        .flatMapLatest { state ->
            val isAdmin = (state as? UiState.Success)?.data?.role == "admin"
            if (isAdmin) repository.observeAllReportsAdmin() else flowOf(emptyList())
        }
        .catch { e ->
            handleStreamWarning("adminReports", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun initializeAccountAndDemoData(firebaseUser: FirebaseUser) {
        viewModelScope.launch {
            _initError.value = null
            repository.ensureUserProfileAndSeedIfNeeded(firebaseUser)
                .onFailure { e ->
                    _initError.value = e.localizedMessage ?: "Failed to initialize campus profile."
                }
        }
        viewModelScope.launch {
            borrowRequests.collect { list ->
                if (list.isNotEmpty()) {
                    repository.syncDueRemindersAndOverdue(currentUserId, list, currentUserEmail)
                }
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setDarkMode(enabled)
        }
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query, pageSize = 8) }
    }

    fun updateCategory(category: ResourceCategory?) {
        _filterState.update { it.copy(category = category, pageSize = 8) }
    }

    fun updateCondition(condition: ResourceCondition?) {
        _filterState.update { it.copy(condition = condition, pageSize = 8) }
    }

    fun updateMode(mode: ShareMode?) {
        _filterState.update { it.copy(mode = mode, pageSize = 8) }
    }

    fun updateAvailability(availability: AvailabilityStatus?) {
        _filterState.update { it.copy(availability = availability, pageSize = 8) }
    }

    fun updateDepartment(department: String?) {
        _filterState.update { it.copy(department = department, pageSize = 8) }
    }

    fun updateSort(sortOption: SortOption) {
        _filterState.update { it.copy(sortBy = sortOption) }
    }

    fun resetFilters() {
        _filterState.value = FeedFilterState()
    }

    fun loadMoreListings() {
        _filterState.update { it.copy(pageSize = it.pageSize + 6) }
    }

    fun observeResource(resourceId: String): Flow<ResourceListing?> =
        repository.observeResource(resourceId).catch { e ->
            handleStreamWarning("resource($resourceId)", e)
            emit(null)
        }

    fun observeBookings(resourceId: String): Flow<List<ResourceBookingSlot>> =
        repository.observeResourceBookings(resourceId).catch { e ->
            handleStreamWarning("bookings($resourceId)", e)
            emit(emptyList())
        }

    fun observeReviewsForResource(resourceId: String): Flow<List<Review>> =
        repository.observeReviewsForResource(resourceId).catch { e ->
            handleStreamWarning("reviewsRes($resourceId)", e)
            emit(emptyList())
        }

    fun observeReviewsForUser(userId: String): Flow<List<Review>> =
        repository.observeReviewsForUser(userId).catch { e ->
            handleStreamWarning("reviewsUser($userId)", e)
            emit(emptyList())
        }

    fun observePublicProfile(userId: String): Flow<UserProfile?> =
        repository.observeUserProfile(userId).catch { e ->
            handleStreamWarning("publicUser($userId)", e)
            emit(null)
        }

    fun saveListing(
        existingId: String?,
        title: String,
        description: String,
        category: ResourceCategory,
        condition: ResourceCondition,
        mode: ShareMode,
        availability: AvailabilityStatus,
        maxBorrowDays: Int,
        pickupLocation: String,
        department: String,
        photoUrls: List<String>,
        onSuccess: (String) -> Unit
    ) {
        val owner = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createOrUpdateListing(
                existingId, owner, title, description, category, condition, mode,
                availability, maxBorrowDays, pickupLocation, department, photoUrls
            ).onSuccess { id ->
                _toastMessages.tryEmit(if (existingId == null) "Listing published!" else "Listing updated!")
                onSuccess(id)
            }.onFailure { e ->
                _toastMessages.tryEmit(e.localizedMessage ?: "Failed to save listing.")
            }
            _isBusy.value = false
        }
    }

    fun deleteListing(resourceId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteListing(resourceId)
                .onSuccess {
                    _toastMessages.tryEmit("Listing deleted.")
                    onDeleted()
                }
                .onFailure { e ->
                    _toastMessages.tryEmit(e.localizedMessage ?: "Failed to delete listing.")
                }
            _isBusy.value = false
        }
    }

    fun toggleWishlist(resourceId: String) {
        val currentlySaved = resourceId in wishlistIds.value
        viewModelScope.launch {
            repository.toggleWishlist(currentUserId, resourceId, currentlySaved)
                .onSuccess {
                    _toastMessages.tryEmit(if (currentlySaved) "Removed from Wishlist" else "Saved to Wishlist")
                }
                .onFailure { e ->
                    _toastMessages.tryEmit(e.localizedMessage ?: "Wishlist update failed.")
                }
        }
    }

    fun submitBorrowRequest(
        resource: ResourceListing,
        startDateMillis: Long,
        endDateMillis: Long,
        message: String,
        onSuccess: () -> Unit
    ) {
        val borrower = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createBorrowRequest(resource, borrower, startDateMillis, endDateMillis, message)
                .onSuccess {
                    _toastMessages.tryEmit("Borrow request sent to ${resource.ownerName}!")
                    onSuccess()
                }
                .onFailure { e ->
                    _toastMessages.tryEmit(e.localizedMessage ?: "Could not submit request.")
                }
            _isBusy.value = false
        }
    }

    fun approveRequest(request: BorrowRequest) {
        runAction("Request approved! Notification & email sent.") {
            repository.approveBorrowRequest(request)
        }
    }

    fun rejectRequest(request: BorrowRequest, reason: String) {
        runAction("Request declined.") {
            repository.rejectBorrowRequest(request, reason)
        }
    }

    fun markBorrowed(request: BorrowRequest) {
        runAction("Marked as picked up / borrowed.") {
            repository.markAsBorrowed(request)
        }
    }

    fun confirmReturned(request: BorrowRequest) {
        runAction("Return confirmed! Listing is available again.") {
            repository.confirmReturned(request)
        }
    }

    fun cancelRequest(request: BorrowRequest) {
        runAction("Request cancelled.") {
            repository.cancelBorrowRequest(request)
        }
    }

    fun requestExtension(request: BorrowRequest, extraDays: Int) {
        runAction("Extension request sent to ${request.ownerName}.") {
            repository.requestExtension(request, extraDays)
        }
    }

    fun resolveExtension(request: BorrowRequest, approve: Boolean) {
        runAction(if (approve) "Extension approved!" else "Extension declined.") {
            repository.resolveExtension(request, approve)
        }
    }

    fun submitReview(request: BorrowRequest, rating: Int, comment: String, onSuccess: () -> Unit = {}) {
        val reviewer = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.submitReview(request, reviewer, rating, comment)
                .onSuccess {
                    _toastMessages.tryEmit("Thank you! Review published.")
                    onSuccess()
                }
                .onFailure { e ->
                    _toastMessages.tryEmit(e.localizedMessage ?: "Failed to submit review.")
                }
            _isBusy.value = false
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead(notifications.value)
                .onSuccess { _toastMessages.tryEmit("All notifications marked as read.") }
        }
    }

    fun updateProfile(
        displayName: String,
        department: String,
        year: String,
        collegeEmail: String,
        phone: String,
        emailNotificationsEnabled: Boolean,
        avatarKey: String,
        onSuccess: () -> Unit
    ) {
        val isEdu = ShareSpaceRepository.isCollegeDomainEmail(collegeEmail)
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateOwnProfile(
                uid = currentUserId,
                displayName = displayName,
                department = department,
                year = year,
                collegeEmail = collegeEmail,
                phone = phone,
                campusVerified = isEdu || currentUserEmail.equals(ShareSpaceRepository.ADMIN_EMAIL, true),
                emailNotificationsEnabled = emailNotificationsEnabled,
                avatarKey = avatarKey
            ).onSuccess {
                _toastMessages.tryEmit("Profile updated!")
                onSuccess()
            }.onFailure { e ->
                _toastMessages.tryEmit(e.localizedMessage ?: "Profile update failed.")
            }
            _isBusy.value = false
        }
    }

    fun submitReport(
        targetType: String,
        targetId: String,
        targetName: String,
        reason: String,
        details: String,
        onSuccess: () -> Unit = {}
    ) {
        val reporter = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.submitReport(reporter, targetType, targetId, targetName, reason, details)
                .onSuccess {
                    _toastMessages.tryEmit("Report submitted for campus admin review.")
                    onSuccess()
                }
                .onFailure { e ->
                    _toastMessages.tryEmit(e.localizedMessage ?: "Report submission failed.")
                }
            _isBusy.value = false
        }
    }

    fun adminToggleBan(targetUid: String, ban: Boolean, reason: String) {
        runAction(if (ban) "User banned." else "User unbanned.") {
            repository.adminToggleBanUser(targetUid, ban, reason)
        }
    }

    fun adminSetListingRemoved(resourceId: String, remove: Boolean) {
        runAction(if (remove) "Listing removed." else "Listing restored.") {
            repository.adminSetListingStatus(resourceId, remove)
        }
    }

    fun adminResolveReport(reportId: String, status: String, actionNote: String) {
        runAction("Report marked $status.") {
            repository.adminResolveReport(reportId, status, actionNote)
        }
    }

    private fun runAction(successMessage: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isBusy.value = true
            block()
                .onSuccess { _toastMessages.tryEmit(successMessage) }
                .onFailure { e -> _toastMessages.tryEmit(e.localizedMessage ?: "Operation failed.") }
            _isBusy.value = false
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<T>>.emitError(tag: String, error: Throwable) {
        val firestoreError = unwrapFirestoreException(error)
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $tag", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Failed to observe $tag"))
    }

    private fun handleStreamWarning(tag: String, error: Throwable) {
        val firestoreError = unwrapFirestoreException(error)
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing $tag", firestoreError ?: error)
    }

    private companion object {
        const val TAG = "ShareSpaceVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
