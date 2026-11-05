package com.troikoss.continuum_explorer.ui.components

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import java.io.File

/**
 * ContinuumEditorWindow
 *
 * Universal file editing & preview workspace framework component.
 * Extends [ViewAreaWindow] to provide an integrated layout container for reading incoming
 * Android Document File URIs, detecting extensions/MIME groups, and dynamically inflating
 * the correct functional layout:
 * 1. TEXT: CodeEditor (Sora) + Markwon split-view Markdown renderer
 * 2. IMAGE: PhotoEditorView with interactive drawing layers & PNG export
 * 3. PDF: Android-PdfViewer + Apache PDFBox page addition & note stamping
 * 4. AUDIO_VIDEO: Jetpack Media3 ExoPlayer surface view
 * 5. OFFICE: Apache POI structure for Word (DOCX), Excel (XLSX), PowerPoint (PPTX)
 */
class ContinuumEditorWindow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewAreaWindow(context, attrs, defStyleAttr) {

    /**
     * Entry point to load an incoming Android Document File URI or file reference.
     */
    fun openDocumentUri(uri: Uri, fileName: String? = null, fileRef: File? = null) {
        loadFile(uri, fileName, fileRef)
    }
}
