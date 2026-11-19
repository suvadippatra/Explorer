package com.troikoss.continuum_explorer.ui.preview

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.components.suite.TonalSearchPill
import com.troikoss.continuum_explorer.ui.components.suite.TonalVerticalPaneDivider
import com.troikoss.continuum_explorer.ui.editor.text.DocumentPreviewEngine
import com.troikoss.continuum_explorer.ui.editor.text.FormatRegistry
import com.troikoss.continuum_explorer.ui.editor.text.SyntaxHighlighter
import com.troikoss.continuum_explorer.ui.editor.text.TextFormatType
import com.troikoss.continuum_explorer.ui.editor.text.TodoTxtInteractiveView
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-page Image Viewer with pinch-to-zoom, double-tap zoom, pan, and 90° rotation.
 */
@Composable
fun ImageFullPageView(file: UniversalFile) {
    val context = LocalContext.current
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    var rotation by remember(file) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .clipToBounds()
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .pointerInput(file) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(file.fileRef ?: file.documentFileRef?.uri ?: file)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            loading = {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation
                )
        )

        // Floating Image Control Pill
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        scale = (scale - 0.5f).coerceIn(1f, 6f)
                        if (scale <= 1.05f) offset = Offset.Zero
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { scale = (scale + 0.5f).coerceIn(1f, 6f) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { rotation = (rotation + 90f) % 360f },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                if (scale > 1.05f || rotation != 0f) {
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            rotation = 0f
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Interactive HTML / XHTML Web Previewer with zero stacked top bar.
 * Mode switcher (Web / Split / Source), Desktop Viewport toggle, Dark canvas toggle,
 * and Reload sit cleanly in a borderless tonal bottom bar.
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlFullPagePreview(
    rawHtml: String,
    baseFile: java.io.File? = null,
    fileName: String = "index.html",
    onEditInCodeStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Web, 1 = Split, 2 = Source
    var desktopMode by remember { mutableStateOf(false) }
    var darkWebCanvas by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val baseUrl = remember(baseFile) {
        baseFile?.parentFile?.let { "file://${it.absolutePath}/" } ?: "file:///android_asset/"
    }

    val effectiveHtml = remember(rawHtml, darkWebCanvas) {
        if (!darkWebCanvas) rawHtml
        else {
            val darkStyle = "<style>html,body{background:#121212 !important;color:#e2e8f0 !important;}a{color:#60a5fa !important;}</style>"
            if (rawHtml.contains("</head>", ignoreCase = true)) {
                rawHtml.replaceFirst(Regex("(?i)</head>"), "$darkStyle</head>")
            } else {
                "$darkStyle$rawHtml"
            }
        }
    }

    @Composable
    fun RenderHtmlWebView(modifier: Modifier = Modifier) {
        key(reloadKey, desktopMode, darkWebCanvas) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = desktopMode
                        settings.loadWithOverviewMode = desktopMode
                        webViewClient = android.webkit.WebViewClient()
                        loadDataWithBaseURL(baseUrl, effectiveHtml, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webView.settings.useWideViewPort = desktopMode
                    webView.settings.loadWithOverviewMode = desktopMode
                    webView.loadDataWithBaseURL(baseUrl, effectiveHtml, "text/html", "UTF-8", null)
                }
            )
        }
    }

    @Composable
    fun RenderSourceCodePane(modifier: Modifier = Modifier) {
        val highlighted = remember(rawHtml, fileName) {
            SyntaxHighlighter.highlightText(rawHtml, FormatRegistry.detectFormat(fileName))
        }
        SelectionContainer {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFD4D4D4)
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Full-bleed HTML viewport directly beneath the single main top bar
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (viewMode) {
                0 -> RenderHtmlWebView(Modifier.fillMaxSize())
                1 -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        RenderSourceCodePane(Modifier.weight(1f).fillMaxHeight())
                        TonalVerticalPaneDivider()
                        RenderHtmlWebView(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                else -> RenderSourceCodePane(Modifier.fillMaxSize())
            }
        }

        // Sleek Borderless Tonal Bottom Control Strip (zero second top row!)
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf("Web", "Split", "Source")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.height(30.dp)) {
                    modes.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = viewMode == idx,
                            onClick = { viewMode = idx },
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
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Surface(
                    onClick = {
                        desktopMode = !desktopMode
                        reloadKey++
                    },
                    shape = RoundedCornerShape(50),
                    color = if (desktopMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            if (desktopMode) Icons.Default.DesktopWindows else Icons.Default.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (desktopMode) "Desktop" else "Fit",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                IconButton(
                    onClick = {
                        darkWebCanvas = !darkWebCanvas
                        reloadKey++
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        if (darkWebCanvas) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Web Theme",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { reloadKey++ },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Full-page Read-Only Document, HTML & Code Viewer.
 * Zero stacked top header or duplicate Edit button — renders content full-bleed
 * with a clean floating bottom-right Rendered/Raw pill when rich preview is supported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFullPageView(
    file: UniversalFile,
    appState: FileExplorerState? = null
) {
    var textContent by remember(file) { mutableStateOf<String?>(null) }
    var isLoading by remember(file) { mutableStateOf(true) }
    val ext = remember(file.name) { file.name.substringAfterLast('.', "").lowercase() }
    val isHtmlFile = remember(ext) { ext in listOf("html", "htm", "xhtml") }
    val formatType = remember(file.name) { FormatRegistry.detectFormat(file.name) }
    val supportsRichPreview = remember(formatType, isHtmlFile) {
        isHtmlFile ||
            formatType == TextFormatType.MARKDOWN ||
            formatType == TextFormatType.ORG ||
            formatType == TextFormatType.ASCIIDOC ||
            formatType == TextFormatType.CSV ||
            formatType == TextFormatType.TODO_TXT
    }
    var showRawSource by remember(file) { mutableStateOf(!supportsRichPreview) }

    LaunchedEffect(file) {
        isLoading = true
        textContent = withContext(Dispatchers.IO) {
            try {
                file.provider.openInput(file.providerId).bufferedReader().use { reader ->
                    val buffer = CharArray(200_000)
                    val read = reader.read(buffer)
                    if (read > 0) String(buffer, 0, read) else ""
                }
            } catch (e: Exception) {
                "Error reading file: ${e.localizedMessage}"
            }
        }
        isLoading = false
    }

    if (!isLoading && isHtmlFile) {
        HtmlFullPagePreview(
            rawHtml = textContent ?: "",
            baseFile = file.fileRef,
            fileName = file.name,
            onEditInCodeStudio = null,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            !showRawSource && formatType == TextFormatType.CSV -> {
                CsvInteractiveTablePreview(
                    rawText = textContent ?: "",
                    modifier = Modifier.fillMaxSize()
                )
            }
            !showRawSource && formatType == TextFormatType.TODO_TXT -> {
                TodoTxtInteractiveView(
                    rawText = textContent ?: "",
                    onTextChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !showRawSource && supportsRichPreview -> {
                DocumentPreviewEngine(
                    rawText = textContent ?: "",
                    formatType = formatType,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val highlighted = remember(textContent, formatType) {
                    SyntaxHighlighter.highlightText(textContent ?: "", formatType)
                }
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = highlighted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Compact Floating Rendered | Raw switcher pill (bottom-right, never stacks a top row)
        if (!isLoading && supportsRichPreview && !isHtmlFile) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = if (formatType == TextFormatType.CSV && !showRawSource) 54.dp else 14.dp)
                    .shadow(6.dp, RoundedCornerShape(50))
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(false to "Rendered", true to "Raw").forEach { (raw, label) ->
                        val active = showRawSource == raw
                        Surface(
                            onClick = { showRawSource = raw },
                            shape = RoundedCornerShape(50),
                            color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ultra-Clean Tonal Flat Spreadsheet Table Preview for .csv and .tsv files.
 * Zero stacked top filter bar — filter pill & row stats live neatly in a slim 42.dp bottom bar.
 */
@Composable
fun CsvInteractiveTablePreview(
    rawText: String,
    modifier: Modifier = Modifier
) {
    var filterQuery by remember { mutableStateOf("") }
    var sortColumnIndex by remember { mutableStateOf<Int?>(null) }
    var sortAscending by remember { mutableStateOf(true) }
    val delimiter = remember(rawText) {
        val firstLine = rawText.lineSequence().firstOrNull().orEmpty()
        when {
            firstLine.contains('\t') -> '\t'
            firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
            else -> ','
        }
    }

    val allRows = remember(rawText, delimiter) {
        rawText.lineSequence()
            .filter { it.isNotBlank() }
            .map { line -> parseCsvPreviewLine(line, delimiter) }
            .toList()
    }

    val headerRow = allRows.firstOrNull().orEmpty()
    val dataRows = remember(allRows, filterQuery, sortColumnIndex, sortAscending) {
        val body = if (allRows.size > 1) allRows.drop(1) else emptyList()
        val filtered = if (filterQuery.isBlank()) body
        else body.filter { row -> row.any { cell -> cell.contains(filterQuery.trim(), ignoreCase = true) } }
        val sCol = sortColumnIndex
        if (sCol == null) {
            filtered
        } else {
            val sorted = filtered.sortedWith { a, b ->
                val va = a.getOrNull(sCol).orEmpty()
                val vb = b.getOrNull(sCol).orEmpty()
                val na = va.toDoubleOrNull()
                val nb = vb.toDoubleOrNull()
                if (na != null && nb != null) na.compareTo(nb)
                else va.compareTo(vb, ignoreCase = true)
            }
            if (sortAscending) sorted else sorted.reversed()
        }
    }
    val maxCols = remember(allRows) { (allRows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val hScroll = rememberScrollState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(hScroll)
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                // Sticky Tonal Header Row (Clickable for column sorting)
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "#",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    for (colIdx in 0 until maxCols) {
                        VerticalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxHeight()
                        )
                        val colTitle = headerRow.getOrNull(colIdx)?.ifBlank { "Col ${colIdx + 1}" } ?: "Col ${colIdx + 1}"
                        val isSorted = sortColumnIndex == colIdx
                        val sortArrow = if (isSorted) (if (sortAscending) " ▲" else " ▼") else ""
                        Box(
                            modifier = Modifier
                                .width(156.dp)
                                .fillMaxHeight()
                                .pointerInput(colIdx) {
                                    detectTapGestures(
                                        onTap = {
                                            if (sortColumnIndex == colIdx) {
                                                sortAscending = !sortAscending
                                            } else {
                                                sortColumnIndex = colIdx
                                                sortAscending = true
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = colTitle + sortArrow,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(dataRows.size) { rowIndex ->
                        val row = dataRows[rowIndex]
                        Row(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .background(
                                    if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${rowIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            for (colIdx in 0 until maxCols) {
                                VerticalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                                    modifier = Modifier.height(38.dp)
                                )
                                val cell = row.getOrNull(colIdx).orEmpty()
                                Box(
                                    modifier = Modifier
                                        .width(156.dp)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cell,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sleek 42.dp Bottom Table Filter & Stats Strip
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${dataRows.size} rows × $maxCols cols",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sortColumnIndex != null) {
                        TextButton(
                            onClick = { sortColumnIndex = null },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Reset Sort", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                TonalSearchPill(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = "Filter rows",
                    modifier = Modifier.widthIn(min = 150.dp, max = 240.dp),
                    testTagName = "csv_preview_filter"
                )
            }
        }
    }
}

private fun parseCsvPreviewLine(line: String, delimiter: Char): List<String> {
    val cells = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            c == delimiter && !inQuotes -> {
                cells.add(sb.toString().trim())
                sb.setLength(0)
            }
            else -> sb.append(c)
        }
        i++
    }
    cells.add(sb.toString().trim())
    return cells
}

@Composable
fun UnsupportedFullPageView(
    file: UniversalFile,
    appState: FileExplorerState
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${appState.formatSize(file.length)} • ${appState.formatDate(file.lastModified)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = {
                    appState.viewingFile = file
                    appState.openInEditMode = true
                },
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open as Text")
            }

            Button(
                onClick = {
                    val uri = getUriForUniversalFile(context, file)
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "Open with"))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No external app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open External")
            }
        }
    }
}
