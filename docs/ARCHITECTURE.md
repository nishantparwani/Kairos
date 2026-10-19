# Architecture and data model

## Layers

Compose screens → KairosViewModel → Repository interface.
DemoRepository persists an AppData snapshot and chat messages in local SharedPreferences.
FirebaseRepository uses Firebase Auth, Firestore snapshots, Storage and callable functions.
Cloud Functions authorize identity and own all credit mutations. Firestore transactions serialize competing balance/settlement operations.

## Collections

| Path | Contents | Client access |
|---|---|---|
| profiles/{uid} | Public campus profile, skills, availability, photo, ratings aggregate | Campus members read; callable writes |
| wallets/{uid} | Available credit balance | Owner reads; server writes |
| wallets/{uid}/ledger/{entry} | Signed credit changes with timestamp and label | Owner reads; server writes |
| sessions/{id} | Learner, mentor, members, time, duration, status, confirmations, rated IDs | Participants read; server writes |
| sessions/{id}/messages/{id} | Sender, text, server-assigned timestamp | Participants read; callable writes |
| sessions/{id}/ratings/{uid} | Stars and recipient | Participants read; server writes |
| notices/{id} | Campus post title, body and author | Members read; callable writes |
| tokens/{uid} | FCM registration token | Owner only |
| orders/{providerOrderId} | Order owner, amount, month, settlement state | Server only |
| paymentSlots/{uid_month_slot} | Reusable order slot and status | Server only |
| purchaseQuota/{uid_month} | Settled slot count | Server only |
| public/fund | Recorded fund allocation in paise | Members read; server writes |

No email addresses are copied into public profile documents. All documents default to denied access unless explicitly permitted by rules. New accounts receive a profile and wallet atomically from bootstrap. Existing profiles remain members for reads even if a domain is later removed from the allow-list; remove/block those memberships explicitly when revoking access.

## Session state machine

`requested → accepted → completed`

- Booking atomically deducts credits from available balance; the session represents escrow.
- The mentor alone accepts the request.
- Either participant can cancel a requested session, or an accepted session with zero completion confirmations. The learner is refunded once.
- Completion requires two distinct participant UIDs.
- The second confirmation transfers the reserved amount to the mentor in the same transaction that marks the session complete.
- Duplicate confirmations are idempotent. A transaction retry cannot pay twice.
- After any completion confirmation, a dispute needs administrator intervention; the app does not allow unilateral cancellation.
- Each participant can rate the other once after completion. Aggregates are updated transactionally.

Credit conservation: aside from explicitly minted joining/purchased credits, available learner + available mentor + active escrow remains constant. The fund counter is denominated in paise and is separate from time credits.

## Backend callables

`bootstrap`, `profile`, `book`, `session`, `rate`, `notice`, `message`, `createOrder`, `verifyPayment`, `reconcilePayment`.
All require a verified Firebase identity with an exact approved domain. Server time and prices are authoritative. Clients cannot choose the wallet to credit or the purchase amount. Secrets stay in Firebase secret storage.

## Matching limitations

SkillMatcher tokenizes the query, expands a small synonym dictionary (e.g. inheritance → Java), scores skill/bio matches and breaks ties by average rating. This is explainable rule-based matching, not generative AI. Availability is displayed for human coordination and is not used as a verified schedule constraint.

## Scale and reliability limitations

Profiles and participant sessions currently use unpaginated listeners for a campus pilot. Chat and ledger show 100 entries; notices show the latest 100. Live mutations require network access and use visible errors rather than optimistic credit changes. Booking retry after an unknown network outcome can create another request; inspect Sessions before retrying. Production work should add caller-supplied idempotency keys to booking and posting, pagination, rate limits, moderation and scheduled expiry/refund jobs.
