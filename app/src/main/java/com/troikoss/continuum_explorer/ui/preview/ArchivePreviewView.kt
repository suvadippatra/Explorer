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
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.ZipUtils
import com.troikoss.continuum_explorer.utils.navigateTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Interactive Archive Browser & Extraction Previewer.
 * Supports browsing internal folders/files inside ZIP/JAR/APK/TAR/GZ archives,
 * searching archive entries, opening the archive as a native Explorer directory tab,
 * and one-tap extraction to a dedicated folder or current directory.
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

    val allEntries = remember(archiveMap) {
        archiveMap.values.flatten()
    }
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
        // Archive Summary & Action Header Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderZip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$totalFilesCount files • $totalFoldersCount folders • Compressed: ${appState.formatSize(file.length)} • Unpacked: ${appState.formatSize(totalUncompressedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val fRef = file.fileRef ?: File(file.absolutePath).takeIf { it.exists() }
                                onClose()
                                appState.navigateTo(
                                    newPath = null,
                                    newUri = null,
                                    archiveFile = fRef,
                                    archiveUri = if (fRef == null) file.documentFileRef?.uri else null,
                                    archivePath = currentInnerPath
                                )
                            }
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Explorer")
                        }

                        Button(
                            onClick = { extractArchive(toSeparateFolder = true) },
                            enabled = !isExtracting
                        ) {
                            if (isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isExtracting) "Extracting..." else "Extract to Folder")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Breadcrumb + Search Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Breadcrumbs inside archive
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { currentInnerPath = "" },
                            label = { Text("Root /", fontWeight = if (currentInnerPath.isEmpty()) FontWeight.Bold else FontWeight.Normal) }
                        )
                        val segments = currentInnerPath.trim('/').split('/').filter { it.isNotEmpty() }
                        segments.forEachIndexed { idx, seg ->
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val targetPath = segments.take(idx + 1).joinToString("/") + "/"
                            AssistChip(
                                onClick = { currentInnerPath = targetPath },
                                label = {
                                    Text(
                                        seg,
                                        fontWeight = if (idx == segments.lastIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter entries...", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.widthIn(min = 180.dp, max = 260.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Inspecting archive structure...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (currentEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderZip,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching entries inside archive" else "Archive folder is empty or unsupported format",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (currentInnerPath.isNotEmpty() && searchQuery.isBlank()) {
                    item(key = "archive_up_row") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
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
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(".. (Parent Folder)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                items(
                    items = currentEntries,
                    key = { it.providerId }
                ) { entry ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
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
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (entry.isDirectory) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (entry.isDirectory) "Folder" else "${appState.formatSize(entry.length)} • ${appState.formatDate(entry.lastModified)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (entry.isDirectory) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
