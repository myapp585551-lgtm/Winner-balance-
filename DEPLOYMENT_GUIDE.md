# WINNER BALANCE — Cloud Firestore, Public Routing & Operations Guide

## Architecture Overview

**Winner Balance** operates across both **Native Android** (`app/`) and the **Public Web Portal** (`web/`), backed by **Firebase Authentication (Google Sign-In)** and **Firebase Cloud Firestore**:

- **Firebase Project ID**: `gen-lang-client-0530252854`
- **Named Firestore Database ID**: `ai-studio-android-winnerba-3979b9d3-2b97-4683-a1fe-d975aa7e02e4`
- **Android Application ID**: `com.aistudio.winnerbalance.wbkpxq`
- **Deployed Shared App URL**: `https://ais-pre-clxmtq4b6kwszyihq4tu6n-190436024907.asia-southeast1.run.app`
- **Verified Public User Link**: `https://ais-pre-clxmtq4b6kwszyihq4tu6n-190436024907.asia-southeast1.run.app/?u=WB7x9Km2Pq4Rt8Vz3N`

---

## Key Capabilities

1. **Public User Access Without Admin Login (`?u=UNIQUE_PUBLIC_ID`)**:
   - Every user gets a cryptographically random `publicId` link (`/?u=<publicId>`).
   - Opening the link in Chrome, Firefox, Edge, Safari, Android browsers, or the native Android app reads the `u` query parameter (`?u=`) and loads the user's live record from `/public_users/{publicId}` in Firebase Cloud Firestore without requiring administrator login.
   - Direct navigation and page refreshes return `HTTP 200 OK` (`web/server.js` + `404.html` SPA fallback) so no `404 Not Found` occurs.
2. **Administrator Authentication (Google Sign-In)**:
   - Administrators sign in via Jetpack `CredentialManager` (`Sign in with Google`) in the Android app.
   - Zero-trust `firestore.rules` enforce authenticated `ownerUid` isolation on `/admin_users`, `/deposit_requests`, `/withdrawal_requests`, `/ledger_entries`, and `/site_settings/global` mutations, while keeping pencil media-edit buttons hidden from public users.
3. **Atomic Financial Integrity**:
   - **Deposit (bKash Only)**: Public visitors copy the official bKash number and submit Amount (BDT), Sender's bKash Number, and Transaction ID. Duplicate Transaction IDs are blocked via `/deposit_trx_locks/{trxId}`. Balance increases **once** only when an administrator marks the deposit `Approved`.
   - **Withdraw (Bank Account Only)**: Public visitors submit Amount (BDT), Bank Name, Account Holder Name, and Bank Account Number. Pending requests atomically reserve balance (`reservedPaisa`); approving deducts **once**, and rejecting releases the reservation.
4. **Thailand Timezone & Online-Only Enforcement**:
   - All live clocks and transaction timestamps format exclusively in `Asia/Bangkok` (`ICT`, UTC+07:00).
   - Offline mutations are blocked immediately with `"Internet connection required."`

---

## Publishing / Updating the Shared Cloud Run Snapshot

- Inside the workspace container, `node web/server.js` serves the public web portal on port `3000` (proxied on port `8080`).
- Whenever web or routing changes are made in AI Studio, click **Share / Deploy** in the AI Studio top bar to publish the latest container snapshot to `https://ais-pre-clxmtq4b6kwszyihq4tu6n-190436024907.asia-southeast1.run.app`.
