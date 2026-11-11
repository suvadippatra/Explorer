package com.troikoss.continuum_explorer.ui.components

import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
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

private enum class DialogSortBy { NAME, DATE, SIZE, TYPE }

/**
 * Adaptive Desktop & Mobile File/Folder Picker Dialog.
 * - On Android Desktop / DeX / Tablet (`maxWidth >= 640.dp`): Renders a desktop-grade split-pane
 *   Explorer window with a Left Places Sidebar, Unified Top Breadcrumb + Compact Search Toolbar,
 *   Multi-Column Desktop Tiles/Grid (no stretched full-width single-column bars!), and Bottom Status Bar.
 * - On Mobile (`maxWidth < 640.dp`): Renders a compact mobile layout with quick chips and 2-column grid / list toggle.
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
    var highlightedFile by remember { mutableStateOf<File?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // Default to true (Grid / Multi-Column Tiles) on Desktop and Mobile so it never shows stretched single-column bars
    var isGridView by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(DialogSortBy.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var filterByExtension by remember { mutableStateOf(allowedExtensions.isNotEmpty()) }

    val selectedMultiFiles = remember { mutableStateListOf<File>() }

    val quickFolders = remember {
        val suiteDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumSuite")
        listOfNotNull(
            Triple("Internal Storage", Icons.Default.PhoneAndroid, rootStorage),
            Triple("Downloads", Icons.Default.Download, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)),
            Triple("Documents", Icons.Default.Description, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)),
            Triple("My Suite", Icons.Default.AutoAwesomeMosaic, suiteDir),
            Triple("Pictures", Icons.Default.Image, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)),
            Triple("DCIM", Icons.Default.PhotoCamera, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)),
            Triple("Movies", Icons.Default.Movie, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)),
            Triple("Music", Icons.Default.MusicNote, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
        ).filter { it.third.exists() }
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
            DialogSortBy.TYPE -> filtered.sortedWith(
                if (sortAscending) compareBy({ !it.isDirectory }, { it.extension.lowercase() }, { it.name.lowercase() })
                else compareBy<File>({ !it.isDirectory }).thenByDescending { it.extension.lowercase() }.thenBy { it.name.lowercase() }
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

    fun handleItemClick(file: File) {
        if (file.isDirectory) {
            currentDir = file
            highlightedFile = null
            searchQuery = ""
        } else if (!selectFolders) {
            if (allowMultiSelect && onMultipleSelected != null) {
                val exists = selectedMultiFiles.any { it.absolutePath == file.absolutePath }
                if (exists) {
                    selectedMultiFiles.removeAll { it.absolutePath == file.absolutePath }
                } else {
                    selectedMultiFiles.add(file)
                }
            } else {
                highlightedFile = file
                onSelected(file)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isDesktopLayout = maxWidth >= 660.dp

            Surface(
                shape = RoundedCornerShape(if (isDesktopLayout) 16.dp else 18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth(if (isDesktopLayout) 0.90f else 0.95f)
                    .widthIn(max = 980.dp)
                    .fillMaxHeight(if (isDesktopLayout) 0.84f else 0.88f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // =========================================================================
                    // STICKY DESKTOP / MOBILE WINDOW HEADER & UNIFIED TOOLBAR
                    // =========================================================================
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                    IconButton(
                                        onClick = {
                                            val parent = currentDir.parentFile
                                            if (parent != null && currentDir != rootStorage) {
                                                currentDir = parent
                                            }
                                        },
                                        enabled = currentDir.parentFile != null && currentDir != rootStorage,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", modifier = Modifier.size(18.dp))
                                    }

                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // On Desktop: Inline Compact Search Box right in the header bar!
                                if (isDesktopLayout) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .width(230.dp)
                                            .height(34.dp)
                                            .padding(horizontal = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.weight(1f),
                                                decorationBox = { inner ->
                                                    if (searchQuery.isEmpty()) {
                                                        Text(
                                                            "Filter ${currentDir.name.ifBlank { "folder" }}...",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    inner()
                                                }
                                            )
                                            if (searchQuery.isNotEmpty()) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { searchQuery = "" },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    // View Mode Toggle (Compact Tiles vs Icon Grid)
                                    IconButton(
                                        onClick = { isGridView = !isGridView },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isGridView) Icons.Default.ViewModule else Icons.Default.GridView,
                                            contentDescription = if (isGridView) "Compact Tiles" else "Icon Grid",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Sort Menu
                                    Box {
                                        IconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Name (${if (sortBy == DialogSortBy.NAME && !sortAscending) "Z-A" else "A-Z"})") },
                                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) },
                                                onClick = {
                                                    if (sortBy == DialogSortBy.NAME) sortAscending = !sortAscending
                                                    else { sortBy = DialogSortBy.NAME; sortAscending = true }
                                                    showSortMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Date (${if (sortBy == DialogSortBy.DATE && sortAscending) "Oldest" else "Newest"})") },
                                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                                onClick = {
                                                    if (sortBy == DialogSortBy.DATE) sortAscending = !sortAscending
                                                    else { sortBy = DialogSortBy.DATE; sortAscending = false }
                                                    showSortMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Size (${if (sortBy == DialogSortBy.SIZE && sortAscending) "Smallest" else "Largest"})") },
                                                leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                                onClick = {
                                                    if (sortBy == DialogSortBy.SIZE) sortAscending = !sortAscending
                                                    else { sortBy = DialogSortBy.SIZE; sortAscending = false }
                                                    showSortMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Type (${if (sortBy == DialogSortBy.TYPE && !sortAscending) "Z-A" else "A-Z"})") },
                                                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                                                onClick = {
                                                    if (sortBy == DialogSortBy.TYPE) sortAscending = !sortAscending
                                                    else { sortBy = DialogSortBy.TYPE; sortAscending = true }
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }

                                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Clickable Breadcrumb Bar + Extension Filter Chip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
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

                                if (allowedExtensions.isNotEmpty()) {
                                    FilterChip(
                                        selected = filterByExtension,
                                        onClick = { filterByExtension = !filterByExtension },
                                        label = {
                                            Text(
                                                if (filterByExtension) ".${allowedExtensions.take(3).joinToString(", .")}"
                                                else "All Files",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            // Mobile-only search & quick chips row
                            if (!isDesktopLayout) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    quickFolders.forEach { (label, icon, folder) ->
                                        val isActive = currentDir.absolutePath == folder.absolutePath
                                        FilterChip(
                                            selected = isActive,
                                            onClick = {
                                                currentDir = folder
                                                searchQuery = ""
                                            },
                                            label = { Text(label.substringBefore(" "), fontSize = 11.sp) },
                                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp)) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(6.dp))
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                if (searchQuery.isEmpty()) {
                                                    Text("Search in ${currentDir.name.ifBlank { "folder" }}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                inner()
                                            }
                                        )
                                        if (searchQuery.isNotEmpty()) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear",
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable { searchQuery = "" }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    // =========================================================================
                    // MAIN BODY: DESKTOP SPLIT-PANE (SIDEBAR + MULTI-COLUMN GRID) OR MOBILE GRID
                    // =========================================================================
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Left Places Sidebar on Android Desktop / DeX / Wide Tablet
                        if (isDesktopLayout) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .width(185.dp)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "PLACES",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        items(quickFolders) { (label, icon, folder) ->
                                            val isSelected = currentDir.absolutePath == folder.absolutePath
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        currentDir = folder
                                                        searchQuery = ""
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        // Right File/Folder Multi-Column Grid Pane (Zero 1000px full-width stretched bars!)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(10.dp)
                        ) {
                            if (fileList.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            Icons.Default.FolderOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Text(
                                            text = "No matching files or folders",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else if (isGridView) {
                                // Icon Grid Mode (Adaptive 130dp cells -> 4 to 6 columns on Desktop, 2-3 on Mobile)
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 128.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(fileList, key = { it.absolutePath }) { file ->
                                        val isDir = file.isDirectory
                                        val uFile = remember(file.absolutePath) { file.toUniversal() }
                                        val isSelected = selectedMultiFiles.any { it.absolutePath == file.absolutePath } ||
                                            highlightedFile?.absolutePath == file.absolutePath
                                        OutlinedCard(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.outlinedCardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { handleItemClick(file) }
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
                                                        .size(50.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    IconHelper.FileThumbnail(
                                                        file = uFile,
                                                        iconSize = 42.dp,
                                                        modifier = Modifier.size(42.dp)
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
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Multi-Column Desktop Compact Tiles (3 columns on Desktop, 2 on Tablet, 1 on Mobile)
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = if (isDesktopLayout) 220.dp else 280.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(fileList, key = { it.absolutePath }) { file ->
                                        val isDir = file.isDirectory
                                        val uFile = remember(file.absolutePath) { file.toUniversal() }
                                        val isMultiSelected = selectedMultiFiles.any { it.absolutePath == file.absolutePath }
                                        val isHighlighted = highlightedFile?.absolutePath == file.absolutePath
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isMultiSelected || isHighlighted) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isMultiSelected || isHighlighted) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { handleItemClick(file) }
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
                                                        iconSize = 28.dp,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = file.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Medium,
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
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                if (isDir) {
                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else if (!selectFolders && allowMultiSelect && onMultipleSelected != null) {
                                                    Checkbox(
                                                        checked = isMultiSelected,
                                                        onCheckedChange = { checked ->
                                                            if (checked) selectedMultiFiles.add(file)
                                                            else selectedMultiFiles.removeAll { it.absolutePath == file.absolutePath }
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    // =========================================================================
                    // BOTTOM DESKTOP STATUS & ACTION BAR
                    // =========================================================================
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${fileList.count { it.isDirectory }} folders • ${fileList.count { !it.isDirectory }} files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (selectedMultiFiles.isEmpty()) "Select All"
                                            else "Selected (${selectedMultiFiles.size})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onDismiss) {
                                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                                }

                                if (selectFolders) {
                                    Button(
                                        onClick = { onSelected(currentDir) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Select \"${currentDir.name.ifBlank { "Root" }}\"", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else if (allowMultiSelect && onMultipleSelected != null) {
                                    Button(
                                        onClick = { onMultipleSelected(selectedMultiFiles.toList()) },
                                        enabled = selectedMultiFiles.isNotEmpty(),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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
    }
}
