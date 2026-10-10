# LearnMate Developer Guide & Build Verification

## 1. Prerequisites
- **JDK**: Java 17 or Java 21 (Temurin JDK 21 recommended)
- **Android SDK**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`
- **Gradle**: 9.3.1 (configured via included Gradle wrapper)

## 2. Environment Setup

Cloud AI is optional. Configure Firebase AI Logic and App Check in Firebase, then store the complete `google-services.json` contents as the GitHub Actions secret `GOOGLE_SERVICES_JSON`. Debug builds use the debug App Check provider for development only; never distribute debug builds to students or commit debug tokens. Public GitHub APKs should be signed with one persistent release keystore and use Play Integrity App Check. Configure these GitHub Actions secrets: `ANDROID_KEYSTORE_BASE64` (base64-encoded persistent `.jks` file), `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Generate the keystore once and back it up securely; never generate a new key per build, commit it, or share it in chat. The release job only publishes a production-signed APK when all signing secrets are present; otherwise CI publishes a clearly labeled debug/testing APK. Register the release signing certificate's SHA-256 fingerprint in Firebase App Check. For GitHub-only distribution, configure Play Integrity's advanced settings for distribution outside Google Play: `PLAY_RECOGNIZED` and `LICENSED` not required, with the recommended device-integrity setting. Link the Play Integrity API to the same Google Cloud/Firebase project through Play Console. Do not add a Gemini Developer API key to the app, `.env`, Gradle, or BuildConfig. Local document ingestion, BM25 indexing, quizzes, mastery tracking, and deterministic offline tutoring do not require Firebase configuration.

## 3. Building the Application

Using the Gradle wrapper:

```bash
# Verify Gradle wrapper setup
./gradlew --version

# Run local unit tests (100% passing across 13 test suites)
./gradlew :app:testDebugUnitTest

# Assemble debug APK
./gradlew :app:assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## 4. One-time setup for public, signed GitHub APKs

1. On your trusted Windows development PC, run this in PowerShell from a private folder (with JDK/keytool installed). Choose a strong keystore password and key password when prompted:
   ```powershell
   keytool -genkeypair -v -keystore learnmate-release.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Back up `learnmate-release.jks` and its passwords somewhere secure. Losing the key means you cannot sign compatible updates to this APK. Never commit the keystore or send it in chat.
3. Print the certificate fingerprint and copy the SHA-256 value:
   ```powershell
   keytool -list -v -keystore learnmate-release.jks -alias upload
   ```
4. In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add:
   - `ANDROID_KEYSTORE_BASE64`: run the following in PowerShell and copy the output (keep the keystore private):
     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\learnmate-release.jks")) | Set-Clipboard
     ```
   - `ANDROID_STORE_PASSWORD`: the keystore password
   - `ANDROID_KEY_ALIAS`: `upload` (or the alias you chose)
   - `ANDROID_KEY_PASSWORD`: the key password
   - `GOOGLE_SERVICES_JSON`: the complete contents of the Firebase Android app's `google-services.json`, package name `com.learnmate.app`
5. In Firebase Console → **Security → App Check → Apps**, register the app with **Play Integrity** and add the release certificate's SHA-256 fingerprint. In Google Play Console, link the Play Integrity API to the same Google Cloud/Firebase project. For an app distributed exclusively outside Google Play, configure the Play Integrity advanced settings accordingly: do not require `PLAY_RECOGNIZED` or `LICENSED`; use the recommended device-integrity requirement.
6. Ensure Firebase Console → **AI Logic** is configured for the intended Gemini backend and App Check is enabled for Firebase AI Logic.

After these one-time steps, pushes to `main` run unit tests and build the APK. If all signing secrets are valid, GitHub Releases publishes `learnmate.apk` as a production-signed APK; the workflow deliberately falls back to a debug/testing APK if signing secrets are completely absent. A partially configured signing setup fails rather than silently publishing an incorrectly signed release. CI increments the Android version code per workflow run.

**Important for the first transition:** the old debug APK and the production-signed APK have different signing certificates. Android will not install the production APK as an in-place update over the debug APK. Back up any local study data and uninstall the old debug build once before installing the first signed release. Later releases signed with the same keystore can update normally.

## 4. Test Suite Coverage

The project includes thorough JVM unit and Robolectric tests covering all core systems:
- `MasteryCalculatorTest.kt`: Mastery scoring, decay, and updates.
- `QuizEvaluatorTest.kt`: Scoring accuracy, feedback, and rubric verification.
- `SpacedRepetitionEngineTest.kt`: SM-2 algorithm, intervals, ease factors.
- `PersonalizationEngineTest.kt`: Prerequisite dependency resolution.
- `LocalRetrieverTest.kt`: BM25 chunk retrieval and ranking.
- `TutorFoundationTest.kt`: Deterministic offline tutor and prompt grounding.
- `PdfTextExtractorTest.kt`: PDF document parsing and text extraction.
- `RoomDatabaseTest.kt`: Document and chunk database operations.
- `MainViewModelTest.kt`: UI state management and asynchronous flows.

Run tests with details:
```bash
./gradlew :app:testDebugUnitTest --info
```
