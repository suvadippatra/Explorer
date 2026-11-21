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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.SlimStatusStrip
import com.troikoss.continuum_explorer.ui.components.suite.TonalSearchPill
import java.io.File

/**
 * Desktop-First Ultra-Clean Tonal Flat CSV / TSV Spreadsheet Table Studio.
 * - Strict single 48.dp Command Bar (zero stacked filter/action rows).
 * - Borderless tonal alternating rows with whisper-quiet vertical column guides.
 * - Inline bottom cell/formula bar when a cell is selected.
 * - Quiet 26.dp bottom status strip.
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
    var showCompactFilter by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

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
    var selectedCellText by remember { mutableStateOf("") }

    fun commitSelectedCell() {
        val (r, c) = selectedCell ?: return
        val newRows = csvRows.mapIndexed { rIdx, row ->
            if (rIdx == r) {
                val mutableRow = row.toMutableList()
                while (mutableRow.size <= c) mutableRow.add("")
                mutableRow[c] = selectedCellText
                mutableRow
            } else row
        }
        syncToRawText(newRows)
        selectedCell = null
    }

    fun addRow() {
        val colCount = csvRows.firstOrNull()?.size ?: 3
        val newRows = csvRows.toMutableList()
        newRows.add(List(colCount) { "" })
        syncToRawText(newRows)
    }

    fun addColumn() {
        val newRows = csvRows.map { row -> row + "" }.toMutableList()
        if (newRows.isEmpty()) newRows.add(listOf(""))
        syncToRawText(newRows)
    }

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
            icon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unsaved Changes", fontWeight = FontWeight.Bold) },
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
        val isLargeScreen = maxWidth >= 700.dp
        val isFullScreen by com.troikoss.continuum_explorer.managers.SettingsManager.isFullScreenTabBarHidden

        Column(modifier = Modifier.fillMaxSize()) {
            // Strict Single 48.dp Command Bar
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
                                onClick = { isGridView = grid },
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
                        if (isLargeScreen) {
                            TonalSearchPill(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                placeholder = "Filter",
                                modifier = Modifier.width(156.dp),
                                testTagName = "csv_filter_input"
                            )
                            Surface(
                                onClick = { addRow() },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Row", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                }
                            }
                            Surface(
                                onClick = { addColumn() },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.ViewColumn, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Col", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { showCompactFilter = !showCompactFilter },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Filter",
                                    tint = if (showCompactFilter || filterQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (isLargeScreen) {
                        FilledTonalButton(
                            onClick = {
                                commitSelectedCell()
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
                                commitSelectedCell()
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
                            if (!isLargeScreen && isGridView) {
                                DropdownMenuItem(
                                    text = { Text("Add Row") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = { showMoreMenu = false; addRow() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Column") },
                                    leadingIcon = { Icon(Icons.Default.ViewColumn, contentDescription = null) },
                                    onClick = { showMoreMenu = false; addColumn() }
                                )
                                HorizontalDivider()
                            }
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
                                onClick = { showMoreMenu = false; handleExit() }
                            )
                        }
                    }
                }
            )

            // Main Table / Raw Workspace
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
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // Tonal Flat Column Letters Header
                            item {
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .height(34.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.width(44.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "#",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    for (colIndex in 0 until maxCols) {
                                        VerticalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(148.dp)
                                                .fillMaxHeight()
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
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

                            // Borderless Tonal Alternating Rows
                            itemsIndexed(visibleRows, key = { _, pair -> pair.first }) { _, (rowIndex, row) ->
                                val isHeaderRow = rowIndex == 0
                                Row(
                                    modifier = Modifier
                                        .background(
                                            when {
                                                isHeaderRow -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                                rowIndex % 2 == 0 -> MaterialTheme.colorScheme.surface
                                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                                            }
                                        )
                                        .height(38.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.width(44.dp),
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
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                                        )
                                        val cellText = row.getOrNull(colIndex) ?: ""
                                        val isSelected = selectedCell?.first == rowIndex && selectedCell?.second == colIndex
                                        Box(
                                            modifier = Modifier
                                                .width(148.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                                )
                                                .clickable {
                                                    if (selectedCell != null && selectedCell != Pair(rowIndex, colIndex)) {
                                                        commitSelectedCell()
                                                    }
                                                    selectedCell = Pair(rowIndex, colIndex)
                                                    selectedCellText = cellText
                                                }
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = cellText.ifEmpty { "—" },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isHeaderRow || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    cellText.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
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

                    // Floating compact filter pill on narrow screens
                    if (!isLargeScreen && showCompactFilter) {
                        TonalSearchPill(
                            value = filterQuery,
                            onValueChange = { filterQuery = it },
                            placeholder = "Filter cells",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .width(220.dp),
                            testTagName = "csv_filter_input"
                        )
                    }
                } else {
                    // Clean borderless Raw CSV Editor
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
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
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

            // Inline Bottom Formula / Cell Edit Strip (appears only when a cell is tapped)
            selectedCell?.let { (r, c) ->
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${getColName(c)}${r + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                    value = selectedCellText,
                                    onValueChange = { selectedCellText = it },
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
                            onClick = { commitSelectedCell() },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
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

            // Quiet 26.dp Desktop Status Strip
            SlimStatusStrip(
                leftText = "${csvRows.size} rows × $maxCols cols • ${if (delimiter == '\t') "TSV" else "CSV"}" +
                    if (isModified) " • Modified" else " • Saved",
                rightText = selectedCell?.let { (r, c) -> "${getColName(c)}${r + 1}" } ?: "UTF-8"
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
