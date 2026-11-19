package com.troikoss.continuum_explorer.ui.preview

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.components.suite.TonalSearchPill
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ultra-Clean Tonal Flat Archive Browser & Extraction Previewer.
 * Zero duplicate stacked top summary header (the single 48.dp Command Bar handles title & Browse).
 * Entries fill the viewport immediately; search, stats, and Extract sit in a sleek 44.dp bottom bar.
 */
@Composable
fun ArchiveFullPageView(
    file: UniversalFile,
    appState: FileExplorerState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember(file) { mutableStateOf(true) }
    var archiveMap by remember(file) { mutableStateOf<Map<String, List<UniversalFile>>>(emptyMap()) }
    var currentInnerPath by remember(file) { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }

    val archiveSource = remember(file) {
        file.fileRef ?: file.documentFileRef?.uri ?: File(file.absolutePath)
    }

    LaunchedEffect(file) {
        isLoading = true
        archiveMap = withContext(Dispatchers.IO) {
            runCatching { ZipUtils.parseArchive(context, archiveSource) }.getOrDefault(emptyMap())
        }
        currentInnerPath = ""
        isLoading = false
    }

    val allEntries = remember(archiveMap) { archiveMap.values.flatten() }
    val totalFilesCount = remember(allEntries) { allEntries.count { !it.isDirectory } }
    val totalFoldersCount = remember(allEntries) { allEntries.count { it.isDirectory } }
    val totalUncompressedBytes = remember(allEntries) { allEntries.sumOf { if (it.isDirectory) 0L else it.length } }

    val currentEntries = remember(archiveMap, currentInnerPath, searchQuery) {
        if (searchQuery.isNotBlank()) {
            allEntries.filter { it.name.contains(searchQuery, ignoreCase = true) }
                .sortedWith(compareBy<UniversalFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
        } else {
            (archiveMap[currentInnerPath] ?: emptyList())
                .sortedWith(compareBy<UniversalFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    fun extractArchive(toSeparateFolder: Boolean) {
        val archiveRef = file.fileRef ?: File(file.absolutePath).takeIf { it.exists() }
        val destParent = archiveRef?.parentFile ?: appState.currentPath
        if (archiveRef == null || destParent == null) {
            Toast.makeText(context, "Direct extraction requires a local archive file", Toast.LENGTH_SHORT).show()
            return
        }
        isExtracting = true
        scope.launch {
            ZipUtils.extractArchives(
                context = context,
                archives = listOf(file),
                baseDestDir = destParent,
                toSeparateFolders = toSeparateFolder
            )
            isExtracting = false
            val targetName = if (toSeparateFolder) file.name.substringBeforeLast('.') else destParent.name
            Toast.makeText(context, "Extracted to $targetName", Toast.LENGTH_SHORT).show()
            appState.refresh()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Inline breadcrumb strip ONLY when drilled into a subfolder inside the archive
        if (currentInnerPath.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = { currentInnerPath = "" },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Root",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    val segments = currentInnerPath.trim('/').split('/').filter { it.isNotEmpty() }
                    segments.forEachIndexed { idx, seg ->
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val targetPath = segments.take(idx + 1).joinToString("/") + "/"
                        val isCurrent = idx == segments.lastIndex
                        Surface(
                            onClick = { currentInnerPath = targetPath },
                            shape = RoundedCornerShape(50),
                            color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = seg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )
        }

        // Full-height entry list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Inspecting archive...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                currentEntries.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching entries" else "Empty or unsupported archive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (currentInnerPath.isNotEmpty() && searchQuery.isBlank()) {
                            item(key = "archive_up_row") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val trimmed = currentInnerPath.removeSuffix("/")
                                            val lastSlash = trimmed.lastIndexOf('/')
                                            currentInnerPath = if (lastSlash < 0) "" else trimmed.substring(0, lastSlash + 1)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Parent Folder", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        items(
                            items = currentEntries,
                            key = { it.providerId }
                        ) { entry ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (entry.isDirectory) {
                                            val sep = entry.providerId.indexOf("::")
                                            val nextPath = if (sep >= 0) entry.providerId.substring(sep + 2) else "${currentInnerPath}${entry.name}/"
                                            currentInnerPath = if (nextPath.endsWith("/")) nextPath else "$nextPath/"
                                            searchQuery = ""
                                        } else {
                                            appState.viewingFile = entry
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (entry.isDirectory) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (entry.isDirectory) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (entry.isDirectory) "Folder" else appState.formatSize(entry.length),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (entry.isDirectory) {
                                        Spacer(modifier = Modifier.width(4.dp))
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

        // Compact 44.dp Bottom Action & Filter Strip
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TonalSearchPill(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Filter entries",
                    modifier = Modifier.weight(1f).widthIn(max = 260.dp),
                    testTagName = "archive_filter_input"
                )

                Text(
                    text = "$totalFilesCount files • $totalFoldersCount folders • ${appState.formatSize(totalUncompressedBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                FilledTonalButton(
                    onClick = { extractArchive(toSeparateFolder = true) },
                    enabled = !isExtracting,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (isExtracting) "Extracting" else "Extract", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
