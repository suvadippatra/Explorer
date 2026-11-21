package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Borderless Tonal Flat Formatting Dock placed at the bottom of Markdown & Text Studio.
 * Omits Undo/Redo/Search duplicates by default since those live in the unified top command bar.
 */
@Composable
fun EditorActionBar(
    formatType: TextFormatType,
    onFormatClick: (prefix: String, suffix: String, placeholder: String) -> Unit,
    onInsertText: (text: String) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onPaste: () -> Unit = {},
    onIndent: () -> Unit = {},
    onUnindent: () -> Unit = {},
    onToggleSearch: () -> Unit = {},
    onJumpToLineClick: () -> Unit = {},
    onFormatAlignTable: (() -> Unit)? = null,
    onToggleCsvGrid: (() -> Unit)? = null,
    onToggleTodoInteractive: (() -> Unit)? = null,
    isTodoInteractive: Boolean = false,
    onAddCsvRow: (() -> Unit)? = null,
    onAddCsvColumn: (() -> Unit)? = null,
    onDeleteCsvRow: (() -> Unit)? = null,
    onDeleteCsvColumn: (() -> Unit)? = null,
    showTopBarDuplicates: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkTitle by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("https://") }

    var showProjectDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }

    var showContextDialog by remember { mutableStateOf(false) }
    var contextName by remember { mutableStateOf("") }

    var showHeaderMenu by remember { mutableStateOf(false) }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Insert Link", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = linkTitle,
                        onValueChange = { linkTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val title = linkTitle.ifBlank { "Link" }
                    val url = linkUrl.ifBlank { "https://" }
                    onInsertText("[$title]($url)")
                    showLinkDialog = false
                    linkTitle = ""
                    linkUrl = "https://"
                }) { Text("Insert") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPriorityDialog) {
        AlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            title = { Text("Priority", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A", "B", "C", "D", "E").forEach { prio ->
                        FilledTonalButton(
                            onClick = {
                                onInsertText("($prio) ")
                                showPriorityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("($prio)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPriorityDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showProjectDialog) {
        AlertDialog(
            onDismissRequest = { showProjectDialog = false },
            title = { Text("Project Tag", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project") },
                    singleLine = true,
                    prefix = { Text("+") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val tag = projectName.trim().removePrefix("+")
                    if (tag.isNotBlank()) onInsertText(" +$tag ")
                    showProjectDialog = false
                    projectName = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showProjectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showContextDialog) {
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            title = { Text("Context Tag", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = contextName,
                    onValueChange = { contextName = it },
                    label = { Text("Context") },
                    singleLine = true,
                    prefix = { Text("@") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val tag = contextName.trim().removePrefix("@")
                    if (tag.isNotBlank()) onInsertText(" @$tag ")
                    showContextDialog = false
                    contextName = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showContextDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (showTopBarDuplicates) {
                    IconButton(onClick = onUndo, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onRedo, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onToggleSearch, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "Find", modifier = Modifier.size(16.dp))
                    }
                }

                IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onCut, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onPaste, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onIndent, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.FormatIndentIncrease, contentDescription = "Indent", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onUnindent, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.FormatIndentDecrease, contentDescription = "Unindent", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onJumpToLineClick, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.PinDrop, contentDescription = "Jump to Line", modifier = Modifier.size(15.dp))
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(18.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                when (formatType) {
                    TextFormatType.MARKDOWN, TextFormatType.ORG, TextFormatType.ASCIIDOC -> {
                        Box {
                            TonalFormatPill(label = "H▾", bold = true, onClick = { showHeaderMenu = true })
                            DropdownMenu(
                                expanded = showHeaderMenu,
                                onDismissRequest = { showHeaderMenu = false }
                            ) {
                                (1..6).forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text("H$level", fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            onFormatClick("#".repeat(level) + " ", "", "Heading $level")
                                            showHeaderMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        TonalFormatPill("B", bold = true) { onFormatClick("**", "**", "bold") }
                        TonalFormatPill("I", italic = true) { onFormatClick("*", "*", "italic") }
                        TonalFormatPill("S") { onFormatClick("~~", "~~", "strike") }
                        TonalFormatPill("Task") { onFormatClick("- [ ] ", "", "Task") }
                        TonalFormatPill("List") { onFormatClick("- ", "", "Item") }
                        TonalFormatPill("1. List") { onFormatClick("1. ", "", "Item") }
                        TonalFormatPill("Quote") { onFormatClick("> ", "", "Quote") }
                        TonalFormatPill("Code") { onFormatClick("`", "`", "code") }
                        TonalFormatPill("Block") { onFormatClick("```\n", "\n```", "code") }
                        TonalFormatPill("Link") { showLinkDialog = true }
                        TonalFormatPill("Image") { onFormatClick("![", "](https://example.com/image.png)", "Alt") }
                        TonalFormatPill("Table") {
                            onInsertText("\n| Col 1 | Col 2 | Col 3 |\n| --- | --- | --- |\n| A | B | C |\n")
                        }
                        TonalFormatPill("Math") { onFormatClick("$$ ", " $$", "E = mc^2") }
                        TonalFormatPill("Mermaid") {
                            onInsertText("\n```mermaid\ngraph TD\n    A[Start] --> B[End]\n```\n")
                        }
                        TonalFormatPill("Rule") { onInsertText("\n---\n") }
                        TonalFormatPill("Date") {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            onInsertText(today)
                        }
                    }

                    TextFormatType.TODO_TXT -> {
                        if (onToggleTodoInteractive != null) {
                            TonalFormatPill(
                                label = if (isTodoInteractive) "Raw" else "Board",
                                highlighted = true,
                                onClick = onToggleTodoInteractive
                            )
                        }
                        TonalFormatPill("(A) Priority", bold = true) { showPriorityDialog = true }
                        TonalFormatPill("Done") {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            onInsertText("x $today ")
                        }
                        TonalFormatPill("+Project") { showProjectDialog = true }
                        TonalFormatPill("@Context") { showContextDialog = true }
                        TonalFormatPill("Today") {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            onInsertText("$today ")
                        }
                        TonalFormatPill("Due +7d") {
                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                            val due = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                            onInsertText("due:$due ")
                        }
                    }

                    TextFormatType.CSV -> {
                        if (onToggleCsvGrid != null) {
                            TonalFormatPill("Grid", highlighted = true, onClick = onToggleCsvGrid)
                        }
                        TonalFormatPill("+ Col") { onAddCsvColumn?.invoke() ?: onInsertText(",Col") }
                        TonalFormatPill("+ Row") { onAddCsvRow?.invoke() ?: onInsertText("\n") }
                        TonalFormatPill("- Col") { onDeleteCsvColumn?.invoke() }
                        TonalFormatPill("- Row") { onDeleteCsvRow?.invoke() }
                        if (onFormatAlignTable != null) {
                            TonalFormatPill("Align", onClick = onFormatAlignTable)
                        }
                        TonalFormatPill("Comma") { onInsertText(", ") }
                        TonalFormatPill("Tab") { onInsertText("\t") }
                    }

                    TextFormatType.STRUCTURED_CONFIG -> {
                        TonalFormatPill("Tab") { onInsertText("  ") }
                        TonalFormatPill("\"\"") { onFormatClick("\"", "\"", "value") }
                        TonalFormatPill("[]") { onFormatClick("[", "]", "item") }
                        TonalFormatPill("{}") { onFormatClick("{", "}", "key: value") }
                        TonalFormatPill("#") { onFormatClick("# ", "", "comment") }
                    }

                    TextFormatType.PLAIN_TEXT -> {
                        TonalFormatPill("Tab") { onInsertText("  ") }
                        TonalFormatPill("Timestamp") {
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            onInsertText(now)
                        }
                        TonalFormatPill("\"\"") { onFormatClick("\"", "\"", "quote") }
                        TonalFormatPill("()") { onFormatClick("(", ")", "note") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TonalFormatPill(
    label: String,
    bold: Boolean = false,
    italic: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (bold || highlighted) FontWeight.Bold else FontWeight.Medium,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
                ),
                color = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
