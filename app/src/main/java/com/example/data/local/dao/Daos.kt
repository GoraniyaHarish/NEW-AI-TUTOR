package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.LearningPlanEntity
import com.example.data.local.entity.LearningPlanItemEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRelationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY createdAt DESC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: Long): CourseEntity?

    @Query("SELECT * FROM courses ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstCourse(): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: Long)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE courseId = :courseId ORDER BY createdAt DESC")
    fun getDocumentsForCourse(courseId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE courseId = :courseId")
    suspend fun getDocumentsSync(courseId: Long): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("UPDATE documents SET processed = :processed WHERE id = :id")
    suspend fun updateProcessed(id: Long, processed: Boolean)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)
}

@Dao
interface DocumentChunkDao {
    @Query("SELECT * FROM document_chunks WHERE courseId = :courseId ORDER BY documentId, chunkIndex")
    fun getChunksForCourse(courseId: Long): Flow<List<DocumentChunkEntity>>

    @Query("SELECT * FROM document_chunks WHERE courseId = :courseId")
    suspend fun getChunksSync(courseId: Long): List<DocumentChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("DELETE FROM document_chunks WHERE documentId = :docId")
    suspend fun deleteChunksForDocument(docId: Long)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills WHERE courseId = :courseId ORDER BY chapter, id")
    fun getSkillsForCourse(courseId: Long): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE courseId = :courseId")
    suspend fun getSkillsSync(courseId: Long): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE id = :id LIMIT 1")
    suspend fun getSkillById(id: Long): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkills(skills: List<SkillEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntity): Long

    @Query("DELETE FROM skills WHERE courseId = :courseId")
    suspend fun deleteSkillsForCourse(courseId: Long)
}

@Dao
interface SkillRelationDao {
    @Query("SELECT * FROM skill_relations WHERE courseId = :courseId")
    fun getRelationsForCourse(courseId: Long): Flow<List<SkillRelationEntity>>

    @Query("SELECT * FROM skill_relations WHERE courseId = :courseId")
    suspend fun getRelationsSync(courseId: Long): List<SkillRelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<SkillRelationEntity>)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE courseId = :courseId")
    fun getQuestionsForCourse(courseId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE courseId = :courseId")
    suspend fun getQuestionsSync(courseId: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE skillId = :skillId")
    suspend fun getQuestionsForSkill(skillId: Long): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)
}

@Dao
interface LearnerSkillDao {
    @Query("SELECT * FROM learner_skills WHERE courseId = :courseId")
    fun getLearnerSkillsForCourse(courseId: Long): Flow<List<LearnerSkillEntity>>

    @Query("SELECT * FROM learner_skills WHERE courseId = :courseId")
    suspend fun getLearnerSkillsSync(courseId: Long): List<LearnerSkillEntity>

    @Query("SELECT * FROM learner_skills WHERE skillId = :skillId LIMIT 1")
    suspend fun getLearnerSkill(skillId: Long): LearnerSkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearnerSkill(learnerSkill: LearnerSkillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearnerSkills(list: List<LearnerSkillEntity>)
}

@Dao
interface QuizAttemptDao {
    @Query("SELECT * FROM quiz_attempts WHERE courseId = :courseId ORDER BY timestamp DESC")
    fun getAttemptsForCourse(courseId: Long): Flow<List<QuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity): Long
}

@Dao
interface LearningPlanDao {
    @Query("SELECT * FROM learning_plans WHERE courseId = :courseId ORDER BY generatedAt DESC LIMIT 1")
    fun getLatestPlan(courseId: Long): Flow<LearningPlanEntity?>

    @Query("SELECT * FROM learning_plans WHERE courseId = :courseId ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestPlanSync(courseId: Long): LearningPlanEntity?

    @Query("SELECT * FROM learning_plan_items WHERE planId = :planId ORDER BY orderIndex ASC")
    fun getPlanItems(planId: Long): Flow<List<LearningPlanItemEntity>>

    @Query("SELECT * FROM learning_plan_items WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getPlanItemsSync(planId: Long): List<LearningPlanItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: LearningPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanItems(items: List<LearningPlanItemEntity>)

    @Query("UPDATE learning_plan_items SET isCompleted = :completed WHERE id = :itemId")
    suspend fun updateItemCompleted(itemId: Long, completed: Boolean)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE courseId = :courseId ORDER BY timestamp ASC")
    fun getMessagesForCourse(courseId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE courseId = :courseId ORDER BY timestamp ASC")
    suspend fun getMessagesSync(courseId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE courseId = :courseId")
    suspend fun clearMessagesForCourse(courseId: Long)
}
