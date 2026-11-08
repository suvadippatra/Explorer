package com.troikoss.continuum_explorer.rules

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Transform
import androidx.compose.ui.graphics.Color

/**
 * Central Registry of My Suite Mini-Apps and their file extension routing rules.
 * Base64 Codec is marked `isActiveInUi = false` and archived in `com.troikoss.continuum_explorer.archive.base64`.
 */
object MiniAppRegistry {

    val allMiniApps: List<MiniAppRuleDescriptor> = listOf(
        MiniAppRuleDescriptor(
            id = "file_converter",
            title = "File Converter",
            subtitle = "Student Photo/Signature KB Resizer, PDF Compressor, VERT Single & Batch Converter",
            badge = "VERT + Squoosh",
            icon = Icons.Default.Transform,
            accentColor = Color(0xFF0EA5E9),
            supportedExtensions = emptySet(),
            isActiveInUi = true,
            requiresInitialFileFromDisk = false,
            defaultNewFileName = null
        ),
        MiniAppRuleDescriptor(
            id = "text_editor",
            title = "Markor Text & Markdown Studio",
            subtitle = "Markdown, Todo.txt, Org-Mode, AsciiDoc, LaTeX math, Mermaid diagrams & plain notes",
            badge = "Markor Engine",
            icon = Icons.Default.Description,
            accentColor = Color(0xFF4F46E5),
            supportedExtensions = setOf(
                "txt", "md", "markdown", "mdown", "mkd", "org", "adoc", "asciidoc", "rst", "rtf", "log"
            ),
            isActiveInUi = true,
            requiresInitialFileFromDisk = false,
            defaultNewFileName = "QuickNote.md"
        ),
        MiniAppRuleDescriptor(
            id = "code_studio",
            title = "Code Studio",
            subtitle = "Monospace code workbench with line numbers, syntax highlighting, search & UTF-8 stats",
            badge = "Code IDE",
            icon = Icons.Default.Code,
            accentColor = Color(0xFF7C3AED),
            supportedExtensions = setOf(
                "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "scss",
                "json", "xml", "yaml", "yml", "toml", "ini", "conf", "sh", "bash", "c", "cpp", "h",
                "hpp", "sql", "gradle", "properties", "go", "rs", "dart", "php", "rb", "lua"
            ),
            isActiveInUi = true,
            requiresInitialFileFromDisk = false,
            defaultNewFileName = "Main.kt"
        ),
        MiniAppRuleDescriptor(
            id = "csv_editor",
            title = "CSV & Table Studio",
            subtitle = "Visual spreadsheet cell grid editor with row/column insertion & raw CSV sync",
            badge = "Table Grid",
            icon = Icons.Default.TableChart,
            accentColor = Color(0xFFD97706),
            supportedExtensions = setOf("csv", "tsv"),
            isActiveInUi = true,
            requiresInitialFileFromDisk = false,
            defaultNewFileName = "Sheet1.csv"
        ),
        MiniAppRuleDescriptor(
            id = "image_studio",
            title = "PhotoEditor Studio",
            subtitle = "Non-destructive filters, crop, curves, HSL sliders, brush, text & stickers",
            badge = "Image Studio",
            icon = Icons.Default.Image,
            accentColor = Color(0xFF059669),
            supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp"),
            isActiveInUi = true,
            requiresInitialFileFromDisk = true,
            defaultNewFileName = null
        ),
        MiniAppRuleDescriptor(
            id = "base64_codec",
            title = "Base64 & Hex Codec",
            subtitle = "Archived utility preserved in archive/base64/ArchivedBase64Codec.kt",
            badge = "Archived",
            icon = Icons.Default.Code,
            accentColor = Color(0xFF64748B),
            supportedExtensions = emptySet(),
            isActiveInUi = false,
            archivePackagePath = "com.troikoss.continuum_explorer.archive.base64.ArchivedBase64Codec"
        )
    )

    /** Returns only Mini-Apps enabled for active UI display in My Suite. */
    val activeMiniApps: List<MiniAppRuleDescriptor>
        get() = allMiniApps.filter { it.isActiveInUi }

    /** Finds the appropriate active Mini-App for a given file extension. */
    fun findMiniAppForExtension(extension: String): MiniAppRuleDescriptor? {
        val clean = extension.lowercase().trimStart('.')
        return activeMiniApps.firstOrNull { clean in it.supportedExtensions }
    }
}
