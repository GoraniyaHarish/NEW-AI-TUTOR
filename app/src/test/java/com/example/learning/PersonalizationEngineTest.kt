package com.example.learning

import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRelationEntity
import com.example.learning.personalization.PersonalizationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationEngineTest {

    @Test
    fun `weak skills with prerequisite status are prioritized first`() {
        val s1 = SkillEntity(id = 1, courseId = 1, name = "Kinematics", description = "", chapter = "Mechanics")
        val s2 = SkillEntity(id = 2, courseId = 1, name = "Newton's Laws", description = "", chapter = "Dynamics")
        val s3 = SkillEntity(id = 3, courseId = 1, name = "Friction", description = "", chapter = "Dynamics")

        val relations = listOf(
            SkillRelationEntity(id = 1, courseId = 1, fromSkillId = 2, toSkillId = 3, relationType = "PREREQUISITE")
        )

        val learnerSkills = listOf(
            LearnerSkillEntity(skillId = 1, courseId = 1, masteryScore = 85, attempts = 5),
            LearnerSkillEntity(skillId = 2, courseId = 1, masteryScore = 42, attempts = 3),
            LearnerSkillEntity(skillId = 3, courseId = 1, masteryScore = 28, attempts = 3)
        )

        val recommendations = PersonalizationEngine.getRecommendations(
            skills = listOf(s1, s2, s3),
            relations = relations,
            learnerSkills = learnerSkills,
            limit = 3
        )

        assertNotNull(recommendations)
        assertTrue(recommendations.isNotEmpty())

        // Newton's Laws (id=2) has low mastery AND is a prerequisite for Friction, so it should rank highest
        val topRec = recommendations.first()
        assertEquals("Newton's Laws", topRec.skill.name)
        assertTrue(topRec.reason.contains("prerequisite") || topRec.reason.contains("low mastery"))
    }

    @Test
    fun `daily plan contains study lessons and adaptive quiz items`() {
        val s1 = SkillEntity(id = 1, courseId = 1, name = "Newton's Laws", description = "", chapter = "Dynamics")
        val learnerSkills = listOf(
            LearnerSkillEntity(skillId = 1, courseId = 1, masteryScore = 40, attempts = 2)
        )

        val (plan, items) = PersonalizationEngine.buildDailyLearningPlan(
            courseId = 1,
            skills = listOf(s1),
            relations = emptyList(),
            learnerSkills = learnerSkills
        )

        assertNotNull(plan)
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.itemType == "LESSON" })
        assertTrue(items.any { it.itemType == "QUIZ" })
    }

    @Test
    fun `daily plan schedules an evidence based spaced review when practice is overdue`() {
        val skill = SkillEntity(id = 8, courseId = 2, name = "Cell division", description = "", chapter = "Biology")
        val learner = LearnerSkillEntity(
            skillId = skill.id,
            courseId = 2,
            masteryScore = 92,
            attempts = 4,
            lastPracticed = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        )

        val (_, items) = PersonalizationEngine.buildDailyLearningPlan(2, listOf(skill), emptyList(), listOf(learner))

        assertTrue(items.any { it.itemType == "REVIEW" && it.title.contains("Cell division") })
        assertTrue(items.any { it.reason.contains("last practiced") })
    }
}
