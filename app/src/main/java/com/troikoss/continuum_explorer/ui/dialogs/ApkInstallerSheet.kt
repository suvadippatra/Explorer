package com.troikoss.continuum_explorer.ui.dialogs

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.troikoss.continuum_explorer.domain.logic.ApkMetadataExtractor
import com.troikoss.continuum_explorer.domain.logic.ApkPackageMetadata
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.shareFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * APK Package Info & Installer Sheet.
 * Shown whenever the user taps an `.apk` file in Explorer.
 * Displays rich package details and launches the Android Package Installer.
 * Never exposes a "Browse Archive" button or opens the APK as a folder tree.
 */
@Composable
fun ApkInstallerSheet(
    apkUniversalFile: UniversalFile,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apkFile: File? = apkUniversalFile.fileRef
    var metadata by remember { mutableStateOf<ApkPackageMetadata?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(apkFile) {
        if (apkFile != null && apkFile.exists()) {
            val extracted = withContext(Dispatchers.IO) {
                ApkMetadataExtractor.extractMetadata(context, apkFile)
            }
            metadata = extracted
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val iconDrawable = metadata?.appIcon
                        if (iconDrawable != null) {
                            Image(
                                painter = rememberAsyncImagePainter(model = iconDrawable),
                                contentDescription = metadata?.appName,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata?.appName ?: apkUniversalFile.name.substringBeforeLast('.'),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metadata?.packageName ?: "Android Application Package (.apk)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                }
            } else {
                val meta = metadata
                val dateStr = remember(apkUniversalFile.lastModified) {
                    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        .format(Date(apkUniversalFile.lastModified))
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ApkInfoRow("File Name", apkUniversalFile.name)
                            if (meta != null) {
                                ApkInfoRow("Version", "${meta.versionName} (Code ${meta.versionCode})")
                                if (meta.minSdkVersion > 0 || meta.targetSdkVersion > 0) {
                                    ApkInfoRow("SDK Target", "Min API ${meta.minSdkVersion} • Target API ${meta.targetSdkVersion}")
                                }
                                ApkInfoRow("Permissions", "${meta.permissionsCount} declared")
                                ApkInfoRow("SHA-256", meta.sha256Short, isMono = true)
                            }
                            ApkInfoRow("Package Size", Formatter.formatFileSize(context, apkUniversalFile.length))
                            ApkInfoRow("Modified", dateStr)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val f = apkFile
                    onDismiss()
                    if (f != null && f.exists()) {
                        ApkMetadataExtractor.launchSystemPackageInstaller(context, f)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Install with Package Installer", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        shareFiles(context, scope, listOf(apkUniversalFile))
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun ApkInfoRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
