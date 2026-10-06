package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.LearnMateDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseTest {

    private lateinit var db: LearnMateDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LearnMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert and retrieve course and skills`() {
        runBlocking {
            val courseId = db.courseDao().insertCourse(
                CourseEntity(title = "Chemistry", description = "Organic chemistry")
            )
            val course = db.courseDao().getCourseById(courseId)
            assertNotNull(course)
            assertEquals("Chemistry", course?.title)

            val docId = db.documentDao().insertDocument(
                DocumentEntity(
                    courseId = courseId,
                    fileName = "Organic_Notes.pdf",
                    filePath = "local/path",
                    fileType = "PDF",
                    fileSize = "1.5 MB"
                )
            )

            val skillId = db.skillDao().insertSkill(
                SkillEntity(
                    courseId = courseId,
                    name = "Hydrocarbons",
                    description = "Alkanes, alkenes, alkynes",
                    chapter = "Organic",
                    sourceDocumentId = docId,
                    sourceDocumentName = "Organic_Notes.pdf",
                    sourcePage = 10
                )
            )

            val retrievedSkill = db.skillDao().getSkillById(skillId)
            assertNotNull(retrievedSkill)
            assertEquals("Hydrocarbons", retrievedSkill?.name)
        }
    }

    @Test
    fun `learner skill upsert and chat message storage`() {
        runBlocking {
            val courseId = db.courseDao().insertCourse(
                CourseEntity(title = "Physics", description = "Mechanics")
            )

            db.learnerSkillDao().upsertLearnerSkill(
                LearnerSkillEntity(
                    skillId = 42,
                    courseId = courseId,
                    masteryScore = 75,
                    streak = 3
                )
            )

            val learner = db.learnerSkillDao().getLearnerSkill(42)
            assertNotNull(learner)
            assertEquals(75, learner?.masteryScore)
            assertEquals(3, learner?.streak)

            db.chatMessageDao().insertMessage(
                ChatMessageEntity(
                    courseId = courseId,
                    skillId = 42,
                    role = "user",
                    content = "Explain F=ma"
                )
            )

            db.chatMessageDao().insertMessage(
                ChatMessageEntity(
                    courseId = courseId,
                    skillId = 42,
                    role = "assistant",
                    content = "F=ma relates force to acceleration.",
                    sourceDocumentName = "Physics Notes.pdf",
                    sourcePage = 24
                )
            )
        }
    }
}
