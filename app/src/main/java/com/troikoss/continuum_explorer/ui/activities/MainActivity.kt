package com.troikoss.continuum_explorer.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.FileExplorer
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.utils.GlobalEvents
import com.troikoss.continuum_explorer.utils.PermissionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Primary entry point for the Continuum Explorer application.
 * Handles lifecycle, serialized runtime permissions, high-speed Coil thumbnail caching,
 * and sets up the Compose UI.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var permissionCoordinator: PermissionCoordinator

    private val manageAllFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            GlobalEvents.triggerRefresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SettingsManager quickly
        SettingsManager.init(this)

        // Register serialized PermissionCoordinator BEFORE activity reaches STARTED state
        permissionCoordinator = PermissionCoordinator(this) {
            GlobalEvents.triggerRefresh()
        }

        // Configure global high-performance Coil ImageLoader on background IO thread
        lifecycleScope.launch(Dispatchers.IO) {
            configureHighPerformanceCoilLoader()
        }

        val initialZipUri = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        val rawPath = intent?.getStringExtra("shortcut_path")
        val shortcutPath = if (rawPath != null && !File(rawPath).exists()) {
            Toast.makeText(this, "Folder does not exist: $rawPath", Toast.LENGTH_SHORT).show()
            null
        } else {
            rawPath
        }
        val shortcutSafUri = intent?.getStringExtra("shortcut_saf_uri")
        val shortcutNetworkId = intent?.getStringExtra("shortcut_network_id")

        enableEdgeToEdge()

        setContent {
            FileExplorerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileExplorer(
                        initialPath = shortcutPath,
                        initialUri = shortcutSafUri,
                        initialArchiveUri = initialZipUri,
                        initialNetworkConnectionId = shortcutNetworkId
                    )
                }
            }
        }

        // Defer runtime permission check until after the first UI frame renders to prevent
        // main-thread frame drops (`Skipped 35 frames!`) and overlapping permission requests.
        window.decorView.post {
            requestStorageAndNotificationPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        GlobalEvents.triggerRefresh()
    }

    private fun configureHighPerformanceCoilLoader() {
        try {
            val imageLoader = ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(applicationContext.cacheDir, "coil_media_thumbs"))
                        .maxSizeBytes(120L * 1024L * 1024L) // 120 MB fast SSD thumbnail cache
                        .build()
                }
                .components {
                    add(VideoFrameDecoder.Factory())
                    add(SvgDecoder.Factory())
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .allowHardware(true)
                .crossfade(false)
                .build()
            Coil.setImageLoader(imageLoader)
        } catch (_: Exception) {}
    }

    /**
     * Requests storage and notification permissions without ever launching two permission dialogs simultaneously.
     */
    private fun requestStorageAndNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        "package:$packageName".toUri()
                    )
                    manageAllFilesLauncher.launch(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageAllFilesLauncher.launch(intent)
                }
            }
        }
        // Single serialized batch request for READ_MEDIA_* and POST_NOTIFICATIONS
        permissionCoordinator.requestStartupPermissionsIfNeeded()
    }
}