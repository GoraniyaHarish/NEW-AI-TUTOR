# LearnMate

> **Offline-first personalized learning assistant that turns a student's own study materials into grounded tutoring, adaptive mastery tracking, and personalized study plans.**

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Room-SQLite-276DC3.svg)](https://developer.android.com/training/data-storage/room)
[![Build](https://img.shields.io/badge/Gradle-9.3.1-02303A.svg?logo=gradle&logoColor=white)](https://gradle.org/)

---

## Download and Try LearnMate

**Want to install the Android app without building it yourself?**

- **[Download the latest APK](https://github.com/GoraniyaHarish/NEW-AI-TUTOR/releases/latest/download/app-debug.apk)** — download the APK to an Android device and open it to install.
- **[View all releases](https://github.com/GoraniyaHarish/NEW-AI-TUTOR/releases)** — see available builds and release notes.
- **[Check build and test status](https://github.com/GoraniyaHarish/NEW-AI-TUTOR/actions/workflows/android.yml)** — the APK is published automatically after the main-branch CI build and tests succeed.

> **Testing build:** The downloadable APK is a debug build intended for demonstrations and testing, not a Play Store production release. Android may ask you to allow installation from your browser or file manager. Only install APKs you trust. If the download link is not available yet, check the Actions page for the latest build status.

---

## Why LearnMate?

Students often face major challenges with generic online learning tools:
- **No Direct Grounding in Course Materials**: Generic chatbots hallucinate concepts that contradict course lecture notes or textbooks.
- **Connectivity Gaps**: Many students lack continuous high-speed internet while commuting or studying in areas with intermittent access.
- **One-Size-Fits-All Pace**: Standard study guides do not adapt dynamically to individual weak spots or prerequisite gaps.
- **Privacy Concerns**: Students are often required to upload private school documents and study materials to unvetted cloud servers.

LearnMate solves these problems with an **offline-first, document-grounded architecture** that runs core indexing, retrieval, assessment, and tutoring locally on Android.

---

## What LearnMate Does

LearnMate powers a closed-loop personalized learning cycle:

```
Student Material (PDF / Text)
       ↓
Local Ingestion & Chunking (PdfBox-Android)
       ↓
Local Knowledge Base (Room SQLite)
       ↓
Local BM25 Retrieval
       ↓
Grounded Tutor (Offline Deterministic / Online Gemini)
       ↓
Interactive Assessment & Quizzes
       ↓
Skill Mastery Tracking (SM-2 Spaced Repetition)
       ↓
Personalized Next Step & Study Path
```

---

## Key Features

- **Document Ingestion**: Parse PDF and text notes locally into structured, searchable text chunks preserving source file and page metadata.
- **BM25 Local Retrieval**: Fast on-device lexical search ranking relevant study chunks without server round-trips.
- **Grounded AI Tutoring**: Socratic responses grounded strictly in the student's study material, preventing fabricated citations.
- **Interactive Quizzes**: Diagnostic and practice assessments with automated scoring and instant feedback.
- **Skill Mastery Tracking**: Continuous mastery metrics derived from quiz performance and knowledge retention.
- **Adaptive Study Plans**: Prerequisite tree evaluation recommending the highest-priority topics.
- **Spaced Repetition Scheduler**: SuperMemo-2 (SM-2) algorithm determining review intervals and ease factors.
- **Text-to-Speech (TTS)**: Built-in Android TTS support for audio-assisted learning.
- **Offline-First Resilience**: Local document search, deterministic tutoring, assessment, mastery tracking, and review scheduling are designed to work without a network connection.

---

## Offline vs Online Capabilities

| Capability | Offline (Local Device) | Online (Connected + Key) |
| :--- | :---: | :---: |
| **Material Ingestion & PDF Extraction** | **Full** (Local Room & PdfBox) | **Full** |
| **BM25 Search & Chunk Indexing** | **Full** (<50ms on-device) | **Full** |
| **Quiz Generation & Evaluation** | **Full** (Deterministic engine) | **Enhanced** (Dynamic generation) |
| **Mastery & Prerequisite Tracking** | **Full** (Local database) | **Full** |
| **Spaced Repetition (SM-2)** | **Full** (Local scheduler) | **Full** |
| **Document-Grounded Tutoring** | **Full** (Grounded chunk extracts) | **Enhanced** (Gemini Socratic dialog) |
| **On-Device Neural Model** | *Deferred / In Roadmap* | N/A |

> **Note on Local Neural Model**: While BM25 search, document indexing, quiz evaluation, mastery tracking, and deterministic tutoring run 100% locally on-device, large-parameter neural LLM inference (e.g. MediaPipe / On-device SLMs) is architectural and planned for future iterations.

---

## Grounding & Trust

LearnMate strictly enforces document provenance:
1. **Query Analysis**: The app identifies the likely tutoring intent and useful query terms.
2. **Context Retrieval**: BM25 ranks chunks from the selected course materials.
3. **Grounded Response**: Offline tutoring uses retrieved text; if no readable passage is found, it reports that limitation rather than treating skill metadata as evidence.
4. **Provenance**: Document name and page citations are taken from retrieved chunk metadata.

---

## Adaptive Learning System

LearnMate replaces static flashcards with dynamic adaptation:
- **Prerequisite Graphs**: Pinpoints foundational gaps before advancing to complex topics.
- **Continuous Mastery Scoring**: Scores reflect recent quiz results with decay modeling for unrevisited concepts.
- **SM-2 Algorithm**: Calculates optimal review intervals based on difficulty and recall accuracy.

---

## System Architecture

```
+-------------------------------------------------------------+
|                      Jetpack Compose UI                     |
|           (Material 3, Dark/Light Mode, Touch-First)        |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                        MainViewModel                        |
|             (Kotlin Coroutines, Reactive StateFlow)         |
+-------------------------------------------------------------+
           |                                       |
           v                                       v
+-----------------------+              +----------------------+
|    Learning Engine    |              | Ingestion & Retrieval|
| - MasteryCalculator   |              | - PdfTextExtractor   |
| - SpacedRepetition    |              | - TextChunker        |
| - QuizEvaluator       |              | - BM25OkapiRetriever |
| - Personalization     |              |                      |
+-----------------------+              +----------------------+
           |                                       |
           +-------------------+-------------------+
                               |
                               v
+-------------------------------------------------------------+
|                     Room SQLite Database                    |
|          (Documents, Chunks, Mastery, Quizzes, Items)       |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                       Tutor Provider                        |
|      [Online: Gemini API]  |  [Offline: Deterministic]      |
+-------------------------------------------------------------+
```

---

## Quickstart & Reproducibility

### Prerequisites
- JDK 17 or JDK 21 (Temurin JDK 21 recommended)
- Android SDK (API Level 26–35)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/GoraniyaHarish/NEW-AI-TUTOR.git
cd NEW-AI-TUTOR

# Copy example environment configuration
cp .env.example .env

# Verify Gradle wrapper
./gradlew --version

# Run unit and Robolectric tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`. Verify build and test results on the current checkout before reporting them as passing.

---

## Project Structure

```
NEW-AI-TUTOR/
├── app/                  # Android application module
│   ├── src/main/java/    # Kotlin Jetpack Compose and architecture source
│   ├── src/test/java/    # Unit and Robolectric tests
│   └── build.gradle.kts  # App-level build configuration
├── docs/                 # Authoritative project documentation
│   ├── ARCHITECTURE.md   # System design and component interactions
│   ├── DEVELOPMENT.md    # Build instructions, test verification, and setup
│   ├── PROJECT_DEFINITION.md
│   ├── AI_ENGINEERING_RULES.md
│   └── CURRENT_PROJECT_AUDIT.md
├── gradle/               # Gradle wrapper and version catalog
│   ├── libs.versions.toml
│   └── wrapper/
├── .env.example          # Environment variable template
├── .gitignore            # Git exclusion rules
├── build.gradle.kts      # Root build configuration
├── settings.gradle.kts   # Root settings configuration
├── gradlew               # Gradle wrapper executable
└── README.md             # Project overview and quickstart
```
