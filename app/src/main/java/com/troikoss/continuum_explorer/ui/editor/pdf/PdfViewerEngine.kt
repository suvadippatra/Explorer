package com.troikoss.continuum_explorer.ui.editor.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.TonalSearchPill
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.openInFileConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class PdfLayoutMode(val label: String) {
    VERTICAL_CONTINUOUS("Continuous"),
    HORIZONTAL_PAGED("Single Page"),
    TWO_PAGE_SPREAD("2-Page Spread")
}

enum class PdfReadingTheme(val label: String) {
    NORMAL("Day"),
    NIGHT_INVERTED("Night"),
    SEPIA("Sepia")
}

/**
 * Desktop-Class PDF Viewer Engine using Android's native `PdfRenderer` + `PDFBox` text search + `PrintManager`.
 * Eliminates duplicate headers when hosted inside `ViewOnlyFileViewer` (`showInternalHeader = false`)
 * while providing a complete desktop control bar:
 * - Page Jump Dialog & Previous/Next page buttons
 * - Continuous Vertical, Single Page Horizontal, and Two-Page Book Spread layouts
 * - Zoom Out / Fit (100%) / Zoom In (50% to 400%)
 * - 90° Clockwise Page Rotation
 * - Day / Night Inverted / Sepia Reading Color Filters
 * - Thumbnail Strip Toggle
 * - Full-text PDF Search
 * - Quick Compress PDF & Native Print
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerEngine(
    file: UniversalFile,
    appState: FileExplorerState? = null,
    showInternalHeader: Boolean = false,
    onBack: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var tempCopiedFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Desktop PDF Controls State
    var layoutMode by remember { mutableStateOf(PdfLayoutMode.VERTICAL_CONTINUOUS) }
    var readingTheme by remember { mutableStateOf(PdfReadingTheme.NORMAL) }
    var showThumbnailBar by remember { mutableStateOf(true) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("1") }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchMatchingPages by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentMatchPointer by remember { mutableIntStateOf(0) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var pageRotationDegrees by remember { mutableFloatStateOf(0f) }

    val renderMutex = remember { Mutex() }

    val pageBitmapCache = remember {
        object : LruCache<String, Bitmap>(20 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    val ReadingColorFilter: ColorFilter? = remember(readingTheme) {
        when (readingTheme) {
            PdfReadingTheme.NORMAL -> null
            PdfReadingTheme.NIGHT_INVERTED -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            PdfReadingTheme.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.94f, 0.04f, 0.0f, 0f, 12f,
                        0.03f, 0.88f, 0.02f, 0f, 6f,
                        0.02f, 0.05f, 0.72f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    LaunchedEffect(file) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context)
                val targetFile: File = file.fileRef?.takeIf { it.exists() } ?: run {
                    val temp = File(context.cacheDir, "pdf_preview_${System.currentTimeMillis()}.pdf")
                    tempCopiedFile = temp
                    file.provider.openInput(file.providerId).use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    temp
                }

                val pfd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
                pageCount = renderer.pageCount
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to open PDF: ${e.localizedMessage ?: "Corrupted or password-protected PDF"}"
                isLoading = false
            }
        }
    }

    DisposableEffect(file) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                renderMutex.withLock {
                    try { pdfRenderer?.close() } catch (_: Exception) {}
                    try { fileDescriptor?.close() } catch (_: Exception) {}
                    try { tempCopiedFile?.delete() } catch (_: Exception) {}
                    pageBitmapCache.evictAll()
                }
            }
        }
    }

    val verticalListState = rememberLazyListState()
    val horizontalPagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })
    val spreadCount = remember(pageCount) { ((pageCount + 1) / 2).coerceAtLeast(1) }
    val spreadPagerState = rememberPagerState(pageCount = { spreadCount })

    val currentPageIndex by remember {
        derivedStateOf {
            when (layoutMode) {
                PdfLayoutMode.VERTICAL_CONTINUOUS -> verticalListState.firstVisibleItemIndex
                PdfLayoutMode.HORIZONTAL_PAGED -> horizontalPagerState.currentPage
                PdfLayoutMode.TWO_PAGE_SPREAD -> (spreadPagerState.currentPage * 2).coerceAtMost((pageCount - 1).coerceAtLeast(0))
            }
        }
    }

    fun jumpToPage(targetIndex: Int) {
        val clamped = targetIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scope.launch {
            when (layoutMode) {
                PdfLayoutMode.VERTICAL_CONTINUOUS -> verticalListState.scrollToItem(clamped)
                PdfLayoutMode.HORIZONTAL_PAGED -> horizontalPagerState.scrollToPage(clamped)
                PdfLayoutMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
            }
        }
    }

    fun executePdfSearch(query: String) {
        if (query.isBlank()) {
            searchMatchingPages = emptyList()
            return
        }
        val activePdfFile = file.fileRef ?: tempCopiedFile ?: return
        scope.launch {
            isSearching = true
            val matches = withContext(Dispatchers.IO) {
                val found = mutableListOf<Int>()
                try {
                    PDDocument.load(activePdfFile).use { doc ->
                        val stripper = PDFTextStripper()
                        for (pageNum in 1..doc.numberOfPages) {
                            stripper.startPage = pageNum
                            stripper.endPage = pageNum
                            val pageText = stripper.getText(doc)
                            if (pageText.contains(query, ignoreCase = true)) {
                                found.add(pageNum - 1)
                            }
                        }
                    }
                } catch (_: Exception) {}
                found
            }
            searchMatchingPages = matches
            currentMatchPointer = 0
            isSearching = false
            if (matches.isNotEmpty()) {
                jumpToPage(matches.first())
            } else {
                Toast.makeText(context, "No matches for \"$query\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun printCurrentPdf() {
        val activePdfFile = file.fileRef ?: tempCopiedFile
        if (activePdfFile == null || !activePdfFile.exists()) {
            Toast.makeText(context, "PDF file not ready for printing", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            printManager.print(
                file.name,
                object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val info = PrintDocumentInfo.Builder(file.name)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(pageCount.coerceAtLeast(1))
                            .build()
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        if (destination == null) {
                            callback?.onWriteFailed("No destination")
                            return
                        }
                        try {
                            FileInputStream(activePdfFile).use { input ->
                                FileOutputStream(destination.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                        }
                    }
                },
                null
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    if (showJumpPageDialog && pageCount > 0) {
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("Go to Page (1 – $pageCount)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = jumpPageInput,
                    onValueChange = { jumpPageInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Page Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pageNum = jumpPageInput.toIntOrNull()
                        if (pageNum != null && pageNum in 1..pageCount) {
                            jumpToPage(pageNum - 1)
                            showJumpPageDialog = false
                        } else {
                            Toast.makeText(context, "Enter a page between 1 and $pageCount", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                when (readingTheme) {
                    PdfReadingTheme.NIGHT_INVERTED -> Color(0xFF0E0E10)
                    PdfReadingTheme.SEPIA -> Color(0xFFF3EADB)
                    PdfReadingTheme.NORMAL -> Color(0xFF202124)
                }
            )
    ) {
        // Optional standalone header only when NOT hosted inside ViewOnlyFileViewer
        if (showInternalHeader) {
            SingleRowCommandBar(
                navigationContent = {
                    if (onMenuClick != null) {
                        IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                titleContent = {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        // Main PDF Viewport (Zero stacked inner top control/search bars)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (zoomScale * zoom).coerceIn(0.5f, 4.0f)
                        zoomScale = newScale
                        if (newScale > 1.02f) {
                            val maxPanX = (size.width * (newScale - 1f)) / 2f
                            val maxPanY = (size.height * (newScale - 1f)) / 2f
                            panOffset = Offset(
                                x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                        } else {
                            panOffset = Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1.15f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                zoomScale = 2.25f
                            }
                        }
                    )
                }
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Rendering PDF...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage!!, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                pageCount > 0 && pdfRenderer != null -> {
                    val pageIndices = remember(pageCount) { (0 until pageCount).toList() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            )
                    ) {
                        when (layoutMode) {
                            PdfLayoutMode.VERTICAL_CONTINUOUS -> {
                                LazyColumn(
                                    state = verticalListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(pageIndices, key = { it }) { pageIdx ->
                                        PdfPageCard(
                                            pageIndex = pageIdx,
                                            totalPages = pageCount,
                                            renderer = pdfRenderer!!,
                                            renderMutex = renderMutex,
                                            bitmapCache = pageBitmapCache,
                                            rotationDegrees = pageRotationDegrees,
                                            colorFilter = ReadingColorFilter,
                                            isSearchHighlight = searchMatchingPages.contains(pageIdx)
                                        )
                                    }
                                }
                            }
                            PdfLayoutMode.HORIZONTAL_PAGED -> {
                                HorizontalPager(
                                    state = horizontalPagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    pageSpacing = 16.dp
                                ) { pageIdx ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PdfPageCard(
                                            pageIndex = pageIdx,
                                            totalPages = pageCount,
                                            renderer = pdfRenderer!!,
                                            renderMutex = renderMutex,
                                            bitmapCache = pageBitmapCache,
                                            rotationDegrees = pageRotationDegrees,
                                            colorFilter = ReadingColorFilter,
                                            isSearchHighlight = searchMatchingPages.contains(pageIdx)
                                        )
                                    }
                                }
                            }
                            PdfLayoutMode.TWO_PAGE_SPREAD -> {
                                HorizontalPager(
                                    state = spreadPagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    pageSpacing = 16.dp
                                ) { spreadIdx ->
                                    val leftPage = spreadIdx * 2
                                    val rightPage = leftPage + 1
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            PdfPageCard(
                                                pageIndex = leftPage,
                                                totalPages = pageCount,
                                                renderer = pdfRenderer!!,
                                                renderMutex = renderMutex,
                                                bitmapCache = pageBitmapCache,
                                                rotationDegrees = pageRotationDegrees,
                                                colorFilter = ReadingColorFilter,
                                                isSearchHighlight = searchMatchingPages.contains(leftPage)
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            if (rightPage < pageCount) {
                                                PdfPageCard(
                                                    pageIndex = rightPage,
                                                    totalPages = pageCount,
                                                    renderer = pdfRenderer!!,
                                                    renderMutex = renderMutex,
                                                    bitmapCache = pageBitmapCache,
                                                    rotationDegrees = pageRotationDegrees,
                                                    colorFilter = ReadingColorFilter,
                                                    isSearchHighlight = searchMatchingPages.contains(rightPage)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Top-Right PDF Search Pill Card (zero stacked top header row)
            if (showSearchBar) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .widthIn(min = 280.dp, max = 420.dp)
                        .shadow(8.dp, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TonalSearchPill(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Find in PDF",
                            modifier = Modifier.weight(1f),
                            testTagName = "pdf_find_input"
                        )
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            FilledTonalButton(
                                onClick = { executePdfSearch(searchQuery) },
                                enabled = searchQuery.isNotBlank(),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Find", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (searchMatchingPages.isNotEmpty()) {
                            Text(
                                text = "${currentMatchPointer + 1}/${searchMatchingPages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    currentMatchPointer = (currentMatchPointer - 1 + searchMatchingPages.size) % searchMatchingPages.size
                                    jumpToPage(searchMatchingPages[currentMatchPointer])
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    currentMatchPointer = (currentMatchPointer + 1) % searchMatchingPages.size
                                    jumpToPage(searchMatchingPages[currentMatchPointer])
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(
                            onClick = {
                                showSearchBar = false
                                searchQuery = ""
                                searchMatchingPages = emptyList()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Borderless Tonal Bottom PDF Control Dock (Zero second top row)
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { jumpToPage(currentPageIndex - 1) },
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Page", modifier = Modifier.size(18.dp))
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = pageCount > 0) {
                            jumpPageInput = "${currentPageIndex + 1}"
                            showJumpPageDialog = true
                        }
                ) {
                    Text(
                        text = if (pageCount > 0) "${currentPageIndex + 1} / $pageCount" else "0 / 0",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = { jumpToPage(currentPageIndex + 1) },
                    enabled = currentPageIndex < pageCount - 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Page", modifier = Modifier.size(18.dp))
                }

                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.height(18.dp)
                )

                IconButton(
                    onClick = {
                        zoomScale = (zoomScale - 0.25f).coerceIn(0.5f, 4.0f)
                        if (zoomScale <= 1.02f) panOffset = Offset.Zero
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(17.dp))
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        }
                ) {
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.25f).coerceIn(0.5f, 4.0f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(17.dp))
                }

                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.height(18.dp)
                )

                PdfLayoutMode.entries.forEach { mode ->
                    val selected = layoutMode == mode
                    Surface(
                        onClick = { layoutMode = mode },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Text(mode.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }

                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.height(18.dp)
                )

                IconButton(
                    onClick = { pageRotationDegrees = (pageRotationDegrees + 90f) % 360f },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", modifier = Modifier.size(17.dp))
                }

                Surface(
                    onClick = {
                        readingTheme = when (readingTheme) {
                            PdfReadingTheme.NORMAL -> PdfReadingTheme.NIGHT_INVERTED
                            PdfReadingTheme.NIGHT_INVERTED -> PdfReadingTheme.SEPIA
                            PdfReadingTheme.SEPIA -> PdfReadingTheme.NORMAL
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            when (readingTheme) {
                                PdfReadingTheme.NORMAL -> Icons.Default.LightMode
                                PdfReadingTheme.NIGHT_INVERTED -> Icons.Default.DarkMode
                                PdfReadingTheme.SEPIA -> Icons.Default.AutoStories
                            },
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(readingTheme.label, style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(
                    onClick = { showThumbnailBar = !showThumbnailBar },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ViewCarousel,
                        contentDescription = "Thumbnails",
                        tint = if (showThumbnailBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) {
                            searchQuery = ""
                            searchMatchingPages = emptyList()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Find in PDF",
                        tint = if (showSearchBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = { printCurrentPdf() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(17.dp))
                }

                val localRef = file.fileRef ?: tempCopiedFile
                if (appState != null && localRef != null) {
                    Surface(
                        onClick = { appState.openInFileConverter(listOf(localRef)) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(13.dp))
                            Text("Convert", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Bottom Thumbnail Strip
        if (showThumbnailBar && pageCount > 1 && pdfRenderer != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val thumbIndices = remember(pageCount) { (0 until pageCount).toList() }
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(thumbIndices, key = { it }) { idx ->
                        val isSelected = idx == currentPageIndex
                        PdfThumbnailItem(
                            pageIndex = idx,
                            isSelected = isSelected,
                            renderer = pdfRenderer!!,
                            renderMutex = renderMutex,
                            bitmapCache = pageBitmapCache,
                            onClick = { jumpToPage(idx) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageCard(
    pageIndex: Int,
    totalPages: Int,
    renderer: PdfRenderer,
    renderMutex: Mutex,
    bitmapCache: LruCache<String, Bitmap>,
    rotationDegrees: Float,
    colorFilter: ColorFilter?,
    isSearchHighlight: Boolean
) {
    val cacheKey = "page_$pageIndex"
    var pageBitmap by remember(pageIndex) { mutableStateOf(bitmapCache.get(cacheKey)) }
    var aspectRatio by remember(pageIndex) { mutableFloatStateOf(0.707f) }

    LaunchedEffect(pageIndex, renderer) {
        if (pageBitmap == null) {
            withContext(Dispatchers.IO) {
                renderMutex.withLock {
                    try {
                        val cached = bitmapCache.get(cacheKey)
                        if (cached != null && !cached.isRecycled) {
                            pageBitmap = cached
                        } else {
                            renderer.openPage(pageIndex).use { page ->
                                val w = page.width.coerceAtLeast(1)
                                val h = page.height.coerceAtLeast(1)
                                aspectRatio = w.toFloat() / h.toFloat()

                                val renderWidth = (w * 2).coerceIn(800, 2100)
                                val renderHeight = (renderWidth / aspectRatio).toInt().coerceAtLeast(1)

                                val bmp = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmapCache.put(cacheKey, bmp)
                                pageBitmap = bmp
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            val bmp = pageBitmap!!
            aspectRatio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
        }
    }

    Box(
        modifier = Modifier
            .widthIn(max = 880.dp)
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .then(
                if (isSearchHighlight) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val bmp = pageBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(rotationZ = rotationDegrees)
            )
        } else {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }

        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            shape = RoundedCornerShape(topStart = 8.dp),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text(
                text = "${pageIndex + 1} / $totalPages",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PdfThumbnailItem(
    pageIndex: Int,
    isSelected: Boolean,
    renderer: PdfRenderer,
    renderMutex: Mutex,
    bitmapCache: LruCache<String, Bitmap>,
    onClick: () -> Unit
) {
    val thumbKey = "thumb_$pageIndex"
    var thumbBitmap by remember(pageIndex) { mutableStateOf(bitmapCache.get(thumbKey)) }

    LaunchedEffect(pageIndex, renderer) {
        if (thumbBitmap == null) {
            withContext(Dispatchers.IO) {
                renderMutex.withLock {
                    try {
                        val cached = bitmapCache.get(thumbKey)
                        if (cached != null && !cached.isRecycled) {
                            thumbBitmap = cached
                        } else {
                            renderer.openPage(pageIndex).use { page ->
                                val thumbW = 110
                                val thumbH = (thumbW * (page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f))).toInt().coerceAtLeast(120)
                                val bmp = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmapCache.put(thumbKey, bmp)
                                thumbBitmap = bmp
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .border(
                    width = if (isSelected) 2.dp else 0.5.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
