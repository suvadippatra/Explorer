package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

enum class TextDisplayMode {
    EDIT, PREVIEW, SPLIT
}

data class TextStats(
    val wordCount: Int,
    val charCount: Int,
    val lineCount: Int,
    val readingTimeMin: Int,
    val currentLine: Int = 1,
    val currentCol: Int = 1
)

/**
 * Backend State & Logic Controller for Text, Markdown, Todo.txt, and CSV editing.
 */
class TextEditorStateController(
    initialText: String = ""
) {
    var textFieldValue by mutableStateOf(TextFieldValue(text = initialText))
    var displayMode by mutableStateOf(TextDisplayMode.EDIT)
    var fontSizeSp by mutableFloatStateOf(15f)
    var formatType by mutableStateOf(TextFormatType.PLAIN_TEXT)

    // CSV Grid Mode, Todo.txt Interactive Mode & TOC Drawer States
    var showCsvGrid by mutableStateOf(false)
    var showTodoInteractive by mutableStateOf(false)
    var showTocDrawer by mutableStateOf(false)

    // Search and Replace State
    var isSearchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var replaceQuery by mutableStateOf("")
    var currentMatchIndex by mutableIntStateOf(0)
    var totalMatches by mutableIntStateOf(0)

    // Jump to Line State
    var showJumpToLineDialog by mutableStateOf(false)

    // Text Undo / Redo Stack
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    val textContent: String
        get() = textFieldValue.text

    fun updateText(newValue: TextFieldValue) {
        val oldText = textFieldValue.text
        val newText = newValue.text
        if (newText != oldText) {
            pushUndo(oldText)
            // Smart Markor list auto-continuation when pressing Enter
            val cursor = newValue.selection.start
            if (newText.length == oldText.length + 1 &&
                cursor > 0 &&
                newText[cursor - 1] == '\n'
            ) {
                val beforeNewline = newText.substring(0, cursor - 1)
                val lineStart = beforeNewline.lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
                val prevLine = beforeNewline.substring(lineStart)

                val taskMatch = Regex("^(\\s*[-*+]\\s+\\[)[ xX](\\]\\s+)(.*)$").find(prevLine)
                val bulletMatch = if (taskMatch == null) Regex("^(\\s*[-*+]\\s+)(.*)$").find(prevLine) else null
                val numberedMatch = if (taskMatch == null && bulletMatch == null) Regex("^(\\s*)(\\d+)(\\.\\s+)(.*)$").find(prevLine) else null

                when {
                    taskMatch != null -> {
                        val prefix = "${taskMatch.groupValues[1]} ${taskMatch.groupValues[2]}"
                        val body = taskMatch.groupValues[3]
                        if (body.isBlank()) {
                            // Empty checkbox line -> clear prefix
                            val cleaned = newText.substring(0, lineStart) + newText.substring(cursor)
                            textFieldValue = TextFieldValue(cleaned, TextRange(lineStart))
                            return
                        } else {
                            val inserted = newText.substring(0, cursor) + prefix + newText.substring(cursor)
                            textFieldValue = TextFieldValue(inserted, TextRange(cursor + prefix.length))
                            return
                        }
                    }
                    bulletMatch != null -> {
                        val prefix = bulletMatch.groupValues[1]
                        val body = bulletMatch.groupValues[2]
                        if (body.isBlank()) {
                            val cleaned = newText.substring(0, lineStart) + newText.substring(cursor)
                            textFieldValue = TextFieldValue(cleaned, TextRange(lineStart))
                            return
                        } else {
                            val inserted = newText.substring(0, cursor) + prefix + newText.substring(cursor)
                            textFieldValue = TextFieldValue(inserted, TextRange(cursor + prefix.length))
                            return
                        }
                    }
                    numberedMatch != null -> {
                        val indent = numberedMatch.groupValues[1]
                        val num = numberedMatch.groupValues[2].toIntOrNull() ?: 1
                        val sep = numberedMatch.groupValues[3]
                        val body = numberedMatch.groupValues[4]
                        if (body.isBlank()) {
                            val cleaned = newText.substring(0, lineStart) + newText.substring(cursor)
                            textFieldValue = TextFieldValue(cleaned, TextRange(lineStart))
                            return
                        } else {
                            val prefix = "$indent${num + 1}$sep"
                            val inserted = newText.substring(0, cursor) + prefix + newText.substring(cursor)
                            textFieldValue = TextFieldValue(inserted, TextRange(cursor + prefix.length))
                            return
                        }
                    }
                }
            }
        }
        textFieldValue = newValue
    }

    fun updateTextString(newText: String) {
        if (newText != textFieldValue.text) {
            pushUndo(textFieldValue.text)
            textFieldValue = TextFieldValue(
                text = newText,
                selection = TextRange(textFieldValue.selection.start.coerceIn(0, newText.length))
            )
        }
    }

    private fun pushUndo(text: String) {
        undoStack.add(text)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun insertTextAtCursor(insertString: String) {
        val current = textFieldValue.text
        val selection = textFieldValue.selection
        val newText = current.substring(0, selection.start) + insertString + current.substring(selection.end)
        val newCursor = selection.start + insertString.length
        updateText(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
        )
    }

    fun applyWrapFormat(prefix: String, suffix: String, defaultPlaceholder: String) {
        textFieldValue = MarkdownFormatter.applyFormat(
            currentValue = textFieldValue,
            prefix = prefix,
            suffix = suffix,
            defaultPlaceholder = defaultPlaceholder
        )
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(textFieldValue.text)
            textFieldValue = TextFieldValue(text = previous, selection = TextRange(previous.length.coerceAtMost(textFieldValue.selection.start)))
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(textFieldValue.text)
            textFieldValue = TextFieldValue(text = next, selection = TextRange(next.length.coerceAtMost(textFieldValue.selection.start)))
        }
    }

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun copySelectedText(clipboard: androidx.compose.ui.platform.ClipboardManager) {
        val selection = textFieldValue.selection
        val textToCopy = if (selection.collapsed) {
            getCurrentLineText()
        } else {
            val min = selection.min.coerceIn(0, textFieldValue.text.length)
            val max = selection.max.coerceIn(0, textFieldValue.text.length)
            textFieldValue.text.substring(min, max)
        }
        if (textToCopy.isNotEmpty()) {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
        }
    }

    fun cutSelectedText(clipboard: androidx.compose.ui.platform.ClipboardManager) {
        val selection = textFieldValue.selection
        if (!selection.collapsed) {
            val min = selection.min.coerceIn(0, textFieldValue.text.length)
            val max = selection.max.coerceIn(0, textFieldValue.text.length)
            val textToCut = textFieldValue.text.substring(min, max)
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(textToCut))
            insertTextAtCursor("")
        }
    }

    fun pasteFromClipboard(clipboard: androidx.compose.ui.platform.ClipboardManager) {
        val clip = clipboard.getText()?.text ?: return
        insertTextAtCursor(clip)
    }

    fun indent() {
        insertTextAtCursor("  ")
    }

    fun unindent() {
        val current = textFieldValue.text
        val cursor = textFieldValue.selection.start
        val lineStart = current.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        if (current.startsWith("  ", lineStart)) {
            val newText = current.substring(0, lineStart) + current.substring(lineStart + 2)
            updateText(TextFieldValue(newText, TextRange((cursor - 2).coerceAtLeast(lineStart))))
        } else if (current.startsWith("\t", lineStart)) {
            val newText = current.substring(0, lineStart) + current.substring(lineStart + 1)
            updateText(TextFieldValue(newText, TextRange((cursor - 1).coerceAtLeast(lineStart))))
        }
    }

    private fun getCurrentLineText(): String {
        val current = textFieldValue.text
        val cursor = textFieldValue.selection.start.coerceIn(0, current.length)
        val start = current.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val end = current.indexOf('\n', cursor).let { if (it == -1) current.length else it }
        return if (start <= end && end <= current.length) current.substring(start, end) else ""
    }

    // Line & Offset Navigation
    fun jumpToLine(lineNumber: Int) {
        val text = textFieldValue.text
        val lines = text.lines()
        val targetLineIndex = (lineNumber - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))

        var charOffset = 0
        for (i in 0 until targetLineIndex) {
            charOffset += lines[i].length + 1
        }
        val safeOffset = charOffset.coerceIn(0, text.length)
        textFieldValue = textFieldValue.copy(
            selection = TextRange(safeOffset)
        )
    }

    fun jumpToCharOffset(offset: Int) {
        val safeOffset = offset.coerceIn(0, textFieldValue.text.length)
        textFieldValue = textFieldValue.copy(
            selection = TextRange(safeOffset)
        )
    }

    // Search & Replace Implementation
    fun performSearch(query: String) {
        searchQuery = query
        if (query.isEmpty()) {
            totalMatches = 0
            currentMatchIndex = 0
            return
        }

        val text = textFieldValue.text
        val matches = mutableListOf<Int>()
        var index = text.indexOf(query, 0, ignoreCase = true)
        while (index >= 0) {
            matches.add(index)
            index = text.indexOf(query, index + query.length.coerceAtLeast(1), ignoreCase = true)
        }

        totalMatches = matches.size
        if (matches.isNotEmpty()) {
            currentMatchIndex = 1
            highlightMatch(matches[0], query.length)
        } else {
            currentMatchIndex = 0
        }
    }

    fun findNext() {
        if (searchQuery.isEmpty() || totalMatches == 0) return
        val text = textFieldValue.text
        val currentCursor = textFieldValue.selection.end
        var nextIndex = text.indexOf(searchQuery, currentCursor, ignoreCase = true)
        if (nextIndex < 0) {
            nextIndex = text.indexOf(searchQuery, 0, ignoreCase = true)
        }
        if (nextIndex >= 0) {
            highlightMatch(nextIndex, searchQuery.length)
            currentMatchIndex = ((currentMatchIndex % totalMatches) + 1)
        }
    }

    fun findPrevious() {
        if (searchQuery.isEmpty() || totalMatches == 0) return
        val text = textFieldValue.text
        val currentCursor = (textFieldValue.selection.start - 1).coerceAtLeast(0)
        var prevIndex = text.lastIndexOf(searchQuery, currentCursor, ignoreCase = true)
        if (prevIndex < 0) {
            prevIndex = text.lastIndexOf(searchQuery, text.length, ignoreCase = true)
        }
        if (prevIndex >= 0) {
            highlightMatch(prevIndex, searchQuery.length)
            currentMatchIndex = if (currentMatchIndex <= 1) totalMatches else currentMatchIndex - 1
        }
    }

    fun replaceCurrent() {
        if (searchQuery.isEmpty()) return
        val selection = textFieldValue.selection
        val selectedText = textFieldValue.text.substring(selection.start, selection.end)
        if (selectedText.equals(searchQuery, ignoreCase = true)) {
            val newText = textFieldValue.text.replaceRange(selection.start, selection.end, replaceQuery)
            updateTextString(newText)
            findNext()
        } else {
            findNext()
        }
    }

    fun replaceAll() {
        if (searchQuery.isEmpty()) return
        val regex = Regex.escape(searchQuery).toRegex(RegexOption.IGNORE_CASE)
        val newText = textFieldValue.text.replace(regex, replaceQuery)
        updateTextString(newText)
        totalMatches = 0
        currentMatchIndex = 0
    }

    private fun highlightMatch(startIndex: Int, length: Int) {
        val safeStart = startIndex.coerceIn(0, textFieldValue.text.length)
        val safeEnd = (startIndex + length).coerceIn(0, textFieldValue.text.length)
        textFieldValue = textFieldValue.copy(
            selection = TextRange(safeStart, safeEnd)
        )
    }

    fun formatAlignCsv() {
        val aligned = formatAlignCsvText(textFieldValue.text)
        updateTextString(aligned)
    }

    // Cursor position and statistics
    val stats: TextStats
        get() {
            val text = textFieldValue.text
            val charCount = text.length
            val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
            val lines = text.lines()
            val lineCount = if (text.isEmpty()) 0 else lines.size
            val readingTimeMin = (wordCount / 200).coerceAtLeast(1)

            // Calculate current line and column from selection
            val cursor = textFieldValue.selection.start.coerceIn(0, text.length)
            var currentLine = 1
            var currentCol = 1
            var countedChars = 0

            for ((index, line) in lines.withIndex()) {
                val nextCount = countedChars + line.length + 1 // +1 for newline
                if (cursor < nextCount) {
                    currentLine = index + 1
                    currentCol = (cursor - countedChars) + 1
                    break
                }
                countedChars = nextCount
            }

            return TextStats(wordCount, charCount, lineCount, readingTimeMin, currentLine, currentCol)
        }
}

@Composable
fun rememberTextEditorStateController(initialText: String): TextEditorStateController {
    return remember(initialText) { TextEditorStateController(initialText) }
}
