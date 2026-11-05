package com.troikoss.continuum_explorer.managers

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import java.io.File

object RecentFilesManager {
    @Volatile
    private var cachedFiles: List<UniversalFile>? = null
    @Volatile
    private var lastFetchTime = 0L
    private const val CACHE_EXPIRY_MS = 15_000L

    fun getRecentFiles(context: Context, forceRefresh: Boolean = false): List<UniversalFile> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedFiles != null && (now - lastFetchTime) < CACHE_EXPIRY_MS) {
            return cachedFiles!!
        }

        val recentFiles = mutableListOf<UniversalFile>()
        
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )
        
        // Fast, indexed query: size > 0 and mime type not null (ignores folders)
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0 AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 35)
                }
                context.contentResolver.query(queryUri, projection, queryArgs, null)
            } else {
                context.contentResolver.query(
                    queryUri,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 35"
                )
            }

            cursor?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext() && recentFiles.size < 30) {
                    val path = c.getString(dataIndex) ?: continue
                    // Fast in-memory path filtering: skip Android private system dirs and hidden files
                    if (path.contains("/Android/data") || path.contains("/Android/obb") || path.contains("/.")) {
                        continue
                    }
                    val file = File(path)
                    if (file.exists() && !file.isDirectory) {
                        recentFiles.add(
                            UniversalFile(
                                name = c.getString(nameIndex) ?: file.name,
                                isDirectory = false,
                                lastModified = c.getLong(dateIndex) * 1000,
                                length = c.getLong(sizeIndex),
                                provider = LocalProvider,
                                providerId = path,
                                parentId = file.parentFile?.absolutePath,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedFiles = recentFiles
        lastFetchTime = now
        return recentFiles
    }
}
