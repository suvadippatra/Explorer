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
    appState: com.troikoss.continuum_explorer.utils.FileExplorerState? = null,
    onMenuClick: () -> Unit = {},
    onBackToSuite: () -> Unit = {
        appState?.activeEditorToolId = null
        appState?.activeEditorToolTitle = null
    },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeFile by remember(initialFile) { mutableStateOf(initialFile) }
    var showFileTree by remember { mutableStateOf(false) }
    var fileContent by remember { mutableStateOf("") }
    var binaryBytes by remember { mutableStateOf(byteArrayOf()) }
    var isLoadingFile by remember { mutableStateOf(false) }

    val rootDir = remember(activeFile) {
        activeFile?.parentFile ?: Environment.getExternalStorageDirectory()
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
                com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase.recordFileActivity(
                    context = context,
                    file = file,
                    activityType = com.troikoss.continuum_explorer.data.db.SuiteActivityType.EDITED
                )
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

        // Main Studio Area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val currentFile = activeFile
            if (currentFile == null) {
                com.troikoss.continuum_explorer.ui.components.MiniAppLandingScreen(
                    toolId = editorToolId ?: "text_editor",
                    appState = appState,
                    onBackToSuite = onBackToSuite,
                    onMenuClick = onMenuClick,
                    onOpenFileInStudio = { chosenFile ->
                        val ext = chosenFile.extension.lowercase()
                        if (ext in listOf("jpg", "jpeg", "png", "webp", "bmp") && appState != null) {
                            appState.openInEditMode = true
                            appState.viewingFile = chosenFile.toUniversal()
                        } else {
                            activeFile = chosenFile
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoadingFile) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Loading Document...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                // ACTIVE FILE EDITOR DISPATCH (Separated CSV Studio, Code Studio, and Markor Text & Markdown Studio)
                val ext = currentFile.extension.lowercase()
                when {
                    ext in listOf("csv", "tsv") || editorToolId == "csv_editor" -> {
                        CsvEditorView(
                            file = currentFile,
                            content = fileContent,
                            onContentChange = { fileContent = it },
                            onSave = { saveActiveFile() },
                            onMenuClick = onMenuClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ext in listOf(
                        "kt", "kts", "java", "json", "json5", "jsonc", "xml", "html", "htm", "xhtml", "css", "scss", "less", "sass",
                        "js", "jsx", "ts", "tsx", "py", "pyw", "c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "sh", "bash", "zsh", "bat", "cmd",
                        "gradle", "properties", "yaml", "yml", "ini", "conf", "toml", "env", "sql", "php", "rb", "go", "rs", "swift", "m", "mm",
                        "r", "lua", "pl", "dart", "cs", "vb", "fs", "asm", "diff", "patch"
                    ) || editorToolId == "code_studio" -> {
                        CodeEditorView(
                            file = currentFile,
                            content = fileContent,
                            onContentChange = { fileContent = it },
                            onSave = { saveActiveFile() },
                            onMenuClick = onMenuClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ext == "pdf" -> {
                        com.troikoss.continuum_explorer.ui.editor.pdf.PdfViewerEngine(
                            file = currentFile.toUniversal(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg") -> {
                        if (appState != null) {
                            com.troikoss.continuum_explorer.ui.components.ViewOnlyFileViewer(
                                file = currentFile.toUniversal(),
                                appState = appState,
                                startInEditMode = true,
                                onClose = { activeFile = null },
                                onMenuClick = onMenuClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    ext in listOf("mp4", "mkv", "mp3", "wav", "ogg", "flac", "m4a") -> {
                        MediaViewer(file = currentFile)
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
                            onMenuClick = onMenuClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
