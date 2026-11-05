package com.troikoss.continuum_explorer.ui.editor.text

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Format-Specific Action Bar (EditorActionBar) docked above the soft keyboard.
 * Dynamically adapts to the active document format (Markdown, Todo.txt, CSV, Structured Config, Plain text).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // Link insertion dialog
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Insert Link", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = linkTitle,
                        onValueChange = { linkTitle = it },
                        label = { Text("Link Text / Title") },
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
                }) {
                    Text("Insert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Priority dialog for Todo.txt
    if (showPriorityDialog) {
        AlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            title = { Text("Select Priority Level", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "A" to "Highest / Critical",
                        "B" to "High",
                        "C" to "Medium",
                        "D" to "Low",
                        "E" to "Lowest"
                    ).forEach { (prio, desc) ->
                        OutlinedButton(
                            onClick = {
                                onInsertText("($prio) ")
                                showPriorityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("($prio) - $desc", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPriorityDialog = false }) { Text("Cancel") }
            }
        )
    }

    // +Project Tagger Dialog
    if (showProjectDialog) {
        AlertDialog(
            onDismissRequest = { showProjectDialog = false },
            title = { Text("Add Project Tag", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    prefix = { Text("+") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val tag = projectName.trim().removePrefix("+")
                    if (tag.isNotBlank()) {
                        onInsertText(" +$tag ")
                    }
                    showProjectDialog = false
                    projectName = ""
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProjectDialog = false }) { Text("Cancel") }
            }
        )
    }

    // @Context Tagger Dialog
    if (showContextDialog) {
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            title = { Text("Add Context Tag", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = contextName,
                    onValueChange = { contextName = it },
                    label = { Text("Context Name") },
                    singleLine = true,
                    prefix = { Text("@") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val tag = contextName.trim().removePrefix("@")
                    if (tag.isNotBlank()) {
                        onInsertText(" @$tag ")
                    }
                    showContextDialog = false
                    contextName = ""
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContextDialog = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onUndo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onRedo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onCut, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onPaste, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onIndent, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FormatIndentIncrease, contentDescription = "Indent", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onUnindent, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FormatIndentDecrease, contentDescription = "Unindent", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onToggleSearch, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search / Replace", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onJumpToLineClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PinDrop, contentDescription = "Jump to line", modifier = Modifier.size(16.dp))
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

            when (formatType) {
                TextFormatType.MARKDOWN, TextFormatType.ORG, TextFormatType.ASCIIDOC -> {
                    // Header dropdown / levels
                    Box {
                        AssistChip(
                            onClick = { showHeaderMenu = true },
                            label = { Text("H▾", fontWeight = FontWeight.Bold) }
                        )
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false }
                        ) {
                            (1..6).forEach { level ->
                                DropdownMenuItem(
                                    text = { Text("H$level Heading", fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        onFormatClick("#".repeat(level) + " ", "", "Heading $level")
                                        showHeaderMenu = false
                                    }
                                )
                            }
                        }
                    }

                    AssistChip(
                        onClick = { onFormatClick("**", "**", "bold") },
                        label = { Text("B", fontWeight = FontWeight.ExtraBold) }
                    )
                    AssistChip(
                        onClick = { onFormatClick("*", "*", "italic") },
                        label = { Text("I", fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
                    )
                    AssistChip(
                        onClick = { onFormatClick("~~", "~~", "strikethrough") },
                        label = { Text("S", fontWeight = FontWeight.Bold) }
                    )
                    AssistChip(
                        onClick = { onFormatClick("- [ ] ", "", "Task") },
                        label = { Text("☑ Task") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("- ", "", "Item") },
                        label = { Text("• List") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("1. ", "", "Item") },
                        label = { Text("1. Num") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("> ", "", "Quote") },
                        label = { Text("❝ Quote") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("`", "`", "code") },
                        label = { Text("‹› Code") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("```\n", "\n```", "code block") },
                        label = { Text("``` Block") }
                    )
                    AssistChip(
                        onClick = { showLinkDialog = true },
                        label = { Text("🔗 Link") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("![", "](https://example.com/image.png)", "Image Alt") },
                        label = { Text("🖼 Image") }
                    )
                    AssistChip(
                        onClick = {
                            onInsertText("\n| Column 1 | Column 2 | Column 3 |\n| --- | --- | --- |\n| Data 1 | Data 2 | Data 3 |\n")
                        },
                        label = { Text("⊞ Table") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("$$ ", " $$", "E = mc^2") },
                        label = { Text("∑ Math") }
                    )
                    AssistChip(
                        onClick = {
                            onInsertText("\n```mermaid\ngraph TD\n    A[Start] --> B[Complete]\n```\n")
                        },
                        label = { Text("◇ Mermaid") }
                    )
                    AssistChip(
                        onClick = { onInsertText("\n---\n") },
                        label = { Text("― Rule") }
                    )
                    AssistChip(
                        onClick = {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            onInsertText(today)
                        },
                        label = { Text("📅 Date") }
                    )
                }

                TextFormatType.TODO_TXT -> {
                    if (onToggleTodoInteractive != null) {
                        Button(
                            onClick = onToggleTodoInteractive,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                if (isTodoInteractive) Icons.Default.Code else Icons.Default.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isTodoInteractive) "Raw Text" else "Task Board",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    AssistChip(
                        onClick = { showPriorityDialog = true },
                        label = { Text("(A) Priority", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFE53935)) }
                    )
                    AssistChip(
                        onClick = {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            onInsertText("x $today ")
                        },
                        label = { Text("x Complete") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF388E3C)) }
                    )
                    AssistChip(
                        onClick = { showProjectDialog = true },
                        label = { Text("+ Project") }
                    )
                    AssistChip(
                        onClick = { showContextDialog = true },
                        label = { Text("@ Context") }
                    )
                    AssistChip(
                        onClick = {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            onInsertText("$today ")
                        },
                        label = { Text("📅 Today") }
                    )
                    AssistChip(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                            val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                            onInsertText("$tomorrow ")
                        },
                        label = { Text("📅 Tomorrow") }
                    )
                    AssistChip(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                            val due = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                            onInsertText("due:$due ")
                        },
                        label = { Text("⏱ Due") }
                    )
                }

                TextFormatType.CSV -> {
                    if (onToggleCsvGrid != null) {
                        Button(
                            onClick = onToggleCsvGrid,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Visual Grid", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    AssistChip(
                        onClick = { onAddCsvColumn?.invoke() ?: onInsertText(",NewCol") },
                        label = { Text("+ Column") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    AssistChip(
                        onClick = { onAddCsvRow?.invoke() ?: onInsertText("\n") },
                        label = { Text("+ Row") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    AssistChip(
                        onClick = { onDeleteCsvColumn?.invoke() },
                        label = { Text("- Column") }
                    )
                    AssistChip(
                        onClick = { onDeleteCsvRow?.invoke() },
                        label = { Text("- Row") }
                    )
                    if (onFormatAlignTable != null) {
                        AssistChip(
                            onClick = onFormatAlignTable,
                            label = { Text("⊞ Align Table") },
                            leadingIcon = { Icon(Icons.Default.FormatAlignJustify, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                    AssistChip(
                        onClick = { onInsertText(", ") },
                        label = { Text("Comma ,") }
                    )
                    AssistChip(
                        onClick = { onInsertText("\t") },
                        label = { Text("Tab \\t") }
                    )
                }

                TextFormatType.STRUCTURED_CONFIG -> {
                    AssistChip(
                        onClick = { onInsertText("  ") },
                        label = { Text("Indent ⇥") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("\"", "\"", "string") },
                        label = { Text("\"\" Quotes") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("[", "]", "item") },
                        label = { Text("[] Brackets") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("{", "}", "key: value") },
                        label = { Text("{} Braces") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("# ", "", "comment") },
                        label = { Text("# Comment") }
                    )
                }

                TextFormatType.PLAIN_TEXT -> {
                    AssistChip(
                        onClick = { onInsertText("  ") },
                        label = { Text("Tab ⇥") }
                    )
                    AssistChip(
                        onClick = {
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            onInsertText(now)
                        },
                        label = { Text("🕒 Timestamp") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("\"", "\"", "quote") },
                        label = { Text("\"\" Quote") }
                    )
                    AssistChip(
                        onClick = { onFormatClick("(", ")", "note") },
                        label = { Text("() Paren") }
                    )
                }
            }
        }
    }
}
