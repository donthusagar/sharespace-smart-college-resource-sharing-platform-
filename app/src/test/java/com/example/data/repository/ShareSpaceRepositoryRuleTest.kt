package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AvailabilityStatus
import com.example.data.model.BorrowStatus
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceCondition
import com.example.data.model.ShareMode
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ShareSpaceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileAndCreateListing_authenticatedOwner_succeedsAndStreams() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@campus.edu")
        val currentUser = checkNotNull(auth.currentUser)
        val repo = ShareSpaceRepository(firestore)

        val seedRes = withTimeout(TIMEOUT_MS) { repo.ensureUserProfileAndSeedIfNeeded(currentUser) }
        assertTrue("Seed/profile init failed: ${seedRes.exceptionOrNull()}", seedRes.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProfile(uid).first { it != null }
        } ?: UserProfile(uid = uid, displayName = "Alice")

        val createRes = withTimeout(TIMEOUT_MS) {
            repo.createOrUpdateListing(
                existingId = null,
                owner = profile,
                title = "Organic Chemistry Lab Kit",
                description = "Full glassware and molecular set for CHEM 121.",
                category = ResourceCategory.LAB_EQUIPMENT,
                condition = ResourceCondition.LIKE_NEW,
                mode = ShareMode.LEND,
                availability = AvailabilityStatus.AVAILABLE,
                maxBorrowDays = 14,
                pickupLocation = "Chemistry Courtyard",
                department = "Chemistry",
                photoUrls = listOf("res://img_lab_kit_demo")
            )
        }
        assertTrue("Create listing failed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
        val listingId = createRes.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeActiveResources().first { list -> list.any { it.id == listingId } }
        }
        assertTrue(observed.any { it.id == listingId })
    }

    @Test
    fun borrowRequestWorkflow_createApproveBlockOverlapAndConfirmReturn_succeeds() = runBlocking {
        val ownerUid = signInTestUser("owner_${UUID.randomUUID().toString().take(6)}@campus.edu")
        val ownerUser = checkNotNull(auth.currentUser)
        val repo = ShareSpaceRepository(firestore)
        withTimeout(TIMEOUT_MS) { repo.ensureUserProfileAndSeedIfNeeded(ownerUser) }.getOrThrow()
        val ownerProfile = withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserProfile(ownerUid).first { it != null } }!!

        val resId = withTimeout(TIMEOUT_MS) {
            repo.createOrUpdateListing(
                existingId = null,
                owner = ownerProfile,
                title = "TI-84 Plus CE Calculator",
                description = "Charged and ready for Calculus finals.",
                category = ResourceCategory.CALCULATORS,
                condition = ResourceCondition.GOOD,
                mode = ShareMode.LEND,
                availability = AvailabilityStatus.AVAILABLE,
                maxBorrowDays = 14,
                pickupLocation = "Math Lobby",
                department = "Mathematics",
                photoUrls = listOf("res://img_calculator_demo")
            )
        }.getOrThrow()

        val listing = withTimeout(FLOW_TIMEOUT_MS) { repo.observeResource(resId).first { it != null } }!!

        // Switch to borrower
        val borrowerUid = signInTestUser("borrower_${UUID.randomUUID().toString().take(6)}@campus.edu")
        val borrowerUser = checkNotNull(auth.currentUser)
        withTimeout(TIMEOUT_MS) { repo.ensureUserProfileAndSeedIfNeeded(borrowerUser) }.getOrThrow()
        val borrowerProfile = withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserProfile(borrowerUid).first { it != null } }!!

        val start = System.currentTimeMillis() + 86_400_000L
        val end = start + 3 * 86_400_000L

        val reqId = withTimeout(TIMEOUT_MS) {
            repo.createBorrowRequest(listing, borrowerProfile, start, end, "Need for midterm")
        }.getOrThrow()

        // Switch back to owner and approve
        signInTestUser(ownerUser.email!!)
        val req = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserBorrowRequests(ownerUid).first { list -> list.any { it.id == reqId } }
        }.first { it.id == reqId }

        withTimeout(TIMEOUT_MS) { repo.approveBorrowRequest(req) }.getOrThrow()
        val approvedReq = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserBorrowRequests(ownerUid).first { list ->
                list.any { it.id == reqId && it.status == BorrowStatus.APPROVED.key }
            }
        }.first { it.id == reqId }

        // Overlapping request by borrower must be blocked
        signInTestUser(borrowerUser.email!!)
        val overlapAttempt = withTimeout(TIMEOUT_MS) {
            repo.createBorrowRequest(listing, borrowerProfile, start, end, "Duplicate overlap")
        }
        assertTrue(overlapAttempt.isFailure)

        // Owner marks borrowed -> returned -> borrower submits review
        signInTestUser(ownerUser.email!!)
        withTimeout(TIMEOUT_MS) { repo.markAsBorrowed(approvedReq) }.getOrThrow()
        val borrowedReq = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserBorrowRequests(ownerUid).first { list ->
                list.any { it.id == reqId && it.status == BorrowStatus.BORROWED.key }
            }
        }.first { it.id == reqId }

        withTimeout(TIMEOUT_MS) { repo.confirmReturned(borrowedReq) }.getOrThrow()
        val returnedReq = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserBorrowRequests(ownerUid).first { list ->
                list.any { it.id == reqId && it.status == BorrowStatus.RETURNED.key }
            }
        }.first { it.id == reqId }

        signInTestUser(borrowerUser.email!!)
        val reviewResult = withTimeout(TIMEOUT_MS) {
            repo.submitReview(returnedReq, borrowerProfile, 5, "Awesome lender!")
        }
        assertTrue("Submit review failed: ${reviewResult.exceptionOrNull()}", reviewResult.isSuccess)
    }

    @Test
    fun observeActiveResources_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ShareSpaceRepository(firestore)
        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeActiveResources().first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull("Expected PERMISSION_DENIED exception", caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
