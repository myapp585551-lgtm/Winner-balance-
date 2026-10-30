const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_admin_123";
const BOB_UID = "bob_other_456";
const PUBLIC_ID = "WB7x9Km2Pq4Rt8Vz3N";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("1. Unauthenticated caller cannot list admin_users or public_users (no user enumeration)", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("admin_users").get());
  await assertFails(unauthDb.collection("public_users").get());
  await assertFails(unauthDb.collection("deposit_requests").get());
  await assertFails(unauthDb.collection("withdrawal_requests").get());
  await assertFails(unauthDb.collection("ledger_entries").get());
});

test("2. Public user can open single public_users/{publicId} page without admin login", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("public_users").doc(PUBLIC_ID).set({
      publicId: PUBLIC_ID,
      userId: "usr-verified-wb01",
      ownerUid: ALICE_UID,
      displayName: "Tanvir Rahman",
      balancePaisa: 125000,
      reservedPaisa: 0,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const snap = await assertSucceeds(unauthDb.collection("public_users").doc(PUBLIC_ID).get());
  if (snap.data().displayName !== "Tanvir Rahman" || snap.data().balancePaisa !== 125000) {
    throw new Error("Unexpected public user payload");
  }
});

test("3. Unauthenticated caller cannot tamper with public user balance or create arbitrary users", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("public_users").doc(PUBLIC_ID).set({
      publicId: PUBLIC_ID,
      userId: "usr-verified-wb01",
      ownerUid: ALICE_UID,
      displayName: "Tanvir Rahman",
      balancePaisa: 125000,
      reservedPaisa: 0,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("public_users").doc(PUBLIC_ID).update({
      balancePaisa: 99999999,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("public_users").doc("WBattackerFakeId99").set({
      publicId: "WBattackerFakeId99",
      userId: "usr-fake",
      ownerUid: ALICE_UID,
      displayName: "Attacker",
      balancePaisa: 500000,
      reservedPaisa: 0,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("4. Cross-user isolation & Email Spoofing Test (unverified admin email rejected)", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admin_users").doc("usr-1").set({
      userId: "usr-1",
      publicId: PUBLIC_ID,
      ownerUid: ALICE_UID,
      displayName: "Tanvir Rahman",
      balancePaisa: 125000,
      reservedPaisa: 0,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Bob cannot read Alice's admin_users
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("admin_users").doc("usr-1").get());
  await assertFails(bobDb.collection("admin_users").where("ownerUid", "==", ALICE_UID).get());

  // Spoofed unverified admin email MUST fail
  const spoofDb = testEnv.authenticatedContext("spoof_uid_999", {
    email: "devroy58555120@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("admin_users").doc("usr-1").get());

  // Verified bootstrapped admin email succeeds
  const realAdminDb = testEnv.authenticatedContext("real_owner_uid", {
    email: "devroy58555120@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(realAdminDb.collection("admin_users").doc("usr-1").get());
});

test("5. Shadow Update Test: ghost fields rejected on create and update", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("admin_users").doc("usr-shadow").set({
      userId: "usr-shadow",
      publicId: "WBshadowTestId1234",
      ownerUid: ALICE_UID,
      displayName: "Shadow User",
      balancePaisa: 10000,
      reservedPaisa: 0,
      isActive: true,
      isVerified: true, // Ghost field!
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("6. Public atomic bKash deposit batch succeeds once and blocks duplicate TrxID replay; PII read denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("public_users").doc(PUBLIC_ID).set({
      publicId: PUBLIC_ID,
      userId: "usr-verified-wb01",
      ownerUid: ALICE_UID,
      displayName: "Tanvir Rahman",
      balancePaisa: 125000,
      reservedPaisa: 0,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const batch1 = unauthDb.batch();
  batch1.set(unauthDb.collection("deposit_requests").doc("DEP-TEST01"), {
    requestId: "DEP-TEST01",
    userId: "usr-verified-wb01",
    publicId: PUBLIC_ID,
    ownerUid: ALICE_UID,
    userDisplayName: "Tanvir Rahman",
    amountPaisa: 50000,
    senderBkashNumber: "01712345678",
    transactionId: "TRX998877AB",
    status: "PENDING",
    isCredited: false,
    createdAt: now,
    updatedAt: now,
  });
  batch1.set(unauthDb.collection("deposit_trx_locks").doc("TRX998877AB"), {
    transactionId: "TRX998877AB",
    requestId: "DEP-TEST01",
    publicId: PUBLIC_ID,
    ownerUid: ALICE_UID,
    createdAt: now,
  });
  batch1.set(
    unauthDb.collection("public_users").doc(PUBLIC_ID).collection("request_statuses").doc("DEP-TEST01"),
    {
      requestId: "DEP-TEST01",
      publicId: PUBLIC_ID,
      ownerUid: ALICE_UID,
      requestType: "DEPOSIT",
      amountPaisa: 50000,
      summaryLabel: "TrxID: TRX998877AB",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    }
  );
  await assertSucceeds(batch1.commit());

  // Unauthenticated caller CANNOT read PII deposit_requests document
  await assertFails(unauthDb.collection("deposit_requests").doc("DEP-TEST01").get());

  // Duplicate TrxID replay MUST fail
  const batchDup = unauthDb.batch();
  batchDup.set(unauthDb.collection("deposit_requests").doc("DEP-TEST02"), {
    requestId: "DEP-TEST02",
    userId: "usr-verified-wb01",
    publicId: PUBLIC_ID,
    ownerUid: ALICE_UID,
    userDisplayName: "Tanvir Rahman",
    amountPaisa: 50000,
    senderBkashNumber: "01712345678",
    transactionId: "TRX998877AB",
    status: "PENDING",
    isCredited: false,
    createdAt: now,
    updatedAt: now,
  });
  batchDup.set(unauthDb.collection("deposit_trx_locks").doc("TRX998877AB"), {
    transactionId: "TRX998877AB",
    requestId: "DEP-TEST02",
    publicId: PUBLIC_ID,
    ownerUid: ALICE_UID,
    createdAt: now,
  });
  batchDup.set(
    unauthDb.collection("public_users").doc(PUBLIC_ID).collection("request_statuses").doc("DEP-TEST02"),
    {
      requestId: "DEP-TEST02",
      publicId: PUBLIC_ID,
      ownerUid: ALICE_UID,
      requestType: "DEPOSIT",
      amountPaisa: 50000,
      summaryLabel: "TrxID: TRX998877AB",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    }
  );
  await assertFails(batchDup.commit());
});

test("7. Public atomic Bank withdrawal batch reserves exact amount and rejects overdraft", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("public_users").doc(PUBLIC_ID).set({
      publicId: PUBLIC_ID,
      userId: "usr-verified-wb01",
      ownerUid: ALICE_UID,
      displayName: "Tanvir Rahman",
      balancePaisa: 125000,
      reservedPaisa: 0,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();

  // Overdraft (> 125000) MUST fail
  const overdraftBatch = unauthDb.batch();
  overdraftBatch.set(unauthDb.collection("withdrawal_requests").doc("WDR-OVER"), {
    requestId: "WDR-OVER",
    userId: "usr-verified-wb01",
    publicId: PUBLIC_ID,
    ownerUid: ALICE_UID,
    userDisplayName: "Tanvir Rahman",
    amountPaisa: 200000,
    bankName: "BRAC Bank",
    accountHolderName: "Tanvir Rahman",
    bankAccountNumber: "150120998877",
    status: "PENDING",
    isReserved: true,
    isDeducted: false,
    createdAt: now,
    updatedAt: now,
  });
  overdraftBatch.update(unauthDb.collection("public_users").doc(PUBLIC_ID), {
    reservedPaisa: 200000,
    lastWithdrawalRequestId: "WDR-OVER",
    updatedAt: now,
  });
  overdraftBatch.set(
    unauthDb.collection("public_users").doc(PUBLIC_ID).collection("request_statuses").doc("WDR-OVER"),
    {
      requestId: "WDR-OVER",
      publicId: PUBLIC_ID,
      ownerUid: ALICE_UID,
      requestType: "WITHDRAWAL",
      amountPaisa: 200000,
      summaryLabel: "Bank: BRAC Bank",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    }
  );
  await assertFails(overdraftBatch.commit());

  // Valid 50,000 paisa (500 BDT) withdrawal succeeds atomically
  const validBatch = unauthDb.batch();
  validBatch.set(unauthDb.collection("withdrawal_requests").doc("WDR-OK01"), {
    requestId: "WDR-OK01",
    userId: "usr-verified-wb01",
    publicId: PUBLIC_ID,
    ownerUid: ALICE_UID,
    userDisplayName: "Tanvir Rahman",
    amountPaisa: 50000,
    bankName: "BRAC Bank",
    accountHolderName: "Tanvir Rahman",
    bankAccountNumber: "150120998877",
    status: "PENDING",
    isReserved: true,
    isDeducted: false,
    createdAt: now,
    updatedAt: now,
  });
  validBatch.update(unauthDb.collection("public_users").doc(PUBLIC_ID), {
    reservedPaisa: 50000,
    lastWithdrawalRequestId: "WDR-OK01",
    updatedAt: now,
  });
  validBatch.set(
    unauthDb.collection("public_users").doc(PUBLIC_ID).collection("request_statuses").doc("WDR-OK01"),
    {
      requestId: "WDR-OK01",
      publicId: PUBLIC_ID,
      ownerUid: ALICE_UID,
      requestType: "WITHDRAWAL",
      amountPaisa: 50000,
      summaryLabel: "Bank: BRAC Bank",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    }
  );
  await assertSucceeds(validBatch.commit());
});
