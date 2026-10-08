# LearnMate Architecture & System Design

## 1. System Architecture Diagram

```
+--------------------------------------------------------------------------+
|                            USER INTERFACE                                |
|           Jetpack Compose M3 UI (Dynamic Dark / Light Themes)            |
|                                                                          |
| [Home / Dashboard]  [Upload Materials]  [Tutor Chat]  [Quizzes / Mastery] |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                             VIEWMODEL                                    |
|         MainViewModel (Unidirectional StateFlow, Coroutines)            |
+--------------------------------------------------------------------------+
          |                                      |
          v (Local Learning Logic)               v (Knowledge & AI)
+---------------------------------------+  +-------------------------------+
|           LEARNING ENGINE             |  |      INGESTION & RETRIEVAL    |
| - MasteryCalculator (Weighted Score)  |  | - PdfTextExtractor (PdfBox)   |
| - SpacedRepetitionEngine (SuperMemo-2)|  | - TextChunker (Sliding window)|
| - QuizEvaluator (Rubric & Breakdown)  |  | - BM25OkapiRetriever (Cosine/ |
| - PersonalizationEngine (Prereq Graph)|  |   BM25 Term Frequency)        |
+---------------------------------------+  +-------------------------------+
          |                                              |
          v                                              v
+--------------------------------------------------------------------------+
|                              DATA LAYER                                  |
|          Room SQLite Database (Local Entities & DAOs)                    |
| - DocumentEntity, ChunkEntity, CourseEntity                              |
| - SkillMasteryEntity, QuizResultEntity, StudyItemEntity                  |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                             TUTOR ENGINE                                 |
|                                                                          |
|  [Network Available + Valid Key]           [Offline / Key Missing]       |
|                 |                                      |                 |
|                 v                                      v                 |
|       CloudAIService (Gemini)              DeterministicTutorEngine      |
|  - Grounded prompt injection             - Grounded chunk summarization  |
|  - Source provenance validation          - Keyword-matched extraction    |
|  - Anti-hallucination guardrails         - Zero network required         |
+--------------------------------------------------------------------------+
```

## 2. Component Breakdown

### 2.1 UI Layer
- Built 100% in modern **Jetpack Compose** with Material Design 3.
- Implements single-activity architecture (`MainActivity`) driven by `MainViewModel`.
- Reactive state updates using Kotlin Coroutines and `StateFlow`.

### 2.2 Ingestion & Indexing Engine
- **PdfTextExtractor**: Local PDF parsing and text extraction powered by Apache PdfBox-Android.
- **TextChunker**: Splits raw document text into semantically cohesive, overlapping windows (e.g. 500-1000 characters) preserving source document ID, filename, and page numbers.
- **Room Persistence**: Stores raw documents and pre-indexed chunks locally in SQLite.

### 2.3 Retrieval Layer (Local BM25)
- **LocalRetriever / BM25Okapi**: Fast on-device lexical search calculating term frequency-inverse document frequency (TF-IDF) and Okapi BM25 ranking across local chunks.
- Completely runs on-device in under 50ms without external server dependencies.

### 2.4 Hybrid Tutoring Engine
- **CloudAIService (Gemini)**:
  - Generates conversational Socratic explanations, quizzes, and learning tips when connected.
  - Injects retrieved local document chunks into the prompt context with strict citation instructions.
  - Validates source provenance to prevent hallucinated citations.
- **DeterministicTutorEngine (Offline Fallback)**:
  - When offline or without an API key, extracts and organizes key knowledge points directly from retrieved chunks.
  - Provides reliable, verifiable study summaries without fabricated answers.

### 2.5 Mastery & Adaptation Engine
- **MasteryCalculator**: Updates continuous mastery scores based on diagnostic and practice quiz outcomes.
- **SpacedRepetitionEngine**: Implements an SM-2 algorithmic scheduler computing optimal review intervals and ease factors.
- **PersonalizationEngine**: Evaluates prerequisite trees to recommend the highest-leverage next study topic.
