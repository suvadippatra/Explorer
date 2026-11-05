package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TodoTxtTaskItem(
    val lineIndex: Int,
    val rawLine: String,
    val isCompleted: Boolean,
    val priority: Char?,
    val completionDate: String?,
    val creationDate: String?,
    val description: String,
    val projects: List<String>,
    val contexts: List<String>,
    val dueDate: String?
)

fun parseTodoTxtTasks(rawText: String): List<TodoTxtTaskItem> {
    val priorityRegex = Regex("^\\(([A-Z])\\)\\s+(.*)$")
    val completedRegex = Regex("^x\\s+(?:(\\d{4}-\\d{2}-\\d{2})\\s+)?(?:(\\d{4}-\\d{2}-\\d{2})\\s+)?(.*)$")
    val projectRegex = Regex("(?<=^|\\s)\\+([A-Za-z0-9_-]+)")
    val contextRegex = Regex("(?<=^|\\s)@([A-Za-z0-9_-]+)")
    val dueRegex = Regex("(?<=^|\\s)due:(\\d{4}-\\d{2}-\\d{2})")

    return rawText.lines().mapIndexedNotNull { index, line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@mapIndexedNotNull null

        var isCompleted = false
        var priority: Char? = null
        var completionDate: String? = null
        var creationDate: String? = null
        var body = trimmed

        val compMatch = completedRegex.find(trimmed)
        if (compMatch != null) {
            isCompleted = true
            completionDate = compMatch.groupValues[1].ifEmpty { null }
            creationDate = compMatch.groupValues[2].ifEmpty { null }
            body = compMatch.groupValues[3]
        } else {
            val prioMatch = priorityRegex.find(trimmed)
            if (prioMatch != null) {
                priority = prioMatch.groupValues[1].firstOrNull()
                body = prioMatch.groupValues[2]
            }
            val datePrefix = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s+(.*)$").find(body)
            if (datePrefix != null) {
                creationDate = datePrefix.groupValues[1]
                body = datePrefix.groupValues[2]
            }
        }

        val projects = projectRegex.findAll(trimmed).map { it.groupValues[1] }.distinct().toList()
        val contexts = contextRegex.findAll(trimmed).map { it.groupValues[1] }.distinct().toList()
        val dueDate = dueRegex.find(trimmed)?.groupValues?.getOrNull(1)

        TodoTxtTaskItem(
            lineIndex = index,
            rawLine = line,
            isCompleted = isCompleted,
            priority = priority,
            completionDate = completionDate,
            creationDate = creationDate,
            description = body,
            projects = projects,
            contexts = contexts,
            dueDate = dueDate
        )
    }
}

/**
 * Interactive Markor Todo.txt Task List & Filter View.
 * Supports one-tap task completion toggles, priority cycling, @context / +project filtering,
 * and quick task entry while keeping the underlying Todo.txt plain text synchronized.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoTxtInteractiveView(
    rawText: String,
    onTextChange: (String) -> Unit,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val allTasks = remember(rawText) { parseTodoTxtTasks(rawText) }
    val allContexts = remember(allTasks) { allTasks.flatMap { it.contexts }.distinct().sorted() }
    val allProjects = remember(allTasks) { allTasks.flatMap { it.projects }.distinct().sorted() }

    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, DONE
    var selectedContext by remember { mutableStateOf<String?>(null) }
    var selectedProject by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var newTaskInput by remember { mutableStateOf("") }
    var newTaskPriority by remember { mutableStateOf<Char?>('A') }

    val filteredTasks = remember(allTasks, selectedStatusFilter, selectedContext, selectedProject, searchQuery) {
        allTasks.filter { task ->
            val matchesStatus = when (selectedStatusFilter) {
                "ACTIVE" -> !task.isCompleted
                "DONE" -> task.isCompleted
                else -> true
            }
            val matchesContext = selectedContext == null || task.contexts.contains(selectedContext)
            val matchesProject = selectedProject == null || task.projects.contains(selectedProject)
            val matchesSearch = searchQuery.isBlank() || task.rawLine.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesContext && matchesProject && matchesSearch
        }.sortedWith(
            compareBy<TodoTxtTaskItem> { it.isCompleted }
                .thenBy { it.priority ?: 'Z' + 1 }
                .thenBy { it.lineIndex }
        )
    }

    fun toggleTaskCompletion(task: TodoTxtTaskItem) {
        if (readOnly) return
        val mutableLines = rawText.lines().toMutableList()
        if (task.lineIndex !in mutableLines.indices) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentLine = mutableLines[task.lineIndex].trim()
        val updatedLine = if (task.isCompleted) {
            currentLine.replaceFirst(Regex("^x\\s+(?:\\d{4}-\\d{2}-\\d{2}\\s+)?"), "")
        } else {
            val withoutPrio = currentLine.replaceFirst(Regex("^\\([A-Z]\\)\\s+"), "")
            "x $today $withoutPrio"
        }
        mutableLines[task.lineIndex] = updatedLine
        onTextChange(mutableLines.joinToString("\n"))
    }

    fun cycleTaskPriority(task: TodoTxtTaskItem) {
        if (readOnly || task.isCompleted) return
        val mutableLines = rawText.lines().toMutableList()
        if (task.lineIndex !in mutableLines.indices) return
        val nextPriority: Char? = when (task.priority) {
            null -> 'A'
            'A' -> 'B'
            'B' -> 'C'
            else -> null
        }
        val currentLine = mutableLines[task.lineIndex].trim()
        val stripped = currentLine.replaceFirst(Regex("^\\([A-Z]\\)\\s+"), "")
        mutableLines[task.lineIndex] = if (nextPriority != null) "($nextPriority) $stripped" else stripped
        onTextChange(mutableLines.joinToString("\n"))
    }

    fun addNewTask() {
        val text = newTaskInput.trim()
        if (text.isEmpty() || readOnly) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val prioPrefix = if (newTaskPriority != null) "(${newTaskPriority}) " else ""
        val taskLine = "$prioPrefix$today $text"
        val updated = if (rawText.isBlank()) taskLine else "${rawText.trimEnd()}\n$taskLine"
        onTextChange(updated)
        newTaskInput = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Filter & Summary Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter tasks, @contexts, +projects...", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    val activeCount = allTasks.count { !it.isCompleted }
                    val doneCount = allTasks.count { it.isCompleted }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$activeCount open • $doneCount done",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Filter Chips Row (Status + @Contexts + +Projects)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL" to "All Tasks", "ACTIVE" to "Active", "DONE" to "Completed").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedStatusFilter == key,
                            onClick = { selectedStatusFilter = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    if (allProjects.isNotEmpty() || allContexts.isNotEmpty()) {
                        VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))
                    }

                    allProjects.forEach { proj ->
                        FilterChip(
                            selected = selectedProject == proj,
                            onClick = { selectedProject = if (selectedProject == proj) null else proj },
                            label = { Text("+$proj", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    allContexts.forEach { ctx ->
                        FilterChip(
                            selected = selectedContext == ctx,
                            onClick = { selectedContext = if (selectedContext == ctx) null else ctx },
                            label = { Text("@$ctx", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Quick Add Task Bar (if editable)
        if (!readOnly) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            newTaskPriority = when (newTaskPriority) {
                                'A' -> 'B'
                                'B' -> 'C'
                                'C' -> null
                                else -> 'A'
                            }
                        },
                        label = {
                            Text(
                                text = newTaskPriority?.let { "($it)" } ?: "(--)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )

                    OutlinedTextField(
                        value = newTaskInput,
                        onValueChange = { newTaskInput = it },
                        placeholder = { Text("Add Todo.txt task (e.g. Ship release +Explorer @work due:2026-12-01)", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    FilledTonalIconButton(
                        onClick = { addNewTask() },
                        enabled = newTaskInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching Todo.txt tasks",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredTasks,
                    key = { "${it.lineIndex}:${it.rawLine}" }
                ) { task ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        tonalElevation = if (task.isCompleted) 0.dp else 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { toggleTaskCompletion(task) },
                                enabled = !readOnly
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            if (task.priority != null && !task.isCompleted) {
                                val badgeColor = when (task.priority) {
                                    'A' -> Color(0xFFE53935)
                                    'B' -> Color(0xFFFB8C00)
                                    'C' -> Color(0xFFFDD835)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(badgeColor.copy(alpha = 0.18f))
                                        .clickable(enabled = !readOnly) { cycleTaskPriority(task) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "(${task.priority})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (task.isCompleted) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (task.projects.isNotEmpty() || task.contexts.isNotEmpty() || task.dueDate != null || task.creationDate != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        task.projects.forEach { proj ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                modifier = Modifier.clickable {
                                                    selectedProject = if (selectedProject == proj) null else proj
                                                }
                                            ) {
                                                Text(
                                                    text = "+$proj",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        task.contexts.forEach { ctx ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                                modifier = Modifier.clickable {
                                                    selectedContext = if (selectedContext == ctx) null else ctx
                                                }
                                            ) {
                                                Text(
                                                    text = "@$ctx",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        task.dueDate?.let { due ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    text = "due:$due",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        task.creationDate?.let { created ->
                                            Text(
                                                text = created,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
