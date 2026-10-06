package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val fileName: String,
    val filePath: String,
    val fileType: String, // PDF, TXT, IMAGE, DOC
    val fileSize: String,
    val pageCount: Int = 1,
    val processed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index("courseId")]
)
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val courseId: Long,
    val sourceDocumentName: String,
    val pageNumber: Int,
    val text: String,
    val chunkIndex: Int
)

@Entity(
    tableName = "skills",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId")]
)
data class SkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val name: String,
    val description: String,
    val chapter: String, // e.g. "Mechanics", "Dynamics"
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val sourceDocumentId: Long = 0,
    val sourceDocumentName: String = "",
    val sourcePage: Int = 1,
    val confidence: Float = 0.9f,
    val parentSkillId: Long? = null
)

@Entity(
    tableName = "skill_relations",
    indices = [Index("courseId"), Index("fromSkillId"), Index("toSkillId")]
)
data class SkillRelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val fromSkillId: Long,
    val toSkillId: Long,
    val relationType: String // PREREQUISITE, RELATED, PARENT, CHILD
)

@Entity(
    tableName = "questions",
    indices = [Index("courseId"), Index("skillId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val skillId: Long,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswerIndex: Int, // 0..3
    val explanation: String,
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val hint: String = "",
    val sourceDocumentName: String = "",
    val sourcePage: Int = 1
)

@Entity(
    tableName = "learner_skills",
    primaryKeys = ["skillId", "courseId"],
    indices = [Index("courseId")]
)
data class LearnerSkillEntity(
    val skillId: Long,
    val courseId: Long,
    val masteryScore: Int = 0, // 0..100
    val confidence: Float = 0.5f,
    val attempts: Int = 0,
    val correctAnswers: Int = 0,
    val incorrectAnswers: Int = 0,
    val averageResponseTimeSeconds: Float = 0f,
    val hintsUsed: Int = 0,
    val lastPracticed: Long = 0,
    val streak: Int = 0
)

@Entity(
    tableName = "quiz_attempts",
    indices = [Index("courseId"), Index("skillId")]
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val skillId: Long,
    val skillName: String,
    val score: Int,
    val totalQuestions: Int,
    val beforeMastery: Int,
    val afterMastery: Int,
    val summaryText: String,
    val hintsUsedCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "learning_plans",
    indices = [Index("courseId")]
)
data class LearningPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val title: String,
    val date: String,
    val isCompleted: Boolean = false,
    val generatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "learning_plan_items",
    indices = [Index("planId"), Index("skillId")]
)
data class LearningPlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val courseId: Long,
    val skillId: Long,
    val title: String,
    val reason: String,
    val itemType: String = "LESSON", // LESSON, QUIZ, REVIEW
    val orderIndex: Int = 0,
    val isCompleted: Boolean = false
)

@Entity(
    tableName = "chat_messages",
    indices = [Index("courseId"), Index("skillId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val skillId: Long? = null,
    val role: String, // "user", "assistant"
    val content: String,
    val sourceDocumentName: String? = null,
    val sourcePage: Int? = null,
    val isOfflineGenerated: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
