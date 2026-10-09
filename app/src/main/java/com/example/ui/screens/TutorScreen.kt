package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.local.entity.ChatMessageEntity
import com.example.ui.components.NetworkStatusIndicator
import com.example.ui.components.SourceCitationChip
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandCyan
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorScreen(
    viewModel: MainViewModel,
    initialPrompt: String? = null
) {
    val activeCourse by viewModel.activeCourse.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isCloudAIAvailable by viewModel.isCloudAIAvailable.collectAsState()
    val isSimulatedOffline by viewModel.isSimulatedOffline.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isThinking by viewModel.isTutorThinking.collectAsState()
    val skills by viewModel.skills.collectAsState()

    var inputText by remember { mutableStateOf(initialPrompt ?: "") }
    val listState = rememberLazyListState()

    val suggestedQuestions = remember(skills) {
        if (skills.isNotEmpty()) {
            val firstSkill = skills.first().name
            listOf(
                "Explain $firstSkill step by step.",
                "Give me a clear example from my notes.",
                "Give me a hint for solving a problem.",
                "Summarize the main takeaways.",
                "Test my understanding with a question."
            )
        } else {
            listOf(
                "Explain the main concepts in my notes.",
                "Give me a practical example.",
                "Give me a hint to solve this problem.",
                "Summarize key definitions.",
                "Quiz me on this topic."
            )
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Tutor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = when {
                                isCloudAIAvailable -> "Cloud AI configured · a successful reply confirms access"
                                isOnline -> "Local tutor (Firebase AI Logic not configured)"
                                else -> "Offline notes tutor (no neural model installed)"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    NetworkStatusIndicator(
                        isOnline = isOnline,
                        isSimulatedOffline = isSimulatedOffline,
                        onToggleSimulation = { viewModel.toggleOfflineSimulation() },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .fillMaxWidth()
                .testTag("ai_tutor_screen")
        ) {
            // Chat message stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (activeCourse == null) {
                    item {
                        Text(
                            text = "Create or select a course before asking questions. Your tutor uses only that course's material.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .testTag("tutor_empty_course_message")
                        )
                    }
                }
                items(chatMessages) { message ->
                    ChatMessageBubble(message = message)
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = BrandBluePrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "LearnMate is searching notes and thinking...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick suggestion chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestedQuestions) { prompt ->
                    FilterChip(
                        selected = false,
                        enabled = activeCourse != null,
                        onClick = {
                            activeCourse?.let { course ->
                                viewModel.sendTutorMessage(course.id, prompt)
                            }
                        },
                        label = {
                            Text(text = prompt, fontSize = 12.sp)
                        }
                    )
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask anything about your notes...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutor_message_input"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        enabled = activeCourse != null && inputText.isNotBlank(),
                        onClick = {
                            activeCourse?.let { course ->
                                if (inputText.isNotBlank()) {
                                    viewModel.sendTutorMessage(course.id, inputText)
                                }
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BrandBluePrimary)
                            .testTag("send_tutor_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BrandBluePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Tutor",
                    tint = BrandBluePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) BrandBluePrimary else MaterialTheme.colorScheme.surface,
                tonalElevation = if (isUser) 0.dp else 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = presentTutorText(message.content),
                        fontSize = 14.sp,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )

                    // Grounded source citation
                    if (!isUser && message.sourceDocumentName != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SourceCitationChip(
                            docName = message.sourceDocumentName,
                            page = message.sourcePage
                        )
                    }

                    if (!isUser && message.isOfflineGenerated) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Grounded from local notes (offline)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal fun presentTutorText(rawText: String): String {
    val normalized = rawText
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .replace(Regex("\\[([^\\]]+)]\\((?:https?://)?[^)]+\\)"), "$1")

    return normalized.lines().mapNotNull { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trimStart()
        when {
            trimmed.matches(Regex("\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?")) -> null
            trimmed.startsWith("|") && trimmed.endsWith("|") ->
                trimmed.trim('|').split('|').joinToString("  •  ") { it.trim() }.takeIf { it.isNotBlank() }
            else -> line
                .replace(Regex("^\\s{0,3}#{1,6}\\s*"), "")
                .replace(Regex("^\\s*>\\s?"), "")
                .replace(Regex("\\*\\*(.+?)\\*\\*|__(.+?)__"), "$1$2")
                .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)"), "$1$2")
                .replace(Regex("`{1,3}"), "")
                .replace(Regex("^\\s*[-+*]\\s+"), "• ")
                .trimEnd()
        }
    }.joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
