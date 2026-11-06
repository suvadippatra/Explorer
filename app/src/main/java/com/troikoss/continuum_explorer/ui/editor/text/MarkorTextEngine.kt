package com.troikoss.continuum_explorer.ui.editor.text

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager

/**
 * Native MarkorTextEngine (Central Text & Document Engine Blueprint).
 *
 * Implements:
 * A. Format Registry & File Type Router (FormatRegistry)
 * B. Live Syntax Highlighting (SyntaxHighlighter)
 * C. Format-Specific Action Bar (EditorActionBar)
 * D. Dual-Pane / Tabbed Live Markdown & Document Preview (DocumentPreviewEngine)
 * E. Interactive CSV Table Mode (CsvGridEditor)
 * F. Table of Contents (TOC) Outliner Drawer (MarkdownTocDrawer)
 */
@Composable
fun MarkorTextEngine(
    fileName: String,
    initialContent: String,
    onSaveContent: (String) -> Unit,
    modifier: Modifier = Modifier,
    fileRef: File? = null,
    onMenuClick: () -> Unit = {},
    onExitEditor: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    onViewPath: (() -> Unit)? = null,
    onChangePath: (() -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isLargeScreen = remember(configuration.screenWidthDp) {
        configuration.screenWidthDp >= 600
    }

    // State Controller
    val state = rememberTextEditorStateController(initialContent)
    var lastSavedContent by remember { mutableStateOf(initialContent) }
    val isDirty = state.textContent != lastSavedContent
    var showUnsavedDialog by remember { mutableStateOf(false) }

    fun handleClose() {
        if (isDirty) {
            showUnsavedDialog = true
        } else {
            onExitEditor?.invoke() ?: onMenuClick()
        }
    }

    BackHandler(enabled = true) {
        handleClose()
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Save Changes?", fontWeight = FontWeight.Bold) },
            text = { Text("Do you want to save the changes you made to \"$fileName\" before exiting?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveContent(state.textContent)
                        lastSavedContent = state.textContent
                        showUnsavedDialog = false
                        onExitEditor?.invoke() ?: onMenuClick()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showUnsavedDialog = false
                            onExitEditor?.invoke() ?: onMenuClick()
                        }
                    ) {
                        Text("Discard")
                    }
                    TextButton(
                        onClick = { showUnsavedDialog = false }
                    ) {
                        Text("Keep Editing")
                    }
                }
            }
        )
    }

    // Detect format type automatically
    LaunchedEffect(fileName) {
        state.formatType = FormatRegistry.detectFormat(fileName)
    }

    val formatHandler = remember(state.formatType) {
        FormatRegistry.getHandler(state.formatType)
    }
    val syntaxHighlighter = remember(state.formatType) {
        SyntaxHighlighter(state.formatType)
    }

    // Markdown and text files open in Edit mode initially with live preview & edit+preview toggle buttons

    // Jump to Line Dialog
    var jumpLineInput by remember { mutableStateOf("") }
    if (state.showJumpToLineDialog) {
        AlertDialog(
            onDismissRequest = { state.showJumpToLineDialog = false },
            title = { Text("Jump to Line", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = jumpLineInput,
                    onValueChange = { jumpLineInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Line number (1 - ${state.stats.lineCount})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val num = jumpLineInput.toIntOrNull()
                        if (num != null) state.jumpToLine(num)
                        state.showJumpToLineDialog = false
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val num = jumpLineInput.toIntOrNull()
                    if (num != null) state.jumpToLine(num)
                    state.showJumpToLineDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { state.showJumpToLineDialog = false }) { Text("Cancel") }
            }
        )
    }

    // If CSV Grid Mode is active, render interactive data grid
    if (state.showCsvGrid) {
        CsvGridEditor(
            rawCsvText = state.textContent,
            onSaveCsv = { updatedText ->
                state.updateTextString(updatedText)
            },
            onCloseGrid = { state.showCsvGrid = false },
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 1. Top Header Bar
            TextEditorTopBar(
                fileName = if (isDirty) "$fileName •" else fileName,
                isMarkdown = formatHandler.supportsPreview,
                displayMode = state.displayMode,
                isLargeScreen = isLargeScreen,
                onMenuClick = { handleClose() },
                onModeChange = { state.displayMode = it },
                onUndo = { state.undo() },
                onRedo = { state.redo() },
                onSave = {
                    onSaveContent(state.textContent)
                    lastSavedContent = state.textContent
                },
                onShare = { onShare?.invoke() },
                onOpenExternal = { onOpenExternal?.invoke() },
                onViewPath = onViewPath,
                onChangePath = onChangePath,
                onToggleToc = if (formatHandler.supportsPreview) { { state.showTocDrawer = !state.showTocDrawer } } else null
            )

            // 2. Search & Replace Toolbar (Collapsible)
            AnimatedVisibility(
                visible = state.isSearchActive,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { state.performSearch(it) },
                                placeholder = { Text("Find...", fontSize = 13.sp) },
                                singleLine = true,
                                trailingIcon = {
                                    if (state.totalMatches > 0) {
                                        Text(
                                            "${state.currentMatchIndex}/${state.totalMatches}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            IconButton(onClick = { state.findPrevious() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Previous match", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { state.findNext() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Next match", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { state.isSearchActive = false }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = state.replaceQuery,
                                onValueChange = { state.replaceQuery = it },
                                placeholder = { Text("Replace with...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            Button(
                                onClick = { state.replaceCurrent() },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("Replace", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { state.replaceAll() },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("All", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. Format-Specific Action Bar (EditorActionBar) docked above keyboard
            if (state.displayMode != TextDisplayMode.PREVIEW) {
                EditorActionBar(
                    formatType = state.formatType,
                    onFormatClick = { prefix, suffix, placeholder ->
                        state.applyWrapFormat(prefix, suffix, placeholder)
                    },
                    onInsertText = { state.insertTextAtCursor(it) },
                    onUndo = { state.undo() },
                    onRedo = { state.redo() },
                    onCopy = { state.copySelectedText(clipboardManager) },
                    onCut = { state.cutSelectedText(clipboardManager) },
                    onPaste = { state.pasteFromClipboard(clipboardManager) },
                    onIndent = { state.indent() },
                    onUnindent = { state.unindent() },
                    onToggleSearch = { state.isSearchActive = !state.isSearchActive },
                    onJumpToLineClick = { state.showJumpToLineDialog = true },
                    onFormatAlignTable = { state.formatAlignCsv() },
                    onToggleCsvGrid = if (state.formatType == TextFormatType.CSV) { { state.showCsvGrid = true } } else null,
                    onToggleTodoInteractive = if (state.formatType == TextFormatType.TODO_TXT) { { state.showTodoInteractive = !state.showTodoInteractive } } else null,
                    isTodoInteractive = state.showTodoInteractive,
                    onAddCsvRow = { state.insertTextAtCursor("\n") },
                    onAddCsvColumn = { state.insertTextAtCursor(",NewColumn") }
                )
            }

            // 4. Main Workspace Area (Edit / Preview / Split / Todo.txt Interactive)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.formatType == TextFormatType.TODO_TXT &&
                    (state.showTodoInteractive || state.displayMode == TextDisplayMode.PREVIEW)
                ) {
                    TodoTxtInteractiveView(
                        rawText = state.textContent,
                        onTextChange = { state.updateTextString(it) },
                        readOnly = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    when (state.displayMode) {
                        TextDisplayMode.EDIT -> {
                            TextField(
                                value = state.textFieldValue,
                                onValueChange = { state.updateText(it) },
                                visualTransformation = syntaxHighlighter,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = state.fontSizeSp.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = (state.fontSizeSp * 1.4f).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        TextDisplayMode.PREVIEW -> {
                            DocumentPreviewEngine(
                                rawText = state.textContent,
                                formatType = state.formatType,
                                cursorLine = state.stats.currentLine,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        TextDisplayMode.SPLIT -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left Pane: Source Code Editor with Live Syntax Highlighting
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    TextField(
                                        value = state.textFieldValue,
                                        onValueChange = { state.updateText(it) },
                                        visualTransformation = syntaxHighlighter,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = state.fontSizeSp.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = (state.fontSizeSp * 1.4f).sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }

                                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                                // Right Pane: Live Document Preview with auto-sync scrolling
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (state.formatType == TextFormatType.TODO_TXT) {
                                        TodoTxtInteractiveView(
                                            rawText = state.textContent,
                                            onTextChange = { state.updateTextString(it) },
                                            readOnly = false,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        DocumentPreviewEngine(
                                            rawText = state.textContent,
                                            formatType = state.formatType,
                                            cursorLine = state.stats.currentLine,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Status Bar Footer
            TextEditorFooter(
                stats = state.stats,
                fontSizeSp = state.fontSizeSp,
                onFontSizeDecrease = {
                    if (state.fontSizeSp > 10f) state.fontSizeSp -= 1f
                },
                onFontSizeIncrease = {
                    if (state.fontSizeSp < 32f) state.fontSizeSp += 1f
                }
            )
        }

        // Table of Contents Drawer Overlay
        if (state.showTocDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(end = 48.dp)
            ) {
                MarkdownTocDrawerContent(
                    rawMarkdown = state.textContent,
                    onHeaderClick = { tocItem ->
                        state.jumpToLine(tocItem.lineIndex + 1)
                        state.showTocDrawer = false
                    },
                    onClose = { state.showTocDrawer = false }
                )
            }
        }
    }
}

/**
 * UniversalFile & FileExplorerState integration overload of MarkorTextEngine.
 */
@Composable
fun MarkorTextEngine(
    file: UniversalFile,
    appState: FileExplorerState,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val fileRef = remember(file) {
        file.fileRef ?: (file.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) })
    }
    val uri = remember(file) {
        file.documentFileRef?.uri ?: getUriForUniversalFile(context, file)
    }

    // Load initial text asynchronously
    var initialTextLoaded by remember(file) { mutableStateOf("") }
    var isLoadingText by remember(file) { mutableStateOf(true) }

    LaunchedEffect(file) {
        isLoadingText = true
        withContext(Dispatchers.IO) {
            try {
                if (fileRef != null && fileRef.exists()) {
                    initialTextLoaded = fileRef.readText()
                } else if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        initialTextLoaded = stream.bufferedReader().readText()
                    }
                }
            } catch (_: Exception) {
                initialTextLoaded = ""
            }
        }
        isLoadingText = false
    }

    if (isLoadingText) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    fun saveContent(newContent: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val contentBytes = newContent.toByteArray()
                if (fileRef != null && fileRef.canWrite()) {
                    FileOutputStream(fileRef).use { out -> out.write(contentBytes) }
                    com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase.recordFileActivity(
                        context = context,
                        file = fileRef,
                        activityType = com.troikoss.continuum_explorer.data.db.SuiteActivityType.EDITED
                    )
                } else if (uri != null) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(contentBytes)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
                    appState.refresh()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareText(content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        context.startActivity(Intent.createChooser(intent, "Share Document"))
    }

    fun openExternal() {
        val targetUri = uri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "text/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No external app available", Toast.LENGTH_SHORT).show()
        }
    }

    // Path details and directory move dialogs
    var showViewPathDialog by remember { mutableStateOf(false) }
    var showChangePathPicker by remember { mutableStateOf(false) }

    if (showViewPathDialog) {
        AlertDialog(
            onDismissRequest = { showViewPathDialog = false },
            title = { Text("File Path Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File Name: ${file.name}", fontWeight = FontWeight.SemiBold)
                    Text("Absolute Path: ${file.fileRef?.absolutePath ?: file.absolutePath}")
                    Text("Parent Directory: ${file.fileRef?.parent ?: "Internal Storage"}")
                    Text("Provider: ${file.provider.kind.name}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewPathDialog = false }) { Text("Close") }
            }
        )
    }

    if (showChangePathPicker) {
        com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog(
            title = "Select New Directory for File",
            selectFolders = true,
            initialDirectory = fileRef?.parentFile ?: android.os.Environment.getExternalStorageDirectory(),
            onDismiss = { showChangePathPicker = false },
            onSelected = { selectedDir ->
                showChangePathPicker = false
                val newLocation = File(selectedDir, file.name)
                if (fileRef != null && fileRef.exists()) {
                    fileRef.renameTo(newLocation)
                    Toast.makeText(context, "File moved to ${newLocation.absolutePath}", Toast.LENGTH_SHORT).show()
                    appState.viewingFile = newLocation.toUniversal()
                    appState.refresh()
                } else {
                    Toast.makeText(context, "Cannot move virtual file", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    MarkorTextEngine(
        fileName = file.name,
        initialContent = initialTextLoaded,
        onSaveContent = { saveContent(it) },
        fileRef = fileRef,
        onMenuClick = onMenuClick,
        onExitEditor = { onMenuClick() },
        onShare = { shareText(initialTextLoaded) },
        onOpenExternal = { openExternal() },
        onViewPath = { showViewPathDialog = true },
        onChangePath = { showChangePathPicker = true },
        modifier = modifier
    )
}
