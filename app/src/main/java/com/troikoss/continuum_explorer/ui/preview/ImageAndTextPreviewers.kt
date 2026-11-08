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
                .padding(bottom = 20.dp)
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
 * Full-page Read-Only Document & Code Viewer with Markor WebView Rendering
 * for Markdown/Org/AsciiDoc/CSV, Interactive Task View for Todo.txt,
 * and Syntax-Highlighted Code View with a quick "Edit in Markor" button.
 */
@Composable
fun TextFullPageView(
    file: UniversalFile,
    appState: FileExplorerState? = null
) {
    var textContent by remember(file) { mutableStateOf<String?>(null) }
    var isLoading by remember(file) { mutableStateOf(true) }
    val formatType = remember(file.name) { FormatRegistry.detectFormat(file.name) }
    val supportsRichPreview = remember(formatType) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Quick Action Bar for switching Rendered vs Raw Source or launching Markor Editor
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = formatType.name.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (supportsRichPreview) {
                        FilterChip(
                            selected = !showRawSource,
                            onClick = { showRawSource = false },
                            label = { Text("Rendered View", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = showRawSource,
                            onClick = { showRawSource = true },
                            label = { Text("Raw Source", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (appState != null) {
                    val ext = file.name.substringAfterLast('.', "").lowercase()
                    val studioButtonLabel = when {
                        formatType == TextFormatType.CSV || ext in listOf("csv", "tsv") -> "Edit in CSV Studio"
                        ext in listOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "yaml", "yml", "sh", "sql", "c", "cpp") -> "Edit in Code Studio"
                        else -> "Edit in Markor"
                    }
                    FilledTonalButton(
                        onClick = {
                            appState.viewingFile = file
                            appState.openInEditMode = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(studioButtonLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                .padding(16.dp)
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
        }
    }
}

/**
 * Interactive formatted Spreadsheet Table Preview for .csv and .tsv files
 * with sticky header row, row numbers, alternating row striping, and row search filter.
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
                if (na != null && nb != null) {
                    na.compareTo(nb)
                } else {
                    va.compareTo(vb, ignoreCase = true)
                }
            }
            if (sortAscending) sorted else sorted.reversed()
        }
    }
    val maxCols = remember(allRows) { (allRows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Table Filter & Stats Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${dataRows.size} rows × $maxCols columns",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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

                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filter rows...", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    trailingIcon = {
                        if (filterQuery.isNotEmpty()) {
                            IconButton(onClick = { filterQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(13.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 280.dp)
                        .height(44.dp)
                )
            }
        }

        val hScroll = rememberScrollState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(hScroll)
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                // Sticky Header Row (Clickable for column sorting)
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("#", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    for (colIdx in 0 until maxCols) {
                        val colTitle = headerRow.getOrNull(colIdx)?.ifBlank { "Col ${colIdx + 1}" } ?: "Col ${colIdx + 1}"
                        val isSorted = sortColumnIndex == colIdx
                        val sortArrow = if (isSorted) (if (sortAscending) " ▲" else " ▼") else ""
                        Box(
                            modifier = Modifier
                                .width(150.dp)
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
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = colTitle + sortArrow,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    items(dataRows.size) { rowIndex ->
                        val row = dataRows[rowIndex]
                        Row(
                            modifier = Modifier
                                .background(
                                    if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .padding(6.dp),
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
                                val cell = row.getOrNull(colIdx).orEmpty()
                                Box(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
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
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Size: ${appState.formatSize(file.length)} • Modified: ${appState.formatDate(file.lastModified)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    appState.viewingFile = file
                    appState.openInEditMode = true
                }
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
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
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Externally")
            }
        }
    }
}
