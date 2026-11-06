package com.troikoss.continuum_explorer.domain.logic

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

data class ApkPackageMetadata(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val sha256Short: String,
    val permissionsCount: Int,
    val appIcon: Drawable?
)

/**
 * Extracts rich APK package metadata using Android PackageManager and launches
 * the system Package Installer without ever exposing the APK as a raw ZIP folder tree.
 */
object ApkMetadataExtractor {

    fun extractMetadata(context: Context, apkFile: File): ApkPackageMetadata {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
        val pkgInfo: PackageInfo? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        } catch (_: Exception) {
            null
        }

        val appInfo = pkgInfo?.applicationInfo?.apply {
            sourceDir = apkFile.absolutePath
            publicSourceDir = apkFile.absolutePath
        }

        val label = try {
            appInfo?.let { pm.getApplicationLabel(it).toString() }
        } catch (_: Exception) {
            null
        } ?: apkFile.nameWithoutExtension

        val icon = try {
            appInfo?.let { pm.getApplicationIcon(it) }
        } catch (_: Exception) {
            null
        }

        val versionCodeLong = if (pkgInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
        } else {
            0L
        }

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo?.minSdkVersion ?: 0
        } else {
            0
        }

        val targetSdk = appInfo?.targetSdkVersion ?: 0
        val permsCount = pkgInfo?.requestedPermissions?.size ?: 0

        val sha256Short = try {
            if (apkFile.length() <= 64L * 1024L * 1024L) {
                val digest = MessageDigest.getInstance("SHA-256")
                apkFile.inputStream().use { input ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }.take(24) + "…"
            } else {
                "Large Package (${apkFile.length() / (1024 * 1024)} MB)"
            }
        } catch (_: Exception) {
            "Unavailable"
        }

        return ApkPackageMetadata(
            appName = label,
            packageName = pkgInfo?.packageName ?: "Unknown Package ID",
            versionName = pkgInfo?.versionName ?: "1.0",
            versionCode = versionCodeLong,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            fileSizeBytes = apkFile.length(),
            lastModified = apkFile.lastModified(),
            sha256Short = sha256Short,
            permissionsCount = permsCount,
            appIcon = icon
        )
    }

    fun launchSystemPackageInstaller(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    try {
                        val settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                        Toast.makeText(
                            context,
                            "Please allow installing apps from Explorer, then tap Install again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    } catch (_: Exception) {}
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Could not launch Package Installer: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
