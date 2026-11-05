package com.troikoss.continuum_explorer.ui.preview

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.editor.text.DocumentPreviewEngine
import com.troikoss.continuum_explorer.ui.editor.text.FormatRegistry
import com.troikoss.continuum_explorer.ui.editor.text.SyntaxHighlighter
import com.troikoss.continuum_explorer.ui.editor.text.TextFormatType
import com.troikoss.continuum_explorer.ui.editor.text.TodoTxtInteractiveView
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-page Image Viewer with pinch-to-zoom, double-tap zoom, pan, and 90° rotation.
 */
@Composable
fun ImageFullPageView(file: UniversalFile) {
    val context = LocalContext.current
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    var rotation by remember(file) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .clipToBounds()
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .pointerInput(file) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(file.fileRef ?: file.documentFileRef?.uri ?: file)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            loading = {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation
                )
        )

        // Floating Image Control Pill
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        scale = (scale - 0.5f).coerceIn(1f, 6f)
                        if (scale <= 1.05f) offset = Offset.Zero
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { scale = (scale + 0.5f).coerceIn(1f, 6f) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { rotation = (rotation + 90f) % 360f },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                if (scale > 1.05f || rotation != 0f) {
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            rotation = 0f
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Full-page Read-Only Document & Code Viewer with Markor WebView Rendering
 * for Markdown/Org/AsciiDoc/CSV, Interactive Task View for Todo.txt,
 * and Syntax-Highlighted Code View with a quick "Edit in Markor" button.
 */
@Composable
fun TextFullPageView(
    file: UniversalFile,
    appState: FileExplorerState? = null
) {
    var textContent by remember(file) { mutableStateOf<String?>(null) }
    var isLoading by remember(file) { mutableStateOf(true) }
    val formatType = remember(file.name) { FormatRegistry.detectFormat(file.name) }
    val supportsRichPreview = remember(formatType) {
        formatType == TextFormatType.MARKDOWN ||
            formatType == TextFormatType.ORG ||
            formatType == TextFormatType.ASCIIDOC ||
            formatType == TextFormatType.CSV ||
            formatType == TextFormatType.TODO_TXT
    }
    var showRawSource by remember(file) { mutableStateOf(!supportsRichPreview) }

    LaunchedEffect(file) {
        isLoading = true
        textContent = withContext(Dispatchers.IO) {
            try {
                file.provider.openInput(file.providerId).bufferedReader().use { reader ->
                    val buffer = CharArray(200_000)
                    val read = reader.read(buffer)
                    if (read > 0) String(buffer, 0, read) else ""
                }
            } catch (e: Exception) {
                "Error reading file: ${e.localizedMessage}"
            }
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Quick Action Bar for switching Rendered vs Raw Source or launching Markor Editor
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = formatType.name.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (supportsRichPreview) {
                        FilterChip(
                            selected = !showRawSource,
                            onClick = { showRawSource = false },
                            label = { Text("Rendered View", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = showRawSource,
                            onClick = { showRawSource = true },
                            label = { Text("Raw Source", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (appState != null) {
                    FilledTonalButton(
                        onClick = {
                            appState.viewingFile = file
                            appState.openInEditMode = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit in Markor", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                !showRawSource && formatType == TextFormatType.TODO_TXT -> {
                    TodoTxtInteractiveView(
                        rawText = textContent ?: "",
                        onTextChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !showRawSource && supportsRichPreview -> {
                    DocumentPreviewEngine(
                        rawText = textContent ?: "",
                        formatType = formatType,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    val highlighted = remember(textContent, formatType) {
                        SyntaxHighlighter.highlightText(textContent ?: "", formatType)
                    }
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = highlighted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnsupportedFullPageView(
    file: UniversalFile,
    appState: FileExplorerState
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Size: ${appState.formatSize(file.length)} • Modified: ${appState.formatDate(file.lastModified)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    appState.viewingFile = file
                    appState.openInEditMode = true
                }
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open as Text")
            }

            Button(
                onClick = {
                    val uri = getUriForUniversalFile(context, file)
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "Open with"))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No external app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Externally")
            }
        }
    }
}
