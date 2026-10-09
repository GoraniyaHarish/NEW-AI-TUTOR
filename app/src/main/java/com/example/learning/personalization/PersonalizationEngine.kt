package com.example.learning.personalization

import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.LearningPlanEntity
import com.example.data.local.entity.LearningPlanItemEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRelationEntity
import com.example.learning.mastery.MasteryCalculator
import com.example.learning.mastery.MasteryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SkillRecommendation(
    val skill: SkillEntity,
    val currentMastery: Int,
    val status: MasteryStatus,
    val priorityRank: Int,
    val reason: String
)

data class LearningDiagnostics(
    val overallMastery: Int,
    val strongSkills: List<Pair<SkillEntity, Int>>,
    val weakSkills: List<Pair<SkillEntity, Int>>,
    val unassessedSkills: List<SkillEntity>,
    val recommendations: List<SkillRecommendation>
)

object PersonalizationEngine {

    fun analyzeLearner(
        skills: List<SkillEntity>,
        relations: List<SkillRelationEntity>,
        learnerSkills: List<LearnerSkillEntity>
    ): LearningDiagnostics {
        val learnerMap = learnerSkills.associateBy { it.skillId }
        val strongList = mutableListOf<Pair<SkillEntity, Int>>()
        val weakList = mutableListOf<Pair<SkillEntity, Int>>()
        val unassessedList = mutableListOf<SkillEntity>()

        var totalMastery = 0
        var assessedCount = 0

        for (skill in skills) {
            val learner = learnerMap[skill.id]
            val score = learner?.masteryScore ?: 0
            val attempts = learner?.attempts ?: 0
            val status = MasteryCalculator.getStatus(score, attempts)

            when (status) {
                MasteryStatus.STRONG -> {
                    strongList.add(skill to score)
                    totalMastery += score
                    assessedCount++
                }
                MasteryStatus.LEARNING -> {
                    totalMastery += score
                    assessedCount++
                }
                MasteryStatus.NEEDS_ATTENTION -> {
                    weakList.add(skill to score)
                    totalMastery += score
                    assessedCount++
                }
                MasteryStatus.NOT_ASSESSED -> {
                    unassessedList.add(skill)
                }
            }
        }

        val overallMastery = if (skills.isEmpty()) 0 else {
            // Unassessed count as 0 in overall progress towards 100%
            (totalMastery.toFloat() / skills.size).toInt()
        }

        val recommendations = getRecommendations(skills, relations, learnerSkills)

        return LearningDiagnostics(
            overallMastery = overallMastery,
            strongSkills = strongList.sortedByDescending { it.second },
            weakSkills = weakList.sortedBy { it.second },
            unassessedSkills = unassessedList,
            recommendations = recommendations
        )
    }

    /**
     * Prioritizes next skills to study:
     * 1. Skills that are prerequisites for other skills and currently have low mastery (< 50%)
     * 2. Other weak skills (< 50%)
     * 3. Unassessed skills whose prerequisites have been completed
     * 4. In-progress skills for reinforcement
     */
    fun getRecommendations(
        skills: List<SkillEntity>,
        relations: List<SkillRelationEntity>,
        learnerSkills: List<LearnerSkillEntity>,
        limit: Int = 3
    ): List<SkillRecommendation> {
        if (skills.isEmpty()) return emptyList()

        val learnerMap = learnerSkills.associateBy { it.skillId }
        val skillMap = skills.associateBy { it.id }

        // Find prerequisite connections: fromSkillId is PREREQUISITE for toSkillId
        val prereqCounts = mutableMapOf<Long, Int>()
        for (rel in relations) {
            if (rel.relationType.equals("PREREQUISITE", ignoreCase = true)) {
                prereqCounts[rel.fromSkillId] = (prereqCounts[rel.fromSkillId] ?: 0) + 1
            }
        }

        val scoredList = skills.map { skill ->
            val learner = learnerMap[skill.id]
            val mastery = learner?.masteryScore ?: 0
            val attempts = learner?.attempts ?: 0
            val isPrereqForCount = prereqCounts[skill.id] ?: 0
            val daysSincePractice = learner?.lastPracticed?.takeIf { it > 0L }
                ?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / MILLIS_PER_DAY).toInt() }
            val reviewIntervalDays = when {
                mastery < 50 -> 1
                mastery < 75 -> 3
                mastery < 90 -> 7
                else -> 14
            } * (1 + (learner?.streak ?: 0).coerceAtMost(3) / 2)
            val reviewIsDue = attempts > 0 && daysSincePractice != null && daysSincePractice >= reviewIntervalDays

            // Priority score calculation: higher means more urgent
            var priorityWeight = 0
            var reason = ""

            when {
                attempts > 0 && mastery < 50 -> {
                    if (isPrereqForCount > 0) {
                        priorityWeight = 100 + (50 - mastery) + (isPrereqForCount * 10)
                        reason = "This skill has low mastery ($mastery%) and is a critical prerequisite for $isPrereqForCount subsequent topic(s)."
                    } else {
                        priorityWeight = 80 + (50 - mastery)
                        reason = "Mastery is currently at $mastery%. Focus on reviewing core definitions and solving practice problems."
                    }
                }
                attempts == 0 -> {
                    // Check if its prerequisites are satisfied
                    val neededPrereqs = relations
                        .filter { it.toSkillId == skill.id && it.relationType.equals("PREREQUISITE", ignoreCase = true) }
                        .mapNotNull { skillMap[it.fromSkillId] }

                    val unmetPrereq = neededPrereqs.firstOrNull {
                        val pLearner = learnerMap[it.id]
                        (pLearner?.masteryScore ?: 0) < 50
                    }

                    if (unmetPrereq != null) {
                        priorityWeight = 20
                        reason = "Prerequisite '${unmetPrereq.name}' is recommended first before starting ${skill.name}."
                    } else {
                        priorityWeight = 70 + (isPrereqForCount * 5)
                        reason = "Foundational topic ready to be learned. Diagnostic test recommended."
                    }
                }
                reviewIsDue -> {
                    priorityWeight = 65 + (daysSincePractice!! - reviewIntervalDays).coerceAtMost(30)
                    reason = "Spaced review is due: last practiced $daysSincePractice day(s) ago after reaching $mastery% mastery."
                }
                mastery in 50..74 -> {
                    priorityWeight = 40 + (75 - mastery)
                    reason = "In-progress ($mastery%). A quick adaptive quiz will help push this skill to Strong status."
                }
                else -> {
                    priorityWeight = 10
                    reason = "Strong mastery ($mastery%). Ready for periodic spaced review."
                }
            }

            Triple(skill, priorityWeight, reason)
        }

        return scoredList
            .sortedByDescending { it.second }
            .take(limit)
            .mapIndexed { index, (skill, _, reason) ->
                val learner = learnerMap[skill.id]
                val mastery = learner?.masteryScore ?: 0
                val attempts = learner?.attempts ?: 0
                SkillRecommendation(
                    skill = skill,
                    currentMastery = mastery,
                    status = MasteryCalculator.getStatus(mastery, attempts),
                    priorityRank = index + 1,
                    reason = reason
                )
            }
    }

    fun buildDailyLearningPlan(
        courseId: Long,
        skills: List<SkillEntity>,
        relations: List<SkillRelationEntity>,
        learnerSkills: List<LearnerSkillEntity>
    ): Pair<LearningPlanEntity, List<LearningPlanItemEntity>> {
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        val plan = LearningPlanEntity(
            courseId = courseId,
            title = "Personalized Study Session",
            date = dateStr,
            isCompleted = false
        )

        val recommendations = getRecommendations(skills, relations, learnerSkills, limit = 3)
        val items = mutableListOf<LearningPlanItemEntity>()

        var order = 1
        for (rec in recommendations) {
            val isWeak = rec.currentMastery < 50
            val isDueReview = rec.reason.startsWith("Spaced review is due")
            if (isWeak || rec.status == MasteryStatus.NOT_ASSESSED) {
                items.add(
                    LearningPlanItemEntity(
                        planId = 0,
                        courseId = courseId,
                        skillId = rec.skill.id,
                        title = "Study: ${rec.skill.name}",
                        reason = rec.reason,
                        itemType = "LESSON",
                        orderIndex = order++
                    )
                )
            }
            items.add(LearningPlanItemEntity(
                planId = 0,
                courseId = courseId,
                skillId = rec.skill.id,
                title = if (isDueReview) "Spaced Review: ${rec.skill.name}" else "Practice Quiz: ${rec.skill.name}",
                reason = if (isDueReview) rec.reason else "Practice from the course material to check understanding and update mastery.",
                itemType = if (isDueReview) "REVIEW" else "QUIZ",
                orderIndex = order++
            ))
        }

        if (items.isEmpty() && skills.isNotEmpty()) {
            val first = skills.first()
            items.add(
                LearningPlanItemEntity(
                    planId = 0,
                    courseId = courseId,
                    skillId = first.id,
                    title = "Review: ${first.name}",
                    reason = "Reinforce foundational material.",
                    itemType = "REVIEW",
                    orderIndex = 1
                )
            )
        }

        return plan to items
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
}
