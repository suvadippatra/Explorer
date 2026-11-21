package com.troikoss.continuum_explorer.server.omnios

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.troikoss.continuum_explorer.server.ServerManager
import com.troikoss.continuum_explorer.server.SyncPlayManager
import com.troikoss.continuum_explorer.server.SyncPlayRoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class OmniOSWindowState(
    val id: String,
    val appId: String,
    val title: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val zIndex: Int,
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val isFocused: Boolean = true,
    val closable: Boolean = true,
    val minimizable: Boolean = true,
    val appData: JSONObject = JSONObject()
) {
    fun toJson(needsContentRefresh: Boolean = false): JSONObject = JSONObject().apply {
        put("id", id)
        put("appId", appId)
        put("title", title)
        put("x", x)
        put("y", y)
        put("width", width)
        put("height", height)
        put("zIndex", zIndex)
        put("isMinimized", isMinimized)
        put("isMaximized", isMaximized)
        put("isFocused", isFocused)
        put("closable", closable)
        put("minimizable", minimizable)
        put("needsContentRefresh", needsContentRefresh)
        put("appData", appData)
    }
}

data class OmniOSNotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val time: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("time", time)
    }
}

data class OmniOSSystemStats(
    val cpuPercent: Int = 12,
    val ramSummary: String = "2.0 / 8.0 GB",
    val tempCelsius: Int = 34,
    val batteryPercent: Int = 100
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cpu", cpuPercent)
        put("ram", ramSummary)
        put("temp", tempCelsius)
        put("battery", batteryPercent)
    }
}

data class OmniOSTouchTelemetrySnapshot(
    val packetsProcessed: Long = 0L,
    val packetsBlocked: Long = 0L,
    val lastNormX: Float = 0f,
    val lastNormY: Float = 0f,
    val lastOpcode: Int = 0
)

/**
 * Server-authoritative state engine for OmniOS desktop sessions.
 *
 * Manages:
 * - Authoritative window tree (z-ordering, focus, drag/resize, close locks)
 * - Host Admin app whitelist enforcement (immediately closing revoked apps)
 * - Locked full-screen SyncPlay window injection (`closable = false`, `minimizable = true`)
 * - Real Android device diagnostics (RAM, battery, temperature)
 * - Binary touch input telemetry & server-side admin gating
 */
object OmniOSStateEngine {
    const val SYNCPLAY_WINDOW_ID = "win-syncplay-locked"

    private val windows = ConcurrentHashMap<String, OmniOSWindowState>()
    private val notifications = ArrayList<OmniOSNotificationItem>()
    private val zCounter = AtomicInteger(20)
    private val winCounter = AtomicInteger(1)
    private val touchProcessed = AtomicLong(0L)
    private val touchBlocked = AtomicLong(0L)

    @Volatile
    private var quickPanelWifi: Boolean = true
    @Volatile
    private var quickPanelBluetooth: Boolean = true
    @Volatile
    private var quickPanelVolume: Int = 80

    private val _systemStats = MutableStateFlow(OmniOSSystemStats())
    val systemStats: StateFlow<OmniOSSystemStats> = _systemStats.asStateFlow()

    private val _touchTelemetry = MutableStateFlow(OmniOSTouchTelemetrySnapshot())
    val touchTelemetry: StateFlow<OmniOSTouchTelemetrySnapshot> = _touchTelemetry.asStateFlow()

    @Volatile
    var onDeltaBroadcast: ((JSONArray) -> Unit)? = null

    @Volatile
    var onAdminConfigBroadcast: ((JSONObject) -> Unit)? = null

    init {
        notifications.add(
            OmniOSNotificationItem(
                id = "n-host-ready",
                title = "OmniOS Host Online",
                body = "Server-authoritative desktop session ready.",
                time = nowStamp()
            )
        )
    }

    fun resetSessionState() {
        windows.clear()
        zCounter.set(20)
        winCounter.set(1)
        synchronized(notifications) {
            notifications.clear()
            notifications.add(
                OmniOSNotificationItem(
                    id = "n-host-ready",
                    title = "OmniOS Host Online",
                    body = "Connected to Android Hosting session.",
                    time = nowStamp()
                )
            )
        }
        // Re-inject locked SyncPlay window if a SyncPlay session is already active
        val room = SyncPlayManager.roomState.value
        if (room.isActive) {
            syncWithSyncPlayState(room)
        }
    }

    fun handleAdminConfigUpdated(settings: OmniOSAdminSettings) {
        val patches = JSONArray()
        val toRemove = windows.values.filter { win ->
            // Keep locked SyncPlay window if SyncPlay itself is active
            if (win.id == SYNCPLAY_WINDOW_ID && SyncPlayManager.roomState.value.isActive) return@filter false
            win.appId !in settings.allowedApps
        }
        for (win in toRemove) {
            windows.remove(win.id)
            patches.put(JSONObject().apply {
                put("type", "remove")
                put("entity", "window")
                put("id", win.id)
            })
        }
        onAdminConfigBroadcast?.invoke(settings.toJson())
        if (patches.length() > 0) {
            onDeltaBroadcast?.invoke(patches)
        }
    }

    /**
     * Synchronizes OmniOS desktop windows with [SyncPlayManager.roomState].
     * When SyncPlay is active during Casting, opens/updates a maximized SyncPlay window
     * that can be minimized to the dock but CANNOT be closed by receivers.
     */
    fun syncWithSyncPlayState(roomState: SyncPlayRoomState) {
        if (!ServerManager.state.value.isCastingRunning) return

        val patches = JSONArray()
        if (roomState.isActive) {
            val existing = windows[SYNCPLAY_WINDOW_ID]
            val syncData = SyncPlayManager.buildStateJson("SYNC")
            if (existing == null) {
                unfocusAllWindows(patches)
                val win = OmniOSWindowState(
                    id = SYNCPLAY_WINDOW_ID,
                    appId = "syncplay",
                    title = "SyncPlay — ${roomState.mediaFileName}",
                    x = 0,
                    y = 32,
                    width = 1120,
                    height = 680,
                    zIndex = zCounter.incrementAndGet(),
                    isMinimized = false,
                    isMaximized = true,
                    isFocused = true,
                    closable = false,
                    minimizable = true,
                    appData = syncData
                )
                windows[win.id] = win
                patches.put(windowPatch("add", win, needsRefresh = true))
            } else {
                val updated = existing.copy(
                    title = "SyncPlay — ${roomState.mediaFileName}",
                    closable = false,
                    minimizable = true,
                    appData = syncData
                )
                windows[updated.id] = updated
                patches.put(windowPatch("update", updated, needsRefresh = false))
            }
        } else {
            val removed = windows.remove(SYNCPLAY_WINDOW_ID)
            if (removed != null) {
                patches.put(JSONObject().apply {
                    put("type", "remove")
                    put("entity", "window")
                    put("id", SYNCPLAY_WINDOW_ID)
                })
            }
        }

        if (patches.length() > 0) {
            onDeltaBroadcast?.invoke(patches)
        }
    }

    fun openAppWindow(
        appId: String,
        initialData: JSONObject = JSONObject(),
        customTitle: String? = null,
        forceMaximized: Boolean = false
    ): OmniOSWindowState? {
        val allowed = OmniOSAdminConfigStore.settings.value.allowedApps
        val isLockedSync = appId == "syncplay" && SyncPlayManager.roomState.value.isActive
        if (appId !in allowed && !isLockedSync) {
            pushNotification("App Restricted", "Host Admin has disabled this app.")
            return null
        }

        if (isLockedSync) {
            val existingSync = windows[SYNCPLAY_WINDOW_ID]
            if (existingSync != null) {
                val patches = JSONArray()
                unfocusAllWindows(patches)
                val restored = existingSync.copy(
                    isMinimized = false,
                    isMaximized = true,
                    isFocused = true,
                    zIndex = zCounter.incrementAndGet()
                )
                windows[restored.id] = restored
                patches.put(windowPatch("update", restored, needsRefresh = false))
                onDeltaBroadcast?.invoke(patches)
                return restored
            }
            syncWithSyncPlayState(SyncPlayManager.roomState.value)
            return windows[SYNCPLAY_WINDOW_ID]
        }

        val descriptor = OmniOSAppCatalog.findById(appId)
        val title = customTitle ?: descriptor?.name ?: appId.replaceFirstChar { it.uppercase() }
        val offset = ((winCounter.getAndIncrement() - 1) % 6) * 28
        val (defaultW, defaultH) = when (appId) {
            "files", "markdown_studio", "code_studio", "table_studio", "photo_studio" -> 860 to 560
            "file_converter", "video", "pdf" -> 800 to 540
            "syncplay" -> 960 to 620
            "settings", "sysmon" -> 740 to 520
            else -> 720 to 480
        }

        val patches = JSONArray()
        unfocusAllWindows(patches)

        val winId = "win-$appId-${System.currentTimeMillis()}-${winCounter.get()}"
        val win = OmniOSWindowState(
            id = winId,
            appId = appId,
            title = title,
            x = 70 + offset,
            y = 56 + offset,
            width = defaultW,
            height = defaultH,
            zIndex = zCounter.incrementAndGet(),
            isMinimized = false,
            isMaximized = forceMaximized,
            isFocused = true,
            closable = true,
            minimizable = true,
            appData = initialData
        )
        windows[winId] = win
        patches.put(windowPatch("add", win, needsRefresh = true))
        onDeltaBroadcast?.invoke(patches)
        return win
    }

    fun handleWindowAction(json: JSONObject) {
        val action = json.optString("action", "")
        val winId = json.optString("winId", "")
        val appId = json.optString("appId", "")

        if (action == "open" && appId.isNotBlank()) {
            val existing = windows.values.firstOrNull { it.appId == appId }
            if (existing != null && appId in setOf("settings", "sysmon", "syncplay")) {
                focusOrRestoreWindow(existing.id)
                return
            }
            val initData = json.optJSONObject("appData") ?: JSONObject()
            openAppWindow(appId, initData)
            return
        }

        val current = windows[winId] ?: return
        val patches = JSONArray()

        when (action) {
            "focus", "restore" -> {
                unfocusAllWindows(patches, excludeId = winId)
                val updated = current.copy(
                    isMinimized = false,
                    isFocused = true,
                    zIndex = zCounter.incrementAndGet()
                )
                windows[winId] = updated
                patches.put(windowPatch("update", updated, needsRefresh = false))
            }

            "minimize" -> {
                if (!current.minimizable) return
                val updated = current.copy(isMinimized = true, isFocused = false)
                windows[winId] = updated
                patches.put(windowPatch("update", updated, needsRefresh = false))
            }

            "maximize" -> {
                // Locked SyncPlay remains maximized while visible
                val nextMax = if (!current.closable) true else !current.isMaximized
                unfocusAllWindows(patches, excludeId = winId)
                val updated = current.copy(
                    isMaximized = nextMax,
                    isMinimized = false,
                    isFocused = true,
                    zIndex = zCounter.incrementAndGet()
                )
                windows[winId] = updated
                patches.put(windowPatch("update", updated, needsRefresh = false))
            }

            "close" -> {
                // Server-authoritative guard: locked SyncPlay windows cannot be closed by clients
                if (!current.closable) {
                    pushNotification("SyncPlay Active", "SyncPlay window can be minimized, but only the Host can end the session.")
                    return
                }
                windows.remove(winId)
                patches.put(JSONObject().apply {
                    put("type", "remove")
                    put("entity", "window")
                    put("id", winId)
                })
            }

            "move" -> {
                val nx = json.optInt("x", current.x).coerceAtLeast(0)
                val ny = json.optInt("y", current.y).coerceAtLeast(28)
                val updated = current.copy(x = nx, y = ny)
                windows[winId] = updated
                patches.put(windowPatch("update", updated, needsRefresh = false))
            }

            "resize" -> {
                val nw = json.optInt("width", current.width).coerceAtLeast(320)
                val nh = json.optInt("height", current.height).coerceAtLeast(220)
                val updated = current.copy(width = nw, height = nh)
                windows[winId] = updated
                patches.put(windowPatch("update", updated, needsRefresh = false))
            }
        }

        if (patches.length() > 0) {
            onDeltaBroadcast?.invoke(patches)
        }
    }

    fun handleAppAction(json: JSONObject) {
        val appId = json.optString("appId", "")
        val action = json.optString("action", "")

        when {
            appId == "system" && action == "toggle_quick_panel" -> {
                when (json.optString("feature")) {
                    "wifi" -> quickPanelWifi = !quickPanelWifi
                    "bluetooth" -> quickPanelBluetooth = !quickPanelBluetooth
                }
                emitQuickPanelDelta()
            }

            appId == "system" && action == "set_volume" -> {
                quickPanelVolume = json.optInt("volume", quickPanelVolume).coerceIn(0, 100)
                emitQuickPanelDelta()
            }

            appId == "system" && action == "dismiss_notification" -> {
                val id = json.optString("id", "")
                synchronized(notifications) {
                    notifications.removeAll { it.id == id }
                }
                val patches = JSONArray().put(JSONObject().apply {
                    put("type", "remove")
                    put("entity", "notification")
                    put("id", id)
                })
                onDeltaBroadcast?.invoke(patches)
            }

            appId == "system" && action == "clear_notifications" -> {
                val removedIds: List<String>
                synchronized(notifications) {
                    removedIds = notifications.map { it.id }
                    notifications.clear()
                }
                val patches = JSONArray()
                removedIds.forEach { id ->
                    patches.put(JSONObject().apply {
                        put("type", "remove")
                        put("entity", "notification")
                        put("id", id)
                    })
                }
                if (patches.length() > 0) onDeltaBroadcast?.invoke(patches)
            }

            appId == "files" && action == "open_shared_file" -> {
                val filePath = json.optString("path", "")
                val file = File(filePath)
                if (!OmniOSAdminConfigStore.isFilePermittedForCasting(file) || !file.isFile) {
                    pushNotification("Access Blocked", "File is outside host-shared paths.")
                    return
                }
                openSharedFileInAppropriateApp(file)
            }
        }
    }

    private fun openSharedFileInAppropriateApp(file: File) {
        val ext = file.extension.lowercase(Locale.ROOT)
        val encodedPath = java.net.URLEncoder.encode(file.absolutePath, "UTF-8")
        val rawUrl = "/api/files/raw?path=$encodedPath"

        when (ext) {
            "md", "markdown", "txt" -> {
                val content = try { file.readText().take(512_000) } catch (_: Exception) { "" }
                openAppWindow(
                    appId = "markdown_studio",
                    customTitle = "Markdown Studio — ${file.name}",
                    initialData = JSONObject().apply {
                        put("filePath", file.absolutePath)
                        put("fileName", file.name)
                        put("content", content)
                    }
                )
            }

            "kt", "java", "js", "ts", "jsx", "tsx", "py", "json", "xml", "html", "css", "sh", "yaml", "yml", "c", "cpp", "rs", "go", "sql" -> {
                val content = try { file.readText().take(512_000) } catch (_: Exception) { "" }
                openAppWindow(
                    appId = "code_studio",
                    customTitle = "Code Studio — ${file.name}",
                    initialData = JSONObject().apply {
                        put("filePath", file.absolutePath)
                        put("fileName", file.name)
                        put("language", ext)
                        put("content", content)
                    }
                )
            }

            "csv", "tsv" -> {
                val content = try { file.readText().take(512_000) } catch (_: Exception) { "" }
                openAppWindow(
                    appId = "table_studio",
                    customTitle = "Table Studio — ${file.name}",
                    initialData = JSONObject().apply {
                        put("filePath", file.absolutePath)
                        put("fileName", file.name)
                        put("content", content)
                    }
                )
            }

            "png", "jpg", "jpeg", "webp", "gif", "bmp" -> {
                openAppWindow(
                    appId = "photo_studio",
                    customTitle = "Photo Studio — ${file.name}",
                    initialData = JSONObject().apply {
                        put("filePath", file.absolutePath)
                        put("fileName", file.name)
                        put("imageUrl", rawUrl)
                    }
                )
            }

            "mp4", "mkv", "webm", "mov", "m4v" -> {
                openAppWindow(
                    appId = "video",
                    customTitle = "Video — ${file.name}",
                    initialData = JSONObject().apply {
                        put("videoSrc", rawUrl)
                        put("fileName", file.name)
                    }
                )
            }

            "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus" -> {
                openAppWindow(
                    appId = "music",
                    customTitle = "Music — ${file.name}",
                    initialData = JSONObject().apply {
                        put("audioSrc", rawUrl)
                        put("currentTrack", JSONObject().apply {
                            put("title", file.nameWithoutExtension)
                            put("artist", "Host Shared Audio")
                        })
                    }
                )
            }

            "pdf" -> {
                openAppWindow(
                    appId = "pdf",
                    customTitle = "PDF — ${file.name}",
                    initialData = JSONObject().apply {
                        put("docName", file.name)
                        put("pdfUrl", rawUrl)
                    }
                )
            }

            else -> {
                openAppWindow(
                    appId = "file_converter",
                    customTitle = "File Converter — ${file.name}",
                    initialData = JSONObject().apply {
                        put("filePath", file.absolutePath)
                        put("fileName", file.name)
                    }
                )
            }
        }
    }

    fun recordTouchPacket(opcode: Int, pointerId: Int, normX: Float, normY: Float): Boolean {
        val allowed = OmniOSAdminConfigStore.settings.value.touchInputEnabled
        if (!allowed) {
            val blocked = touchBlocked.incrementAndGet()
            _touchTelemetry.update {
                it.copy(packetsBlocked = blocked, lastOpcode = opcode)
            }
            return false
        }
        val processed = touchProcessed.incrementAndGet()
        _touchTelemetry.update {
            it.copy(
                packetsProcessed = processed,
                lastNormX = normX,
                lastNormY = normY,
                lastOpcode = opcode
            )
        }
        return true
    }

    fun pushNotification(title: String, body: String) {
        val item = OmniOSNotificationItem(
            id = "n-${System.currentTimeMillis()}-${(100..999).random()}",
            title = title,
            body = body,
            time = nowStamp()
        )
        synchronized(notifications) {
            notifications.add(0, item)
            if (notifications.size > 24) {
                notifications.removeAt(notifications.lastIndex)
            }
        }
        val patches = JSONArray().put(JSONObject().apply {
            put("type", "add")
            put("entity", "notification")
            put("id", item.id)
            put("data", item.toJson())
        })
        onDeltaBroadcast?.invoke(patches)
    }

    fun refreshSystemMetrics(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val totalGb = (memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).coerceAtLeast(0.5)
            val usedGb = ((memInfo.totalMem - memInfo.availMem).toDouble() / (1024.0 * 1024.0 * 1024.0)).coerceAtLeast(0.1)
            val ramStr = String.format(Locale.US, "%.1f / %.1f GB", usedGb, totalGb)
            val memPct = ((usedGb / totalGb) * 100.0).toInt().coerceIn(5, 99)

            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 95
            val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 340) ?: 340
            val tempC = (rawTemp / 10).coerceIn(15, 85)

            val stats = OmniOSSystemStats(
                cpuPercent = memPct,
                ramSummary = ramStr,
                tempCelsius = tempC,
                batteryPercent = pct
            )
            _systemStats.value = stats

            val patches = JSONArray().put(JSONObject().apply {
                put("type", "update")
                put("entity", "systemStats")
                put("id", "host")
                put("data", stats.toJson())
            })
            onDeltaBroadcast?.invoke(patches)
        } catch (_: Exception) {}
    }

    fun buildFullSyncJson(): JSONObject {
        val admin = OmniOSAdminConfigStore.settings.value
        val winObj = JSONObject()
        windows.values.forEach { w ->
            winObj.put(w.id, w.toJson(needsContentRefresh = true))
        }
        val notifArr = JSONArray()
        synchronized(notifications) {
            notifications.forEach { notifArr.put(it.toJson()) }
        }
        return JSONObject().apply {
            put("adminConfig", admin.toJson())
            put("windows", winObj)
            put("notifications", notifArr)
            put("quickPanel", buildQuickPanelJson(admin))
            put("systemStats", _systemStats.value.toJson())
            put("syncPlay", SyncPlayManager.buildStateJson("STATE"))
        }
    }

    private fun focusOrRestoreWindow(winId: String) {
        val current = windows[winId] ?: return
        val patches = JSONArray()
        unfocusAllWindows(patches, excludeId = winId)
        val updated = current.copy(
            isMinimized = false,
            isFocused = true,
            zIndex = zCounter.incrementAndGet()
        )
        windows[winId] = updated
        patches.put(windowPatch("update", updated, needsRefresh = false))
        onDeltaBroadcast?.invoke(patches)
    }

    private fun unfocusAllWindows(patches: JSONArray, excludeId: String? = null) {
        for (win in windows.values) {
            if (win.id != excludeId && win.isFocused) {
                val unfocused = win.copy(isFocused = false)
                windows[win.id] = unfocused
                patches.put(windowPatch("update", unfocused, needsRefresh = false))
            }
        }
    }

    private fun emitQuickPanelDelta() {
        val admin = OmniOSAdminConfigStore.settings.value
        val patches = JSONArray().put(JSONObject().apply {
            put("type", "update")
            put("entity", "quickPanel")
            put("id", "main")
            put("data", buildQuickPanelJson(admin))
        })
        onDeltaBroadcast?.invoke(patches)
    }

    private fun buildQuickPanelJson(admin: OmniOSAdminSettings): JSONObject = JSONObject().apply {
        put("wifi", quickPanelWifi)
        put("bluetooth", quickPanelBluetooth)
        put("volume", quickPanelVolume)
        put("brightness", admin.brightness)
        put("darkMode", admin.darkMode)
    }

    private fun windowPatch(type: String, win: OmniOSWindowState, needsRefresh: Boolean): JSONObject =
        JSONObject().apply {
            put("type", type)
            put("entity", "window")
            put("id", win.id)
            put("data", win.toJson(needsContentRefresh = needsRefresh))
        }

    private fun nowStamp(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
