package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")

    // Bottom Navigation Destinations
    object Home : Screen("home")
    object Courses : Screen("courses")
    object Learn : Screen("learn")
    object Tutor : Screen("tutor")
    object Profile : Screen("profile")

    // Feature Destinations
    object CreateCourse : Screen("create_course")
    object Processing : Screen("processing/{courseId}") {
        fun createRoute(courseId: Long) = "processing/$courseId"
    }
    object SkillMap : Screen("skill_map/{courseId}") {
        fun createRoute(courseId: Long) = "skill_map/$courseId"
    }
    object Diagnostic : Screen("diagnostic/{courseId}") {
        fun createRoute(courseId: Long) = "diagnostic/$courseId"
    }
    object DiagnosticResult : Screen("diagnostic_result/{courseId}") {
        fun createRoute(courseId: Long) = "diagnostic_result/$courseId"
    }
    object Lesson : Screen("lesson/{skillId}") {
        fun createRoute(skillId: Long) = "lesson/$skillId"
    }
    object Quiz : Screen("quiz/{skillId}") {
        fun createRoute(skillId: Long) = "quiz/$skillId"
    }
    object QuizResult : Screen("quiz_result/{skillId}/{score}/{total}/{prevMastery}/{newMastery}") {
        fun createRoute(skillId: Long, score: Int, total: Int, prevMastery: Int, newMastery: Int) =
            "quiz_result/$skillId/$score/$total/$prevMastery/$newMastery"
    }
    object Settings : Screen("settings")
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, "Home", Icons.Default.Home),
    BottomNavItem(Screen.Courses.route, "My Courses", Icons.Default.FolderSpecial),
    BottomNavItem(Screen.Learn.route, "Learn", Icons.Default.School),
    BottomNavItem(Screen.Tutor.route, "AI Tutor", Icons.Default.AutoAwesome),
    BottomNavItem(Screen.Profile.route, "Profile", Icons.Default.AccountCircle)
)
