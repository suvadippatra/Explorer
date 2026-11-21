package com.troikoss.continuum_explorer.ui.components.hosting

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.troikoss.continuum_explorer.server.UnifiedServerState
import java.io.File

/**
 * Ultra-Clean Tonal Flat Material 3 HTTP Website Hosting Workbench.
 * Hosts an unzipped website folder or single HTML file with optional PIN gate and live preview.
 */
@Composable
fun HostingHttpTab(
    isWideScreen: Boolean,
    unifiedState: UnifiedServerState,
    websiteTargetPath: String,
    httpWebUrl: String,
    httpRequireAuth: Boolean,
    httpAccessPin: String,
    onToggleHttp: (Boolean) -> Unit,
    onRestartHttp: () -> Unit,
    onShowQr: (title: String, content: String, label: String) -> Unit,
    onSelectSource: () -> Unit,
    onClearSource: () -> Unit,
    onSecurityChange: (Boolean, String) -> Unit
) {
    val controlsPane: @Composable () -> Unit = {
        HttpControlsCard(
            unifiedState = unifiedState,
            websiteTargetPath = websiteTargetPath,
            httpWebUrl = httpWebUrl,
            httpRequireAuth = httpRequireAuth,
            httpAccessPin = httpAccessPin,
            onToggleHttp = onToggleHttp,
            onRestartHttp = onRestartHttp,
            onShowQr = { onShowQr("HTTP Website QR", httpWebUrl, httpWebUrl) },
            onSelectSource = onSelectSource,
            onClearSource = onClearSource,
            onSecurityChange = onSecurityChange
        )
    }

    val previewPane: @Composable () -> Unit = {
        HttpLivePreviewCard(
            isHttpRunning = unifiedState.isHttpRunning,
            previewUrl = "http://127.0.0.1:${unifiedState.http.port}",
            websiteTargetPath = websiteTargetPath
        )
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(0.95f)) { controlsPane() }
            Box(modifier = Modifier.weight(1.25f)) { previewPane() }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            controlsPane()
            previewPane()
        }
    }
}

@Composable
private fun HttpControlsCard(
    unifiedState: UnifiedServerState,
    websiteTargetPath: String,
    httpWebUrl: String,
    httpRequireAuth: Boolean,
    httpAccessPin: String,
    onToggleHttp: (Boolean) -> Unit,
    onRestartHttp: () -> Unit,
    onShowQr: () -> Unit,
    onSelectSource: () -> Unit,
    onClearSource: () -> Unit,
    onSecurityChange: (Boolean, String) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val isRunning = unifiedState.isHttpRunning
    val sourceFile = remember(websiteTargetPath) { websiteTargetPath.takeIf { it.isNotBlank() }?.let { File(it) } }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) cs.primary else cs.outlineVariant)
                    )
                    Text(
                        text = "HTTP Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    if (isRunning) {
                        Text(
                            text = ":${unifiedState.http.port}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        IconButton(
                            onClick = onRestartHttp,
                            modifier = Modifier.testTag("http_restart_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart HTTP", tint = cs.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = onToggleHttp,
                        modifier = Modifier.testTag("http_power_switch")
                    )
                }
            }

            // Live HTTP URL Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = httpWebUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (isRunning) cs.primary else cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(httpWebUrl))
                            Toast.makeText(context, "Copied HTTP URL", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("http_copy_url_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy HTTP URL", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onShowQr,
                        modifier = Modifier.testTag("http_qr_button")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Show HTTP QR", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpWebUrl)))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.testTag("http_open_browser_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser", modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.35f))

            // Website Folder / HTML File Source
            Text(
                text = "Website Source",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sourceFile?.name ?: "No folder or HTML selected",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (sourceFile != null) FontFamily.Monospace else FontFamily.Default,
                        color = if (sourceFile != null) cs.onSurface else cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (sourceFile != null) {
                        IconButton(
                            onClick = onClearSource,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("http_clear_source_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear website source", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSelectSource,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("http_select_source_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select Folder / HTML")
                }
                if (sourceFile != null) {
                    OutlinedButton(
                        onClick = onClearSource,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Clear")
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.35f))

            // Optional Access PIN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Access PIN", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = httpRequireAuth,
                    onCheckedChange = { onSecurityChange(it, httpAccessPin) },
                    modifier = Modifier.testTag("http_pin_switch")
                )
            }

            if (httpRequireAuth) {
                OutlinedTextField(
                    value = httpAccessPin,
                    onValueChange = { onSecurityChange(httpRequireAuth, it.take(12)) },
                    label = { Text("PIN") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("http_pin_input")
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HttpLivePreviewCard(
    isHttpRunning: Boolean,
    previewUrl: String,
    websiteTargetPath: String
) {
    val cs = MaterialTheme.colorScheme
    var reloadNonce by remember { mutableIntStateOf(0) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                IconButton(
                    onClick = { reloadNonce++ },
                    enabled = isHttpRunning,
                    modifier = Modifier.testTag("http_preview_reload_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload preview", modifier = Modifier.size(18.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (isHttpRunning) {
                    var previewWebView by remember { mutableStateOf<WebView?>(null) }
                    DisposableEffect(Unit) {
                        onDispose {
                            try {
                                previewWebView?.stopLoading()
                                previewWebView?.destroy()
                            } catch (_: Exception) {}
                            previewWebView = null
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                }
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                previewWebView = this
                                loadUrl(previewUrl)
                            }
                        },
                        update = { wv ->
                            val target = "$previewUrl?_r=$reloadNonce&p=${websiteTargetPath.hashCode()}"
                            if (wv.tag != target) {
                                wv.tag = target
                                wv.loadUrl(previewUrl)
                            }
                        }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Start HTTP Server to preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
