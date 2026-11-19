package com.troikoss.continuum_explorer.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.SlimStatusStrip
import com.troikoss.continuum_explorer.ui.components.suite.TonalSearchPill
import java.io.File

/**
 * Desktop-First Ultra-Clean Tonal Flat CSV / TSV Table Studio.
 * - Strict single 48.dp Command Bar (Grid/Raw segmented switch, + Row, + Col, inline filter, Save).
 * - Borderless tonal alternating rows with subtle 0.5.dp column hairlines.
 * - Inline bottom cell-formula editor bar when a cell is selected (no blocking modal popup).
 * - Quiet 26.dp SlimStatusStrip at the bottom.
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
    var showMobileSearch by remember { mutableStateOf(false) }

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

    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingCellText by remember { mutableStateOf("") }

    fun commitCellEdit() {
        val (r, c) = selectedCell ?: return
        val newRows = csvRows.mapIndexed { rIdx, row ->
            if (rIdx == r) {
                val mutableRow = row.toMutableList()
                while (mutableRow.size <= c) mutableRow.add("")
                mutableRow[c] = editingCellText
                mutableRow
            } else row
        }
        syncToRawText(newRows)
        selectedCell = null
    }

    val handleExit = {
        if (isModified) {
            showUnsavedDialog = true
        } else {
            (onBackClick ?: onMenuClick).invoke()
        }
    }

    BackHandler {
        if (selectedCell != null) {
            selectedCell = null
        } else {
            handleExit()
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            icon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unsaved Table Changes", fontWeight = FontWeight.Bold) },
            text = { Text("Save your changes to \"${file.name}\" before leaving Table Studio?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSave()
                        lastSavedText = rawText
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

    val maxCols = remember(csvRows) { (csvRows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isWide = maxWidth >= 700.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Strict Single 48.dp Command Bar (zero stacked action bar underneath)
            SingleRowCommandBar(
                navigationContent = {
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
                        listOf(true to "Grid", false to "Raw").forEachIndexed { idx, (grid, label) ->
                            SegmentedButton(
                                selected = isGridView == grid,
                                onClick = {
                                    isGridView = grid
                                    if (!grid) selectedCell = null
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
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
                                    fontWeight = if (isGridView == grid) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                actionsContent = {
                    if (isGridView) {
                        if (isWide) {
                            FilledTonalButton(
                                onClick = {
                                    val colCount = csvRows.firstOrNull()?.size ?: 3
                                    syncToRawText(csvRows + listOf(List(colCount) { "" }))
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Row", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val newRows = csvRows.map { row -> row + "" }.toMutableList()
                                    if (newRows.isEmpty()) newRows.add(listOf(""))
                                    syncToRawText(newRows)
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.ViewColumn, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Col", style = MaterialTheme.typography.labelSmall)
                            }
                            TonalSearchPill(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                placeholder = "Filter cells",
                                modifier = Modifier.width(160.dp),
                                testTagName = "csv_filter_input"
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    val colCount = csvRows.firstOrNull()?.size ?: 3
                                    syncToRawText(csvRows + listOf(List(colCount) { "" }))
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Row", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val newRows = csvRows.map { row -> row + "" }.toMutableList()
                                    if (newRows.isEmpty()) newRows.add(listOf(""))
                                    syncToRawText(newRows)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.ViewColumn, contentDescription = "Add Column", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { showMobileSearch = !showMobileSearch },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Filter",
                                    tint = if (showMobileSearch || filterQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (isWide) {
                        FilledTonalButton(
                            onClick = {
                                onSave()
                                lastSavedText = rawText
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("csv_editor_save_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
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
            )

            // Main Table or Raw View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isGridView) {
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
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            // Column Letters Header Row (pure tonal fill, subtle column hairlines)
                            item {
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.width(46.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    for (colIndex in 0 until maxCols) {
                                        VerticalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(148.dp)
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = getColName(colIndex),
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.width(40.dp))
                                }
                            }

                            // Data Rows (Ultra-Clean Alternating Tonal Flat)
                            itemsIndexed(visibleRows, key = { _, pair -> pair.first }) { _, (rowIndex, row) ->
                                val isHeaderRow = rowIndex == 0
                                val rowBg = when {
                                    isHeaderRow -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                    rowIndex % 2 == 0 -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                }
                                Row(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .background(rowBg),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.width(46.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${rowIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    for (colIndex in 0 until maxCols) {
                                        VerticalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                        val cellText = row.getOrNull(colIndex) ?: ""
                                        val isCellSelected = selectedCell == Pair(rowIndex, colIndex)
                                        Box(
                                            modifier = Modifier
                                                .width(148.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    if (isCellSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                    else androidx.compose.ui.graphics.Color.Transparent
                                                )
                                                .clickable {
                                                    selectedCell = Pair(rowIndex, colIndex)
                                                    editingCellText = cellText
                                                }
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = cellText.ifEmpty { "—" },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isHeaderRow || isCellSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (cellText.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                                else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (csvRows.size > 1) {
                                                val updated = csvRows.toMutableList().apply { removeAt(rowIndex) }
                                                if (selectedCell?.first == rowIndex) selectedCell = null
                                                syncToRawText(updated)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Row",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Mobile floating filter pill (never stacks a second header bar)
                    if (!isWide && showMobileSearch) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .width(220.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TonalSearchPill(
                                    value = filterQuery,
                                    onValueChange = { filterQuery = it },
                                    placeholder = "Filter cells",
                                    modifier = Modifier.weight(1f),
                                    testTagName = "csv_filter_input"
                                )
                                IconButton(onClick = { showMobileSearch = false }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Filter", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Clean Borderless Raw CSV Source Editor
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .padding(16.dp)
                    ) {
                        BasicTextField(
                            value = rawText,
                            onValueChange = {
                                rawText = it
                                onContentChange(it)
                                csvRows = parseCsvQuotesAware(it, delimiter)
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("csv_raw_text_field")
                        )
                    }
                }
            }

            // Inline Bottom Cell-Formula Edit Bar (replaces blocking modal dialog)
            selectedCell?.let { (r, c) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${getColName(c)}${r + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp)
                                ) {
                                    BasicTextField(
                                        value = editingCellText,
                                        onValueChange = { editingCellText = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("csv_cell_edit_input")
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { commitCellEdit() },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apply", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(
                                onClick = { selectedCell = null },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Quiet 26.dp Desktop Status Strip
            SlimStatusStrip(
                leftText = "${csvRows.size} rows × $maxCols cols" + if (filterQuery.isNotEmpty()) " • Filtered" else "",
                rightText = "${if (delimiter == '\t') "TSV" else if (delimiter == ';') "CSV (;)" else "CSV"} • ${if (isModified) "Modified" else "Saved"}"
            )
        }
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
