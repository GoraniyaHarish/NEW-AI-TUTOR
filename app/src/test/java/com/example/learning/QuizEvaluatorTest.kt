package com.example.learning

import com.example.data.local.entity.QuestionEntity
import com.example.learning.assessment.QuizEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEvaluatorTest {

    @Test
    fun `quiz evaluator scores correct answers accurately and updates mastery`() {
        val q1 = QuestionEntity(
            id = 101,
            courseId = 1,
            skillId = 1,
            questionText = "What is F=ma?",
            optionA = "Newton's 1st",
            optionB = "Newton's 2nd",
            optionC = "Newton's 3rd",
            optionD = "Gravity",
            correctAnswerIndex = 1,
            explanation = "Second law formula.",
            difficulty = "MEDIUM"
        )
        val q2 = QuestionEntity(
            id = 102,
            courseId = 1,
            skillId = 1,
            questionText = "Is force a vector?",
            optionA = "Yes",
            optionB = "No",
            optionC = "Sometimes",
            optionD = "Only in vacuum",
            correctAnswerIndex = 0,
            explanation = "Force has direction and magnitude.",
            difficulty = "MEDIUM"
        )

        val answers = mapOf(
            101L to 1, // correct
            102L to 0  // correct
        )
        val hints = mapOf(
            101L to false,
            102L to false
        )

        val result = QuizEvaluator.evaluateQuiz(
            questions = listOf(q1, q2),
            userAnswers = answers,
            hintsUsedMap = hints,
            previousMastery = 42,
            currentStreak = 0
        )

        assertEquals(2, result.totalQuestions)
        assertEquals(2, result.correctCount)
        assertEquals(0, result.incorrectCount)
        assertEquals(100, result.scorePercentage)
        assertTrue(result.masteryResult.newMastery > 42)
    }
}
