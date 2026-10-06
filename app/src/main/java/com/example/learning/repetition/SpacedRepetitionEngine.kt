package com.example.learning.repetition

import com.example.data.local.entity.LearnerSkillEntity
import java.util.concurrent.TimeUnit

data class SpacedReviewSchedule(
    val skillId: Long,
    val nextReviewTimestamp: Long,
    val intervalDays: Int,
    val isDueNow: Boolean,
    val dueText: String
)

object SpacedRepetitionEngine {

    /**
     * Calculates the next review date based on consecutive streak & mastery.
     * Intervals: 1 day -> 3 days -> 7 days -> 14 days -> 30 days.
     */
    fun calculateNextReview(learner: LearnerSkillEntity): SpacedReviewSchedule {
        val lastTime = if (learner.lastPracticed > 0) learner.lastPracticed else System.currentTimeMillis()
        val intervalDays = when (learner.streak) {
            0 -> 1
            1 -> 3
            2 -> 7
            3 -> 14
            else -> 30
        }

        val intervalMillis = TimeUnit.DAYS.toMillis(intervalDays.toLong())
        val nextReview = lastTime + intervalMillis
        val now = System.currentTimeMillis()

        val isDue = now >= nextReview || learner.attempts == 0
        val daysUntil = TimeUnit.MILLISECONDS.toDays(nextReview - now).toInt()

        val dueText = when {
            learner.attempts == 0 -> "Initial Diagnostic Due"
            isDue -> "Review Due Today"
            daysUntil <= 1 -> "Review Due Tomorrow"
            else -> "Review Due in $daysUntil days"
        }

        return SpacedReviewSchedule(
            skillId = learner.skillId,
            nextReviewTimestamp = nextReview,
            intervalDays = intervalDays,
            isDueNow = isDue,
            dueText = dueText
        )
    }
}
