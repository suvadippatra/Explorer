package com.troikoss.continuum_explorer.server.omnios

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
    val isPresentationCapable: Boolean
)

/**
 * Manages Wired (USB-C DisplayPort / HDMI secondary Presentation) and Wireless Display
 * (Miracast / Android System Cast) projection for OmniOS.
 *
 * Neither Wired Cast nor Wireless Display requires an extra approval barrier.
 */
object OmniOSDisplayProjectionManager {
    private const val TAG = "OmniOSDisplayProjection"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var displayManager: DisplayManager? = null

    @Volatile
    private var displayListener: DisplayManager.DisplayListener? = null

    @Volatile
    private var activePresentation: OmniOSExternalPresentation? = null

    private val _externalDisplays = MutableStateFlow<List<ExternalDisplayInfo>>(emptyList())
    val externalDisplays: StateFlow<List<ExternalDisplayInfo>> = _externalDisplays.asStateFlow()

    private val _activeProjectedDisplayId = MutableStateFlow<Int?>(null)
    val activeProjectedDisplayId: StateFlow<Int?> = _activeProjectedDisplayId.asStateFlow()

    fun init(context: Context) {
        if (displayManager != null) {
            refreshExternalDisplays()
            return
        }
        val dm = context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        displayManager = dm

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                refreshExternalDisplays()
            }

            override fun onDisplayRemoved(displayId: Int) {
                if (_activeProjectedDisplayId.value == displayId) {
                    stopWiredProjection()
                }
                refreshExternalDisplays()
            }

            override fun onDisplayChanged(displayId: Int) {
                refreshExternalDisplays()
            }
        }
        displayListener = listener
        dm.registerDisplayListener(listener, mainHandler)
        refreshExternalDisplays()
    }

    fun refreshExternalDisplays() {
        val dm = displayManager ?: return
        val presentationIds = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .map { it.displayId }
            .toSet()

        val externals = dm.displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .map { display ->
                val mode = display.mode
                ExternalDisplayInfo(
                    displayId = display.displayId,
                    name = display.name?.takeIf { it.isNotBlank() } ?: "External Display #${display.displayId}",
                    width = mode?.physicalWidth ?: 1920,
                    height = mode?.physicalHeight ?: 1080,
                    refreshRateHz = mode?.refreshRate ?: display.refreshRate,
                    isPresentationCapable = display.displayId in presentationIds
                )
            }

        _externalDisplays.value = externals
    }

    /**
     * Projects the OmniOS desktop directly onto an attached USB-C / HDMI / Wireless Presentation
     * display with zero approval gate. Pass an Activity context so Android allows attaching the
     * secondary Presentation window cleanly.
     */
    fun startWiredProjection(
        activityContext: Context,
        displayId: Int,
        port: Int = OmniOSHostServer.activePort,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        val dm = displayManager ?: (activityContext.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
        val targetDisplay = dm?.getDisplay(displayId)
        if (targetDisplay == null) {
            onResult?.invoke(Result.failure(IllegalStateException("Display #$displayId is no longer connected")))
            return
        }

        val url = "http://127.0.0.1:$port/?mode=wired_display"
        mainHandler.post {
            try {
                dismissActivePresentationOnMain()
                val presentation = OmniOSExternalPresentation(activityContext, targetDisplay, url)
                presentation.setOnDismissListener {
                    if (activePresentation === presentation) {
                        activePresentation = null
                        _activeProjectedDisplayId.value = null
                    }
                }
                presentation.show()
                activePresentation = presentation
                _activeProjectedDisplayId.value = displayId
                Log.i(TAG, "Started wired Presentation on display #$displayId (${targetDisplay.name})")
                onResult?.invoke(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start wired presentation: ${e.message}", e)
                _activeProjectedDisplayId.value = null
                onResult?.invoke(Result.failure(e))
            }
        }
    }

    fun stopWiredProjection() {
        mainHandler.post {
            dismissActivePresentationOnMain()
        }
    }

    private fun dismissActivePresentationOnMain() {
        val current = activePresentation
        activePresentation = null
        _activeProjectedDisplayId.value = null
        try {
            current?.dismiss()
        } catch (_: Exception) {}
    }

    /**
     * Opens the system Wireless Display / Miracast / Cast picker (no app approval required).
     */
    fun openWirelessDisplaySettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try next fallback
            }
        }
        return false
    }

    /**
     * Generates the USB ADB reverse command for wired PC/desktop browser casting over USB-C.
     */
    fun getAdbReverseCommand(port: Int = OmniOSHostServer.activePort): String {
        return "adb reverse tcp:$port tcp:$port"
    }

    private class OmniOSExternalPresentation(
        outerContext: Context,
        display: Display,
        private val targetUrl: String
    ) : Presentation(outerContext, display) {

        private var webView: WebView? = null

        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val wv = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(targetUrl)
            }

            webView = wv
            root.addView(wv)
            setContentView(root)
        }

        override fun onStop() {
            super.onStop()
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Exception) {}
            webView = null
        }
    }
}
