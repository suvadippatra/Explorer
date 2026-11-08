package com.troikoss.continuum_explorer.ui.editor

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Material 3 CSV / Spreadsheet Table Editor & Raw CSV View.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvEditorView(
    file: File,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var rawText by remember(file.absolutePath, content) { mutableStateOf(content) }
    var isGridView by remember { mutableStateOf(true) }

    // Parse CSV rows
    val delimiter = remember(rawText) {
        if (rawText.contains('\t')) '\t' else if (rawText.contains(';')) ';' else ','
    }

    var csvRows by remember(rawText) {
        mutableStateOf(parseCsv(rawText, delimiter))
    }

    // Sync csvRows back to rawText
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

    // Active editing cell state
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (rowIndex, colIndex)
    var editingCellText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Unified 50.dp Toolbar with Triple-Line Menu Button on Left
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().height(50.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Toggle Navigation Sidebar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = "CSV & Table Studio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = isGridView,
                        onClick = { isGridView = true },
                        label = { Text("Grid View") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = !isGridView,
                        onClick = { isGridView = false },
                        label = { Text("Raw CSV") },
                        shape = RoundedCornerShape(8.dp)
                    )

                    IconButton(onClick = { onSave() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Table Action Bar (Add Row / Column)
        if (isGridView) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val colCount = csvRows.firstOrNull()?.size ?: 3
                            val newRows = csvRows.toMutableList()
                            newRows.add(List(colCount) { "" })
                            syncToRawText(newRows)
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Row", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Row", style = MaterialTheme.typography.labelSmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val newRows = csvRows.map { row -> row + "" }.toMutableList()
                            if (newRows.isEmpty()) newRows.add(listOf(""))
                            syncToRawText(newRows)
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Column", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Column", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Body
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isGridView) {
                val maxCols = remember(csvRows) { (csvRows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        // Table Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier.width(40.dp).padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("#", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                                for (colIndex in 0 until maxCols) {
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
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
                            }
                        }

                        // Data Rows
                        itemsIndexed(csvRows) { rowIndex, row ->
                            Row(
                                modifier = Modifier
                                    .background(if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Row Index Header
                                Box(
                                    modifier = Modifier.width(40.dp).padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${rowIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                for (colIndex in 0 until maxCols) {
                                    val cellText = row.getOrNull(colIndex) ?: ""
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(40.dp)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .clickable {
                                                editingCell = Pair(rowIndex, colIndex)
                                                editingCellText = cellText
                                            }
                                            .padding(6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cellText.ifEmpty { "..." },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (cellText.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Raw Text CSV Editor
                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        onContentChange(it)
                        csvRows = parseCsv(it, delimiter)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
        }
    }

    // Cell Edit Dialog
    editingCell?.let { (r, c) ->
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text("Edit Cell [${getColName(c)}${r + 1}]", style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = editingCellText,
                    onValueChange = { editingCellText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
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
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { editingCell = null }) { Text("Cancel") }
            }
        )
    }
}

private fun parseCsv(text: String, delimiter: Char): List<List<String>> {
    if (text.isBlank()) return listOf(listOf(""))
    val lines = text.split("\n")
    return lines.map { line ->
        line.split(delimiter).map { it.trim().removeSurrounding("\"") }
    }
}

private fun getColName(index: Int): String {
    val first = (index / 26)
    val second = (index % 26)
    return if (first > 0) "${(64 + first).toChar()}${(65 + second).toChar()}" else "${(65 + second).toChar()}"
}
