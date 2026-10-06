package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.dao.CourseDao
import com.example.data.local.dao.DocumentChunkDao
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.LearnerSkillDao
import com.example.data.local.dao.LearningPlanDao
import com.example.data.local.dao.QuestionDao
import com.example.data.local.dao.QuizAttemptDao
import com.example.data.local.dao.SkillDao
import com.example.data.local.dao.SkillRelationDao
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

@Database(
    entities = [
        CourseEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
        SkillEntity::class,
        SkillRelationEntity::class,
        QuestionEntity::class,
        LearnerSkillEntity::class,
        QuizAttemptEntity::class,
        LearningPlanEntity::class,
        LearningPlanItemEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LearnMateDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun documentDao(): DocumentDao
    abstract fun documentChunkDao(): DocumentChunkDao
    abstract fun skillDao(): SkillDao
    abstract fun skillRelationDao(): SkillRelationDao
    abstract fun questionDao(): QuestionDao
    abstract fun learnerSkillDao(): LearnerSkillDao
    abstract fun quizAttemptDao(): QuizAttemptDao
    abstract fun learningPlanDao(): LearningPlanDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: LearnMateDatabase? = null

        fun getInstance(context: Context): LearnMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LearnMateDatabase::class.java,
                    "learnmate_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
