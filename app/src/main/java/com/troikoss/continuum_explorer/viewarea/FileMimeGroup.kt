package com.troikoss.continuum_explorer.viewarea

/**
 * Strict FileMimeGroup categorizing supported file types for universal preview and editing.
 */
enum class FileMimeGroup(val label: String) {
    TEXT("Text Document"),
    IMAGE("Raster & Vector Image"),
    PDF("PDF Document"),
    OFFICE("Office Document"),
    ARCHIVE("Compressed Archive"),
    AUDIO_VIDEO("Audio & Video Media"),
    UNKNOWN("Binary / Unknown");

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "mdown", "mkd", "mkdn", "mdtxt", "mdtext", "text", "asc", "ascii", "nfo", "rtf", "org", "adoc", "asciidoc", "rst",
            "py", "pyw", "html", "htm", "xhtml", "tex", "latex", "json", "json5", "jsonc", "xml", "js", "jsx", "ts", "tsx", "kt", "kts", "java", "c", "cpp", "cc", "cxx",
            "h", "hpp", "hxx", "css", "scss", "less", "sass", "styl", "vue", "svelte", "astro", "php", "asp", "aspx", "jsp",
            "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "properties", "yaml", "yml", "ini", "conf", "config", "cfg", "prefs", "csv", "tsv",
            "log", "sql", "gradle", "toml", "env", "gitignore", "gitattributes", "dockerfile", "makefile", "mk", "cmake", "plist",
            "rb", "go", "rs", "swift", "m", "mm", "r", "lua", "pl", "pm", "tcl", "groovy", "scala", "clj", "cljs", "elisp", "lisp", "hs", "lhs",
            "erl", "ex", "exs", "dart", "cs", "vb", "vbs", "fs", "fsi", "ml", "mli", "pas", "pp", "asm", "s", "v", "vh", "sv", "verilog", "vhdl",
            "diff", "patch", "bib", "vtt", "srt", "ass", "ssa"
        )

        private val IMAGE_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico", "heic", "heif", "tiff", "tif", "avif", "dng"
        )

        private val PDF_EXTENSIONS = setOf(
            "pdf"
        )

        private val OFFICE_EXTENSIONS = setOf(
            "docx", "xlsx", "pptx", "doc", "xls", "ppt", "odt", "ods", "odp"
        )

        private val ARCHIVE_EXTENSIONS = setOf(
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "iso", "apk"
        )

        private val AUDIO_VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "mp3", "wav", "flac", "m4a", "ogg", "opus",
            "aac", "avi", "mov", "webm", "3gp", "ts", "wma", "wmv"
        )

        /**
         * Matches an extension string to its strict [FileMimeGroup].
         */
        fun fromExtension(ext: String?): FileMimeGroup {
            if (ext.isNullOrBlank()) return UNKNOWN
            val cleanExt = ext.lowercase().trim().removePrefix(".")
            return when {
                cleanExt in TEXT_EXTENSIONS -> TEXT
                cleanExt in IMAGE_EXTENSIONS -> IMAGE
                cleanExt in PDF_EXTENSIONS -> PDF
                cleanExt in OFFICE_EXTENSIONS -> OFFICE
                cleanExt in ARCHIVE_EXTENSIONS -> ARCHIVE
                cleanExt in AUDIO_VIDEO_EXTENSIONS -> AUDIO_VIDEO
                else -> UNKNOWN
            }
        }
    }
}
