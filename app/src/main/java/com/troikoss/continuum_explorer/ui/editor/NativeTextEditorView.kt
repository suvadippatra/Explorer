package com.troikoss.continuum_explorer.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.editor.text.MarkorTextEngine
import com.troikoss.continuum_explorer.utils.FileExplorerState

/**
 * Universal Native Text & Markdown Editor powered by MarkorTextEngine.
 * Implements FormatRegistry routing, Live Syntax Highlighting, Dynamic Action Bar,
 * Live Document Preview with auto-sync scrolling, Interactive CSV Grid Mode, and TOC Drawer.
 */
@Composable
fun NativeTextEditorView(
    file: UniversalFile,
    appState: FileExplorerState,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MarkorTextEngine(
        file = file,
        appState = appState,
        onMenuClick = onMenuClick,
        modifier = modifier
    )
}
