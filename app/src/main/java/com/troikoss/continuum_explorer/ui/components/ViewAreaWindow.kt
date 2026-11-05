package com.troikoss.continuum_explorer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.barteksc.pdfviewer.PDFView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.troikoss.continuum_explorer.viewarea.FileMimeGroup
import com.troikoss.continuum_explorer.viewarea.FileMimeParser
import io.github.rosemoe.sora.widget.CodeEditor
import io.noties.markwon.Markwon
import ja.burhanrashid52.photoeditor.OnSaveBitmap
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.SaveSettings
import kotlinx.coroutines.*
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import com.troikoss.continuum_explorer.server.ContinuumHubService
import java.io.*

/**
 * ViewAreaWindow
 *
 * An independent, modern, self-contained workspace frame designed for
 * previewing and editing diverse file types within the Explorer multi-window desktop interface.
 * Operates completely inside the workspace layout without executing external app intents.
 */
open class ViewAreaWindow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Coroutine scope for async background IO operations and auto-save loops
    private val windowScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Active state
    private var currentUri: Uri? = null
    private var currentFileRef: File? = null
    private var currentFileName: String = ""
    private var currentMimeGroup: FileMimeGroup = FileMimeGroup.UNKNOWN
    private var isFullScreen: Boolean = false

    // Player instance for audio/video lifecycle cleanup
    private var activeExoPlayer: ExoPlayer? = null

    // Callbacks
    var onFullScreenToggle: ((Boolean) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    // Command Pattern Undo / Redo Manager
    private val undoRedoManager = EditorUndoRedoManager()

    private val btnUndo: Button = Button(context).apply {
        text = "↺ Undo"
        textSize = 10f
        setTextColor(Color.parseColor("#B0BEC5"))
        setBackgroundColor(Color.TRANSPARENT)
        isEnabled = false
        setOnClickListener {
            undoRedoManager.undo()
            updateUndoRedoButtons()
        }
    }

    private val btnRedo: Button = Button(context).apply {
        text = "↻ Redo"
        textSize = 10f
        setTextColor(Color.parseColor("#B0BEC5"))
        setBackgroundColor(Color.TRANSPARENT)
        isEnabled = false
        setOnClickListener {
            undoRedoManager.redo()
            updateUndoRedoButtons()
        }
    }

    fun updateUndoRedoButtons() {
        btnUndo.isEnabled = undoRedoManager.canUndo
        btnUndo.setTextColor(if (undoRedoManager.canUndo) Color.parseColor("#00E676") else Color.parseColor("#555555"))
        btnRedo.isEnabled = undoRedoManager.canRedo
        btnRedo.setTextColor(if (undoRedoManager.canRedo) Color.parseColor("#00E676") else Color.parseColor("#555555"))
    }

    // Frame UI components
    private val rootLayout: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val headerBar: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(40)
        )
        setBackgroundColor(Color.parseColor("#1E1E1E"))
        setPadding(dpToPx(8), 0, dpToPx(8), 0)
    }

    private val titleText: TextView = TextView(context).apply {
        text = "View Area Workspace"
        setTextColor(Color.parseColor("#E0E0E0"))
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
    }

    private val mimeBadge: TextView = TextView(context).apply {
        text = "[READY]"
        setTextColor(Color.parseColor("#00E676"))
        textSize = 11f
        typeface = Typeface.MONOSPACE
        setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
        setBackgroundColor(Color.parseColor("#263238"))
        visibility = View.GONE
    }

    private val headerActionContainer: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }

    private val btnHubService: ImageButton = ImageButton(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.parseColor("#4CAF50"))
        contentDescription = "Continuum Hub Server"
        setImageResource(com.troikoss.continuum_explorer.R.drawable.ic_network_server)
        layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
            marginStart = dpToPx(4)
        }
        setOnClickListener {
            toggleContinuumHubService()
        }
    }

    private val btnFullScreen: ImageButton = ImageButton(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.parseColor("#B0BEC5"))
        contentDescription = "Toggle Fullscreen"
        layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
            marginStart = dpToPx(4)
        }
        setOnClickListener {
            isFullScreen = !isFullScreen
            updateFullScreenButtonIcon()
            onFullScreenToggle?.invoke(isFullScreen)
        }
    }

    private val btnClose: ImageButton = ImageButton(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.parseColor("#EF5350"))
        contentDescription = "Close Preview"
        layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
            marginStart = dpToPx(4)
        }
        setOnClickListener {
            cleanupActiveResources()
            clearWorkspace()
            onClose?.invoke()
        }
    }

    private val contentContainer: FrameLayout = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        )
        setBackgroundColor(Color.parseColor("#121212"))
    }

    private val statusBar: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(28)
        )
        setBackgroundColor(Color.parseColor("#181818"))
        setPadding(dpToPx(12), 0, dpToPx(12), 0)
    }

    private val statusText: TextView = TextView(context).apply {
        text = "Ready"
        setTextColor(Color.parseColor("#888888"))
        textSize = 11f
        typeface = Typeface.MONOSPACE
        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
    }

    private val pathIndicator: TextView = TextView(context).apply {
        text = ""
        setTextColor(Color.parseColor("#666666"))
        textSize = 10f
        typeface = Typeface.MONOSPACE
        maxLines = 1
    }

    init {
        // Modern dark layout color theme (#121212)
        setBackgroundColor(Color.parseColor("#121212"))

        // Build header
        headerBar.addView(titleText)
        headerBar.addView(mimeBadge)
        headerBar.addView(btnUndo)
        headerBar.addView(btnRedo)
        headerBar.addView(headerActionContainer)
        headerBar.addView(btnHubService)
        headerBar.addView(btnFullScreen)
        headerBar.addView(btnClose)

        // Build status bar
        statusBar.addView(statusText)
        statusBar.addView(pathIndicator)

        rootLayout.addView(headerBar)
        rootLayout.addView(contentContainer)
        rootLayout.addView(statusBar)

        addView(rootLayout)

        updateFullScreenButtonIcon()
        setupPlaceholderStructure()

        // Init PDFBox resource loader safely once
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}
    }

    private fun updateFullScreenButtonIcon() {
        btnFullScreen.setImageResource(
            if (isFullScreen) android.R.drawable.ic_menu_close_clear_cancel
            else android.R.drawable.ic_menu_crop
        )
    }

    // FUTURE IMPLEMENTATION: Universal Preview Engine & Local Network Servers will inject exactly here.

    /**
     * Initializes and toggles the background ContinuumHubService (Ktor port 8080 & Apache FTP port 2121)
     * directly from within the workspace module.
     */
    fun toggleContinuumHubService() {
        if (ContinuumHubService.isRunning) {
            ContinuumHubService.stopService(context)
            btnHubService.setColorFilter(Color.parseColor("#757575"))
            Toast.makeText(context, "Continuum Hub Servers Stopped", Toast.LENGTH_SHORT).show()
            updateStatus("Servers Stopped")
        } else {
            ContinuumHubService.startService(context)
            btnHubService.setColorFilter(Color.parseColor("#00E676"))
            val ip = ContinuumHubService.serverIpAddress
            Toast.makeText(
                context,
                "Continuum Hub Online: http://$ip:8080 (FTP: 2121)",
                Toast.LENGTH_LONG
            ).show()
            updateStatus("Hub Online: http://$ip:8080")
        }
    }

    /**
     * Updates the Hub service button color based on active state.
     */
    fun refreshHubStatus() {
        if (ContinuumHubService.isRunning) {
            btnHubService.setColorFilter(Color.parseColor("#00E676"))
        } else {
            btnHubService.setColorFilter(Color.parseColor("#757575"))
        }
    }

    /**
     * Initializes the placeholder framework inside the workspace frame.
     */
    private fun setupPlaceholderStructure() {
        contentContainer.removeAllViews()
        headerActionContainer.removeAllViews()
        mimeBadge.visibility = View.GONE
        pathIndicator.text = ""

        val placeholder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_view)
                setColorFilter(Color.parseColor("#444444"))
                layoutParams = LinearLayout.LayoutParams(dpToPx(64), dpToPx(64))
            }
            val title = TextView(context).apply {
                text = "Universal View & Edit Workspace"
                setTextColor(Color.parseColor("#9E9E9E"))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(12), 0, dpToPx(4))
            }
            val subtitle = TextView(context).apply {
                text = "Select any file to preview, stream media, or edit directly\n(Text, Markdown, Images, PDFs, Office, Audio & Video)"
                setTextColor(Color.parseColor("#555555"))
                textSize = 12f
                gravity = Gravity.CENTER
            }
            addView(icon)
            addView(title)
            addView(subtitle)
        }
        contentContainer.addView(placeholder)
        updateStatus("Ready")
    }

    /**
     * Entry point to load and view any file inside the ViewAreaWindow container.
     */
    fun loadFile(uri: Uri, fileName: String? = null, fileRef: File? = null) {
        val resolvedName = fileName ?: FileMimeParser.getFileNameFromUri(context, uri)
        if (currentUri == uri && currentFileName == resolvedName && contentContainer.childCount > 0) {
            return
        }

        cleanupActiveResources()

        currentUri = uri
        currentFileRef = fileRef
        currentFileName = resolvedName

        setTitle(resolvedName)
        pathIndicator.text = fileRef?.absolutePath ?: uri.lastPathSegment ?: uri.toString()

        // Categorize file into strict FileMimeGroup
        val mimeGroup = FileMimeParser.parse(context, uri)
        currentMimeGroup = mimeGroup

        mimeBadge.visibility = View.VISIBLE
        mimeBadge.text = "[${mimeGroup.name}]"

        headerBar.visibility = if (mimeGroup == FileMimeGroup.PDF) View.GONE else View.VISIBLE
        statusBar.visibility = if (mimeGroup == FileMimeGroup.PDF) View.GONE else View.VISIBLE

        headerActionContainer.removeAllViews()
        contentContainer.removeAllViews()
        updateStatus("Loading ${mimeGroup.label}...")

        // View Inflation Factory by MIME Group
        when (mimeGroup) {
            FileMimeGroup.TEXT -> inflateTextView(uri, fileRef, FileMimeParser.extractExtension(resolvedName))
            FileMimeGroup.IMAGE -> inflateImageView(uri, fileRef)
            FileMimeGroup.PDF -> inflatePdfView(uri, fileRef)
            FileMimeGroup.AUDIO_VIDEO -> inflateAudioVideoView(uri, fileRef)
            FileMimeGroup.OFFICE -> inflateOfficeView(uri, fileRef, FileMimeParser.extractExtension(resolvedName))
            FileMimeGroup.ARCHIVE -> inflateArchiveView(uri, fileRef)
            FileMimeGroup.UNKNOWN -> inflateFallbackView(uri, fileRef)
        }
    }

    // =========================================================================
    // 1. TEXT FACTORY: CodeEditor, auto-save loops, and Markwon split-pane switch
    // =========================================================================
    private fun inflateTextView(uri: Uri, fileRef: File?, extension: String) {
        val rootTextContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val codeEditor = CodeEditor(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            setTextSize(14f)
            isWordwrap = true
            setBackgroundColor(Color.parseColor("#151515"))
        }

        val markdownScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            setBackgroundColor(Color.parseColor("#1C1C1C"))
            visibility = View.GONE
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val markdownText = TextView(context).apply {
            setTextColor(Color.parseColor("#DEDEDE"))
            textSize = 14f
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        markdownScroll.addView(markdownText)

        rootTextContainer.addView(codeEditor)
        rootTextContainer.addView(markdownScroll)
        contentContainer.addView(rootTextContainer)

        val markwon = Markwon.create(context)
        var isMarkdownSplitActive = false

        fun refreshMarkdown() {
            if (isMarkdownSplitActive) {
                val currentText = codeEditor.text.toString()
                markwon.setMarkdown(markdownText, currentText)
            }
        }

        // Header Actions for Text
        val isMdFile = extension in setOf("md", "markdown")
        val btnMarkdown = Button(context).apply {
            text = if (isMdFile) "Render MD" else "MD Preview"
            textSize = 11f
            setTextColor(Color.parseColor("#4FC3F7"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                isMarkdownSplitActive = !isMarkdownSplitActive
                markdownScroll.visibility = if (isMarkdownSplitActive) View.VISIBLE else View.GONE
                text = if (isMarkdownSplitActive) "Split: ON" else "Split: OFF"
                refreshMarkdown()
            }
        }
        headerActionContainer.addView(btnMarkdown)

        val btnSave = Button(context).apply {
            text = "Save"
            textSize = 11f
            setTextColor(Color.parseColor("#81C784"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                saveTextToStorage(uri, fileRef, codeEditor.text.toString())
            }
        }
        headerActionContainer.addView(btnSave)

        // Read stream asynchronously on Dispatchers.IO
        windowScope.launch(Dispatchers.IO) {
            val contentString = try {
                readUriOrFileToString(uri, fileRef)
            } catch (e: Exception) {
                "Error reading file stream: ${e.localizedMessage}"
            }
            withContext(Dispatchers.Main) {
                codeEditor.setText(contentString)
                updateStatus("Loaded (${contentString.length} chars)")
                if (isMdFile) {
                    btnMarkdown.performClick()
                }
            }
        }

        // Auto-save loop in coroutine (checks dirty state every 15 seconds)
        var lastSavedText = ""
        windowScope.launch {
            while (isActive) {
                delay(15000)
                val currentText = codeEditor.text.toString()
                if (currentText.isNotEmpty() && currentText != lastSavedText) {
                    lastSavedText = currentText
                    updateStatus("Auto-saving...")
                    saveTextToStorage(uri, fileRef, currentText, isAutoSave = true)
                    refreshMarkdown()
                }
            }
        }
    }

    private fun saveTextToStorage(uri: Uri, fileRef: File?, text: String, isAutoSave: Boolean = false) {
        windowScope.launch(Dispatchers.IO) {
            try {
                if (fileRef != null && fileRef.canWrite()) {
                    FileOutputStream(fileRef).use { fos ->
                        fos.write(text.toByteArray(Charsets.UTF_8))
                        fos.flush()
                    }
                } else {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(text.toByteArray(Charsets.UTF_8))
                        os.flush()
                    } ?: throw IOException("Cannot open output stream for $uri")
                }
                withContext(Dispatchers.Main) {
                    undoRedoManager.clearOnSave()
                    updateUndoRedoButtons()
                    updateStatus(if (isAutoSave) "Auto-saved at ${currentTimeString()}" else "Saved successfully (Undo cleared)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Save failed: ${e.localizedMessage}")
                }
            }
        }
    }

    // =========================================================================
    // 2. IMAGE FACTORY: PhotoEditorView, drawing layers, and PNG compression saving
    // =========================================================================
    private fun inflateImageView(uri: Uri, fileRef: File?) {
        val rootImageContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // Toolbar for drawing layers and color selection
        val drawingToolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        val photoEditorView = PhotoEditorView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        rootImageContainer.addView(drawingToolbar)
        rootImageContainer.addView(photoEditorView)
        contentContainer.addView(rootImageContainer)

        // Instantiate PhotoEditor controller
        val photoEditor = PhotoEditor.Builder(context, photoEditorView)
            .setPinchTextScalable(true)
            .build()

        var isBrushActive = false
        val btnBrush = Button(context).apply {
            text = "Draw: OFF"
            textSize = 11f
            setTextColor(Color.parseColor("#E0E0E0"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                isBrushActive = !isBrushActive
                photoEditor.setBrushDrawingMode(isBrushActive)
                text = if (isBrushActive) "Draw: ON" else "Draw: OFF"
                updateStatus(if (isBrushActive) "Brush mode active" else "View mode active")
            }
        }
        drawingToolbar.addView(btnBrush)

        // Brush colors
        val colors = listOf(
            "Yellow" to Color.YELLOW,
            "Red" to Color.RED,
            "Green" to Color.GREEN,
            "Cyan" to Color.CYAN,
            "White" to Color.WHITE
        )
        colors.forEach { (name, color) ->
            val colorBtn = Button(context).apply {
                text = name
                textSize = 10f
                setTextColor(color)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    photoEditor.brushColor = color
                    photoEditor.brushSize = dpToPx(8).toFloat()
                    if (!isBrushActive) btnBrush.performClick()
                    updateStatus("Brush color: $name")
                }
            }
            drawingToolbar.addView(colorBtn)
        }

        val btnClear = Button(context).apply {
            text = "Clear"
            textSize = 11f
            setTextColor(Color.parseColor("#EF5350"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                photoEditor.clearAllViews()
                updateStatus("Drawing cleared")
            }
        }
        drawingToolbar.addView(btnClear)

        // Save Button in Header
        val btnSave = Button(context).apply {
            text = "Save PNG"
            textSize = 11f
            setTextColor(Color.parseColor("#81C784"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                updateStatus("Saving image layers...")
                val saveSettings = SaveSettings.Builder()
                    .setClearViewsEnabled(false)
                    .setTransparencyEnabled(true)
                    .build()

                photoEditor.saveAsBitmap(saveSettings, object : OnSaveBitmap {
                    override fun onBitmapReady(saveBitmap: Bitmap) {
                        windowScope.launch(Dispatchers.IO) {
                            try {
                                val outStream = if (fileRef != null && fileRef.canWrite()) {
                                    FileOutputStream(fileRef)
                                } else {
                                    context.contentResolver.openOutputStream(uri, "wt")
                                } ?: throw IOException("Cannot write to image URI")

                                outStream.use { os ->
                                    saveBitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                                    os.flush()
                                }
                                withContext(Dispatchers.Main) {
                                    updateStatus("Image saved to storage")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    updateStatus("Save failed: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                })
            }
        }
        headerActionContainer.addView(btnSave)

        // Load Bitmap into PhotoEditorView source using Coil
        windowScope.launch(Dispatchers.IO) {
            val bitmap = try {
                val imageLoader = coil.Coil.imageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(fileRef ?: uri)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
                    if (fileRef != null) {
                        BitmapFactory.decodeFile(fileRef.absolutePath)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                }
            } catch (e: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    photoEditorView.source.setImageBitmap(bitmap)
                    updateStatus("Image loaded (${bitmap.width}x${bitmap.height})")
                } else {
                    updateStatus("Failed to decode image")
                }
            }
        }
    }

    // =========================================================================
    // 3. PDF FACTORY: Android-PdfViewer and Apache PDFBox programmatic hooks
    // =========================================================================
    private fun inflatePdfView(uri: Uri, fileRef: File?) {
        headerBar.visibility = View.GONE
        statusBar.visibility = View.GONE

        val pdfView = PDFView(context, null).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        contentContainer.addView(pdfView)

        try {
            if (fileRef != null && fileRef.exists()) {
                pdfView.fromFile(fileRef)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .load()
            } else {
                pdfView.fromUri(uri)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .load()
            }
        } catch (e: Exception) {
            updateStatus("PDF display error: ${e.localizedMessage}")
        }
    }

    private fun savePdfDocToStorage(doc: PDDocument, uri: Uri, fileRef: File?) {
        if (fileRef != null && fileRef.canWrite()) {
            FileOutputStream(fileRef).use { fos ->
                doc.save(fos)
                fos.flush()
            }
        } else {
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                doc.save(os)
                os.flush()
            } ?: throw IOException("Cannot write to PDF URI")
        }
    }

    // =========================================================================
    // 4. AUDIO / VIDEO FACTORY: Jetpack Media3 ExoPlayer hardware-accelerated playback
    // =========================================================================
    private fun inflateAudioVideoView(uri: Uri, fileRef: File?) {
        val playerView = PlayerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
            useController = true
        }
        contentContainer.addView(playerView)

        val player = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

        activeExoPlayer = player
        playerView.player = player
        updateStatus("Media Player Active (${if (currentMimeGroup == FileMimeGroup.AUDIO_VIDEO) "ExoPlayer" else "Media"})")
    }

    // =========================================================================
    // 5. OFFICE FACTORY: Apache POI outline parser for docx, xlsx, pptx
    // =========================================================================
    private fun inflateOfficeView(uri: Uri, fileRef: File?, extension: String) {
        val rootOfficeLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#151515"))
        }

        val officeScrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val outlineContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        officeScrollView.addView(outlineContainer)
        rootOfficeLayout.addView(officeScrollView)
        contentContainer.addView(rootOfficeLayout)

        updateStatus("Parsing Office outline...")

        windowScope.launch(Dispatchers.IO) {
            val blocks = mutableListOf<String>()
            try {
                val inputBytes = readUriOrFileToBytes(uri, fileRef)
                when (extension) {
                    "docx" -> {
                        val doc = XWPFDocument(ByteArrayInputStream(inputBytes))
                        doc.paragraphs.forEachIndexed { i, p ->
                            if (p.text.isNotBlank()) blocks.add("§ Paragraph ${i + 1}:\n${p.text}")
                        }
                        doc.tables.forEachIndexed { i, table ->
                            val tableText = table.rows.joinToString("\n") { row ->
                                row.tableCells.joinToString(" | ") { it.text }
                            }
                            blocks.add("▦ Table ${i + 1}:\n$tableText")
                        }
                    }
                    "xlsx" -> {
                        val wb = XSSFWorkbook(ByteArrayInputStream(inputBytes))
                        for (i in 0 until wb.numberOfSheets) {
                            val sheet = wb.getSheetAt(i)
                            val sheetBuilder = StringBuilder("Sheet: ${sheet.sheetName}\n")
                            sheet.take(50).forEach { row ->
                                sheetBuilder.append(row.joinToString(" | ") { cell -> cell.toString() }).append("\n")
                            }
                            blocks.add(sheetBuilder.toString())
                        }
                    }
                    "pptx" -> {
                        val ppt = XMLSlideShow(ByteArrayInputStream(inputBytes))
                        ppt.slides.forEachIndexed { i, slide ->
                            val slideText = slide.shapes.filterIsInstance<org.apache.poi.xslf.usermodel.XSLFTextShape>()
                                .joinToString("\n") { it.text }
                            blocks.add("Slide ${i + 1}:\n$slideText")
                        }
                    }
                    else -> {
                        blocks.add("Supported formats: DOCX, XLSX, PPTX\nRaw file size: ${inputBytes.size} bytes")
                    }
                }
            } catch (e: Exception) {
                blocks.add("Office Parser Notice:\n${e.localizedMessage ?: "Unable to read structured XML model"}")
            }

            withContext(Dispatchers.Main) {
                outlineContainer.removeAllViews()
                if (blocks.isEmpty()) {
                    outlineContainer.addView(TextView(context).apply {
                        text = "Document is empty or has no text blocks."
                        setTextColor(Color.GRAY)
                    })
                } else {
                    blocks.forEach { block ->
                        val card = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundColor(Color.parseColor("#222222"))
                            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = dpToPx(8)
                            }
                        }
                        val text = TextView(context).apply {
                            this.text = block
                            setTextColor(Color.parseColor("#E0E0E0"))
                            textSize = 13f
                            typeface = Typeface.MONOSPACE
                        }
                        card.addView(text)
                        outlineContainer.addView(card)
                    }
                }
                updateStatus("Office Outline loaded (${blocks.size} blocks)")
            }
        }
    }

    // =========================================================================
    // 6. ARCHIVE & FALLBACK FACTORY
    // =========================================================================
    private fun inflateArchiveView(uri: Uri, fileRef: File?) {
        val textView = TextView(context).apply {
            text = "Archive Workspace Container\nFile: $currentFileName\n(Supports Zip, 7z extraction and inspection)"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        contentContainer.addView(textView)
        updateStatus("Archive Ready")
    }

    private fun inflateFallbackView(uri: Uri, fileRef: File?) {
        val textView = TextView(context).apply {
            text = "Binary / Universal File\nFile: $currentFileName\nSize: ${fileRef?.length() ?: "Unknown"} bytes"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        contentContainer.addView(textView)
        updateStatus("Binary File Loaded")
    }

    // =========================================================================
    // Storage IO Helpers (Off Main Thread)
    // =========================================================================
    private fun readUriOrFileToString(uri: Uri, fileRef: File?): String {
        return if (fileRef != null && fileRef.exists()) {
            fileRef.readText(Charsets.UTF_8)
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: ""
        }
    }

    private fun readUriOrFileToBytes(uri: Uri, fileRef: File?): ByteArray {
        return if (fileRef != null && fileRef.exists()) {
            fileRef.readBytes()
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        }
    }

    private fun cleanupActiveResources() {
        activeExoPlayer?.let { player ->
            player.stop()
            player.release()
            activeExoPlayer = null
        }
    }

    fun clearWorkspace() {
        cleanupActiveResources()
        currentUri = null
        currentFileRef = null
        currentFileName = ""
        currentMimeGroup = FileMimeGroup.UNKNOWN
        setupPlaceholderStructure()
    }

    fun setTitle(title: String) {
        titleText.text = title
    }

    fun updateStatus(status: String) {
        statusText.text = status
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun currentTimeString(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanupActiveResources()
        windowScope.cancel()
    }
}

interface EditorCommand {
    fun execute()
    fun undo()
}

class EditorUndoRedoManager {
    private val undoStack = ArrayDeque<EditorCommand>()
    private val redoStack = ArrayDeque<EditorCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun executeCommand(command: EditorCommand) {
        command.execute()
        undoStack.addLast(command)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.removeLast()
            command.undo()
            redoStack.addLast(command)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.removeLast()
            command.execute()
            undoStack.addLast(command)
        }
    }

    fun clearOnSave() {
        undoStack.clear()
        redoStack.clear()
    }
}
