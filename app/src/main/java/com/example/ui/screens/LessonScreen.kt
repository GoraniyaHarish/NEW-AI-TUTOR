package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.LessonExplanation
import com.example.core.audio.TtsReader
import com.example.data.local.entity.LearnerSkillEntity
import com.example.learning.mastery.MasteryCalculator
import com.example.learning.repetition.SpacedRepetitionEngine
import com.example.learning.revision.CheatSheetData
import com.example.learning.revision.CheatSheetGenerator
import com.example.ui.components.MasteryBadge
import com.example.ui.components.SourceCitationChip
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandEmerald
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    skillId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onStartPractice: (Long) -> Unit,
    onAskTutor: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val ttsReader = remember { TtsReader(context) }
    val isTtsPlaying by ttsReader.isPlaying.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            ttsReader.shutdown()
        }
    }

    val skills by viewModel.skills.collectAsState()
    val learnerSkills by viewModel.learnerSkills.collectAsState()
    val skill = skills.find { it.id == skillId } ?: skills.firstOrNull()

    val learner = learnerSkills.find { it.skillId == skill?.id }
        ?: LearnerSkillEntity(skillId = skillId, courseId = skill?.courseId ?: 1L)
    val mastery = learner.masteryScore
    val attempts = learner.attempts
    val status = MasteryCalculator.getStatus(mastery, attempts)
    val repetitionSchedule = remember(learner) {
        SpacedRepetitionEngine.calculateNextReview(learner)
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Notes & Lesson, 1 = Guided Problem Solver
    var showCheatSheet by remember { mutableStateOf(false) }

    var explanation by remember { mutableStateOf<LessonExplanation?>(null) }
    var isLoadingExplanation by remember { mutableStateOf(true) }

    LaunchedEffect(skill?.id) {
        if (skill != null) {
            isLoadingExplanation = true
            explanation = viewModel.repository.getLessonExplanation(skill)
            isLoadingExplanation = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(skill?.name ?: "Lesson", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Audio Lecture Button (Native Offline TTS)
                    IconButton(
                        onClick = {
                            if (isTtsPlaying) {
                                ttsReader.stop()
                            } else {
                                val exp = explanation
                                val lectureText = buildString {
                                    append("Lesson on ${skill?.name}. ")
                                    if (exp != null) {
                                        append(exp.summary)
                                        append(" Key principles include: ")
                                        exp.keyPoints.forEach { append("$it. ") }
                                    }
                                }
                                ttsReader.speak(lectureText)
                            }
                        },
                        modifier = Modifier.testTag("tts_lecture_button")
                    ) {
                        Icon(
                            imageVector = if (isTtsPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (isTtsPlaying) "Stop Audio" else "Listen to Lesson",
                            tint = if (isTtsPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Cheat-Sheet Quick Revision Button
                    IconButton(
                        onClick = { showCheatSheet = true },
                        modifier = Modifier.testTag("open_cheat_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Cheat-Sheet",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (skill != null) {
                        MasteryBadge(
                            status = status,
                            score = mastery,
                            modifier = Modifier.padding(end = 8.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (skill != null) {
                                onAskTutor(skill.id, "Explain ${skill.name} using my notes.")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ask_ai_tutor_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ask Tutor")
                    }

                    Button(
                        onClick = {
                            if (skill != null) onStartPractice(skill.id)
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("practice_skill_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Practice Skill")
                    }
                }
            }
        }
    ) { innerPadding ->
        if (skill == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Skill not found.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Audio Playing Banner
            AnimatedVisibility(visible = isTtsPlaying) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LearnMate Audio Teacher is reading aloud...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = { ttsReader.stop() }) {
                            Text("Stop", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Tabs: Overview & Notes vs Step-by-Step Solver
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Core Lesson") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Step-by-Step Solver") }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp)
                        .testTag("lesson_screen"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        // Spaced Repetition Due Badge & Prerequisite Banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (repetitionSchedule.isDueNow) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = if (repetitionSchedule.isDueNow) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = repetitionSchedule.dueText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (repetitionSchedule.isDueNow) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TextButton(onClick = { showCheatSheet = true }) {
                                Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Exam Cheat-Sheet", fontSize = 12.sp)
                            }
                        }
                    }

                    item {
                        // Why you're learning this card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "Why you're learning this", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Foundational prerequisite for ${skill.chapter}. Current mastery is $mastery%.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SourceCitationChip(docName = skill.sourceDocumentName.ifBlank { "Course Notes" }, page = skill.sourcePage)
                            Text(text = "Chapter: ${skill.chapter}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (isLoadingExplanation) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandBluePrimary)
                            }
                        }
                    } else {
                        val exp = explanation
                        if (exp != null) {
                            // Summary
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "Overview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = exp.summary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                                    }
                                }
                            }

                            // Key points
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "Key Concepts from Notes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        exp.keyPoints.forEach { point ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                                Box(modifier = Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(BrandBluePrimary))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(text = point, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // Worked examples
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "Worked Examples", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        exp.examples.forEach { example ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ) {
                                                Text(text = example, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(10.dp), lineHeight = 18.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            } else {
                // Tab 1: Step-by-Step Guided Problem Solver
                GuidedProblemSolver(skill = skill)
            }
        }
    }

    // Cheat Sheet Bottom Sheet Dialog
    if (showCheatSheet && skill != null) {
        val sheetData = remember(skill) { CheatSheetGenerator.generateCheatSheet(skill) }
        CheatSheetModal(
            data = sheetData,
            onDismiss = { showCheatSheet = false },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = buildString {
                    append("${sheetData.title}\n")
                    append("Source: ${sheetData.sourceDoc} (Page ${sheetData.sourcePage})\n\n")
                    append("FORMULAS:\n")
                    sheetData.keyFormulas.forEach { append("• ${it.first}: ${it.second}\n") }
                    append("\nDEFINITIONS:\n")
                    sheetData.coreDefinitions.forEach { append("• $it\n") }
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("CheatSheet", text))
                Toast.makeText(context, "Cheat-Sheet copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun GuidedProblemSolver(skill: com.example.data.local.entity.SkillEntity) {
    var userFormula by remember { mutableStateOf("") }
    var userCalculation by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BrandBluePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "1", fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                        }
                        Text(
                            text = "Step 1: Focus Topic Analysis",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Topic: ${skill.name} (${skill.chapter})\nDescription: ${skill.description}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "• Source Material: ${skill.sourceDocumentName}\n• Page Reference: Page ${skill.sourcePage}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BrandBluePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "2", fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                        }
                        Text(
                            text = "Step 2: Core Rule or Formula",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "What is the primary formula, equation, or theoretical principle for ${skill.name}?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userFormula,
                        onValueChange = { userFormula = it },
                        placeholder = { Text("Enter rule, definition, or formula...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BrandBluePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "3", fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                        }
                        Text(
                            text = "Step 3: Self-Check & Notes Application",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "How would you explain or apply ${skill.name} to solve an exam problem?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userCalculation,
                        onValueChange = { userCalculation = it },
                        placeholder = { Text("Write your step-by-step reasoning or solution...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isSubmitted = userFormula.isNotBlank() || userCalculation.isNotBlank()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Record Guided Solution")
                    }

                    if (isSubmitted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5)
                        ) {
                            Text(
                                text = "✅ Great practice! You formulated key relationships for '${skill.name}'. Use the AI Tutor if you want a detailed review.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandEmerald,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheetModal(
    data: CheatSheetData,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = data.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Grounded in ${data.sourceDoc} (Page ${data.sourcePage})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = onCopy) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "KEY FORMULAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(6.dp))
            data.keyFormulas.forEach { (formula, meaning) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formula, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = meaning, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "CORE LAWS & DEFINITIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(6.dp))
            data.coreDefinitions.forEach { def ->
                Text(text = "• $def", fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 2.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "EXAM PITFALLS TO AVOID", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
            Spacer(modifier = Modifier.height(6.dp))
            data.examPitfalls.forEach { pit ->
                Text(text = "⚠️ $pit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 2.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
