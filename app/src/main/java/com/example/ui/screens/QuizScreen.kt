package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.QuestionEntity
import com.example.ui.components.SourceCitationChip
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    skillId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onQuizCompleted: (Int, Int, Int, Int) -> Unit
) {
    val skills by viewModel.skills.collectAsState()
    val allQuestions by viewModel.questions.collectAsState()
    val learnerSkills by viewModel.learnerSkills.collectAsState()

    val skill = skills.find { it.id == skillId } ?: skills.firstOrNull()
    val learner = learnerSkills.find { it.skillId == skill?.id }
    val currentMastery = learner?.masteryScore ?: 0

    // Repair legacy offline questions created by earlier app versions. Those rows
    // stored only Yes/No in A/B and left C/D blank, so changing the generator alone
    // would not fix quizzes already saved on a student's device.
    val quizQuestions = remember(allQuestions, skillId) {
        allQuestions.filter { it.skillId == skillId }.map { question ->
            val isLegacyEvidenceQuestion =
                question.optionC.isBlank() &&
                question.optionD.isBlank() &&
                question.questionText.startsWith("Your notes say:")
            if (!isLegacyEvidenceQuestion) {
                question
            } else {
                val claim = question.questionText
                    .removePrefix("Your notes say:")
                    .substringBefore("\n\nIs this fact stated in the source?")
                    .trim()
                    .trim('"')
                val supported = "Explicitly supported by the material"
                val options = listOf(
                    supported,
                    "Contradicted by the material",
                    "Not mentioned in the material",
                    "Only implied, not directly stated"
                ).shuffled(java.util.Random(question.id))
                question.copy(
                    questionText = "What evidence status best describes this claim in the source?\n\n\"$claim\"",
                    optionA = options[0],
                    optionB = options[1],
                    optionC = options[2],
                    optionD = options[3],
                    correctAnswerIndex = options.indexOf(supported),
                    explanation = "This claim was extracted from page ${question.sourcePage} of ${question.sourceDocumentName}, so it is explicitly supported by the material."
                )
            }
        }
    }

    val userAnswers = remember(skillId) { mutableStateMapOf<Long, Int>() }
    val hintsUsed = remember(skillId) { mutableStateMapOf<Long, Boolean>() }

    var currentIndex by remember(skillId) { mutableStateOf(0) }
    var showHint by remember(skillId) { mutableStateOf(false) }

    if (quizQuestions.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Practice Quiz") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                        Text("Practice is not ready yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "LearnMate needs readable facts from this course before it can make a grounded quiz. Add clear notes or try another document.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onNavigateBack) { Text("Back to learning map") }
                    }
            }
        }
        return
    }

    val currentQ = quizQuestions.getOrNull(currentIndex) ?: quizQuestions.first()
    val selectedOption = userAnswers[currentQ.id]
    val isLast = currentIndex == quizQuestions.size - 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(skill?.name ?: "Adaptive Quiz", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = "Question ${currentIndex + 1} of ${quizQuestions.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Mastery: $currentMastery%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentIndex > 0) {
                        OutlinedButton(
                            onClick = {
                                showHint = false
                                currentIndex--
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = {
                            if (isLast) {
                                val cId = skill?.courseId ?: 1L
                                viewModel.submitPracticeQuiz(
                                    skillId = skill?.id ?: skillId,
                                    courseId = cId,
                                    questions = quizQuestions,
                                    userAnswers = userAnswers,
                                    hintsUsedMap = hintsUsed,
                                    onResultReady = { score, total, before, after ->
                                        onQuizCompleted(score, total, before, after)
                                    }
                                )
                            } else {
                                showHint = false
                                currentIndex++
                            }
                        },
                        enabled = selectedOption != null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.35f).testTag("quiz_submit_button")
                    ) {
                        Text(if (isLast) "Finish quiz" else "Next")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isLast) Icons.Default.Check else Icons.Default.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
                .testTag("quiz_screen")
        ) {
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / quizQuestions.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BrandBluePrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SourceCitationChip(
                                    docName = currentQ.sourceDocumentName.ifBlank { "Course Notes" },
                                    page = currentQ.sourcePage
                                )

                                TextButton(
                                    onClick = {
                                        showHint = !showHint
                                        hintsUsed[currentQ.id] = true
                                    },
                                    modifier = Modifier.testTag("quiz_hint_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = "Hint",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hint", color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentQ.questionText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )

                            AnimatedVisibility(visible = showHint && currentQ.hint.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "💡 ${currentQ.hint}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                    }
                }

                val options = listOf(currentQ.optionA, currentQ.optionB, currentQ.optionC, currentQ.optionD)
                    .withIndex().filter { it.value.isNotBlank() }
                items(options.size) { displayIndex ->
                    val optIndex = options[displayIndex].index
                    val isSelected = selectedOption == optIndex
                    val text = options[displayIndex].value
                    val letter = ('A' + optIndex).toString()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) BrandBluePrimary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { userAnswers[currentQ.id] = optIndex }
                            .testTag("quiz_option_$optIndex"),
                        color = if (isSelected) BrandBluePrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) BrandBluePrimary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = text,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
