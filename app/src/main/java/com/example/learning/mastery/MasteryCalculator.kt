package com.example.learning.mastery

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class MasteryStatus(val label: String) {
    STRONG("Strong"),
    LEARNING("Learning"),
    NEEDS_ATTENTION("Needs Attention"),
    NOT_ASSESSED("Not Assessed")
}

data class MasteryUpdateResult(
    val previousMastery: Int,
    val newMastery: Int,
    val delta: Int,
    val whatChangedSummary: String,
    val streak: Int,
    val status: MasteryStatus
)

object MasteryCalculator {

    fun getStatus(masteryScore: Int, attempts: Int): MasteryStatus {
        return when {
            attempts == 0 -> MasteryStatus.NOT_ASSESSED
            masteryScore >= 75 -> MasteryStatus.STRONG
            masteryScore >= 50 -> MasteryStatus.LEARNING
            else -> MasteryStatus.NEEDS_ATTENTION
        }
    }

    /**
     * Calculates updated mastery score using a multi-factor heuristic:
     * - Base correctness ratio
     * - Difficulty modifier (EASY=0.85, MEDIUM=1.0, HARD=1.2)
     * - Hints penalty (-4% per hint used, min 0)
     * - Consecutive streak bonus (+2% per streak item, max +10%)
     * - Exponential moving average with previous mastery (alpha = 0.6 for responsiveness)
     */
    fun calculateUpdatedMastery(
        previousMastery: Int,
        totalQuestions: Int,
        correctAnswers: Int,
        difficulty: String = "MEDIUM",
        hintsUsed: Int = 0,
        currentStreak: Int = 0
    ): MasteryUpdateResult {
        if (totalQuestions <= 0) {
            val status = getStatus(previousMastery, if (previousMastery > 0) 1 else 0)
            return MasteryUpdateResult(
                previousMastery = previousMastery,
                newMastery = previousMastery,
                delta = 0,
                whatChangedSummary = "No questions completed.",
                streak = currentStreak,
                status = status
            )
        }

        val rawRatio = correctAnswers.toFloat() / totalQuestions.toFloat()

        val diffMultiplier = when (difficulty.uppercase()) {
            "HARD" -> 1.20f
            "EASY" -> 0.85f
            else -> 1.0f
        }

        // Hints penalty: each hint deducts up to 4% from the performance score
        val hintPenalty = min(0.25f, hintsUsed * 0.04f)
        val adjustedRatio = max(0f, (rawRatio * diffMultiplier) - hintPenalty)

        // Streak bonus: up to +10%
        val isAllCorrect = (correctAnswers == totalQuestions)
        val newStreak = if (isAllCorrect) currentStreak + 1 else 0
        val streakBonus = min(10, newStreak * 2)

        // Quiz score on 0..100 scale
        val targetScore = min(100f, (adjustedRatio * 100f) + streakBonus)

        // Blend with previous mastery: if first attempt, take targetScore; otherwise blend 60% new, 40% prev
        val blended = if (previousMastery == 0) {
            targetScore
        } else {
            (0.6f * targetScore) + (0.4f * previousMastery.toFloat())
        }

        val clamped = min(100, max(0, blended.roundToInt()))
        val delta = clamped - previousMastery

        val summary = buildString {
            append("You answered $correctAnswers/$totalQuestions correctly")
            if (difficulty.isNotBlank()) append(" on $difficulty questions.")
            if (hintsUsed == 0) {
                append(" Solved without using hints (+accuracy bonus).")
            } else {
                append(" $hintsUsed hint(s) used.")
            }
            if (newStreak > 1) {
                append(" 🔥 Active streak of $newStreak!")
            }
        }

        return MasteryUpdateResult(
            previousMastery = previousMastery,
            newMastery = clamped,
            delta = delta,
            whatChangedSummary = summary,
            streak = newStreak,
            status = getStatus(clamped, 1)
        )
    }
}
