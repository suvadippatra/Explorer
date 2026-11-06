package com.troikoss.continuum_explorer.domain.logic

import com.troikoss.continuum_explorer.model.UniversalFile
import java.util.Locale

enum class FileOpenCategory {
    /** Supported for editing in Markor Text & Code Studio, CSV Studio, or PhotoEditor Studio. */
    EDITABLE_FILE,
    /** Non-editable but playable or viewable (Video, Audio, PDF, Archive excluding APK). */
    PLAYABLE_OR_VIEWABLE_MEDIA,
    /** Android Package (.apk) — shows APK Info Sheet & launches system Package Installer. */
    APK_PACKAGE,
    /** Non-editable binary / unknown file — shows File Info Sheet & "Choose Preferred App to View". */
    NON_EDITABLE_BINARY
}

/**
 * Central engine that enforces the user's file opening and editing rules:
 * - Editable files:
 *   - Single tap in Explorer -> Opens Preview with an "Edit" button.
 *   - Three-dot menu -> Shows "Edit" button that opens directly in Editor UI.
 *   - Opened from My Suite -> Opens directly in Editor UI.
 * - APK files:
 *   - Never opened as a ZIP folder tree. Shows APK Info Sheet + Package Installer.
 * - Playable / Viewable non-editable files (Video, Audio, PDF, ZIP/TAR/7Z/RAR):
 *   - Opens rich media/document/archive viewer (never raw binary text).
 * - Other non-editable binary files:
 *   - Shows File Info Sheet + "Choose Preferred App to View".
 */
object FileOpenPolicyEngine {

    val EDITABLE_TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "org", "adoc", "asciidoc",
        "json", "xml", "html", "htm", "css", "js", "ts", "jsx", "tsx",
        "kt", "kts", "java", "py", "sh", "bash", "c", "cpp", "h", "hpp",
        "rs", "go", "rb", "php", "sql", "ini", "conf", "cfg", "yaml", "yml",
        "toml", "log", "prop", "properties", "gitignore", "env", "gradle"
    )

    val EDITABLE_TABLE_EXTENSIONS = setOf(
        "csv", "tsv"
    )

    val EDITABLE_IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp"
    )

    val PLAYABLE_VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts"
    )

    val PLAYABLE_AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "mid"
    )

    val VIEWABLE_DOCUMENT_EXTENSIONS = setOf(
        "pdf"
    )

    val VIEWABLE_ARCHIVE_EXTENSIONS = setOf(
        "zip", "jar", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "7z", "rar"
    )

    val VIEWABLE_READONLY_IMAGE_EXTENSIONS = setOf(
        "gif", "svg", "heic", "heif", "ico"
    )

    fun extractExtension(fileName: String): String {
        val lower = fileName.lowercase(Locale.US)
        if (lower.endsWith(".tar.gz")) return "tgz"
        if (lower.endsWith(".tar.bz2")) return "tbz2"
        if (lower.endsWith(".tar.xz")) return "txz"
        return lower.substringAfterLast('.', "")
    }

    fun isEditable(file: UniversalFile): Boolean {
        if (file.isDirectory) return false
        val ext = extractExtension(file.name)
        return ext in EDITABLE_TEXT_EXTENSIONS ||
            ext in EDITABLE_TABLE_EXTENSIONS ||
            ext in EDITABLE_IMAGE_EXTENSIONS ||
            file.name.lowercase(Locale.US).endsWith(".todo.txt")
    }

    fun isApk(file: UniversalFile): Boolean {
        if (file.isDirectory) return false
        return extractExtension(file.name) == "apk"
    }

    fun classify(file: UniversalFile): FileOpenCategory {
        if (file.isDirectory) return FileOpenCategory.PLAYABLE_OR_VIEWABLE_MEDIA
        val ext = extractExtension(file.name)
        return when {
            ext == "apk" -> FileOpenCategory.APK_PACKAGE
            isEditable(file) -> FileOpenCategory.EDITABLE_FILE
            ext in PLAYABLE_VIDEO_EXTENSIONS ||
                ext in PLAYABLE_AUDIO_EXTENSIONS ||
                ext in VIEWABLE_DOCUMENT_EXTENSIONS ||
                ext in VIEWABLE_ARCHIVE_EXTENSIONS ||
                ext in VIEWABLE_READONLY_IMAGE_EXTENSIONS -> FileOpenCategory.PLAYABLE_OR_VIEWABLE_MEDIA
            else -> FileOpenCategory.NON_EDITABLE_BINARY
        }
    }
}
