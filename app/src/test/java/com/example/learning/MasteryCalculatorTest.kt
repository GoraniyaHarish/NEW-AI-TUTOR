package com.example.learning

import com.example.learning.mastery.MasteryCalculator
import com.example.learning.mastery.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryCalculatorTest {

    @Test
    fun `initial zero questions produces not assessed status`() {
        val status = MasteryCalculator.getStatus(0, 0)
        assertEquals(MasteryStatus.NOT_ASSESSED, status)
    }

    @Test
    fun `mastery thresholds evaluate correctly`() {
        assertEquals(MasteryStatus.STRONG, MasteryCalculator.getStatus(75, 1))
        assertEquals(MasteryStatus.STRONG, MasteryCalculator.getStatus(90, 3))
        assertEquals(MasteryStatus.LEARNING, MasteryCalculator.getStatus(50, 1))
        assertEquals(MasteryStatus.LEARNING, MasteryCalculator.getStatus(74, 2))
        assertEquals(MasteryStatus.NEEDS_ATTENTION, MasteryCalculator.getStatus(49, 1))
        assertEquals(MasteryStatus.NEEDS_ATTENTION, MasteryCalculator.getStatus(20, 2))
    }

    @Test
    fun `perfect quiz increases mastery and grants streak bonus`() {
        // Start from 42% (like demo Newton's Laws), answer 5/5 correctly
        val result = MasteryCalculator.calculateUpdatedMastery(
            previousMastery = 42,
            totalQuestions = 5,
            correctAnswers = 5,
            difficulty = "MEDIUM",
            hintsUsed = 0,
            currentStreak = 0
        )

        assertTrue("New mastery should be significantly higher than 42%", result.newMastery > 42)
        assertTrue("New mastery should be >= 65%", result.newMastery >= 65)
        assertEquals(1, result.streak)
        assertTrue(result.delta > 0)
        assertTrue(result.whatChangedSummary.contains("without using hints"))
    }

    @Test
    fun `hints usage reduces mastery bonus`() {
        val withoutHints = MasteryCalculator.calculateUpdatedMastery(
            previousMastery = 40,
            totalQuestions = 4,
            correctAnswers = 4,
            difficulty = "MEDIUM",
            hintsUsed = 0
        )

        val withHints = MasteryCalculator.calculateUpdatedMastery(
            previousMastery = 40,
            totalQuestions = 4,
            correctAnswers = 4,
            difficulty = "MEDIUM",
            hintsUsed = 3
        )

        assertTrue("Mastery without hints should be strictly higher than with hints", withoutHints.newMastery > withHints.newMastery)
    }
}
