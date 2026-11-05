package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Backend helper applying Markdown syntax transformations to TextFieldValue.
 */
object MarkdownFormatter {

    fun applyFormat(
        currentValue: TextFieldValue,
        prefix: String,
        suffix: String,
        defaultPlaceholder: String = ""
    ): TextFieldValue {
        val text = currentValue.text
        val selection = currentValue.selection

        val selectedText = if (selection.collapsed) {
            defaultPlaceholder
        } else {
            text.substring(selection.start, selection.end)
        }

        val replacement = "$prefix$selectedText$suffix"
        val newText = text.substring(0, selection.start) + replacement + text.substring(selection.end)

        val newCursorPosition = if (selection.collapsed && defaultPlaceholder.isEmpty()) {
            selection.start + prefix.length
        } else {
            selection.start + replacement.length
        }

        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPosition)
        )
    }
}
