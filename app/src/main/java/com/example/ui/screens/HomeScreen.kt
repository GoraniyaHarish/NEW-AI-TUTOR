package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.SkillEntity
import com.example.ui.viewmodel.MainViewModel
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToLearn: () -> Unit,
    onNavigateToCreateCourse: () -> Unit,
    onNavigateToSkillMap: () -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    onNavigateToLesson: (Long) -> Unit,
    onNavigateToQuiz: (Long) -> Unit
) {
    val activeCourse by viewModel.activeCourse.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isSimulatedOffline by viewModel.isSimulatedOffline.collectAsState()
    val cloudConfigured by viewModel.isCloudAIAvailable.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val planItems by viewModel.planItems.collectAsState()
    val skills by viewModel.skills.collectAsState()
    val learnerSkills by viewModel.learnerSkills.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val assessed = learnerSkills.filter { it.attempts > 0 }
    val mastery = assessed.takeIf { it.isNotEmpty() }?.map { it.masteryScore }?.average()?.toInt()
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("home_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Make today count.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "LearnMate", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(13.dp).size(25.dp))
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleOfflineSimulation() },
                shape = RoundedCornerShape(15.dp),
                color = if (isOnline) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            isSimulatedOffline -> "Offline preview is on · tap here to restore your network view"
                            cloudConfigured -> "Internet connected · Gemini configured; a successful reply confirms access"
                            isOnline -> "Internet connected · LearnMate is ready in offline tutor mode"
                            else -> "Offline · your saved notes and learning tools remain available"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            val course = activeCourse
            if (course == null) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("empty_course_card"),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                        Text("Your next big idea starts here.", style = MaterialTheme.typography.titleLarge)
                        Text("Create a course from your own notes. LearnMate will keep its map, questions and tutor grounded in those materials.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onNavigateToCreateCourse, modifier = Modifier.fillMaxWidth().testTag("create_first_course_button")) { Text("Create course from notes") }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("current_course_card"),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("YOUR STUDY SPACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .82f)) {
                                Text(if (mastery == null) "Not assessed" else "$mastery% mastery", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                        Text(course.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(course.description.ifBlank { "A focused place for your materials and progress." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiniStat(Icons.Default.Description, "${documents.size} materials")
                            MiniStat(Icons.Default.MenuBook, "${skills.size} topics")
                            if (mastery != null) MiniStat(Icons.Default.CheckCircle, "${assessed.size} practiced")
                        }
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 360.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onNavigateToLearn, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Continue learning")
                                    }
                                    OutlinedButton(onClick = onNavigateToSkillMap, modifier = Modifier.fillMaxWidth()) { Text("View study map") }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = onNavigateToLearn, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Continue")
                                    }
                                    OutlinedButton(onClick = onNavigateToSkillMap, modifier = Modifier.weight(1f)) { Text("View study map") }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeCourse != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Your next steps", style = MaterialTheme.typography.titleLarge)
                        Text("Built from what you’ve studied so far", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${planItems.count { !it.isCompleted }} left", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (planItems.isEmpty()) {
                item { HintCard("Add a readable document to create a study plan, or explore a topic from your learning map.") }
            } else {
                items(planItems.take(4), key = { "plan-${it.id}" }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (item.itemType.equals("QUIZ", true)) onNavigateToQuiz(item.skillId) else onNavigateToLesson(item.skillId)
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(if (item.itemType.equals("QUIZ", true)) Icons.Default.CheckCircle else Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(21.dp))
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(if (item.isCompleted) "Done" else "Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (skills.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Pick up a topic", style = MaterialTheme.typography.titleLarge)
                        Text("${skills.size} mapped", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(skills.take(5), key = { "skill-${it.id}" }) { skill ->
                    TopicCard(skill, learnerSkills.firstOrNull { it.skillId == skill.id }?.masteryScore, learnerSkills.firstOrNull { it.skillId == skill.id }?.attempts ?: 0) {
                        onNavigateToLesson(skill.id)
                    }
                }
                item {
                    OutlinedButton(onClick = onNavigateToDiagnostic, modifier = Modifier.fillMaxWidth()) { Text("Check what you know") }
                }
            }
            if (diagnostics == null && skills.isEmpty()) {
                item { HintCard("No topics were found in this course yet. Check that your document contains selectable text, then import it again if it is a scan.") }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .68f)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun HintCard(message: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(17.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TopicCard(skill: SkillEntity, mastery: Int?, attempts: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(22.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(skill.chapter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (attempts == 0) "Start" else "$mastery%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
