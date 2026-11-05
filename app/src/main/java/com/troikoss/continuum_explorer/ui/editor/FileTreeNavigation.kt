package com.troikoss.continuum_explorer.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Multi-structure file tree node representation for workspace hierarchy navigation.
 */
data class FileTreeNode(
    val file: File,
    val level: Int = 0,
    var isExpanded: Boolean = false,
    val children: MutableList<FileTreeNode> = mutableListOf()
)

@Composable
fun FileTreeNavigation(
    rootDirectory: File,
    activeFile: File?,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedPaths by remember { mutableStateOf(setOf(rootDirectory.absolutePath)) }

    fun togglePath(path: String) {
        expandedPaths = if (expandedPaths.contains(path)) {
            expandedPaths - path
        } else {
            expandedPaths + path
        }
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = "File Tree",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Workspace Tree",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                renderFileTreeItems(
                    dir = rootDirectory,
                    level = 0,
                    expandedPaths = expandedPaths,
                    activeFile = activeFile,
                    onTogglePath = { togglePath(it) },
                    onFileSelected = onFileSelected
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderFileTreeItems(
    dir: File,
    level: Int,
    expandedPaths: Set<String>,
    activeFile: File?,
    onTogglePath: (String) -> Unit,
    onFileSelected: (File) -> Unit
) {
    val isExpanded = expandedPaths.contains(dir.absolutePath)
    val children = try {
        dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    item(key = dir.absolutePath) {
        val isSelected = activeFile?.absolutePath == dir.absolutePath
        val paddingStart = (level * 14).dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = paddingStart)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable {
                    if (dir.isDirectory) {
                        onTogglePath(dir.absolutePath)
                    } else {
                        onFileSelected(dir)
                    }
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dir.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
                val icon = when (dir.extension.lowercase()) {
                    "kt", "java", "json", "xml", "html", "css", "js", "py", "c", "cpp" -> Icons.Default.Code
                    "md", "txt" -> Icons.Default.Description
                    "png", "jpg", "jpeg", "webp" -> Icons.Default.Image
                    "mp4", "mkv", "avi" -> Icons.Default.Movie
                    "mp3", "wav", "ogg" -> Icons.Default.MusicNote
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = dir.name.ifBlank { dir.absolutePath },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (dir.isDirectory || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (dir.isDirectory && isExpanded) {
        children.forEach { child ->
            renderFileTreeItems(
                dir = child,
                level = level + 1,
                expandedPaths = expandedPaths,
                activeFile = activeFile,
                onTogglePath = onTogglePath,
                onFileSelected = onFileSelected
            )
        }
    }
}
