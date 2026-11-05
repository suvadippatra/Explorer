package com.troikoss.continuum_explorer.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.managers.RecentFilesManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.ui.editor.EditorContainer
import com.troikoss.continuum_explorer.utils.IconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class EditorTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

/**
 * My suite hub listing editors + Recently Accessed Files item + pill search bar + List/Grid view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeAreaScreen(
    appState: com.troikoss.continuum_explorer.utils.FileExplorerState? = null,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedEditor by remember { mutableStateOf<EditorTool?>(null) }
    var activeFileToEdit by remember { mutableStateOf<File?>(null) }

    // Search state for filtering recent files
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }

    val editorsList = remember {
        listOf(
            EditorTool(
                id = "text_editor",
                title = "Text & Code Studio",
                description = "Markor-style unified editor for Plain Text, Markdown, Code (Kotlin, Java, Python, JSON, XML), CSV, and logs",
                icon = Icons.Default.EditNote,
                color = Color(0xFF1E88E5)
            ),
            EditorTool(
                id = "base64",
                title = "Base64 Code",
                description = "Base64 data string generator & inspector for images and small audio files (< 2 MB)",
                icon = Icons.Default.Code,
                color = Color(0xFF00E676)
            ),
            EditorTool(
                id = "image_studio",
                title = "Image Studio",
                description = "High-definition image viewer with zoom, rotate, crop, filters, and color tools",
                icon = Icons.Default.Image,
                color = Color(0xFFE91E63)
            ),
            EditorTool(
                id = "recent_files",
                title = "Recent Suites",
                description = "View and manage documents recently opened, edited, or modified in the suite",
                icon = Icons.Default.History,
                color = Color(0xFF8E24AA)
            )
        )
    }

    var recentFiles by remember { mutableStateOf<List<UniversalFile>>(emptyList()) }
    var isLoadingRecent by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = RecentFilesManager.getRecentFiles(context)
            recentFiles = list
            isLoadingRecent = false
        }
    }

    val filteredRecentFiles = remember(recentFiles, searchQuery) {
        if (searchQuery.isBlank()) recentFiles
        else recentFiles.filter { file ->
            file.name.contains(searchQuery.trim(), ignoreCase = true) ||
            file.name.substringAfterLast('.', "").contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (activeFileToEdit != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                EditorContainer(
                    initialFile = activeFileToEdit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (selectedEditor != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedEditor = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back to My Suite",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = selectedEditor!!.icon,
                            contentDescription = null,
                            tint = selectedEditor!!.color,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedEditor!!.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                EditorContainer(
                    initialFile = null,
                    editorToolId = selectedEditor?.id,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Header Row with Triple Line Button, Centered "My Suite", Search, and Grid/List Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMenuClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isSearchActive,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                            },
                            label = "MySuiteSearchTransition"
                        ) { searching ->
                            if (!searching) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "My Suite",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .padding(horizontal = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    )
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
                                                        text = "Search recent files...",
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
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Toggle List/Grid View Button
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Search Toggle Button
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearchActive) "Close Search" else "Search Recent Files",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Search Results
                if (isSearchActive && searchQuery.isNotBlank()) {
                    if (isLoadingRecent) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (filteredRecentFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recent files match '$searchQuery'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredRecentFiles) { file ->
                                val relativeTime = if (file.lastModified > 0) {
                                    DateUtils.getRelativeTimeSpanString(
                                        file.lastModified,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString()
                                } else "Recently"

                                OutlinedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (appState?.onOpenInNewTab != null) {
                                                appState.onOpenInNewTab?.invoke(file)
                                            } else if (file.fileRef != null && file.fileRef!!.exists()) {
                                                activeFileToEdit = file.fileRef
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            IconHelper.FileThumbnail(
                                                file = file,
                                                iconSize = 36.dp,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = file.name.substringAfterLast('.', "").uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "• $relativeTime",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Icon(
                                            Icons.Default.OpenInNew,
                                            contentDescription = "Open in Separate Tab",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (isGridView) {
                    // GRID VIEW MODE FOR MY SUITE
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(editorsList) { tool ->
                            OutlinedCard(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (tool.id == "recent_files") {
                                            appState?.onOpenInNewTab?.invoke(
                                                UniversalFile(
                                                    name = "Recent Suites",
                                                    isDirectory = false,
                                                    lastModified = 0,
                                                    length = 0,
                                                    provider = LocalProvider,
                                                    providerId = "recent_suites://"
                                                )
                                            )
                                        } else {
                                            if (appState?.onOpenInNewTab != null) {
                                                appState.onOpenInNewTab?.invoke(
                                                    UniversalFile(
                                                        name = tool.title,
                                                        isDirectory = false,
                                                        lastModified = 0,
                                                        length = 0,
                                                        provider = LocalProvider,
                                                        providerId = "editor://${tool.id}"
                                                    )
                                                )
                                            } else {
                                                selectedEditor = tool
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = tool.color.copy(alpha = 0.15f),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = tool.icon,
                                                contentDescription = tool.title,
                                                tint = tool.color,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = tool.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // MINIMALIST COMPACT LIST VIEW MODE FOR MY SUITE
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(editorsList) { tool ->
                            OutlinedCard(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (tool.id == "recent_files") {
                                            appState?.onOpenInNewTab?.invoke(
                                                UniversalFile(
                                                    name = "Recent Suites",
                                                    isDirectory = false,
                                                    lastModified = 0,
                                                    length = 0,
                                                    provider = LocalProvider,
                                                    providerId = "recent_suites://"
                                                )
                                            )
                                        } else {
                                            if (appState?.onOpenInNewTab != null) {
                                                appState.onOpenInNewTab?.invoke(
                                                    UniversalFile(
                                                        name = tool.title,
                                                        isDirectory = false,
                                                        lastModified = 0,
                                                        length = 0,
                                                        provider = LocalProvider,
                                                        providerId = "editor://${tool.id}"
                                                    )
                                                )
                                            } else {
                                                selectedEditor = tool
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = tool.color.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = tool.icon,
                                                contentDescription = tool.title,
                                                tint = tool.color,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tool.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = tool.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
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
