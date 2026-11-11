package com.troikoss.continuum_explorer.ui.components

import android.os.Environment
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.utils.toUniversal
import java.io.File

enum class PickerSortBy { NAME, SIZE, DATE }

/**
 * Desktop/DeX & Mobile optimized full-page file/folder picker.
 * - On Desktop/DeX (`maxWidth >= 660.dp`): Split-pane Explorer layout with a Left Quick-Access Places Sidebar,
 *   inline header search box, breadcrumb bar, multi-column compact tiles (`GridCells.Adaptive(220.dp)`) or
 *   icon grid (`GridCells.Adaptive(130.dp)`), and a desktop bottom status bar.
 * - On Mobile (`maxWidth < 660.dp`): Clean Material 3 single-column list or 2-column grid with floating Done pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPageFilePicker(
    title: String = "Select Files",
    isHtmlMode: Boolean = false,
    onDismiss: () -> Unit,
    onHtmlSelected: ((File) -> Unit)? = null
) {
    val context = LocalContext.current
    val internalStorage = Environment.getExternalStorageDirectory()
    val isHeaderPinned by SettingsManager.isSubpageHeaderPinned

    val externalVolumes = remember {
        val list = mutableListOf<File>()
        list.add(internalStorage)
        try {
            val rootMedia = File("/storage")
            if (rootMedia.exists() && rootMedia.isDirectory) {
                rootMedia.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.canRead() && f.name != "emulated" && f.name != "self") {
                        if (!list.any { it.absolutePath == f.absolutePath }) {
                            list.add(f)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    val quickPlaces = remember(externalVolumes) {
        buildList {
            add(Triple("Internal Storage", Icons.Default.Storage, internalStorage))
            externalVolumes.drop(1).forEach { vol ->
                add(Triple("SD / USB (${vol.name})", Icons.Default.SdCard, vol))
            }
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads.exists()) add(Triple("Downloads", Icons.Default.Download, downloads))
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documents.exists()) add(Triple("Documents", Icons.Default.Description, documents))
            val suiteDir = File(documents, "ContinuumSuite")
            if (suiteDir.exists()) add(Triple("My Suite", Icons.Default.AutoAwesomeMosaic, suiteDir))
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (pictures.exists()) add(Triple("Pictures", Icons.Default.Image, pictures))
            val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            if (dcim.exists()) add(Triple("Camera (DCIM)", Icons.Default.PhotoCamera, dcim))
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (movies.exists()) add(Triple("Movies", Icons.Default.Movie, movies))
            val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (music.exists()) add(Triple("Music", Icons.Default.MusicNote, music))
        }
    }

    var currentDir by remember { mutableStateOf(internalStorage) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(PickerSortBy.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    BackHandler {
        val parent = currentDir.parentFile
        if (parent != null && externalVolumes.any { currentDir.absolutePath.startsWith(it.absolutePath) && currentDir != it }) {
            currentDir = parent
        } else {
            onDismiss()
        }
    }

    val fileList = remember(currentDir, searchQuery, sortBy, sortAscending) {
        val raw = try {
            currentDir.listFiles()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val filtered = raw.filter { file ->
            if (file.name.startsWith(".")) false
            else if (searchQuery.isNotBlank()) file.name.contains(searchQuery.trim(), ignoreCase = true)
            else true
        }

        when (sortBy) {
            PickerSortBy.NAME -> {
                filtered.sortedWith(
                    if (sortAscending) compareBy({ !it.isDirectory }, { it.name.lowercase() })
                    else compareBy<File>({ !it.isDirectory }).thenByDescending { it.name.lowercase() }
                )
            }
            PickerSortBy.SIZE -> {
                filtered.sortedWith(
                    if (sortAscending) compareBy({ !it.isDirectory }, { it.length() })
                    else compareBy<File>({ !it.isDirectory }).thenByDescending { it.length() }
                )
            }
            PickerSortBy.DATE -> {
                filtered.sortedWith(
                    if (sortAscending) compareBy({ !it.isDirectory }, { it.lastModified() })
                    else compareBy<File>({ !it.isDirectory }).thenByDescending { it.lastModified() }
                )
            }
        }
    }

    val bucketList = FtpBucketManager.bucketItems

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isDesktopLayout = maxWidth >= 660.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // --- LOCKABLE TOP HEADING BAR ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = if (isHeaderPinned) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val parent = currentDir.parentFile
                            if (parent != null && externalVolumes.any { currentDir.absolutePath.startsWith(it.absolutePath) && currentDir != it }) {
                                currentDir = parent
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }

                    if (isDesktopLayout) {
                        Text(
                            text = if (isHtmlMode) "Select Website Folder or HTML File" else title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Inline Desktop Search Box
                        Surface(
                            modifier = Modifier
                                .width(240.dp)
                                .height(34.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                                Spacer(modifier = Modifier.width(6.dp))
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
                                                text = "Filter current folder...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        inner()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            AnimatedContent(
                                targetState = isSearchActive,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                },
                                label = "FilePickerSearchTransition"
                            ) { searching ->
                                if (!searching) {
                                    Text(
                                        text = if (isHtmlMode) "Select Website Target" else title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .padding(horizontal = 4.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 14.sp
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.weight(1f),
                                                decorationBox = { innerTextField ->
                                                    if (searchQuery.isEmpty()) {
                                                        Text(
                                                            text = "Search files & folders...",
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                fontSize = 13.sp
                                                            )
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { searchQuery = "" },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Pin / Lock Heading Bar Toggle
                    IconButton(
                        onClick = { SettingsManager.toggleSubpageHeaderPinned(context) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (isHeaderPinned) "Header Locked on Top" else "Lock Header on Top",
                            tint = if (isHeaderPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Compact Tiles / Icon Grid Toggle
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewModule else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Sort Menu Button
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort By", modifier = Modifier.size(19.dp))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by Name") },
                                onClick = {
                                    if (sortBy == PickerSortBy.NAME) sortAscending = !sortAscending
                                    else { sortBy = PickerSortBy.NAME; sortAscending = true }
                                    showSortMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Size") },
                                onClick = {
                                    if (sortBy == PickerSortBy.SIZE) sortAscending = !sortAscending
                                    else { sortBy = PickerSortBy.SIZE; sortAscending = false }
                                    showSortMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Date") },
                                onClick = {
                                    if (sortBy == PickerSortBy.DATE) sortAscending = !sortAscending
                                    else { sortBy = PickerSortBy.DATE; sortAscending = false }
                                    showSortMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                            )
                        }
                    }

                    if (isDesktopLayout) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (!isHtmlMode && bucketList.isNotEmpty()) "Done (${bucketList.size})" else "Done",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // --- MAIN EXPLORER AREA (Split-Pane on Desktop, Single-Pane on Mobile) ---
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isDesktopLayout) {
                    // Left Places Sidebar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Text(
                                    text = "PLACES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            items(quickPlaces) { (label, icon, placeDir) ->
                                val isCurrentPlace = currentDir.absolutePath == placeDir.absolutePath
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrentPlace) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentDir = placeDir }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isCurrentPlace) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isCurrentPlace) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrentPlace) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Right / Main File Browser Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Mobile quick-jump chips
                    if (!isDesktopLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickPlaces.take(6).forEach { (label, icon, placeDir) ->
                                val isSelected = currentDir.absolutePath == placeDir.absolutePath
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { currentDir = placeDir },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Current Path Bar + Folder Action
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentDir.absolutePath,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isHtmlMode) {
                                FilledTonalButton(
                                    onClick = {
                                        onHtmlSelected?.invoke(currentDir)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Host This Folder", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                val inBucket = bucketList.any { it.absolutePath == currentDir.absolutePath }
                                FilledTonalButton(
                                    onClick = {
                                        if (inBucket) FtpBucketManager.removeItem(context, currentDir)
                                        else FtpBucketManager.addItem(context, currentDir)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        if (inBucket) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (inBucket) "Remove Folder" else "+ Stage Folder", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Staged Bucket Status Strip
                    if (!isHtmlMode && bucketList.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "${bucketList.size} item(s) staged for FTP share",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = { FtpBucketManager.clearBucket(context) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // File & Folder Content Area
                    if (fileList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No files or folders found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (isDesktopLayout || isGridView) {
                        val minCellWidth = when {
                            isGridView && isDesktopLayout -> 135.dp
                            isGridView -> 145.dp
                            else -> 230.dp // Desktop multi-column compact tiles
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = minCellWidth),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = if (!isDesktopLayout) 72.dp else 12.dp)
                        ) {
                            items(fileList, key = { it.absolutePath }) { file ->
                                val isDir = file.isDirectory
                                val isSelectedInBucket = bucketList.any { it.absolutePath == file.absolutePath }
                                val isHtmlFile = file.name.endsWith(".html", ignoreCase = true) || file.name.endsWith(".htm", ignoreCase = true)
                                val uFile = remember(file.absolutePath) { file.toUniversal() }

                                OutlinedCard(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = if (isSelectedInBucket) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelectedInBucket) 1.5.dp else 1.dp,
                                        color = if (isSelectedInBucket) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isDir) {
                                                currentDir = file
                                            } else if (isHtmlMode) {
                                                onHtmlSelected?.invoke(file)
                                                onDismiss()
                                            } else {
                                                if (isSelectedInBucket) FtpBucketManager.removeItem(context, file)
                                                else FtpBucketManager.addItem(context, file)
                                            }
                                        }
                                ) {
                                    if (isGridView) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.troikoss.continuum_explorer.utils.IconHelper.FileThumbnail(
                                                    file = uFile,
                                                    iconSize = 40.dp,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isDir) FontWeight.Bold else FontWeight.Medium,
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
                                    } else {
                                        // Desktop Multi-Column Compact Tile
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.troikoss.continuum_explorer.utils.IconHelper.FileThumbnail(
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
                                                Text(
                                                    text = if (isDir) "Folder" else Formatter.formatFileSize(context, file.length()),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                            if (!isHtmlMode) {
                                                IconButton(
                                                    onClick = {
                                                        if (isSelectedInBucket) FtpBucketManager.removeItem(context, file)
                                                        else FtpBucketManager.addItem(context, file)
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelectedInBucket) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                                        contentDescription = null,
                                                        tint = if (isSelectedInBucket) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            } else if (isHtmlFile) {
                                                Icon(
                                                    imageVector = Icons.Default.Html,
                                                    contentDescription = "HTML",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Mobile Compact List
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 74.dp)
                        ) {
                            items(fileList, key = { it.absolutePath }) { file ->
                                val isDir = file.isDirectory
                                val isSelectedInBucket = bucketList.any { it.absolutePath == file.absolutePath }
                                val isHtmlFile = file.name.endsWith(".html", ignoreCase = true) || file.name.endsWith(".htm", ignoreCase = true)
                                val uFile = remember(file.absolutePath) { file.toUniversal() }

                                OutlinedCard(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = if (isSelectedInBucket) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelectedInBucket) 1.5.dp else 1.dp,
                                        color = if (isSelectedInBucket) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isDir) {
                                                currentDir = file
                                            } else if (isHtmlMode) {
                                                onHtmlSelected?.invoke(file)
                                                onDismiss()
                                            } else {
                                                if (isSelectedInBucket) FtpBucketManager.removeItem(context, file)
                                                else FtpBucketManager.addItem(context, file)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.troikoss.continuum_explorer.utils.IconHelper.FileThumbnail(
                                                    file = uFile,
                                                    iconSize = 30.dp,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal,
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

                                        if (isHtmlMode && isHtmlFile) {
                                            FilledTonalButton(
                                                onClick = {
                                                    onHtmlSelected?.invoke(file)
                                                    onDismiss()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("Host HTML", style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else if (!isHtmlMode) {
                                            IconButton(
                                                onClick = {
                                                    if (isSelectedInBucket) FtpBucketManager.removeItem(context, file)
                                                    else FtpBucketManager.addItem(context, file)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelectedInBucket) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                                    contentDescription = null,
                                                    tint = if (isSelectedInBucket) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Desktop Bottom Status Bar
                    if (isDesktopLayout) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${fileList.size} items in ${currentDir.name.ifBlank { "/" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isHtmlMode) "Click any folder to browse, 'Host This Folder' to host a website directory, or click an HTML file"
                                    else "Click folders to open • Click files or '+' to stage into FTP Bucket",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mobile Floating Done Pill
        if (!isDesktopLayout) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!isHtmlMode && bucketList.isNotEmpty()) "Done (${bucketList.size} selected)" else "Done",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
