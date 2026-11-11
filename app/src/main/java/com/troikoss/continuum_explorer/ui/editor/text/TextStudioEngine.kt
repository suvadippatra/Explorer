package com.troikoss.continuum_explorer.ui.editor.text

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.GlobalEvents
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Markor-powered Document, Markdown, Todo.txt, Org-Mode, AsciiDoc & CSV Editor Engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkorTextEngine(
    fileName: String,
    initialContent: String,
    onSaveContent: (String) -> Unit,
    fileRef: File? = null,
    onMenuClick: () -> Unit = {},
    onExitEditor: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uFile = remember(fileRef, fileName) {
        (fileRef ?: File(context.cacheDir, fileName)).toUniversal()
    }
    MarkorTextEngine(
        file = uFile,
        appState = null,
        onMenuClick = onMenuClick,
        onExitEditor = onExitEditor,
        initialTextOverride = initialContent,
        onCustomSave = onSaveContent,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkorTextEngine(
    file: UniversalFile,
    appState: FileExplorerState? = null,
    onMenuClick: () -> Unit = {},
    onExitEditor: (() -> Unit)? = null,
    initialTextOverride: String? = null,
    onCustomSave: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600
    val coroutineScope = rememberCoroutineScope()

    val fileRef = remember(file) {
        file.fileRef ?: file.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) }
    }
    val uri = remember(file) {
        file.documentFileRef?.uri ?: getUriForUniversalFile(context, file)
    }

    var isLoading by remember(file) { mutableStateOf(initialTextOverride == null) }
    var loadedText by remember(file, initialTextOverride) { mutableStateOf(initialTextOverride ?: "") }

    LaunchedEffect(file, initialTextOverride) {
        if (initialTextOverride != null) {
            loadedText = initialTextOverride
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val content = withContext(Dispatchers.IO) {
            try {
                when {
                    fileRef != null && fileRef.exists() -> fileRef.readText()
                    uri != null -> context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    else -> ""
                }
            } catch (_: Exception) {
                ""
            }
        }
        loadedText = content
        isLoading = false
    }

    val stateController = rememberTextEditorStateController(loadedText)
    val detectedFormat = remember(file.name) { FormatRegistry.detectFormat(file.name) }

    LaunchedEffect(detectedFormat) {
        stateController.formatType = detectedFormat
        if (detectedFormat == TextFormatType.TODO_TXT) {
            stateController.showTodoInteractive = true
        }
    }

    val syntaxHighlighter = remember(stateController.formatType) {
        SyntaxHighlighter(stateController.formatType)
    }

    var showPathInfoDialog by remember { mutableStateOf(false) }
    var showMoveFilePicker by remember { mutableStateOf(false) }
    var jumpLineInput by remember { mutableStateOf("") }

    fun saveDocument() {
        if (onCustomSave != null) {
            onCustomSave(stateController.textContent)
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val textToSave = stateController.textContent
                if (fileRef != null) {
                    fileRef.parentFile?.mkdirs()
                    fileRef.writeText(textToSave)
                    SuiteRecentDatabase.recordFileActivity(
                        context = context,
                        file = fileRef,
                        activityType = SuiteActivityType.EDITED
                    )
                } else if (uri != null) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                        it.write(textToSave)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
                    appState?.refresh()
                    GlobalEvents.triggerRefresh()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareDocument() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, stateController.textContent)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }

    fun openExternalApp() {
        val targetUri = uri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No external app found", Toast.LENGTH_SHORT).show()
        }
    }

    if (showPathInfoDialog) {
        AlertDialog(
            onDismissRequest = { showPathInfoDialog = false },
            title = { Text("File Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${file.name}", style = MaterialTheme.typography.bodySmall)
                    Text("Format: ${stateController.formatType.displayName}", style = MaterialTheme.typography.bodySmall)
                    Text("Path: ${file.absolutePath}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPathInfoDialog = false }) { Text("Close") }
            }
        )
    }

    if (showMoveFilePicker && fileRef != null) {
        InAppFilePickerDialog(
            title = "Move Document",
            selectFolders = true,
            initialDirectory = fileRef.parentFile ?: Environment.getExternalStorageDirectory(),
            onDismiss = { showMoveFilePicker = false },
            onSelected = { destFolder ->
                showMoveFilePicker = false
                if (destFolder.isDirectory) {
                    val destFile = File(destFolder, fileRef.name)
                    if (fileRef.renameTo(destFile)) {
                        appState?.viewingFile = destFile.toUniversal()
                        appState?.refresh()
                        GlobalEvents.triggerRefresh()
                        Toast.makeText(context, "Moved to ${destFolder.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (stateController.showJumpToLineDialog) {
        AlertDialog(
            onDismissRequest = { stateController.showJumpToLineDialog = false },
            title = { Text("Jump to Line", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = jumpLineInput,
                    onValueChange = { jumpLineInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Line Number (1 - ${stateController.stats.lineCount.coerceAtLeast(1)})") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val line = jumpLineInput.toIntOrNull() ?: 1
                    stateController.jumpToLine(line)
                    stateController.showJumpToLineDialog = false
                    jumpLineInput = ""
                }) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { stateController.showJumpToLineDialog = false }) { Text("Cancel") }
            }
        )
    }

    val isMarkdownLike = stateController.formatType in setOf(
        TextFormatType.MARKDOWN,
        TextFormatType.ORG,
        TextFormatType.ASCIIDOC
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TextEditorTopBar(
            fileName = file.name,
            isMarkdown = isMarkdownLike,
            displayMode = stateController.displayMode,
            isLargeScreen = isLargeScreen,
            onMenuClick = {
                if (onExitEditor != null) onExitEditor() else onMenuClick()
            },
            onModeChange = { stateController.displayMode = it },
            onUndo = { stateController.undo() },
            onRedo = { stateController.redo() },
            onSave = { saveDocument() },
            onShare = { shareDocument() },
            onOpenExternal = { openExternalApp() },
            onViewPath = { showPathInfoDialog = true },
            onChangePath = { showMoveFilePicker = true },
            onToggleToc = if (isMarkdownLike) {
                { stateController.showTocDrawer = !stateController.showTocDrawer }
            } else null
        )

        // Search & Replace Bar
        if (stateController.isSearchActive) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = stateController.searchQuery,
                        onValueChange = { stateController.performSearch(it) },
                        placeholder = { Text("Find...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stateController.replaceQuery,
                        onValueChange = { stateController.replaceQuery = it },
                        placeholder = { Text("Replace...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { stateController.findNext() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                    }
                    TextButton(onClick = { stateController.replaceAll() }) {
                        Text("All", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { stateController.isSearchActive = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search")
                    }
                }
            }
        }

        // Format Action Bar
        EditorActionBar(
            formatType = stateController.formatType,
            onFormatClick = { prefix, suffix, placeholder ->
                stateController.applyWrapFormat(prefix, suffix, placeholder)
            },
            onInsertText = { stateController.insertTextAtCursor(it) },
            onUndo = { stateController.undo() },
            onRedo = { stateController.redo() },
            onCopy = { stateController.copySelectedText(clipboard) },
            onCut = { stateController.cutSelectedText(clipboard) },
            onPaste = { stateController.pasteFromClipboard(clipboard) },
            onIndent = { stateController.indent() },
            onUnindent = { stateController.unindent() },
            onToggleSearch = { stateController.isSearchActive = !stateController.isSearchActive },
            onJumpToLineClick = { stateController.showJumpToLineDialog = true },
            onFormatAlignTable = { stateController.formatAlignCsv() },
            onToggleCsvGrid = { stateController.showCsvGrid = !stateController.showCsvGrid },
            onToggleTodoInteractive = { stateController.showTodoInteractive = !stateController.showTodoInteractive },
            isTodoInteractive = stateController.showTodoInteractive
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Main Editor / Preview / Split / CSV Grid / Todo.txt Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                stateController.showCsvGrid -> {
                    CsvGridEditor(
                        rawCsvText = stateController.textContent,
                        onSaveCsv = { updated ->
                            stateController.updateTextString(updated)
                            saveDocument()
                        },
                        onCloseGrid = { stateController.showCsvGrid = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                stateController.formatType == TextFormatType.TODO_TXT && stateController.showTodoInteractive -> {
                    TodoTxtInteractiveView(
                        rawText = stateController.textContent,
                        onTextChange = { updated -> stateController.updateTextString(updated) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                stateController.displayMode == TextDisplayMode.PREVIEW -> {
                    DocumentPreviewEngine(
                        rawText = stateController.textContent,
                        formatType = stateController.formatType,
                        cursorLine = stateController.stats.currentLine,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                stateController.displayMode == TextDisplayMode.SPLIT -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            BasicTextField(
                                value = stateController.textFieldValue,
                                onValueChange = { stateController.updateText(it) },
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = stateController.fontSizeSp.sp,
                                    lineHeight = (stateController.fontSizeSp * 1.4f).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                visualTransformation = syntaxHighlighter,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        DocumentPreviewEngine(
                            rawText = stateController.textContent,
                            formatType = stateController.formatType,
                            cursorLine = stateController.stats.currentLine,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        BasicTextField(
                            value = stateController.textFieldValue,
                            onValueChange = { stateController.updateText(it) },
                            textStyle = TextStyle(
                                fontFamily = if (stateController.formatType == TextFormatType.PLAIN_TEXT) FontFamily.Default else FontFamily.Monospace,
                                fontSize = stateController.fontSizeSp.sp,
                                lineHeight = (stateController.fontSizeSp * 1.45f).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            visualTransformation = syntaxHighlighter,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (stateController.showTocDrawer) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    MarkdownTocDrawerContent(
                        rawMarkdown = stateController.textContent,
                        onHeaderClick = { item ->
                            stateController.jumpToCharOffset(item.charOffset)
                            stateController.showTocDrawer = false
                        },
                        onClose = { stateController.showTocDrawer = false }
                    )
                }
            }
        }

        TextEditorFooter(
            stats = stateController.stats,
            fontSizeSp = stateController.fontSizeSp,
            onFontSizeDecrease = {
                if (stateController.fontSizeSp > 10f) stateController.fontSizeSp -= 1f
            },
            onFontSizeIncrease = {
                if (stateController.fontSizeSp < 30f) stateController.fontSizeSp += 1f
            }
        )
    }
}
