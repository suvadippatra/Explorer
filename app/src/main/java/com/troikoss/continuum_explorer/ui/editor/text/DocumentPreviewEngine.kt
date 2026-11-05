package com.troikoss.continuum_explorer.ui.editor.text

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Markor-Powered Live Document & Markdown WebView Preview Engine.
 * Supports:
 * - Local WebView assets via file:///android_asset/
 * - KaTeX math rendering ($...$ and $$...$$ + mhchem)
 * - Prism.js syntax highlighting for fenced code blocks
 * - Mermaid.js diagram rendering (```mermaid)
 * - AsciiDoctor (.adoc) & Org-mode (.org) native JS rendering
 * - CSV table rendering & Todo.txt HTML preview
 * - Smooth cursor-line scroll synchronization without reloading HTML on cursor move
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocumentPreviewEngine(
    rawText: String,
    formatType: TextFormatType = TextFormatType.MARKDOWN,
    cursorLine: Int = 1,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val primaryColorHex = String.format("#%06X", (0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb()))
    val surfaceColorHex = if (isDark) "#121212" else "#FAFAFA"
    val onSurfaceColorHex = if (isDark) "#E0E0E0" else "#212121"
    val cardColorHex = if (isDark) "#1E1E1E" else "#F2F4F7"
    val borderColorHex = if (isDark) "#333333" else "#DCDFE4"

    val htmlContent = remember(rawText, formatType, isDark, primaryColorHex) {
        generateHtmlDocument(
            rawText = rawText,
            formatType = formatType,
            primaryColor = primaryColorHex,
            surfaceColor = surfaceColorHex,
            textColor = onSurfaceColorHex,
            cardColor = cardColorHex,
            borderColor = borderColorHex,
            isDark = isDark
        )
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Auto-sync scrolling: scroll preview when cursor line changes without reloading the page
    LaunchedEffect(cursorLine, webViewRef) {
        webViewRef?.let { webView ->
            val totalLines = rawText.lines().size.coerceAtLeast(1)
            val jsScroll = """
                (function() {
                    var el = document.getElementById('line-$cursorLine');
                    if (el) {
                        el.scrollIntoView({behavior: 'smooth', block: 'center'});
                    } else {
                        var totalLines = $totalLines;
                        var targetRatio = ($cursorLine - 1) / totalLines;
                        var maxScroll = document.documentElement.scrollHeight - window.innerHeight;
                        if (maxScroll > 0) {
                            window.scrollTo({top: maxScroll * targetRatio, behavior: 'smooth'});
                        }
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(jsScroll, null)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    defaultFontSize = 16
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                webViewClient = WebViewClient()
                tag = htmlContent
                loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        update = { webView ->
            if (webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
            }
            webViewRef = webView
        },
        modifier = modifier.fillMaxSize()
    )
}

private fun generateHtmlDocument(
    rawText: String,
    formatType: TextFormatType,
    primaryColor: String,
    surfaceColor: String,
    textColor: String,
    cardColor: String,
    borderColor: String,
    isDark: Boolean
): String {
    val bodyHtml = when (formatType) {
        TextFormatType.CSV -> renderCsvToHtml(rawText)
        TextFormatType.TODO_TXT -> renderTodoTxtToHtml(rawText, primaryColor)
        TextFormatType.ORG -> renderOrgModeToHtml(rawText)
        TextFormatType.ASCIIDOC -> renderAsciiDocToHtml(rawText)
        else -> renderMarkdownToHtml(rawText)
    }

    val mermaidTheme = if (isDark) "dark" else "default"
    val extraHeadAssets = buildString {
        append("""<link rel="stylesheet" href="file:///android_asset/katex/katex.min.css">""")
        append("""<link rel="stylesheet" href="file:///android_asset/prism/prism-markor.css">""")
        if (formatType == TextFormatType.ASCIIDOC) {
            append("""<link rel="stylesheet" href="file:///android_asset/asciidoc/asciidoctor.css">""")
            if (isDark) append("""<link rel="stylesheet" href="file:///android_asset/asciidoc/dark.css">""")
        }
        if (formatType == TextFormatType.ORG) {
            append("""<link rel="stylesheet" href="file:///android_asset/orgmode/org.css">""")
        }
    }

    val extraScripts = buildString {
        append("""<script defer src="file:///android_asset/katex/katex.min.js"></script>""")
        append("""<script defer src="file:///android_asset/katex/mhchem.min.js"></script>""")
        append("""<script defer src="file:///android_asset/katex/auto-render.min.js"></script>""")
        append("""<script defer src="file:///android_asset/prism/prism.js"></script>""")
        append("""<script defer src="file:///android_asset/mermaid/mermaid.min.js"></script>""")
        if (formatType == TextFormatType.ASCIIDOC) {
            append("""<script defer src="file:///android_asset/asciidoc/asciidoctor.min.js"></script>""")
        }
        if (formatType == TextFormatType.ORG) {
            append("""<script defer src="file:///android_asset/orgmode/org-bundle.js"></script>""")
        }
        append(
            """
            <script>
            document.addEventListener("DOMContentLoaded", function() {
                try {
                    if (typeof Asciidoctor !== 'undefined') {
                        var adocRaw = document.getElementById('adoc-raw-source');
                        var adocTarget = document.getElementById('adoc-rendered');
                        if (adocRaw && adocTarget) {
                            var asciidoctor = Asciidoctor();
                            adocTarget.innerHTML = asciidoctor.convert(adocRaw.textContent, { safe: 'safe', attributes: { showtitle: true, icons: 'font' } });
                        }
                    }
                } catch (e) { console.error(e); }

                try {
                    if (typeof Org !== 'undefined') {
                        var orgRaw = document.getElementById('org-raw-source');
                        var orgTarget = document.getElementById('org-rendered');
                        if (orgRaw && orgTarget) {
                            var parser = new Org.Parser();
                            var orgDocument = parser.parse(orgRaw.textContent);
                            var orgHTMLDocument = orgDocument.convert(Org.ConverterHTML, { headerOffset: 0, exportFromLineNumber: false, suppressSubScriptHandling: false, suppressAutoLink: false });
                            if (orgHTMLDocument && orgHTMLDocument.toString()) {
                                orgTarget.innerHTML = orgHTMLDocument.toString();
                            }
                        }
                    }
                } catch (e) { console.error(e); }

                try {
                    if (typeof renderMathInElement !== 'undefined') {
                        renderMathInElement(document.body, {
                            delimiters: [
                                {left: '$$', right: '$$', display: true},
                                {left: '$', right: '$', display: false},
                                {left: '\\(', right: '\\)', display: false},
                                {left: '\\[', right: '\\]', display: true}
                            ],
                            throwOnError: false
                        });
                    }
                } catch (e) { console.error(e); }

                try {
                    if (typeof Prism !== 'undefined') {
                        Prism.highlightAll();
                    }
                } catch (e) { console.error(e); }

                try {
                    if (typeof mermaid !== 'undefined') {
                        mermaid.initialize({ startOnLoad: true, theme: '$mermaidTheme', securityLevel: 'loose' });
                        mermaid.init(undefined, document.querySelectorAll('.mermaid'));
                    }
                } catch (e) { console.error(e); }
            });
            </script>
            """.trimIndent()
        )
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            $extraHeadAssets
            <style>
                * { box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    background-color: $surfaceColor;
                    color: $textColor;
                    line-height: 1.65;
                    padding: 16px;
                    margin: 0;
                    word-wrap: break-word;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: $primaryColor;
                    margin-top: 1.15em;
                    margin-bottom: 0.45em;
                    font-weight: 700;
                    line-height: 1.25;
                }
                h1 { font-size: 1.75em; border-bottom: 1px solid $borderColor; padding-bottom: 0.3em; }
                h2 { font-size: 1.4em; border-bottom: 1px solid $borderColor; padding-bottom: 0.2em; }
                h3 { font-size: 1.2em; }
                h4 { font-size: 1.05em; }
                p { margin-top: 0; margin-bottom: 0.9em; }
                a { color: $primaryColor; text-decoration: none; }
                a:hover { text-decoration: underline; }

                .task-list-item { list-style-type: none; margin-left: -1.2em; margin-bottom: 4px; }
                .task-checkbox {
                    margin-right: 0.55em;
                    transform: scale(1.15);
                    vertical-align: middle;
                    accent-color: $primaryColor;
                }

                table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 1em 0;
                    overflow-x: auto;
                    display: block;
                    border: 1px solid $borderColor;
                    border-radius: 8px;
                }
                th, td {
                    border: 1px solid $borderColor;
                    padding: 9px 13px;
                    text-align: left;
                }
                th {
                    background-color: $cardColor;
                    font-weight: 700;
                    color: $primaryColor;
                }
                tr:nth-child(even) {
                    background-color: ${if (isDark) "rgba(255, 255, 255, 0.03)" else "rgba(0, 0, 0, 0.02)"};
                }

                pre {
                    background-color: $cardColor;
                    border: 1px solid $borderColor;
                    border-radius: 8px;
                    padding: 12px 14px;
                    overflow-x: auto;
                    font-family: Consolas, "JetBrains Mono", "Courier New", monospace;
                    font-size: 0.9em;
                }
                code {
                    font-family: Consolas, "JetBrains Mono", "Courier New", monospace;
                    background-color: ${if (isDark) "rgba(255, 255, 255, 0.1)" else "rgba(0, 0, 0, 0.06)"};
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-size: 0.9em;
                }
                pre code {
                    background: transparent;
                    padding: 0;
                }

                blockquote {
                    margin: 1em 0;
                    padding: 8px 16px;
                    border-left: 4px solid $primaryColor;
                    background-color: ${if (isDark) "rgba(255, 255, 255, 0.04)" else "rgba(0, 0, 0, 0.03)"};
                    border-radius: 0 8px 8px 0;
                    font-style: italic;
                }

                .mermaid {
                    background-color: $cardColor;
                    border: 1px solid $borderColor;
                    border-radius: 8px;
                    padding: 12px;
                    margin: 1em 0;
                    text-align: center;
                }

                .todo-badge {
                    display: inline-block;
                    padding: 1px 7px;
                    border-radius: 10px;
                    font-size: 0.8em;
                    font-weight: 700;
                    margin-right: 6px;
                }

                hr {
                    border: none;
                    border-top: 1px solid $borderColor;
                    margin: 1.8em 0;
                }
                img {
                    max-width: 100%;
                    height: auto;
                    border-radius: 8px;
                }
            </style>
            $extraScripts
        </head>
        <body>
            $bodyHtml
        </body>
        </html>
    """.trimIndent()
}

private fun renderMarkdownToHtml(markdown: String): String {
    if (markdown.isBlank()) return "<p><em>Empty document</em></p>"

    val lines = markdown.lines()
    val sb = StringBuilder()
    var inCodeBlock = false
    var isMermaidBlock = false
    var inMathBlock = false
    var inTable = false
    var inUl = false
    var inOl = false

    fun closeListsAndTables() {
        if (inTable) { sb.append("</table>\n"); inTable = false }
        if (inUl) { sb.append("</ul>\n"); inUl = false }
        if (inOl) { sb.append("</ol>\n"); inOl = false }
    }

    lines.forEachIndexed { index, line ->
        val lineNum = index + 1
        val trimmed = line.trim()

        // Multi-line $$ math block toggle
        if (trimmed == "$$") {
            if (!inMathBlock) {
                closeListsAndTables()
                sb.append("<div id=\"line-$lineNum\" class=\"math-display\">$$\n")
                inMathBlock = true
            } else {
                sb.append("\n$$</div>\n")
                inMathBlock = false
            }
            return@forEachIndexed
        }

        if (inMathBlock) {
            sb.append(escapeHtml(line)).append("\n")
            return@forEachIndexed
        }

        // Fenced code block toggle
        if (trimmed.startsWith("```")) {
            if (!inCodeBlock) {
                closeListsAndTables()
                val lang = trimmed.removePrefix("```").trim().lowercase()
                if (lang == "mermaid") {
                    isMermaidBlock = true
                    sb.append("<div id=\"line-$lineNum\" class=\"mermaid\">\n")
                } else {
                    isMermaidBlock = false
                    val langClass = if (lang.isNotEmpty()) " class=\"language-$lang\"" else " class=\"language-text\""
                    sb.append("<pre id=\"line-$lineNum\"><code$langClass>")
                }
                inCodeBlock = true
            } else {
                if (isMermaidBlock) {
                    sb.append("</div>\n")
                    isMermaidBlock = false
                } else {
                    sb.append("</code></pre>\n")
                }
                inCodeBlock = false
            }
            return@forEachIndexed
        }

        if (inCodeBlock) {
            if (isMermaidBlock) {
                sb.append(line).append("\n")
            } else {
                sb.append(escapeHtml(line)).append("\n")
            }
            return@forEachIndexed
        }

        // Markdown Table
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            if (inUl) { sb.append("</ul>\n"); inUl = false }
            if (inOl) { sb.append("</ol>\n"); inOl = false }
            val isHeaderRow = !inTable
            if (!inTable) {
                sb.append("<table id=\"line-$lineNum\">\n")
                inTable = true
            }
            if (trimmed.matches(Regex("^\\|[\\s:-]+\\|.*$"))) {
                return@forEachIndexed
            }
            val cells = trimmed.removeSurrounding("|").split("|").map { it.trim() }
            sb.append("<tr>")
            val cellTag = if (isHeaderRow) "th" else "td"
            for (cell in cells) {
                sb.append("<$cellTag>").append(formatInlineMarkdown(cell)).append("</$cellTag>")
            }
            sb.append("</tr>\n")
            return@forEachIndexed
        } else if (inTable) {
            sb.append("</table>\n")
            inTable = false
        }

        // Markdown or WikiText Headers
        val headerMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        val wikiHeaderMatch = if (headerMatch == null) Regex("^(={1,6})\\s*(.*?)\\s*\\1$").find(trimmed) else null
        if (headerMatch != null || wikiHeaderMatch != null) {
            closeListsAndTables()
            val match = (headerMatch ?: wikiHeaderMatch)!!
            val level = match.groupValues[1].length.coerceIn(1, 6)
            val title = formatInlineMarkdown(match.groupValues[2])
            sb.append("<h$level id=\"line-$lineNum\">$title</h$level>\n")
            return@forEachIndexed
        }

        // Horizontal Rule
        if (trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
            closeListsAndTables()
            sb.append("<hr id=\"line-$lineNum\" />\n")
            return@forEachIndexed
        }

        // Task checkbox: - [ ] or - [x]
        val taskMatch = Regex("^[-*+]\\s+\\[([ xX])\\]\\s+(.*)$").find(trimmed)
        if (taskMatch != null) {
            if (inOl) { sb.append("</ol>\n"); inOl = false }
            if (!inUl) { sb.append("<ul>\n"); inUl = true }
            val checked = taskMatch.groupValues[1].lowercase() == "x"
            val text = formatInlineMarkdown(taskMatch.groupValues[2])
            sb.append("<li class=\"task-list-item\" id=\"line-$lineNum\">")
            sb.append("<input type=\"checkbox\" class=\"task-checkbox\" ${if (checked) "checked" else ""} disabled />")
            sb.append(if (checked) "<del>$text</del>" else text)
            sb.append("</li>\n")
            return@forEachIndexed
        }

        // Unordered list
        if (trimmed.matches(Regex("^[-*+]\\s+(.*)$"))) {
            if (inOl) { sb.append("</ol>\n"); inOl = false }
            if (!inUl) { sb.append("<ul>\n"); inUl = true }
            val text = formatInlineMarkdown(trimmed.replaceFirst(Regex("^[-*+]\\s+"), ""))
            sb.append("<li id=\"line-$lineNum\">$text</li>\n")
            return@forEachIndexed
        } else if (inUl) {
            sb.append("</ul>\n")
            inUl = false
        }

        // Ordered list
        val olMatch = Regex("^\\d+\\.\\s+(.*)$").find(trimmed)
        if (olMatch != null) {
            if (!inOl) { sb.append("<ol>\n"); inOl = true }
            val text = formatInlineMarkdown(olMatch.groupValues[1])
            sb.append("<li id=\"line-$lineNum\">$text</li>\n")
            return@forEachIndexed
        } else if (inOl) {
            sb.append("</ol>\n")
            inOl = false
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val text = formatInlineMarkdown(trimmed.removePrefix(">").trim())
            sb.append("<blockquote id=\"line-$lineNum\">$text</blockquote>\n")
            return@forEachIndexed
        }

        if (trimmed.isEmpty()) {
            sb.append("<br />\n")
            return@forEachIndexed
        }

        sb.append("<p id=\"line-$lineNum\">").append(formatInlineMarkdown(trimmed)).append("</p>\n")
    }

    if (inCodeBlock) {
        if (isMermaidBlock) sb.append("</div>\n") else sb.append("</code></pre>\n")
    }
    if (inMathBlock) sb.append("\n$$</div>\n")
    closeListsAndTables()

    return sb.toString()
}

private fun renderOrgModeToHtml(orgText: String): String {
    if (orgText.isBlank()) return "<p><em>Empty Org document</em></p>"
    val fallbackHtml = buildString {
        orgText.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#+TITLE:", ignoreCase = true) -> {
                    val title = escapeHtml(trimmed.substringAfter(":").trim())
                    append("<h1 id=\"line-$lineNum\">$title</h1>\n")
                }
                trimmed.matches(Regex("^\\*+\\s+.*$")) -> {
                    val stars = trimmed.takeWhile { it == '*' }.length.coerceIn(1, 6)
                    var rest = trimmed.drop(stars).trim()
                    val badge = when {
                        rest.startsWith("TODO ") -> {
                            rest = rest.removePrefix("TODO ").trim()
                            "<span class=\"todo-badge\" style=\"background:#E53935;color:#FFF;\">TODO</span>"
                        }
                        rest.startsWith("DONE ") -> {
                            rest = rest.removePrefix("DONE ").trim()
                            "<span class=\"todo-badge\" style=\"background:#43A047;color:#FFF;\">DONE</span>"
                        }
                        else -> ""
                    }
                    append("<h$stars id=\"line-$lineNum\">$badge${formatInlineMarkdown(rest)}</h$stars>\n")
                }
                else -> append("<p id=\"line-$lineNum\">${formatInlineMarkdown(trimmed)}</p>\n")
            }
        }
    }
    return """
        <script type="text/plain" id="org-raw-source">${escapeHtml(orgText)}</script>
        <div id="org-rendered">$fallbackHtml</div>
    """.trimIndent()
}

private fun renderAsciiDocToHtml(adocText: String): String {
    if (adocText.isBlank()) return "<p><em>Empty AsciiDoc document</em></p>"
    val fallbackHtml = renderMarkdownToHtml(
        adocText.lines().joinToString("\n") { line ->
            val eqMatch = Regex("^(=+)\\s+(.*)$").find(line.trim())
            if (eqMatch != null) {
                "#".repeat(eqMatch.groupValues[1].length.coerceIn(1, 6)) + " " + eqMatch.groupValues[2]
            } else line
        }
    )
    return """
        <script type="text/plain" id="adoc-raw-source">${escapeHtml(adocText)}</script>
        <div id="adoc-rendered">$fallbackHtml</div>
    """.trimIndent()
}

private fun renderTodoTxtToHtml(todoText: String, primaryColor: String): String {
    if (todoText.isBlank()) return "<p><em>No Todo.txt tasks</em></p>"
    val sb = StringBuilder("<div>\n")
    todoText.lines().forEachIndexed { idx, rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEachIndexed
        val isCompleted = line.startsWith("x ")
        var styled = escapeHtml(line)
        styled = styled.replace(Regex("^\\(([A-Z])\\)"), "<span class=\"todo-badge\" style=\"background:#E53935;color:#FFF;\">($1)</span>")
        styled = styled.replace(Regex("(\\+[A-Za-z0-9_-]+)"), "<span class=\"todo-badge\" style=\"background:$primaryColor;color:#FFF;\">$1</span>")
        styled = styled.replace(Regex("(@[A-Za-z0-9_-]+)"), "<span class=\"todo-badge\" style=\"background:#00897B;color:#FFF;\">$1</span>")
        sb.append("<div class=\"task-list-item\" id=\"line-${idx + 1}\" style=\"padding:6px 0;border-bottom:1px solid rgba(128,128,128,0.2);\">")
        sb.append("<input type=\"checkbox\" class=\"task-checkbox\" ${if (isCompleted) "checked" else ""} disabled />")
        sb.append(if (isCompleted) "<del style=\"opacity:0.6;\">$styled</del>" else styled)
        sb.append("</div>\n")
    }
    sb.append("</div>")
    return sb.toString()
}

private fun formatInlineMarkdown(text: String): String {
    var s = escapeHtml(text)

    // Images: ![alt](url)
    s = s.replace(Regex("!\\[(.*?)\\]\\((.*?)\\)"), "<img src=\"$2\" alt=\"$1\" />")

    // Links: [title](url) and WikiLinks [[url|title]] / [[title]]
    s = s.replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\" target=\"_blank\">$1</a>")
    s = s.replace(Regex("\\[\\[([^\\]|]+)\\|([^\\]]+)\\]\\]"), "<a href=\"$1\">$2</a>")
    s = s.replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "<a href=\"$1\">$1</a>")

    // Bold: **text**
    s = s.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")

    // Italic: *text*
    s = s.replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")

    // Strikethrough: ~~text~~
    s = s.replace(Regex("~~(.*?)~~"), "<del>$1</del>")

    // Highlight: ==text==
    s = s.replace(Regex("==(.*?)=="), "<mark>$1</mark>")

    // Inline code: `text`
    s = s.replace(Regex("`([^`]+)`"), "<code>$1</code>")

    return s
}

private fun renderCsvToHtml(csvText: String): String {
    if (csvText.isBlank()) return "<p><em>Empty CSV</em></p>"
    val delimiter = detectDelimiter(csvText)
    val lines = csvText.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return "<p><em>Empty CSV</em></p>"

    val sb = StringBuilder("<table>\n")
    lines.forEachIndexed { index, line ->
        val cells = parseCsvLine(line, delimiter)
        sb.append("<tr id=\"line-${index + 1}\">")
        for (cell in cells) {
            val tag = if (index == 0) "th" else "td"
            sb.append("<$tag>").append(escapeHtml(cell)).append("</$tag>")
        }
        sb.append("</tr>\n")
    }
    sb.append("</table>")
    return sb.toString()
}

private fun escapeHtml(text: String): String {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
