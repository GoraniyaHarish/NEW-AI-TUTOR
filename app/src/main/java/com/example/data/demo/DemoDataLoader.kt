package com.example.data.demo

import com.example.data.local.database.LearnMateDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.LearningPlanEntity
import com.example.data.local.entity.LearningPlanItemEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRelationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DemoDataLoader {

    suspend fun populateDemoCourseIfEmpty(database: LearnMateDatabase) = withContext(Dispatchers.IO) {
        val existing = database.courseDao().getFirstCourse()
        if (existing != null) return@withContext

        populateDemoCourse(database)
    }

    suspend fun populateDemoCourse(database: LearnMateDatabase): Long = withContext(Dispatchers.IO) {
        // 1. Create Course
        val courseId = database.courseDao().insertCourse(
            CourseEntity(
                title = "Physics",
                description = "Mechanics, Dynamics, Kinematics, Energy and Laws of Motion",
                isDemo = true
            )
        )

        // 2. Documents
        val notesDocId = database.documentDao().insertDocument(
            DocumentEntity(
                courseId = courseId,
                fileName = "Physics Notes.pdf",
                filePath = "assets/demo/physics_notes.pdf",
                fileType = "PDF",
                fileSize = "2.4 MB",
                pageCount = 32,
                processed = true
            )
        )

        val syllabusDocId = database.documentDao().insertDocument(
            DocumentEntity(
                courseId = courseId,
                fileName = "Physics Syllabus.pdf",
                filePath = "assets/demo/physics_syllabus.pdf",
                fileType = "PDF",
                fileSize = "480 KB",
                pageCount = 4,
                processed = true
            )
        )

        val examDocId = database.documentDao().insertDocument(
            DocumentEntity(
                courseId = courseId,
                fileName = "Physics Question Paper.pdf",
                filePath = "assets/demo/physics_exam.pdf",
                fileType = "PDF",
                fileSize = "1.1 MB",
                pageCount = 8,
                processed = true
            )
        )

        // 3. Document Chunks (with page references)
        val chunks = listOf(
            DocumentChunkEntity(
                documentId = notesDocId,
                courseId = courseId,
                sourceDocumentName = "Physics Notes.pdf",
                pageNumber = 12,
                chunkIndex = 0,
                text = "Kinematics is the branch of classical mechanics that describes the motion of points, bodies (objects), and systems of bodies without considering the forces that cause them to move. Displacement is a vector quantity representing the change in position."
            ),
            DocumentChunkEntity(
                documentId = notesDocId,
                courseId = courseId,
                sourceDocumentName = "Physics Notes.pdf",
                pageNumber = 14,
                chunkIndex = 1,
                text = "Velocity is the rate of change of displacement with respect to time: v = ds/dt. Average velocity is total displacement divided by total time. Acceleration represents the time rate of change of velocity: a = dv/dt = d^2s/dt^2. For constant acceleration, v = u + at, and s = ut + 0.5*a*t^2."
            ),
            DocumentChunkEntity(
                documentId = notesDocId,
                courseId = courseId,
                sourceDocumentName = "Physics Notes.pdf",
                pageNumber = 24,
                chunkIndex = 2,
                text = "Newton's Second Law of Motion: The rate of change of momentum of a body is directly proportional to the applied force and takes place in the direction in which the force acts. Mathematically, F = dp/dt = d(mv)/dt. When mass is constant, F = m*a. Newton's First Law defines inertia (an object remains at rest or uniform motion unless acted upon by a net external force). Newton's Third Law states that for every action, there is an equal and opposite reaction: F_AB = -F_BA."
            ),
            DocumentChunkEntity(
                documentId = notesDocId,
                courseId = courseId,
                sourceDocumentName = "Physics Notes.pdf",
                pageNumber = 26,
                chunkIndex = 3,
                text = "Force Diagrams and Free-Body Diagrams (FBD): An FBD isolates an object and graphically represents all external forces acting upon it. Normal force (N) acts perpendicular to the contact surface. Gravitational weight acts downward: W = m*g. Tension (T) pulls along ropes or strings."
            ),
            DocumentChunkEntity(
                documentId = notesDocId,
                courseId = courseId,
                sourceDocumentName = "Physics Notes.pdf",
                pageNumber = 28,
                chunkIndex = 4,
                text = "Friction is the resistive force opposing relative tangential motion between contact surfaces. Static friction (f_s) opposes intended motion: f_s <= mu_s * N, reaching maximum static friction f_s(max) = mu_s * N at the verge of slipping. Kinetic friction occurs during sliding: f_k = mu_k * N, where mu_k is usually less than mu_s."
            ),
            DocumentChunkEntity(
                documentId = notesDocId,
                courseId = courseId,
                sourceDocumentName = "Physics Notes.pdf",
                pageNumber = 31,
                chunkIndex = 5,
                text = "Work and Energy: Work done by a constant force is the dot product W = F · d = F * d * cos(theta). The Work-Energy Theorem states that net work done on a particle equals the change in its kinetic energy: W_net = Delta K = 0.5*m*v_f^2 - 0.5*m*v_i^2. In conservative systems, mechanical energy E = K + U is conserved."
            )
        )
        database.documentChunkDao().insertChunks(chunks)

        // 4. Skills
        val sKinematics = SkillEntity(
            courseId = courseId,
            name = "Kinematics",
            description = "Study of position, motion, and trajectory without considering mass or force.",
            chapter = "Mechanics",
            difficulty = "EASY",
            sourceDocumentId = notesDocId,
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 12,
            confidence = 0.96f
        )
        val sVelocity = SkillEntity(
            courseId = courseId,
            name = "Velocity",
            description = "Vector rate of change of displacement with respect to time.",
            chapter = "Mechanics",
            difficulty = "EASY",
            sourceDocumentId = notesDocId,
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 14,
            confidence = 0.95f
        )
        val sAcceleration = SkillEntity(
            courseId = courseId,
            name = "Acceleration",
            description = "Time rate of change of velocity and equations of uniform motion.",
            chapter = "Mechanics",
            difficulty = "MEDIUM",
            sourceDocumentId = notesDocId,
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 14,
            confidence = 0.94f
        )
        val sNewtonsLaws = SkillEntity(
            courseId = courseId,
            name = "Newton's Laws",
            description = "Fundamental principles governing forces, inertia, F = ma, and action-reaction pairs.",
            chapter = "Dynamics",
            difficulty = "HARD",
            sourceDocumentId = notesDocId,
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 24,
            confidence = 0.92f
        )
        val sForce = SkillEntity(
            courseId = courseId,
            name = "Force",
            description = "Vector interactions, contact forces, normal force, and free-body diagram analysis.",
            chapter = "Dynamics",
            difficulty = "MEDIUM",
            sourceDocumentId = notesDocId,
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 26,
            confidence = 0.91f
        )
        val sFriction = SkillEntity(
            courseId = courseId,
            name = "Friction",
            description = "Static and kinetic friction coefficients, normal reaction coupling, and inclined planes.",
            chapter = "Dynamics",
            difficulty = "HARD",
            sourceDocumentId = notesDocId,
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 28,
            confidence = 0.89f
        )
        val sWorkEnergy = SkillEntity(
            courseId = courseId,
            name = "Work and Energy",
            description = "Work-energy theorem, conservative forces, and mechanical energy conservation.",
            chapter = "Energy",
            difficulty = "MEDIUM",
            sourceDocumentId = notesDocId,
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 31,
            confidence = 0.90f
        )

        val insertedIds = database.skillDao().insertSkills(
            listOf(sKinematics, sVelocity, sAcceleration, sNewtonsLaws, sForce, sFriction, sWorkEnergy)
        )
        val idKinematics = insertedIds[0]
        val idVelocity = insertedIds[1]
        val idAcceleration = insertedIds[2]
        val idNewtonsLaws = insertedIds[3]
        val idForce = insertedIds[4]
        val idFriction = insertedIds[5]
        val idWorkEnergy = insertedIds[6]

        // 5. Skill Relationships
        database.skillRelationDao().insertRelations(
            listOf(
                SkillRelationEntity(courseId = courseId, fromSkillId = idKinematics, toSkillId = idVelocity, relationType = "PREREQUISITE"),
                SkillRelationEntity(courseId = courseId, fromSkillId = idVelocity, toSkillId = idAcceleration, relationType = "PREREQUISITE"),
                SkillRelationEntity(courseId = courseId, fromSkillId = idAcceleration, toSkillId = idNewtonsLaws, relationType = "PREREQUISITE"),
                SkillRelationEntity(courseId = courseId, fromSkillId = idNewtonsLaws, toSkillId = idForce, relationType = "PREREQUISITE"),
                SkillRelationEntity(courseId = courseId, fromSkillId = idForce, toSkillId = idFriction, relationType = "PREREQUISITE"),
                SkillRelationEntity(courseId = courseId, fromSkillId = idNewtonsLaws, toSkillId = idFriction, relationType = "PREREQUISITE"),
                SkillRelationEntity(courseId = courseId, fromSkillId = idForce, toSkillId = idWorkEnergy, relationType = "PREREQUISITE")
            )
        )

        // 6. Demo Learner State (as prescribed in prompt section 31):
        // Kinematics = 85%
        // Velocity = 92%
        // Acceleration = 78%
        // Newton's Laws = 42%
        // Force = 35%
        // Friction = 28%
        // Work and Energy = 61%
        database.learnerSkillDao().insertLearnerSkills(
            listOf(
                LearnerSkillEntity(skillId = idKinematics, courseId = courseId, masteryScore = 85, confidence = 0.92f, attempts = 5, correctAnswers = 5, incorrectAnswers = 0, streak = 4, lastPracticed = System.currentTimeMillis()),
                LearnerSkillEntity(skillId = idVelocity, courseId = courseId, masteryScore = 92, confidence = 0.95f, attempts = 6, correctAnswers = 6, incorrectAnswers = 0, streak = 5, lastPracticed = System.currentTimeMillis()),
                LearnerSkillEntity(skillId = idAcceleration, courseId = courseId, masteryScore = 78, confidence = 0.88f, attempts = 4, correctAnswers = 3, incorrectAnswers = 1, streak = 2, lastPracticed = System.currentTimeMillis()),
                LearnerSkillEntity(skillId = idNewtonsLaws, courseId = courseId, masteryScore = 42, confidence = 0.70f, attempts = 4, correctAnswers = 2, incorrectAnswers = 2, hintsUsed = 2, streak = 0, lastPracticed = System.currentTimeMillis() - 86400000),
                LearnerSkillEntity(skillId = idForce, courseId = courseId, masteryScore = 35, confidence = 0.65f, attempts = 3, correctAnswers = 1, incorrectAnswers = 2, hintsUsed = 3, streak = 0, lastPracticed = System.currentTimeMillis() - 172800000),
                LearnerSkillEntity(skillId = idFriction, courseId = courseId, masteryScore = 28, confidence = 0.60f, attempts = 3, correctAnswers = 1, incorrectAnswers = 2, hintsUsed = 2, streak = 0, lastPracticed = System.currentTimeMillis() - 259200000),
                LearnerSkillEntity(skillId = idWorkEnergy, courseId = courseId, masteryScore = 61, confidence = 0.80f, attempts = 3, correctAnswers = 2, incorrectAnswers = 1, hintsUsed = 1, streak = 1, lastPracticed = System.currentTimeMillis() - 43200000)
            )
        )

        // 7. Questions (tied to skill IDs, varying difficulty, grounded in notes)
        val questions = listOf(
            QuestionEntity(
                courseId = courseId,
                skillId = idNewtonsLaws,
                questionText = "According to Newton's Second Law, if a net force of 24 N is applied to an object with a mass of 6 kg, what is the resulting acceleration?",
                optionA = "144 m/s²",
                optionB = "4 m/s²",
                optionC = "18 m/s²",
                optionD = "0.25 m/s²",
                correctAnswerIndex = 1,
                explanation = "From F = m * a, acceleration a = F / m = 24 N / 6 kg = 4 m/s².",
                difficulty = "MEDIUM",
                hint = "Recall the formula F = m * a. Solve for acceleration: a = F / m.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 24
            ),
            QuestionEntity(
                courseId = courseId,
                skillId = idNewtonsLaws,
                questionText = "Which of the following statements correctly represents Newton's Third Law of Motion?",
                optionA = "Action and reaction forces act on the same body and cancel each other out.",
                optionB = "Action and reaction forces act on different bodies simultaneously and are equal in magnitude but opposite in direction.",
                optionC = "The action force occurs first, followed shortly after by the reaction force.",
                optionD = "Action force is always greater than reaction force when an object accelerates.",
                correctAnswerIndex = 1,
                explanation = "Action and reaction forces always act simultaneously on two different interacting bodies, so they never cancel each other out on a single object.",
                difficulty = "HARD",
                hint = "Think about whether a horse pulling a cart acts on the cart or on the ground.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 24
            ),
            QuestionEntity(
                courseId = courseId,
                skillId = idNewtonsLaws,
                questionText = "What physical property of matter determines its inertia according to Newton's First Law?",
                optionA = "Velocity",
                optionB = "Weight",
                optionC = "Mass",
                optionD = "Volume",
                correctAnswerIndex = 2,
                explanation = "Mass is the quantitative measure of an object's inertia (resistance to change in motion).",
                difficulty = "EASY",
                hint = "Inertia is intrinsic to the amount of matter in the body, independent of gravity.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 24
            ),
            QuestionEntity(
                courseId = courseId,
                skillId = idForce,
                questionText = "A 5 kg block sits at rest on a horizontal surface. If g = 9.8 m/s², what is the magnitude of the normal force acting on the block?",
                optionA = "0 N",
                optionB = "49 N",
                optionC = "9.8 N",
                optionD = "24.5 N",
                correctAnswerIndex = 1,
                explanation = "In vertical equilibrium on a level surface, Normal force N = m * g = 5 kg * 9.8 m/s² = 49 N upward.",
                difficulty = "MEDIUM",
                hint = "Apply Sigma F_y = 0: N - mg = 0.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 26
            ),
            QuestionEntity(
                courseId = courseId,
                skillId = idFriction,
                questionText = "A block with normal force N = 100 N has a coefficient of static friction mu_s = 0.4. What is the maximum horizontal force that can be applied before the block starts slipping?",
                optionA = "250 N",
                optionB = "40 N",
                optionC = "100 N",
                optionD = "4 N",
                correctAnswerIndex = 1,
                explanation = "Maximum static friction is f_s(max) = mu_s * N = 0.4 * 100 N = 40 N.",
                difficulty = "MEDIUM",
                hint = "Use the equation for maximum static friction: f_s = mu_s * N.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 28
            ),
            QuestionEntity(
                courseId = courseId,
                skillId = idFriction,
                questionText = "Why is the coefficient of kinetic friction (mu_k) typically lower than the coefficient of static friction (mu_s)?",
                optionA = "Because surface microscopic irregularities interlock more deeply when surfaces are stationary relative to each other.",
                optionB = "Because the normal force decreases when an object is in motion.",
                optionC = "Because gravity weakens when velocity increases.",
                optionD = "Because kinetic friction acts perpendicular to the surface.",
                correctAnswerIndex = 0,
                explanation = "When stationary, contact asperities form microscopic cold welds and deeper interlocking bonds, requiring more force to initiate motion than to maintain it.",
                difficulty = "HARD",
                hint = "Consider what happens at the microscopic level when two stationary surfaces press together.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 28
            ),
            QuestionEntity(
                courseId = courseId,
                skillId = idVelocity,
                questionText = "An object travels 150 meters east in 10 seconds, then 50 meters west in 5 seconds. What is its average velocity over the 15-second trip?",
                optionA = "13.3 m/s east",
                optionB = "6.67 m/s east",
                optionC = "10.0 m/s east",
                optionD = "6.67 m/s west",
                correctAnswerIndex = 1,
                explanation = "Displacement = 150 m - 50 m = 100 m east. Total time = 10 s + 5 s = 15 s. Average velocity = 100 m / 15 s = 6.67 m/s east.",
                difficulty = "MEDIUM",
                hint = "Remember that velocity depends on total displacement (a vector), not total distance traveled.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 14
            ),
            QuestionEntity(
                courseId = courseId,
                skillId = idKinematics,
                questionText = "Which of the following is a fundamental vector quantity in kinematics?",
                optionA = "Speed",
                optionB = "Distance",
                optionC = "Displacement",
                optionD = "Time",
                correctAnswerIndex = 2,
                explanation = "Displacement has both magnitude and direction, making it a vector quantity, unlike speed, distance, or scalar time.",
                difficulty = "EASY",
                hint = "Vectors have both magnitude and spatial direction.",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 12
            )
        )
        database.questionDao().insertQuestions(questions)

        // 8. Learning Plan
        val planId = database.learningPlanDao().insertPlan(
            LearningPlanEntity(
                courseId = courseId,
                title = "Today's Targeted Plan",
                date = "Oct 06, 2026",
                isCompleted = false
            )
        )

        database.learningPlanDao().insertPlanItems(
            listOf(
                LearningPlanItemEntity(
                    planId = planId,
                    courseId = courseId,
                    skillId = idNewtonsLaws,
                    title = "Newton's Second Law & Momentum",
                    reason = "Critical prerequisite with low mastery (42%). Required for Dynamics.",
                    itemType = "LESSON",
                    orderIndex = 1
                ),
                LearningPlanItemEntity(
                    planId = planId,
                    courseId = courseId,
                    skillId = idFriction,
                    title = "Static vs Kinetic Friction",
                    reason = "Lowest mastery skill (28%). Needs foundational concept review.",
                    itemType = "LESSON",
                    orderIndex = 2
                ),
                LearningPlanItemEntity(
                    planId = planId,
                    courseId = courseId,
                    skillId = idNewtonsLaws,
                    title = "Adaptive Practice Quiz",
                    reason = "Test Newton's Laws and track mastery improvement.",
                    itemType = "QUIZ",
                    orderIndex = 3
                )
            )
        )

        // 9. Initial friendly greeting in Chat
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                courseId = courseId,
                skillId = idNewtonsLaws,
                role = "assistant",
                content = "Hi! I'm your offline-first LearnMate AI Tutor. I have indexed your Physics notes, syllabus, and practice materials. Ask me to explain Newton's Laws, give examples, provide hints, or test your knowledge!",
                sourceDocumentName = "Physics Notes.pdf",
                sourcePage = 24,
                isOfflineGenerated = true
            )
        )

        return@withContext courseId
    }
}
