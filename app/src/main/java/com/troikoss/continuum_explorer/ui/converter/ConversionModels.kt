package com.troikoss.continuum_explorer.ui.converter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import java.util.UUID

/**
 * High-level conversion categories modeled after VERT.sh (`VERT-sh/VERT`),
 * running 100% locally on Android with zero file size limits or cloud uploads.
 */
enum class ConversionCategory(
    val id: String,
    val title: String,
    val shortTitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val description: String
) {
    ALL(
        id = "all",
        title = "All Formats",
        shortTitle = "All",
        icon = Icons.Default.AutoAwesomeMosaic,
        accentColor = Color(0xFF0EA5E9),
        description = "Unified batch queue across images, documents, data tables, media & archives"
    ),
    IMAGE(
        id = "image",
        title = "Images & Graphics",
        shortTitle = "Images",
        icon = Icons.Default.Image,
        accentColor = Color(0xFF10B981),
        description = "PNG, JPG, WEBP, BMP, ICO, SVG rasterization & single-page PDF"
    ),
    DOCUMENT_DATA(
        id = "document_data",
        title = "Documents & Data",
        shortTitle = "Docs & Data",
        icon = Icons.Default.Description,
        accentColor = Color(0xFF6366F1),
        description = "MD, HTML, PDF, TXT, JSON, CSV, TSV, XML, YAML, DOCX & XLSX"
    ),
    AUDIO_VIDEO(
        id = "audio_video",
        title = "Audio & Video",
        shortTitle = "Audio/Video",
        icon = Icons.Default.AudioFile,
        accentColor = Color(0xFFF59E0B),
        description = "MP4, WEBM, MKV, MOV, MP3, WAV, M4A, AAC, FLAC & video frame capture"
    ),
    ARCHIVE(
        id = "archive",
        title = "Archives & Packages",
        shortTitle = "Archives",
        icon = Icons.Default.Archive,
        accentColor = Color(0xFFEC4899),
        description = "ZIP (with optional AES-256 password), TAR, GZ & TAR.GZ / TGZ repacking"
    )
}

enum class ConversionStatus {
    IDLE,
    CONVERTING,
    COMPLETED,
    FAILED
}

typealias ConversionJobStatus = ConversionStatus

enum class OutputDestinationMode(val title: String, val subtitle: String) {
    CONVERTED_SUBFOLDER("Converted Subfolder", "Saves inside a '/Converted/' folder next to the source file"),
    SAME_AS_SOURCE("Original Folder", "Saves directly alongside the original file"),
    CUSTOM_FOLDER("Custom Folder", "Saves all converted outputs into a chosen folder")
}

/**
 * Per-file or batch tuning parameters for image quality, resolution scaling,
 * EXIF privacy, PDF page sizing, audio PCM formatting, and archive encryption.
 */
data class ConversionTuningConfig(
    // Image tuning
    val imageQuality: Int = 90,              // 10..100
    val maxDimensionPx: Int = 0,             // 0 = Original, 3840 = 4K, 1920 = 1080p, 1280 = 720p
    val stripExifMetadata: Boolean = true,   // Privacy-first default like VERT
    val jpgBackgroundColorHex: Long = 0xFFFFFFFF,
    val icoSizePx: Int = 256,                // 32, 64, 128, 256
    // Document / Data tuning
    val prettyPrintData: Boolean = true,
    val pdfRenderDpi: Int = 180,             // For PDF -> PNG/JPG/WEBP
    // Media tuning
    val audioSampleRateHz: Int = 44100,      // 22050, 44100, 48000
    val audioMonoChannel: Boolean = false,   // false = Stereo, true = Mono
    val stripAudioFromVideo: Boolean = false,// For MP4 remuxing without audio track
    val videoFrameTimestampSec: Int = 1,     // For Video -> PNG/JPG thumbnail extraction
    // Archive tuning
    val zipCompressionLevel: Int = 6,        // 0 (Store) .. 9 (Ultra)
    val zipPassword: String = ""             // Optional AES-256 ZIP password via Zip4j
)

/**
 * Represents a single file in the VERT Batch Conversion Queue.
 */
data class ConverterQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val sourceFile: File,
    val sourceExtension: String,
    val category: ConversionCategory,
    val availableTargets: List<String>,
    var targetFormat: String,
    var tuning: ConversionTuningConfig = ConversionTuningConfig(),
    var status: ConversionStatus = ConversionStatus.IDLE,
    var progress: Float = 0f,
    var statusMessage: String = "Ready",
    var outputFile: File? = null,
    var errorMessage: String? = null
) {
    var statusDetail: String
        get() = statusMessage
        set(value) { statusMessage = value }
}

/**
 * Format compatibility matrix mapping every supported input extension to its category,
 * valid output target extensions, and recommended default target.
 */
object ConversionFormatMatrix {

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "ico", "svg")
    private val docDataExtensions = setOf(
        "md", "markdown", "html", "htm", "txt", "log",
        "json", "csv", "tsv", "xml", "yaml", "yml",
        "pdf", "docx", "xlsx"
    )
    private val audioVideoExtensions = setOf(
        "mp4", "webm", "mkv", "mov", "3gp",
        "mp3", "wav", "m4a", "aac", "flac", "ogg"
    )
    private val archiveExtensions = setOf("zip", "tar", "gz", "tgz", "jar")

    fun resolveSourceExtension(file: File): String {
        val lowerName = file.name.lowercase()
        return when {
            lowerName.endsWith(".tar.gz") -> "tgz"
            else -> file.extension.lowercase().trimStart('.')
        }
    }

    fun resolveCategory(extension: String): ConversionCategory {
        val ext = extension.lowercase().trimStart('.')
        return when (ext) {
            in imageExtensions -> ConversionCategory.IMAGE
            in docDataExtensions -> ConversionCategory.DOCUMENT_DATA
            in audioVideoExtensions -> ConversionCategory.AUDIO_VIDEO
            in archiveExtensions -> ConversionCategory.ARCHIVE
            else -> ConversionCategory.DOCUMENT_DATA
        }
    }

    fun isSupportedFile(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
        val ext = resolveSourceExtension(file)
        return getAvailableTargets(ext).isNotEmpty()
    }

    fun getAvailableTargets(sourceExtension: String): List<String> {
        return when (val ext = sourceExtension.lowercase().trimStart('.')) {
            // Images & Graphics
            "png" -> listOf("webp", "jpg", "bmp", "ico", "pdf")
            "jpg", "jpeg" -> listOf("png", "webp", "bmp", "ico", "pdf")
            "webp" -> listOf("png", "jpg", "bmp", "ico", "pdf")
            "bmp" -> listOf("png", "webp", "jpg", "ico", "pdf")
            "ico" -> listOf("png", "webp", "jpg", "bmp")
            "svg" -> listOf("png", "webp", "jpg", "pdf", "ico")

            // Documents & Data
            "md", "markdown" -> listOf("html", "pdf", "txt")
            "html", "htm" -> listOf("pdf", "md", "txt")
            "txt", "log" -> listOf("pdf", "html", "md", "json")
            "json" -> listOf("csv", "yaml", "xml", "html", "md", "txt", "pdf")
            "csv" -> listOf("json", "tsv", "yaml", "xml", "html", "md", "txt", "pdf")
            "tsv" -> listOf("csv", "json", "yaml", "xml", "html", "md", "txt", "pdf")
            "yaml", "yml" -> listOf("json", "xml", "csv", "html", "md", "txt", "pdf")
            "xml" -> listOf("json", "yaml", "html", "txt", "pdf")
            "pdf" -> listOf("png", "jpg", "webp", "txt", "md", "html")
            "docx" -> listOf("pdf", "html", "md", "txt")
            "xlsx" -> listOf("csv", "json", "html", "md", "txt", "pdf")

            // Audio & Video
            "mp4", "webm", "mkv", "mov", "3gp" ->
                listOf("m4a", "wav", "aac", "mp3", "mp4", "png", "jpg")
            "mp3", "m4a", "aac", "flac", "ogg" ->
                listOf("wav", "m4a", "aac")
            "wav" ->
                listOf("m4a", "aac", "wav")

            // Archives & Packages
            "zip", "jar" -> listOf("tar", "tgz", "gz", "zip")
            "tar" -> listOf("zip", "tgz", "gz")
            "tgz" -> listOf("zip", "tar")
            "gz" -> listOf("zip", "tar")

            else -> emptyList()
        }.filter { it != sourceExtension.lowercase() || sourceExtension.lowercase() in listOf("mp4", "wav", "zip") }
    }

    fun getDefaultTarget(sourceExtension: String): String {
        val targets = getAvailableTargets(sourceExtension)
        return targets.firstOrNull() ?: "txt"
    }

    /**
     * Summary matrix pairs displayed on the VERT Landing Page for quick scanning.
     */
    fun getFormatSummaryPairs(category: ConversionCategory): List<Pair<String, String>> {
        return when (category) {
            ConversionCategory.ALL -> listOf(
                "PNG / JPG / WEBP / SVG" to "WEBP • PNG • JPG • ICO • BMP • PDF",
                "MD / HTML / DOCX / TXT" to "PDF • HTML • MD • TXT",
                "JSON / CSV / YAML / XML / XLSX" to "CSV • JSON • YAML • XML • MD • PDF",
                "MP4 / MKV / WEBM / MP3 / FLAC" to "WAV (PCM) • M4A • AAC • MP4 • Frame PNG",
                "ZIP / TAR / GZ / TGZ" to "ZIP (AES-256) • TAR • TAR.GZ"
            )
            ConversionCategory.IMAGE -> listOf(
                "PNG" to "WEBP • JPG • BMP • ICO • PDF",
                "JPG / JPEG" to "PNG • WEBP • BMP • ICO • PDF",
                "WEBP" to "PNG • JPG • BMP • ICO • PDF",
                "SVG (Vector)" to "PNG • WEBP • JPG • PDF • ICO",
                "BMP / ICO" to "PNG • WEBP • JPG • PDF"
            )
            ConversionCategory.DOCUMENT_DATA -> listOf(
                "Markdown (.md)" to "HTML5 • PDF • TXT",
                "JSON / YAML / XML" to "CSV • JSON • YAML • XML • HTML • MD • PDF",
                "CSV / TSV / XLSX" to "JSON • YAML • XML • Markdown Table • HTML • PDF",
                "PDF Document" to "PNG / JPG / WEBP Pages • Extracted TXT / MD / HTML",
                "Word (.docx)" to "PDF • HTML • Markdown • TXT"
            )
            ConversionCategory.AUDIO_VIDEO -> listOf(
                "MP4 / WEBM / MKV / MOV" to "M4A / AAC Audio • WAV (16-bit PCM) • Muted MP4",
                "Video Frame Capture" to "High-Res PNG / JPG / WEBP Frame Snapshot",
                "MP3 / FLAC / OGG / M4A" to "WAV (RIFF PCM 44.1/48kHz) • M4A / AAC",
                "WAV Audio" to "Compressed M4A / AAC • Resampled WAV"
            )
            ConversionCategory.ARCHIVE -> listOf(
                "ZIP Archive" to "TAR • TAR.GZ (.tgz) • AES-256 Encrypted ZIP",
                "TAR / TAR.GZ (.tgz)" to "ZIP Archive • Uncompressed TAR",
                "GZIP (.gz)" to "ZIP Archive • TAR"
            )
        }
    }
}
