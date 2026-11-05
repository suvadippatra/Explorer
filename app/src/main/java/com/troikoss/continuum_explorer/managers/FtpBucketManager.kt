package com.troikoss.continuum_explorer.managers

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import java.io.File

/**
 * Manages items in the Bucket for FTP / File Serving.
 * Allows picking files/folders from internal storage, SD cards, and USB drives.
 * Prepares a hosted directory (symlinks or staging directory) to be cleanly served by Apache FTP and Ktor HTTP.
 */
object FtpBucketManager {
    private const val PREFS_NAME = "ftp_bucket_prefs"
    private const val KEY_BUCKET_PATHS = "bucket_paths"

    val bucketItems = mutableStateListOf<File>()
    val isBucketActive = mutableStateOf(false)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BUCKET_PATHS, null)
        bucketItems.clear()
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val path = array.getString(i)
                    val f = File(path)
                    if (f.exists()) {
                        bucketItems.add(f)
                    }
                }
            } catch (_: Exception) {}
        }
        isBucketActive.value = bucketItems.isNotEmpty()
    }

    fun addItem(context: Context, file: File) {
        if (!bucketItems.any { it.absolutePath == file.absolutePath }) {
            bucketItems.add(file)
            save(context)
        }
    }

    fun removeItem(context: Context, file: File) {
        bucketItems.removeAll { it.absolutePath == file.absolutePath }
        save(context)
    }

    fun clearBucket(context: Context) {
        bucketItems.clear()
        save(context)
    }

    private fun save(context: Context) {
        isBucketActive.value = bucketItems.isNotEmpty()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        bucketItems.forEach { array.put(it.absolutePath) }
        prefs.edit().putString(KEY_BUCKET_PATHS, array.toString()).apply()
    }

    /**
     * Prepares a virtual staging directory representing the bucket items.
     * Copies or links files into a clean temporary serving directory.
     */
    fun prepareBucketDirectory(context: Context): File {
        val stagingDir = File(context.cacheDir, "ftp_hosted_bucket").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        bucketItems.forEach { item ->
            try {
                if (item.exists()) {
                    val dest = File(stagingDir, item.name)
                    if (item.isDirectory) {
                        dest.mkdirs()
                        // Copy first-level children or symlink if available
                        item.copyRecursively(dest, overwrite = true)
                    } else {
                        item.copyTo(dest, overwrite = true)
                    }
                }
            } catch (_: Exception) {}
        }

        return stagingDir
    }
}
