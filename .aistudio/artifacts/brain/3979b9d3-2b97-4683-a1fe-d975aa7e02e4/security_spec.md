# Winner Balance — Firestore Security Specification

## 1. Data Invariants
1. **Default-Deny Catch-All**: All undeclared paths deny all reads and writes (`allow read, write: if false;`).
2. **PII Split-Collection Isolation**:
   - Sensitive admin and financial PII (`admin_users`, `deposit_requests` containing `senderBkashNumber`, `withdrawal_requests` containing `bankAccountNumber` and `accountHolderName`, and `ledger_entries` containing `actorEmail`) are strictly isolated from public collections and readable (`get` and `list`) ONLY by the authenticated owner (`request.auth.uid == resource.data.ownerUid`) or verified bootstrapped admin (`devroy58555120@gmail.com` with `email_verified == true`).
   - Public user balance projections (`public_users/{publicId}`) contain zero PII, deny unauthenticated `list` enumeration (`allow list` requires authenticated owner/admin), and permit single-document `get` only with a valid capability `publicId`.
3. **Immutable Audit & Identity Fields**: `createdAt`, `userId`, `publicId` (on public projection), and `requestId` cannot be mutated after creation.
4. **Atomic Financial Integrity**:
   - Unauthenticated public users can never alter `balancePaisa` or decrease `reservedPaisa`.
   - Public bank withdrawal reservation on `public_users/{publicId}` can only increase `reservedPaisa` (up to `balancePaisa`) atomically with a newly created `withdrawal_requests/{requestId}` verified via `!exists(...)`, `existsAfter(...)`, and `getAfter(...)`.
   - Public bKash deposits must atomically create `/deposit_trx_locks/{transactionId}` (`allow update: if false;`) to prevent duplicate Transaction ID replay.
   - Terminal payment states (`APPROVED`, `REJECTED`) lock standard updates against double-crediting or double-deduction.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Admin Scrape**: `unauth.collection("admin_users").get()` -> `PERMISSION_DENIED`.
2. **Unauthenticated Public User Enumeration**: `unauth.collection("public_users").get()` -> `PERMISSION_DENIED`.
3. **Unauthenticated PII Deposit Read**: `unauth.collection("deposit_requests").doc("DEP-1").get()` -> `PERMISSION_DENIED`.
4. **Cross-User Isolation Breach**: Authenticated `bob_456` reading `alice_123`'s `admin_users/u1` or `deposit_requests/d1` -> `PERMISSION_DENIED`.
5. **Unverified Admin Email Spoof**: Token `{ email: "devroy58555120@gmail.com", email_verified: false }` attempting to read `alice_123`'s `admin_users` -> `PERMISSION_DENIED`.
6. **Shadow Field Injection**: Creating `admin_users/u1` with extra ghost field `{ isVerified: true }` -> `PERMISSION_DENIED` (`hasOnly` guard).
7. **Unauthenticated Balance Tampering**: Public caller updating `public_users/{publicId}` with `{ balancePaisa: 99999999 }` -> `PERMISSION_DENIED`.
8. **Orphaned Public Deposit**: Submitting `deposit_requests/DEP-1` referencing non-existent `public_users/FAKE_ID` or without `deposit_trx_locks` -> `PERMISSION_DENIED`.
9. **Duplicate bKash Transaction ID Replay**: Submitting a second deposit reusing `TRX123456` -> `PERMISSION_DENIED` (`deposit_trx_locks` rejects overwrite).
10. **Withdrawal Overdraft / Unbacked Hold**: Public caller increasing `reservedPaisa` beyond `balancePaisa` or without atomic `withdrawal_requests` batch -> `PERMISSION_DENIED`.
11. **Terminal State Replay (Double Approval)**: Updating an already `APPROVED` deposit as standard owner -> `PERMISSION_DENIED`.
12. **Resource / ID Poisoning**: Passing 2KB string or invalid characters in document ID or `displayName` -> `PERMISSION_DENIED`.
