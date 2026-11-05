package com.troikoss.continuum_explorer.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.editor.pdf.PdfViewerEngine
import com.troikoss.continuum_explorer.utils.FileExplorerState

/**
 * Enhanced PDF Viewer Engine delegate.
 */
@Composable
fun PdfEngineView(
    file: UniversalFile,
    appState: FileExplorerState? = null,
    modifier: Modifier = Modifier
) {
    PdfViewerEngine(
        file = file,
        appState = appState,
        modifier = modifier
    )
}

@Composable
fun PdfViewerEngine(
    file: UniversalFile,
    appState: FileExplorerState? = null,
    modifier: Modifier = Modifier
) {
    com.troikoss.continuum_explorer.ui.editor.pdf.PdfViewerEngine(
        file = file,
        appState = appState,
        modifier = modifier
    )
}
