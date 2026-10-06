package com.example.learning.revision

import com.example.data.local.entity.SkillEntity

data class CheatSheetData(
    val title: String,
    val chapter: String,
    val sourceDoc: String,
    val sourcePage: Int,
    val keyFormulas: List<Pair<String, String>>, // Formula to meaning
    val coreDefinitions: List<String>,
    val examPitfalls: List<String>
)

object CheatSheetGenerator {

    fun generateCheatSheet(skill: SkillEntity): CheatSheetData {
        val name = skill.name
        val doc = skill.sourceDocumentName.ifBlank { "Physics Notes.pdf" }
        val page = skill.sourcePage

        return when {
            name.contains("Newton", ignoreCase = true) -> CheatSheetData(
                title = "Newton's Laws & Momentum Cheat-Sheet",
                chapter = skill.chapter,
                sourceDoc = doc,
                sourcePage = page,
                keyFormulas = listOf(
                    "F_net = m · a" to "Second law (constant mass)",
                    "F_net = dp / dt" to "General rate of change of momentum",
                    "F_AB = - F_BA" to "Third law action-reaction pair",
                    "p = m · v" to "Linear momentum (kg·m/s)"
                ),
                coreDefinitions = listOf(
                    "First Law (Inertia): Bodies persist in uniform velocity unless unbalanced net external force acts.",
                    "Mass is the scalar quantitative measure of a body's inertia.",
                    "Action and Reaction always act on TWO DIFFERENT bodies simultaneously, so they never cancel each other out on a single body."
                ),
                examPitfalls = listOf(
                    "Common Trap: Claiming normal force and gravity are an action-reaction pair. (False! Both act on the book; reaction to book's gravity is pulling the Earth up).",
                    "Common Trap: Forgetting vector directions when resolving forces along inclined planes."
                )
            )

            name.contains("Friction", ignoreCase = true) -> CheatSheetData(
                title = "Friction Analysis Cheat-Sheet",
                chapter = skill.chapter,
                sourceDoc = doc,
                sourcePage = page,
                keyFormulas = listOf(
                    "f_s ≤ μ_s · N" to "Static friction (matches applied force up to threshold)",
                    "f_s(max) = μ_s · N" to "Maximum static friction at verge of slipping",
                    "f_k = μ_k · N" to "Kinetic sliding friction",
                    "tan(θ_repose) = μ_s" to "Angle of repose on inclined plane"
                ),
                coreDefinitions = listOf(
                    "Static friction is self-adjusting: if you push with 10 N and max is 40 N, friction is exactly 10 N, NOT 40 N.",
                    "Kinetic friction is generally strictly less than static friction (μ_k < μ_s).",
                    "Normal force N is perpendicular to the contact surface (on incline: N = mg · cos θ)."
                ),
                examPitfalls = listOf(
                    "Common Trap: Using f_s = μ_s · N when the block is stationary and the push is small.",
                    "Common Trap: Assuming friction depends on surface contact area (it is independent of macroscopic area)."
                )
            )

            name.contains("Kinematics", ignoreCase = true) || name.contains("Velocity", ignoreCase = true) -> CheatSheetData(
                title = "Kinematics & Motion Cheat-Sheet",
                chapter = skill.chapter,
                sourceDoc = doc,
                sourcePage = page,
                keyFormulas = listOf(
                    "v = u + a · t" to "First equation of uniform acceleration",
                    "s = u·t + 0.5·a·t²" to "Displacement as function of time",
                    "v² = u² + 2·a·s" to "Velocity as function of displacement",
                    "v_avg = Δs / Δt" to "Average velocity vector"
                ),
                coreDefinitions = listOf(
                    "Displacement is a vector pointing from initial to final position.",
                    "Acceleration is the time derivative of velocity (a = dv/dt).",
                    "At the peak of projectile motion, vertical velocity v_y = 0, but acceleration is still -g."
                ),
                examPitfalls = listOf(
                    "Common Trap: Confusing total distance with net displacement.",
                    "Common Trap: Applying uniform acceleration formulas when acceleration varies with time."
                )
            )

            else -> CheatSheetData(
                title = "${skill.name} Quick Study Sheet",
                chapter = skill.chapter,
                sourceDoc = doc,
                sourcePage = page,
                keyFormulas = listOf(
                    "W = F · d · cos(θ)" to "Work done by constant force",
                    "ΔK = W_net" to "Work-Energy Theorem",
                    "E_mech = K + U" to "Total mechanical energy conservation"
                ),
                coreDefinitions = listOf(
                    "Grounded theoretical principles extracted from uploaded course notes.",
                    "SI standard units: Force in Newtons (N), Energy in Joules (J), Velocity in m/s.",
                    "Check dimensional consistency before solving calculations."
                ),
                examPitfalls = listOf(
                    "Ensure sign conventions are strictly consistent throughout the solution."
                )
            )
        }
    }
}
