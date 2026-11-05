package com.troikoss.continuum_explorer.ui.components

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import java.io.File

enum class PickerSortBy { NAME, SIZE, DATE }

/**
 * Optimized full page file/folder picker following M3 guidelines.
 * Features:
 * - Direct header alignment below tab bar with no gap
 * - Search icon in header that transforms into a pill search bar
 * - Short title ("Select Files")
 * - Mobile bottom pill bar for Done action
 * - Clean M3 card-based item rendering
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

    // Detect external SD cards / USB storage
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

    var currentDir by remember { mutableStateOf(internalStorage) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(PickerSortBy.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Intercept back button to navigate parent folder first
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
        val isMobileLayout = maxWidth < 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP HEADING BAR (Directly below tab lines, flush layout) ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val parent = currentDir.parentFile
                        if (parent != null && externalVolumes.any { currentDir.absolutePath.startsWith(it.absolutePath) && currentDir != it }) {
                            currentDir = parent
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
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
                                    text = if (isHtmlMode) "Select HTML Target" else title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

                    // Search Toggle Icon Button
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Sort Menu Button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort By")
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

                    // Done Button in Header (Wide screen only)
                    if (!isMobileLayout) {
                        TextButton(onClick = onDismiss) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- STORAGE ROOT TABS (Internal Storage, SD Card, USB) ---
            if (externalVolumes.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    externalVolumes.forEachIndexed { index, volume ->
                        val isSelected = currentDir.absolutePath.startsWith(volume.absolutePath)
                        FilterChip(
                            selected = isSelected,
                            onClick = { currentDir = volume },
                            label = {
                                Text(if (index == 0) "Internal Storage" else "External ${volume.name}", style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = {
                                Icon(if (index == 0) Icons.Default.PhoneAndroid else Icons.Default.SdCard, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // --- CURRENT DIRECTORY PATH PILL & QUICK FOLDER ACTION ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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
                        text = currentDir.name.ifBlank { currentDir.absolutePath },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Host Folder", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    val inBucket = bucketList.any { it.absolutePath == currentDir.absolutePath }
                    TextButton(
                        onClick = {
                            if (inBucket) FtpBucketManager.removeItem(context, currentDir)
                            else FtpBucketManager.addItem(context, currentDir)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            if (inBucket) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (inBucket) "Remove Folder" else "+ Add Folder", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // --- BUCKET STATUS PILL ---
            if (!isHtmlMode && bucketList.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingBasket, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bucket: ${bucketList.size} item(s) staged",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        TextButton(
                            onClick = { FtpBucketManager.clearBucket(context) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // --- FILE & FOLDER LIST (Material UI Card Cards) ---
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = if (isMobileLayout) 70.dp else 12.dp)
            ) {
                if (fileList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No files or folders found", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                    }
                }

                items(fileList, key = { it.absolutePath }) { file ->
                    val isDir = file.isDirectory
                    val isSelectedInBucket = bucketList.any { it.absolutePath == file.absolutePath }
                    val isHtmlFile = file.name.endsWith(".html", ignoreCase = true) || file.name.endsWith(".htm", ignoreCase = true)

                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelectedInBucket) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
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
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isDir -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        isHtmlFile -> Color(0xFFE65100).copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when {
                                                isDir -> Icons.Default.Folder
                                                isHtmlFile -> Icons.Default.Html
                                                else -> Icons.Default.InsertDriveFile
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                isDir -> MaterialTheme.colorScheme.primary
                                                isHtmlFile -> Color(0xFFE65100)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
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
                                        text = if (isDir) "Folder" else "${file.length() / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isHtmlMode) {
                                if (isHtmlFile) {
                                    FilledTonalButton(
                                        onClick = {
                                            onHtmlSelected?.invoke(file)
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Select HTML", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                IconButton(onClick = {
                                    if (isSelectedInBucket) FtpBucketManager.removeItem(context, file)
                                    else FtpBucketManager.addItem(context, file)
                                }) {
                                    Icon(
                                        imageVector = if (isSelectedInBucket) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                        contentDescription = if (isSelectedInBucket) "Selected" else "Add",
                                        tint = if (isSelectedInBucket) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- MOBILE BOTTOM PILL BAR FOR DONE ACTION ---
        if (isMobileLayout) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        text = if (bucketList.isNotEmpty()) "Done (${bucketList.size} selected)" else "Done",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
