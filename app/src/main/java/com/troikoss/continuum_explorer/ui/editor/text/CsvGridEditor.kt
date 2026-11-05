package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Interactive tabular spreadsheet matrix editor for CSV/TSV files.
 * Supports inline editing, adding/deleting rows & columns, column sorting, and aligning table data.
 */
@Composable
fun CsvGridEditor(
    rawCsvText: String,
    onSaveCsv: (updatedCsvText: String) -> Unit,
    onCloseGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Detect delimiter: comma, semicolon, tab
    val delimiter = remember(rawCsvText) {
        detectDelimiter(rawCsvText)
    }

    // Parse CSV lines into a mutable matrix state
    var gridData by remember(rawCsvText, delimiter) {
        val lines = rawCsvText.lines().filter { it.isNotBlank() }
        val initialRows = if (lines.isEmpty()) {
            listOf(listOf("Column 1", "Column 2"), listOf("", ""))
        } else {
            val rows = lines.map { line ->
                parseCsvLine(line, delimiter)
            }
            val maxCols = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            rows.map { row ->
                if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
            }
        }
        mutableStateOf(initialRows)
    }

    var selectedCellRow by remember { mutableIntStateOf(-1) }
    var selectedCellCol by remember { mutableIntStateOf(-1) }
    var cellEditValue by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }

    // Column sorting state
    var sortColIndex by remember { mutableIntStateOf(-1) }
    var sortAscending by remember { mutableStateOf(true) }

    // Column context menu / delete confirmation
    var showDeleteColDialog by remember { mutableStateOf(false) }
    var colToDeleteIndex by remember { mutableIntStateOf(-1) }

    fun serializeBack(): String {
        return gridData.joinToString("\n") { row ->
            row.joinToString(delimiter.toString()) { cell ->
                if (cell.contains(delimiter) || cell.contains("\"") || cell.contains("\n")) {
                    "\"${cell.replace("\"", "\"\"")}\""
                } else {
                    cell
                }
            }
        }
    }

    fun updateCell(rowIndex: Int, colIndex: Int, newValue: String) {
        gridData = gridData.mapIndexed { r, row ->
            if (r == rowIndex) {
                row.mapIndexed { c, cell -> if (c == colIndex) newValue else cell }
            } else row
        }
    }

    fun addRow() {
        val colCount = if (gridData.isNotEmpty()) gridData[0].size else 2
        val newRow = List(colCount) { "" }
        gridData = gridData + listOf(newRow)
    }

    fun deleteRow(index: Int) {
        if (gridData.size > 1 && index in gridData.indices) {
            gridData = gridData.filterIndexed { r, _ -> r != index }
        }
    }

    fun addColumn() {
        val colNum = if (gridData.isNotEmpty()) gridData[0].size + 1 else 1
        gridData = gridData.mapIndexed { r, row ->
            row + if (r == 0) "Column $colNum" else ""
        }
    }

    fun deleteColumn(colIndex: Int) {
        if (gridData.isNotEmpty() && gridData[0].size > 1) {
            gridData = gridData.map { row ->
                row.filterIndexed { c, _ -> c != colIndex }
            }
        }
    }

    fun sortColumn(colIndex: Int) {
        if (gridData.size <= 1) return
        val isAsc = if (sortColIndex == colIndex) !sortAscending else true
        sortColIndex = colIndex
        sortAscending = isAsc

        val header = gridData.first()
        val dataRows = gridData.drop(1).sortedWith { r1, r2 ->
            val v1 = r1.getOrElse(colIndex) { "" }
            val v2 = r2.getOrElse(colIndex) { "" }
            if (isAsc) v1.compareTo(v2, ignoreCase = true) else v2.compareTo(v1, ignoreCase = true)
        }

        gridData = listOf(header) + dataRows
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "Edit Cell [Row ${selectedCellRow + 1}, Col ${selectedCellCol + 1}]",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = cellEditValue,
                    onValueChange = { cellEditValue = it },
                    label = { Text("Cell Value") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    updateCell(selectedCellRow, selectedCellCol, cellEditValue)
                    showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteColDialog && colToDeleteIndex >= 0) {
        AlertDialog(
            onDismissRequest = { showDeleteColDialog = false },
            title = { Text("Delete Column?", fontWeight = FontWeight.Bold) },
            text = {
                val colName = gridData.firstOrNull()?.getOrNull(colToDeleteIndex) ?: "Column ${colToDeleteIndex + 1}"
                Text("Are you sure you want to delete '$colName'? All data in this column will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteColumn(colToDeleteIndex)
                        showDeleteColDialog = false
                        colToDeleteIndex = -1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteColDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Grid Top Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCloseGrid, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to text", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "Visual Grid (${if (delimiter == '\t') "TSV" else "CSV"})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { addRow() },
                        label = { Text("+ Row") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    AssistChip(
                        onClick = { addColumn() },
                        label = { Text("+ Col") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    Button(
                        onClick = {
                            onSaveCsv(serializeBack())
                            onCloseGrid()
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Horizontal and Vertical Scrollable Spreadsheet Matrix
        val horizontalScrollState = rememberScrollState()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (gridData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data available in spreadsheet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState)) {
                    LazyColumn(modifier = Modifier.fillMaxHeight()) {
                        itemsIndexed(gridData) { rowIndex, rowList ->
                            val isHeader = rowIndex == 0
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (isHeader) MaterialTheme.colorScheme.primaryContainer
                                        else if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                // Row Header Index
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(44.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isHeader) "#" else "$rowIndex",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                rowList.forEachIndexed { colIndex, cellValue ->
                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(44.dp)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .clickable {
                                                if (isHeader) {
                                                    // Toggle sort or long press to delete
                                                    sortColumn(colIndex)
                                                } else {
                                                    selectedCellRow = rowIndex
                                                    selectedCellCol = colIndex
                                                    cellEditValue = cellValue
                                                    showEditDialog = true
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cellValue,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isHeader) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isHeader) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (sortColIndex == colIndex) {
                                                        Icon(
                                                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    if (rowList.size > 1) {
                                                        IconButton(
                                                            onClick = {
                                                                colToDeleteIndex = colIndex
                                                                showDeleteColDialog = true
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Delete Column",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Delete Row Action Button
                                Box(
                                    modifier = Modifier
                                        .width(50.dp)
                                        .height(44.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isHeader) {
                                        IconButton(
                                            onClick = { deleteRow(rowIndex) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Row",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun detectDelimiter(text: String): Char {
    val commaCount = text.count { it == ',' }
    val semiCount = text.count { it == ';' }
    val tabCount = text.count { it == '\t' }
    return when {
        tabCount > commaCount && tabCount > semiCount -> '\t'
        semiCount > commaCount -> ';'
        else -> ','
    }
}

fun parseCsvLine(line: String, delimiter: Char): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false

    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            c == delimiter && !inQuotes -> {
                cells.add(current.toString().trim())
                current.clear()
            }
            else -> {
                current.append(c)
            }
        }
        i++
    }
    cells.add(current.toString().trim())
    return cells
}

/**
 * Formats CSV text so columns are neatly space-aligned for monospaced viewing and editing.
 */
fun formatAlignCsvText(rawCsvText: String): String {
    if (rawCsvText.isBlank()) return rawCsvText
    val delimiter = detectDelimiter(rawCsvText)
    val lines = rawCsvText.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return rawCsvText

    val parsed = lines.map { parseCsvLine(it, delimiter) }
    val maxCols = parsed.maxOfOrNull { it.size } ?: 1
    val colWidths = IntArray(maxCols) { 0 }

    for (row in parsed) {
        for (col in 0 until maxCols) {
            val cell = row.getOrElse(col) { "" }
            if (cell.length > colWidths[col]) {
                colWidths[col] = cell.length
            }
        }
    }

    return parsed.joinToString("\n") { row ->
        val paddedRow = (0 until maxCols).map { col ->
            val cell = row.getOrElse(col) { "" }
            cell.padEnd(colWidths[col])
        }
        paddedRow.joinToString(" $delimiter ")
    }
}
