package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.util.AppThemeMode
import com.example.ui.screens.CourseListScreen
import com.example.ui.screens.CreateCourseScreen
import com.example.ui.screens.DiagnosticResultScreen
import com.example.ui.screens.DiagnosticScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LearnScreen
import com.example.ui.screens.LessonScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.QuizResultScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SkillMapScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TutorScreen
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AppNavGraph(
    viewModel: MainViewModel,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show Bottom Navigation only on main tabs
    val isBottomNavVisible = currentRoute in listOf(
        Screen.Home.route,
        Screen.Courses.route,
        Screen.Learn.route,
        Screen.Tutor.route,
        Screen.Profile.route
    )

    var tutorInitialPrompt by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomNavVisible) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                  NavigationBar(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("bottom_nav_bar")
                  ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                            label = { Text(text = item.title) },
                            modifier = Modifier.testTag("nav_item_${item.route}")
                        )
                    }
                  }
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
          val contentMaxWidth = if (maxWidth >= 840.dp) 760.dp else 920.dp
          NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().fillMaxHeight()
          ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateNext = {
                        val completed = viewModel.isOnboardingCompleted.value
                        val destination = if (completed) Screen.Home.route else Screen.Onboarding.route
                        navController.navigate(destination) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinishOnboarding = {
                        viewModel.setOnboardingCompleted(true)
                        navController.navigate(Screen.CreateCourse.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            // Bottom Navigation Screens
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToLearn = { navController.navigate(Screen.Learn.route) },
                    onNavigateToCreateCourse = { navController.navigate(Screen.CreateCourse.route) },
                    onNavigateToSkillMap = {
                        val cId = viewModel.activeCourseId.value ?: 1L
                        navController.navigate(Screen.SkillMap.createRoute(cId))
                    },
                    onNavigateToDiagnostic = {
                        val cId = viewModel.activeCourseId.value ?: 1L
                        navController.navigate(Screen.Diagnostic.createRoute(cId))
                    },
                    onNavigateToLesson = { skillId ->
                        navController.navigate(Screen.Lesson.createRoute(skillId))
                    },
                    onNavigateToQuiz = { skillId ->
                        navController.navigate(Screen.Quiz.createRoute(skillId))
                    }
                )
            }

            composable(Screen.Courses.route) {
                CourseListScreen(
                    viewModel = viewModel,
                    onCreateCourseClick = { navController.navigate(Screen.CreateCourse.route) },
                    onCourseSelected = {
                        navController.navigate(Screen.Home.route)
                    }
                )
            }

            composable(Screen.Learn.route) {
                LearnScreen(
                    viewModel = viewModel,
                    onNavigateToLesson = { skillId ->
                        navController.navigate(Screen.Lesson.createRoute(skillId))
                    },
                    onNavigateToQuiz = { skillId ->
                        navController.navigate(Screen.Quiz.createRoute(skillId))
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    },
                    onNavigateToSkillMap = {
                        val cId = viewModel.activeCourseId.value ?: 1L
                        navController.navigate(Screen.SkillMap.createRoute(cId))
                    }
                )
            }

            composable(Screen.Tutor.route) {
                TutorScreen(
                    viewModel = viewModel,
                    initialPrompt = tutorInitialPrompt
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            // Course Creation & Processing
            composable(Screen.CreateCourse.route) {
                CreateCourseScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onCourseCreated = { courseId ->
                        navController.navigate(Screen.Processing.createRoute(courseId)) {
                            popUpTo(Screen.CreateCourse.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.Processing.route,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { backStack ->
                val courseId = backStack.arguments?.getLong("courseId") ?: 1L
                LaunchedEffect(courseId) {
                    viewModel.selectCourse(courseId)
                }
                ProcessingScreen(
                    courseId = courseId,
                    viewModel = viewModel,
                    onProcessingFinished = {
                        navController.navigate(Screen.SkillMap.createRoute(courseId)) {
                            popUpTo(Screen.Processing.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.SkillMap.route,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { backStack ->
                val courseId = backStack.arguments?.getLong("courseId") ?: 1L
                LaunchedEffect(courseId) {
                    viewModel.selectCourse(courseId)
                }
                SkillMapScreen(
                    courseId = courseId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onStartDiagnostic = {
                        navController.navigate(Screen.Diagnostic.createRoute(courseId))
                    },
                    onSkillClick = { skillId ->
                        navController.navigate(Screen.Lesson.createRoute(skillId))
                    }
                )
            }

            // Diagnostic Flow
            composable(
                route = Screen.Diagnostic.route,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { backStack ->
                val courseId = backStack.arguments?.getLong("courseId") ?: 1L
                LaunchedEffect(courseId) {
                    viewModel.selectCourse(courseId)
                }
                DiagnosticScreen(
                    courseId = courseId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onDiagnosticFinished = {
                        navController.navigate(Screen.DiagnosticResult.createRoute(courseId)) {
                            popUpTo(Screen.Diagnostic.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.DiagnosticResult.route,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { backStack ->
                val courseId = backStack.arguments?.getLong("courseId") ?: 1L
                LaunchedEffect(courseId) {
                    viewModel.selectCourse(courseId)
                }
                DiagnosticResultScreen(
                    courseId = courseId,
                    viewModel = viewModel,
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onStartRecommendedLesson = { skillId ->
                        navController.navigate(Screen.Lesson.createRoute(skillId))
                    }
                )
            }

            // Lesson Screen
            composable(
                route = Screen.Lesson.route,
                arguments = listOf(navArgument("skillId") { type = NavType.LongType })
            ) { backStack ->
                val skillId = backStack.arguments?.getLong("skillId") ?: 1L
                LessonScreen(
                    skillId = skillId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onStartPractice = { sId ->
                        navController.navigate(Screen.Quiz.createRoute(sId))
                    },
                    onAskTutor = { _, prompt ->
                        tutorInitialPrompt = prompt
                        navController.navigate(Screen.Tutor.route)
                    }
                )
            }

            // Quiz & Results
            composable(
                route = Screen.Quiz.route,
                arguments = listOf(navArgument("skillId") { type = NavType.LongType })
            ) { backStack ->
                val skillId = backStack.arguments?.getLong("skillId") ?: 1L
                QuizScreen(
                    skillId = skillId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onQuizCompleted = { score, total, prev, next ->
                        navController.navigate(
                            Screen.QuizResult.createRoute(skillId, score, total, prev, next)
                        ) {
                            popUpTo(Screen.Quiz.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.QuizResult.route,
                arguments = listOf(
                    navArgument("skillId") { type = NavType.LongType },
                    navArgument("score") { type = NavType.IntType },
                    navArgument("total") { type = NavType.IntType },
                    navArgument("prevMastery") { type = NavType.IntType },
                    navArgument("newMastery") { type = NavType.IntType }
                )
            ) { backStack ->
                val skillId = backStack.arguments?.getLong("skillId") ?: 1L
                val score = backStack.arguments?.getInt("score") ?: 0
                val total = backStack.arguments?.getInt("total") ?: 1
                val prev = backStack.arguments?.getInt("prevMastery") ?: 0
                val next = backStack.arguments?.getInt("newMastery") ?: 0

                QuizResultScreen(
                    skillId = skillId,
                    score = score,
                    total = total,
                    prevMastery = prev,
                    newMastery = next,
                    viewModel = viewModel,
                    onNavigateBackToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onPracticeAgain = { sId ->
                        navController.navigate(Screen.Quiz.createRoute(sId))
                    }
                )
            }

            // Search Screen
            composable("search") {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onAskTutorWithSnippet = { snippetPrompt ->
                        tutorInitialPrompt = snippetPrompt
                        navController.navigate(Screen.Tutor.route)
                    }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
          }
        }
}
