package com.troikoss.continuum_explorer.ui.components

import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.toUniversal
import java.io.File

private enum class DialogSortBy { NAME, DATE, SIZE }

/**
 * Redesigned Universal File & Folder Picker Dialog.
 * Features:
 * 1. List / Grid View Toggle with live image/video/document thumbnails (`IconHelper.FileThumbnail`).
 * 2. Sort Controls (Name, Date, Size, Ascending/Descending).
 * 3. Clickable Breadcrumb Path Bar + Quick Storage Bookmark Chips (Internal, Downloads, Documents, Pictures, DCIM).
 * 4. Optional `allowedExtensions` filter and Multi-File selection mode (`onMultipleSelected`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppFilePickerDialog(
    title: String = "Select File or Folder",
    selectFolders: Boolean = false,
    initialDirectory: File? = null,
    allowedExtensions: Set<String> = emptySet(),
    allowMultiSelect: Boolean = false,
    onDismiss: () -> Unit,
    onSelected: (File) -> Unit,
    onMultipleSelected: ((List<File>) -> Unit)? = null
) {
    val context = LocalContext.current
    val rootStorage = Environment.getExternalStorageDirectory()
    var currentDir by remember {
        mutableStateOf(
            initialDirectory?.takeIf { it.exists() && it.isDirectory } ?: rootStorage
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(DialogSortBy.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var filterByExtension by remember { mutableStateOf(allowedExtensions.isNotEmpty()) }

    val selectedMultiFiles = remember { mutableStateListOf<File>() }

    val quickFolders = remember {
        listOfNotNull(
            "Internal" to rootStorage,
            "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Pictures" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "DCIM" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        ).filter { it.second.exists() }
    }

    val fileList = remember(currentDir, searchQuery, sortBy, sortAscending, filterByExtension, allowedExtensions) {
        val raw = try {
            currentDir.listFiles()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val filtered = raw.filter { file ->
            if (file.name.startsWith(".")) return@filter false
            if (searchQuery.isNotBlank() && !file.name.contains(searchQuery.trim(), ignoreCase = true)) {
                return@filter false
            }
            if (!file.isDirectory && filterByExtension && allowedExtensions.isNotEmpty()) {
                val ext = file.extension.lowercase()
                ext in allowedExtensions || (allowedExtensions.contains("txt") && file.name.endsWith("todo.txt", true))
            } else {
                true
            }
        }
        when (sortBy) {
            DialogSortBy.NAME -> filtered.sortedWith(
                if (sortAscending) compareBy({ !it.isDirectory }, { it.name.lowercase() })
                else compareBy<File>({ !it.isDirectory }).thenByDescending { it.name.lowercase() }
            )
            DialogSortBy.DATE -> filtered.sortedWith(
                if (sortAscending) compareBy({ !it.isDirectory }, { it.lastModified() })
                else compareBy<File>({ !it.isDirectory }).thenByDescending { it.lastModified() }
            )
            DialogSortBy.SIZE -> filtered.sortedWith(
                if (sortAscending) compareBy({ !it.isDirectory }, { it.length() })
                else compareBy<File>({ !it.isDirectory }).thenByDescending { it.length() }
            )
        }
    }

    val breadcrumbs = remember(currentDir) {
        val segments = mutableListOf<Pair<String, File>>()
        var ptr: File? = currentDir
        while (ptr != null && ptr.absolutePath.startsWith(rootStorage.absolutePath)) {
            val label = if (ptr.absolutePath == rootStorage.absolutePath) "Internal" else ptr.name
            segments.add(0, label to ptr)
            if (ptr.absolutePath == rootStorage.absolutePath) break
            ptr = ptr.parentFile
        }
        if (segments.isEmpty()) segments.add(currentDir.name.ifBlank { "/" } to currentDir)
        segments
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 760.dp)
                .fillMaxHeight(0.86f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (currentDir.parentFile != null && currentDir != rootStorage) {
                            IconButton(
                                onClick = { currentDir.parentFile?.let { currentDir = it } },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${fileList.count { it.isDirectory }} folders • ${fileList.count { !it.isDirectory }} files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // List / Grid Toggle
                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "List View" else "Grid View",
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Sort Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(19.dp))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort by Name (${if (sortBy == DialogSortBy.NAME && !sortAscending) "Z-A" else "A-Z"})") },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) },
                                    onClick = {
                                        if (sortBy == DialogSortBy.NAME) sortAscending = !sortAscending
                                        else { sortBy = DialogSortBy.NAME; sortAscending = true }
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Date (${if (sortBy == DialogSortBy.DATE && sortAscending) "Oldest" else "Newest"})") },
                                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                    onClick = {
                                        if (sortBy == DialogSortBy.DATE) sortAscending = !sortAscending
                                        else { sortBy = DialogSortBy.DATE; sortAscending = false }
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Size (${if (sortBy == DialogSortBy.SIZE && sortAscending) "Smallest" else "Largest"})") },
                                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                    onClick = {
                                        if (sortBy == DialogSortBy.SIZE) sortAscending = !sortAscending
                                        else { sortBy = DialogSortBy.SIZE; sortAscending = false }
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(19.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Storage Bookmark Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickFolders.forEach { (label, folder) ->
                        val isActive = currentDir.absolutePath == folder.absolutePath
                        FilterChip(
                            selected = isActive,
                            onClick = {
                                currentDir = folder
                                searchQuery = ""
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (label) {
                                        "Downloads" -> Icons.Default.Download
                                        "Documents" -> Icons.Default.Description
                                        "Pictures", "DCIM" -> Icons.Default.Image
                                        else -> Icons.Default.PhoneAndroid
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    if (allowedExtensions.isNotEmpty()) {
                        FilterChip(
                            selected = filterByExtension,
                            onClick = { filterByExtension = !filterByExtension },
                            label = {
                                Text(
                                    if (filterByExtension) "Supported Only (${allowedExtensions.take(4).joinToString(",")})"
                                    else "All Files",
                                    fontSize = 11.sp
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Clickable Breadcrumb Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        breadcrumbs.forEachIndexed { idx, (name, dir) ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (idx == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Medium,
                                color = if (idx == breadcrumbs.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { currentDir = dir }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            if (idx < breadcrumbs.lastIndex) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Filter Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search in ${currentDir.name.ifBlank { "folder" }}...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(15.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Directory Contents (List or Grid)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWideDialog = maxWidth >= 520.dp
                    if (fileList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "No matching files or folders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (isGridView) {
                        val cols = if (isWideDialog) 4 else 2
                        val rows = fileList.chunked(cols)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rows) { rowFiles ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowFiles.forEach { file ->
                                        val isDir = file.isDirectory
                                        val uFile = remember(file.absolutePath) { file.toUniversal() }
                                        val isMultiSelected = selectedMultiFiles.any { it.absolutePath == file.absolutePath }
                                        OutlinedCard(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.outlinedCardColors(
                                                containerColor = if (isMultiSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                }
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    if (isDir) {
                                                        currentDir = file
                                                        searchQuery = ""
                                                    } else if (!selectFolders) {
                                                        if (allowMultiSelect && onMultipleSelected != null) {
                                                            if (isMultiSelected) {
                                                                selectedMultiFiles.removeAll { it.absolutePath == file.absolutePath }
                                                            } else {
                                                                selectedMultiFiles.add(file)
                                                            }
                                                        } else {
                                                            onSelected(file)
                                                        }
                                                    }
                                                }
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(54.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    IconHelper.FileThumbnail(
                                                        file = uFile,
                                                        iconSize = 46.dp,
                                                        modifier = Modifier.size(46.dp)
                                                    )
                                                }
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isDir) FontWeight.Bold else FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isDir) "Folder" else Formatter.formatFileSize(context, file.length()),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    repeat(cols - rowFiles.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(fileList, key = { it.absolutePath }) { file ->
                                val isDir = file.isDirectory
                                val uFile = remember(file.absolutePath) { file.toUniversal() }
                                val isMultiSelected = selectedMultiFiles.any { it.absolutePath == file.absolutePath }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isMultiSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isDir) {
                                                currentDir = file
                                                searchQuery = ""
                                            } else if (!selectFolders) {
                                                if (allowMultiSelect && onMultipleSelected != null) {
                                                    if (isMultiSelected) {
                                                        selectedMultiFiles.removeAll { it.absolutePath == file.absolutePath }
                                                    } else {
                                                        selectedMultiFiles.add(file)
                                                    }
                                                } else {
                                                    onSelected(file)
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            IconHelper.FileThumbnail(
                                                file = uFile,
                                                iconSize = 30.dp,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val dateSpan = remember(file.lastModified()) {
                                                DateUtils.getRelativeTimeSpanString(
                                                    file.lastModified(),
                                                    System.currentTimeMillis(),
                                                    DateUtils.MINUTE_IN_MILLIS
                                                ).toString()
                                            }
                                            Text(
                                                text = if (isDir) "Folder • $dateSpan"
                                                else "${Formatter.formatFileSize(context, file.length())} • $dateSpan",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (isDir) {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else if (!selectFolders) {
                                            if (allowMultiSelect && onMultipleSelected != null) {
                                                Checkbox(
                                                    checked = isMultiSelected,
                                                    onCheckedChange = { checked ->
                                                        if (checked) selectedMultiFiles.add(file)
                                                        else selectedMultiFiles.removeAll { it.absolutePath == file.absolutePath }
                                                    }
                                                )
                                            } else {
                                                FilledTonalButton(
                                                    onClick = { onSelected(file) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Select", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (allowMultiSelect && !selectFolders) {
                        TextButton(
                            onClick = {
                                val allFiles = fileList.filter { !it.isDirectory }
                                if (selectedMultiFiles.size == allFiles.size) {
                                    selectedMultiFiles.clear()
                                } else {
                                    selectedMultiFiles.clear()
                                    selectedMultiFiles.addAll(allFiles)
                                }
                            }
                        ) {
                            Text(
                                if (selectedMultiFiles.isEmpty()) "Select All in Folder"
                                else "Selected (${selectedMultiFiles.size})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }

                        if (selectFolders) {
                            Button(
                                onClick = { onSelected(currentDir) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select \"${currentDir.name.ifBlank { "Root" }}\"", style = MaterialTheme.typography.labelMedium)
                            }
                        } else if (allowMultiSelect && onMultipleSelected != null) {
                            Button(
                                onClick = { onMultipleSelected(selectedMultiFiles.toList()) },
                                enabled = selectedMultiFiles.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add ${selectedMultiFiles.size} File(s)", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
