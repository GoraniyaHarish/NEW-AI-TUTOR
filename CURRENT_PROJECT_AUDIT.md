# LearnMate — Current Project Audit

**Last reviewed:** 9 October 2026  
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
- Cloud AI routing when connectivity and configuration allow it.
- Quiz evaluation, learner-skill/mastery calculations, personalization, and spaced-repetition logic.
- Android text-to-speech support.
- Unit and Robolectric tests covering key retrieval, tutoring, data, and learning behavior.

## Grounding rule

Retrieved document chunks are the evidence for document-grounded answers. A skill name or description is metadata, not proof that a matching passage was retrieved. When no readable relevant chunk is available, the offline tutor must say so and return no document citation. Source document names and page numbers must come from retrieved chunk metadata.

## Current offline behavior

The base app provides local document search and deterministic, structured tutoring without requiring a neural model. This is not equivalent to a general-purpose offline LLM. Natural-language intent handling is heuristic, and the quality of answers depends on the extracted text and retrieved passages.

## Not implemented or not yet verified as production-ready

- A downloadable, working on-device neural LLM with real model weights and inference.
- Firebase AI Logic cloud requests are not verified in this checkout because `app/google-services.json` and a Firebase AI Logic project configuration are absent. The app uses Firebase AI Logic + App Check and contains no Gemini Developer API key path.
- Semantic/vector retrieval using embeddings.
- OCR for scanned PDFs and robust extraction of every PDF layout, table, equation, or multi-column page.
- Cloud account synchronization and cross-device profiles.
- Comprehensive real-device testing across Android versions and diverse study documents.

## Known quality limitations

- Offline quiz distractors are deterministic placeholders rather than a mature assessment-generation system.
- Lexical retrieval can miss semantically related passages when the student's wording differs substantially from the source.
- PDF extraction needs validation against real textbooks, lecture notes, scanned files, tables, and malformed documents.
- Imported source files are parsed and their chunks/metadata are stored in Room; the original file bytes are not retained in app-private storage.
- Room currently uses destructive migration fallback, which can erase local learning data after a schema version change.
- A successful Kotlin compile does not establish that all runtime flows work correctly.

## Verification status

The repository contains unit, Robolectric, and emulator journey tests. The latest local audit on 9 October 2026 found that AAPT2 exits unexpectedly during Android resource linking on this Windows host. Kotlin main-source compilation succeeds when the failed resource-link task is excluded; unit tests and APK assembly have therefore not completed in this environment. Verify on CI before sharing a build:

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
5. Configure Firebase AI Logic, register Play Integrity, and verify real cloud requests on a configured app/device.
6. Integrate an on-device inference runtime only after selecting a model, licensing terms, device-memory targets, download integrity checks, and a measurable APK/app-storage strategy.
