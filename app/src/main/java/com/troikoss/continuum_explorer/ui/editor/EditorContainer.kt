package com.troikoss.continuum_explorer.ui.editor

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Master Editor Container featuring:
 * - Collapsible multi-structure side file tree
 * - Unified Markor-style Text & Code Editor dispatch
 * - In-app file selection & direct new text file creation
 * - Safe large file handling with non-blocking async IO
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorContainer(
    initialFile: File? = null,
    editorToolId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeFile by remember { mutableStateOf(initialFile) }
    var showFileTree by remember { mutableStateOf(false) }
    var fileContent by remember { mutableStateOf("") }
    var binaryBytes by remember { mutableStateOf(byteArrayOf()) }
    var isLoadingFile by remember { mutableStateOf(false) }

    val rootDir = remember(activeFile) {
        activeFile?.parentFile ?: Environment.getExternalStorageDirectory()
    }

    var showInAppPicker by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileNameInput by remember { mutableStateOf("notes.md") }

    if (showInAppPicker) {
        com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog(
            title = "Choose File to Edit",
            selectFolders = false,
            initialDirectory = rootDir,
            onDismiss = { showInAppPicker = false },
            onSelected = { selectedFile ->
                activeFile = selectedFile
                showInAppPicker = false
            }
        )
    }

    // New File Creation Dialog
    if (showCreateFileDialog) {
        val extension = remember(newFileNameInput) {
            newFileNameInput.substringAfterLast('.', "").lowercase()
        }

        val layoutModeDescription = remember(extension) {
            when (extension) {
                "md", "markdown", "mdown" -> "Layout Mode: Markdown Editor (Live Preview & Edit toggle)"
                "txt", "text", "log" -> "Layout Mode: Plain Text Editor"
                "csv", "tsv" -> "Layout Mode: CSV Table Editor & Grid View"
                "todo.txt" -> "Layout Mode: Todo.txt List Editor"
                "json", "xml", "html", "css", "js", "ts", "kt", "java", "py", "c", "cpp", "sh", "yml", "yaml", "sql", "gradle" ->
                    "Layout Mode: Code Editor with Syntax Highlighting"
                else -> "Layout Mode: Text & Code Workspace"
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Create New Text File", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter a filename with an extension. The layout mode will adapt automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Extension Quick Choice Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Quick Extensions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            listOf(".txt", ".md", ".json", ".py", ".kt", ".csv").forEach { ext ->
                                val baseName = newFileNameInput.substringBeforeLast('.', newFileNameInput)
                                FilterChip(
                                    selected = newFileNameInput.endsWith(ext, ignoreCase = true),
                                    onClick = { newFileNameInput = if (baseName.isBlank()) "new_file$ext" else "$baseName$ext" },
                                    label = { Text(ext, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = layoutModeDescription,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileNameInput.trim()
                        if (name.isNotEmpty()) {
                            try {
                                val targetDir = Environment.getExternalStorageDirectory()
                                var fileToCreate = File(targetDir, name)
                                if (!fileToCreate.exists()) {
                                    fileToCreate.createNewFile()
                                }
                                activeFile = fileToCreate
                                showCreateFileDialog = false
                                Toast.makeText(context, "Created ${fileToCreate.name}", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error creating file: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Create & Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Load content when active file changes with crash safety and max 2MB size cap
    LaunchedEffect(activeFile) {
        val file = activeFile
        if (file != null && file.exists() && file.isFile) {
            isLoadingFile = true
            try {
                val ext = file.extension.lowercase()
                val length = file.length()

                withContext(Dispatchers.IO) {
                    if (length > 2 * 1024 * 1024) {
                        // Truncated safe loading for massive files (>2MB)
                        if (ext in listOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "mp3", "exe", "bin", "so", "class")) {
                            binaryBytes = byteArrayOf()
                            fileContent = ""
                        } else {
                            file.bufferedReader().use { reader ->
                                val buffer = CharArray(1024 * 1024)
                                val readCount = reader.read(buffer, 0, 1024 * 1024)
                                fileContent = if (readCount > 0) {
                                    String(buffer, 0, readCount) + "\n\n--- [TRUNCATED: File exceeds 2MB limit for full in-memory editing] ---"
                                } else ""
                                binaryBytes = byteArrayOf()
                            }
                        }
                    } else {
                        if (ext in listOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "mp3", "exe", "bin", "so", "class")) {
                            binaryBytes = file.readBytes()
                            fileContent = ""
                        } else {
                            fileContent = file.readText()
                            binaryBytes = byteArrayOf()
                        }
                    }
                }
            } catch (_: Exception) {
                fileContent = "Error reading file content."
                binaryBytes = byteArrayOf()
            } finally {
                isLoadingFile = false
            }
        } else {
            fileContent = ""
            binaryBytes = byteArrayOf()
            isLoadingFile = false
        }
    }

    fun saveActiveFile() {
        val file = activeFile
        if (file != null && file.canWrite()) {
            try {
                file.writeText(fileContent)
                Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "File is read-only or temporary", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Collapsible Multi-Structure Side File Tree Navigation
        AnimatedVisibility(visible = showFileTree && activeFile != null) {
            FileTreeNavigation(
                rootDirectory = rootDir,
                activeFile = activeFile,
                onFileSelected = { selected -> activeFile = selected },
                modifier = Modifier.width(220.dp)
            )
        }

        // Main Editor Area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val currentFile = activeFile
            if (editorToolId == "base64" || editorToolId == "hex") {
                Base64CodeViewer(
                    file = currentFile,
                    bytes = binaryBytes,
                    modifier = Modifier.fillMaxSize(),
                    onChooseFile = { showInAppPicker = true }
                )
            } else if (currentFile == null) {
                // BLANK INITIAL WORKSPACE WITH CHOOSE FILE + CREATE NEW TEXT FILE BUTTONS
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(28.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Markor Text & Code Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Choose an existing document or create a new text, markdown, code, or CSV file. The layout will configure automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Action Buttons: Choose File & Create New File
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showInAppPicker = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(0.85f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose File to Edit", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = { showCreateFileDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(0.85f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create New Text File", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else if (isLoadingFile) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Loading Document...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                // ACTIVE FILE EDITOR DISPATCH
                val ext = currentFile.extension.lowercase()
                when {
                    ext in listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdtxt", "mdtext", "adoc", "asciidoc", "org", "rst", "csv", "tsv", "txt", "text", "log") || currentFile.name.lowercase().contains("todo.txt") -> {
                        com.troikoss.continuum_explorer.ui.editor.text.MarkorTextEngine(
                            fileName = currentFile.name,
                            initialContent = fileContent,
                            onSaveContent = { newContent ->
                                fileContent = newContent
                                saveActiveFile()
                            },
                            fileRef = currentFile,
                            onMenuClick = { showFileTree = !showFileTree },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ext in listOf(
                        "kt", "kts", "java", "json", "json5", "jsonc", "xml", "html", "htm", "xhtml", "css", "scss", "less", "sass",
                        "js", "jsx", "ts", "tsx", "py", "pyw", "c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "sh", "bash", "zsh", "bat", "cmd",
                        "gradle", "properties", "yaml", "yml", "ini", "conf", "toml", "env", "sql", "php", "rb", "go", "rs", "swift", "m", "mm",
                        "r", "lua", "pl", "dart", "cs", "vb", "fs", "asm", "diff", "patch"
                    ) -> {
                        CodeEditorView(
                            file = currentFile,
                            content = fileContent,
                            onContentChange = { fileContent = it },
                            onSave = { saveActiveFile() }
                        )
                    }
                    ext == "pdf" -> {
                        com.troikoss.continuum_explorer.ui.editor.pdf.PdfViewerEngine(
                            file = currentFile.toUniversal(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "mp4", "mkv", "mp3", "wav", "ogg", "flac", "m4a") -> {
                        MediaViewer(file = currentFile)
                    }
                    binaryBytes.isNotEmpty() -> {
                        Base64CodeViewer(
                            file = currentFile,
                            bytes = binaryBytes,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        com.troikoss.continuum_explorer.ui.editor.text.MarkorTextEngine(
                            fileName = currentFile.name,
                            initialContent = fileContent,
                            onSaveContent = { newContent ->
                                fileContent = newContent
                                saveActiveFile()
                            },
                            fileRef = currentFile,
                            onMenuClick = { showFileTree = !showFileTree },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
