# Test results — 20 October 2026

Environment: Windows 11, JDK 21, Android SDK 35, Gradle 9.3.1 (wrapper), Node.js 24.

| Check | Command | Result |
|---|---|---|
| Android unit tests (SkillMatcher) | `gradlew :app:testDemoDebugUnitTest` | 4 tests, 0 failures |
| Demo APK build | `gradlew :app:assembleDemoDebug` | BUILD SUCCESSFUL |
| Live flavor compiles | `gradlew :app:compileLiveDebugKotlin` | BUILD SUCCESSFUL |
| Backend policy tests | `cd functions && npm test` | 5 tests, 0 failures |
| Backend syntax check | `cd functions && npm run check` | passed |
| Demo app on emulator | API 34 x86_64 emulator, `demoDebug` APK | ran; see `screenshots/` |

Android unit tests: inheritance maps to Java, unknown skills return no match, filler words do not match bios, DSA and ML queries match the AI/ML mentor.

Backend tests: duration validation, only the mentor accepts, two distinct confirmations settle once, no cancel after a confirmation, pending requests cancel but cannot complete.

Not run: Firebase deployment, Firestore/Storage rules emulator, two-device live tests, real Razorpay payments. See `TESTING.md` for the live acceptance checklist.
