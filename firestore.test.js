const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_EMAIL = "donthusagar1@gmail.com";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 1000);
}

function makeUserProfile(uid, role = "student", banned = false) {
  const ts = nowTs();
  return {
    uid,
    displayName: `Student ${uid}`,
    department: "Computer Science",
    year: "Junior",
    campusDomain: "stanford.edu",
    avatarKey: "avatar_1",
    role,
    banned,
    banReason: "",
    campusVerified: true,
    averageRating: 4.8,
    reviewCount: 2,
    itemsListedCount: 1,
    itemsBorrowedCount: 0,
    isPublic: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makePrivateContact(uid) {
  const ts = nowTs();
  return {
    uid,
    email: `${uid}@stanford.edu`,
    collegeEmail: `${uid}@stanford.edu`,
    phone: "555-0199",
    emailNotificationsEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeResource(ownerId, status = "active") {
  const ts = nowTs();
  return {
    ownerId,
    ownerName: "Alice",
    ownerDepartment: "Computer Science",
    ownerRating: 4.9,
    title: "CLRS Algorithms 4th Ed",
    description: "Clean hardcover textbook with zero markings.",
    category: "textbooks",
    condition: "like_new",
    mode: "lend",
    availability: "available",
    maxBorrowDays: 14,
    pickupLocation: "Engineering Quad Atrium",
    department: "Computer Science",
    photoUrls: ["res://img_textbooks_demo"],
    averageRating: 4.9,
    reviewCount: 3,
    status,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeBorrowRequest(resourceId, ownerId, borrowerId, status = "pending") {
  const ts = nowTs();
  const start = Date.now() + 86400000;
  const end = start + 5 * 86400000;
  return {
    resourceId,
    resourceTitle: "CLRS Algorithms 4th Ed",
    resourceCategory: "textbooks",
    resourcePhoto: "res://img_textbooks_demo",
    resourceMode: "lend",
    ownerId,
    ownerName: "Alice",
    borrowerId,
    borrowerName: "Bob",
    borrowerDepartment: "Physics",
    participantIds: [ownerId, borrowerId],
    startDateMillis: start,
    endDateMillis: end,
    dueDateMillis: end,
    message: "Need this for CS161 problem sets.",
    status,
    rejectionReason: "",
    extensionRequested: false,
    requestedExtensionDays: 0,
    extensionStatus: "none",
    ownerReviewed: false,
    borrowerReviewed: false,
    reminderDayBeforeSent: false,
    reminderDueDaySent: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read resources or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("resources").where("status", "==", "active").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("User cannot self-assign admin role or insert ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@stanford.edu", email_verified: true }).firestore();
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "admin", false)));
  const withGhost = { ...makeUserProfile(ALICE_UID, "student", false), isVerifiedAdmin: true };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(withGhost));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "student", false)));
});

test("PII Isolation: Bob cannot read Alice private contact doc", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("private").doc("contact").set(makePrivateContact(ALICE_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("contact").get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("contact").get());
});

test("Email spoofing test: unverified admin email is rejected from admin actions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set(makeUserProfile(BOB_UID));
  });

  const spoofedDb = testEnv.authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  await assertFails(spoofedDb.collection("users").doc(BOB_UID).update({ banned: true, banReason: "Spoof" }));

  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(realAdminDb.collection("users").doc(BOB_UID).update({ banned: true, banReason: "Policy violation" }));
});

test("Resource listings: owner can create and edit, non-owner cannot edit or delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("resources").doc("res_1").set(makeResource(ALICE_UID)));
  await assertSucceeds(bobDb.collection("resources").where("status", "==", "active").get());
  await assertFails(bobDb.collection("resources").doc("res_1").update({ title: "Hacked Title" }));
  await assertFails(bobDb.collection("resources").doc("res_1").delete());
  await assertSucceeds(aliceDb.collection("resources").doc("res_1").update({ title: "CLRS Algorithms 4th Ed (Updated)" }));
});

test("Borrow requests: borrower cannot self-approve; only owner can approve", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("resources").doc("res_1").set(makeResource(ALICE_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(bobDb.collection("borrow_requests").doc("req_1").set(makeBorrowRequest("res_1", ALICE_UID, BOB_UID, "pending")));
  await assertFails(bobDb.collection("borrow_requests").doc("req_1").update({ status: "approved" }));
  await assertSucceeds(aliceDb.collection("borrow_requests").doc("req_1").update({ status: "approved" }));
  await assertSucceeds(bobDb.collection("borrow_requests").where("participantIds", "array-contains", BOB_UID).get());
});

test("Reviews: only allowed after returned status and strictly once per participant", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("resources").doc("res_1").set(makeResource(ALICE_UID));
    await db.collection("borrow_requests").doc("req_1").set(makeBorrowRequest("res_1", ALICE_UID, BOB_UID, "returned"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const reviewData = {
    transactionId: "req_1",
    resourceId: "res_1",
    resourceTitle: "CLRS Algorithms 4th Ed",
    reviewerId: BOB_UID,
    reviewerName: "Bob",
    revieweeId: ALICE_UID,
    revieweeName: "Alice",
    direction: "borrower_to_owner",
    rating: 5,
    comment: "Super helpful and prompt pickup!",
    createdAt: nowTs(),
  };

  await assertSucceeds(bobDb.collection("reviews").doc(`req_1_${BOB_UID}`).set(reviewData));
  await assertFails(bobDb.collection("reviews").doc("arbitrary_id").set(reviewData));
});

test("Banned user cannot read resources", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).set(makeUserProfile(BOB_UID, "student", true));
    await db.collection("resources").doc("res_1").set(makeResource(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("resources").where("status", "==", "active").get());
});
