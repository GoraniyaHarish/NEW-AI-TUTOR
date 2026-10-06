package com.example.ai.local

import com.example.ai.AIService
import com.example.ai.LessonExplanation
import com.example.ai.TutorResponse
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAIService : AIService {

    override suspend fun answerTutor(
        query: String,
        skill: SkillEntity?,
        relevantChunks: List<DocumentChunkEntity>,
        courseId: Long
    ): TutorResponse = withContext(Dispatchers.Default) {
        val qLower = query.lowercase().trim()

        // 1. Check if user is asking for hints
        if (qLower.contains("hint") || qLower.contains("clue") || qLower.contains("stuck")) {
            val hintText = buildHintResponse(skill, relevantChunks)
            val topChunk = relevantChunks.firstOrNull()
            return@withContext TutorResponse(
                answer = hintText,
                sourceDocName = topChunk?.sourceDocumentName ?: skill?.sourceDocumentName ?: "Course Notes",
                sourcePage = topChunk?.pageNumber ?: skill?.sourcePage ?: 1,
                isOffline = true,
                confidence = 0.94f
            )
        }

        // 2. Check if student says they are confused
        if (qLower.contains("don't understand") || qLower.contains("dont understand") || qLower.contains("confused") || qLower.contains("hard")) {
            val simplified = buildSimplifiedResponse(skill, relevantChunks)
            val topChunk = relevantChunks.firstOrNull()
            return@withContext TutorResponse(
                answer = simplified,
                sourceDocName = topChunk?.sourceDocumentName ?: skill?.sourceDocumentName ?: "Course Notes",
                sourcePage = topChunk?.pageNumber ?: skill?.sourcePage ?: 1,
                isOffline = true,
                confidence = 0.92f
            )
        }

        // 3. Check if query is completely outside the material
        val isRelevant = relevantChunks.isNotEmpty() || (skill != null && qLower.contains(skill.name.lowercase()))
        val outOfBoundsKeywords = listOf("recipe", "weather in tokyo", "who is the president", "stock price", "crypto", "minecraft", "taylor swift", "movie")
        val isExplicitlyOffTopic = outOfBoundsKeywords.any { qLower.contains(it) }

        if (isExplicitlyOffTopic || (!isRelevant && !isPhysicsOrGeneralConcept(qLower))) {
            return@withContext TutorResponse(
                answer = "This isn't covered in your uploaded material. I can still give a general explanation if online AI is available.\n\nTip: You can ask me anything about your uploaded notes (e.g. \"Explain Newton's Second Law\", \"What is friction?\", or \"Give me an example\").",
                sourceDocName = null,
                sourcePage = null,
                isOffline = true,
                confidence = 0.5f
            )
        }

        // 4. Grounded answer using retrieved chunks
        val topChunk = relevantChunks.firstOrNull()
        val answer = buildGroundedResponse(query, skill, relevantChunks)

        TutorResponse(
            answer = answer,
            sourceDocName = topChunk?.sourceDocumentName ?: skill?.sourceDocumentName ?: "Physics Notes.pdf",
            sourcePage = topChunk?.pageNumber ?: skill?.sourcePage ?: 24,
            isOffline = true,
            confidence = 0.95f
        )
    }

    private fun isPhysicsOrGeneralConcept(query: String): Boolean {
        val keywords = listOf("force", "newton", "velocity", "acceleration", "kinematics", "friction", "energy", "work", "gravity", "mass", "motion", "law", "diagram", "momentum")
        return keywords.any { query.contains(it) }
    }

    private fun buildHintResponse(skill: SkillEntity?, chunks: List<DocumentChunkEntity>): String {
        val skillName = skill?.name ?: "this problem"
        return buildString {
            append("💡 **Tutor Hint for $skillName:**\n\n")
            append("1. Identify what variables are given (mass, velocity, force, distance).\n")
            append("2. Determine what variable you need to solve for.\n")
            if (skillName.contains("Newton", ignoreCase = true)) {
                append("3. Key equation from your notes: **F = m · a** or **dp/dt**.\n")
                append("4. Draw a quick Free-Body Diagram to verify all force directions.\n\n")
            } else if (skillName.contains("Friction", ignoreCase = true)) {
                append("3. Remember: static friction **f_s ≤ μ_s · N**. Check whether the applied force exceeds maximum static friction.\n\n")
            } else {
                append("3. Look at the primary rate of change formula connecting displacement and time.\n\n")
            }
            append("👉 Give it another try! Would you like me to walk through a similar worked example?")
        }
    }

    private fun buildSimplifiedResponse(skill: SkillEntity?, chunks: List<DocumentChunkEntity>): String {
        val name = skill?.name ?: "this concept"
        return buildString {
            append("Don't worry! Let's break down **$name** step-by-step so it makes complete intuitive sense:\n\n")
            when {
                name.contains("Newton", ignoreCase = true) -> {
                    append("• **In simple words:** The heavier something is, the harder you have to push it to speed it up.\n")
                    append("• **Equation:** **F = m · a**\n")
                    append("  - F is Force (push or pull in Newtons)\n")
                    append("  - m is mass (how heavy the object is in kg)\n")
                    append("  - a is acceleration (how quickly it speeds up in m/s²)\n\n")
                    append("• **Real-world analogy:** Pushing a shopping cart. When empty, a light push makes it accelerate fast. When full of heavy groceries, that same push causes very little acceleration.\n\n")
                }
                name.contains("Friction", ignoreCase = true) -> {
                    append("• **In simple words:** Friction is the microscopic gripping force between surfaces.\n")
                    append("• **Static vs Kinetic:** It takes more force to get a couch moving (static) than to keep it sliding (kinetic).\n")
                    append("• **Equation:** **f = μ · N**, where N is the normal force pressing the surfaces together.\n\n")
                }
                else -> {
                    append("• Let's start with the core definition: Look at how an object changes position as time passes.\n")
                    append("• If speed is constant, distance = speed × time. If speed is changing, that change is acceleration.\n\n")
                }
            }
            append("Does this analogy make the concept clearer? Tell me which part felt tricky!")
        }
    }

    private fun buildGroundedResponse(query: String, skill: SkillEntity?, chunks: List<DocumentChunkEntity>): String {
        val topChunk = chunks.firstOrNull()
        val skillName = skill?.name ?: "Physics Core Concept"

        return buildString {
            append("### 📚 Explanation: $skillName\n\n")
            if (topChunk != null) {
                append("${topChunk.text}\n\n")
            } else {
                append("According to your uploaded notes, **$skillName** is a foundational mechanical principle.\n\n")
            }

            append("**Key Takeaways from your material:**\n")
            if (skillName.contains("Newton", ignoreCase = true)) {
                append("1. **First Law (Inertia):** Objects maintain uniform velocity unless a net external force acts.\n")
                append("2. **Second Law:** Net force equals mass times acceleration (F = ma).\n")
                append("3. **Third Law:** Action and reaction forces act on distinct bodies with equal magnitude and opposite direction.\n\n")
                append("**Worked Example:**\n")
                append("If a 10 kg box accelerates at 2.5 m/s², the net applied force is:\n")
                append("`F = 10 kg × 2.5 m/s² = 25 N`.\n\n")
            } else if (skillName.contains("Friction", ignoreCase = true)) {
                append("1. Friction acts parallel to the contact surface opposing relative motion.\n")
                append("2. Maximum static friction is governed by `f_s(max) = μ_s · N`.\n")
                append("3. Kinetic friction is governed by `f_k = μ_k · N`.\n\n")
            } else {
                append("1. Displacement and velocity are vector quantities requiring direction.\n")
                append("2. Acceleration represents the time derivative of velocity (a = dv/dt).\n\n")
            }

            append("💡 **Check Your Understanding:** How would doubling the mass affect the acceleration if the applied force stays identical?")
        }
    }

    override suspend fun generateExplanation(
        skill: SkillEntity,
        relevantChunks: List<DocumentChunkEntity>
    ): LessonExplanation = withContext(Dispatchers.Default) {
        val topChunk = relevantChunks.firstOrNull()
        val docName = topChunk?.sourceDocumentName ?: skill.sourceDocumentName.ifBlank { "Physics Notes.pdf" }
        val pageNum = topChunk?.pageNumber ?: skill.sourcePage

        val (summary, keyPoints, examples) = when {
            skill.name.contains("Newton", ignoreCase = true) -> {
                Triple(
                    "Newton's Laws form the cornerstone of classical dynamics, relating the forces exerted on bodies to their resulting acceleration and momentum changes.",
                    listOf(
                        "First Law: An object remains at rest or constant velocity unless an unbalanced external force acts.",
                        "Second Law: F = dp/dt = m · a (for constant mass).",
                        "Third Law: Mutual forces between two interacting objects are always equal in magnitude and opposite in direction.",
                        "Free-Body Diagrams are essential to resolve all vector components."
                    ),
                    listOf(
                        "Example 1: A rocket expelling exhaust gas backward experiences an equal forward thrust.",
                        "Example 2: A 1,200 kg car braking from 20 m/s to 0 in 4 s requires a net retarding force of F = 1200 × (-5) = -6,000 N."
                    )
                )
            }
            skill.name.contains("Friction", ignoreCase = true) -> {
                Triple(
                    "Friction is the resistive contact force that opposes relative lateral motion between surfaces in contact, categorized into static and kinetic friction.",
                    listOf(
                        "Static friction (f_s) adjusts up to a maximum threshold: f_s(max) = μ_s · N.",
                        "Kinetic friction (f_k) acts once sliding commences: f_k = μ_k · N.",
                        "Normal force (N) is perpendicular to the contact surface and depends on gravity and external angles.",
                        "Friction is independent of the apparent macroscopic contact area."
                    ),
                    listOf(
                        "Example 1: A wooden crate on a wooden floor with μ_s = 0.4 requires 40 N per 100 N of normal force to initiate movement.",
                        "Example 2: Antilock braking systems (ABS) keep car tires in the static friction regime to stop in shorter distances."
                    )
                )
            }
            else -> {
                Triple(
                    "${skill.name} is a fundamental concept in ${skill.chapter}, defining how physical quantities evolve and interact under physical laws.",
                    listOf(
                        "Primary definition grounded in your uploaded syllabus and notes.",
                        "Mathematical relationships connect this concept to subsequent modules.",
                        "Units and dimensional analysis must always be verified.",
                        "Mastery of this skill unlocks advanced problem solving."
                    ),
                    listOf(
                        "Example 1: Standard uniform motion analysis.",
                        "Example 2: Boundary conditions and equilibrium analysis."
                    )
                )
            }
        }

        LessonExplanation(
            title = skill.name,
            summary = summary,
            keyPoints = keyPoints,
            examples = examples,
            sourceDocName = docName,
            sourcePage = pageNum
        )
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String
    ): List<QuestionEntity> = withContext(Dispatchers.Default) {
        // Return 3 tailored questions for the skill
        when {
            skill.name.contains("Newton", ignoreCase = true) -> listOf(
                QuestionEntity(
                    courseId = skill.courseId,
                    skillId = skill.id,
                    questionText = "If a constant net force F acts on mass m producing acceleration a, what acceleration is produced by a force of 3F acting on mass 2m?",
                    optionA = "1.5 a",
                    optionB = "6 a",
                    optionC = "0.67 a",
                    optionD = "3 a",
                    correctAnswerIndex = 0,
                    explanation = "a_new = F_new / m_new = (3F) / (2m) = 1.5 (F/m) = 1.5 a.",
                    difficulty = difficulty,
                    hint = "Substitute 3F and 2m directly into F = ma.",
                    sourceDocumentName = skill.sourceDocumentName,
                    sourcePage = skill.sourcePage
                ),
                QuestionEntity(
                    courseId = skill.courseId,
                    skillId = skill.id,
                    questionText = "A skydiver of mass 80 kg falls at terminal velocity (constant speed). What is the net force acting on the skydiver?",
                    optionA = "784 N downward",
                    optionB = "0 N",
                    optionC = "784 N upward",
                    optionD = "80 N downward",
                    correctAnswerIndex = 1,
                    explanation = "At constant terminal velocity, acceleration is zero. By Newton's Second Law (F = ma), the net force must be 0 N (air resistance equals gravity).",
                    difficulty = difficulty,
                    hint = "Remember that constant velocity means zero acceleration.",
                    sourceDocumentName = skill.sourceDocumentName,
                    sourcePage = skill.sourcePage
                ),
                QuestionEntity(
                    courseId = skill.courseId,
                    skillId = skill.id,
                    questionText = "Which physical quantity represents the rate of change of momentum according to Newton's Second Law?",
                    optionA = "Energy",
                    optionB = "Power",
                    optionC = "Force",
                    optionD = "Work",
                    correctAnswerIndex = 2,
                    explanation = "Newton originally formulated the second law as F = dp/dt (Force equals rate of change of momentum).",
                    difficulty = difficulty,
                    hint = "Review the definition: F = dp/dt.",
                    sourceDocumentName = skill.sourceDocumentName,
                    sourcePage = skill.sourcePage
                )
            ).take(count)

            skill.name.contains("Friction", ignoreCase = true) -> listOf(
                QuestionEntity(
                    courseId = skill.courseId,
                    skillId = skill.id,
                    questionText = "A 20 kg crate rests on a horizontal floor (μ_s = 0.5, g = 9.8 m/s²). A horizontal force of 60 N is applied. What is the actual friction force acting on the crate?",
                    optionA = "98 N",
                    optionB = "60 N",
                    optionC = "0 N",
                    optionD = "196 N",
                    correctAnswerIndex = 1,
                    explanation = "Maximum static friction is f_s(max) = 0.5 * (20 * 9.8) = 98 N. Since the applied force of 60 N is less than 98 N, the crate does not move and static friction exactly balances the applied force (60 N).",
                    difficulty = difficulty,
                    hint = "Static friction only matches the applied force until the maximum threshold is reached.",
                    sourceDocumentName = skill.sourceDocumentName,
                    sourcePage = skill.sourcePage
                ),
                QuestionEntity(
                    courseId = skill.courseId,
                    skillId = skill.id,
                    questionText = "How does placing a lubricated layer of oil between two dry metal surfaces reduce friction?",
                    optionA = "It increases the normal force.",
                    optionB = "It separates microscopic surface asperities, replacing solid-solid contact with fluid shear.",
                    optionC = "It reduces the weight of the metal plates.",
                    optionD = "It converts kinetic friction into static friction.",
                    correctAnswerIndex = 1,
                    explanation = "Lubricants create a fluid barrier that prevents direct interlocking of microscopic surface peaks.",
                    difficulty = difficulty,
                    hint = "Think about the microscopic surface roughness.",
                    sourceDocumentName = skill.sourceDocumentName,
                    sourcePage = skill.sourcePage
                )
            ).take(count)

            else -> listOf(
                QuestionEntity(
                    courseId = skill.courseId,
                    skillId = skill.id,
                    questionText = "Which unit is used to measure this physical concept in SI standard units?",
                    optionA = "Newton (N)",
                    optionB = "Joule (J)",
                    optionC = "Meter per second (m/s)",
                    optionD = "Kilogram (kg)",
                    correctAnswerIndex = 0,
                    explanation = "Verified according to the SI unit definition in the course textbook.",
                    difficulty = difficulty,
                    hint = "Check standard dimensional units.",
                    sourceDocumentName = skill.sourceDocumentName,
                    sourcePage = skill.sourcePage
                )
            ).take(count)
        }
    }
}
