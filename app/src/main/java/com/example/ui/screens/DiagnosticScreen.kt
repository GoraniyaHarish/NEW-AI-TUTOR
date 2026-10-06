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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.BrandEmerald
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    courseId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onDiagnosticFinished: () -> Unit
) {
    val questions by viewModel.questions.collectAsState()
    val answers by viewModel.diagnosticAnswers.collectAsState()
    val hintsUsed by viewModel.diagnosticHintsUsed.collectAsState()

    var currentQuestionIndex by remember { mutableStateOf(0) }
    var showHint by remember { mutableStateOf(false) }

    if (questions.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Diagnostic Test") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No diagnostic questions found for this course.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val currentQuestion = questions.getOrNull(currentQuestionIndex) ?: questions.first()
    val selectedOption = answers[currentQuestion.id]
    val isLast = currentQuestionIndex == questions.size - 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Find My Weaknesses", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Question ${currentQuestionIndex + 1} of ${questions.size}",
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
                            text = currentQuestion.difficulty,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentQuestionIndex > 0) {
                        OutlinedButton(
                            onClick = {
                                showHint = false
                                currentQuestionIndex--
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Previous")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (isLast) {
                                viewModel.submitDiagnosticAssessment(courseId) {
                                    onDiagnosticFinished()
                                }
                            } else {
                                showHint = false
                                currentQuestionIndex++
                            }
                        },
                        enabled = selectedOption != null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("diagnostic_next_submit_button")
                    ) {
                        Text(if (isLast) "Complete Diagnostic" else "Next Question")
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
                .testTag("diagnostic_question_screen")
        ) {
            LinearProgressIndicator(
                progress = { (currentQuestionIndex + 1).toFloat() / questions.size.toFloat() },
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
                    // Question text card
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
                                    docName = currentQuestion.sourceDocumentName.ifBlank { "Course Notes" },
                                    page = currentQuestion.sourcePage
                                )

                                TextButton(
                                    onClick = {
                                        showHint = !showHint
                                        viewModel.useDiagnosticHint(currentQuestion.id)
                                    },
                                    modifier = Modifier.testTag("use_hint_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = "Hint",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFFD97706)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hint", color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentQuestion.questionText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )

                            AnimatedVisibility(visible = showHint && currentQuestion.hint.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "💡 Hint: ${currentQuestion.hint}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                    }
                }

                // Options list
                val options = listOf(
                    currentQuestion.optionA,
                    currentQuestion.optionB,
                    currentQuestion.optionC,
                    currentQuestion.optionD
                )

                items(options.size) { optIndex ->
                    val isSelected = selectedOption == optIndex
                    val optionText = options[optIndex]
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
                            .clickable {
                                viewModel.answerDiagnosticQuestion(currentQuestion.id, optIndex)
                            }
                            .testTag("option_$optIndex"),
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
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) BrandBluePrimary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = optionText,
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
