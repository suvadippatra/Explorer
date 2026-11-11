package com.troikoss.continuum_explorer.managers

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import com.troikoss.continuum_explorer.model.FileColumnType
import com.troikoss.continuum_explorer.model.SortOrder
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.model.ViewMode
import com.troikoss.continuum_explorer.rules.WorkspaceWindowMode
import com.troikoss.continuum_explorer.utils.GlobalEvents
import com.troikoss.continuum_explorer.viewarea.FileMimeGroup
import com.troikoss.continuum_explorer.viewarea.FileMimeParser

enum class DetailsMode {
    OFF,
    PANE,
    BAR
}

enum class DeleteBehavior {
    ASK,
    RECYCLE,
    PERMANENT
}

enum class TouchDragBehavior {
    ASK,
    COPY,
    MOVE
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    MATERIAL_UI
}

enum class AppFontFamily(val label: String) {
    SYSTEM_DEFAULT("System Default"),
    PLUS_JAKARTA_SANS("Plus Jakarta Sans"),
    JETBRAINS_MONO("JetBrains Mono")
}


enum class DefaultAppHandler {
    INTERNAL_PREVIEWER,
    EXTERNAL_APP
}

enum class FtpHostedMode {
    FULL_STORAGE,
    CUSTOM_FOLDER,
    BATCH_FILES
}

object SettingsManager {
    private const val PREFS_NAME = "explorer_settings"
    private const val KEY_DELETE_BEHAVIOR = "delete_behavior"

    private const val KEY_TOUCH_DRAG_BEHAVIOR = "touch_drag_behavior"
    private const val KEY_DEFAULT_ARCHIVE_VIEWER = "default_archive_viewer"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_APP_FONT_FAMILY = "app_font_family"


    private const val KEY_LANGUAGE = "language"
    private const val KEY_BATCH_RENAME_ENABLED = "batch_rename_enabled"
    private const val KEY_DETAILS_MODE = "details_mode"

    private const val KEY_COMMAND_BAR_VISIBLE = "command_bar_visible"
    private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
    private const val KEY_ICON_TOUCH_SELECTION = "icon_touch_selection"
    private const val KEY_DEFAULT_VIEW_MODE = "default_view_mode"
    private const val KEY_COLORFUL_BARS = "colorful_bars"
    private const val KEY_FOLDERS_FIRST = "folders_first"
    private const val KEY_GROUPING_ENABLED = "grouping_enabled"
    private const val KEY_DEFAULT_SORT_COLUMN = "default_sort_column"
    private const val KEY_DEFAULT_SORT_ORDER = "default_sort_order"
    private const val KEY_DEFAULT_HEADER_DATE = "default_header_date"
    private const val KEY_DEFAULT_HEADER_SIZE = "default_header_size"
    private const val KEY_DEFAULT_HEADER_TYPE = "default_header_type"
    private const val KEY_DEFAULT_GRID_ZOOM = "default_grid_zoom"
    private const val KEY_DEFAULT_GRID_COLUMNS = "default_grid_columns"
    private const val KEY_WORKSPACE_WINDOW_MODE = "workspace_window_mode"

    private val _isFullScreenTabBarHidden = mutableStateOf(false)
    val isFullScreenTabBarHidden: State<Boolean> = _isFullScreenTabBarHidden

    private val _isWideSidebarCollapsed = mutableStateOf(false)
    val isWideSidebarCollapsed: State<Boolean> = _isWideSidebarCollapsed

    fun toggleWideSidebarCollapsed() {
        _isWideSidebarCollapsed.value = !_isWideSidebarCollapsed.value
    }

    fun setWideSidebarCollapsed(collapsed: Boolean) {
        _isWideSidebarCollapsed.value = collapsed
    }

    private val _workspaceWindowMode = mutableStateOf(WorkspaceWindowMode.DEX_WINDOWS_TABLET_MOBILE_TABS)
    val workspaceWindowMode: State<WorkspaceWindowMode> = _workspaceWindowMode

    fun setWorkspaceWindowMode(context: Context, mode: WorkspaceWindowMode) {
        _workspaceWindowMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WORKSPACE_WINDOW_MODE, mode.id).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFullScreenTabBarHidden(hidden: Boolean) {
        _isFullScreenTabBarHidden.value = hidden
    }

    fun toggleFullScreenTabBar() {
        _isFullScreenTabBarHidden.value = !_isFullScreenTabBarHidden.value
    }

    private val _defaultGridColumns = mutableIntStateOf(3)
    val defaultGridColumns: State<Int> = _defaultGridColumns

    private const val KEY_HOVER_PREVIEW_ENABLED = "hover_preview_enabled"
    private const val KEY_SIDEBAR_SWIPE_GESTURE = "sidebar_swipe_gesture"
    private const val KEY_DEFAULT_HANDLER_PDF = "default_handler_pdf"
    private const val KEY_DEFAULT_HANDLER_IMAGE = "default_handler_image"
    private const val KEY_DEFAULT_HANDLER_TEXT = "default_handler_text"
    private const val KEY_DEFAULT_HANDLER_MEDIA = "default_handler_media"
    private const val KEY_DEFAULT_HANDLER_OFFICE = "default_handler_office"

    private const val KEY_FTP_PORT = "ftp_port"
    private const val KEY_FTP_ANONYMOUS = "ftp_anonymous"
    private const val KEY_FTP_USERNAME = "ftp_username"
    private const val KEY_FTP_PASSWORD = "ftp_password"
    private const val KEY_FTP_WRITE_PERM = "ftp_write_perm"
    private const val KEY_FTP_HOSTED_MODE = "ftp_hosted_mode"
    private const val KEY_FTP_HOSTED_PATH = "ftp_hosted_path"
    private const val KEY_FTP_UPLOAD_PASSWORD = "ftp_upload_password"
    private const val KEY_FTP_ALLOW_UPLOAD = "ftp_allow_upload"
    private const val KEY_FTP_ALLOW_MINI_APPS = "ftp_allow_mini_apps"
    private const val KEY_FTP_UPLOAD_FOLDER = "ftp_upload_folder"

    private const val KEY_SCREENCAST_ENABLED = "screencast_enabled"
    private const val KEY_SCREENCAST_RESOLUTION = "screencast_resolution"
    private const val KEY_SCREENCAST_FPS = "screencast_fps"
    private const val KEY_SCREENCAST_QUALITY = "screencast_quality"
    private const val KEY_SCREENCAST_ROTATION = "screencast_rotation"
    private const val KEY_SCREENCAST_AUDIO = "screencast_audio"
    private const val KEY_BOOKMARKED_FOLDERS = "bookmarked_folders"

    private val _deleteBehavior = mutableStateOf(DeleteBehavior.ASK)
    val deleteBehavior: State<DeleteBehavior> = _deleteBehavior

    private val _touchDragBehavior = mutableStateOf(TouchDragBehavior.ASK)
    val touchDragBehavior: State<TouchDragBehavior> = _touchDragBehavior

    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: State<ThemeMode> = _themeMode

    private val _appFontFamily = mutableStateOf(AppFontFamily.SYSTEM_DEFAULT)
    val appFontFamily: State<AppFontFamily> = _appFontFamily


    private val _language = mutableStateOf("system")
    val language: State<String> = _language

    private val _detailsMode = mutableStateOf(DetailsMode.OFF)
    val detailsMode: State<DetailsMode> = _detailsMode

    private val _isCommandBarVisible = mutableStateOf(true)
    val isCommandBarVisible: State<Boolean> = _isCommandBarVisible

    private val _showHiddenFiles = mutableStateOf(false)
    val showHiddenFiles: State<Boolean> = _showHiddenFiles

    private val _iconTouchSelection = mutableStateOf(true)
    val iconTouchSelection: State<Boolean> = _iconTouchSelection

    private val _defaultViewMode = mutableStateOf(ViewMode.DETAILS)
    val defaultViewMode: State<ViewMode> = _defaultViewMode

    private val _isColorfulBarsEnabled = mutableStateOf(false)
    val isColorfulBarsEnabled: State<Boolean> = _isColorfulBarsEnabled

    // Derived state: enabled if behavior is not PERMANENT
    private val _isRecycleBinEnabled = mutableStateOf(true)
    val isRecycleBinEnabled: State<Boolean> = _isRecycleBinEnabled

    private val _isDefaultArchiveViewerEnabled = mutableStateOf(true)
    val isDefaultArchiveViewerEnabled: State<Boolean> = _isDefaultArchiveViewerEnabled

    private val _isSidebarSwipeEnabled = mutableStateOf(true)
    val isSidebarSwipeEnabled: State<Boolean> = _isSidebarSwipeEnabled

    private val _isBatchRenameEnabled = mutableStateOf(true)
    val isBatchRenameEnabled: State<Boolean> = _isBatchRenameEnabled

    private val _foldersFirst = mutableStateOf(true)
    val foldersFirst: State<Boolean> = _foldersFirst

    private val _isGroupingEnabled = mutableStateOf(false)
    val isGroupingEnabled: State<Boolean> = _isGroupingEnabled

    private val _defaultSortColumnType = mutableStateOf(FileColumnType.NAME)
    val defaultSortColumnType: State<FileColumnType> = _defaultSortColumnType

    private val _defaultSortOrder = mutableStateOf(SortOrder.Ascending)
    val defaultSortOrder: State<SortOrder> = _defaultSortOrder

    private val _defaultHeaderDate = mutableStateOf(true)
    val defaultHeaderDate: State<Boolean> = _defaultHeaderDate

    private val _defaultHeaderSize = mutableStateOf(true)
    val defaultHeaderSize: State<Boolean> = _defaultHeaderSize

    private val _defaultHeaderType = mutableStateOf(true)
    val defaultHeaderType: State<Boolean> = _defaultHeaderType

    private val _defaultGridZoom = mutableIntStateOf(100)
    val defaultGridZoom: State<Int> = _defaultGridZoom

    private val _isHoverPreviewEnabled = mutableStateOf(false)
    val isHoverPreviewEnabled: State<Boolean> = _isHoverPreviewEnabled

    private val _defaultHandlerPdf = mutableStateOf(DefaultAppHandler.INTERNAL_PREVIEWER)
    val defaultHandlerPdf: State<DefaultAppHandler> = _defaultHandlerPdf

    private val _defaultHandlerImage = mutableStateOf(DefaultAppHandler.INTERNAL_PREVIEWER)
    val defaultHandlerImage: State<DefaultAppHandler> = _defaultHandlerImage

    private val _defaultHandlerText = mutableStateOf(DefaultAppHandler.INTERNAL_PREVIEWER)
    val defaultHandlerText: State<DefaultAppHandler> = _defaultHandlerText

    private val _defaultHandlerMedia = mutableStateOf(DefaultAppHandler.INTERNAL_PREVIEWER)
    val defaultHandlerMedia: State<DefaultAppHandler> = _defaultHandlerMedia

    private val _defaultHandlerOffice = mutableStateOf(DefaultAppHandler.INTERNAL_PREVIEWER)
    val defaultHandlerOffice: State<DefaultAppHandler> = _defaultHandlerOffice

    private val _ftpPort = mutableIntStateOf(2121)
    val ftpPort: State<Int> = _ftpPort

    private val _isFtpAnonymousAllowed = mutableStateOf(true)
    val isFtpAnonymousAllowed: State<Boolean> = _isFtpAnonymousAllowed

    private val _ftpUsername = mutableStateOf("explorer")
    val ftpUsername: State<String> = _ftpUsername

    private val _ftpPassword = mutableStateOf("admin")
    val ftpPassword: State<String> = _ftpPassword

    private val _ftpWritePermission = mutableStateOf(true)
    val ftpWritePermission: State<Boolean> = _ftpWritePermission

    private val _ftpHostedMode = mutableStateOf(FtpHostedMode.FULL_STORAGE)
    val ftpHostedMode: State<FtpHostedMode> = _ftpHostedMode

    private val _ftpHostedPath = mutableStateOf("")
    val ftpHostedPath: State<String> = _ftpHostedPath

    private val _ftpUploadPassword = mutableStateOf("admin123")
    val ftpUploadPassword: State<String> = _ftpUploadPassword

    private val _ftpAllowUpload = mutableStateOf(true)
    val ftpAllowUpload: State<Boolean> = _ftpAllowUpload

    private val _ftpAllowMiniApps = mutableStateOf(true)
    val ftpAllowMiniApps: State<Boolean> = _ftpAllowMiniApps

    private val _ftpUploadFolderPath = mutableStateOf(
        java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "ExplorerUploads"
        ).absolutePath
    )
    val ftpUploadFolderPath: State<String> = _ftpUploadFolderPath

    private val _screenCastEnabled = mutableStateOf(true)
    val screenCastEnabled: State<Boolean> = _screenCastEnabled

    private val _screenCastResolution = mutableStateOf("720p")
    val screenCastResolution: State<String> = _screenCastResolution

    private val _screenCastFps = mutableIntStateOf(15)
    val screenCastFps: State<Int> = _screenCastFps

    private val _screenCastQuality = mutableIntStateOf(75)
    val screenCastQuality: State<Int> = _screenCastQuality

    private val _screenCastRotation = mutableIntStateOf(0)
    val screenCastRotation: State<Int> = _screenCastRotation

    private val _screenCastAudio = mutableStateOf(false)
    val screenCastAudio: State<Boolean> = _screenCastAudio

    private val _bookmarkedFolders = mutableStateOf<Set<String>>(
        setOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath,
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).absolutePath,
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS).absolutePath
        )
    )
    val bookmarkedFolders: State<Set<String>> = _bookmarkedFolders

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val savedBehavior = prefs.getString(KEY_DELETE_BEHAVIOR, DeleteBehavior.ASK.name)
        val behavior = try {
            DeleteBehavior.valueOf(savedBehavior ?: DeleteBehavior.ASK.name)
        } catch (e: Exception) {
            DeleteBehavior.ASK
        }
        
        updateBehaviorInternal(behavior)

        val savedTouchDrag = prefs.getString(KEY_TOUCH_DRAG_BEHAVIOR, TouchDragBehavior.ASK.name)
        _touchDragBehavior.value = try {
            TouchDragBehavior.valueOf(savedTouchDrag ?: TouchDragBehavior.ASK.name)
        } catch (e: Exception) {
            TouchDragBehavior.ASK
        }

        val savedTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val theme = try {
            ThemeMode.valueOf(savedTheme ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        _themeMode.value = theme

        val savedFont = prefs.getString(KEY_APP_FONT_FAMILY, AppFontFamily.SYSTEM_DEFAULT.name)
        _appFontFamily.value = try {
            AppFontFamily.valueOf(savedFont ?: AppFontFamily.SYSTEM_DEFAULT.name)
        } catch (_: Exception) {
            AppFontFamily.SYSTEM_DEFAULT
        }


        val savedLanguage = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        _language.value = savedLanguage
        applyLocale(savedLanguage)

        val savedDetails = prefs.getString(KEY_DETAILS_MODE, DetailsMode.OFF.name)
        _detailsMode.value = try {
            DetailsMode.valueOf(savedDetails ?: DetailsMode.OFF.name)
        } catch (e: Exception) {
            DetailsMode.OFF
        }

        _isCommandBarVisible.value = prefs.getBoolean(KEY_COMMAND_BAR_VISIBLE, true)
        _showHiddenFiles.value = prefs.getBoolean(KEY_SHOW_HIDDEN_FILES, false)
        _iconTouchSelection.value = prefs.getBoolean(KEY_ICON_TOUCH_SELECTION, true)
        _isSidebarSwipeEnabled.value = prefs.getBoolean(KEY_SIDEBAR_SWIPE_GESTURE, true)
        _isColorfulBarsEnabled.value = prefs.getBoolean(KEY_COLORFUL_BARS, false)

        _isDefaultArchiveViewerEnabled.value = prefs.getBoolean(KEY_DEFAULT_ARCHIVE_VIEWER, true)
        _isBatchRenameEnabled.value = prefs.getBoolean(KEY_BATCH_RENAME_ENABLED, true)
        _foldersFirst.value = prefs.getBoolean(KEY_FOLDERS_FIRST, true)
        _isGroupingEnabled.value = prefs.getBoolean(KEY_GROUPING_ENABLED, false)

        val savedSortCol = prefs.getString(KEY_DEFAULT_SORT_COLUMN, FileColumnType.NAME.name)
        _defaultSortColumnType.value = try {
            FileColumnType.valueOf(savedSortCol ?: FileColumnType.NAME.name)
        } catch (e: Exception) {
            FileColumnType.NAME
        }
        val savedSortOrder = prefs.getString(KEY_DEFAULT_SORT_ORDER, SortOrder.Ascending.name)
        _defaultSortOrder.value = try {
            SortOrder.valueOf(savedSortOrder ?: SortOrder.Ascending.name)
        } catch (e: Exception) {
            SortOrder.Ascending
        }

        val savedViewMode = prefs.getString(KEY_DEFAULT_VIEW_MODE, ViewMode.DETAILS.name)
        _defaultViewMode.value = try {
            ViewMode.valueOf(savedViewMode ?: ViewMode.DETAILS.name)
        } catch (e: Exception) {
            ViewMode.DETAILS
        }

        _defaultHeaderDate.value = prefs.getBoolean(KEY_DEFAULT_HEADER_DATE, true)
        _defaultHeaderSize.value = prefs.getBoolean(KEY_DEFAULT_HEADER_SIZE, true)
        _defaultHeaderType.value = prefs.getBoolean(KEY_DEFAULT_HEADER_TYPE, true)
        _defaultGridZoom.value = prefs.getInt(KEY_DEFAULT_GRID_ZOOM, 100).coerceIn(60, 300)

        _isHoverPreviewEnabled.value = prefs.getBoolean(KEY_HOVER_PREVIEW_ENABLED, false)
        _workspaceWindowMode.value = WorkspaceWindowMode.fromId(
            prefs.getInt(KEY_WORKSPACE_WINDOW_MODE, WorkspaceWindowMode.DEX_WINDOWS_TABLET_MOBILE_TABS.id)
        )

        val loadHandler = { key: String ->
            val saved = prefs.getString(key, DefaultAppHandler.INTERNAL_PREVIEWER.name)
            try {
                DefaultAppHandler.valueOf(saved ?: DefaultAppHandler.INTERNAL_PREVIEWER.name)
            } catch (_: Exception) {
                DefaultAppHandler.INTERNAL_PREVIEWER
            }
        }
        _defaultHandlerPdf.value = loadHandler(KEY_DEFAULT_HANDLER_PDF)
        _defaultHandlerImage.value = loadHandler(KEY_DEFAULT_HANDLER_IMAGE)
        _defaultHandlerText.value = loadHandler(KEY_DEFAULT_HANDLER_TEXT)
        _defaultHandlerMedia.value = loadHandler(KEY_DEFAULT_HANDLER_MEDIA)
        _defaultHandlerOffice.value = loadHandler(KEY_DEFAULT_HANDLER_OFFICE)

        _ftpPort.value = prefs.getInt(KEY_FTP_PORT, 2121)
        _isFtpAnonymousAllowed.value = prefs.getBoolean(KEY_FTP_ANONYMOUS, true)
        _ftpUsername.value = prefs.getString(KEY_FTP_USERNAME, "explorer") ?: "explorer"
        _ftpPassword.value = prefs.getString(KEY_FTP_PASSWORD, "admin") ?: "admin"
        _ftpWritePermission.value = prefs.getBoolean(KEY_FTP_WRITE_PERM, true)

        val savedMode = prefs.getString(KEY_FTP_HOSTED_MODE, FtpHostedMode.FULL_STORAGE.name)
        _ftpHostedMode.value = try {
            FtpHostedMode.valueOf(savedMode ?: FtpHostedMode.FULL_STORAGE.name)
        } catch (_: Exception) {
            FtpHostedMode.FULL_STORAGE
        }
        _ftpHostedPath.value = prefs.getString(KEY_FTP_HOSTED_PATH, "") ?: ""
        _ftpUploadPassword.value = prefs.getString(KEY_FTP_UPLOAD_PASSWORD, "admin123") ?: "admin123"
        _ftpAllowUpload.value = prefs.getBoolean(KEY_FTP_ALLOW_UPLOAD, true)
        _ftpWritePermission.value = _ftpAllowUpload.value
        _ftpAllowMiniApps.value = prefs.getBoolean(KEY_FTP_ALLOW_MINI_APPS, true)
        val defaultUploadDir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "ExplorerUploads"
        ).absolutePath
        _ftpUploadFolderPath.value = prefs.getString(KEY_FTP_UPLOAD_FOLDER, defaultUploadDir) ?: defaultUploadDir

        _screenCastEnabled.value = prefs.getBoolean(KEY_SCREENCAST_ENABLED, true)
        _screenCastResolution.value = prefs.getString(KEY_SCREENCAST_RESOLUTION, "720p") ?: "720p"
        _screenCastFps.value = prefs.getInt(KEY_SCREENCAST_FPS, 15).coerceIn(5, 30)
        _screenCastQuality.value = prefs.getInt(KEY_SCREENCAST_QUALITY, 75).coerceIn(30, 95)
        _screenCastRotation.value = prefs.getInt(KEY_SCREENCAST_ROTATION, 0)
        _screenCastAudio.value = prefs.getBoolean(KEY_SCREENCAST_AUDIO, false)

        val savedBookmarks = prefs.getStringSet(KEY_BOOKMARKED_FOLDERS, null)
        if (savedBookmarks != null) {
            _bookmarkedFolders.value = savedBookmarks
        }
    }

    fun toggleBookmark(context: Context, path: String) {
        val current = _bookmarkedFolders.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _bookmarkedFolders.value = current
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_BOOKMARKED_FOLDERS, current).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun isBookmarked(path: String): Boolean = _bookmarkedFolders.value.contains(path)

    fun setFtpUploadPassword(context: Context, pass: String) {
        _ftpUploadPassword.value = pass.ifBlank { "admin123" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FTP_UPLOAD_PASSWORD, _ftpUploadPassword.value).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpAllowUpload(context: Context, allowed: Boolean) {
        _ftpAllowUpload.value = allowed
        _ftpWritePermission.value = allowed
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_FTP_ALLOW_UPLOAD, allowed)
            .putBoolean(KEY_FTP_WRITE_PERM, allowed)
            .apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpAllowMiniApps(context: Context, allowed: Boolean) {
        _ftpAllowMiniApps.value = allowed
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FTP_ALLOW_MINI_APPS, allowed).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpUploadFolderPath(context: Context, path: String) {
        if (path.isBlank()) return
        _ftpUploadFolderPath.value = path
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FTP_UPLOAD_FOLDER, path).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setScreenCastEnabled(context: Context, enabled: Boolean) {
        _screenCastEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SCREENCAST_ENABLED, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setScreenCastParams(
        context: Context,
        resolution: String,
        fps: Int,
        rotation: Int,
        audio: Boolean,
        quality: Int = _screenCastQuality.value
    ) {
        val clampedFps = fps.coerceIn(5, 30)
        val clampedQuality = quality.coerceIn(30, 95)
        _screenCastResolution.value = resolution
        _screenCastFps.value = clampedFps
        _screenCastQuality.value = clampedQuality
        _screenCastRotation.value = rotation
        _screenCastAudio.value = audio
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SCREENCAST_RESOLUTION, resolution)
            .putInt(KEY_SCREENCAST_FPS, clampedFps)
            .putInt(KEY_SCREENCAST_QUALITY, clampedQuality)
            .putInt(KEY_SCREENCAST_ROTATION, rotation)
            .putBoolean(KEY_SCREENCAST_AUDIO, audio)
            .apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpHostedMode(context: Context, mode: FtpHostedMode, customPath: String = "") {
        _ftpHostedMode.value = mode
        _ftpHostedPath.value = customPath
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FTP_HOSTED_MODE, mode.name)
            .putString(KEY_FTP_HOSTED_PATH, customPath)
            .apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpPort(context: Context, port: Int) {
        val clamped = port.coerceIn(1024, 65535)
        _ftpPort.value = clamped
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FTP_PORT, clamped).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpAnonymousAllowed(context: Context, allowed: Boolean) {
        _isFtpAnonymousAllowed.value = allowed
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FTP_ANONYMOUS, allowed).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpCredentials(context: Context, user: String, pass: String) {
        _ftpUsername.value = user.ifBlank { "explorer" }
        _ftpPassword.value = pass.ifBlank { "admin" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FTP_USERNAME, _ftpUsername.value)
            .putString(KEY_FTP_PASSWORD, _ftpPassword.value)
            .apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFtpWritePermission(context: Context, allowed: Boolean) {
        _ftpWritePermission.value = allowed
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FTP_WRITE_PERM, allowed).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setHoverPreviewEnabled(context: Context, enabled: Boolean) {
        _isHoverPreviewEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HOVER_PREVIEW_ENABLED, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHandlerPdf(context: Context, handler: DefaultAppHandler) {
        _defaultHandlerPdf.value = handler
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_HANDLER_PDF, handler.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHandlerImage(context: Context, handler: DefaultAppHandler) {
        _defaultHandlerImage.value = handler
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_HANDLER_IMAGE, handler.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHandlerText(context: Context, handler: DefaultAppHandler) {
        _defaultHandlerText.value = handler
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_HANDLER_TEXT, handler.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHandlerMedia(context: Context, handler: DefaultAppHandler) {
        _defaultHandlerMedia.value = handler
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_HANDLER_MEDIA, handler.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHandlerOffice(context: Context, handler: DefaultAppHandler) {
        _defaultHandlerOffice.value = handler
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_HANDLER_OFFICE, handler.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    /**
     * Determines whether a file should open in the unified in-app previewer
     * based on user associations in Settings. Defaults to true (internal) for all supported types.
     */
    fun shouldOpenWithInternalViewer(file: UniversalFile): Boolean {
        if (file.isDirectory) return false
        val ext = FileMimeParser.extractExtension(file.name)
        val group = FileMimeGroup.fromExtension(ext)
        return when (group) {
            FileMimeGroup.PDF -> _defaultHandlerPdf.value == DefaultAppHandler.INTERNAL_PREVIEWER
            FileMimeGroup.IMAGE -> _defaultHandlerImage.value == DefaultAppHandler.INTERNAL_PREVIEWER
            FileMimeGroup.TEXT -> _defaultHandlerText.value == DefaultAppHandler.INTERNAL_PREVIEWER
            FileMimeGroup.AUDIO_VIDEO -> _defaultHandlerMedia.value == DefaultAppHandler.INTERNAL_PREVIEWER
            FileMimeGroup.OFFICE -> _defaultHandlerOffice.value == DefaultAppHandler.INTERNAL_PREVIEWER
            else -> true
        }
    }

    fun setLanguage(context: Context, languageTag: String) {
        _language.value = languageTag
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageTag).apply()
        applyLocale(languageTag)
        GlobalEvents.triggerConfigUpdate()
    }

    private fun applyLocale(languageTag: String) {
        val localeList = if (languageTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)

    }

    fun setDetailsMode(context: Context, mode: DetailsMode) {
        _detailsMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DETAILS_MODE, mode.name).apply()

        // ADD THIS LINE: It tells the rest of the app "Hey! Settings changed!"
        GlobalEvents.triggerConfigUpdate()
    }

    fun setCommandBarVisible(context: Context, visible: Boolean) {
        _isCommandBarVisible.value = visible
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COMMAND_BAR_VISIBLE, visible).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setShowHiddenFiles(context: Context, show: Boolean) {
        _showHiddenFiles.value = show
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, show).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setIconTouchSelection(context: Context, enabled: Boolean) {
        _iconTouchSelection.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ICON_TOUCH_SELECTION, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setSidebarSwipeEnabled(context: Context, enabled: Boolean) {
        _isSidebarSwipeEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SIDEBAR_SWIPE_GESTURE, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setColorfulBarsEnabled(context: Context, enabled: Boolean) {
        _isColorfulBarsEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COLORFUL_BARS, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDeleteBehavior(context: Context, behavior: DeleteBehavior) {
        updateBehaviorInternal(behavior)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DELETE_BEHAVIOR, behavior.name).apply()
    }

    fun setTouchDragBehavior(context: Context, behavior: TouchDragBehavior) {
        _touchDragBehavior.value = behavior
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOUCH_DRAG_BEHAVIOR, behavior.name).apply()
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setAppFontFamily(context: Context, fontFamily: AppFontFamily) {
        _appFontFamily.value = fontFamily
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_FONT_FAMILY, fontFamily.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }


    private fun updateBehaviorInternal(behavior: DeleteBehavior) {
        _deleteBehavior.value = behavior
        // Automatically disable recycle bin view if user chooses to always delete permanently
        _isRecycleBinEnabled.value = (behavior != DeleteBehavior.PERMANENT)
    }

    fun setDefaultArchiveViewerEnabled(context: Context, enabled: Boolean) {
        _isDefaultArchiveViewerEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_ARCHIVE_VIEWER, enabled).apply()
    }

    fun setDefaultViewMode(context: Context, mode: ViewMode) {
        _defaultViewMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_VIEW_MODE, mode.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFoldersFirst(context: Context, enabled: Boolean) {
        _foldersFirst.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FOLDERS_FIRST, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setGroupingEnabled(context: Context, enabled: Boolean) {
        _isGroupingEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GROUPING_ENABLED, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultSortColumnType(context: Context, columnType: FileColumnType) {
        _defaultSortColumnType.value = columnType
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_SORT_COLUMN, columnType.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultSortOrder(context: Context, order: SortOrder) {
        _defaultSortOrder.value = order
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_SORT_ORDER, order.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHeaderDate(context: Context, enabled: Boolean) {
        _defaultHeaderDate.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_HEADER_DATE, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHeaderSize(context: Context, enabled: Boolean) {
        _defaultHeaderSize.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_HEADER_SIZE, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHeaderType(context: Context, enabled: Boolean) {
        _defaultHeaderType.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_HEADER_TYPE, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultGridZoom(context: Context, zoom: Int) {
        val clamped = zoom.coerceIn(60, 300)
        _defaultGridZoom.value = clamped
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DEFAULT_GRID_ZOOM, clamped).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun resetDefaultFolderOptions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DEFAULT_VIEW_MODE, ViewMode.DETAILS.name)
            putString(KEY_DEFAULT_SORT_COLUMN, FileColumnType.NAME.name)
            putString(KEY_DEFAULT_SORT_ORDER, SortOrder.Ascending.name)
            putBoolean(KEY_SHOW_HIDDEN_FILES, false)
            putBoolean(KEY_FOLDERS_FIRST, true)
            putBoolean(KEY_GROUPING_ENABLED, false)
            putBoolean(KEY_DEFAULT_HEADER_DATE, true)
            putBoolean(KEY_DEFAULT_HEADER_SIZE, true)
            putBoolean(KEY_DEFAULT_HEADER_TYPE, true)
            putInt(KEY_DEFAULT_GRID_ZOOM, 100)
            apply()
        }
        _defaultViewMode.value = ViewMode.DETAILS
        _defaultSortColumnType.value = FileColumnType.NAME
        _defaultSortOrder.value = SortOrder.Ascending
        _showHiddenFiles.value = false
        _foldersFirst.value = true
        _isGroupingEnabled.value = false
        _defaultHeaderDate.value = true
        _defaultHeaderSize.value = true
        _defaultHeaderType.value = true
        _defaultGridZoom.value = 100
        GlobalEvents.triggerConfigUpdate()
    }

    fun setBatchRenameEnabled(context: Context, enabled: Boolean) {
        _isBatchRenameEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BATCH_RENAME_ENABLED, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun resetAllFolderOverrides(context: Context) {
        val prefNames = listOf(
            "folder_view_modes", "folder_sort_params", "folder_grid_sizes",
            "folder_grouping", "folder_folders_first", "folder_show_hidden",
            "column_visibility", "column_widths"
        )
        prefNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        GlobalEvents.triggerConfigUpdate()
    }
}
