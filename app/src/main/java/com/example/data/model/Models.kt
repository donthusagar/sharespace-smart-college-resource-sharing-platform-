package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ResourceCategory(val key: String, val label: String) {
    TEXTBOOKS("textbooks", "Textbooks"),
    CALCULATORS("calculators", "Calculators"),
    LAB_EQUIPMENT("lab_equipment", "Lab Gear"),
    NOTES("notes", "Notes"),
    OTHER("other", "Other");

    companion object {
        fun fromKey(key: String): ResourceCategory =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}

enum class ResourceCondition(val key: String, val label: String) {
    NEW("new", "Brand New"),
    LIKE_NEW("like_new", "Like New"),
    GOOD("good", "Good"),
    FAIR("fair", "Fair");

    companion object {
        fun fromKey(key: String): ResourceCondition =
            entries.firstOrNull { it.key == key } ?: GOOD
    }
}

enum class ShareMode(val key: String, val label: String) {
    LEND("lend", "Lend"),
    EXCHANGE("exchange", "Exchange"),
    FREE("free", "Free");

    companion object {
        fun fromKey(key: String): ShareMode =
            entries.firstOrNull { it.key == key } ?: LEND
    }
}

enum class AvailabilityStatus(val key: String, val label: String) {
    AVAILABLE("available", "Available"),
    BORROWED("borrowed", "Borrowed"),
    UNAVAILABLE("unavailable", "Unavailable");

    companion object {
        fun fromKey(key: String): AvailabilityStatus =
            entries.firstOrNull { it.key == key } ?: AVAILABLE
    }
}

enum class BorrowStatus(val key: String, val label: String) {
    PENDING("pending", "Pending"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    BORROWED("borrowed", "Borrowed"),
    RETURNED("returned", "Returned"),
    CANCELLED("cancelled", "Cancelled"),
    OVERDUE("overdue", "Overdue");

    companion object {
        fun fromKey(key: String): BorrowStatus =
            entries.firstOrNull { it.key == key } ?: PENDING
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val department: String = "Computer Science",
    val year: String = "Junior",
    val campusDomain: String = "campus.edu",
    val avatarKey: String = "avatar_1",
    val role: String = "student",
    val banned: Boolean = false,
    val banReason: String = "",
    val campusVerified: Boolean = true,
    val averageRating: Double = 0.0,
    val reviewCount: Long = 0L,
    val itemsListedCount: Long = 0L,
    val itemsBorrowedCount: Long = 0L,
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPrivateContact(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val collegeEmail: String = "",
    val phone: String = "",
    val emailNotificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WishlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val resourceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ResourceListing(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerDepartment: String = "",
    val ownerRating: Double = 0.0,
    val title: String = "",
    val description: String = "",
    val category: String = ResourceCategory.TEXTBOOKS.key,
    val condition: String = ResourceCondition.GOOD.key,
    val mode: String = ShareMode.LEND.key,
    val availability: String = AvailabilityStatus.AVAILABLE.key,
    val maxBorrowDays: Long = 14L,
    val pickupLocation: String = "",
    val department: String = "",
    val photoUrls: List<String> = emptyList(),
    val averageRating: Double = 0.0,
    val reviewCount: Long = 0L,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ResourceBookingSlot(
    @DocumentId val id: String = "",
    val resourceId: String = "",
    val ownerId: String = "",
    val borrowerId: String = "",
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L,
    val status: String = BorrowStatus.PENDING.key,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BorrowRequest(
    @DocumentId val id: String = "",
    val resourceId: String = "",
    val resourceTitle: String = "",
    val resourceCategory: String = "",
    val resourcePhoto: String = "",
    val resourceMode: String = ShareMode.LEND.key,
    val ownerId: String = "",
    val ownerName: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerDepartment: String = "",
    val participantIds: List<String> = emptyList(),
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L,
    val dueDateMillis: Long = 0L,
    val message: String = "",
    val status: String = BorrowStatus.PENDING.key,
    val rejectionReason: String = "",
    val extensionRequested: Boolean = false,
    val requestedExtensionDays: Long = 0L,
    val extensionStatus: String = "none",
    val ownerReviewed: Boolean = false,
    val borrowerReviewed: Boolean = false,
    val reminderDayBeforeSent: Boolean = false,
    val reminderDueDaySent: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Review(
    @DocumentId val id: String = "",
    val transactionId: String = "",
    val resourceId: String = "",
    val resourceTitle: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val revieweeId: String = "",
    val revieweeName: String = "",
    val direction: String = "borrower_to_owner",
    val rating: Long = 5L,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "request_received",
    val relatedId: String = "",
    val isRead: Boolean = false,
    val emailDispatched: Boolean = true,
    val emailRecipient: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ModerationReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val targetType: String = "listing",
    val targetId: String = "",
    val targetName: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "open",
    val adminAction: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
