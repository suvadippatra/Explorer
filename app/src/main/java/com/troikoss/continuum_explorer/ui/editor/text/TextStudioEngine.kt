package com.troikoss.continuum_explorer.ui.editor.text

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
 * Features 1:1 TextLayoutResult-synchronized line numbers that stay aligned even when long lines soft-wrap,
 * Triple-Line Menu sidebar toggle in the header, and gesture/tab-close exit.
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
    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    // Allow system Back gesture to close the editing page cleanly
    BackHandler(enabled = onExitEditor != null) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressMs < 2000L) {
            onExitEditor?.invoke()
        } else {
            lastBackPressMs = now
            Toast.makeText(context, "Swipe/press back again or close tab to exit editor", Toast.LENGTH_SHORT).show()
        }
    }

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
            onMenuClick = onMenuClick,
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
            } else null,
            onCloseEditor = onExitEditor
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
                        SynchronizedLineNumberTextEditor(
                            textFieldValue = stateController.textFieldValue,
                            onValueChange = { stateController.updateText(it) },
                            fontSizeSp = stateController.fontSizeSp,
                            fontFamily = FontFamily.Monospace,
                            syntaxHighlighter = syntaxHighlighter,
                            currentCursorLine = stateController.stats.currentLine,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
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
                    SynchronizedLineNumberTextEditor(
                        textFieldValue = stateController.textFieldValue,
                        onValueChange = { stateController.updateText(it) },
                        fontSizeSp = stateController.fontSizeSp,
                        fontFamily = if (stateController.formatType == TextFormatType.PLAIN_TEXT) FontFamily.Default else FontFamily.Monospace,
                        syntaxHighlighter = syntaxHighlighter,
                        currentCursorLine = stateController.stats.currentLine,
                        modifier = Modifier.fillMaxSize()
                    )
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

/**
 * Synchronized Line-Number + BasicTextField Pane.
 * Uses [TextLayoutResult] (`onTextLayout`) to measure the exact Y-coordinate (`getLineTop`) and baseline
 * of every logical line's first visual line. Even when long paragraphs soft-wrap across multiple rows,
 * each line number is rendered at the exact vertical offset of its corresponding text line.
 */
@Composable
private fun SynchronizedLineNumberTextEditor(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    syntaxHighlighter: SyntaxHighlighter,
    currentCursorLine: Int,
    modifier: Modifier = Modifier
) {
    val verticalScroll = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    val logicalLineStartOffsets = remember(textFieldValue.text) {
        val offsets = mutableListOf(0)
        val raw = textFieldValue.text
        for (i in raw.indices) {
            if (raw[i] == '\n') {
                offsets.add(i + 1)
            }
        }
        offsets
    }

    val gutterDigits = remember(logicalLineStartOffsets.size) {
        logicalLineStartOffsets.size.toString().length.coerceAtLeast(2)
    }
    val gutterWidthDp = remember(gutterDigits, fontSizeSp) {
        ((gutterDigits * (fontSizeSp * 0.65f)) + 20f).dp
    }

    val numberColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f).toArgb()
    val activeNumberColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val gutterBgColor = MaterialTheme.colorScheme.surfaceContainerLow

    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll)
    ) {
        // 1:1 TextLayoutResult-Synchronized Line Number Gutter
        val totalTextHeightDp = with(density) {
            (textLayoutResult?.size?.height ?: 0).toDp().coerceAtLeast(240.dp)
        }
        Box(
            modifier = Modifier
                .width(gutterWidthDp)
                .heightIn(min = totalTextHeightDp + 28.dp)
                .background(gutterBgColor)
                .padding(top = 14.dp, bottom = 14.dp)
        ) {
            val textSizePx = with(density) { (fontSizeSp * 0.88f).sp.toPx() }
            val rightPadPx = with(density) { 8.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalTextHeightDp)
            ) {
                val layout = textLayoutResult ?: return@Canvas
                val textLen = textFieldValue.text.length
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.RIGHT
                    textSize = textSizePx
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                logicalLineStartOffsets.forEachIndexed { index, startOffset ->
                    val lineNum = index + 1
                    val safeOffset = startOffset.coerceIn(0, textLen)
                    val visualLine = try {
                        layout.getLineForOffset(safeOffset)
                    } catch (_: Exception) {
                        0
                    }
                    val baselineY = try {
                        layout.getLineBaseline(visualLine)
                    } catch (_: Exception) {
                        visualLine * textSizePx * 1.45f + textSizePx
                    }
                    paint.color = if (lineNum == currentCursorLine) activeNumberColorArgb else numberColorArgb
                    paint.isFakeBoldText = (lineNum == currentCursorLine)
                    drawContext.canvas.nativeCanvas.drawText(
                        lineNum.toString(),
                        size.width - rightPadPx,
                        baselineY,
                        paint
                    )
                }
            }
        }

        VerticalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )

        // Editor Text Field
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                onTextLayout = { result -> textLayoutResult = result },
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                visualTransformation = syntaxHighlighter,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
            )
        }
    }
}
