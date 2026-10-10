# LearnMate — Current Project Audit

**Last reviewed:** 10 October 2026  
**Purpose:** Record the repository's current implementation status and known limitations. This document is not a substitute for running the build and tests on the current commit.

## Product summary

LearnMate is an Android learning assistant built with Kotlin, Jetpack Compose, Room, local document retrieval, and an optional cloud-AI path. Its goal is to help students study from their own course material and use quiz performance and mastery tracking to guide revision.

## Implemented foundations

- Android UI built with Jetpack Compose and Material 3.
- Local persistence through Room entities and DAOs.
- PDF/text material ingestion, chunk storage, and page/document provenance.
- Local BM25 lexical retrieval with course-aware filtering.
- Query-intent handling for common tutoring requests.
- A deterministic offline tutor that formats explanations from retrieved passages.
- Direct Gemini Developer API routing when connectivity and a valid `GEMINI_API_KEY` allow it; Firebase is not required for this prototype path.
- Quiz evaluation, learner-skill/mastery calculations, personalization, and spaced-repetition logic.
- Android text-to-speech support.
- Unit and Robolectric tests covering key retrieval, tutoring, data, learning behavior, and course/document deletion cleanup.
- Course deletion with confirmation and transactional removal of related local data.
- Document removal with confirmation; generated topics/questions are rebuilt from remaining source documents and stale practice history is cleared.
- Startup cleanup for legacy courses explicitly marked as demo data; fresh installs do not seed sample courses.

## Grounding rule

Retrieved document chunks are the evidence for document-grounded answers. A skill name or description is metadata, not proof that a matching passage was retrieved. When no readable relevant chunk is available, the offline tutor must say so and return no document citation. Source document names and page numbers must come from retrieved chunk metadata.

## Current offline behavior

The base app provides local document search and deterministic, structured tutoring without requiring a neural model. This is not equivalent to a general-purpose offline LLM. Natural-language intent handling is heuristic, and the quality of answers depends on the extracted text and retrieved passages.

## Not implemented or not yet verified as production-ready

- A downloadable, working on-device neural LLM with real model weights and inference.
- Real Gemini API requests are not verified on a physical device in this audit. Online AI requires a valid API key, model access, network connectivity, and available quota.
- Semantic/vector retrieval using embeddings.
- OCR for scanned PDFs and robust extraction of every PDF layout, table, equation, or multi-column page.
- Cloud account synchronization and cross-device profiles.
- Comprehensive real-device testing across Android versions and diverse study documents.

## Known quality limitations

- Offline quizzes currently use four evidence-status choices rather than rich, subject-specific distractors. This prevents empty C/D options and keeps answers tied to the source, but assessment quality still needs improvement.
- Lexical retrieval can miss semantically related passages when the student's wording differs substantially from the source.
- PDF extraction needs validation against real textbooks, lecture notes, scanned files, tables, and malformed documents.
- Imported source files are parsed and their chunks/metadata are stored in Room; the original file bytes are not retained in app-private storage.
- Room currently uses destructive migration fallback, which can erase local learning data after a schema version change.
- A successful Kotlin compile does not establish that all runtime flows work correctly.

## Verification status

The repository contains unit, Robolectric, and emulator journey tests. CI on GitHub Actions is the authoritative check for the current changes; check the latest run before installing a release. A green unit-test and signed-APK build still does not prove every screen and feature works on a real phone. The emulator student-journey workflow has had failures/cancellations in previous runs, so real-device/emulator verification remains outstanding. Verify with CI before sharing a build:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If either command fails, record the actual failure and resolve it before describing the build as verified.

## Next priorities

1. Keep the no-retrieved-evidence behavior covered by regression tests.
2. Run the unit-test suite and debug build on the current commit.
3. Test PDF ingestion and grounded tutoring with real study documents from more than one subject.
4. Improve offline quiz distractors using retrieved course content.
5. Verify direct Gemini API requests on a configured device; for production, move API credentials behind a controlled backend rather than embedding a long-lived secret in the APK.
6. Integrate an on-device inference runtime only after selecting a model, licensing terms, device-memory targets, download integrity checks, and a measurable APK/app-storage strategy.
