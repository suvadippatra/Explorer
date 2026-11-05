package com.troikoss.continuum_explorer.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.*
import com.troikoss.continuum_explorer.utils.IconHelper.FileThumbnail
import com.troikoss.continuum_explorer.viewarea.FileMimeGroup
import com.troikoss.continuum_explorer.viewarea.FileMimeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Unified Desktop & Tablet Right Preview Pane.
 * Emphasizes:
 * 1. High-fidelity visual preview (Images, PDF, Video thumbnail with play indicator, Audio, Text snippet).
 * 2. File details (type, size, path, date modified, resolution, duration).
 * 3. Quick action toolbar (Open/View, Edit, Share, Copy to, Move to, Move to Bin).
 */
@Composable
fun FilePreviewer(
    file: UniversalFile,
    appState: FileExplorerState? = null,
    isFullScreen: Boolean = false,
    onFullScreenToggle: (Boolean) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val extension = remember(file.name) {
        FileMimeParser.extractExtension(file.name).lowercase()
    }
    val mimeGroup = remember(file.name) {
        FileMimeGroup.fromExtension(extension)
    }

    val fileType = remember(file) { getFileType(file, context) }

    // Dialog states
    var showCopyDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Media metadata (resolution, duration)
    var resolution by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        resolution = null
        duration = null
        withContext(Dispatchers.IO) {
            if (fileType == "Video" || fileType == "Audio") {
                duration = getMediaDuration(context, file)
            }
            when (fileType) {
                "Video" -> resolution = getVideoResolution(context, file)
                "Image" -> resolution = getImageResolution(context, file)
            }
        }
    }

    // Text snippet for plain text / markdown / code / org / asciidoc
    var textSnippet by remember { mutableStateOf<String?>(null) }
    val isArchiveFile = extension in listOf("zip", "jar", "apk", "tar", "gz", "tgz", "7z", "rar")
    val isTextDocument = mimeGroup == FileMimeGroup.TEXT ||
            extension in listOf(
                "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log",
                "yaml", "yml", "py", "kt", "java", "js", "html", "css", "org", "adoc", "asciidoc", "toml"
            ) || file.name.lowercase().let { it == "todo.txt" || it == "done.txt" || it == "readme" }

    // Live PDF First-Page Bitmap & Page Count for Split Preview Pane
    var pdfPreviewBitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pdfTotalPages by remember(file) { mutableIntStateOf(0) }

    // Live Archive Summary for Split Preview Pane
    var archiveEntryCount by remember(file) { mutableIntStateOf(0) }
    var archiveSampleNames by remember(file) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(file) {
        pdfPreviewBitmap = null
        pdfTotalPages = 0
        archiveEntryCount = 0
        archiveSampleNames = emptyList()

        if (extension == "pdf") {
            withContext(Dispatchers.IO) {
                runCatching {
                    val pfd = file.fileRef?.let {
                        android.os.ParcelFileDescriptor.open(it, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } ?: file.documentFileRef?.uri?.let {
                        context.contentResolver.openFileDescriptor(it, "r")
                    }
                    pfd?.use { descriptor ->
                        android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                            pdfTotalPages = renderer.pageCount
                            if (renderer.pageCount > 0) {
                                renderer.openPage(0).use { page ->
                                    val w = 540
                                    val h = (w * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(100)
                                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    pdfPreviewBitmap = bmp
                                }
                            }
                        }
                    }
                }
            }
        } else if (isArchiveFile) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val src = file.fileRef ?: file.documentFileRef?.uri ?: File(file.absolutePath)
                    val parsed = ZipUtils.parseArchive(context, src)
                    val all = parsed.values.flatten()
                    archiveEntryCount = all.size
                    archiveSampleNames = all.take(6).map { if (it.isDirectory) "📁 ${it.name}/" else "📄 ${it.name}" }
                }
            }
        }
    }

    LaunchedEffect(file) {
        if (isTextDocument) {
            withContext(Dispatchers.IO) {
                try {
                    val stream = file.fileRef?.let { FileInputStream(it) }
                        ?: (file.documentFileRef?.uri?.let { context.contentResolver.openInputStream(it) })
                    val lines = stream?.bufferedReader()?.useLines { linesSeq ->
                        linesSeq.take(25).joinToString("\n")
                    }
                    withContext(Dispatchers.Main) {
                        textSnippet = lines
                    }
                } catch (_: Exception) {
                    textSnippet = null
                }
            }
        } else {
            textSnippet = null
        }
    }

    // Dialogs
    if (showCopyDialog) {
        InAppFilePickerDialog(
            title = "Copy \"${file.name}\" to...",
            selectFolders = true,
            initialDirectory = file.fileRef?.parentFile ?: android.os.Environment.getExternalStorageDirectory(),
            onDismiss = { showCopyDialog = false },
            onSelected = { targetDir ->
                showCopyDialog = false
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val src = file.fileRef
                        if (src != null && src.exists()) {
                            val dest = File(targetDir, file.name)
                            FileInputStream(src).use { input ->
                                FileOutputStream(dest).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Copied to ${dest.absolutePath}", Toast.LENGTH_SHORT).show()
                                appState?.refresh()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Copy failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    if (showMoveDialog) {
        InAppFilePickerDialog(
            title = "Move \"${file.name}\" to...",
            selectFolders = true,
            initialDirectory = file.fileRef?.parentFile ?: android.os.Environment.getExternalStorageDirectory(),
            onDismiss = { showMoveDialog = false },
            onSelected = { targetDir ->
                showMoveDialog = false
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val src = file.fileRef
                        if (src != null && src.exists()) {
                            val dest = File(targetDir, file.name)
                            src.renameTo(dest)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Moved to ${dest.absolutePath}", Toast.LENGTH_SHORT).show()
                                appState?.refresh()
                                onClose()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Move failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Move to Bin?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to move \"${file.name}\" to the Recycle Bin?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                com.troikoss.continuum_explorer.utils.moveToRecycleBin(context, listOf(file))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                                    appState?.refresh()
                                    onClose()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error deleting: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Title & Close / Fullscreen Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = IconHelper.getIconForItem(file),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onFullScreenToggle(!isFullScreen) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) "Exit Fullscreen" else "Fullscreen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Preview",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Visual Preview Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                when {
                    mimeGroup == FileMimeGroup.IMAGE -> {
                        val imageModel = if (file.provider.capabilities.isRemote) {
                            file
                        } else {
                            file.fileRef?.absolutePath ?: file.documentFileRef?.uri
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    appState?.viewingFile = file
                                }
                        )
                    }

                    extension == "pdf" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clickable {
                                    appState?.viewingFile = file
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (pdfPreviewBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = pdfPreviewBitmap!!.asImageBitmap(),
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                IconHelper.ItemIcon(
                                    file = file,
                                    contentSize = 130.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Surface(
                                color = Color(0xCCB71C1C),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = if (pdfTotalPages > 0) "PDF • $pdfTotalPages pages (Tap to Read)" else "PDF Document",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    isArchiveFile -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .clickable { appState?.viewingFile = file },
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Archive Contents ($archiveEntryCount items)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Tap to Browse",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (archiveSampleNames.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    archiveSampleNames.forEach { entryName ->
                                        Text(
                                            text = entryName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    IconHelper.ItemIcon(file = file, contentSize = 80.dp)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        val fRef = file.fileRef
                                        appState?.navigateTo(
                                            newPath = null,
                                            newUri = null,
                                            archiveFile = fRef,
                                            archiveUri = if (fRef == null) file.documentFileRef?.uri else null
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Open in Tab", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { appState?.viewingFile = file },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Extract / Inspect", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    mimeGroup == FileMimeGroup.AUDIO_VIDEO && (extension in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp")) -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    appState?.viewingFile = file
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            IconHelper.ItemIcon(
                                file = file,
                                contentSize = 130.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Play Overlay Button
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                shape = CircleShape,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            if (duration != null) {
                                Surface(
                                    color = Color(0xCC000000),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = duration!!,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    mimeGroup == FileMimeGroup.AUDIO_VIDEO -> {
                        // Audio preview
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { appState?.viewingFile = file }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Audio Track",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (duration != null) {
                                Text(
                                    text = duration!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    isTextDocument && textSnippet != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .clickable {
                                    appState?.viewingFile = file
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Preview (${file.name.substringAfterLast('.', "text")})",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Click to View Full",
                                    color = Color(0xFF888888),
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = textSnippet!!,
                                color = Color(0xFFE0E0E0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                                maxLines = 11,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    else -> {
                        IconHelper.ItemIcon(
                            file = file,
                            contentSize = 110.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { appState?.viewingFile = file }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Quick Actions Toolbar
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Open / View Button
                FilledTonalButton(
                    onClick = {
                        appState?.viewingFile = file
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View", fontSize = 12.sp)
                }

                // Edit Button (if text or photo)
                val canEdit = mimeGroup == FileMimeGroup.IMAGE || isTextDocument
                if (canEdit) {
                    Button(
                        onClick = {
                            if (appState != null) {
                                appState.viewingFile = file
                                appState.openInEditMode = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit", fontSize = 12.sp)
                    }
                }

                // Share Button
                OutlinedButton(
                    onClick = {
                        shareFiles(context, coroutineScope, listOf(file))
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", fontSize = 12.sp)
                }

                // Copy Button
                OutlinedButton(
                    onClick = { showCopyDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", fontSize = 12.sp)
                }

                // Move Button
                OutlinedButton(
                    onClick = { showMoveDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Move", fontSize = 12.sp)
                }

                // Move to Bin Button
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bin", fontSize = 12.sp)
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // File Details Section
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "FILE DETAILS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                DetailRow(label = "Type", value = fileType)
                if (!file.isDirectory) {
                    DetailRow(
                        label = "Size",
                        value = appState?.formatSize(file.length) ?: "${file.length} bytes"
                    )
                }
                DetailRow(
                    label = "Modified",
                    value = appState?.formatDate(file.lastModified) ?: file.lastModified.toString()
                )

                if (resolution != null) {
                    DetailRow(label = "Resolution", value = resolution!!)
                }
                if (duration != null) {
                    DetailRow(label = "Duration", value = duration!!)
                }

                DetailRow(
                    label = "Path",
                    value = file.fileRef?.absolutePath ?: file.absolutePath
                )
                DetailRow(label = "Storage", value = file.provider.kind.name)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Modern placeholder shown in the preview workspace area when no file is currently selected.
 */
@Composable
fun FilePreviewerPlaceholder(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "File Previewer",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Universal File Previewer",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select or hover any file to inspect its live preview in this split pane.\nSupported: PDF, Video, Audio, Archives, Images, Markdown, CSV, Todo.txt & Code",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
