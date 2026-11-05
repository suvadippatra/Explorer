package com.troikoss.continuum_explorer.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Base64 Code Generator & Inspector component.
 * Replaces the old Hex viewer. Strictly limited to images and smaller audio files (< 2 MB).
 * Displays a friendly size/type warning for larger or unsupported files to prevent app crashes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Base64CodeViewer(
    file: File?,
    bytes: ByteArray = byteArrayOf(),
    modifier: Modifier = Modifier,
    onChooseFile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var base64Result by remember { mutableStateOf<String?>(null) }
    var mimeType by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var includeDataUriPrefix by remember { mutableStateOf(true) }

    val maxFileSizeBytes = 2 * 1024 * 1024L // 2.0 MB limit

    val fileLength = remember(file, bytes) {
        if (file != null && file.exists()) file.length() else bytes.size.toLong()
    }

    val extension = remember(file) {
        file?.extension?.lowercase(Locale.ROOT) ?: ""
    }

    val isImage = remember(extension) {
        extension in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico")
    }

    val isAudio = remember(extension) {
        extension in setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "wma")
    }

    val isSupportedType = isImage || isAudio

    val detectedMime = remember(extension) {
        when (extension) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "opus" -> "audio/opus"
            else -> if (isImage) "image/$extension" else if (isAudio) "audio/$extension" else "application/octet-stream"
        }
    }

    LaunchedEffect(file, bytes, isSupportedType, fileLength) {
        if (file == null && bytes.isEmpty()) {
            base64Result = null
            return@LaunchedEffect
        }

        if (!isSupportedType || fileLength > maxFileSizeBytes) {
            base64Result = null
            return@LaunchedEffect
        }

        isLoading = true
        mimeType = detectedMime
        withContext(Dispatchers.IO) {
            try {
                val data = if (file != null && file.exists()) {
                    file.readBytes()
                } else {
                    bytes
                }
                val encoded = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                base64Result = encoded
            } catch (_: Exception) {
                base64Result = null
            } finally {
                isLoading = false
            }
        }
    }

    val formattedSize = remember(fileLength) {
        val mb = fileLength / (1024.0 * 1024.0)
        if (mb >= 0.1) String.format(Locale.US, "%.2f MB", mb)
        else String.format(Locale.US, "%.1f KB", fileLength / 1024.0)
    }

    val displayString = remember(base64Result, includeDataUriPrefix, mimeType) {
        if (base64Result == null) ""
        else if (includeDataUriPrefix) "data:$mimeType;base64,$base64Result"
        else base64Result!!
    }

    fun copyToClipboard() {
        if (displayString.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Base64 Code", displayString)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Base64 code copied to clipboard (${displayString.length} chars)", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Base64 Code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (file != null) "Base64 Code: ${file.name}" else "Base64 Code Studio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (file != null) {
                            Text(
                                text = "Type: ${detectedMime.uppercase()} • Size: $formattedSize",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (onChooseFile != null) {
                    FilledTonalButton(
                        onClick = onChooseFile,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose File", fontSize = 12.sp)
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        when {
            file == null && bytes.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Base64 Code Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Generate clean Base64 data strings for images (PNG, JPG, SVG, WebP) and smaller audio files (MP3, WAV, OGG) up to 2.0 MB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (onChooseFile != null) {
                                Button(
                                    onClick = onChooseFile,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select Image or Audio File")
                                }
                            }
                        }
                    }
                }
            }

            fileLength > maxFileSizeBytes -> {
                // EXCEEDED SIZE LIMIT WARNING (PREVENTS APP CRASH / LAG)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Too Large",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )

                            Text(
                                text = "File is too long to show Base64 code",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Selected file size ($formattedSize) exceeds the maximum allowed limit of 2.0 MB.\nBase64 code rendering for massive files is disabled to keep the app smooth, fast, and crash-free.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (onChooseFile != null) {
                                OutlinedButton(
                                    onClick = onChooseFile,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Choose Smaller File")
                                }
                            }
                        }
                    }
                }
            }

            !isSupportedType -> {
                // UNSUPPORTED FILE TYPE WARNING
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Unsupported",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )

                            Text(
                                text = "Base64 Code Limited to Images & Audio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "The file '${file?.name}' (extension: .$extension) is not an image or audio format.\nBase64 code generation is strictly limited to image and audio files under 2 MB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (onChooseFile != null) {
                                Button(
                                    onClick = onChooseFile,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Choose Image or Audio File")
                                }
                            }
                        }
                    }
                }
            }

            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Generating Base64 Code...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            else -> {
                // SUCCESSFUL BASE64 RENDER
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Controls Row: Data URI Toggle & Copy Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = includeDataUriPrefix,
                                onClick = { includeDataUriPrefix = !includeDataUriPrefix },
                                label = { Text("Data URI Prefix", fontSize = 12.sp) },
                                leadingIcon = if (includeDataUriPrefix) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }

                        Button(
                            onClick = { copyToClipboard() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Base64", fontSize = 13.sp)
                        }
                    }

                    // Metadata Summary Bar
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MIME: $mimeType",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Length: ${displayString.length} chars",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Base64 Text Field Box
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = displayString,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility overload for HexViewer
 */
@Composable
fun HexViewer(
    file: File,
    bytes: ByteArray,
    modifier: Modifier = Modifier
) {
    Base64CodeViewer(
        file = file,
        bytes = bytes,
        modifier = modifier
    )
}
