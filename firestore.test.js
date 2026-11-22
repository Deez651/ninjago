const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const SALT = "njg_deez_salt_2026";
const CLIENT_HASH = crypto.createHash("sha256").update(`${SALT}:amountcare600`).digest("hex");
const VERIFIER_HASH = crypto.createHash("sha256").update(CLIENT_HASH).digest("hex");
const VAULT_ID = "a".repeat(64);

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

test("Super admin deez bootstrap succeeds with valid verifierHash and fails with wrong hash", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  // Wrong verifierHash must fail
  await assertFails(
    db.collection("admin_accounts").doc("deez").set({
      login: "deez",
      salt: SALT,
      verifierHash: "0".repeat(64),
      encryptedVaultId: "enc_vault_sample_123456",
      role: "super_admin",
      active: true,
      createdBy: "deez",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid verifierHash succeeds once
  await assertSucceeds(
    db.collection("admin_accounts").doc("deez").set({
      login: "deez",
      salt: SALT,
      verifierHash: VERIFIER_HASH,
      encryptedVaultId: "enc_vault_sample_123456",
      role: "super_admin",
      active: true,
      createdBy: "deez",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Listing admin_accounts must be denied
  await assertFails(db.collection("admin_accounts").get());
});

test("Admin session creation verifies SHA-256(authProof) and atomic WriteBatch publishes post + burns session", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  const future = new Date(Date.now() + 3600 * 1000);

  await assertSucceeds(
    db.collection("admin_accounts").doc("deez").set({
      login: "deez",
      salt: SALT,
      verifierHash: VERIFIER_HASH,
      encryptedVaultId: "enc_vault_sample_123456",
      role: "super_admin",
      active: true,
      createdBy: "deez",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Wrong authProof must fail
  await assertFails(
    db.collection("admin_sessions").doc("session_bad_1234567890").set({
      accountId: "deez",
      authProof: "f".repeat(64),
      createdAt: now,
      expiresAt: future,
    })
  );

  const session1 = "session_001_abcdefghij";
  await assertSucceeds(
    db.collection("admin_sessions").doc(session1).set({
      accountId: "deez",
      authProof: CLIENT_HASH,
      createdAt: now,
      expiresAt: future,
    })
  );

  // Reading admin_sessions must fail
  await assertFails(db.collection("admin_sessions").doc(session1).get());

  // Publishing post WITHOUT burning session must fail
  await assertFails(
    db.collection("posts").doc("post_unauth").set({
      title: "Fake Post",
      description: "Should fail",
      category: "Scripts",
      tags: ["lua"],
      fileName: "script.lua",
      fileSizeBytes: 120,
      mimeType: "text/plain",
      hasFile: true,
      requiresKey: false,
      downloadCount: 0,
      authorLogin: "deez",
      deleted: false,
      sessionProofId: session1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Atomic batch: burn session1, create session2, publish post + payload
  const session2 = "session_002_abcdefghij";
  const batch = db.batch();
  batch.delete(db.collection("admin_sessions").doc(session1));
  batch.set(db.collection("admin_sessions").doc(session2), {
    accountId: "deez",
    authProof: CLIENT_HASH,
    createdAt: now,
    expiresAt: future,
  });
  batch.set(db.collection("posts").doc("post_1"), {
    title: "Ninjago AutoFarm v2",
    description: "Working script release",
    category: "Скрипты",
    tags: ["ninjago", "lua"],
    fileName: "ninjago_v2.lua",
    fileSizeBytes: 512,
    mimeType: "text/plain",
    hasFile: true,
    requiresKey: true,
    downloadCount: 0,
    authorLogin: "deez",
    deleted: false,
    sessionProofId: session1,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(db.collection("post_payloads").doc("post_1"), {
    postId: "post_1",
    embeddedBase64: "cHJpbnQoIkhlbGxvIik=",
    chunkCount: 0,
    sha256: "abc123",
    requiresKey: true,
    sessionProofId: session1,
    createdAt: now,
    updatedAt: now,
  });

  await assertSucceeds(batch.commit());

  // Public user can list non-deleted posts and increment downloadCount by 1
  await assertSucceeds(db.collection("posts").where("deleted", "==", false).get());
  await assertFails(db.collection("posts").get()); // unfiltered list fails
  await assertSucceeds(
    db.collection("posts").doc("post_1").update({
      downloadCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Cannot jump downloadCount by 10
  await assertFails(
    db.collection("posts").doc("post_1").update({
      downloadCount: 10,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Access keys enforce maxActivations, expiration, non-listability, and atomic activation logs", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);
  const future = new Date(Date.now() + 3600 * 1000);

  await assertSucceeds(
    db.collection("admin_accounts").doc("deez").set({
      login: "deez",
      salt: SALT,
      verifierHash: VERIFIER_HASH,
      encryptedVaultId: "enc_vault_sample_123456",
      role: "super_admin",
      active: true,
      createdBy: "deez",
      createdAt: now,
      updatedAt: now,
    })
  );

  const s1 = "session_key_123456789";
  await assertSucceeds(
    db.collection("admin_sessions").doc(s1).set({
      accountId: "deez",
      authProof: CLIENT_HASH,
      createdAt: now,
      expiresAt: future,
    })
  );

  const s2 = "session_key_223456789";
  const createKeyBatch = db.batch();
  createKeyBatch.delete(db.collection("admin_sessions").doc(s1));
  createKeyBatch.set(db.collection("admin_sessions").doc(s2), {
    accountId: "deez",
    authProof: CLIENT_HASH,
    createdAt: now,
    expiresAt: future,
  });
  createKeyBatch.set(db.collection("access_keys").doc("NJG-1111-AAAA"), {
    code: "NJG-1111-AAAA",
    label: "VIP Batch",
    scope: "all",
    maxActivations: 1,
    usedCount: 0,
    active: true,
    expiresAt: future,
    createdBy: "deez",
    sessionProofId: s1,
    createdAt: now,
    updatedAt: now,
  });
  createKeyBatch.set(db.collection("admin_vaults").doc(VAULT_ID).collection("key_registry").doc("NJG-1111-AAAA"), {
    keyCode: "NJG-1111-AAAA",
    label: "VIP Batch",
    createdBy: "deez",
    deleted: false,
    sessionProofId: s1,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(createKeyBatch.commit());

  // Regular user cannot list all access_keys
  await assertFails(db.collection("access_keys").get());

  // Regular user can get specific known key
  await assertSucceeds(db.collection("access_keys").doc("NJG-1111-AAAA").get());

  // Atomic redemption + activation log succeeds once
  const redeemBatch = db.batch();
  redeemBatch.update(db.collection("access_keys").doc("NJG-1111-AAAA"), {
    usedCount: 1,
    lastUsedAt: new Date(Date.now() - 500),
    updatedAt: new Date(Date.now() - 500),
  });
  redeemBatch.set(db.collection("access_keys").doc("NJG-1111-AAAA").collection("activations").doc("act_1"), {
    keyCode: "NJG-1111-AAAA",
    deviceLabel: "Pixel 9",
    deviceHash: "hash12345678",
    activatedAt: new Date(Date.now() - 500),
  });
  await assertSucceeds(redeemBatch.commit());

  // Second redemption exceeds maxActivations=1 and must fail
  const overBatch = db.batch();
  overBatch.update(db.collection("access_keys").doc("NJG-1111-AAAA"), {
    usedCount: 2,
    lastUsedAt: new Date(Date.now() - 100),
    updatedAt: new Date(Date.now() - 100),
  });
  overBatch.set(db.collection("access_keys").doc("NJG-1111-AAAA").collection("activations").doc("act_2"), {
    keyCode: "NJG-1111-AAAA",
    deviceLabel: "Pixel 9",
    deviceHash: "hash12345678",
    activatedAt: new Date(Date.now() - 100),
  });
  await assertFails(overBatch.commit());
});
