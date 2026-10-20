# Firebase and Razorpay setup

Start with the demo before configuring the live variant. Nothing has been deployed to your account.

## 1. Create and connect Firebase

1. Create your own Firebase project in Firebase Console.
2. Add an Android application with package **com.kairos.campus** (no `.demo`). Download `google-services.json` into `Kairos/app/google-services.json`.
3. Enable Authentication → Email/Password. Configure password policy to require at least 8 characters.
4. Create Cloud Firestore (database ID `(default)`) in production mode with location **asia-south1 (Mumbai)**, then enable Firebase Storage. The functions and their Firestore notification triggers run in asia-south1, so the database must be there too.
5. Enable the billing plan required for Cloud Functions, Storage and external payment API access. Review your project's actual quotas and costs.
6. Install Node.js 22 and Firebase CLI on your computer:

```sh
npm install -g firebase-tools
firebase login
firebase use --add
cd functions
npm ci
npm test
cd ..
```

When `firebase use --add` asks, select your own project and alias it `default`.

## 2. Set college domains and payment secrets

Create `functions/.env.YOUR_FIREBASE_PROJECT_ID`:

```text
CAMPUS_DOMAINS=your-actual-approved-student-domain.edu
```

Replace the example with the exact domain your institution approves. Multiple exact domains can be separated with commas. Do not use a broad substring such as `nmims`. Verification plus a matching email domain is required by the callable backend. New Firebase Auth accounts are not campus members until the verified bootstrap transaction succeeds. The domain default intentionally permits no normal student account.

Start with Razorpay **test-mode** keys. They are configured on the backend, never in Kotlin:

```sh
firebase functions:secrets:set RAZORPAY_KEY_ID
firebase functions:secrets:set RAZORPAY_KEY_SECRET
firebase functions:secrets:set RAZORPAY_WEBHOOK_SECRET
```

Use a strong separate webhook secret. Full deployment below expects these secrets. If you only need non-payment live features, deploy explicitly selected functions (`bootstrap,profile,book,session,rate,notice,message,sessionNotification,chatNotification`) and leave payment controls unused; they will report that the functions are unavailable.

```sh
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

Accept the prompted IAM setup needed for Storage rules to consult Firestore. The deployed rules deny direct client mutations to profiles, wallets, ledgers, sessions, ratings and notices. Only server functions can write them.

## 3. Run two real test accounts

1. Sync Android Studio and select **liveDebug**.
2. Run on two devices/emulators.
3. Create two accounts using actual approved college inboxes.
4. Open each verification email, then use **I verified my email** in the app. A verified first bootstrap awards the 120-credit bonus exactly once per Firebase UID.
5. Fill in each profile's name and skills.
6. Account A requests a session with B. B accepts. Each confirms completion on their own device. A's reserved credits are credited to B only once.
7. Test chat, ratings, cancellation and photo upload.

A verified, signed-in account is restored automatically when the app starts. Sign out clears that session. App data appears only after backend authorization.

## 4. Razorpay test checkout

- Enable automatic capture in your Razorpay test configuration.
- Wallet → Buy / resume creates a server order for exactly ₹100 and opens Checkout.
- Never trust the Android success callback alone. `verifyPayment` validates the order owner, signature, captured status, amount and currency before crediting.
- Configure the deployed `paymentWebhook` URL from the Firebase deploy output in Razorpay Dashboard. Subscribe to **payment.captured** and set the same webhook secret.
- If the app closes during checkout, the webhook settles the captured payment. **Recover pending payment** also checks the provider directly.
- Repeated success callbacks and webhook deliveries do not credit the same order twice.
- A pending monthly slot reuses its order. There are two slots per UTC **order-creation month**. That policy is deliberately explicit: an old pending order may settle in a later month. If you need a cap based on payment month, implement order expiry/refund handling before going live.
- An ambiguous provider/network error during order creation leaves the slot in `creating` state. An administrator must reconcile it using the provider receipt and notes before unlocking it. Do not blindly delete it and generate another payable order.
- Refunds, chargebacks, disputes and automatic bank transfers are not implemented. The Campus Skill Fund is an accounting counter only.

## 5. FCM and photos

The app requests notification permission on Android 13+. Allow it. The backend sends notifications for session state changes and new chat messages. One token is stored per account, so the most recently registered device receives alerts. Sign out/in after reinstalling if alerts do not appear.

Photos use the system document picker and Firebase Storage (maximum 5 MB). Profile photo download URLs are capability URLs: anyone with a copied URL may view the image. Use an authenticated image loading design if strict image confidentiality is required.

## Before real campus deployment

Run the two-device acceptance checklist. Add App Check (debug provider locally, Play Integrity for release) before enabling enforcement. Add abuse controls, moderation, account deletion, privacy/retention controls, dispute resolution and an admin panel. Review real-money and campus policy requirements with the institution. Keep API secrets and service account keys out of Git. Use a staging Firebase project and test payment credentials until reviewed.
