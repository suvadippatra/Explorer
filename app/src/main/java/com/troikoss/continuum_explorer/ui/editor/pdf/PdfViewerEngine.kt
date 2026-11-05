package com.troikoss.continuum_explorer.ui.editor.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.troikoss.continuum_explorer.ui.editor.PdfSearchResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.editor.PdfSearchOcr
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.formatEllipsizedFileName
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fast multi-level in-memory LruCache for rendered PDF pages and thumbnails.
 */
class PdfTileCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 6 // 1/6th of max memory in KB

    private val lruCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = lruCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (lruCache.get(key) == null) {
            lruCache.put(key, bitmap)
        }
    }

    fun clear() {
        lruCache.evictAll()
    }
}

/**
 * Enhanced PDF Viewer Engine:
 * 1. Two-Page Spread (Booklet / Tablet Mode):
 *    - Auto-detects wide screens (sw600dp or landscape) and renders pages side-by-side like an open book.
 *    - Center book spine crease gradient and realistic page edges.
 * 2. Fast Responsive Page Scrolling:
 *    - Multi-threaded background tile pre-rendering on Dispatchers.IO.
 *    - LruCache memory bitmap cache for fast 60fps scrolling and zoom gestures without lag.
 *    - Multi-touch pinch-to-zoom (up to 4x) & double-tap to toggle zoom.
 * 3. Page Counter Overlay & Scrub Bar:
 *    - Animated floating pill (Page X of Y) appearing during scroll and auto-dismissing after 2s.
 *    - Horizontal page thumbnail scrub bar + fast scrub slider at the bottom.
 * 4. OCR & Text Extraction Search:
 *    - Embedded PDFBox search + ML Kit OCR fallback.
 *    - Real-time bounding box coordinate highlights directly rendered over PDF pages.
 */
@Composable
fun PdfViewerEngine(
    file: UniversalFile,
    appState: FileExplorerState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    // Screen dimensions & orientation detection for Two-Page Spread Booklet Mode
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isWideScreen = remember(screenWidthDp, screenHeightDp) {
        screenWidthDp >= 600 || screenWidthDp > screenHeightDp
    }

    var isTwoPageSpread by remember(isWideScreen) { mutableStateOf(isWideScreen) }

    // PDF Renderer state
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }
    var isPdfLoading by remember { mutableStateOf(true) }

    // Page navigation & pill timer state
    val listState = rememberLazyListState()
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isPagePillVisible by remember { mutableStateOf(false) }
    var dismissPillJob by remember { mutableStateOf<Job?>(null) }

    // Zoom & pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Scrub bar state
    var isScrubBarVisible by remember { mutableStateOf(true) }

    // Search state
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PdfSearchResult>>(emptyList()) }
    var currentSearchMatchIndex by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }
    var searchProgressText by remember { mutableStateOf("") }

    // Tile bitmap cache
    val tileCache = remember { PdfTileCache() }

    fun showPill() {
        isPagePillVisible = true
        dismissPillJob?.cancel()
        dismissPillJob = coroutineScope.launch {
            delay(2000)
            isPagePillVisible = false
        }
    }

    // Initialize PdfRenderer on IO thread safely
    DisposableEffect(file) {
        val job = coroutineScope.launch(Dispatchers.IO) {
            try {
                val fileRef = file.fileRef
                val pfd = if (fileRef != null && fileRef.exists()) {
                    ParcelFileDescriptor.open(fileRef, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    val uri = file.documentFileRef?.uri ?: getUriForUniversalFile(context, file)
                    if (uri != null) context.contentResolver.openFileDescriptor(uri, "r") else null
                }

                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    withContext(Dispatchers.Main) {
                        parcelFileDescriptor = pfd
                        pdfRenderer = renderer
                        totalPages = renderer.pageCount
                        isPdfLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) { isPdfLoading = false }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isPdfLoading = false }
            }
        }

        onDispose {
            job.cancel()
            tileCache.clear()
            try {
                pdfRenderer?.close()
                parcelFileDescriptor?.close()
            } catch (_: Exception) {}
        }
    }

    // Background tile pre-rendering look-ahead (Dispatchers.IO)
    LaunchedEffect(currentPageIndex, totalPages, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (totalPages <= 0) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val range = (currentPageIndex - 2)..(currentPageIndex + 3)
            for (p in range) {
                if (p in 0 until totalPages && tileCache.get("page_$p") == null) {
                    try {
                        synchronized(renderer) {
                            val page = renderer.openPage(p)
                            val targetW = 1080
                            val targetH = (targetW * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(100)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            tileCache.put("page_$p", bmp)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Update current page on scroll
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val visibleIndex = listState.firstVisibleItemIndex
        currentPageIndex = if (isTwoPageSpread) (visibleIndex * 2).coerceAtMost(totalPages - 1) else visibleIndex
        showPill()
    }

    // Run Embedded Text & ML Kit OCR search
    fun runSearch(query: String) {
        if (query.isBlank()) {
            searchResults = emptyList()
            currentSearchMatchIndex = 0
            searchProgressText = ""
            return
        }
        isSearching = true
        searchProgressText = "Searching..."
        coroutineScope.launch {
            val results = PdfSearchOcr.searchPdf(
                context = context,
                file = file,
                query = query,
                totalPages = totalPages,
                onProgress = { current, total ->
                    searchProgressText = "Scanning page $current of $total..."
                }
            )
            searchResults = results
            currentSearchMatchIndex = 0
            isSearching = false
            searchProgressText = ""
            if (results.isNotEmpty()) {
                val matchPage = results[0].pageIndex
                val targetIndex = if (isTwoPageSpread) matchPage / 2 else matchPage
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ==========================================
        // 1. Compact Top Bar with Action Controls
        // ==========================================
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { appState?.viewingFile = null },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatEllipsizedFileName(file.name, 28),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Two-Page Spread Booklet Mode toggle
                    IconButton(
                        onClick = {
                            isTwoPageSpread = !isTwoPageSpread
                            scale = 1f
                            offset = Offset.Zero
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isTwoPageSpread) Icons.Default.MenuBook else Icons.Default.Layers,
                            contentDescription = if (isTwoPageSpread) "Booklet Mode Active" else "Single Page Mode",
                            tint = if (isTwoPageSpread) MaterialTheme.colorScheme.primary else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Thumbnail Scrub Bar toggle
                    IconButton(
                        onClick = { isScrubBarVisible = !isScrubBarVisible },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = "Thumbnails",
                            tint = if (isScrubBarVisible) MaterialTheme.colorScheme.primary else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Zoom Controls (- / Reset / +)
                    IconButton(
                        onClick = {
                            scale = (scale - 0.5f).coerceIn(1f, 4.5f)
                            if (scale <= 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ZoomOut,
                            contentDescription = "Zoom Out",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (scale > 1.05f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("${(scale * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(
                        onClick = {
                            scale = (scale + 0.5f).coerceIn(1f, 4.5f)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = "Zoom In",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Search OCR toggle
                    IconButton(
                        onClick = {
                            isSearchOpen = !isSearchOpen
                            if (!isSearchOpen) {
                                searchQuery = ""
                                searchResults = emptyList()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search PDF",
                            tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. Search & OCR Keyword Bar
        // ==========================================
        if (isSearchOpen) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            runSearch(it)
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search text / ML Kit OCR in PDF...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            inner()
                        }
                    )

                    if (isSearching) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchProgressText.isNotEmpty()) {
                                Text(searchProgressText, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                            }
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            text = "${currentSearchMatchIndex + 1}/${searchResults.size}",
                            color = Color(0xFF81C784),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = {
                                if (searchResults.isNotEmpty()) {
                                    currentSearchMatchIndex =
                                        (currentSearchMatchIndex - 1 + searchResults.size) % searchResults.size
                                    val matchPage = searchResults[currentSearchMatchIndex].pageIndex
                                    val targetIndex = if (isTwoPageSpread) matchPage / 2 else matchPage
                                    coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Match", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (searchResults.isNotEmpty()) {
                                    currentSearchMatchIndex =
                                        (currentSearchMatchIndex + 1) % searchResults.size
                                    val matchPage = searchResults[currentSearchMatchIndex].pageIndex
                                    val targetIndex = if (isTwoPageSpread) matchPage / 2 else matchPage
                                    coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Match", tint = Color.White)
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Text("No match", color = Color(0xFFE57373), fontSize = 12.sp)
                    }
                }
            }

            // Snippet preview banner if matched
            if (searchResults.isNotEmpty()) {
                val currentMatch = searchResults.getOrNull(currentSearchMatchIndex)
                if (currentMatch != null) {
                    Surface(
                        color = Color(0xFF1E2836),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Page ${currentMatch.pageIndex + 1}: ${currentMatch.matchedTextSnippet}",
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. Main Viewer Canvas (Single or Two-Page Spread)
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            if (isPdfLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Rendering PDF...", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else if (pdfRenderer == null || totalPages == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Failed to load PDF document", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                val renderer = pdfRenderer!!
                val itemCount = if (isTwoPageSpread) (totalPages + 1) / 2 else totalPages

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (event.changes.size >= 2 || scale > 1.02f) {
                                        scale = (scale * zoomChange).coerceIn(1f, 4.5f)
                                        if (scale > 1.02f) {
                                            offset += panChange
                                        } else {
                                            scale = 1f
                                            offset = Offset.Zero
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale > 1.2f) 1f else 2.25f
                                    offset = Offset.Zero
                                },
                                onTap = { showPill() }
                            )
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(
                            items = List(itemCount) { it }
                        ) { index, _ ->
                            if (isTwoPageSpread) {
                                // Two-Page Spread (Booklet Mode)
                                val page1Index = index * 2
                                val page2Index = page1Index + 1

                                val match1 = searchResults.find { it.pageIndex == page1Index }
                                val match2 = if (page2Index < totalPages) searchResults.find { it.pageIndex == page2Index } else null

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left Page
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .shadow(6.dp, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                                    ) {
                                        PdfPageTile(
                                            renderer = renderer,
                                            pageIndex = page1Index,
                                            cache = tileCache,
                                            searchMatch = match1,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        // Spine inner shadow on right edge of left page
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(10.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(Color.Transparent, Color(0x33000000))
                                                    )
                                                )
                                        )
                                    }

                                    // Center Spine Divider Crease
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(300.dp)
                                            .background(Color(0xFF333333))
                                    )

                                    // Right Page
                                    if (page2Index < totalPages) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .shadow(6.dp, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                        ) {
                                            PdfPageTile(
                                                renderer = renderer,
                                                pageIndex = page2Index,
                                                cache = tileCache,
                                                searchMatch = match2,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            // Spine inner shadow on left edge of right page
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .width(10.dp)
                                                    .fillMaxHeight()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(Color(0x33000000), Color.Transparent)
                                                        )
                                                    )
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            } else {
                                // Single Page Mode
                                val match = searchResults.find { it.pageIndex == index }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.96f)
                                        .shadow(6.dp, RoundedCornerShape(4.dp))
                                ) {
                                    PdfPageTile(
                                        renderer = renderer,
                                        pageIndex = index,
                                        cache = tileCache,
                                        searchMatch = match,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 4. Page Counter Floating Overlay Pill (Auto-dismiss 2s)
                // ==========================================
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPagePillVisible && totalPages > 0,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF444444))
                    ) {
                        val label = if (isTwoPageSpread) {
                            val endPage = (currentPageIndex + 2).coerceAtMost(totalPages)
                            "Pages ${currentPageIndex + 1}-$endPage of $totalPages"
                        } else {
                            "Page ${currentPageIndex + 1} of $totalPages"
                        }
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. Horizontal Page Thumbnail Scrub Bar
        // ==========================================
        if (isScrubBarVisible && totalPages > 0 && pdfRenderer != null) {
            Surface(
                color = Color(0xFF181818),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .border(width = 0.5.dp, color = Color(0xFF2C2C2C)),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(totalPages) { p ->
                            val isSelected = if (isTwoPageSpread) {
                                p == currentPageIndex || p == currentPageIndex + 1
                            } else {
                                p == currentPageIndex
                            }

                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        currentPageIndex = p
                                        showPill()
                                        coroutineScope.launch {
                                            val target = if (isTwoPageSpread) p / 2 else p
                                            listState.animateScrollToItem(target)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                PdfThumbnailTile(
                                    renderer = pdfRenderer!!,
                                    pageIndex = p,
                                    cache = tileCache
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                ) {
                                    Text(
                                        text = "${p + 1}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom progress slider line
                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { newPage ->
                            val p = newPage.toInt().coerceIn(0, totalPages - 1)
                            if (p != currentPageIndex) {
                                currentPageIndex = p
                                showPill()
                                coroutineScope.launch {
                                    val target = if (isTwoPageSpread) p / 2 else p
                                    listState.scrollToItem(target)
                                }
                            }
                        },
                        valueRange = 0f..(totalPages - 1).toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color(0xFF333333)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Individual Page Tile with multi-threaded bitmap rendering and
 * real-time Canvas overlay for search keyword coordinate highlights.
 */
@Composable
private fun PdfPageTile(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: PdfTileCache,
    searchMatch: PdfSearchResult?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf(cache.get("page_$pageIndex")) }

    LaunchedEffect(pageIndex) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    synchronized(renderer) {
                        val page = renderer.openPage(pageIndex)
                        val targetWidth = 1080
                        val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        cache.put("page_$pageIndex", bmp)
                        bitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val isHighlighted = searchMatch != null

    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .border(
                width = if (isHighlighted) 2.5.dp else 0.dp,
                color = if (isHighlighted) Color(0xFFFFD54F) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )

                // Coordinate highlighting overlay over PDF page
                if (searchMatch != null && searchMatch.normalizedHighlights.isNotEmpty()) {
                    Canvas(
                        modifier = Modifier.matchParentSize()
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        for (rect in searchMatch.normalizedHighlights) {
                            val left = rect.left * canvasW
                            val top = rect.top * canvasH
                            val width = (rect.right - rect.left) * canvasW
                            val height = (rect.bottom - rect.top) * canvasH

                            // Highlight fill
                            drawRect(
                                color = Color(0x66FFEB3B), // Translucent yellow
                                topLeft = Offset(left, top),
                                size = Size(width, height)
                            )

                            // Highlight border
                            drawRect(
                                color = Color(0xFFFFC107), // Amber outline
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(Color(0xFF262626)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Thumbnail tile used in horizontal bottom scrub bar.
 */
@Composable
private fun PdfThumbnailTile(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: PdfTileCache
) {
    var thumbBitmap by remember(pageIndex) { mutableStateOf(cache.get("thumb_$pageIndex")) }

    LaunchedEffect(pageIndex) {
        if (thumbBitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    synchronized(renderer) {
                        val page = renderer.openPage(pageIndex)
                        val thumbW = 120
                        val thumbH = (thumbW * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(30)
                        val bmp = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        cache.put("thumb_$pageIndex", bmp)
                        thumbBitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (thumbBitmap != null) {
        Image(
            bitmap = thumbBitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF222222))
        )
    }
}
