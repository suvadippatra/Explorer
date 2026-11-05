package com.troikoss.continuum_explorer.ui.editor.text

import java.io.File

enum class TextFormatType(val displayName: String) {
    MARKDOWN("Markdown"),
    PLAIN_TEXT("Plain Text"),
    CSV("CSV / Spreadsheet"),
    TODO_TXT("Todo.txt"),
    ORG("Org-Mode"),
    ASCIIDOC("Asciidoc"),
    STRUCTURED_CONFIG("YAML / JSON Config")
}

sealed interface FormatHandler {
    val formatType: TextFormatType
    val supportsPreview: Boolean
    val supportsTable: Boolean
    val defaultExtension: String
    val displayName: String
}

object MarkdownFormatHandler : FormatHandler {
    override val formatType: TextFormatType = TextFormatType.MARKDOWN
    override val supportsPreview: Boolean = true
    override val supportsTable: Boolean = true
    override val defaultExtension: String = "md"
    override val displayName: String = "Markdown"
}

object PlainTextFormatHandler : FormatHandler {
    override val formatType: TextFormatType = TextFormatType.PLAIN_TEXT
    override val supportsPreview: Boolean = false
    override val supportsTable: Boolean = false
    override val defaultExtension: String = "txt"
    override val displayName: String = "Plain Text"
}

object CsvFormatHandler : FormatHandler {
    override val formatType: TextFormatType = TextFormatType.CSV
    override val supportsPreview: Boolean = true
    override val supportsTable: Boolean = true
    override val defaultExtension: String = "csv"
    override val displayName: String = "CSV / Spreadsheet"
}

object TodoTxtFormatHandler : FormatHandler {
    override val formatType: TextFormatType = TextFormatType.TODO_TXT
    override val supportsPreview: Boolean = false
    override val supportsTable: Boolean = false
    override val defaultExtension: String = "todo.txt"
    override val displayName: String = "Todo.txt"
}

object OrgFormatHandler : FormatHandler {
    override val formatType: TextFormatType = TextFormatType.ORG
    override val supportsPreview: Boolean = true
    override val supportsTable: Boolean = true
    override val defaultExtension: String = "org"
    override val displayName: String = "Org-Mode"
}

object AsciidocFormatHandler : FormatHandler {
    override val formatType: TextFormatType = TextFormatType.ASCIIDOC
    override val supportsPreview: Boolean = true
    override val supportsTable: Boolean = true
    override val defaultExtension: String = "adoc"
    override val displayName: String = "Asciidoc"
}

object StructuredConfigFormatHandler : FormatHandler {
    override val formatType: TextFormatType = TextFormatType.STRUCTURED_CONFIG
    override val supportsPreview: Boolean = false
    override val supportsTable: Boolean = false
    override val defaultExtension: String = "yaml"
    override val displayName: String = "YAML / JSON Config"
}

object FormatRegistry {

    fun detectFormat(fileName: String): TextFormatType {
        val lowerName = fileName.lowercase()
        val extension = lowerName.substringAfterLast('.', "")

        return when {
            lowerName == "todo.txt" || lowerName.endsWith(".todo.txt") || lowerName.endsWith("-todo.txt") -> {
                TextFormatType.TODO_TXT
            }
            extension in listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdtxt", "mdtext") -> {
                TextFormatType.MARKDOWN
            }
            extension in listOf("csv", "tsv") -> {
                TextFormatType.CSV
            }
            extension in listOf("org") -> {
                TextFormatType.ORG
            }
            extension in listOf("adoc", "asciidoc") -> {
                TextFormatType.ASCIIDOC
            }
            extension in listOf("yml", "yaml", "json", "json5", "jsonc", "toml", "ini", "properties") -> {
                TextFormatType.STRUCTURED_CONFIG
            }
            else -> {
                TextFormatType.PLAIN_TEXT
            }
        }
    }

    fun getHandler(formatType: TextFormatType): FormatHandler {
        return when (formatType) {
            TextFormatType.MARKDOWN -> MarkdownFormatHandler
            TextFormatType.PLAIN_TEXT -> PlainTextFormatHandler
            TextFormatType.CSV -> CsvFormatHandler
            TextFormatType.TODO_TXT -> TodoTxtFormatHandler
            TextFormatType.ORG -> OrgFormatHandler
            TextFormatType.ASCIIDOC -> AsciidocFormatHandler
            TextFormatType.STRUCTURED_CONFIG -> StructuredConfigFormatHandler
        }
    }

    fun getHandlerForFile(fileName: String): FormatHandler {
        return getHandler(detectFormat(fileName))
    }
}
