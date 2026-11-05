package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.os.Environment
import java.io.File

object TempCleanupManager {

    /**
     * Clears temporary files created by file editors, server logs, image preview caches,
     * and general app cache directories. Returns the total number of bytes freed.
     */
    fun cleanTempFiles(context: Context): Long {
        var bytesFreed = 0L

        // 1. Clear context cache directory (editor temp files, logs)
        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.exists()) {
            bytesFreed += deleteDirectoryContents(cacheDir)
        }

        // 2. Clear external cache directory if accessible
        val extCacheDir = context.externalCacheDir
        if (extCacheDir != null && extCacheDir.exists()) {
            bytesFreed += deleteDirectoryContents(extCacheDir)
        }

        // 3. Clear temp logs and editor artifacts in internal files directory
        val filesDir = context.filesDir
        if (filesDir != null && filesDir.exists()) {
            val tempLogDir = File(filesDir, "logs")
            if (tempLogDir.exists()) {
                bytesFreed += deleteDirectoryContents(tempLogDir)
            }
            val tempEditorDir = File(filesDir, "temp_editor")
            if (tempEditorDir.exists()) {
                bytesFreed += deleteDirectoryContents(tempEditorDir)
            }
        }

        // Suggest Garbage Collection
        System.gc()

        return bytesFreed
    }

    private fun deleteDirectoryContents(dir: File): Long {
        var freed = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                freed += deleteDirectoryContents(file)
                val len = file.length()
                if (file.delete()) {
                    freed += len
                }
            } else {
                val len = file.length()
                if (file.delete()) {
                    freed += len
                }
            }
        }
        return freed
    }
}
