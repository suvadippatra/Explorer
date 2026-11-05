package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

/**
 * Live regex-based dynamic syntax highlighter for MarkorTextEngine.
 * Applies real-time visual token styling without modifying underlying document strings.
 */
class SyntaxHighlighter(
    private val formatType: TextFormatType
) : VisualTransformation {

    private var cachedRawText: String? = null
    private var cachedTransformed: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val currentCached = cachedTransformed
        if (raw == cachedRawText && currentCached != null) {
            return currentCached
        }
        val highlighted = highlightText(raw, formatType)
        val result = TransformedText(highlighted, OffsetMapping.Identity)
        cachedRawText = raw
        cachedTransformed = result
        return result
    }

    companion object {
        @Volatile
        private var lastCompanionInput: Pair<String, TextFormatType>? = null
        @Volatile
        private var lastCompanionOutput: AnnotatedString? = null

        fun highlightText(rawText: String, formatType: TextFormatType): AnnotatedString {
            if (rawText.isEmpty()) return AnnotatedString("")
            val key = rawText to formatType
            if (lastCompanionInput == key) {
                lastCompanionOutput?.let { return it }
            }

            val builder = AnnotatedString.Builder(rawText)

            when (formatType) {
                TextFormatType.MARKDOWN, TextFormatType.ORG, TextFormatType.ASCIIDOC -> {
                    highlightMarkdown(builder, rawText)
                }
                TextFormatType.TODO_TXT -> {
                    highlightTodoTxt(builder, rawText)
                }
                TextFormatType.CSV -> {
                    highlightCsv(builder, rawText)
                }
                TextFormatType.STRUCTURED_CONFIG -> {
                    highlightConfig(builder, rawText)
                }
                TextFormatType.PLAIN_TEXT -> {
                    highlightPlainText(builder, rawText)
                }
            }

            val out = builder.toAnnotatedString()
            lastCompanionInput = key
            lastCompanionOutput = out
            return out
        }

        private fun highlightMarkdown(builder: AnnotatedString.Builder, text: String) {
            // H1: # Heading, = Heading (AsciiDoc), * Heading (Org-mode)
            applyRegex(builder, text, "(?m)^(#|=|\\*)\\s+.*$", SpanStyle(
                color = Color(0xFF1565C0), // Dark Blue
                fontWeight = FontWeight.Bold
            ))
            // Org-mode / AsciiDoc metadata directives (#+TITLE:, :toc:, etc.)
            applyRegex(builder, text, "(?m)^(#\\+[A-Za-z0-9_]+:|:[A-Za-z0-9_-]+:).*$", SpanStyle(
                color = Color(0xFF00897B),
                fontWeight = FontWeight.SemiBold
            ))
            // Org-mode TODO / DONE keywords
            applyRegex(builder, text, "\\b(TODO|NEXT|WAITING)\\b", SpanStyle(
                color = Color(0xFFE53935),
                fontWeight = FontWeight.ExtraBold
            ))
            applyRegex(builder, text, "\\b(DONE|CANCELLED)\\b", SpanStyle(
                color = Color(0xFF43A047),
                fontWeight = FontWeight.ExtraBold
            ))

            // H2: ## Heading
            applyRegex(builder, text, "(?m)^##\\s+.*$", SpanStyle(
                color = Color(0xFF1E88E5), // Blue
                fontWeight = FontWeight.Bold
            ))

            // H3: ### Heading
            applyRegex(builder, text, "(?m)^###\\s+.*$", SpanStyle(
                color = Color(0xFF039BE5), // Light Blue
                fontWeight = FontWeight.SemiBold
            ))

            // H4 through H6: #### - ######
            applyRegex(builder, text, "(?m)^#{4,6}\\s+.*$", SpanStyle(
                color = Color(0xFF00ACC1), // Cyan
                fontWeight = FontWeight.SemiBold
            ))

            // Bold: **text** or __text__
            applyRegex(builder, text, "(\\*\\*|__)(.*?)\\1", SpanStyle(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00796B) // Dark Teal
            ))

            // Italic: *text* or _text_
            applyRegex(builder, text, "(?<![\\*_])(\\*|_)(?![\\*_])(.*?)(?<![\\*_])\\1(?![\\*_])", SpanStyle(
                fontStyle = FontStyle.Italic,
                color = Color(0xFF8E24AA) // Purple
            ))

            // Strikethrough: ~~text~~
            applyRegex(builder, text, "~~(.*?)~~", SpanStyle(
                textDecoration = TextDecoration.LineThrough,
                color = Color(0xFF757575)
            ))

            // Inline code: `code`
            applyRegex(builder, text, "`([^`\n]+)`", SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0x22808080),
                color = Color(0xFFD81B60) // Magenta
            ))

            // Code blocks: ```code```
            applyRegex(builder, text, "(?s)```[a-zA-Z0-9_-]*\n.*?```", SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0x15000000),
                color = Color(0xFF2E7D32) // Forest Green
            ))

            // Links: [text](url)
            applyRegex(builder, text, "\\[(.*?)\\]\\((.*?)\\)", SpanStyle(
                color = Color(0xFF1976D2),
                textDecoration = TextDecoration.Underline
            ))

            // Blockquotes: > quote
            applyRegex(builder, text, "(?m)^>.*$", SpanStyle(
                color = Color(0xFF5D4037), // Brown
                fontStyle = FontStyle.Italic
            ))

            // List Bullets: - or * or +
            applyRegex(builder, text, "(?m)^\\s*[-*+]\\s+", SpanStyle(
                color = Color(0xFFFB8C00), // Orange
                fontWeight = FontWeight.Bold
            ))

            // Numbered List: 1.
            applyRegex(builder, text, "(?m)^\\s*\\d+\\.\\s+", SpanStyle(
                color = Color(0xFFFB8C00),
                fontWeight = FontWeight.Bold
            ))

            // Task Checkbox Unchecked: [ ]
            applyRegex(builder, text, "(?m)^\\s*[-*+]\\s+\\[ \\]", SpanStyle(
                color = Color(0xFFD32F2F), // Red/Amber pending
                fontWeight = FontWeight.Bold
            ))

            // Task Checkbox Checked: [x] or [X]
            applyRegex(builder, text, "(?m)^\\s*[-*+]\\s+\\[[xX]\\]", SpanStyle(
                color = Color(0xFF388E3C), // Green completed
                fontWeight = FontWeight.Bold
            ))

            // Markdown Table separators: | --- | :---: | ---: |
            applyRegex(builder, text, "(?m)^\\s*\\|?\\s*[-:]+[-| :]*\\|?\\s*$", SpanStyle(
                color = Color(0xFF7B1FA2),
                fontWeight = FontWeight.Bold
            ))
        }

        private fun highlightTodoTxt(builder: AnnotatedString.Builder, text: String) {
            // Priority A: (A)
            applyRegex(builder, text, "(?m)^\\(A\\)", SpanStyle(
                color = Color(0xFFE53935), // Red
                fontWeight = FontWeight.ExtraBold
            ))

            // Priority B: (B)
            applyRegex(builder, text, "(?m)^\\(B\\)", SpanStyle(
                color = Color(0xFFFB8C00), // Orange
                fontWeight = FontWeight.Bold
            ))

            // Priority C: (C)
            applyRegex(builder, text, "(?m)^\\(C\\)", SpanStyle(
                color = Color(0xFFFBC02D), // Yellow
                fontWeight = FontWeight.Bold
            ))

            // Priority D through Z: (D)-(Z)
            applyRegex(builder, text, "(?m)^\\([D-Z]\\)", SpanStyle(
                color = Color(0xFF1E88E5), // Blue
                fontWeight = FontWeight.Bold
            ))

            // Completed task: x 2026-10-11 ...
            applyRegex(builder, text, "(?m)^x(\\s+\\d{4}-\\d{2}-\\d{2})?.*$", SpanStyle(
                textDecoration = TextDecoration.LineThrough,
                color = Color(0xFF757575),
                fontStyle = FontStyle.Italic
            ))

            // Projects: +ProjectName
            applyRegex(builder, text, "\\+[a-zA-Z0-9_-]+", SpanStyle(
                color = Color(0xFF00897B), // Teal
                fontWeight = FontWeight.Bold
            ))

            // Contexts: @ContextName
            applyRegex(builder, text, "@[a-zA-Z0-9_-]+", SpanStyle(
                color = Color(0xFF8E24AA), // Purple
                fontWeight = FontWeight.Bold
            ))

            // Key-Value tags: due:2026-10-11, pri:A, etc.
            applyRegex(builder, text, "\\b[a-zA-Z0-9_-]+:[^\\s]+", SpanStyle(
                color = Color(0xFF3949AB), // Indigo
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold
            ))
        }

        private fun highlightCsv(builder: AnnotatedString.Builder, text: String) {
            // Delimiters: commas, semicolons, tabs, pipes
            applyRegex(builder, text, "[,;\t|]", SpanStyle(
                color = Color(0xFFE53935), // Red highlight for delimiters
                fontWeight = FontWeight.ExtraBold
            ))

            // Quoted text fields: "field"
            applyRegex(builder, text, "\"([^\"]*)\"", SpanStyle(
                color = Color(0xFF2E7D32), // Green
                fontWeight = FontWeight.Normal
            ))

            // Numeric data (aligned numbers, currency, decimals, percentages)
            applyRegex(builder, text, "(?<=^|[,;\t|])\\s*[-+]?\\$?\\d+(\\.\\d+)?%?\\s*(?=[,;\t|]|$)", SpanStyle(
                color = Color(0xFF1565C0), // Blue numbers
                fontWeight = FontWeight.SemiBold
            ))
        }

        private fun highlightConfig(builder: AnnotatedString.Builder, text: String) {
            // Keys: "key": or key:
            applyRegex(builder, text, "(?m)^\\s*[\"']?[a-zA-Z0-9_.-]+[\"']?\\s*:", SpanStyle(
                color = Color(0xFF1E88E5), // Blue keys
                fontWeight = FontWeight.Bold
            ))

            // Strings: "value" or 'value'
            applyRegex(builder, text, "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'", SpanStyle(
                color = Color(0xFF2E7D32) // Green strings
            ))

            // Numbers: integer or float
            applyRegex(builder, text, "\\b\\d+(\\.\\d+)?\\b", SpanStyle(
                color = Color(0xFFD81B60), // Magenta numbers
                fontWeight = FontWeight.SemiBold
            ))

            // Booleans & null: true, false, null
            applyRegex(builder, text, "\\b(true|false|null|TRUE|FALSE|NULL|yes|no)\\b", SpanStyle(
                color = Color(0xFF8E24AA), // Purple booleans
                fontWeight = FontWeight.Bold
            ))

            // Brackets & Braces: [], {}
            applyRegex(builder, text, "[\\[\\]{}]", SpanStyle(
                color = Color(0xFFF57C00), // Orange brackets
                fontWeight = FontWeight.Bold
            ))

            // Comments: # or //
            applyRegex(builder, text, "(?m)(#|//).*$", SpanStyle(
                color = Color(0xFF9E9E9E),
                fontStyle = FontStyle.Italic
            ))
        }

        private fun highlightPlainText(builder: AnnotatedString.Builder, text: String) {
            // Highlight URLs in plain text: http:// or https://
            applyRegex(builder, text, "https?://[^\\s]+", SpanStyle(
                color = Color(0xFF1565C0),
                textDecoration = TextDecoration.Underline
            ))

            // Highlight email addresses
            applyRegex(builder, text, "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", SpanStyle(
                color = Color(0xFF00796B),
                textDecoration = TextDecoration.Underline
            ))
        }

        private val patternCache = java.util.concurrent.ConcurrentHashMap<String, Pattern>()

        private fun applyRegex(
            builder: AnnotatedString.Builder,
            text: String,
            regexPattern: String,
            style: SpanStyle
        ) {
            if (text.length > 60_000) return
            try {
                val pattern = patternCache.getOrPut(regexPattern) { Pattern.compile(regexPattern) }
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (start in 0..text.length && end <= text.length && start < end) {
                        builder.addStyle(style, start, end)
                    }
                }
            } catch (_: Exception) {
                // Ignore regex pattern compile/matching exceptions safely
            }
        }
    }
}
