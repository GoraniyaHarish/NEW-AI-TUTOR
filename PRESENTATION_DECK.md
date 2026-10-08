---
marp: true
theme: default
paginate: true
header: "LearnMate™ — Team The Visionaries (CT3Y) • Code Carnival 3.0"
footer: "PS-06 On-device personalized learning assistant | Kotlin & Jetpack Compose"
style: |
  section {
    background-color: #0a0f1d;
    color: #f8fafc;
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  }
  h1, h2, h3 {
    color: #60a5fa;
  }
  code {
    background-color: #1e293b;
    color: #38bdf8;
  }
  footer, header {
    color: #64748b;
  }
---

# 🚀 LearnMate™
## On-Device Personalized Learning Assistant

**Event / Track:** Code Carnival 3.0 · PS-06  
**Team Name:** The Visionaries  
**Team ID:** CT3Y  

### Team Structure:
* **Team Leader:** Harish Goraniya
* **Team Members:**
  * Krishna Parmar
  * Prushti Seladiya
  * Sakshi Talaviya

**Technology Stack:** Native Android, Kotlin, Jetpack Compose (Material 3), Room DB, Gemini Flash API

---

## 🚨 Slide 2: The Problem (PS-06)
### Why Generic Educational Tools Fall Short

1. **Information Overload & Disorganization**: Students juggle hundreds of unorganized lecture slides, problem sets, and syllabi without prerequisite dependency mapping.
2. **Generic AI Hallucinations**: Standard chatbots lack syllabus context, hallucinating non-standard formulas, terminology, or topics outside exam scope.
3. **Connectivity Fragility**: Students commuting, studying in offline libraries, or in low-connectivity areas completely lose access to cloud-dependent assistants.
4. **The Passive Reading Trap**: Re-reading notes creates false confidence without active diagnostic testing, prerequisite tracking, and spaced recall.

---

## 💡 Slide 3: The Solution Overview
### LearnMate: Private, Offline-First Learning Pipeline

* **Document Ingestion & Deflation**: Parses student lecture PDFs and notes into 400–600 token chunks stored in local Room DB.
* **Automated Skill Graph (DAG)**: Constructs a Directed Acyclic Graph identifying foundational blocker concepts.
* **Dual-Mode AI Routing**: Seamlessly routes between Gemini Flash when online and local BM25 lexical search when offline.
* **Strict Grounding & Provenance**: Every response strictly cites verified document names and exact page numbers.
* **Mathematical Mastery Engine**: Statistical blending formula updating mastery per skill from active quizzes.

---

## 📱 Slide 4: Prototype 1 — Ingestion & Local Storage
### Zero Cloud Leakage: Private Room Database (Verified Working)

* **Private Storage Sandbox**: All student documents and course notes are stored strictly in `context.filesDir` (zero broad media permissions needed).
* **Semantic Token Chunking**: Chunks text into 400–600 token blocks with preserved chapter and page metadata.
* **Room Relational Database (10 Tables)**:
  * `CourseEntity`, `DocumentEntity`, `ChunkEntity`
  * `SkillEntity`, `DependencyEntity`, `QuizEntity`, `StudyTaskEntity`
* **Local Background Indexing**: Asynchronous execution via Kotlin Coroutines and Flow without freezing the UI.

---

## 🗺️ Slide 5: Prototype 2 — Interactive Skill Mastery Graph
### Directed Acyclic Graph (DAG) & Blocker Detection (Verified Working)

* **Interactive Graph Nodes**:
  * 🟢 **1D Kinematics**: 85% Mastery (*Mastered*)
  * 🔴 **Newton's 2nd Law**: 42% Mastery (*CRITICAL PREREQUISITE BLOCKER*)
  * 🟡 **Work & Energy**: 60% Mastery (*Learning — Blocked by Newton*)
  * ⚪ **Rotational Dynamics**: 20% Mastery (*Locked — Prerequisites Pending*)
* **Prerequisite Reasoning Engine**:
  * Traverses dependencies to stop students from tackling advanced concepts before mastering foundations.
  * Tapping any node opens an active diagnostic quiz or grounded review session.

---

## 📶 Slide 6: Prototype 3 — Dual-Mode Architecture & Offline Search
### Online When Connected, Offline Always (Verified Working)

* **Live Network Status Indicator**:
  * Displays dynamic pill badge: `● Online (Cloud Active)` vs `● Offline (Local Only)`.
* **Online Mode (Gemini Flash REST API)**:
  * Socratic multi-turn dialogue, deep multi-chapter synthesis, and fresh quiz generation.
  * API key injected securely via HTTP request headers (`x-goog-api-key`).
* **Offline Mode (In-Memory BM25 Lexical Retriever)**:
  * Computes term frequency & inverse document frequency on local chunks.
  * Instant sub-50ms retrieval speed with zero crashes and zero cloud dependencies.

---

## 💬 Slide 7: Prototype 4 — Grounded AI Tutor & Audio TTS
### Honest Provenance with Verified Document Citations (Verified Working)

* **Guaranteed Citation Chips**:
  * Answers display clickable provenance chips:  
    `📄 Physics_Notes.pdf • Page 14 • Confidence: 94%`
* **Outside-Syllabus Safeguard**:
  * If an asked topic is absent from uploaded notes, LearnMate honestly notifies the student rather than hallucinating external theories.
* **Native Android Text-to-Speech (TTS)**:
  * Integrated speaker button reads explanations aloud for auditory learners on commutes.
* **Interactive Socratic Chips**:
  * Quick-prompt starters: *"Give me a hint from my notes"*, *"Explain step-by-step"*, *"Show related formulas"*.

---

## 🧮 Slide 8: Prototype 5 — Mathematical Mastery Engine
### Beyond Vanity Streaks: Objective Statistical Scoring (Verified Working)

* **Exponential Moving Average Formula**:
  $$\text{NewMastery} = 0.6 \times \text{QuizScore} + 0.4 \times \text{PreviousMastery}$$
* **Engine Rules**:
  * **First-Attempt Heuristic**: The initial quiz sets 100% of baseline mastery without historical lag or drag.
  * **Difficulty Multipliers**: Scaled weights (`Easy` ×0.85, `Medium` ×1.00, `Hard` ×1.20).
  * **Hint Penalty**: Deducts 4% per hint used to reward independent recall.
* **Targeted Diagnostic Assessment**:
  * Generates multiple-choice quizzes directly from indexed chunks with instant feedback.

---

## 📅 Slide 9: Prototype 6 — Daily Study Plan & Spaced Repetition
### Intelligent Agenda Prioritizing Prerequisite Gaps (Verified Working)

* **Ebbinghaus Spaced Repetition Schedule**:
  * **Day 1**: Immediate post-lecture recall quiz (100% baseline).
  * **Day 3**: First consolidating diagnostic check (prevents initial decay drop).
  * **Day 7**: Mixed problem set incorporating prerequisite blockers.
  * **Day 14**: Long-term retention review.
  * **Day 30**: Comprehensive final exam retention.
* **Today's Prioritized Tasks**:
  * 🔴 `HIGH PRIORITY`: Resolve Blocker — Newton's 2nd Law (15 mins)
  * 🔵 `PRACTICE QUIZ`: Work & Kinetic Energy Practice (10 mins)
  * 🟡 `SPACED REVIEW`: 1D Kinematics Recall Review (5 mins)
* **Overall Course Progress**: Dynamic circular mastery ring (74% Mastery).

---

## 🛠️ Slide 10: Technical Architecture & Testing Quality
### Production-Grade Android Engineering (Verified Working)

* **100% Kotlin & Jetpack Compose**: Modern declarative UI with Material 3 dynamic styling.
* **Room Relational Database**: 10 SQLite entities and DAOs with type-safe migrations.
* **Automated Unit & Robolectric Testing**:
  * **57+ tests passing cleanly** (`BUILD SUCCESSFUL in 2s`).
  * Comprehensive test suites for `MasteryCalculator`, `LocalRetriever`, `RoomDatabase`, and `MainViewModel`.
* **Play Policy & Privacy Compliance**:
  * Zero broad media permissions (`READ_EXTERNAL_STORAGE` avoided).
  * Zero dynamic code loading; private app sandbox isolation.

---

## 🔮 Slide 11: In-Progress Work & Future Vision
### Honest Status & Technical Roadmap (Zero Unbuilt Claims)

* **✔ Currently Working Today (Verified Prototype)**:
  * Room DB local storage, BM25 offline search, Gemini API grounded tutoring with citations, mathematical mastery calculation, adaptive quiz engine, daily study planner.
* **🔨 What We Are Actively Building Now (Next Milestone)**:
  * Bundling quantized neural weights on-device (e.g. Gemma 2B via MediaPipe GenAI) to enable freeform local AI synthesis without any cloud fallback.
  * Enhancing on-device PDF parser to extract tables and mathematical formulas with LaTeX rendering.
  * Interactive graphical node editor for students to customize prerequisite dependencies manually.
* **🎯 Our Long-Term Vision for PS-06**:
  * On-device Multimodal Vision: Scan and transcribe handwritten whiteboard equations and textbook diagrams.
  * Encrypted Peer-to-Peer (P2P) Study Groups: Share prerequisite graphs and quiz cards over Wi-Fi Direct without cloud servers.
  * Federated Learning: Continually optimize quiz difficulty curves without uploading student privacy data.

---

## 🎓 Slide 12: Thank You!
### Questions, Feedback & Prototype Demonstration

**Project:** LearnMate™ — On-Device Personalized Learning Assistant (PS-06)  
**Team Name:** The Visionaries  
**Team ID:** CT3Y  
**Event:** Code Carnival 3.0  

* **Team Leader:** Harish Goraniya
* **Team Members:**
  * Krishna Parmar
  * Prushti Seladiya
  * Sakshi Talaviya

> *"Empowering every student with a private, honest, and accessible personal tutor right on their phone."*

### Ready for Live Demo & Q&A!
