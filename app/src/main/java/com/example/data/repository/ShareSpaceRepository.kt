package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AppNotification
import com.example.data.model.AvailabilityStatus
import com.example.data.model.BorrowRequest
import com.example.data.model.BorrowStatus
import com.example.data.model.ModerationReport
import com.example.data.model.ResourceBookingSlot
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceCondition
import com.example.data.model.ResourceListing
import com.example.data.model.Review
import com.example.data.model.ShareMode
import com.example.data.model.UserPrivateContact
import com.example.data.model.UserProfile
import com.example.data.model.WishlistItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ShareSpaceRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val ADMIN_EMAIL = "donthusagar1@gmail.com"
        private const val DAY_MS = 86_400_000L

        fun isCollegeDomainEmail(email: String): Boolean {
            val lower = email.trim().lowercase()
            if (!lower.contains("@")) return false
            val domain = lower.substringAfter("@")
            return domain.endsWith(".edu") ||
                domain.contains(".edu.") ||
                domain.contains(".ac.") ||
                domain == "campus.edu"
        }

        fun extractCampusDomain(email: String): String {
            val lower = email.trim().lowercase()
            val domain = lower.substringAfter("@", "campus.edu")
            return if (isCollegeDomainEmail(lower)) domain else "campus.edu"
        }
    }

    // --- USER PROFILE & PRIVATE CONTACT ---

    fun observeUserProfile(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                logFlowError(error, OperationType.GET, "users/$uid")
                throw error
            }

    fun observeAllUsers(): Flow<List<UserProfile>> =
        db.collection("users")
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "users")
                throw error
            }

    fun observePrivateContact(uid: String): Flow<UserPrivateContact?> =
        db.collection("users").document(uid).collection("private").document("contact")
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserPrivateContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                logFlowError(error, OperationType.GET, "users/$uid/private/contact")
                throw error
            }

    suspend fun ensureUserProfileAndSeedIfNeeded(user: FirebaseUser): Result<Unit> = runCatching {
        val uid = user.uid
        val email = user.email ?: "$uid@campus.edu"
        val isBootstrapAdmin = email.equals(ADMIN_EMAIL, ignoreCase = true)
        val userRef = db.collection("users").document(uid)
        val existing = userRef.get().await()

        if (!existing.exists()) {
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
            val campusVerified = isCollegeDomainEmail(email) || isBootstrapAdmin
            val role = if (isBootstrapAdmin) "admin" else "student"

            val profileMap = mapOf(
                "uid" to uid,
                "displayName" to displayName.take(80),
                "department" to if (isBootstrapAdmin) "Computer Science" else "Engineering",
                "year" to if (isBootstrapAdmin) "Senior" else "Junior",
                "campusDomain" to extractCampusDomain(email),
                "avatarKey" to (user.photoUrl?.toString() ?: "avatar_1"),
                "role" to role,
                "banned" to false,
                "banReason" to "",
                "campusVerified" to campusVerified,
                "averageRating" to 5.0,
                "reviewCount" to 1L,
                "itemsListedCount" to 0L,
                "itemsBorrowedCount" to 0L,
                "isPublic" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.set(profileMap).await()
        }

        val contactRef = userRef.collection("private").document("contact")
        val existingContact = contactRef.get().await()
        if (!existingContact.exists()) {
            val contactMap = mapOf(
                "uid" to uid,
                "email" to email,
                "collegeEmail" to if (isCollegeDomainEmail(email)) email else "${email.substringBefore("@")}@campus.edu",
                "phone" to "(650) 555-0142",
                "emailNotificationsEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            contactRef.set(contactMap).await()
        }

        val seedRef = db.collection("app_config").document("seed_v1")
        val seedSnap = seedRef.get().await()
        if (!seedSnap.exists()) {
            seedDemoCampusCatalog(
                currentUid = uid,
                currentName = user.displayName?.takeIf { it.isNotBlank() } ?: "You",
                currentEmail = email
            )
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${user.uid}")
    }

    suspend fun updateOwnProfile(
        uid: String,
        displayName: String,
        department: String,
        year: String,
        collegeEmail: String,
        phone: String,
        campusVerified: Boolean,
        emailNotificationsEnabled: Boolean,
        avatarKey: String
    ): Result<Unit> = runCatching {
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        val contactRef = userRef.collection("private").document("contact")
        val domain = extractCampusDomain(collegeEmail)

        batch.update(
            userRef,
            mapOf(
                "displayName" to displayName.trim().take(80),
                "department" to department.trim().take(80),
                "year" to year.trim().take(40),
                "campusDomain" to domain,
                "campusVerified" to campusVerified,
                "avatarKey" to avatarKey,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            contactRef,
            mapOf(
                "collegeEmail" to collegeEmail.trim().take(160),
                "phone" to phone.trim().take(40),
                "emailNotificationsEnabled" to emailNotificationsEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
    }

    // --- RESOURCES ---

    fun observeActiveResources(): Flow<List<ResourceListing>> =
        db.collection("resources")
            .whereEqualTo("status", "active")
            .snapshots()
            .map { snap ->
                snap.toObjects(ResourceListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "resources")
                throw error
            }

    fun observeAllResourcesAdmin(): Flow<List<ResourceListing>> =
        db.collection("resources")
            .snapshots()
            .map { snap ->
                snap.toObjects(ResourceListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "resources(admin)")
                throw error
            }

    fun observeResource(resourceId: String): Flow<ResourceListing?> =
        db.collection("resources").document(resourceId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(ResourceListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                logFlowError(error, OperationType.GET, "resources/$resourceId")
                throw error
            }

    suspend fun createOrUpdateListing(
        existingId: String?,
        owner: UserProfile,
        title: String,
        description: String,
        category: ResourceCategory,
        condition: ResourceCondition,
        mode: ShareMode,
        availability: AvailabilityStatus,
        maxBorrowDays: Int,
        pickupLocation: String,
        department: String,
        photoUrls: List<String>
    ): Result<String> = runCatching {
        val cleanPhotos = photoUrls.take(5).ifEmpty { listOf(defaultPhotoForCategory(category)) }
        if (existingId.isNullOrBlank()) {
            val newId = "res_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val docRef = db.collection("resources").document(newId)
            val payload = mapOf(
                "ownerId" to owner.uid,
                "ownerName" to owner.displayName,
                "ownerDepartment" to owner.department,
                "ownerRating" to owner.averageRating,
                "title" to title.trim().take(120),
                "description" to description.trim().take(1500),
                "category" to category.key,
                "condition" to condition.key,
                "mode" to mode.key,
                "availability" to availability.key,
                "maxBorrowDays" to maxBorrowDays.coerceIn(1, 180).toLong(),
                "pickupLocation" to pickupLocation.trim().take(160),
                "department" to department.trim().take(80),
                "photoUrls" to cleanPhotos,
                "averageRating" to 0.0,
                "reviewCount" to 0L,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            batch.set(docRef, payload)
            batch.update(
                db.collection("users").document(owner.uid),
                mapOf(
                    "itemsListedCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            newId
        } else {
            val docRef = db.collection("resources").document(existingId)
            val updatePayload = mapOf(
                "ownerName" to owner.displayName,
                "ownerDepartment" to owner.department,
                "title" to title.trim().take(120),
                "description" to description.trim().take(1500),
                "category" to category.key,
                "condition" to condition.key,
                "mode" to mode.key,
                "availability" to availability.key,
                "maxBorrowDays" to maxBorrowDays.coerceIn(1, 180).toLong(),
                "pickupLocation" to pickupLocation.trim().take(160),
                "department" to department.trim().take(80),
                "photoUrls" to cleanPhotos,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updatePayload).await()
            existingId
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "resources")
    }

    suspend fun deleteListing(resourceId: String): Result<Unit> = runCatching {
        db.collection("resources").document(resourceId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "resources/$resourceId")
    }

    // --- WISHLIST ---

    fun observeWishlistIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("wishlist")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "users/$uid/wishlist")
                throw error
            }

    suspend fun toggleWishlist(uid: String, resourceId: String, currentlySaved: Boolean): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(uid).collection("wishlist").document(resourceId)
        if (currentlySaved) {
            docRef.delete().await()
        } else {
            val payload = mapOf(
                "userId" to uid,
                "resourceId" to resourceId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/wishlist/$resourceId")
    }

    // --- BOOKINGS & BORROW REQUESTS ---

    fun observeResourceBookings(resourceId: String): Flow<List<ResourceBookingSlot>> =
        db.collection("resources").document(resourceId).collection("bookings")
            .whereEqualTo("resourceId", resourceId)
            .snapshots()
            .map { snap ->
                snap.toObjects(ResourceBookingSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "resources/$resourceId/bookings")
                throw error
            }

    fun observeUserBorrowRequests(uid: String): Flow<List<BorrowRequest>> =
        db.collection("borrow_requests")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(BorrowRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "borrow_requests")
                throw error
            }

    suspend fun createBorrowRequest(
        resource: ResourceListing,
        borrower: UserProfile,
        startDateMillis: Long,
        endDateMillis: Long,
        message: String
    ): Result<String> = runCatching {
        require(resource.ownerId != borrower.uid) { "You cannot request your own listing." }
        require(endDateMillis >= startDateMillis) { "End date must be on or after start date." }
        val durationDays = ((endDateMillis - startDateMillis) / DAY_MS).toInt() + 1
        require(durationDays <= resource.maxBorrowDays) {
            "Requested duration ($durationDays days) exceeds max borrow limit of ${resource.maxBorrowDays} days."
        }

        // Check overlapping active bookings
        val existingBookings = db.collection("resources").document(resource.id)
            .collection("bookings")
            .whereEqualTo("resourceId", resource.id)
            .get()
            .await()
            .toObjects(ResourceBookingSlot::class.java)

        val blockingStatuses = setOf(
            BorrowStatus.APPROVED.key,
            BorrowStatus.BORROWED.key,
            BorrowStatus.OVERDUE.key
        )
        val hasOverlap = existingBookings.any { slot ->
            slot.status in blockingStatuses &&
                startDateMillis <= slot.endDateMillis &&
                endDateMillis >= slot.startDateMillis
        }
        require(!hasOverlap) {
            "Selected dates overlap with an existing approved or active booking."
        }

        val reqId = "req_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val reqRef = db.collection("borrow_requests").document(reqId)
        val bookingRef = db.collection("resources").document(resource.id).collection("bookings").document(reqId)
        val notifRef = db.collection("notifications").document("notif_${UUID.randomUUID().toString().replace("-", "").take(14)}")

        val requestPayload = mapOf(
            "resourceId" to resource.id,
            "resourceTitle" to resource.title,
            "resourceCategory" to resource.category,
            "resourcePhoto" to (resource.photoUrls.firstOrNull() ?: defaultPhotoForCategory(ResourceCategory.fromKey(resource.category))),
            "resourceMode" to resource.mode,
            "ownerId" to resource.ownerId,
            "ownerName" to resource.ownerName,
            "borrowerId" to borrower.uid,
            "borrowerName" to borrower.displayName,
            "borrowerDepartment" to borrower.department,
            "participantIds" to listOf(resource.ownerId, borrower.uid),
            "startDateMillis" to startDateMillis,
            "endDateMillis" to endDateMillis,
            "dueDateMillis" to endDateMillis,
            "message" to message.trim().take(500),
            "status" to BorrowStatus.PENDING.key,
            "rejectionReason" to "",
            "extensionRequested" to false,
            "requestedExtensionDays" to 0L,
            "extensionStatus" to "none",
            "ownerReviewed" to false,
            "borrowerReviewed" to false,
            "reminderDayBeforeSent" to false,
            "reminderDueDaySent" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val bookingPayload = mapOf(
            "resourceId" to resource.id,
            "ownerId" to resource.ownerId,
            "borrowerId" to borrower.uid,
            "startDateMillis" to startDateMillis,
            "endDateMillis" to endDateMillis,
            "status" to BorrowStatus.PENDING.key,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val notifPayload = buildNotificationPayload(
            userId = resource.ownerId,
            title = "New Borrow Request",
            body = "${borrower.displayName} requested \"${resource.title}\". Review dates and respond.",
            type = "request_received",
            relatedId = reqId,
            emailRecipient = "${resource.ownerName.lowercase().replace(" ", ".")}@campus.edu"
        )

        val batch = db.batch()
        batch.set(reqRef, requestPayload)
        batch.set(bookingRef, bookingPayload)
        batch.set(notifRef, notifPayload)
        batch.commit().await()
        reqId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "borrow_requests")
    }

    suspend fun approveBorrowRequest(request: BorrowRequest): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                "status" to BorrowStatus.APPROVED.key,
                "rejectionReason" to "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("resources").document(request.resourceId).collection("bookings").document(request.id),
            mapOf(
                "status" to BorrowStatus.APPROVED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val notifRef = db.collection("notifications").document("notif_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        batch.set(
            notifRef,
            buildNotificationPayload(
                userId = request.borrowerId,
                title = "Request Approved",
                body = "${request.ownerName} approved your request for \"${request.resourceTitle}\".",
                type = "request_approved",
                relatedId = request.id,
                emailRecipient = "${request.borrowerName.lowercase().replace(" ", ".")}@campus.edu"
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "borrow_requests/${request.id}")
    }

    suspend fun rejectBorrowRequest(request: BorrowRequest, reason: String): Result<Unit> = runCatching {
        val cleanReason = reason.trim().ifBlank { "Unavailable for the selected dates." }.take(300)
        val batch = db.batch()
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                "status" to BorrowStatus.REJECTED.key,
                "rejectionReason" to cleanReason,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("resources").document(request.resourceId).collection("bookings").document(request.id),
            mapOf(
                "status" to BorrowStatus.REJECTED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val notifRef = db.collection("notifications").document("notif_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        batch.set(
            notifRef,
            buildNotificationPayload(
                userId = request.borrowerId,
                title = "Request Declined",
                body = "${request.ownerName} declined \"${request.resourceTitle}\": $cleanReason",
                type = "request_rejected",
                relatedId = request.id,
                emailRecipient = "${request.borrowerName.lowercase().replace(" ", ".")}@campus.edu"
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "borrow_requests/${request.id}")
    }

    suspend fun markAsBorrowed(request: BorrowRequest): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                "status" to BorrowStatus.BORROWED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("resources").document(request.resourceId).collection("bookings").document(request.id),
            mapOf(
                "status" to BorrowStatus.BORROWED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("resources").document(request.resourceId),
            mapOf(
                "availability" to AvailabilityStatus.BORROWED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("users").document(request.borrowerId),
            mapOf(
                "itemsBorrowedCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "borrow_requests/${request.id}")
    }

    suspend fun confirmReturned(request: BorrowRequest): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                "status" to BorrowStatus.RETURNED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("resources").document(request.resourceId).collection("bookings").document(request.id),
            mapOf(
                "status" to BorrowStatus.RETURNED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("resources").document(request.resourceId),
            mapOf(
                "availability" to AvailabilityStatus.AVAILABLE.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val notifRef = db.collection("notifications").document("notif_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        batch.set(
            notifRef,
            buildNotificationPayload(
                userId = request.borrowerId,
                title = "Return Confirmed",
                body = "${request.ownerName} confirmed return of \"${request.resourceTitle}\". Leave a review!",
                type = "returned",
                relatedId = request.id,
                emailRecipient = "${request.borrowerName.lowercase().replace(" ", ".")}@campus.edu"
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "borrow_requests/${request.id}")
    }

    suspend fun cancelBorrowRequest(request: BorrowRequest): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                "status" to BorrowStatus.CANCELLED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("resources").document(request.resourceId).collection("bookings").document(request.id),
            mapOf(
                "status" to BorrowStatus.CANCELLED.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "borrow_requests/${request.id}")
    }

    suspend fun requestExtension(request: BorrowRequest, extraDays: Int): Result<Unit> = runCatching {
        val days = extraDays.coerceIn(1, 30).toLong()
        val batch = db.batch()
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                "extensionRequested" to true,
                "requestedExtensionDays" to days,
                "extensionStatus" to "pending",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val notifRef = db.collection("notifications").document("notif_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        batch.set(
            notifRef,
            buildNotificationPayload(
                userId = request.ownerId,
                title = "Extension Requested",
                body = "${request.borrowerName} requested +$days day(s) for \"${request.resourceTitle}\".",
                type = "extension_request",
                relatedId = request.id,
                emailRecipient = "${request.ownerName.lowercase().replace(" ", ".")}@campus.edu"
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "borrow_requests/${request.id}")
    }

    suspend fun resolveExtension(request: BorrowRequest, approve: Boolean): Result<Unit> = runCatching {
        val batch = db.batch()
        val newDueMillis = if (approve) {
            request.dueDateMillis + (request.requestedExtensionDays * DAY_MS)
        } else {
            request.dueDateMillis
        }
        val newStatus = if (approve && request.status == BorrowStatus.OVERDUE.key && newDueMillis > System.currentTimeMillis()) {
            BorrowStatus.BORROWED.key
        } else {
            request.status
        }
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                "extensionStatus" to if (approve) "approved" else "rejected",
                "dueDateMillis" to newDueMillis,
                "endDateMillis" to newDueMillis,
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (approve) {
            batch.update(
                db.collection("resources").document(request.resourceId).collection("bookings").document(request.id),
                mapOf(
                    "endDateMillis" to newDueMillis,
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        val notifRef = db.collection("notifications").document("notif_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        batch.set(
            notifRef,
            buildNotificationPayload(
                userId = request.borrowerId,
                title = if (approve) "Extension Approved" else "Extension Declined",
                body = if (approve) {
                    "${request.ownerName} extended \"${request.resourceTitle}\" by ${request.requestedExtensionDays} day(s)."
                } else {
                    "${request.ownerName} declined the extension for \"${request.resourceTitle}\"."
                },
                type = "extension_resolved",
                relatedId = request.id,
                emailRecipient = "${request.borrowerName.lowercase().replace(" ", ".")}@campus.edu"
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "borrow_requests/${request.id}")
    }

    suspend fun syncDueRemindersAndOverdue(uid: String, requests: List<BorrowRequest>, userEmail: String) {
        val now = System.currentTimeMillis()
        for (req in requests) {
            if (req.status == BorrowStatus.BORROWED.key && req.dueDateMillis < now) {
                try {
                    val batch = db.batch()
                    batch.update(
                        db.collection("borrow_requests").document(req.id),
                        mapOf(
                            "status" to BorrowStatus.OVERDUE.key,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    if (req.borrowerId == uid) {
                        val notifRef = db.collection("notifications").document("notif_od_${req.id}")
                        batch.set(
                            notifRef,
                            buildNotificationPayload(
                                userId = uid,
                                title = "Item Overdue",
                                body = "\"${req.resourceTitle}\" is past its due date. Please return it or request an extension.",
                                type = "overdue",
                                relatedId = req.id,
                                emailRecipient = userEmail
                            )
                        )
                    }
                    batch.commit().await()
                } catch (_: Exception) {
                }
            } else if (req.status == BorrowStatus.BORROWED.key && req.borrowerId == uid) {
                val remainingMs = req.dueDateMillis - now
                if (remainingMs in 1..DAY_MS && !req.reminderDayBeforeSent) {
                    try {
                        val batch = db.batch()
                        batch.update(
                            db.collection("borrow_requests").document(req.id),
                            mapOf(
                                "reminderDayBeforeSent" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        val notifRef = db.collection("notifications").document("notif_1d_${req.id}")
                        batch.set(
                            notifRef,
                            buildNotificationPayload(
                                userId = uid,
                                title = "Due Tomorrow Reminder",
                                body = "\"${req.resourceTitle}\" is due back tomorrow. Coordinate pickup with ${req.ownerName}.",
                                type = "due_tomorrow",
                                relatedId = req.id,
                                emailRecipient = userEmail
                            )
                        )
                        batch.commit().await()
                    } catch (_: Exception) {
                    }
                } else if (remainingMs in 0..(DAY_MS / 3) && !req.reminderDueDaySent) {
                    try {
                        val batch = db.batch()
                        batch.update(
                            db.collection("borrow_requests").document(req.id),
                            mapOf(
                                "reminderDueDaySent" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        val notifRef = db.collection("notifications").document("notif_0d_${req.id}")
                        batch.set(
                            notifRef,
                            buildNotificationPayload(
                                userId = uid,
                                title = "Due Today Reminder",
                                body = "\"${req.resourceTitle}\" is due back today.",
                                type = "due_today",
                                relatedId = req.id,
                                emailRecipient = userEmail
                            )
                        )
                        batch.commit().await()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // --- REVIEWS ---

    fun observeReviewsForResource(resourceId: String): Flow<List<Review>> =
        db.collection("reviews")
            .whereEqualTo("resourceId", resourceId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Review::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "reviews(resource=$resourceId)")
                throw error
            }

    fun observeReviewsForUser(userId: String): Flow<List<Review>> =
        db.collection("reviews")
            .whereEqualTo("revieweeId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Review::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "reviews(user=$userId)")
                throw error
            }

    suspend fun submitReview(
        request: BorrowRequest,
        reviewer: UserProfile,
        rating: Int,
        comment: String
    ): Result<Unit> = runCatching {
        val isOwnerReviewing = reviewer.uid == request.ownerId
        val revieweeId = if (isOwnerReviewing) request.borrowerId else request.ownerId
        val revieweeName = if (isOwnerReviewing) request.borrowerName else request.ownerName
        val direction = if (isOwnerReviewing) "owner_to_borrower" else "borrower_to_owner"
        val reviewId = "${request.id}_${reviewer.uid}"

        val reviewPayload = mapOf(
            "transactionId" to request.id,
            "resourceId" to request.resourceId,
            "resourceTitle" to request.resourceTitle,
            "reviewerId" to reviewer.uid,
            "reviewerName" to reviewer.displayName,
            "revieweeId" to revieweeId,
            "revieweeName" to revieweeName,
            "direction" to direction,
            "rating" to rating.coerceIn(1, 5).toLong(),
            "comment" to comment.trim().take(600),
            "createdAt" to FieldValue.serverTimestamp()
        )

        // Fetch existing reviews for reviewee & resource to compute new averages
        val userReviews = db.collection("reviews").whereEqualTo("revieweeId", revieweeId).get().await()
            .toObjects(Review::class.java)
        val newUserCount = userReviews.size + 1
        val newUserAvg = (userReviews.sumOf { it.rating } + rating) / newUserCount.toDouble()

        val batch = db.batch()
        batch.set(db.collection("reviews").document(reviewId), reviewPayload)
        batch.update(
            db.collection("borrow_requests").document(request.id),
            mapOf(
                (if (isOwnerReviewing) "ownerReviewed" else "borrowerReviewed") to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("users").document(revieweeId),
            mapOf(
                "averageRating" to ((newUserAvg * 10.0).toInt() / 10.0),
                "reviewCount" to newUserCount.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (!isOwnerReviewing) {
            val resReviews = db.collection("reviews")
                .whereEqualTo("resourceId", request.resourceId)
                .whereEqualTo("direction", "borrower_to_owner")
                .get().await().toObjects(Review::class.java)
            val newResCount = resReviews.size + 1
            val newResAvg = (resReviews.sumOf { it.rating } + rating) / newResCount.toDouble()
            batch.update(
                db.collection("resources").document(request.resourceId),
                mapOf(
                    "averageRating" to ((newResAvg * 10.0).toInt() / 10.0),
                    "reviewCount" to newResCount.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val notifRef = db.collection("notifications").document("notif_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        batch.set(
            notifRef,
            buildNotificationPayload(
                userId = revieweeId,
                title = "New $rating★ Review Received",
                body = "${reviewer.displayName} left a review on \"${request.resourceTitle}\".",
                type = "review_received",
                relatedId = request.id,
                emailRecipient = "${revieweeName.lowercase().replace(" ", ".")}@campus.edu"
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "reviews")
    }

    // --- NOTIFICATIONS ---

    fun observeNotifications(uid: String): Flow<List<AppNotification>> =
        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "notifications")
                throw error
            }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
        db.collection("notifications").document(notificationId)
            .update(mapOf("isRead" to true))
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "notifications/$notificationId")
    }

    suspend fun markAllNotificationsRead(notifications: List<AppNotification>): Result<Unit> = runCatching {
        val unread = notifications.filter { !it.isRead }
        if (unread.isEmpty()) return@runCatching
        val batch = db.batch()
        unread.forEach { n ->
            batch.update(db.collection("notifications").document(n.id), mapOf("isRead" to true))
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "notifications")
    }

    // --- REPORTS & ADMIN MODERATION ---

    suspend fun submitReport(
        reporter: UserProfile,
        targetType: String,
        targetId: String,
        targetName: String,
        reason: String,
        details: String
    ): Result<Unit> = runCatching {
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val payload = mapOf(
            "reporterId" to reporter.uid,
            "reporterName" to reporter.displayName,
            "targetType" to targetType,
            "targetId" to targetId,
            "targetName" to targetName.take(120),
            "reason" to reason.trim().take(120),
            "details" to details.trim().take(600),
            "status" to "open",
            "adminAction" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("reports").document(reportId).set(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "reports")
    }

    fun observeAllReportsAdmin(): Flow<List<ModerationReport>> =
        db.collection("reports")
            .snapshots()
            .map { snap ->
                snap.toObjects(ModerationReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, "reports")
                throw error
            }

    suspend fun adminToggleBanUser(targetUid: String, ban: Boolean, reason: String): Result<Unit> = runCatching {
        db.collection("users").document(targetUid).update(
            mapOf(
                "banned" to ban,
                "banReason" to if (ban) reason.ifBlank { "Violated campus sharing guidelines." } else "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$targetUid")
    }

    suspend fun adminSetListingStatus(resourceId: String, remove: Boolean): Result<Unit> = runCatching {
        db.collection("resources").document(resourceId).update(
            mapOf(
                "status" to if (remove) "removed" else "active",
                "availability" to if (remove) AvailabilityStatus.UNAVAILABLE.key else AvailabilityStatus.AVAILABLE.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "resources/$resourceId")
    }

    suspend fun adminResolveReport(reportId: String, status: String, actionNote: String): Result<Unit> = runCatching {
        db.collection("reports").document(reportId).update(
            mapOf(
                "status" to status,
                "adminAction" to actionNote.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId")
    }

    // --- HELPERS & DEMO SEEDING ---

    private fun buildNotificationPayload(
        userId: String,
        title: String,
        body: String,
        type: String,
        relatedId: String,
        emailRecipient: String
    ): Map<String, Any> = mapOf(
        "userId" to userId,
        "title" to title.take(120),
        "body" to body.take(400),
        "type" to type,
        "relatedId" to relatedId.take(128),
        "isRead" to false,
        "emailDispatched" to true,
        "emailRecipient" to emailRecipient.take(160),
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun defaultPhotoForCategory(category: ResourceCategory): String = when (category) {
        ResourceCategory.TEXTBOOKS, ResourceCategory.NOTES -> "res://img_textbooks_demo"
        ResourceCategory.CALCULATORS -> "res://img_calculator_demo"
        ResourceCategory.LAB_EQUIPMENT, ResourceCategory.OTHER -> "res://img_lab_kit_demo"
    }

    private fun logFlowError(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: error
        handleFirestoreError(exceptionToLog, op, path)
    }

    private suspend fun seedDemoCampusCatalog(
        currentUid: String,
        currentName: String,
        currentEmail: String
    ) {
        val now = System.currentTimeMillis()
        // Batch 1: Demo Users + 12 Demo Listings + 1 Starter Listing for Current User
        val batch1 = db.batch()

        val demoUsers = listOf(
            Triple("seed_maya", "Maya Chen", "Computer Science"),
            Triple("seed_liam", "Liam Patel", "Electrical Engineering"),
            Triple("seed_sofia", "Sofia Rodriguez", "Chemistry"),
            Triple("seed_noah", "Noah Kim", "Mathematics")
        )
        demoUsers.forEachIndexed { index, (uId, name, dept) ->
            val userMap = mapOf(
                "uid" to uId,
                "displayName" to name,
                "department" to dept,
                "year" to listOf("Senior", "Junior", "Grad", "Sophomore")[index],
                "campusDomain" to "campus.edu",
                "avatarKey" to "avatar_${index + 2}",
                "role" to "student",
                "banned" to false,
                "banReason" to "",
                "campusVerified" to true,
                "averageRating" to listOf(4.9, 4.7, 4.8, 4.6)[index],
                "reviewCount" to listOf(14L, 9L, 11L, 6L)[index],
                "itemsListedCount" to 3L,
                "itemsBorrowedCount" to listOf(5L, 4L, 6L, 3L)[index],
                "isPublic" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch1.set(db.collection("users").document(uId), userMap)
        }

        data class DemoRes(
            val id: String,
            val ownerId: String,
            val ownerName: String,
            val ownerDept: String,
            val ownerRating: Double,
            val title: String,
            val desc: String,
            val cat: ResourceCategory,
            val cond: ResourceCondition,
            val mode: ShareMode,
            val avail: AvailabilityStatus,
            val maxDays: Long,
            val loc: String,
            val dept: String,
            val photo: String,
            val avg: Double,
            val reviews: Long
        )

        val listings = listOf(
            DemoRes(
                "seed_res_01", "seed_maya", "Maya Chen", "Computer Science", 4.9,
                "Introduction to Algorithms (CLRS 4th Ed)",
                "Hardcover 4th edition in crisp condition with zero highlights. Ideal for CS161 & interview prep.",
                ResourceCategory.TEXTBOOKS, ResourceCondition.LIKE_NEW, ShareMode.LEND, AvailabilityStatus.AVAILABLE,
                21L, "Gates CS Atrium", "Computer Science", "res://img_textbooks_demo", 4.9, 6L
            ),
            DemoRes(
                "seed_res_02", "seed_liam", "Liam Patel", "Electrical Engineering", 4.7,
                "TI-Nspire CX II CAS Graphing Calculator",
                "Includes USB charging cable and protective slide cover. Charged and exam-mode ready.",
                ResourceCategory.CALCULATORS, ResourceCondition.LIKE_NEW, ShareMode.LEND, AvailabilityStatus.BORROWED,
                14L, "Packard EE Lobby", "Electrical Engineering", "res://img_calculator_demo", 4.8, 5L
            ),
            DemoRes(
                "seed_res_03", "seed_sofia", "Sofia Rodriguez", "Chemistry", 4.8,
                "Organic Chemistry Molecular Model Kit (240 pcs)",
                "Complete 240-piece ball-and-stick stereochemistry kit with orbital pieces and wrench.",
                ResourceCategory.LAB_EQUIPMENT, ResourceCondition.GOOD, ShareMode.LEND, AvailabilityStatus.AVAILABLE,
                14L, "Mudd Chemistry Lounge", "Chemistry", "res://img_lab_kit_demo", 4.9, 4L
            ),
            DemoRes(
                "seed_res_04", "seed_noah", "Noah Kim", "Mathematics", 4.6,
                "Linear Algebra Done Right — Bound Study Notes",
                "Color-coded LaTeX printed lecture summaries, proof templates, and practice midterms for MATH 113.",
                ResourceCategory.NOTES, ResourceCondition.NEW, ShareMode.FREE, AvailabilityStatus.AVAILABLE,
                30L, "Main Quad Library", "Mathematics", "res://img_textbooks_demo", 4.7, 3L
            ),
            DemoRes(
                "seed_res_05", "seed_maya", "Maya Chen", "Computer Science", 4.9,
                "Operating Systems: Three Easy Pieces (Print)",
                "Clean spiral-bound copy with annotated chapter index. Happy to exchange for Systems/Networking notes.",
                ResourceCategory.TEXTBOOKS, ResourceCondition.GOOD, ShareMode.EXCHANGE, AvailabilityStatus.AVAILABLE,
                28L, "Huang Engineering Cafe", "Computer Science", "res://img_textbooks_demo", 4.8, 4L
            ),
            DemoRes(
                "seed_res_06", "seed_liam", "Liam Patel", "Electrical Engineering", 4.7,
                "Fluke Digital Multimeter + Breadboard Kit",
                "Includes jumper wires, 830-tie breadboard, resistor pack, and alligator clips for EE101 labs.",
                ResourceCategory.LAB_EQUIPMENT, ResourceCondition.GOOD, ShareMode.LEND, AvailabilityStatus.AVAILABLE,
                10L, "LabMaker Space B12", "Electrical Engineering", "res://img_lab_kit_demo", 5.0, 3L
            ),
            DemoRes(
                "seed_res_07", "seed_sofia", "Sofia Rodriguez", "Chemistry", 4.8,
                "UV-Rated Lab Safety Goggles + Cotton Coat (M)",
                "Freshly laundered 100% cotton lab coat (size M) and anti-fog chemical splash goggles.",
                ResourceCategory.LAB_EQUIPMENT, ResourceCondition.LIKE_NEW, ShareMode.LEND, AvailabilityStatus.AVAILABLE,
                7L, "Science Hill Courtyard", "Chemistry", "res://img_lab_kit_demo", 4.6, 2L
            ),
            DemoRes(
                "seed_res_08", "seed_noah", "Noah Kim", "Mathematics", 4.6,
                "Casio fx-991EX ClassWiz Scientific Calculator",
                "Solar-powered non-programmable calculator approved for all calculus and physics midterms.",
                ResourceCategory.CALCULATORS, ResourceCondition.NEW, ShareMode.LEND, AvailabilityStatus.BORROWED,
                7L, "Math Corner Room 380", "Mathematics", "res://img_calculator_demo", 4.7, 4L
            ),
            DemoRes(
                "seed_res_09", "seed_maya", "Maya Chen", "Computer Science", 4.9,
                "Designing Data-Intensive Applications (Kleppmann)",
                "Well-kept paperback. Essential read for distributed systems and backend architecture.",
                ResourceCategory.TEXTBOOKS, ResourceCondition.GOOD, ShareMode.LEND, AvailabilityStatus.AVAILABLE,
                14L, "Gates CS Atrium", "Computer Science", "res://img_textbooks_demo", 5.0, 7L
            ),
            DemoRes(
                "seed_res_10", "seed_liam", "Liam Patel", "Electrical Engineering", 4.7,
                "Signals & Systems Formula Cheat Sheets",
                "Laminated Fourier, Laplace, and Z-transform reference sheets with worked examples.",
                ResourceCategory.NOTES, ResourceCondition.LIKE_NEW, ShareMode.FREE, AvailabilityStatus.AVAILABLE,
                30L, "Packard EE Lobby", "Electrical Engineering", "res://img_calculator_demo", 4.8, 2L
            ),
            DemoRes(
                "seed_res_11", "seed_sofia", "Sofia Rodriguez", "Chemistry", 4.8,
                "Biochemistry Lehninger 8th Ed + Flashcards",
                "Includes metabolic pathway fold-out chart and 150 amino acid study cards.",
                ResourceCategory.TEXTBOOKS, ResourceCondition.FAIR, ShareMode.EXCHANGE, AvailabilityStatus.AVAILABLE,
                21L, "Mudd Chemistry Lounge", "Chemistry", "res://img_textbooks_demo", 4.5, 3L
            ),
            DemoRes(
                "seed_res_12", "seed_noah", "Noah Kim", "Mathematics", 4.6,
                "Noise-Reducing Exam Ear Defenders + Timer",
                "Comfortable passive acoustic earmuffs and silent digital study stopwatch for library cram sessions.",
                ResourceCategory.OTHER, ResourceCondition.LIKE_NEW, ShareMode.LEND, AvailabilityStatus.AVAILABLE,
                10L, "Green Library East Wing", "Mathematics", "res://img_lab_kit_demo", 4.7, 2L
            )
        )

        listings.forEach { item ->
            batch1.set(
                db.collection("resources").document(item.id),
                mapOf(
                    "ownerId" to item.ownerId,
                    "ownerName" to item.ownerName,
                    "ownerDepartment" to item.ownerDept,
                    "ownerRating" to item.ownerRating,
                    "title" to item.title,
                    "description" to item.desc,
                    "category" to item.cat.key,
                    "condition" to item.cond.key,
                    "mode" to item.mode.key,
                    "availability" to item.avail.key,
                    "maxBorrowDays" to item.maxDays,
                    "pickupLocation" to item.loc,
                    "department" to item.dept,
                    "photoUrls" to listOf(item.photo),
                    "averageRating" to item.avg,
                    "reviewCount" to item.reviews,
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        // Starter listing owned by current user so My Listings & Received Requests work out of the box
        batch1.set(
            db.collection("resources").document("seed_user_res_01"),
            mapOf(
                "ownerId" to currentUid,
                "ownerName" to currentName,
                "ownerDepartment" to "Computer Science",
                "ownerRating" to 5.0,
                "title" to "Arduino Uno R4 Starter Kit + Sensor Pack",
                "description" to "Complete microcontroller kit with ultrasonic sensor, servo, LCD, and USB-C cable.",
                "category" to ResourceCategory.LAB_EQUIPMENT.key,
                "condition" to ResourceCondition.LIKE_NEW.key,
                "mode" to ShareMode.LEND.key,
                "availability" to AvailabilityStatus.AVAILABLE.key,
                "maxBorrowDays" to 14L,
                "pickupLocation" to "Engineering Student Center",
                "department" to "Computer Science",
                "photoUrls" to listOf("res://img_lab_kit_demo"),
                "averageRating" to 5.0,
                "reviewCount" to 1L,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch1.update(
            db.collection("users").document(currentUid),
            mapOf("itemsListedCount" to 1L, "updatedAt" to FieldValue.serverTimestamp())
        )
        batch1.commit().await()

        // Batch 2: Demo requests in varied statuses + booking slots + reviews + notifications + seed_v1 lock
        val batch2 = db.batch()

        // 1. Active borrowed item due tomorrow (Borrower = currentUid)
        batch2.set(
            db.collection("borrow_requests").document("seed_req_borrowed"),
            mapOf(
                "resourceId" to "seed_res_02",
                "resourceTitle" to "TI-Nspire CX II CAS Graphing Calculator",
                "resourceCategory" to ResourceCategory.CALCULATORS.key,
                "resourcePhoto" to "res://img_calculator_demo",
                "resourceMode" to ShareMode.LEND.key,
                "ownerId" to "seed_liam",
                "ownerName" to "Liam Patel",
                "borrowerId" to currentUid,
                "borrowerName" to currentName,
                "borrowerDepartment" to "Computer Science",
                "participantIds" to listOf("seed_liam", currentUid),
                "startDateMillis" to (now - 4 * DAY_MS),
                "endDateMillis" to (now + DAY_MS - 3_600_000L),
                "dueDateMillis" to (now + DAY_MS - 3_600_000L),
                "message" to "Borrowing for EE102 midterm exam this week!",
                "status" to BorrowStatus.BORROWED.key,
                "rejectionReason" to "",
                "extensionRequested" to false,
                "requestedExtensionDays" to 0L,
                "extensionStatus" to "none",
                "ownerReviewed" to false,
                "borrowerReviewed" to false,
                "reminderDayBeforeSent" to false,
                "reminderDueDaySent" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch2.set(
            db.collection("resources").document("seed_res_02").collection("bookings").document("seed_req_borrowed"),
            mapOf(
                "resourceId" to "seed_res_02",
                "ownerId" to "seed_liam",
                "borrowerId" to currentUid,
                "startDateMillis" to (now - 4 * DAY_MS),
                "endDateMillis" to (now + DAY_MS - 3_600_000L),
                "status" to BorrowStatus.BORROWED.key,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // 2. Overdue item (Borrower = currentUid)
        batch2.set(
            db.collection("borrow_requests").document("seed_req_overdue"),
            mapOf(
                "resourceId" to "seed_res_08",
                "resourceTitle" to "Casio fx-991EX ClassWiz Scientific Calculator",
                "resourceCategory" to ResourceCategory.CALCULATORS.key,
                "resourcePhoto" to "res://img_calculator_demo",
                "resourceMode" to ShareMode.LEND.key,
                "ownerId" to "seed_noah",
                "ownerName" to "Noah Kim",
                "borrowerId" to currentUid,
                "borrowerName" to currentName,
                "borrowerDepartment" to "Computer Science",
                "participantIds" to listOf("seed_noah", currentUid),
                "startDateMillis" to (now - 7 * DAY_MS),
                "endDateMillis" to (now - DAY_MS),
                "dueDateMillis" to (now - DAY_MS),
                "message" to "Need for Linear Algebra quiz.",
                "status" to BorrowStatus.OVERDUE.key,
                "rejectionReason" to "",
                "extensionRequested" to false,
                "requestedExtensionDays" to 0L,
                "extensionStatus" to "none",
                "ownerReviewed" to false,
                "borrowerReviewed" to false,
                "reminderDayBeforeSent" to true,
                "reminderDueDaySent" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // 3. Returned item ready for rating/review
        batch2.set(
            db.collection("borrow_requests").document("seed_req_returned"),
            mapOf(
                "resourceId" to "seed_res_01",
                "resourceTitle" to "Introduction to Algorithms (CLRS 4th Ed)",
                "resourceCategory" to ResourceCategory.TEXTBOOKS.key,
                "resourcePhoto" to "res://img_textbooks_demo",
                "resourceMode" to ShareMode.LEND.key,
                "ownerId" to "seed_maya",
                "ownerName" to "Maya Chen",
                "borrowerId" to currentUid,
                "borrowerName" to currentName,
                "borrowerDepartment" to "Computer Science",
                "participantIds" to listOf("seed_maya", currentUid),
                "startDateMillis" to (now - 14 * DAY_MS),
                "endDateMillis" to (now - 8 * DAY_MS),
                "dueDateMillis" to (now - 8 * DAY_MS),
                "message" to "Used for CS161 DP homework.",
                "status" to BorrowStatus.RETURNED.key,
                "rejectionReason" to "",
                "extensionRequested" to false,
                "requestedExtensionDays" to 0L,
                "extensionStatus" to "none",
                "ownerReviewed" to true,
                "borrowerReviewed" to false,
                "reminderDayBeforeSent" to true,
                "reminderDueDaySent" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // 4. Incoming pending request to currentUid (Requests -> Received tab)
        batch2.set(
            db.collection("borrow_requests").document("seed_req_incoming"),
            mapOf(
                "resourceId" to "seed_user_res_01",
                "resourceTitle" to "Arduino Uno R4 Starter Kit + Sensor Pack",
                "resourceCategory" to ResourceCategory.LAB_EQUIPMENT.key,
                "resourcePhoto" to "res://img_lab_kit_demo",
                "resourceMode" to ShareMode.LEND.key,
                "ownerId" to currentUid,
                "ownerName" to currentName,
                "borrowerId" to "seed_sofia",
                "borrowerName" to "Sofia Rodriguez",
                "borrowerDepartment" to "Chemistry",
                "participantIds" to listOf(currentUid, "seed_sofia"),
                "startDateMillis" to (now + 2 * DAY_MS),
                "endDateMillis" to (now + 6 * DAY_MS),
                "dueDateMillis" to (now + 6 * DAY_MS),
                "message" to "Hi! I need an Arduino kit to prototype a pH sensor logger for Chem Lab.",
                "status" to BorrowStatus.PENDING.key,
                "rejectionReason" to "",
                "extensionRequested" to false,
                "requestedExtensionDays" to 0L,
                "extensionStatus" to "none",
                "ownerReviewed" to false,
                "borrowerReviewed" to false,
                "reminderDayBeforeSent" to false,
                "reminderDueDaySent" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // 5. Outgoing pending request from currentUid (Requests -> Sent tab)
        batch2.set(
            db.collection("borrow_requests").document("seed_req_outgoing"),
            mapOf(
                "resourceId" to "seed_res_03",
                "resourceTitle" to "Organic Chemistry Molecular Model Kit (240 pcs)",
                "resourceCategory" to ResourceCategory.LAB_EQUIPMENT.key,
                "resourcePhoto" to "res://img_lab_kit_demo",
                "resourceMode" to ShareMode.LEND.key,
                "ownerId" to "seed_sofia",
                "ownerName" to "Sofia Rodriguez",
                "borrowerId" to currentUid,
                "borrowerName" to currentName,
                "borrowerDepartment" to "Computer Science",
                "participantIds" to listOf("seed_sofia", currentUid),
                "startDateMillis" to (now + 3 * DAY_MS),
                "endDateMillis" to (now + 7 * DAY_MS),
                "dueDateMillis" to (now + 7 * DAY_MS),
                "message" to "Helping my lab partner visualize chair conformations this week!",
                "status" to BorrowStatus.PENDING.key,
                "rejectionReason" to "",
                "extensionRequested" to false,
                "requestedExtensionDays" to 0L,
                "extensionStatus" to "none",
                "ownerReviewed" to false,
                "borrowerReviewed" to false,
                "reminderDayBeforeSent" to false,
                "reminderDueDaySent" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // Sample initial notifications
        batch2.set(
            db.collection("notifications").document("seed_notif_1"),
            buildNotificationPayload(
                userId = currentUid,
                title = "New Borrow Request from Sofia",
                body = "Sofia Rodriguez requested \"Arduino Uno R4 Starter Kit + Sensor Pack\" for 5 days.",
                type = "request_received",
                relatedId = "seed_req_incoming",
                emailRecipient = currentEmail
            )
        )
        batch2.set(
            db.collection("notifications").document("seed_notif_2"),
            buildNotificationPayload(
                userId = currentUid,
                title = "Overdue Return Notice",
                body = "\"Casio fx-991EX ClassWiz Scientific Calculator\" was due yesterday. Return or request an extension.",
                type = "overdue",
                relatedId = "seed_req_overdue",
                emailRecipient = currentEmail
            )
        )

        // Sample report for Admin Moderation Dashboard
        batch2.set(
            db.collection("reports").document("seed_report_01"),
            mapOf(
                "reporterId" to currentUid,
                "reporterName" to currentName,
                "targetType" to "listing",
                "targetId" to "seed_res_11",
                "targetName" to "Biochemistry Lehninger 8th Ed + Flashcards",
                "reason" to "Missing pages check",
                "details" to "Reported to verify whether chapter 14 flashcards are complete before exchange.",
                "status" to "open",
                "adminAction" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch2.commit().await()

        // Batch 3: Sample Review (depends on committed borrow_requests/seed_req_returned) + seed_v1 lock
        val batch3 = db.batch()
        batch3.set(
            db.collection("reviews").document("seed_req_returned_seed_maya"),
            mapOf(
                "transactionId" to "seed_req_returned",
                "resourceId" to "seed_res_01",
                "resourceTitle" to "Introduction to Algorithms (CLRS 4th Ed)",
                "reviewerId" to "seed_maya",
                "reviewerName" to "Maya Chen",
                "revieweeId" to currentUid,
                "revieweeName" to currentName,
                "direction" to "owner_to_borrower",
                "rating" to 5L,
                "comment" to "Returned right on time in spotless condition. Great campus borrower!",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch3.set(
            db.collection("app_config").document("seed_v1"),
            mapOf(
                "seededBy" to currentUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch3.commit().await()
    }
}
