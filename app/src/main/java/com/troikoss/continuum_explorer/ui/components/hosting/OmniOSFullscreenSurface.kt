package com.troikoss.continuum_explorer.ui.components.hosting

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.troikoss.continuum_explorer.managers.SettingsManager

/**
 * Edge-to-edge DeX-style Fullscreen Interactive OmniOS Display Surface.
 *
 * Powers both:
 * 1. **Built-In Screen Cast** (`http://127.0.0.1:<port>/?mode=builtin_screen`)
 * 2. **App-to-App Receiver Mode** (`http://<hostIp>:<hostPort>/?mode=app_receiver`)
 *
 * Captures 100% of the available screen or freeform desktop window width & height with full
 * touch, mouse, keyboard, and file-download support.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OmniOSFullscreenSurface(
    url: String,
    sessionLabel: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val isFullScreenBarHidden by SettingsManager.isFullScreenTabBarHidden

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastRequestedUrl by remember { mutableStateOf(url) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var loadError by remember(url) { mutableStateOf<String?>(null) }
    var startupRetryCount by remember(url) { mutableStateOf(0) }
    var controlsExpanded by remember { mutableStateOf(true) }
    val isLocalLoopback = remember(url) { url.contains("127.0.0.1") || url.contains("localhost") }

    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onExit()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            } catch (_: Exception) {}
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .testTag("omnios_fullscreen_surface")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        setSupportZoom(false)
                        allowFileAccess = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            if (loadError == null) {
                                isLoading = false
                                startupRetryCount = 0
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                val maxRetries = if (isLocalLoopback) 8 else 3
                                if (startupRetryCount < maxRetries) {
                                    startupRetryCount++
                                    isLoading = true
                                    view?.postDelayed({
                                        if (webViewRef != null) {
                                            view.loadUrl(url)
                                        }
                                    }, if (isLocalLoopback) 350L else 500L)
                                } else {
                                    isLoading = false
                                    loadError = error?.description?.toString() ?: "Unable to connect to OmniOS host"
                                }
                            }
                        }
                    }
                    setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
                        enqueueOmniOSDownload(ctx, dlUrl, userAgent, contentDisposition, mimeType)
                    }
                    webViewRef = this
                    lastRequestedUrl = url
                    loadUrl(url)
                }
            },
            update = { wv ->
                if (lastRequestedUrl != url) {
                    lastRequestedUrl = url
                    loadError = null
                    isLoading = true
                    startupRetryCount = 0
                    wv.loadUrl(url)
                }
            }
        )

        // Loading indicator overlay while establishing connection
        AnimatedVisibility(
            visible = isLoading && loadError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh.copy(alpha = 0.94f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = sessionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface
                    )
                }
            }
        }

        // Error overlay if host stopped or unreachable
        if (loadError != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Disconnected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        text = loadError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onExit,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("omnios_error_exit_button")
                        ) {
                            Text("Exit")
                        }
                        Button(
                            onClick = {
                                loadError = null
                                isLoading = true
                                startupRetryCount = 0
                                webViewRef?.loadUrl(url)
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("omnios_error_retry_button")
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Minimal unobtrusive floating control pill (top-right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (controlsExpanded) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = cs.surfaceContainerHighest.copy(alpha = 0.86f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 6.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(cs.primary)
                        )
                        Text(
                            text = sessionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = {
                                loadError = null
                                isLoading = true
                                startupRetryCount = 0
                                webViewRef?.reload()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("omnios_surface_reload_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload OmniOS",
                                tint = cs.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { SettingsManager.toggleFullScreenTabBar() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("omnios_surface_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (isFullScreenBarHidden) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle immersive chrome",
                                tint = cs.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { controlsExpanded = false },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("omnios_surface_hide_pill_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Hide overlay pill",
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("omnios_surface_exit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit OmniOS display",
                                tint = cs.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = cs.surfaceContainerHighest.copy(alpha = 0.55f)
                ) {
                    IconButton(
                        onClick = { controlsExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("omnios_surface_show_pill_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Show OmniOS controls",
                            tint = cs.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun enqueueOmniOSDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
) {
    try {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .ifBlank { "omnios_download" }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) addRequestHeader("Cookie", cookie)
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        dm?.enqueue(request)
        Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
