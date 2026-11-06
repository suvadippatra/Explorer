package com.troikoss.continuum_explorer.ui.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.troikoss.continuum_explorer.providers.UniversalFileFetcher
import com.troikoss.continuum_explorer.model.LibraryItem
import com.troikoss.continuum_explorer.ui.FileExplorer
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.providers.StorageProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import coil.Coil
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // The user granted the permission. Notifications will work!
        } else {
            // The user denied the permission.
            // Optional: You could show a message here explaining why you need it later.
        }
    }


    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions updated
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(fallback)
                    } catch (_: Exception) {}
                }
            }
        }
        
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            storagePermissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                StorageProviders.init(applicationContext)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                StorageProviders.init(applicationContext)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dismiss system splash screen immediately on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        // Request storage permissions immediately on initial launch
        requestStoragePermissions()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize settings and storage providers
        SettingsManager.init(applicationContext)
        StorageProviders.init(applicationContext)

        if (intent.action == "com.troikoss.continuum_explorer.OPEN_NEW_WINDOW") {
            val freshIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                data = Uri.fromParts("window", UUID.randomUUID().toString(), null)
            }
            startActivity(freshIntent)
            finish()
            return
        }

        val initialPath = intent.getStringExtra("path")
        val initialUri = intent.getStringExtra("uri")
        val initialArchive = run {
            val path = intent.getStringExtra("archivePath")
            if (path != null) File(path) else null
        }
        val initialLibraryItem = when {
            intent.getBooleanExtra("isRecent", false) -> LibraryItem.Recent
            intent.getBooleanExtra("isGallery", false) -> LibraryItem.Gallery
            intent.getBooleanExtra("isRecycleBin", false) -> LibraryItem.RecycleBin
            intent.getBooleanExtra("isVibeArea", false) -> LibraryItem.VibeArea
            intent.getBooleanExtra("isFtpServer", false) -> LibraryItem.FtpServer
            intent.getBooleanExtra("isExternalFtp", false) -> LibraryItem.ExternalFtp
            else -> LibraryItem.None
        }
        val initialNetworkConnectionId = intent.getStringExtra("networkConnectionId")
        val initialOpenFilePath = intent.getStringExtra("openFilePath")
        val initialOpenInEditMode = intent.getBooleanExtra("openInEditMode", false)

        // Build ImageLoader asynchronously to prevent main thread lag on startup
        lifecycleScope.launch(Dispatchers.IO) {
            val videoImageLoader = ImageLoader.Builder(this@MainActivity)
                .components {
                    add(UniversalFileFetcher.Factory())
                    add(VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .build()

            // Set this as the global loader
            Coil.setImageLoader(videoImageLoader)
        }

        setContent {
            FileExplorerTheme {
                FileExplorer(
                    initialPath = initialPath,
                    initialUri = initialUri,
                    initialArchive = initialArchive,
                    initialLibraryItem = initialLibraryItem,
                    initialNetworkConnectionId = initialNetworkConnectionId,
                    initialOpenFilePath = initialOpenFilePath,
                    initialOpenInEditMode = initialOpenInEditMode
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "com.troikoss.continuum_explorer.OPEN_NEW_WINDOW") {
            val newWindowIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                data = Uri.fromParts("window", UUID.randomUUID().toString(), null)
            }
            startActivity(newWindowIntent)
            return
        }
    }
}