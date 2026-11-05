package com.troikoss.continuum_explorer.ui.editor.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compatibility wrapper delegating to EditorActionBar.
 */
@Composable
fun FormatActionBar(
    formatType: TextFormatType,
    onFormatClick: (prefix: String, suffix: String, placeholder: String) -> Unit,
    onInsertText: (text: String) -> Unit,
    onToggleCsvGrid: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EditorActionBar(
        formatType = formatType,
        onFormatClick = onFormatClick,
        onInsertText = onInsertText,
        onToggleCsvGrid = onToggleCsvGrid,
        modifier = modifier
    )
}
