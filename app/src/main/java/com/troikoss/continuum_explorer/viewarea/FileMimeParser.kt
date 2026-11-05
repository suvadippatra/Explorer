package com.troikoss.continuum_explorer.viewarea

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Functional parser utility that inspects Android Document File URIs,
 * strips extension types, and classifies them into [FileMimeGroup].
 */
object FileMimeParser {

    /**
     * Reads an incoming Android Document File URI, resolves the real file name,
     * strips its extension type, and returns the correct [FileMimeGroup].
     */
    fun parse(context: Context, uri: Uri): FileMimeGroup {
        val fileName = getFileNameFromUri(context, uri)
        val extension = extractExtension(fileName)
        
        // If extension resolved to a group, return it
        val groupFromExt = FileMimeGroup.fromExtension(extension)
        if (groupFromExt != FileMimeGroup.UNKNOWN) {
            return groupFromExt
        }

        // Secondary fallback: query ContentResolver mime-type
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        return parseFromMimeType(mimeType)
    }

    /**
     * Strips and extracts the clean file extension from a file name or path.
     */
    fun extractExtension(fileNameOrPath: String?): String {
        if (fileNameOrPath.isNullOrBlank()) return ""
        val cleanName = fileNameOrPath.substringBefore('?').substringBefore('#')
        val lastDotIndex = cleanName.lastIndexOf('.')
        if (lastDotIndex == -1 || lastDotIndex == cleanName.length - 1) {
            return ""
        }
        return cleanName.substring(lastDotIndex + 1).lowercase().trim()
    }

    /**
     * Strips the extension from a file name, returning the base name.
     */
    fun stripExtension(fileName: String?): String {
        if (fileName.isNullOrBlank()) return ""
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0) fileName.substring(0, lastDotIndex) else fileName
    }

    /**
     * Resolves the display file name from an Android DocumentFile URI, file URI, or content URI.
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        // Case 1: File scheme
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: File(uri.path ?: "").name
        }

        // Case 2: DocumentFile resolution
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val doc = DocumentFile.fromSingleUri(context, uri)
                val docName = doc?.name
                if (!docName.isNullOrBlank()) return docName
            }
        } catch (_: Exception) {}

        // Case 3: ContentResolver OpenableColumns query
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}

        // Case 4: Uri lastPathSegment or path decoding
        val lastSegment = uri.lastPathSegment
        if (!lastSegment.isNullOrBlank()) {
            val decoded = Uri.decode(lastSegment)
            // SAF URIs often have formats like "primary:Download/example.pdf"
            val simpleName = decoded.substringAfterLast('/')
                .substringAfterLast(':')
            if (simpleName.isNotBlank()) return simpleName
        }

        return uri.toString().substringAfterLast('/')
    }

    /**
     * Maps standard Android MIME strings (e.g. "text/plain", "image/png") to [FileMimeGroup].
     */
    fun parseFromMimeType(mimeType: String?): FileMimeGroup {
        if (mimeType.isNullOrBlank()) return FileMimeGroup.UNKNOWN
        val lower = mimeType.lowercase().trim()
        return when {
            lower.startsWith("text/") || lower.contains("json") || lower.contains("xml") || lower.contains("javascript") -> FileMimeGroup.TEXT
            lower.startsWith("image/") -> FileMimeGroup.IMAGE
            lower == "application/pdf" -> FileMimeGroup.PDF
            lower.contains("wordprocessingml") || lower.contains("spreadsheetml") ||
                lower.contains("presentationml") || lower.contains("msword") ||
                lower.contains("ms-excel") || lower.contains("ms-powerpoint") ||
                lower.contains("opendocument") -> FileMimeGroup.OFFICE
            lower.contains("zip") || lower.contains("tar") || lower.contains("rar") ||
                lower.contains("7z") || lower.contains("gzip") || lower.contains("compressed") -> FileMimeGroup.ARCHIVE
            lower.startsWith("audio/") || lower.startsWith("video/") -> FileMimeGroup.AUDIO_VIDEO
            else -> FileMimeGroup.UNKNOWN
        }
    }
}
