package com.example.learning.assessment

import com.example.data.local.entity.QuestionEntity
import com.example.learning.mastery.MasteryCalculator
import com.example.learning.mastery.MasteryUpdateResult

data class QuestionEvaluation(
    val question: QuestionEntity,
    val selectedOptionIndex: Int,
    val isCorrect: Boolean,
    val hintsUsed: Boolean
)

data class QuizEvaluationResult(
    val totalQuestions: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val hintsUsedCount: Int,
    val scorePercentage: Int,
    val questionEvaluations: List<QuestionEvaluation>,
    val masteryResult: MasteryUpdateResult
)

object QuizEvaluator {

    fun evaluateQuiz(
        questions: List<QuestionEntity>,
        userAnswers: Map<Long, Int>, // questionId to selectedOptionIndex
        hintsUsedMap: Map<Long, Boolean>, // questionId to hintUsed
        previousMastery: Int,
        currentStreak: Int = 0
    ): QuizEvaluationResult {
        var correctCount = 0
        var totalHintsUsed = 0

        val evaluations = questions.map { question ->
            val selected = userAnswers[question.id] ?: -1
            val isCorrect = (selected == question.correctAnswerIndex)
            val hintUsed = hintsUsedMap[question.id] ?: false

            if (isCorrect) correctCount++
            if (hintUsed) totalHintsUsed++

            QuestionEvaluation(
                question = question,
                selectedOptionIndex = selected,
                isCorrect = isCorrect,
                hintsUsed = hintUsed
            )
        }

        val total = questions.size
        val scorePercent = if (total > 0) ((correctCount.toFloat() / total) * 100).toInt() else 0

        val dominantDifficulty = if (questions.isNotEmpty()) {
            questions.groupBy { it.difficulty }.maxByOrNull { it.value.size }?.key ?: "MEDIUM"
        } else "MEDIUM"

        val masteryResult = MasteryCalculator.calculateUpdatedMastery(
            previousMastery = previousMastery,
            totalQuestions = total,
            correctAnswers = correctCount,
            difficulty = dominantDifficulty,
            hintsUsed = totalHintsUsed,
            currentStreak = currentStreak
        )

        return QuizEvaluationResult(
            totalQuestions = total,
            correctCount = correctCount,
            incorrectCount = total - correctCount,
            hintsUsedCount = totalHintsUsed,
            scorePercentage = scorePercent,
            questionEvaluations = evaluations,
            masteryResult = masteryResult
        )
    }
}
