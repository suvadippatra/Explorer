package com.troikoss.continuum_explorer.ui.editor

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.troikoss.continuum_explorer.ui.editor.text.FormatRegistry
import com.troikoss.continuum_explorer.ui.editor.text.SyntaxHighlighter
import java.io.File

/**
 * Code Studio Editor with syntax highlighting, 1:1 TextLayoutResult-synchronized line numbers,
 * Material 3 Find & Replace, unsaved-changes guard, and built-in Live HTML5 Preview / Split View.
 * In Create/Edit mode:
 * - Shows Triple-Line Menu button (`Icons.Default.Menu`) to toggle the sidebar (no Back button or decorative icon before title).
 * - Closing the editor is handled via system Back gesture or closing the tab.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorView(
    file: File,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onMenuClick: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var textValue by remember(file.absolutePath, content) { mutableStateOf(content) }
    var lastSavedText by remember(file.absolutePath) { mutableStateOf(content) }
    val isModified = textValue != lastSavedText
    var showUnsavedDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableIntStateOf(13) }
    var wordWrapEnabled by remember { mutableStateOf(true) }

    val ext = remember(file.name) { file.extension.lowercase() }
    val isHtmlFile = remember(ext) { ext in listOf("html", "htm", "xhtml") }
    // 0 = Code Only, 1 = Split Code + HTML Preview, 2 = Full HTML Preview
    var htmlPreviewMode by remember(file.absolutePath) { mutableIntStateOf(0) }

    val logicalLineStartOffsets = remember(textValue) {
        val offsets = mutableListOf(0)
        for (i in textValue.indices) {
            if (textValue[i] == '\n') {
                offsets.add(i + 1)
            }
        }
        offsets
    }
    val lineCount = logicalLineStartOffsets.size
    val charCount = textValue.length
    val matchCount = remember(textValue, searchQuery) {
        if (searchQuery.isBlank()) 0
        else Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(textValue).count()
    }

    val formatType = remember(file.name) { FormatRegistry.detectFormat(file.name) }
    val syntaxHighlighter = remember(formatType) { SyntaxHighlighter(formatType) }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    val handleExit = {
        if (isModified) {
            showUnsavedDialog = true
        } else {
            (onBackClick ?: onMenuClick).invoke()
        }
    }

    BackHandler {
        handleExit()
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            icon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unsaved Code Changes", fontWeight = FontWeight.Bold) },
            text = { Text("Save your changes to \"${file.name}\" before leaving Code Studio?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSave()
                        lastSavedText = textValue
                        showUnsavedDialog = false
                        (onBackClick ?: onMenuClick).invoke()
                    }
                ) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showUnsavedDialog = false
                            (onBackClick ?: onMenuClick).invoke()
                        }
                    ) {
                        Text("Discard")
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Unified 50.dp Toolbar with Triple-Line Menu Button on Left (no Back button or decorative icon before title)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("code_editor_menu_button")
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (isModified) "${file.name} •" else file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isModified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = file.extension.uppercase().ifEmpty { "CODE" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isModified) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isHtmlFile) {
                        FilterChip(
                            selected = htmlPreviewMode == 1,
                            onClick = { htmlPreviewMode = if (htmlPreviewMode == 1) 0 else 1 },
                            label = { Text("Split", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.VerticalSplit, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.height(30.dp)
                        )
                        FilterChip(
                            selected = htmlPreviewMode == 2,
                            onClick = { htmlPreviewMode = if (htmlPreviewMode == 2) 0 else 2 },
                            label = { Text("Preview", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Web, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                    IconButton(
                        onClick = { wordWrapEnabled = !wordWrapEnabled },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.WrapText,
                            contentDescription = "Toggle Word Wrap",
                            tint = if (wordWrapEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("code_editor_search_toggle")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Find & Replace",
                            tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            onSave()
                            lastSavedText = textValue
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("code_editor_save_button")
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // Material 3 Find & Replace Panel
        AnimatedVisibility(visible = showSearch) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in code...", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        Text(
                                            text = "$matchCount matches",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("code_search_input")
                        )
                        IconButton(onClick = { showSearch = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search", modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace with...", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("code_replace_input")
                        )
                        FilledTonalButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    val updated = textValue.replaceFirst(searchQuery, replaceQuery, ignoreCase = true)
                                    textValue = updated
                                    onContentChange(updated)
                                }
                            },
                            enabled = searchQuery.isNotEmpty() && matchCount > 0,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Replace", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    val updated = textValue.replace(searchQuery, replaceQuery, ignoreCase = true)
                                    textValue = updated
                                    onContentChange(updated)
                                }
                            },
                            enabled = searchQuery.isNotEmpty() && matchCount > 0,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("All", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        @Composable
        fun CodePane(modifier: Modifier = Modifier) {
            val gutterDigits = lineCount.toString().length.coerceAtLeast(2)
            val gutterWidthDp = ((gutterDigits * (fontSizeSp * 0.65f)) + 22f).dp
            val totalHeightDp = with(density) {
                (textLayoutResult?.size?.height ?: 0).toDp().coerceAtLeast(240.dp)
            }
            val textSizePx = with(density) { fontSizeSp.sp.toPx() }
            val rightPadPx = with(density) { 10.dp.toPx() }
            val lineNumArgb = Color(0xFF858585).toArgb()

            Box(
                modifier = modifier
                    .background(Color(0xFF1E1E1E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                ) {
                    // 1:1 TextLayoutResult-Synchronized Line Number Gutter
                    Box(
                        modifier = Modifier
                            .width(gutterWidthDp)
                            .heightIn(min = totalHeightDp + 20.dp)
                            .background(Color(0xFF252526))
                            .padding(vertical = 10.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(totalHeightDp)
                        ) {
                            val layout = textLayoutResult ?: return@Canvas
                            val textLen = textValue.length
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = lineNumArgb
                                textAlign = android.graphics.Paint.Align.RIGHT
                                textSize = textSizePx
                                typeface = android.graphics.Typeface.MONOSPACE
                            }
                            logicalLineStartOffsets.forEachIndexed { idx, startOffset ->
                                val safeOffset = startOffset.coerceIn(0, textLen)
                                val visualLine = try {
                                    layout.getLineForOffset(safeOffset)
                                } catch (_: Exception) {
                                    0
                                }
                                val baselineY = try {
                                    layout.getLineBaseline(visualLine)
                                } catch (_: Exception) {
                                    visualLine * (textSizePx + 10f) + textSizePx
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    (idx + 1).toString(),
                                    size.width - rightPadPx,
                                    baselineY,
                                    paint
                                )
                            }
                        }
                    }

                    // Code Input Field with Live Syntax Highlighting
                    val codeBoxMod = if (wordWrapEnabled) {
                        Modifier
                            .weight(1f)
                            .padding(10.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                            .padding(10.dp)
                    }

                    Box(modifier = codeBoxMod) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = {
                                textValue = it
                                onContentChange(it)
                            },
                            onTextLayout = { result -> textLayoutResult = result },
                            visualTransformation = syntaxHighlighter,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                color = Color(0xFFD4D4D4),
                                lineHeight = (fontSizeSp + 5).sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF00E676)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp)
                                .testTag("code_editor_text_field")
                        )
                    }
                }
            }
        }

        @Composable
        fun HtmlWebPane(modifier: Modifier = Modifier) {
            val baseUrl = remember(file) {
                file.parentFile?.let { "file://${it.absolutePath}/" } ?: "file:///android_asset/"
            }
            AndroidView(
                modifier = modifier.background(Color.White),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        webViewClient = android.webkit.WebViewClient()
                        loadDataWithBaseURL(baseUrl, textValue, "text/html", "UTF-8", null)
                    }
                },
                update = { wv ->
                    wv.loadDataWithBaseURL(baseUrl, textValue, "text/html", "UTF-8", null)
                }
            )
        }

        // Main Workspace Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isHtmlFile && htmlPreviewMode == 2 -> {
                    HtmlWebPane(Modifier.fillMaxSize())
                }
                isHtmlFile && htmlPreviewMode == 1 -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        CodePane(Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        HtmlWebPane(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                else -> {
                    CodePane(Modifier.fillMaxSize())
                }
            }
        }

        // Status Footer Bar with Font Zoom Controls
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lines: $lineCount • Chars: $charCount" + if (isModified) " • Modified" else " • Saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { if (fontSizeSp > 10) fontSizeSp-- },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Smaller Font", modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = "${fontSizeSp}sp • UTF-8",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { if (fontSizeSp < 24) fontSizeSp++ },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Larger Font", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
