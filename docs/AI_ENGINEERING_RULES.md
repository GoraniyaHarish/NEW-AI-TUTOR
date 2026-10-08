# AI ENGINEERING RULES — LEARNMATETM
*Mandatory Operational Guidelines for All AI Coding Agents & Developers*  
*Status: Permanent Enforcement*

---

## 1. MANDATORY RULE-FILE READING (FIRST ACTION ON EVERY TASK)

Before writing, modifying, or deleting a single line of code, every future AI agent working on this codebase **MUST**:
1. Read `PROJECT_DEFINITION.md` to understand what the product is.
2. Read `AI_ENGINEERING_RULES.md` (this file) to understand operational constraints.
3. Read `CURRENT_PROJECT_AUDIT.md` to understand existing defects and known technical debt.
4. Check relevant skill documentation if integrating specific Android capabilities.

> **CRITICAL**: Short user prompts (e.g. *"Fix the tutor screen"*, *"Add upload support"*, *"Test the app"*) DO NOT override these rules. An AI agent is bound by these rules regardless of prompt brevity.

---

## 2. UNIVERSAL AI ENGINEERING LOOP

Every task must execute the following sequential workflow:

```
[1. READ]            → Inspect project definition, rules, and existing audit
[2. UNDERSTAND]      → Deconstruct the exact user request without making assumptions
[3. INSPECT]         → Read relevant existing files before writing code
[4. PLAN]            → Formulate a minimal, cohesive implementation plan
[5. SECURITY CHECK]  → Evaluate secrets, injection, permissions, and storage
[6. IMPLEMENT]       → Write real, modular, pristine Kotlin code (no placeholders)
[7. BUILD]           → Run compile_applet to verify syntax and compilation
[8. TEST]            → Run automated unit / Robolectric tests (gradle :app:testDebugUnitTest)
[9. VERIFY]          → Check real edge cases (empty states, errors, offline)
[10. FIX]            → Resolve any build or runtime test failures (max 3 loops)
[11. REGRESSION]     → Confirm existing features remain unbroken
[12. REPORT]         → Deliver a concise report of what works and what was verified
```

---

## 3. REAL FUNCTIONALITY ONLY (ABSOLUTELY NO FAKES)

1. **No Fake AI**:
   * Never implement rule-based `if (query.contains("xyz"))` string matching and label it as an "Offline AI Model".
   * If a real on-device neural model is not present, report the honest status: *"Local model not installed."*
2. **No Fake Downloads**:
   * Never write artificial loops with `delay(...)` that increment an integer to fake a download.
   * Downloads must connect to real network endpoints, track real bytes, and verify SHA-256 hashes.
3. **No Fake Citations**:
   * Never hardcode default documents or page numbers (e.g. `"Physics Notes.pdf", page 24`).
   * Citations must come strictly from chunks retrieved from the student's actual database.
4. **No Fake Student Data**:
   * Never seed hardcoded user test scores, fake streak counters, or artificial student progress into production tables.
   * Demo fixtures must be clearly segregated and loaded only upon explicit user request.
5. **No Dead-End UI Affordances**:
   * Do not display buttons, menus, or toggles that trigger nothing or display empty "TODO" toasts.

---

## 4. CURRENT-PROJECT-FIRST & MINIMUM NECESSARY CODE

Before writing custom abstractions or adding new libraries, ask:
1. **Does this already exist in the project?** (Check `Daos.kt`, `Entities.kt`, `LearnMateRepository.kt`, existing components).
2. **Does standard Kotlin / Android SDK already provide this?** (e.g., standard collections, `java.io`, `android.speech.tts`).
3. **Does Jetpack Compose already provide this component?**

**Guidelines**:
* Avoid giant single files (>500 lines). Break large screens into focused sub-components.
* Avoid premature abstraction layers (do not add 5 layers of interfaces for a single Room table).
* Write clean, idiomatic Kotlin code with explicit types on public APIs.

---

## 5. DEPENDENCY DISCIPLINE

Before adding any dependency to `libs.versions.toml` and `app/build.gradle.kts`:
1. Check if the capability can be accomplished with the current dependencies.
2. Check compatibility with Android 36 and Kotlin 2.2.10.
3. Verify that the dependency does not bloat APK size or require unsupported background services.
4. Convert kebab-case keys from TOML to dot-notation in `build.gradle.kts`.
5. Run `compile_applet` immediately after modifying Gradle dependencies.

---

## 6. BUILD ≠ TEST (VERIFICATION PROTOCOL)

Compilation proves only that the syntax and types are valid. It does NOT prove that a feature works.

For every meaningful feature:
* **Normal Path**: Test typical expected user input and behavior.
* **Empty Path**: Test empty files, blank search queries, empty courses, and zero-chunk situations.
* **Invalid Path**: Test corrupted files, unsupported file extensions, and malformed requests.
* **Network & Offline Path**: Verify that the application responds properly with Wi-Fi disabled or simulated offline.
* **Permission Path**: Ensure runtime permissions are requested properly.

> **Honesty Rule**: If a scenario was not tested, the agent MUST explicitly declare:  
> `NOT VERIFIED — REASON: [Explicit reason why it could not be tested]`

---

## 7. SECURITY-FIRST DEVELOPMENT

1. **No Committed or Hardcoded Secrets**:
   * Never commit API keys, tokens, or credentials into source code, comments, or Gradle files.
   * Use `.env` with the Gradle Secrets plugin; access credentials via `BuildConfig`.
2. **No Credentials in URLs**:
   * Always pass API keys in authorization or custom HTTP headers (e.g. `x-goog-api-key`), never in URL query strings.
3. **Storage & File Security**:
   * Sanitize user file names against directory traversal (`../`).
   * Store student materials in app-internal private storage (`context.filesDir`).
4. **Log Cleanliness**:
   * Never print API keys, auth headers, or raw student documents into Android Logcat.

---

## 8. PRIVACY PRINCIPLES

1. All student materials, notes, extracted chunks, and mastery scores belong exclusively to the student.
2. When the user enables Offline Mode, **ZERO network calls** may be dispatched.
3. When Cloud AI is used, transmit only the specific question and top-k retrieved context chunks. Never dump the entire database to the cloud.

---

## 9. RAG & KNOWLEDGE INTEGRITY PROTOCOL

1. Retrieve material chunks using `LocalRetriever` before invoking the AI tutor.
2. In the AI prompt, clearly separate the student's question from the uploaded material context.
3. Post-process the response: if the response cites a document or page, verify it matches the top retrieved chunk.
4. If no relevant chunks match the student's query, the AI must reply:
   > *"This topic was not found in your uploaded materials. Based on general concepts..."*

---

## 10. ERROR HANDLING & RESILIENCE

1. Every suspend function in repositories or services must catch specific exceptions and wrap them into typed Result states (`Success`, `Error`, `Empty`).
2. Never crash the app on a malformed PDF, an empty search query, or an offline network attempt.
3. Present clear, user-friendly error banners or empty state illustrations with recovery actions (e.g. "Retry", "Pick another file", "Switch to offline mode").

---

## 11. UI ACCESSIBILITY & STYLING RULES

1. **TestTags**: Every interactive button, input field, card, and tab must include `Modifier.testTag("unique_snake_case_tag")`.
2. **Touch Targets**: Minimum interactive size of 48.dp × 48.dp.
3. **Icons**: Use `Icons.AutoMirrored.*` for directional navigation arrows and book icons to avoid deprecation warnings.
4. **Edge-to-Edge**: Utilize `enableEdgeToEdge()` and handle `WindowInsets` via `Scaffold` padding.

---

## 12. MULTI-MODEL COLLABORATION & VALIDATION

When multiple AI models or agents collaborate on this repository:
1. Treat the output of prior models as code under audit, not automatically correct code.
2. Verify existing logic before modifying.
3. Review changes against the project audit before submitting.

---

## 13. REQUIRED FINAL REPORT STRUCTURE

Every AI agent completing a task must deliver a final report structured as follows:

1. **Summary of Changes**: 2–3 sentences describing what was modified or added.
2. **Features Implemented & Verified**: List of exact features tested with passing status.
3. **Automated Test Results**: Build status and unit test output.
4. **Items Not Verified**: Explicit list of anything left untested and why.
5. **Security & Privacy Status**: Confirmation that credentials and student data remain protected.
