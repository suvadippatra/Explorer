package com.troikoss.continuum_explorer.ui.editor

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.SlimStatusStrip
import com.troikoss.continuum_explorer.ui.components.suite.TonalFloatingFindOverlay
import com.troikoss.continuum_explorer.ui.components.suite.TonalVerticalPaneDivider
import com.troikoss.continuum_explorer.ui.editor.text.FormatRegistry
import com.troikoss.continuum_explorer.ui.editor.text.SyntaxHighlighter
import java.io.File

/**
 * Desktop-First Ultra-Clean Tonal Flat Code Studio Editor.
 * - Strict single 48.dp Command Bar (no stacked Find/Replace or metadata rows).
 * - Floating top-right Tonal Find & Replace card.
 * - 1:1 TextLayoutResult-synchronized line numbers & live HTML5 Split/Preview.
 * - Quiet 26.dp bottom status strip for language, lines, chars, and font zoom.
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
    var showMoreMenu by remember { mutableStateOf(false) }

    val ext = remember(file.name) { file.extension.lowercase() }
    val langLabel = remember(ext) { ext.uppercase().ifEmpty { "CODE" } }
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

    BackHandler { handleExit() }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            icon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unsaved Changes", fontWeight = FontWeight.Bold) },
            text = { Text("Save your changes to \"${file.name}\" before leaving Code Studio?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSave()
                        lastSavedText = textValue
                        showUnsavedDialog = false
                        (onBackClick ?: onMenuClick).invoke()
                    }
                ) { Text("Save & Exit") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            showUnsavedDialog = false
                            (onBackClick ?: onMenuClick).invoke()
                        }
                    ) { Text("Discard") }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isLargeScreen = maxWidth >= 640.dp
        val isFullScreen by com.troikoss.continuum_explorer.managers.SettingsManager.isFullScreenTabBarHidden

        Column(modifier = Modifier.fillMaxSize()) {
            // Strict Single 48.dp Tonal Flat Command Bar
            SingleRowCommandBar(
                navigationContent = {
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
                },
                titleContent = {
                    Text(
                        text = if (isModified) "${file.name} •" else file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                centerContent = {
                    if (isHtmlFile && isLargeScreen) {
                        val modes = listOf("Code", "Split", "Preview")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
                            modes.forEachIndexed { idx, label ->
                                SegmentedButton(
                                    selected = htmlPreviewMode == idx,
                                    onClick = { htmlPreviewMode = idx },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                                    icon = {},
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        activeBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (htmlPreviewMode == idx) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                },
                actionsContent = {
                    if (isHtmlFile && !isLargeScreen) {
                        IconButton(
                            onClick = { htmlPreviewMode = (htmlPreviewMode + 1) % 3 },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = when (htmlPreviewMode) {
                                    0 -> Icons.Default.Web
                                    1 -> Icons.Default.VerticalSplit
                                    else -> Icons.Default.Code
                                },
                                contentDescription = "Switch HTML View",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { wordWrapEnabled = !wordWrapEnabled },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.WrapText,
                            contentDescription = "Word Wrap",
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
                            tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isLargeScreen) {
                        FilledTonalButton(
                            onClick = {
                                onSave()
                                lastSavedText = textValue
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("code_editor_save_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
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

                    Box {
                        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isFullScreen) "Show Tab Bar" else "Full Screen") },
                                leadingIcon = {
                                    Icon(
                                        if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    com.troikoss.continuum_explorer.managers.SettingsManager.toggleFullScreenTabBar()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close Editor", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    handleExit()
                                }
                            )
                        }
                    }
                }
            )

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

                Box(modifier = modifier.background(Color(0xFF1E1E1E))) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                    ) {
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

                        val codeBoxMod = if (wordWrapEnabled) {
                            Modifier
                                .weight(1f)
                                .padding(12.dp)
                        } else {
                            Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                                .padding(12.dp)
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

            // Full-bleed Code Workspace + Floating Find/Replace Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isHtmlFile && htmlPreviewMode == 2 -> HtmlWebPane(Modifier.fillMaxSize())
                    isHtmlFile && htmlPreviewMode == 1 -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            CodePane(Modifier.weight(1f).fillMaxHeight())
                            TonalVerticalPaneDivider()
                            HtmlWebPane(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    else -> CodePane(Modifier.fillMaxSize())
                }

                if (showSearch) {
                    TonalFloatingFindOverlay(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        replaceQuery = replaceQuery,
                        onReplaceChange = { replaceQuery = it },
                        matchCountLabel = if (searchQuery.isNotEmpty()) "$matchCount" else null,
                        onFindNext = {
                            if (searchQuery.isNotEmpty() && matchCount > 0) {
                                val updated = textValue.replaceFirst(searchQuery, replaceQuery, ignoreCase = true)
                                textValue = updated
                                onContentChange(updated)
                            }
                        },
                        onReplaceAll = {
                            if (searchQuery.isNotEmpty() && matchCount > 0) {
                                val updated = textValue.replace(searchQuery, replaceQuery, ignoreCase = true)
                                textValue = updated
                                onContentChange(updated)
                            }
                        },
                        onClose = { showSearch = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }
            }

            // Quiet 26.dp Desktop Status Strip
            SlimStatusStrip(
                leftText = "$langLabel • UTF-8 • $lineCount lines • $charCount chars" + if (isModified) " • Modified" else " • Saved",
                centerContent = {
                    Text(
                        text = "A-",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { if (fontSizeSp > 10) fontSizeSp-- }
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "${fontSizeSp}sp",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "A+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { if (fontSizeSp < 24) fontSizeSp++ }
                            .padding(horizontal = 4.dp)
                    )
                },
                rightText = if (wordWrapEnabled) "Wrap" else "No Wrap"
            )
        }
    }
}
