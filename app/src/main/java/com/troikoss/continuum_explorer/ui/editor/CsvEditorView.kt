package com.troikoss.continuum_explorer.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Material 3 CSV / TSV Spreadsheet Table Editor & Raw CSV View with quote-aware parser,
 * row filtering, column/row operations, and unsaved-changes protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvEditorView(
    file: File,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onMenuClick: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var rawText by remember(file.absolutePath, content) { mutableStateOf(content) }
    var lastSavedText by remember(file.absolutePath) { mutableStateOf(content) }
    val isModified = rawText != lastSavedText
    var showUnsavedDialog by remember { mutableStateOf(false) }

    var isGridView by remember { mutableStateOf(true) }
    var filterQuery by remember { mutableStateOf("") }

    // Detect delimiter (\t, ;, or ,)
    val delimiter = remember(rawText) {
        val firstLine = rawText.lineSequence().firstOrNull().orEmpty()
        when {
            firstLine.contains('\t') -> '\t'
            firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
            else -> ','
        }
    }

    var csvRows by remember(rawText) {
        mutableStateOf(parseCsvQuotesAware(rawText, delimiter))
    }

    fun syncToRawText(newRows: List<List<String>>) {
        csvRows = newRows
        val builder = StringBuilder()
        for (row in newRows) {
            builder.append(row.joinToString(delimiter.toString()) { cell ->
                if (cell.contains(delimiter) || cell.contains('"') || cell.contains('\n')) {
                    "\"${cell.replace("\"", "\"\"")}\""
                } else cell
            }).append("\n")
        }
        val text = builder.toString().trimEnd('\n')
        rawText = text
        onContentChange(text)
    }

    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingCellText by remember { mutableStateOf("") }

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
            icon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unsaved Table Changes", fontWeight = FontWeight.Bold) },
            text = { Text("Save your changes to \"${file.name}\" before leaving CSV Studio?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSave()
                        lastSavedText = rawText
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
        // Unified 50.dp Toolbar with Single Clean Back Button on Left
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
                            .testTag("csv_editor_menu_button")
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
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = isGridView,
                        onClick = { isGridView = true },
                        label = { Text("Grid", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = !isGridView,
                        onClick = { isGridView = false },
                        label = { Text("Raw CSV", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )

                    IconButton(
                        onClick = {
                            onSave()
                            lastSavedText = rawText
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("csv_editor_save_button")
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

        // Table Action & Filter Bar
        if (isGridView) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val colCount = csvRows.firstOrNull()?.size ?: 3
                                val newRows = csvRows.toMutableList()
                                newRows.add(List(colCount) { "" })
                                syncToRawText(newRows)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Row", style = MaterialTheme.typography.labelSmall)
                        }

                        FilledTonalButton(
                            onClick = {
                                val newRows = csvRows.map { row -> row + "" }.toMutableList()
                                if (newRows.isEmpty()) newRows.add(listOf(""))
                                syncToRawText(newRows)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.ViewColumn, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Column", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Filter cells...", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        trailingIcon = {
                            if (filterQuery.isNotEmpty()) {
                                IconButton(onClick = { filterQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(13.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier
                            .widthIn(min = 140.dp, max = 240.dp)
                            .height(42.dp)
                            .testTag("csv_filter_input")
                    )
                }
            }
        }

        // Body
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isGridView) {
                val maxCols = remember(csvRows) { (csvRows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1) }
                val visibleRows = remember(csvRows, filterQuery) {
                    if (filterQuery.isBlank()) {
                        csvRows.mapIndexed { idx, row -> idx to row }
                    } else {
                        csvRows.mapIndexedNotNull { idx, row ->
                            if (idx == 0 || row.any { it.contains(filterQuery, ignoreCase = true) }) idx to row else null
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        // Table Column Letters Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("#", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                                for (colIndex in 0 until maxCols) {
                                    Box(
                                        modifier = Modifier
                                            .width(136.dp)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = getColName(colIndex),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Box(modifier = Modifier.width(40.dp))
                            }
                        }

                        // Data Rows
                        itemsIndexed(visibleRows, key = { _, pair -> pair.first }) { _, (rowIndex, row) ->
                            val isHeaderRow = rowIndex == 0
                            Row(
                                modifier = Modifier
                                    .background(
                                        when {
                                            isHeaderRow -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                            rowIndex % 2 == 0 -> MaterialTheme.colorScheme.surface
                                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                                        }
                                    )
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rowIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                for (colIndex in 0 until maxCols) {
                                    val cellText = row.getOrNull(colIndex) ?: ""
                                    Box(
                                        modifier = Modifier
                                            .width(136.dp)
                                            .height(40.dp)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .clickable {
                                                editingCell = Pair(rowIndex, colIndex)
                                                editingCellText = cellText
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cellText.ifEmpty { "—" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isHeaderRow) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (cellText.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Delete Row button
                                IconButton(
                                    onClick = {
                                        if (csvRows.size > 1) {
                                            val updated = csvRows.toMutableList().apply { removeAt(rowIndex) }
                                            syncToRawText(updated)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Row",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Material 3 Raw CSV Text Editor
                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        onContentChange(it)
                        csvRows = parseCsvQuotesAware(it, delimiter)
                    },
                    label = { Text("Raw Delimited Source (${if (delimiter == '\t') "TSV" else "CSV"})") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .testTag("csv_raw_text_field")
                )
            }
        }
    }

    // Material 3 Cell Edit Dialog
    editingCell?.let { (r, c) ->
        AlertDialog(
            onDismissRequest = { editingCell = null },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Edit Cell ${getColName(c)}${r + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editingCellText,
                    onValueChange = { editingCellText = it },
                    label = { Text("Cell Value") },
                    leadingIcon = { Icon(Icons.Default.TableRows, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (editingCellText.isNotEmpty()) {
                            IconButton(onClick = { editingCellText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("csv_cell_edit_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    val newRows = csvRows.mapIndexed { rIdx, row ->
                        if (rIdx == r) {
                            val mutableRow = row.toMutableList()
                            while (mutableRow.size <= c) mutableRow.add("")
                            mutableRow[c] = editingCellText
                            mutableRow
                        } else row
                    }
                    syncToRawText(newRows)
                    editingCell = null
                }) { Text("Update Cell") }
            },
            dismissButton = {
                TextButton(onClick = { editingCell = null }) { Text("Cancel") }
            }
        )
    }
}

private fun parseCsvQuotesAware(text: String, delimiter: Char): List<List<String>> {
    if (text.isBlank()) return listOf(listOf(""))
    val rows = mutableListOf<List<String>>()
    for (line in text.split("\n")) {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    cells.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        cells.add(sb.toString().trim())
        rows.add(cells)
    }
    return rows.ifEmpty { listOf(listOf("")) }
}

private fun getColName(index: Int): String {
    val first = (index / 26)
    val second = (index % 26)
    return if (first > 0) "${(64 + first).toChar()}${(65 + second).toChar()}" else "${(65 + second).toChar()}"
}
