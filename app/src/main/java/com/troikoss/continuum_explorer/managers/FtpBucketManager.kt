package com.troikoss.continuum_explorer.managers

import android.content.Context
import android.system.Os
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import java.io.File

/**
 * Manages items in the Bucket for FTP / HTTP File Serving.
 * Allows picking multiple files and folders from internal storage, SD cards, and USB drives.
 * Prepares a virtual staging directory using fast OS symlinks (with safe copy fallback)
 * to be cleanly served by Apache FTP and Ktor HTTP.
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
     * Uses fast OS symlinks first so large folders/videos do not block or duplicate storage,
     * falling back to lightweight file copy only if symlinks are disallowed.
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
                    val linked = try {
                        Os.symlink(item.absolutePath, dest.absolutePath)
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (!linked) {
                        if (item.isDirectory) {
                            dest.mkdirs()
                            item.listFiles()?.forEach { child ->
                                val childDest = File(dest, child.name)
                                val childLinked = try {
                                    Os.symlink(child.absolutePath, childDest.absolutePath)
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                                if (!childLinked && child.isFile && child.length() <= 50 * 1024 * 1024L) {
                                    child.copyTo(childDest, overwrite = true)
                                }
                            }
                        } else if (item.length() <= 100 * 1024 * 1024L) {
                            item.copyTo(dest, overwrite = true)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return stagingDir
    }
}
