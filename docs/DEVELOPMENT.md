# LearnMate Developer Guide & Build Verification

## 1. Prerequisites
- **JDK**: Java 17 or Java 21 (Temurin JDK 21 recommended)
- **Android SDK**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`
- **Gradle**: 9.3.1 (configured via included Gradle wrapper)

## 2. Environment Setup

Cloud AI is optional. To enable it, configure a Firebase project for Firebase AI Logic and App Check, then place its `google-services.json` in `app/`. Debug builds use the App Check debug provider; launch the app, copy the debug token from Logcat, and register it under App Check > your Android app > Manage debug tokens in Firebase Console. Never share or commit that token. Release builds use Play Integrity and must be distributed through a channel that satisfies the Play Integrity settings configured in Firebase. Do not add a Gemini Developer API key to the app, `.env`, Gradle, or BuildConfig. Local document ingestion, BM25 indexing, quizzes, mastery tracking, and deterministic offline tutoring do not require Firebase configuration.

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
