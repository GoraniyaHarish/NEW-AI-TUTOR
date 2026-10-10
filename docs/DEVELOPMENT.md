# LearnMate Developer Guide & Build Verification

## 1. Prerequisites
- **JDK**: Java 17 or Java 21 (Temurin JDK 21 recommended)
- **Android SDK**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`
- **Gradle**: 9.3.1 (configured via included Gradle wrapper)

## 2. Environment Setup

Online AI uses the direct Gemini Developer API; Firebase and `google-services.json` are not required for the prototype. Set `GEMINI_API_KEY` in an untracked local `local.properties` file or as an environment/Gradle property for local builds. For GitHub Actions, add the repository secret `GEMINI_API_KEY`. The key is compiled into the prototype APK and can be extracted, so use a restricted test key with quota limits and rotate it after testing; do not use this approach for a public production release. A backend proxy is the safer production design. Configure `ANDROID_KEYSTORE_BASE64` (base64-encoded persistent `.jks` file), `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` for production-signed GitHub releases. Generate the keystore once and back it up securely; never commit it or share it in chat. Local document ingestion, BM25 indexing, quizzes, mastery tracking, and deterministic offline tutoring work without any API key.

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
5. In Google AI Studio, create a restricted Gemini API key for prototype testing and set it as the GitHub Actions secret `GEMINI_API_KEY`. Do not commit it. Remember that keys embedded in APKs can be extracted; use a backend proxy before public production distribution.

After these one-time steps, pushes to `main` run unit tests and build the APK. If all signing secrets are valid, GitHub Releases publishes `learnmate.apk` as a production-signed APK. The workflow fails if signing secrets are absent or incomplete rather than publishing an APK with an unexpected signature. If `GEMINI_API_KEY` is absent, local/offline features still build but online tutoring will fall back to the local tutor. CI increments the Android version code per workflow run.

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
