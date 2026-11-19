package com.troikoss.continuum_explorer.ui.components

import android.content.Intent
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.editor.NativeImageViewerEditor
import com.troikoss.continuum_explorer.ui.editor.pdf.PdfViewerEngine
import com.troikoss.continuum_explorer.ui.editor.text.MarkorTextEngine
import com.troikoss.continuum_explorer.ui.preview.ArchiveFullPageView
import com.troikoss.continuum_explorer.ui.preview.DedicatedAudioPlayerView
import com.troikoss.continuum_explorer.ui.preview.DedicatedVideoPlayerView
import com.troikoss.continuum_explorer.ui.preview.ImageFullPageView
import com.troikoss.continuum_explorer.ui.preview.TextFullPageView
import com.troikoss.continuum_explorer.ui.preview.UnsupportedFullPageView
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.GlobalEvents
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import com.troikoss.continuum_explorer.utils.navigateTo
import com.troikoss.continuum_explorer.utils.shareFiles
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modular Full-Page File Viewer & Editor Router.
 * Displays top bar information and actions in logical order:
 * 1. File Name & Format/Size Subtitle
 * 2. Edit Option (for supported editable files)
 * 3. Metadata View / Edit Option (Inline Rename, EXIF Tag Editor, Checksums, Attributes)
 * 4. Delete Action (with confirmation)
 * 5. Share & Open External
 * 6. Full-Screen Toggle (Hides/Shows Top TabBar)
 */
@Composable
fun ViewOnlyFileViewer(
    file: UniversalFile,
    appState: FileExplorerState,
    startInEditMode: Boolean = appState.openInEditMode,
    onClose: () -> Unit = {
        appState.closeViewerOrReturnToOrigin()
    },
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var currentFile by remember(file) { mutableStateOf(file) }
    val ext = remember(currentFile.name) { currentFile.name.substringAfterLast('.', "").lowercase() }

    // Remember whether this viewer tab was launched directly into Edit Mode (e.g. from 3-dot Edit or My Suite)
    val launchedDirectlyInEdit = remember(file) { startInEditMode || appState.openInEditMode }
    var isEditing by remember(file, startInEditMode, appState.openInEditMode) {
        mutableStateOf(startInEditMode || appState.openInEditMode)
    }

    val isApk = ext == "apk"
    val isPdf = ext == "pdf"
    val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "ico", "svg")
    val isAudio = ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "wma")
    val isVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts", "m4v")
    val isArchive = ext in listOf("zip", "jar", "tar", "gz", "tgz", "7z", "rar")
    val isText = ext in listOf(
        "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log",
        "kt", "java", "py", "js", "ts", "html", "htm", "xhtml", "css", "c", "cpp", "h",
        "sh", "yaml", "yml", "ini", "conf", "properties", "gradle", "sql",
        "org", "adoc", "asciidoc", "wiki", "toml"
    ) || currentFile.name.lowercase().let { it == "todo.txt" || it == "done.txt" || it == "readme" || it == "license" }

    var showMetadataDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

    if (isApk) {
        com.troikoss.continuum_explorer.ui.dialogs.ApkInstallerSheet(
            apkUniversalFile = currentFile,
            scope = scope,
            onDismiss = onClose
        )
        return
    }

    val handleExitEditMode = {
        if (launchedDirectlyInEdit) {
            appState.openInEditMode = false
            onClose()
        } else {
            isEditing = false
            appState.openInEditMode = false
        }
    }

    BackHandler {
        when {
            appState.isVideoFullScreen -> appState.isVideoFullScreen = false
            isEditing -> handleExitEditMode()
            else -> onClose()
        }
    }

    // If in Edit Mode, delegate directly to CSV Studio, Code Studio, Markor Studio, or PhotoEditor Studio
    if (isEditing) {
        val localEditFile = currentFile.fileRef ?: (currentFile.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) })
        LaunchedEffect(currentFile.absolutePath) {
            localEditFile?.let { f ->
                com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase.recordFileActivity(
                    context = context,
                    file = f,
                    activityType = com.troikoss.continuum_explorer.data.db.SuiteActivityType.EDITED
                )
            }
        }
        when {
            ext in listOf("csv", "tsv") && localEditFile != null -> {
                var csvContent by remember(localEditFile.absolutePath) { mutableStateOf("") }
                LaunchedEffect(localEditFile.absolutePath) {
                    csvContent = withContext(Dispatchers.IO) {
                        runCatching { localEditFile.readText() }.getOrDefault("")
                    }
                }
                com.troikoss.continuum_explorer.ui.editor.CsvEditorView(
                    file = localEditFile,
                    content = csvContent,
                    onContentChange = { csvContent = it },
                    onSave = {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                localEditFile.writeText(csvContent)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Saved ${localEditFile.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onMenuClick = onMenuClick,
                    onBackClick = handleExitEditMode,
                    modifier = modifier.fillMaxSize()
                )
                return
            }
            ext in listOf(
                "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "xhtml", "css", "scss",
                "json", "xml", "yaml", "yml", "toml", "ini", "conf", "sh", "bash", "c", "cpp", "h",
                "hpp", "sql", "gradle", "properties", "go", "rs", "dart", "php", "rb", "lua"
            ) && localEditFile != null -> {
                var codeContent by remember(localEditFile.absolutePath) { mutableStateOf("") }
                LaunchedEffect(localEditFile.absolutePath) {
                    codeContent = withContext(Dispatchers.IO) {
                        runCatching { localEditFile.readText() }.getOrDefault("")
                    }
                }
                com.troikoss.continuum_explorer.ui.editor.CodeEditorView(
                    file = localEditFile,
                    content = codeContent,
                    onContentChange = { codeContent = it },
                    onSave = {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                localEditFile.writeText(codeContent)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Saved ${localEditFile.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onMenuClick = onMenuClick,
                    onBackClick = handleExitEditMode,
                    modifier = modifier.fillMaxSize()
                )
                return
            }
            isText -> {
                MarkorTextEngine(
                    file = currentFile,
                    appState = appState,
                    onMenuClick = onMenuClick,
                    onExitEditor = handleExitEditMode,
                    modifier = modifier.fillMaxSize()
                )
                return
            }
            isImage -> {
                NativeImageViewerEditor(
                    file = currentFile,
                    appState = appState,
                    onMenuClick = onMenuClick,
                    onExitEditor = handleExitEditMode,
                    modifier = modifier.fillMaxSize()
                )
                return
            }
        }
    }

    val formattedSize = remember(currentFile.length) {
        if (currentFile.length > 0) Formatter.formatFileSize(context, currentFile.length) else "0 B"
    }

    var showMoreActionsMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Strict Single 48.dp Command Bar (no 2-line subtitle; Video renders its own player bar)
        if (!isVideo) {
            com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar(
                navigationContent = {
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                titleContent = {
                    Text(
                        text = currentFile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actionsContent = {
                    if (isText || isImage) {
                        FilledTonalButton(
                            onClick = {
                                isEditing = true
                                appState.openInEditMode = true
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } else if (isArchive) {
                        FilledTonalButton(
                            onClick = {
                                val fRef = currentFile.fileRef
                                onClose()
                                appState.navigateTo(
                                    newPath = null,
                                    newUri = null,
                                    archiveFile = fRef,
                                    archiveUri = if (fRef == null) currentFile.documentFileRef?.uri else null
                                )
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Browse", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    IconButton(
                        onClick = { showMetadataDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Metadata",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { shareFiles(context, scope, listOf(currentFile)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { SettingsManager.toggleFullScreenTabBar() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) "Exit Full Screen" else "Full Screen",
                            tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMoreActionsMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreActionsMenu,
                            onDismissRequest = { showMoreActionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open External") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    showMoreActionsMenu = false
                                    val uri = getUriForUniversalFile(context, currentFile)
                                    if (uri != null) {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
                                            .onFailure { Toast.makeText(context, "No external app found", Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMoreActionsMenu = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isPdf -> {
                    PdfViewerEngine(
                        file = currentFile,
                        appState = appState,
                        showInternalHeader = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isImage -> {
                    ImageFullPageView(file = currentFile)
                }
                isAudio -> {
                    DedicatedAudioPlayerView(
                        file = currentFile,
                        appState = appState,
                        onClose = onClose
                    )
                }
                isVideo -> {
                    DedicatedVideoPlayerView(
                        file = currentFile,
                        appState = appState,
                        onClose = onClose
                    )
                }
                isArchive -> {
                    ArchiveFullPageView(
                        file = currentFile,
                        appState = appState,
                        onClose = onClose
                    )
                }
                isText -> {
                    TextFullPageView(
                        file = currentFile,
                        appState = appState
                    )
                }
                else -> {
                    UnsupportedFullPageView(
                        file = currentFile,
                        appState = appState
                    )
                }
            }
        }

        if (!isVideo && !isAudio) {
            val parentLabel = remember(currentFile.absolutePath) {
                currentFile.fileRef?.parentFile?.name ?: currentFile.absolutePath.substringBeforeLast('/').substringAfterLast('/', "")
            }
            com.troikoss.continuum_explorer.ui.components.suite.SlimStatusStrip(
                leftText = "${ext.uppercase().ifEmpty { "FILE" }} • $formattedSize",
                rightText = parentLabel
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete File?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete \"${currentFile.name}\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch(Dispatchers.IO) {
                            val fRef = currentFile.fileRef ?: (currentFile.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) })
                            val deleted = try {
                                fRef?.delete() ?: currentFile.documentFileRef?.delete() ?: false
                            } catch (_: Exception) {
                                false
                            }
                            withContext(Dispatchers.Main) {
                                if (deleted) {
                                    Toast.makeText(context, "Deleted ${currentFile.name}", Toast.LENGTH_SHORT).show()
                                    appState.refresh()
                                    GlobalEvents.triggerRefresh()
                                    onClose()
                                } else {
                                    Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Interactive Metadata View & Edit Modal
    if (showMetadataDialog) {
        FileMetadataEditorDialog(
            file = currentFile,
            isImage = isImage,
            onDismiss = { showMetadataDialog = false },
            onFileRenamed = { updatedUniversalFile ->
                currentFile = updatedUniversalFile
                appState.viewingFile = updatedUniversalFile
                appState.refresh()
                GlobalEvents.triggerRefresh()
            },
            onCopyText = { label, value ->
                clipboardManager.setText(AnnotatedString(value))
                Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun FileMetadataEditorDialog(
    file: UniversalFile,
    isImage: Boolean,
    onDismiss: () -> Unit,
    onFileRenamed: (UniversalFile) -> Unit,
    onCopyText: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileRef = remember(file) { file.fileRef ?: (file.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) }) }

    var renameInput by remember(file) { mutableStateOf(file.name) }
    var exifArtist by remember { mutableStateOf("") }
    var exifCopyright by remember { mutableStateOf("") }
    var exifDescription by remember { mutableStateOf("") }
    var exifMake by remember { mutableStateOf("") }
    var exifModel by remember { mutableStateOf("") }
    var exifDateTime by remember { mutableStateOf("") }
    var hasGps by remember { mutableStateOf(false) }

    var md5Hash by remember { mutableStateOf("Calculating...") }
    var sha256Hash by remember { mutableStateOf("Calculating...") }

    val canEditExif = remember(fileRef, isImage) {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        isImage && fileRef != null && fileRef.exists() && ext in listOf("jpg", "jpeg", "png", "webp")
    }

    LaunchedEffect(fileRef) {
        withContext(Dispatchers.IO) {
            if (fileRef != null && fileRef.exists()) {
                // Load EXIF if applicable
                if (canEditExif) {
                    runCatching {
                        val exif = ExifInterface(fileRef.absolutePath)
                        exifArtist = exif.getAttribute(ExifInterface.TAG_ARTIST) ?: ""
                        exifCopyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT) ?: ""
                        exifDescription = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) ?: ""
                        exifMake = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
                        exifModel = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
                        exifDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: ""
                        hasGps = exif.latLong != null
                    }
                }
                // Compute Checksums (up to 64 MB for instant responsiveness)
                if (fileRef.length() <= 64 * 1024 * 1024) {
                    runCatching {
                        val md5 = MessageDigest.getInstance("MD5")
                        val sha256 = MessageDigest.getInstance("SHA-256")
                        fileRef.inputStream().use { fis ->
                            val buf = ByteArray(8192)
                            var read: Int
                            while (fis.read(buf).also { read = it } != -1) {
                                md5.update(buf, 0, read)
                                sha256.update(buf, 0, read)
                            }
                        }
                        md5Hash = md5.digest().joinToString("") { "%02x".format(it) }
                        sha256Hash = sha256.digest().joinToString("") { "%02x".format(it) }
                    }.onFailure {
                        md5Hash = "Unavailable"
                        sha256Hash = "Unavailable"
                    }
                } else {
                    md5Hash = "Skipped (> 64 MB)"
                    sha256Hash = "Skipped (> 64 MB)"
                }
            } else {
                md5Hash = "N/A (Stream)"
                sha256Hash = "N/A (Stream)"
            }
        }
    }

    val fullPath = fileRef?.absolutePath ?: file.absolutePath
    val formattedDate = remember(file.lastModified) {
        if (file.lastModified > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified))
        } else "Unknown"
    }
    val formattedSize = remember(file.length) {
        "${Formatter.formatFileSize(context, file.length)} (${file.length} bytes)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("File Metadata & Properties", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Inline Rename & Extension Edit
                Text("File Name & Extension", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Name & Extension") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (renameInput.isNotEmpty()) {
                            IconButton(onClick = { renameInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // 2. Storage Attributes & Copyable Paths / Checksums
                Text("Storage & Checksums", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                MetadataRowItem(
                    label = "Path",
                    value = fullPath,
                    onCopy = { onCopyText("Path", fullPath) }
                )
                MetadataRowItem(
                    label = "Size",
                    value = formattedSize,
                    onCopy = { onCopyText("Size", formattedSize) }
                )
                MetadataRowItem(
                    label = "Modified",
                    value = formattedDate
                )
                if (fileRef != null) {
                    val perms = buildString {
                        append(if (fileRef.canRead()) "r" else "-")
                        append(if (fileRef.canWrite()) "w" else "-")
                        append(if (fileRef.canExecute()) "x" else "-")
                        if (fileRef.isHidden) append(" (Hidden)")
                    }
                    MetadataRowItem(label = "Permissions", value = perms)
                }
                MetadataRowItem(
                    label = "MD5",
                    value = md5Hash,
                    monospace = true,
                    onCopy = { onCopyText("MD5", md5Hash) }
                )
                MetadataRowItem(
                    label = "SHA-256",
                    value = sha256Hash,
                    monospace = true,
                    onCopy = { onCopyText("SHA-256", sha256Hash) }
                )

                // 3. Editable EXIF Tags (for JPEG / PNG / WebP)
                if (canEditExif) {
                    HorizontalDivider()
                    Text("Editable Image EXIF Tags", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = exifArtist,
                        onValueChange = { exifArtist = it },
                        label = { Text("Artist / Author") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exifCopyright,
                        onValueChange = { exifCopyright = it },
                        label = { Text("Copyright") },
                        leadingIcon = { Icon(Icons.Default.Copyright, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exifDescription,
                        onValueChange = { exifDescription = it },
                        label = { Text("Description / Caption") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = exifMake,
                            onValueChange = { exifMake = it },
                            label = { Text("Camera Make") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = exifModel,
                            onValueChange = { exifModel = it },
                            label = { Text("Camera Model") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (hasGps) {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        val exif = ExifInterface(fileRef!!.absolutePath)
                                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                                        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                                        exif.saveAttributes()
                                        hasGps = false
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "GPS location stripped from image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Strip GPS Location Metadata")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        var activeRef = fileRef
                        val newName = renameInput.trim()

                        // Save EXIF changes first
                        if (canEditExif && activeRef != null && activeRef.exists()) {
                            runCatching {
                                val exif = ExifInterface(activeRef.absolutePath)
                                exif.setAttribute(ExifInterface.TAG_ARTIST, exifArtist)
                                exif.setAttribute(ExifInterface.TAG_COPYRIGHT, exifCopyright)
                                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, exifDescription)
                                exif.setAttribute(ExifInterface.TAG_MAKE, exifMake)
                                exif.setAttribute(ExifInterface.TAG_MODEL, exifModel)
                                exif.saveAttributes()
                            }
                        }

                        // Apply rename if modified
                        var renamedFile: File? = null
                        if (activeRef != null && activeRef.exists() && newName.isNotEmpty() && newName != file.name) {
                            val target = File(activeRef.parentFile ?: Environment.getExternalStorageDirectory(), newName)
                            if (activeRef.renameTo(target)) {
                                renamedFile = target
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (renamedFile != null) {
                                onFileRenamed(renamedFile.toUniversal())
                                Toast.makeText(context, "Saved metadata & renamed to ${renamedFile.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Metadata saved", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun MetadataRowItem(
    label: String,
    value: String,
    monospace: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
