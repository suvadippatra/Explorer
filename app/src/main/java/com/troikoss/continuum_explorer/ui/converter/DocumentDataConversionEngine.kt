package com.troikoss.continuum_explorer.ui.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringReader

/**
 * 100% On-Device Document & Structured Data Conversion Engine.
 * Supports MD, HTML, PDF, TXT, JSON, CSV, TSV, XML, YAML, DOCX, and XLSX conversions.
 */
object DocumentDataConversionEngine {

    suspend fun convertDocumentOrData(
        context: Context,
        sourceFile: File,
        targetExtension: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val srcExt = ConversionFormatMatrix.resolveSourceExtension(sourceFile)
            val dstExt = targetExtension.lowercase().trimStart('.')
            onProgress(0.15f, "Reading ${sourceFile.name}...")

            // 1. Handle PDF as Source
            if (srcExt == "pdf") {
                return@withContext convertFromPdf(context, sourceFile, dstExt, outputFile, tuning, onProgress)
            }

            // 2. Handle Word (.docx) as Source
            if (srcExt == "docx") {
                return@withContext convertFromDocx(sourceFile, dstExt, outputFile, onProgress)
            }

            // 3. Handle Excel (.xlsx) as Source
            if (srcExt == "xlsx") {
                return@withContext convertFromXlsx(sourceFile, dstExt, outputFile, tuning, onProgress)
            }

            // 4. Text / Markdown / HTML / Structured Data Sources
            val rawText = sourceFile.readText(Charsets.UTF_8)
            onProgress(0.45f, "Transforming ${srcExt.uppercase()} to ${dstExt.uppercase()}...")

            val resultText: String? = when (srcExt) {
                "md", "markdown" -> when (dstExt) {
                    "html" -> markdownToHtmlDocument(sourceFile.nameWithoutExtension, rawText)
                    "txt" -> stripMarkdownFormatting(rawText)
                    "pdf" -> null // Handled via writeTextAsPdf below
                    else -> rawText
                }
                "html", "htm" -> when (dstExt) {
                    "md" -> htmlToMarkdown(rawText)
                    "txt" -> htmlToPlainText(rawText)
                    "pdf" -> null
                    else -> rawText
                }
                "txt", "log" -> when (dstExt) {
                    "html" -> plainTextToHtml(sourceFile.nameWithoutExtension, rawText)
                    "md" -> "# ${sourceFile.nameWithoutExtension}\n\n$rawText\n"
                    "json" -> plainTextLinesToJson(rawText, tuning.prettyPrintData)
                    "pdf" -> null
                    else -> rawText
                }
                "csv", "tsv" -> {
                    val delimiter = if (srcExt == "tsv") '\t' else ','
                    val rows = parseDelimitedTable(rawText, delimiter)
                    when (dstExt) {
                        "json" -> tableToJson(rows, tuning.prettyPrintData)
                        "csv" -> tableToDelimited(rows, ',')
                        "tsv" -> tableToDelimited(rows, '\t')
                        "yaml", "yml" -> tableToYaml(rows)
                        "xml" -> tableToXml(rows)
                        "md" -> tableToMarkdown(rows)
                        "html" -> tableToHtmlDocument(sourceFile.nameWithoutExtension, rows)
                        "txt" -> tableToAlignedPlainText(rows)
                        "pdf" -> null
                        else -> rawText
                    }
                }
                "json" -> {
                    val rows = jsonToTableRows(rawText)
                    when (dstExt) {
                        "csv" -> tableToDelimited(rows, ',')
                        "tsv" -> tableToDelimited(rows, '\t')
                        "yaml", "yml" -> jsonToYaml(rawText)
                        "xml" -> jsonToXml(rawText)
                        "md" -> if (rows.size > 1) tableToMarkdown(rows) else "```json\n$rawText\n```\n"
                        "html" -> if (rows.size > 1) tableToHtmlDocument(sourceFile.nameWithoutExtension, rows) else plainTextToHtml(sourceFile.nameWithoutExtension, rawText)
                        "txt" -> if (rows.size > 1) tableToAlignedPlainText(rows) else rawText
                        "pdf" -> null
                        else -> rawText
                    }
                }
                "yaml", "yml" -> {
                    val jsonEquivalent = yamlToJsonString(rawText, tuning.prettyPrintData)
                    val rows = jsonToTableRows(jsonEquivalent)
                    when (dstExt) {
                        "json" -> jsonEquivalent
                        "xml" -> jsonToXml(jsonEquivalent)
                        "csv" -> tableToDelimited(rows, ',')
                        "md" -> if (rows.size > 1) tableToMarkdown(rows) else "```yaml\n$rawText\n```\n"
                        "html" -> if (rows.size > 1) tableToHtmlDocument(sourceFile.nameWithoutExtension, rows) else plainTextToHtml(sourceFile.nameWithoutExtension, rawText)
                        "txt" -> rawText
                        "pdf" -> null
                        else -> rawText
                    }
                }
                "xml" -> {
                    val jsonEquivalent = xmlToJsonString(rawText, tuning.prettyPrintData)
                    when (dstExt) {
                        "json" -> jsonEquivalent
                        "yaml", "yml" -> jsonToYaml(jsonEquivalent)
                        "html" -> plainTextToHtml(sourceFile.nameWithoutExtension, rawText)
                        "txt" -> xmlToPlainText(rawText)
                        "pdf" -> null
                        else -> rawText
                    }
                }
                else -> rawText
            }

            onProgress(0.80f, "Writing ${outputFile.name}...")
            if (dstExt == "pdf") {
                val printableBody = when (srcExt) {
                    "html", "htm" -> htmlToPlainText(rawText)
                    "csv", "tsv" -> {
                        val rows = parseDelimitedTable(rawText, if (srcExt == "tsv") '\t' else ',')
                        tableToAlignedPlainText(rows)
                    }
                    else -> rawText
                }
                writeTextAsPaginatedPdf(
                    title = sourceFile.name,
                    bodyText = printableBody,
                    outputFile = outputFile,
                    monospace = srcExt in listOf("json", "xml", "yaml", "yml", "csv", "tsv")
                )
            } else {
                outputFile.writeText(resultText ?: rawText, Charsets.UTF_8)
            }

            onProgress(1.0f, "Converted to ${outputFile.name}")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertFromPdf(
        context: Context,
        sourceFile: File,
        dstExt: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        return if (dstExt in listOf("png", "jpg", "jpeg", "webp")) {
            onProgress(0.35f, "Rendering PDF page 1 at ${tuning.pdfRenderDpi} DPI...")
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            var bitmap: Bitmap? = null
            try {
                pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                if (renderer.pageCount <= 0) {
                    return Result.failure(IllegalArgumentException("PDF has 0 pages."))
                }
                page = renderer.openPage(0)
                val scale = (tuning.pdfRenderDpi.coerceIn(72, 300) / 72f)
                val w = (page.width * scale).toInt().coerceIn(100, 4096)
                val h = (page.height * scale).toInt().coerceIn(100, 4096)
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                onProgress(0.80f, "Encoding rendered PDF page to ${dstExt.uppercase()}...")
                FileOutputStream(outputFile).use { fos ->
                    val q = tuning.imageQuality.coerceIn(10, 100)
                    when (dstExt) {
                        "png" -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        "jpg", "jpeg" -> bitmap.compress(Bitmap.CompressFormat.JPEG, q, fos)
                        "webp" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, q, fos)
                            } else {
                                @Suppress("DEPRECATION")
                                bitmap.compress(Bitmap.CompressFormat.WEBP, q, fos)
                            }
                        }
                    }
                }
                onProgress(1.0f, "Rendered PDF page to ${outputFile.name}")
                Result.success(outputFile)
            } finally {
                try { page?.close() } catch (_: Exception) {}
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
                try { if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() } catch (_: Exception) {}
            }
        } else {
            onProgress(0.35f, "Extracting text from PDF via PDFBox...")
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(sourceFile).use { doc ->
                val stripper = PDFTextStripper()
                val extracted = stripper.getText(doc).trim()
                val finalContent = when (dstExt) {
                    "html" -> plainTextToHtml(sourceFile.nameWithoutExtension, extracted)
                    "md" -> "# ${sourceFile.nameWithoutExtension}\n\n$extracted\n"
                    else -> extracted
                }
                outputFile.writeText(finalContent, Charsets.UTF_8)
            }
            onProgress(1.0f, "Extracted PDF text to ${outputFile.name}")
            Result.success(outputFile)
        }
    }

    private fun convertFromDocx(
        sourceFile: File,
        dstExt: String,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        onProgress(0.35f, "Extracting Word (.docx) paragraphs & tables...")
        val paragraphs = mutableListOf<String>()
        FileInputStream(sourceFile).use { fis ->
            XWPFDocument(fis).use { doc ->
                doc.paragraphs.forEach { p ->
                    val text = p.text?.trim().orEmpty()
                    if (text.isNotEmpty()) paragraphs.add(text)
                }
                doc.tables.forEach { table ->
                    table.rows.forEach { row ->
                        val cells = row.tableCells.joinToString(" | ") { it.text.trim() }
                        if (cells.isNotBlank()) paragraphs.add(cells)
                    }
                }
            }
        }
        val joinedText = paragraphs.joinToString("\n\n")
        onProgress(0.75f, "Formatting as ${dstExt.uppercase()}...")
        when (dstExt) {
            "pdf" -> writeTextAsPaginatedPdf(sourceFile.nameWithoutExtension, joinedText, outputFile, false)
            "html" -> outputFile.writeText(plainTextToHtml(sourceFile.nameWithoutExtension, joinedText), Charsets.UTF_8)
            "md" -> outputFile.writeText("# ${sourceFile.nameWithoutExtension}\n\n$joinedText\n", Charsets.UTF_8)
            else -> outputFile.writeText(joinedText, Charsets.UTF_8)
        }
        onProgress(1.0f, "Converted DOCX to ${outputFile.name}")
        return Result.success(outputFile)
    }

    private fun convertFromXlsx(
        sourceFile: File,
        dstExt: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        onProgress(0.35f, "Reading Excel (.xlsx) workbook sheets...")
        val rows = mutableListOf<List<String>>()
        val formatter = DataFormatter()
        FileInputStream(sourceFile).use { fis ->
            XSSFWorkbook(fis).use { workbook ->
                if (workbook.numberOfSheets > 0) {
                    val sheet = workbook.getSheetAt(0)
                    for (row in sheet) {
                        val lastCell = row.lastCellNum.toInt().coerceAtLeast(0)
                        val rowCells = (0 until lastCell).map { colIdx ->
                            val cell = row.getCell(colIdx)
                            if (cell != null) formatter.formatCellValue(cell).trim() else ""
                        }
                        if (rowCells.any { it.isNotEmpty() }) {
                            rows.add(rowCells)
                        }
                    }
                }
            }
        }
        onProgress(0.75f, "Writing ${dstExt.uppercase()} output...")
        when (dstExt) {
            "pdf" -> writeTextAsPaginatedPdf(
                sourceFile.nameWithoutExtension,
                tableToAlignedPlainText(rows),
                outputFile,
                monospace = true
            )
            "json" -> outputFile.writeText(tableToJson(rows, tuning.prettyPrintData), Charsets.UTF_8)
            "html" -> outputFile.writeText(tableToHtmlDocument(sourceFile.nameWithoutExtension, rows), Charsets.UTF_8)
            "md" -> outputFile.writeText(tableToMarkdown(rows), Charsets.UTF_8)
            "txt" -> outputFile.writeText(tableToAlignedPlainText(rows), Charsets.UTF_8)
            else -> outputFile.writeText(tableToDelimited(rows, ','), Charsets.UTF_8)
        }
        onProgress(1.0f, "Converted XLSX to ${outputFile.name}")
        return Result.success(outputFile)
    }

    // --- Markdown / HTML / Plain Text Helpers ---

    private fun markdownToHtmlDocument(title: String, md: String): String {
        val lines = md.lines()
        val body = StringBuilder()
        var inCodeBlock = false
        for (line in lines) {
            if (line.trim().startsWith("```")) {
                if (!inCodeBlock) {
                    body.append("<pre><code>")
                    inCodeBlock = true
                } else {
                    body.append("</code></pre>\n")
                    inCodeBlock = false
                }
                continue
            }
            if (inCodeBlock) {
                body.append(escapeHtml(line)).append("\n")
                continue
            }
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> body.append("<h3>").append(formatInlineMarkdown(trimmed.removePrefix("### "))).append("</h3>\n")
                trimmed.startsWith("## ") -> body.append("<h2>").append(formatInlineMarkdown(trimmed.removePrefix("## "))).append("</h2>\n")
                trimmed.startsWith("# ") -> body.append("<h1>").append(formatInlineMarkdown(trimmed.removePrefix("# "))).append("</h1>\n")
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    body.append("<li>").append(formatInlineMarkdown(trimmed.drop(2))).append("</li>\n")
                trimmed.startsWith("> ") ->
                    body.append("<blockquote>").append(formatInlineMarkdown(trimmed.removePrefix("> "))).append("</blockquote>\n")
                trimmed.isEmpty() -> body.append("<br/>\n")
                else -> body.append("<p>").append(formatInlineMarkdown(line)).append("</p>\n")
            }
        }
        if (inCodeBlock) body.append("</code></pre>\n")

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>${escapeHtml(title)}</title>
              <style>
                body { font-family: system-ui, -apple-system, sans-serif; line-height: 1.6; max-width: 820px; margin: 2rem auto; padding: 0 1.2rem; color: #1e293b; background: #ffffff; }
                h1, h2, h3 { color: #0f172a; border-bottom: 1px solid #e2e8f0; padding-bottom: 0.3rem; }
                pre { background: #0f172a; color: #f8fafc; padding: 1rem; border-radius: 8px; overflow-x: auto; }
                code { font-family: monospace; background: #f1f5f9; padding: 0.15rem 0.35rem; border-radius: 4px; }
                pre code { background: transparent; padding: 0; }
                blockquote { border-left: 4px solid #6366f1; margin: 0; padding-left: 1rem; color: #475569; }
                table { border-collapse: collapse; width: 100%; margin: 1rem 0; }
                th, td { border: 1px solid #cbd5e1; padding: 0.5rem 0.75rem; text-align: left; }
                th { background: #f8fafc; }
              </style>
            </head>
            <body>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun formatInlineMarkdown(text: String): String {
        var s = escapeHtml(text)
        s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        s = s.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        s = s.replace(Regex("`(.+?)`"), "<code>$1</code>")
        s = s.replace(Regex("\\[(.+?)]\\((.+?)\\)"), "<a href=\"$2\">$1</a>")
        return s
    }

    private fun stripMarkdownFormatting(md: String): String {
        return md
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("`(.+?)`"), "$1")
            .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1 ($2)")
    }

    private fun htmlToMarkdown(html: String): String {
        return html
            .replace(Regex("(?i)<h1[^>]*>(.*?)</h1>"), "# $1\n\n")
            .replace(Regex("(?i)<h2[^>]*>(.*?)</h2>"), "## $1\n\n")
            .replace(Regex("(?i)<h3[^>]*>(.*?)</h3>"), "### $1\n\n")
            .replace(Regex("(?i)<strong[^>]*>(.*?)</strong>"), "**$1**")
            .replace(Regex("(?i)<b[^>]*>(.*?)</b>"), "**$1**")
            .replace(Regex("(?i)<em[^>]*>(.*?)</em>"), "*$1*")
            .replace(Regex("(?i)<i[^>]*>(.*?)</i>"), "*$1*")
            .replace(Regex("(?i)<li[^>]*>(.*?)</li>"), "- $1\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }

    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("(?i)<style[^>]*>[\\s\\S]*?</style>"), "")
            .replace(Regex("(?i)<script[^>]*>[\\s\\S]*?</script>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h1|h2|h3|h4|li|tr)>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }

    private fun plainTextToHtml(title: String, text: String): String {
        val escaped = escapeHtml(text)
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <title>${escapeHtml(title)}</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 860px; padding: 0 1rem; color: #1e293b; }
                pre { white-space: pre-wrap; word-wrap: break-word; background: #f8fafc; padding: 1rem; border-radius: 8px; border: 1px solid #e2e8f0; }
              </style>
            </head>
            <body>
              <h1>${escapeHtml(title)}</h1>
              <pre>$escaped</pre>
            </body>
            </html>
        """.trimIndent()
    }

    private fun plainTextLinesToJson(text: String, pretty: Boolean): String {
        val arr = JSONArray()
        text.lines().forEachIndexed { index, line ->
            val obj = JSONObject()
            obj.put("line", index + 1)
            obj.put("text", line)
            arr.put(obj)
        }
        return if (pretty) arr.toString(2) else arr.toString()
    }

    // --- Structured Table / JSON / CSV / YAML / XML Helpers ---

    private fun parseDelimitedTable(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        for (line in content.lines()) {
            if (line.isBlank()) continue
            val cells = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' -> {
                        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    ch == delimiter && !inQuotes -> {
                        cells.add(current.toString().trim())
                        current.clear()
                    }
                    else -> current.append(ch)
                }
                i++
            }
            cells.add(current.toString().trim())
            rows.add(cells)
        }
        return rows
    }

    private fun tableToDelimited(rows: List<List<String>>, delimiter: Char): String {
        return rows.joinToString("\n") { row ->
            row.joinToString(delimiter.toString()) { cell ->
                if (cell.contains(delimiter) || cell.contains('"') || cell.contains('\n')) {
                    "\"${cell.replace("\"", "\"\"")}\""
                } else {
                    cell
                }
            }
        }
    }

    private fun tableToJson(rows: List<List<String>>, pretty: Boolean): String {
        if (rows.isEmpty()) return "[]"
        val headers = rows.first().mapIndexed { idx, h -> h.ifBlank { "col_${idx + 1}" } }
        val arr = JSONArray()
        for (r in 1 until rows.size) {
            val row = rows[r]
            val obj = JSONObject()
            for (c in headers.indices) {
                val value = row.getOrNull(c) ?: ""
                val numLong = value.toLongOrNull()
                val numDouble = value.toDoubleOrNull()
                when {
                    value.equals("true", ignoreCase = true) -> obj.put(headers[c], true)
                    value.equals("false", ignoreCase = true) -> obj.put(headers[c], false)
                    numLong != null -> obj.put(headers[c], numLong)
                    numDouble != null -> obj.put(headers[c], numDouble)
                    else -> obj.put(headers[c], value)
                }
            }
            arr.put(obj)
        }
        return if (pretty) arr.toString(2) else arr.toString()
    }

    private fun jsonToTableRows(rawJson: String): List<List<String>> {
        return try {
            val tokener = JSONTokener(rawJson.trim())
            val root = tokener.nextValue()
            val array = when (root) {
                is JSONArray -> root
                is JSONObject -> {
                    val firstArrKey = root.keys().asSequence().firstOrNull { root.opt(it) is JSONArray }
                    if (firstArrKey != null) root.getJSONArray(firstArrKey) else JSONArray().put(root)
                }
                else -> return listOf(listOf("value"), listOf(root.toString()))
            }
            if (array.length() == 0) return emptyList()
            val headers = linkedSetOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item != null) {
                    item.keys().forEach { headers.add(it) }
                } else {
                    headers.add("value")
                }
            }
            val headerList = headers.toList()
            val result = mutableListOf<List<String>>(headerList)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item != null) {
                    result.add(headerList.map { key -> item.opt(key)?.toString() ?: "" })
                } else {
                    result.add(listOf(array.opt(i)?.toString() ?: ""))
                }
            }
            result
        } catch (_: Exception) {
            listOf(listOf("content"), listOf(rawJson))
        }
    }

    private fun tableToYaml(rows: List<List<String>>): String {
        if (rows.isEmpty()) return "[]"
        val headers = rows.first().mapIndexed { idx, h -> h.ifBlank { "col_${idx + 1}" } }
        val sb = StringBuilder()
        for (r in 1 until rows.size) {
            val row = rows[r]
            headers.forEachIndexed { idx, key ->
                val prefix = if (idx == 0) "- " else "  "
                val value = row.getOrNull(idx) ?: ""
                sb.append("$prefix$key: ${escapeYamlScalar(value)}\n")
            }
        }
        return sb.toString()
    }

    private fun tableToXml(rows: List<List<String>>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dataset>\n")
        if (rows.isNotEmpty()) {
            val headers = rows.first().mapIndexed { idx, h ->
                h.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "col_${idx + 1}" }
            }
            for (r in 1 until rows.size) {
                val row = rows[r]
                sb.append("  <record>\n")
                headers.forEachIndexed { idx, tag ->
                    val safeTag = if (tag.first().isDigit()) "n_$tag" else tag
                    val value = escapeHtml(row.getOrNull(idx) ?: "")
                    sb.append("    <$safeTag>$value</$safeTag>\n")
                }
                sb.append("  </record>\n")
            }
        }
        sb.append("</dataset>\n")
        return sb.toString()
    }

    private fun tableToMarkdown(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)
        val headers = (0 until colCount).map { rows.first().getOrNull(it) ?: "Col ${it + 1}" }
        val sb = StringBuilder()
        sb.append("| ").append(headers.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(headers.joinToString(" | ") { "---" }).append(" |\n")
        for (r in 1 until rows.size) {
            val row = (0 until colCount).map { (rows[r].getOrNull(it) ?: "").replace("|", "\\|") }
            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }
        return sb.toString()
    }

    private fun tableToHtmlDocument(title: String, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("<table>\n")
        if (rows.isNotEmpty()) {
            sb.append("  <thead><tr>")
            rows.first().forEach { h -> sb.append("<th>").append(escapeHtml(h)).append("</th>") }
            sb.append("</tr></thead>\n  <tbody>\n")
            for (r in 1 until rows.size) {
                sb.append("    <tr>")
                rows[r].forEach { c -> sb.append("<td>").append(escapeHtml(c)).append("</td>") }
                sb.append("</tr>\n")
            }
            sb.append("  </tbody>\n")
        }
        sb.append("</table>")
        return markdownToHtmlDocument(title, "# $title\n\n")
            .replace("</body>", "$sb\n</body>")
    }

    private fun tableToAlignedPlainText(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)
        val widths = IntArray(colCount) { col ->
            rows.maxOf { row -> (row.getOrNull(col) ?: "").length }.coerceIn(3, 40)
        }
        return rows.joinToString("\n") { row ->
            (0 until colCount).joinToString("  |  ") { col ->
                val cell = (row.getOrNull(col) ?: "").take(40)
                cell.padEnd(widths[col], ' ')
            }
        }
    }

    private fun jsonToYaml(rawJson: String): String {
        return try {
            val root = JSONTokener(rawJson.trim()).nextValue()
            renderYamlNode(root, 0)
        } catch (_: Exception) {
            rawJson
        }
    }

    private fun renderYamlNode(node: Any?, indent: Int): String {
        val pad = "  ".repeat(indent)
        return when (node) {
            is JSONObject -> {
                val sb = StringBuilder()
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (v is JSONObject || v is JSONArray) {
                        sb.append("$pad$k:\n").append(renderYamlNode(v, indent + 1))
                    } else {
                        sb.append("$pad$k: ${escapeYamlScalar(v?.toString() ?: "null")}\n")
                    }
                }
                sb.toString()
            }
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until node.length()) {
                    val v = node.opt(i)
                    if (v is JSONObject || v is JSONArray) {
                        sb.append("$pad-\n").append(renderYamlNode(v, indent + 1))
                    } else {
                        sb.append("$pad- ${escapeYamlScalar(v?.toString() ?: "null")}\n")
                    }
                }
                sb.toString()
            }
            else -> "$pad${escapeYamlScalar(node?.toString() ?: "null")}\n"
        }
    }

    private fun jsonToXml(rawJson: String): String {
        return try {
            val root = JSONTokener(rawJson.trim()).nextValue()
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n${renderXmlNode(root, 1)}</root>\n"
        } catch (_: Exception) {
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>${escapeHtml(rawJson)}</root>\n"
        }
    }

    private fun renderXmlNode(node: Any?, indent: Int): String {
        val pad = "  ".repeat(indent)
        return when (node) {
            is JSONObject -> {
                val sb = StringBuilder()
                val keys = node.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val tag = rawKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "field" }
                    val safeTag = if (tag.first().isDigit()) "n_$tag" else tag
                    val v = node.opt(rawKey)
                    if (v is JSONObject || v is JSONArray) {
                        sb.append("$pad<$safeTag>\n").append(renderXmlNode(v, indent + 1)).append("$pad</$safeTag>\n")
                    } else {
                        sb.append("$pad<$safeTag>${escapeHtml(v?.toString() ?: "")}</$safeTag>\n")
                    }
                }
                sb.toString()
            }
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until node.length()) {
                    val v = node.opt(i)
                    if (v is JSONObject || v is JSONArray) {
                        sb.append("$pad<item>\n").append(renderXmlNode(v, indent + 1)).append("$pad</item>\n")
                    } else {
                        sb.append("$pad<item>${escapeHtml(v?.toString() ?: "")}</item>\n")
                    }
                }
                sb.toString()
            }
            else -> "$pad${escapeHtml(node?.toString() ?: "")}\n"
        }
    }

    private fun yamlToJsonString(yaml: String, pretty: Boolean): String {
        val rootObj = JSONObject()
        val listItems = JSONArray()
        var currentListItem: JSONObject? = null

        for (rawLine in yaml.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("- ")) {
                val rest = trimmed.removePrefix("- ").trim()
                if (rest.contains(":")) {
                    val obj = JSONObject()
                    val k = rest.substringBefore(":").trim()
                    val v = rest.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'")
                    obj.put(k, v)
                    listItems.put(obj)
                    currentListItem = obj
                } else {
                    listItems.put(rest.removeSurrounding("\"").removeSurrounding("'"))
                    currentListItem = null
                }
            } else if (trimmed.contains(":")) {
                val k = trimmed.substringBefore(":").trim()
                val v = trimmed.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'")
                if (currentListItem != null && rawLine.startsWith(" ")) {
                    currentListItem.put(k, v)
                } else if (v.isNotEmpty()) {
                    rootObj.put(k, v)
                }
            }
        }
        return when {
            listItems.length() > 0 && rootObj.length() == 0 ->
                if (pretty) listItems.toString(2) else listItems.toString()
            listItems.length() > 0 -> {
                rootObj.put("items", listItems)
                if (pretty) rootObj.toString(2) else rootObj.toString()
            }
            else -> if (pretty) rootObj.toString(2) else rootObj.toString()
        }
    }

    private fun xmlToJsonString(xml: String, pretty: Boolean): String {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            val root = JSONObject()
            val stack = ArrayDeque<Pair<String, JSONObject>>()
            stack.addLast("root" to root)
            var currentText = StringBuilder()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name ?: "node"
                        val child = JSONObject()
                        for (i in 0 until parser.attributeCount) {
                            child.put("@${parser.getAttributeName(i)}", parser.getAttributeValue(i))
                        }
                        stack.addLast(name to child)
                        currentText.clear()
                    }
                    XmlPullParser.TEXT -> {
                        currentText.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        if (stack.size > 1) {
                            val (tagName, completedObj) = stack.removeLast()
                            val parentObj = stack.last().second
                            val textVal = currentText.toString().trim()
                            val valueToStore: Any = if (completedObj.length() == 0) {
                                textVal
                            } else {
                                if (textVal.isNotEmpty()) completedObj.put("#text", textVal)
                                completedObj
                            }
                            val existing = parentObj.opt(tagName)
                            when (existing) {
                                null -> parentObj.put(tagName, valueToStore)
                                is JSONArray -> existing.put(valueToStore)
                                else -> {
                                    val arr = JSONArray()
                                    arr.put(existing)
                                    arr.put(valueToStore)
                                    parentObj.put(tagName, arr)
                                }
                            }
                            currentText.clear()
                        }
                    }
                }
                eventType = parser.next()
            }
            if (pretty) root.toString(2) else root.toString()
        } catch (_: Exception) {
            JSONObject().put("xml", xml).toString(if (pretty) 2 else 0)
        }
    }

    private fun xmlToPlainText(xml: String): String {
        return xml
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun escapeYamlScalar(value: String): String {
        return if (value.contains(":") || value.contains("#") || value.contains("\n") || value.startsWith(" ")) {
            "\"${value.replace("\"", "\\\"")}\""
        } else {
            value
        }
    }

    /**
     * Renders arbitrary text or tabular content into a paginated A4 PDF document.
     */
    private fun writeTextAsPaginatedPdf(
        title: String,
        bodyText: String,
        outputFile: File,
        monospace: Boolean
    ) {
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 595 // A4 width in PostScript points (72 dpi)
            val pageHeight = 842 // A4 height
            val margin = 40
            val contentWidth = pageWidth - (margin * 2)
            val usableHeight = pageHeight - (margin * 2) - 24

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = if (monospace) 8.5f else 10.5f
                typeface = if (monospace) Typeface.MONOSPACE else Typeface.SANS_SERIF
            }

            val fullText = if (bodyText.isBlank()) "(Empty document)" else bodyText
            val staticLayout = StaticLayout.Builder
                .obtain(fullText, 0, fullText.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.12f)
                .setIncludePad(false)
                .build()

            val totalLines = staticLayout.lineCount
            var currentLine = 0
            var pageNumber = 1

            while (currentLine < totalLines) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                // Header on page 1
                var topY = margin.toFloat()
                if (pageNumber == 1) {
                    canvas.drawText(title.take(64), margin.toFloat(), topY + 12f, titlePaint)
                    topY += 28f
                }

                val startLineTop = staticLayout.getLineTop(currentLine)
                var endLine = currentLine
                while (endLine < totalLines) {
                    val lineBottom = staticLayout.getLineBottom(endLine) - startLineTop
                    if (lineBottom > (usableHeight - (if (pageNumber == 1) 28 else 0))) {
                        break
                    }
                    endLine++
                }
                if (endLine == currentLine) endLine++

                canvas.save()
                canvas.clipRect(
                    margin.toFloat(),
                    topY,
                    (pageWidth - margin).toFloat(),
                    (pageHeight - margin).toFloat()
                )
                canvas.translate(margin.toFloat(), topY - startLineTop)
                staticLayout.draw(canvas)
                canvas.restore()

                pdfDocument.finishPage(page)
                currentLine = endLine
                pageNumber++
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
    }
}
