package com.troikoss.continuum_explorer.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import com.troikoss.continuum_explorer.utils.navigateTo
import com.troikoss.continuum_explorer.utils.shareFiles

/**
 * Modular Full-Page File Viewer & Editor Router.
 * Adheres to INSTRUCTION.md: flush Heading (Action Bar) directly beneath the Title Bar (Tab Bar)
 * with zero middle gaps, delegating file rendering and editing to dedicated modular engines.
 */
@Composable
fun ViewOnlyFileViewer(
    file: UniversalFile,
    appState: FileExplorerState,
    onClose: () -> Unit = {
        appState.openInEditMode = false
        appState.viewingFile = null
    },
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = file.name.substringAfterLast('.', "").lowercase()

    val isPdf = ext == "pdf"
    val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "ico", "svg")
    val isAudio = ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "wma")
    val isVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts", "m4v")
    val isArchive = ext in listOf("zip", "jar", "apk", "tar", "gz", "tgz", "7z", "rar")
    val isText = ext in listOf(
        "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log",
        "kt", "java", "py", "js", "ts", "html", "htm", "css", "c", "cpp", "h",
        "sh", "yaml", "yml", "ini", "conf", "properties", "gradle", "sql",
        "org", "adoc", "asciidoc", "wiki", "toml"
    ) || file.name.lowercase().let { it == "todo.txt" || it == "done.txt" || it == "readme" || it == "license" }

    BackHandler {
        when {
            appState.isVideoFullScreen -> appState.isVideoFullScreen = false
            appState.openInEditMode -> appState.openInEditMode = false
            else -> onClose()
        }
    }

    // If opened in Edit Mode, delegate directly to MarkorTextEngine or PhotoEditorEngine
    if (appState.openInEditMode) {
        when {
            isText -> {
                MarkorTextEngine(
                    file = file,
                    appState = appState,
                    onMenuClick = {
                        appState.openInEditMode = false
                        onClose()
                    },
                    modifier = modifier.fillMaxSize()
                )
                return
            }
            isImage -> {
                NativeImageViewerEditor(
                    file = file,
                    appState = appState,
                    onMenuClick = {
                        appState.openInEditMode = false
                        onClose()
                    },
                    modifier = modifier.fillMaxSize()
                )
                return
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Flush Heading (Action Bar) directly beneath Title Bar (Tab Bar) for non-PDF / non-fullscreen video
        if (!isPdf && !(isVideo && appState.isVideoFullScreen)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isText || isImage) {
                            FilledTonalButton(
                                onClick = { appState.openInEditMode = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (isArchive) {
                            FilledTonalButton(
                                onClick = {
                                    val fRef = file.fileRef
                                    onClose()
                                    appState.navigateTo(
                                        newPath = null,
                                        newUri = null,
                                        archiveFile = fRef,
                                        archiveUri = if (fRef == null) file.documentFileRef?.uri else null
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Browse Tab", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        IconButton(
                            onClick = { shareFiles(context, scope, listOf(file)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }

                        IconButton(
                            onClick = {
                                val uri = getUriForUniversalFile(context, file)
                                if (uri != null) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
                                        .onFailure { Toast.makeText(context, "No external app found", Toast.LENGTH_SHORT).show() }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Externally")
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isPdf -> {
                    PdfViewerEngine(
                        file = file,
                        appState = appState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isImage -> {
                    ImageFullPageView(file = file)
                }
                isAudio -> {
                    DedicatedAudioPlayerView(
                        file = file,
                        appState = appState,
                        onClose = onClose
                    )
                }
                isVideo -> {
                    DedicatedVideoPlayerView(
                        file = file,
                        appState = appState,
                        onClose = onClose
                    )
                }
                isArchive -> {
                    ArchiveFullPageView(
                        file = file,
                        appState = appState,
                        onClose = onClose
                    )
                }
                isText -> {
                    TextFullPageView(
                        file = file,
                        appState = appState
                    )
                }
                else -> {
                    UnsupportedFullPageView(
                        file = file,
                        appState = appState
                    )
                }
            }
        }
    }
}
