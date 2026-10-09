package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SelectedFileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCourseScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onCourseCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    var courseName by rememberSaveable { mutableStateOf("") }
    var courseDescription by rememberSaveable { mutableStateOf("") }

    val files = remember {
        mutableStateListOf<SelectedFileItem>()
    }

    var showTextInputDialog by remember { mutableStateOf(false) }
    var customTextTitle by remember { mutableStateOf("") }
    var customTextContent by remember { mutableStateOf("") }
    var validationErrorMessage by remember { mutableStateOf<String?>(null) }

    // System File Picker for PDF and documents
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var displayName: String? = null
            var sizeBytes: Long? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) displayName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {
                // Ignore cursor query failure and fall back
            }

            val rawName = displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
            val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val extension = sanitizedName.substringAfterLast('.', "").uppercase().ifBlank { "PDF" }
            val currentSizeBytes = sizeBytes

            // Validation: Reject oversized files (> 50 MB)
            if (currentSizeBytes != null && currentSizeBytes > 50L * 1024L * 1024L) {
                validationErrorMessage = "File '$sanitizedName' exceeds the 50 MB limit."
                return@rememberLauncherForActivityResult
            }

            // Validation: Reject 0-byte empty files
            if (currentSizeBytes != null && currentSizeBytes == 0L) {
                validationErrorMessage = "File '$sanitizedName' is empty (0 bytes)."
                return@rememberLauncherForActivityResult
            }

            validationErrorMessage = null

            val formattedSize = if (currentSizeBytes != null && currentSizeBytes > 0) {
                if (currentSizeBytes >= 1024 * 1024) String.format("%.1f MB", currentSizeBytes / (1024.0 * 1024.0))
                else "${(currentSizeBytes / 1024).coerceAtLeast(1)} KB"
            } else {
                "Document"
            }

            // Replace existing duplicate if already picked
            val existingIndex = files.indexOfFirst { it.name.equals(sanitizedName, ignoreCase = true) }
            val newItem = SelectedFileItem(
                name = sanitizedName,
                type = extension,
                size = formattedSize,
                uri = uri
            )
            if (existingIndex >= 0) {
                files[existingIndex] = newItem
            } else {
                files.add(newItem)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build My Course", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .testTag("build_my_course_screen"),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Course Details",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { courseName = it },
                        label = { Text("Course Name") },
                        placeholder = { Text("e.g. Physics, Chemistry, Biology") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = courseDescription,
                        onValueChange = { courseDescription = it },
                        label = { Text("Description / Scope") },
                        placeholder = { Text("Brief summary of syllabus or focus chapters") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_description_input"),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    if (validationErrorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("validation_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = validationErrorMessage ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 360.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Uploaded materials · ${files.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { showTextInputDialog = true }, modifier = Modifier.weight(1f).testTag("paste_notes_button")) {
                                        Text("Add notes")
                                    }
                                    TextButton(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.weight(1f).testTag("upload_file_button")) {
                                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload file")
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Uploaded materials · ${files.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                Row {
                                    TextButton(onClick = { showTextInputDialog = true }, modifier = Modifier.testTag("paste_notes_button")) { Text("Add notes") }
                                    TextButton(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.testTag("upload_file_button")) {
                                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload file")
                                    }
                                }
                            }
                        }
                    }
                }

                itemsIndexed(files) { index, file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("file_card_$index"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrandBluePrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (file.type == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Description,
                                        contentDescription = file.type,
                                        tint = BrandBluePrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = file.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = file.type,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = file.size,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• Ready to parse",
                                            fontSize = 11.sp,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { files.removeAt(index) },
                                modifier = Modifier.testTag("delete_file_button_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Bottom CTA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        if (courseName.isNotBlank() && files.isNotEmpty()) {
                            viewModel.createCourseWithFiles(
                                title = courseName,
                                description = courseDescription,
                                files = files,
                                onFailure = { message -> validationErrorMessage = message },
                                onCreated = { newCourseId ->
                                    onCourseCreated(newCourseId)
                                }
                            )
                        }
                    },
                    enabled = courseName.isNotBlank() && files.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("build_my_course_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Build My Course (${files.size} documents)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text("Paste Notes / Syllabus Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customTextTitle,
                        onValueChange = { customTextTitle = it },
                        label = { Text("Document Title (e.g. Chapter 1 Notes)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_notes_title_input")
                    )
                    OutlinedTextField(
                        value = customTextContent,
                        onValueChange = { customTextContent = it },
                        label = { Text("Content / Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .testTag("custom_notes_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customTextTitle.isNotBlank() && customTextContent.isNotBlank()) {
                            files.add(
                                SelectedFileItem(
                                    name = "$customTextTitle.txt",
                                    type = "TXT",
                                    size = "${(customTextContent.length / 1024) + 1} KB",
                                    customText = customTextContent
                                )
                            )
                            customTextTitle = ""
                            customTextContent = ""
                            showTextInputDialog = false
                        }
                    }
                ) {
                    Text("Add Notes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
