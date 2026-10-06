package com.example.learning

import com.example.data.local.entity.LearnerSkillEntity
import com.example.learning.repetition.SpacedRepetitionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SpacedRepetitionEngineTest {

    @Test
    fun `zero attempts skill is due immediately for diagnostic`() {
        val learner = LearnerSkillEntity(
            skillId = 1,
            courseId = 1,
            attempts = 0,
            streak = 0
        )
        val schedule = SpacedRepetitionEngine.calculateNextReview(learner)
        assertTrue(schedule.isDueNow)
        assertEquals("Initial Diagnostic Due", schedule.dueText)
    }

    @Test
    fun `streak increases review interval`() {
        val now = System.currentTimeMillis()
        val learnerStreak0 = LearnerSkillEntity(skillId = 1, courseId = 1, attempts = 1, streak = 0, lastPracticed = now)
        val learnerStreak1 = LearnerSkillEntity(skillId = 1, courseId = 1, attempts = 2, streak = 1, lastPracticed = now)
        val learnerStreak2 = LearnerSkillEntity(skillId = 1, courseId = 1, attempts = 3, streak = 2, lastPracticed = now)
        val learnerStreak3 = LearnerSkillEntity(skillId = 1, courseId = 1, attempts = 4, streak = 3, lastPracticed = now)

        val sched0 = SpacedRepetitionEngine.calculateNextReview(learnerStreak0)
        val sched1 = SpacedRepetitionEngine.calculateNextReview(learnerStreak1)
        val sched2 = SpacedRepetitionEngine.calculateNextReview(learnerStreak2)
        val sched3 = SpacedRepetitionEngine.calculateNextReview(learnerStreak3)

        assertEquals(1, sched0.intervalDays)
        assertEquals(3, sched1.intervalDays)
        assertEquals(7, sched2.intervalDays)
        assertEquals(14, sched3.intervalDays)
    }

    @Test
    fun `past review date marks skill due today`() {
        // Practiced 10 days ago with streak 1 (interval = 3 days)
        val tenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)
        val learner = LearnerSkillEntity(
            skillId = 1,
            courseId = 1,
            attempts = 2,
            streak = 1,
            lastPracticed = tenDaysAgo
        )
        val schedule = SpacedRepetitionEngine.calculateNextReview(learner)
        assertTrue(schedule.isDueNow)
        assertEquals("Review Due Today", schedule.dueText)
    }
}
