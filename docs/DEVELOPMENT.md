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
