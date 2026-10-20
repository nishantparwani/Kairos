# Verification and acceptance

## Automated checks

- Backend syntax: `npm run check`.
- Backend module load: `node -e "require('./index.js')"` with installed dependencies.
- Backend policy tests: `npm test` (5 tests).
- Android compilation and JVM tests: see BUILD_VERIFICATION.txt in the project root for the actual outcome.

Policy tests cover invalid durations, unauthorized/learner acceptance, two distinct confirmations, duplicate completion idempotency, and cancellation restrictions. Android JVM tests cover synonym matching and irrelevant-query filtering. These are **not** substitutes for Firebase transaction, rules emulator or two-device end-to-end tests.

## Required live acceptance tests (not run against your accounts)

1. An unverified user cannot bootstrap a profile or read campus data.
2. A verified email outside the configured exact domains cannot bootstrap.
3. Calling bootstrap twice grants one joining bonus, including concurrent calls.
4. Direct Firestore writes to wallets, ledgers, sessions and rating sums are denied.
5. An unrelated user cannot read a session, its messages or another wallet.
6. Two requests competing for the final balance do not overspend.
7. Only the mentor accepts a request; neither participant can complete before acceptance.
8. Repeated completion calls by one participant do not settle. Two distinct participants settle once.
9. Cancelling refunds once, and cannot refund after partial/full completion.
10. Duplicate rating attempts are denied and aggregates remain correct.
11. Chat appears on both devices, respects participation and survives reconnect.
12. A failed payment, forged signature, wrong order owner, wrong amount or uncaptured payment adds no credits.
13. Callback plus webhook replay credits only once; recovery works after the app closes.
14. The third monthly order slot is rejected. Validate the documented order-creation-month policy.
15. Photo uploads reject oversized/non-image objects and other users' paths.
16. Notifications arrive with permissions granted and expose no chat body in their preview.
17. Rotation, keyboard display, dark mode, large font size and back navigation are usable on a device.
18. Test low-balance, empty marketplace, network failure and pending payment states.

## Demo reset

Android Settings → Apps → Kairos → Storage → Clear storage. This resets the sample state and local purchase counter. It has no effect on a live Firebase account.

## Release gates

Before a public release, run rules/emulator and real device tests, implement moderation/disputes and account deletion, configure App Check/rate limits, review campus and payment policy, and test a signed release build. No live deployment, real payment or production certification is claimed by this source package.
