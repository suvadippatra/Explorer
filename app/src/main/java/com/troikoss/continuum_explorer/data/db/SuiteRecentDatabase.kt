package com.troikoss.continuum_explorer.data.db

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class SuiteActivityType {
    CREATED,
    EDITED
}

data class SuiteRecentRecord(
    val absolutePath: String,
    val fileName: String,
    val extension: String,
    val editorToolId: String,
    val editorToolName: String,
    val activityType: SuiteActivityType,
    val timestamp: Long
)

/**
 * Dedicated database for "My Suite" Recent Files.
 * Stores ONLY files explicitly created or edited inside My Suite or Continuum Editors
 * (Markor Text & Code Studio, CSV & Table Studio, PhotoEditor Studio).
 * Never queries device-wide MediaStore recent downloads/screenshots.
 */
object SuiteRecentDatabase {
    private const val DB_FILE_NAME = "suite_recent_edited_db.json"
    private const val MAX_RECORDS = 50

    val records = mutableStateListOf<SuiteRecentRecord>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        reload(context)
    }

    fun reload(context: Context) {
        try {
            val dbFile = File(context.filesDir, DB_FILE_NAME)
            if (!dbFile.exists()) {
                records.clear()
                return
            }
            val raw = dbFile.readText()
            val arr = JSONArray(raw)
            val parsed = mutableListOf<SuiteRecentRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val path = obj.optString("absolutePath", "")
                if (path.isBlank()) continue
                val f = File(path)
                if (!f.exists() || f.isDirectory) continue
                parsed.add(
                    SuiteRecentRecord(
                        absolutePath = path,
                        fileName = f.name,
                        extension = f.extension.lowercase(),
                        editorToolId = obj.optString("editorToolId", "text_editor"),
                        editorToolName = obj.optString("editorToolName", "Text & Code Studio"),
                        activityType = try {
                            SuiteActivityType.valueOf(obj.optString("activityType", SuiteActivityType.EDITED.name))
                        } catch (_: Exception) {
                            SuiteActivityType.EDITED
                        },
                        timestamp = obj.optLong("timestamp", f.lastModified())
                    )
                )
            }
            parsed.sortByDescending { it.timestamp }
            records.clear()
            records.addAll(parsed.take(MAX_RECORDS))
        } catch (_: Exception) {
            records.clear()
        }
    }

    fun recordFileActivity(
        context: Context,
        file: File,
        editorToolId: String = resolveEditorToolId(file),
        editorToolName: String = resolveEditorToolName(file),
        activityType: SuiteActivityType = SuiteActivityType.EDITED
    ) {
        if (!file.exists() || file.isDirectory) return
        val newEntry = SuiteRecentRecord(
            absolutePath = file.absolutePath,
            fileName = file.name,
            extension = file.extension.lowercase(),
            editorToolId = editorToolId,
            editorToolName = editorToolName,
            activityType = activityType,
            timestamp = System.currentTimeMillis()
        )
        records.removeAll { it.absolutePath == file.absolutePath }
        records.add(0, newEntry)
        while (records.size > MAX_RECORDS) {
            records.removeAt(records.lastIndex)
        }
        persistAsync(context)
    }

    fun removeRecord(context: Context, absolutePath: String) {
        records.removeAll { it.absolutePath == absolutePath }
        persistAsync(context)
    }

    fun clearAll(context: Context) {
        records.clear()
        persistAsync(context)
    }

    fun getValidUniversalFiles(context: Context): List<Pair<SuiteRecentRecord, UniversalFile>> {
        if (!isInitialized) init(context)
        val valid = mutableListOf<Pair<SuiteRecentRecord, UniversalFile>>()
        val stalePaths = mutableListOf<String>()
        for (record in records) {
            val f = File(record.absolutePath)
            if (f.exists() && f.isFile) {
                valid.add(record to f.toUniversal())
            } else {
                stalePaths.add(record.absolutePath)
            }
        }
        if (stalePaths.isNotEmpty()) {
            records.removeAll { it.absolutePath in stalePaths }
            persistAsync(context)
        }
        return valid
    }

    private fun persistAsync(context: Context) {
        val snapshot = records.toList()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = JSONArray()
                for (rec in snapshot) {
                    val obj = JSONObject().apply {
                        put("absolutePath", rec.absolutePath)
                        put("fileName", rec.fileName)
                        put("extension", rec.extension)
                        put("editorToolId", rec.editorToolId)
                        put("editorToolName", rec.editorToolName)
                        put("activityType", rec.activityType.name)
                        put("timestamp", rec.timestamp)
                    }
                    arr.put(obj)
                }
                File(appContext.filesDir, DB_FILE_NAME).writeText(arr.toString())
            } catch (_: Exception) {}
        }
    }

    fun resolveEditorToolId(file: File): String {
        val ext = file.extension.lowercase()
        return when {
            ext in setOf("csv", "tsv") -> "csv_editor"
            ext in setOf("jpg", "jpeg", "png", "webp", "bmp") -> "image_studio"
            ext in setOf(
                "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "scss",
                "json", "xml", "yaml", "yml", "toml", "ini", "conf", "sh", "bash", "c", "cpp", "h",
                "hpp", "sql", "gradle", "properties", "go", "rs", "dart", "php", "rb", "lua"
            ) -> "code_studio"
            else -> "text_editor"
        }
    }

    fun resolveEditorToolName(file: File): String {
        val ext = file.extension.lowercase()
        return when {
            ext in setOf("csv", "tsv") -> "CSV & Table Studio"
            ext in setOf("jpg", "jpeg", "png", "webp", "bmp") -> "PhotoEditor Studio"
            ext in setOf(
                "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "scss",
                "json", "xml", "yaml", "yml", "toml", "ini", "conf", "sh", "bash", "c", "cpp", "h",
                "hpp", "sql", "gradle", "properties", "go", "rs", "dart", "php", "rb", "lua"
            ) -> "Code Studio"
            ext in setOf("md", "markdown") -> "Markor Markdown Studio"
            else -> "Markor Text Studio"
        }
    }
}
