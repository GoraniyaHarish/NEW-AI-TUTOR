# PROJECT DEFINITION — LEARNMATETM
*AI-Powered Student Learning & Skill Development Platform*  
*Target OS: Android | Framework: Kotlin + Jetpack Compose*  
*Status: Permanent Architecture & Product Specification*

---

## 1. PRODUCT OVERVIEW

**LearnMate** is an offline-first, AI-powered student learning and skill development Android application. 

Instead of relying on generic web search or static pre-packaged textbooks, LearnMate transforms a student's **own educational materials** (lecture slides, syllabi, PDF notes, reference documents, and problem sets) into a structured **skill mastery graph**, adaptive diagnostic assessments, grounded AI tutoring, and personalized spaced-repetition study paths.

LearnMate is built to function both **online** (leveraging frontier cloud AI models like Gemini) and **offline** (leveraging on-device retrieval and optional downloadable local models), ensuring learning is uninterrupted by poor connectivity or data costs.

---

## 2. PROBLEM BEING SOLVED

1. **Information Overload Without Structure**: Students are inundated with hundreds of pages of unorganized PDFs, lecture notes, and slides, without understanding which core concepts and prerequisites are required for mastery.
2. **Generic AI Hallucinations & Disconnection**: Standard AI chatbots lack grounding in the student's actual course syllabus, frequently hallucinating concepts or using conflicting notation, foreign terminology, or out-of-scope methods.
3. **Connectivity Fragility**: Students studying in transit, rural areas, or high-cost mobile environments lose access to cloud-only learning assistants.
4. **Passive Reading vs. Active Mastery**: Merely reading notes produces an illusion of competence; students need automated diagnostic tests, prerequisite gap detection, and spaced practice.
5. **Privacy Concerns**: Educational coursework, proprietary lectures, and student performance metrics should not be unnecessarily leaked to third parties without consent.

---

## 3. TARGET USERS

* **University & College Students**: Managing complex STEM and humanities courses with heavy document loads.
* **High School Students**: Preparing for standardized exams and foundational coursework.
* **Independent & Professional Learners**: Mastering technical skills from documentation, whitepapers, and textbooks.
* **Offline & Commuter Learners**: Students who study in network-constrained environments.

---

## 4. PRODUCT VISION

To provide every student with a private, personalized tutor that lives on their phone, completely understands their specific syllabus and course notes, diagnoses knowledge gaps with mathematical precision, and guides them step-by-step from confusion to mastery.

---

## 5. TARGET PLATFORM & TECHNICAL FOUNDATION

* **Operating System**: Android (minSdk 24, targetSdk 36)
* **Programming Language**: 100% Kotlin with strict null-safety and Coroutines/Flow
* **UI Toolkit**: Jetpack Compose adhering to Material Design 3 (M3)
* **Local Database**: Room Database (SQLite engine) with typed relational DAOs
* **Architecture**: Clean Architecture / MVVM with decoupled AI and data layers
* **Distribution & Deployment**: Standalone Android APK / App Bundle

---

## 6. CORE FEATURES

1. **Course & Material Management**:
   * Create custom courses for distinct subjects.
   * Import student materials (PDFs, plain text notes, markdown, syllabi).
   * Document metadata extraction, page indexing, and secure local file storage.
2. **Automated Skill Graph Construction**:
   * Extracts granular skills, chapters, and topics directly from student content.
   * Identifies prerequisite dependencies between skills (Directed Acyclic Graph).
   * Visualizes interactive skill maps with color-coded mastery states.
3. **Adaptive Diagnostic Assessments**:
   * Evaluates student understanding before or after study sessions.
   * Multi-choice questions grounded in the student's specific notes.
   * Tracks hints used, time spent, and response correctness.
4. **Mathematical Mastery Engine**:
   * Multi-factor mastery score (0–100%) incorporating difficulty weight, hint penalties, streak bonuses, and exponential moving average (EMA) blending.
   * Categorizes skills into: *Not Assessed*, *Needs Attention (<50%)*, *Learning (50–74%)*, and *Strong (≥75%)*.
5. **Personalized Daily Study Plans**:
   * Ranks next actions by prerequisite urgency and knowledge gaps.
   * Generates actionable daily tasks (Study, Practice Quiz, Review).
6. **Grounded AI Tutor (RAG)**:
   * Real-time conversational tutor providing intuitive explanations, analogies, step-by-step breakdowns, and check-for-understanding questions.
   * Grounded exclusively in retrieved chunks from the student's uploaded material.
   * Explicitly cites document title and page number for verified facts.
   * Acknowledges when an answer is not present in uploaded material.
7. **Spaced Repetition Review Engine**:
   * Calculates review schedules (1 → 3 → 7 → 14 → 30 days) to prevent knowledge decay.
8. **Audio / Text-to-Speech (TTS) Reader**:
   * Native Android TTS voice playback for lesson summaries and tutor explanations.
9. **Dual-Mode AI (Online Cloud + Offline Local)**:
   * Seamless switching between cloud frontier models and local on-device processing.
   * Optional download manager for on-device quantized neural models.

---

## 7. AI ARCHITECTURE & DUAL-MODE STRATEGY

### Orchestration Pipeline
```
USER QUERY / ACTION
       ↓
AI ORCHESTRATOR (AIRouter)
       ↓
CHECK PRIVACY MODE & USER SETTINGS
       ↓
CHECK CONNECTIVITY (NetworkMonitor)
       ↓
RETRIEVE MATERIAL CHUNKS (LocalRetriever BM25)
       ↓
DECIDE MODEL ROUTE:
  ├─ [Online & Cloud Allowed] → CloudAIService (Gemini Flash API)
  └─ [Offline or Privacy Mode] → LocalAIService (On-Device Model / Local Fallback)
       ↓
POST-PROCESS & GROUNDING CHECK
  (Verify citations match actual chunks; flag ungrounded claims)
       ↓
DELIVER TO USER INTERFACE
```

### Online AI Requirements
* Uses Google Gemini Flash models via official endpoints.
* Uses Firebase AI Logic with App Check so Gemini Developer API credentials remain server-side.
* Never embed Gemini Developer API keys in `BuildConfig`, app resources, or URLs.
* Handles timeouts, rate limits, and network dropouts gracefully by falling back to local processing.

### Offline AI Requirements
* When disconnected or when the user enforces offline mode, the app must NEVER fail silently or attempt hidden network calls.
* If a local neural model is installed: executes on-device inference using the local model.
* If no local model is installed: provides lexical search and structured knowledge summaries, while clearly notifying the user: *"Local neural model is not installed. Download in Settings for deep offline AI reasoning."*
* **NO FAKE OFFLINE AI**: Never claim on-device AI generated an answer if it was generated by rule-based mock text or internet calls.

---

## 8. DOWNLOADABLE OFFLINE LLM REQUIREMENTS

The application must support managing on-device model weights:

1. **Hardware & Resource Compatibility**:
   * Check available device RAM and free internal storage before starting download.
   * Model specs (e.g. Gemma 2B INT4: ~1.4 GB storage, requires min 3 GB free RAM).
2. **Download Lifecycle**:
   * Standard HTTP file stream using Android `DownloadManager` or OkHttp streaming.
   * Real progress reporting (bytes downloaded / total bytes, percent, elapsed time).
   * Explicit user controls: Start, Pause, Resume, and Cancel.
   * Resilient to network interruptions with partial-content (`Range: bytes=`) support.
3. **Integrity & Security**:
   * Downloaded models MUST be verified with SHA-256 checksums before installation.
   * Models stored in private app storage (`context.filesDir` or `getExternalFilesDir`).
   * Never execute arbitrary external code or untrusted `.so` libraries.
4. **Lifecycle Management**:
   * Ability to delete downloaded weights to reclaim device storage.
   * Clear UI indicator showing model status: *Not Downloaded*, *Downloading*, *Installed*, *Corrupt*, or *Disabled*.

---

## 9. USER LEARNING MATERIAL PIPELINE

Every uploaded file must pass through the following deterministic pipeline:

```
STUDENT FILE (PDF / TXT / MD)
       ↓
[1. IMPORT & VALIDATE]
   - Verify file size (< 50 MB)
   - Verify non-empty and readable stream
   - Sanitize file name (remove path traversal tokens)
       ↓
[2. SECURE STORAGE]
   - Store in app internal storage directory
       ↓
[3. CONTENT EXTRACTION]
   - Robust text stream extraction
   - Maintain page boundaries & line offsets
   - Surface error if file is scanned/empty/password-protected
       ↓
[4. NORMALIZATION & CHUNKING]
   - Clean unicode artifacts, normalize whitespace
   - Sliding window or paragraph chunking (400–600 tokens with 50-token overlap)
   - Associate each chunk with DocumentId, PageNumber, and ChunkIndex
       ↓
[5. LOCAL INDEXING]
   - Insert chunks into Room Database (`DocumentChunkEntity`)
   - Index lexical tokens for BM25 search
       ↓
[6. SKILL MAP GENERATION]
   - Discover skills, chapters, and prerequisite relationships
```

---

## 10. RAG & KNOWLEDGE INTEGRITY RULES

1. **Strict Grounding**: The AI tutor must draw its primary explanations from retrieved chunks of student material.
2. **Citation Truthfulness**:
   * The AI tutor must ONLY cite a document name and page number if that chunk was actually retrieved and used in the prompt.
   * Hardcoded citations (e.g. defaulting to `"Physics Notes.pdf", page 24`) are strictly forbidden.
3. **Honesty on Missing Information**:
   * If a student asks a question that is not covered in their uploaded materials, the tutor must explicitly state: *"This concept was not found in your uploaded materials. Based on general knowledge..."*
4. **No Fact Invention**: The AI must never invent quotations, formulas, or page numbers that do not exist in the student's files.

---

## 11. SKILL SYSTEM & STUDENT PROGRESS RULES

1. **No Fake Progress Data**: Do not seed artificial student test scores or fake mastery. Only record real quiz attempts taken by the user.
2. **Deterministic Mastery Formulation**:
   * Base Score = `(Correct / Total) * DifficultyWeight`
   * Penalty = `- (HintsUsed * 0.04)`
   * Streak Bonus = `+ (min(10, Streak * 2))`
   * Blended Mastery = `0.6 * QuizScore + 0.4 * PreviousMastery`
3. **Graph Dependency Integrity**:
   * When a prerequisite skill is unmastered (<50%), dependent skills must flag the prerequisite in the recommendation reason.

---

## 12. SECURITY FIRST

1. **Credential Hygiene**:
   * Zero hardcoded API keys in code or version control.
   * Gemini Developer API keys must remain server-side through Firebase AI Logic or a secured proxy.
   * No API keys in URL query strings.
2. **Storage Isolation**:
   * Uploaded files stored within app-private directories.
   * Path sanitization applied to prevent directory traversal attacks.
3. **Network Security**:
   * Enforce TLS 1.3/HTTPS for all external API calls.
   * Cleartext HTTP traffic is disabled in AndroidManifest.
4. **Log Sanitization**:
   * Never log student document contents, passwords, or API keys in Logcat.

---

## 13. PRIVACY & DATA RULES

1. **Local-First Principle**: All document chunking, indexing, retrieval, and progress calculations are performed on the device by default.
2. **Explicit Cloud Consent**: When the user operates in offline mode, no data is transmitted over the network.
3. **Minimal Payload**: When cloud AI is invoked, transmit only the necessary question context and top retrieved chunks, not the student's entire library.

---

## 14. ACCESSIBILITY & UX PRINCIPLES

1. **Minimum Touch Targets**: All interactive elements (buttons, chips, icons) must have touch targets of at least 48dp × 48dp.
2. **Content Descriptions**: Every icon and image must provide a descriptive TalkBack `contentDescription` or be marked as decorative (`null`).
3. **Dynamic Theme & Contrast**: Support Material 3 dynamic color, Light mode, and Dark mode with WCAG AA compliant contrast ratios.
4. **Responsive Layout**: Designed for phones and foldables, adapting gracefully to screen size classes.

---

## 15. HACKATHON GOALS & DEFINITION OF DONE

### Hackathon Goals
* Deliver a fully functional, reliable application that a student or hackathon judge can test live with real files.
* Zero crashes, zero fake buttons, zero simulated network delays, and zero fabricated citations.

### Definition of Done for Any Feature
1. Feature has a real, working Kotlin implementation.
2. Code builds cleanly without compilation errors (`compile_applet`).
3. Automated unit/Robolectric tests pass without failure.
4. Feature handles edge cases: empty input, corrupted files, offline mode, and API errors.
5. All UI action tags (`Modifier.testTag(...)`) are present and tested.
6. No hardcoded or hallucinated student/document data.
