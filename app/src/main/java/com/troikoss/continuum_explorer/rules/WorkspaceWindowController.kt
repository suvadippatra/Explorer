package com.troikoss.continuum_explorer.rules

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.activities.MainActivity
import java.io.File
import java.util.UUID

/**
 * Holds state for a floating resizable panel when WorkspaceWindowMode.FLOATING_PANELS_DEX_TABLET_TABS_MOBILE
 * is active on Tablet or DeX.
 */
data class FloatingPanelSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val file: UniversalFile?,
    val openInEditMode: Boolean,
    val toolId: String? = null
)

/**
 * Runtime controller enforcing WorkspaceWindowMode rules across DeX, Tablet, and Mobile.
 */
object WorkspaceWindowController {

    var activeFloatingPanel by mutableStateOf<FloatingPanelSession?>(null)
        private set

    fun closeFloatingPanel() {
        activeFloatingPanel = null
    }

    /**
     * Detects whether the device is currently running in Samsung DeX, Desktop Freeform,
     * or a wide desktop display (> 1000dp).
     */
    fun isDesktopOrDexEnvironment(context: Context): Boolean {
        val config = context.resources.configuration
        val isDeskUiMode = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK
        val isDeXEnabled = try {
            val semDesktopMode = config.javaClass.getField("semDesktopModeEnabled").getInt(config)
            val semDesktopEnabledConst = config.javaClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(null)
            semDesktopMode == semDesktopEnabledConst
        } catch (_: Exception) {
            false
        }
        return isDeskUiMode || isDeXEnabled || config.screenWidthDp >= 960
    }

    fun isTabletOrWideScreen(context: Context): Boolean {
        return context.resources.configuration.screenWidthDp >= 600
    }

    /**
     * Resolves whether a Mini-App or file editor should open as:
     * - A Floating Resizable Panel (Mode 3 on Tablet/DeX)
     * - A Separate OS Multi-Window (Mode 1 on DeX when requested)
     * - An Animated In-App Tab with `originTabIndex` restoration (Default on Mobile/Tablet & Mode 2)
     */
    fun openFloatingPanelIfConfigured(
        context: Context,
        title: String,
        file: UniversalFile?,
        openInEditMode: Boolean,
        toolId: String? = null
    ): Boolean {
        val mode = SettingsManager.workspaceWindowMode.value
        if (mode == WorkspaceWindowMode.FLOATING_PANELS_DEX_TABLET_TABS_MOBILE && isTabletOrWideScreen(context)) {
            activeFloatingPanel = FloatingPanelSession(
                title = title,
                file = file,
                openInEditMode = openInEditMode,
                toolId = toolId
            )
            return true
        }
        return false
    }

    /**
     * Launches a separate freeform multi-instance Android window on DeX / Desktop environments.
     */
    fun launchSeparateDeXWindow(
        context: Context,
        file: File? = null,
        openInEditMode: Boolean = false,
        isVibeArea: Boolean = false,
        isFtpServer: Boolean = false
    ): Boolean {
        return try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                data = Uri.fromParts("workspace", UUID.randomUUID().toString(), null)
                if (file != null) {
                    putExtra("openFilePath", file.absolutePath)
                    putExtra("openInEditMode", openInEditMode)
                }
                if (isVibeArea) putExtra("isVibeArea", true)
                if (isFtpServer) putExtra("isFtpServer", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val options = ActivityOptions.makeBasic()
                val metrics = context.resources.displayMetrics
                val width = (metrics.widthPixels * 0.72f).toInt().coerceAtLeast(600)
                val height = (metrics.heightPixels * 0.78f).toInt().coerceAtLeast(500)
                val left = ((metrics.widthPixels - width) / 2).coerceAtLeast(24)
                val top = ((metrics.heightPixels - height) / 2).coerceAtLeast(24)
                options.launchBounds = Rect(left, top, left + width, top + height)
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
