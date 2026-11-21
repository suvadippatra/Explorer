package com.troikoss.continuum_explorer.server.omnios

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class OmniOSAppCategory {
    MY_SUITE,
    OMNIOS_CORE
}

data class OmniOSAppDescriptor(
    val id: String,
    val name: String,
    val category: OmniOSAppCategory,
    val defaultEnabled: Boolean = true
)

/**
 * Persisted Host Admin Governance configuration for OmniOS Casting.
 * Strictly authoritative on the Android host device and pushed in real time
 * over WebSocket (`ADMIN_CONFIG`) to all connected OmniOS displays.
 */
data class OmniOSAdminSettings(
    val allowedApps: Set<String> = OmniOSAppCatalog.defaultAllowedIds,
    val touchInputEnabled: Boolean = true,
    val audioRoute: String = "receiver", // "receiver" | "host" | "both"
    val brightness: Int = 100,           // 15..100 software luminance
    val theme: String = "dex",           // "dex" | "macos" | "windows" | "linux"
    val darkMode: Boolean = true,
    val animationDurationMs: Int = 300,
    val requireHostApproval: Boolean = false,
    val settingsVisibility: Map<String, Boolean> = defaultSettingsVisibility(),
    val sharedCastPaths: List<String> = emptyList(),
    val customIcons: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("touchInputEnabled", touchInputEnabled)
        put("audioRoute", audioRoute)
        put("brightness", brightness)
        put("theme", theme)
        put("darkMode", darkMode)
        put("animationDuration", animationDurationMs)
        put("requireHostApproval", requireHostApproval)
        put("allowedApps", JSONArray(allowedApps.toList()))
        put("settingsVisibility", JSONObject().apply {
            settingsVisibility.forEach { (k, v) -> put(k, v) }
        })
        put("sharedPathsCount", sharedCastPaths.size)
        if (customIcons.isNotEmpty()) {
            put("customIcons", JSONObject().apply {
                customIcons.forEach { (k, v) -> put(k, v) }
            })
        }
    }

    companion object {
        fun defaultSettingsVisibility(): Map<String, Boolean> = mapOf(
            "appearance" to true,
            "animation" to true,
            "sound" to true,
            "storage" to true,
            "host" to true
        )
    }
}

/**
 * Authoritative registry of My Suite mini-apps and OmniOS core desktop apps
 * available for Host Admin whitelisting inside OmniOS.
 */
object OmniOSAppCatalog {
    val allApps: List<OmniOSAppDescriptor> = listOf(
        // My Suite Mini-Apps
        OmniOSAppDescriptor("files", "File Manager", OmniOSAppCategory.MY_SUITE),
        OmniOSAppDescriptor("markdown_studio", "Markdown Studio", OmniOSAppCategory.MY_SUITE),
        OmniOSAppDescriptor("code_studio", "Code Studio", OmniOSAppCategory.MY_SUITE),
        OmniOSAppDescriptor("table_studio", "Table Studio", OmniOSAppCategory.MY_SUITE),
        OmniOSAppDescriptor("photo_studio", "Photo Studio", OmniOSAppCategory.MY_SUITE),
        OmniOSAppDescriptor("file_converter", "File Converter", OmniOSAppCategory.MY_SUITE),
        // OmniOS Core Desktop Apps
        OmniOSAppDescriptor("syncplay", "SyncPlay", OmniOSAppCategory.OMNIOS_CORE),
        OmniOSAppDescriptor("video", "Video Player", OmniOSAppCategory.OMNIOS_CORE),
        OmniOSAppDescriptor("music", "Music", OmniOSAppCategory.OMNIOS_CORE),
        OmniOSAppDescriptor("pdf", "PDF Reader", OmniOSAppCategory.OMNIOS_CORE),
        OmniOSAppDescriptor("terminal", "Terminal", OmniOSAppCategory.OMNIOS_CORE),
        OmniOSAppDescriptor("sysmon", "System Monitor", OmniOSAppCategory.OMNIOS_CORE),
        OmniOSAppDescriptor("settings", "Settings", OmniOSAppCategory.OMNIOS_CORE)
    )

    val mySuiteApps: List<OmniOSAppDescriptor> = allApps.filter { it.category == OmniOSAppCategory.MY_SUITE }
    val coreApps: List<OmniOSAppDescriptor> = allApps.filter { it.category == OmniOSAppCategory.OMNIOS_CORE }
    val defaultAllowedIds: Set<String> = allApps.filter { it.defaultEnabled }.map { it.id }.toSet()

    fun findById(id: String): OmniOSAppDescriptor? = allApps.firstOrNull { it.id == id }
}

/**
 * SharedPreferences-backed reactive store for [OmniOSAdminSettings].
 */
object OmniOSAdminConfigStore {
    private const val PREFS_NAME = "omnios_admin_governance_prefs"
    private const val KEY_ALLOWED_APPS = "allowed_apps"
    private const val KEY_TOUCH_ENABLED = "touch_enabled"
    private const val KEY_AUDIO_ROUTE = "audio_route"
    private const val KEY_BRIGHTNESS = "brightness"
    private const val KEY_THEME = "theme"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_ANIM_DURATION = "anim_duration"
    private const val KEY_REQUIRE_APPROVAL = "require_host_approval"
    private const val KEY_SETTINGS_VIS = "settings_visibility"
    private const val KEY_SHARED_PATHS = "shared_cast_paths"

    private var prefs: SharedPreferences? = null

    private val _settings = MutableStateFlow(OmniOSAdminSettings())
    val settings: StateFlow<OmniOSAdminSettings> = _settings.asStateFlow()

    @Volatile
    var onConfigChangedListener: ((OmniOSAdminSettings) -> Unit)? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val storedApps = sp.getStringSet(KEY_ALLOWED_APPS, null)
            ?.filter { OmniOSAppCatalog.findById(it) != null }
            ?.toSet()
            ?: OmniOSAppCatalog.defaultAllowedIds

        val visMap = OmniOSAdminSettings.defaultSettingsVisibility().toMutableMap()
        sp.getString(KEY_SETTINGS_VIS, null)?.let { raw ->
            try {
                val obj = JSONObject(raw)
                visMap.keys.forEach { key ->
                    if (obj.has(key)) visMap[key] = obj.optBoolean(key, true)
                }
            } catch (_: Exception) {}
        }

        val sharedPaths = mutableListOf<String>()
        sp.getString(KEY_SHARED_PATHS, null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val path = arr.optString(i).trim()
                    if (path.isNotEmpty() && File(path).exists()) {
                        sharedPaths.add(path)
                    }
                }
            } catch (_: Exception) {}
        }

        _settings.value = OmniOSAdminSettings(
            allowedApps = storedApps,
            touchInputEnabled = sp.getBoolean(KEY_TOUCH_ENABLED, true),
            audioRoute = sp.getString(KEY_AUDIO_ROUTE, "receiver") ?: "receiver",
            brightness = sp.getInt(KEY_BRIGHTNESS, 100).coerceIn(15, 100),
            theme = sp.getString(KEY_THEME, "dex") ?: "dex",
            darkMode = sp.getBoolean(KEY_DARK_MODE, true),
            animationDurationMs = sp.getInt(KEY_ANIM_DURATION, 300).coerceIn(100, 800),
            requireHostApproval = sp.getBoolean(KEY_REQUIRE_APPROVAL, false),
            settingsVisibility = visMap,
            sharedCastPaths = sharedPaths.distinct()
        )
    }

    private fun persistAndNotify(transform: (OmniOSAdminSettings) -> OmniOSAdminSettings) {
        val updated = _settings.updateAndGet(transform)
        prefs?.edit()?.apply {
            putStringSet(KEY_ALLOWED_APPS, updated.allowedApps)
            putBoolean(KEY_TOUCH_ENABLED, updated.touchInputEnabled)
            putString(KEY_AUDIO_ROUTE, updated.audioRoute)
            putInt(KEY_BRIGHTNESS, updated.brightness)
            putString(KEY_THEME, updated.theme)
            putBoolean(KEY_DARK_MODE, updated.darkMode)
            putInt(KEY_ANIM_DURATION, updated.animationDurationMs)
            putBoolean(KEY_REQUIRE_APPROVAL, updated.requireHostApproval)
            putString(KEY_SETTINGS_VIS, JSONObject().apply {
                updated.settingsVisibility.forEach { (k, v) -> put(k, v) }
            }.toString())
            putString(KEY_SHARED_PATHS, JSONArray(updated.sharedCastPaths).toString())
            apply()
        }
        onConfigChangedListener?.invoke(updated)
    }

    private inline fun MutableStateFlow<OmniOSAdminSettings>.updateAndGet(
        transform: (OmniOSAdminSettings) -> OmniOSAdminSettings
    ): OmniOSAdminSettings {
        var result = value
        update { current ->
            result = transform(current)
            result
        }
        return result
    }

    fun setAppAllowed(appId: String, allowed: Boolean) {
        if (OmniOSAppCatalog.findById(appId) == null) return
        persistAndNotify { cur ->
            val next = cur.allowedApps.toMutableSet()
            if (allowed) next.add(appId) else next.remove(appId)
            cur.copy(allowedApps = next)
        }
    }

    fun setAllowedApps(appIds: Set<String>) {
        val valid = appIds.filter { OmniOSAppCatalog.findById(it) != null }.toSet()
        persistAndNotify { it.copy(allowedApps = valid) }
    }

    fun setTouchInputEnabled(enabled: Boolean) {
        persistAndNotify { it.copy(touchInputEnabled = enabled) }
    }

    fun setAudioRoute(route: String) {
        val normalized = when (route.lowercase()) {
            "host", "both", "receiver" -> route.lowercase()
            else -> "receiver"
        }
        persistAndNotify { it.copy(audioRoute = normalized) }
    }

    fun setBrightness(percent: Int) {
        persistAndNotify { it.copy(brightness = percent.coerceIn(15, 100)) }
    }

    fun setTheme(themeId: String) {
        val normalized = when (themeId.lowercase()) {
            "android", "dex" -> "dex"
            "macos", "windows", "linux" -> themeId.lowercase()
            else -> "dex"
        }
        persistAndNotify { it.copy(theme = normalized) }
    }

    fun setDarkMode(dark: Boolean) {
        persistAndNotify { it.copy(darkMode = dark) }
    }

    fun setAnimationDuration(durationMs: Int) {
        persistAndNotify { it.copy(animationDurationMs = durationMs.coerceIn(100, 800)) }
    }

    fun setRequireHostApproval(required: Boolean) {
        persistAndNotify { it.copy(requireHostApproval = required) }
    }

    fun setSettingSectionVisible(section: String, visible: Boolean) {
        persistAndNotify { cur ->
            val next = cur.settingsVisibility.toMutableMap()
            next[section] = visible
            cur.copy(settingsVisibility = next)
        }
    }

    fun addSharedPath(path: String) {
        val file = File(path)
        if (!file.exists()) return
        val canonical = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        persistAndNotify { cur ->
            if (cur.sharedCastPaths.contains(canonical)) cur
            else cur.copy(sharedCastPaths = cur.sharedCastPaths + canonical)
        }
    }

    fun removeSharedPath(path: String) {
        persistAndNotify { cur ->
            cur.copy(sharedCastPaths = cur.sharedCastPaths.filterNot { it == path })
        }
    }

    fun clearSharedPaths() {
        persistAndNotify { it.copy(sharedCastPaths = emptyList()) }
    }

    fun setCustomIcon(iconId: String, svgOrDataUri: String) {
        if (iconId.isBlank() || svgOrDataUri.isBlank()) return
        persistAndNotify { cur ->
            val next = cur.customIcons.toMutableMap()
            next[iconId] = svgOrDataUri
            cur.copy(customIcons = next)
        }
    }

    /**
     * Strict host-enforced sandbox validator for Casting file browsing & downloads.
     * Prevents directory traversal outside host-approved shared paths.
     */
    fun isFilePermittedForCasting(target: File): Boolean {
        if (!target.exists()) return false
        val targetCanonical = try { target.canonicalPath } catch (_: Exception) { return false }
        for (shared in _settings.value.sharedCastPaths) {
            val sharedFile = File(shared)
            if (!sharedFile.exists()) continue
            val rootCanonical = try { sharedFile.canonicalPath } catch (_: Exception) { continue }
            if (sharedFile.isDirectory) {
                if (targetCanonical == rootCanonical || targetCanonical.startsWith("$rootCanonical${File.separator}")) {
                    return true
                }
            } else if (targetCanonical == rootCanonical) {
                return true
            }
        }
        return false
    }
}
