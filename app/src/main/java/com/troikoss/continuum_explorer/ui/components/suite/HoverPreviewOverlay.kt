package com.troikoss.continuum_explorer.ui.components.suite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Controller for the 2-second Desktop Mouse Dwell Hover Preview Overlay.
 * - Hovering over a file target for 2000ms displays a floating preview anchored over the file name.
 * - Once displayed, the preview remains visible until the mouse moves or exits.
 * - Touch devices can invoke [showImmediately] from long-press gestures.
 */
class HoverPreviewController(
    private val scope: CoroutineScope
) {
    var activeFile by mutableStateOf<File?>(null)
        private set
    var anchorBoundsInRoot by mutableStateOf<Rect?>(null)
        private set
    var isTouchTriggered by mutableStateOf(false)
        private set

    private var dwellJob: Job? = null
    private var pendingFilePath: String? = null
    private var dwellOrigin: Offset = Offset.Zero

    fun onPointerEntered(file: File, bounds: Rect?, localPos: Offset) {
        if (activeFile?.absolutePath == file.absolutePath && !isTouchTriggered) return
        scheduleDwell(file, bounds, localPos)
    }

    fun onPointerMoved(file: File, bounds: Rect?, localPos: Offset) {
        // If preview is currently shown for this hover, any meaningful mouse movement dismisses it
        if (activeFile != null && !isTouchTriggered) {
            val moved = hypot(
                (localPos.x - dwellOrigin.x).toDouble(),
                (localPos.y - dwellOrigin.y).toDouble()
            )
            if (moved > 8.0) {
                dismiss()
            }
            return
        }
        // While waiting for 2s dwell, reset timer if cursor is actively sliding across the row
        val moved = hypot(
            (localPos.x - dwellOrigin.x).toDouble(),
            (localPos.y - dwellOrigin.y).toDouble()
        )
        if (pendingFilePath != file.absolutePath || moved > 14.0) {
            scheduleDwell(file, bounds, localPos)
        }
    }

    fun onPointerExited(file: File) {
        if (pendingFilePath == file.absolutePath) {
            dwellJob?.cancel()
            dwellJob = null
            pendingFilePath = null
        }
        if (activeFile?.absolutePath == file.absolutePath && !isTouchTriggered) {
            activeFile = null
            anchorBoundsInRoot = null
        }
    }

    fun showImmediately(file: File, bounds: Rect?) {
        dwellJob?.cancel()
        dwellJob = null
        pendingFilePath = null
        isTouchTriggered = true
        anchorBoundsInRoot = bounds
        activeFile = file
    }

    fun dismiss() {
        dwellJob?.cancel()
        dwellJob = null
        pendingFilePath = null
        activeFile = null
        anchorBoundsInRoot = null
        isTouchTriggered = false
    }

    private fun scheduleDwell(file: File, bounds: Rect?, localPos: Offset) {
        dwellJob?.cancel()
        pendingFilePath = file.absolutePath
        dwellOrigin = localPos
        dwellJob = scope.launch {
            delay(2000L)
            if (pendingFilePath == file.absolutePath) {
                isTouchTriggered = false
                anchorBoundsInRoot = bounds
                activeFile = file
            }
        }
    }
}

@Composable
fun rememberHoverPreviewController(): HoverPreviewController {
    val scope = rememberCoroutineScope()
    return remember(scope) { HoverPreviewController(scope) }
}

/**
 * Attaches desktop 2-second hover-dwell tracking to any file row or grid tile.
 */
fun Modifier.hoverPreviewTarget(
    file: File,
    controller: HoverPreviewController
): Modifier = composed {
    var boundsInRoot by remember(file.absolutePath) { mutableStateOf<Rect?>(null) }

    this
        .onGloballyPositioned { coords ->
            boundsInRoot = coords.boundsInRoot()
        }
        .pointerInput(file.absolutePath, controller) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val pos = event.changes.firstOrNull()?.position ?: Offset.Zero
                    when (event.type) {
                        PointerEventType.Enter -> {
                            controller.onPointerEntered(file, boundsInRoot, pos)
                        }
                        PointerEventType.Move -> {
                            controller.onPointerMoved(file, boundsInRoot, pos)
                        }
                        PointerEventType.Exit -> {
                            controller.onPointerExited(file)
                        }
                        PointerEventType.Press -> {
                            if (controller.activeFile != null && !controller.isTouchTriggered) {
                                controller.dismiss()
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
}

private sealed class LoadedHoverContent {
    data object Loading : LoadedHoverContent()
    data class ImagePreview(val bitmap: Bitmap, val width: Int, val height: Int) : LoadedHoverContent()
    data class CsvTablePreview(val rows: List<List<String>>, val totalRows: Int) : LoadedHoverContent()
    data class TextSnippetPreview(val lines: List<String>, val truncated: Boolean) : LoadedHoverContent()
    data class GenericPreview(val extension: String) : LoadedHoverContent()
}

/**
 * Renders the floating tonal preview popover anchored near the hovered file name.
 * Must be placed inside the root [Box] of screens using [hoverPreviewTarget].
 */
@Composable
fun HoverPreviewPopupHost(
    controller: HoverPreviewController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var hostOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var hostWidthPx by remember { mutableIntStateOf(0) }
    var hostHeightPx by remember { mutableIntStateOf(0) }

    val targetFile = controller.activeFile
    val anchor = controller.anchorBoundsInRoot

    var loadedContent by remember(targetFile?.absolutePath) {
        mutableStateOf<LoadedHoverContent>(LoadedHoverContent.Loading)
    }

    LaunchedEffect(targetFile?.absolutePath) {
        val file = targetFile ?: return@LaunchedEffect
        loadedContent = LoadedHoverContent.Loading
        loadedContent = withContext(Dispatchers.IO) {
            loadHoverPreviewContent(file)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val rect = coords.boundsInRoot()
                hostOriginInRoot = rect.topLeft
                hostWidthPx = coords.size.width
                hostHeightPx = coords.size.height
            }
    ) {
        // Touch-triggered scrim lets mobile users tap anywhere outside to dismiss
        if (targetFile != null && controller.isTouchTriggered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { controller.dismiss() }
            )
        }

        val cardWidthPx = with(density) { 300.dp.roundToPx() }
        val cardEstHeightPx = with(density) { 230.dp.roundToPx() }
        val marginPx = with(density) { 12.dp.roundToPx() }

        val offset = remember(anchor, hostOriginInRoot, hostWidthPx, hostHeightPx) {
            if (anchor == null || hostWidthPx == 0 || hostHeightPx == 0) {
                IntOffset(marginPx, marginPx)
            } else {
                val relLeft = (anchor.left - hostOriginInRoot.x).roundToInt()
                val relTop = (anchor.top - hostOriginInRoot.y).roundToInt()
                val relBottom = (anchor.bottom - hostOriginInRoot.y).roundToInt()

                val x = relLeft.coerceIn(marginPx, (hostWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx))
                val aboveY = relTop - cardEstHeightPx - marginPx
                val belowY = relBottom + 6
                val y = when {
                    aboveY >= marginPx -> aboveY
                    belowY + cardEstHeightPx <= hostHeightPx - marginPx -> belowY
                    else -> (hostHeightPx - cardEstHeightPx - marginPx).coerceAtLeast(marginPx)
                }
                IntOffset(x, y)
            }
        }

        AnimatedVisibility(
            visible = targetFile != null,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.96f),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f),
            modifier = Modifier.offset { offset }
        ) {
            val file = targetFile ?: return@AnimatedVisibility
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .width(300.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("hover_preview_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header: File name + extension chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (controller.isTouchTriggered) {
                            IconButton(
                                onClick = { controller.dismiss() },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Body preview container
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 92.dp, max = 160.dp)
                    ) {
                        when (val content = loadedContent) {
                            is LoadedHoverContent.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            is LoadedHoverContent.ImagePreview -> {
                                Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                                    Image(
                                        bitmap = content.bitmap.asImageBitmap(),
                                        contentDescription = file.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "${content.width}×${content.height}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            is LoadedHoverContent.CsvTablePreview -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    content.rows.forEachIndexed { rowIdx, cols ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (rowIdx == 0) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                                    else if (rowIdx % 2 == 1) MaterialTheme.colorScheme.surface
                                                    else MaterialTheme.colorScheme.surfaceContainer
                                                )
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            cols.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                    if (content.totalRows > content.rows.size) {
                                        Text(
                                            text = "+${content.totalRows - content.rows.size} rows",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            is LoadedHoverContent.TextSnippetPreview -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (content.lines.isEmpty()) {
                                        Text(
                                            text = "Empty file",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        content.lines.forEach { line ->
                                            Text(
                                                text = line.ifEmpty { " " },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = 14.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (content.truncated) {
                                            Text(
                                                text = "...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            is LoadedHoverContent.GenericPreview -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.size(40.dp)) {
                                        IconHelper.FileThumbnail(
                                            file = file.toUniversal(),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Text(
                                        text = ".${content.extension.uppercase().ifBlank { "FILE" }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Footer: Size + parent path
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.parentFile?.name?.let { "/$it" } ?: file.parent.orEmpty(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Formatter.formatFileSize(context, file.length()),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun loadHoverPreviewContent(file: File): LoadedHoverContent {
    if (!file.exists() || !file.canRead()) {
        return LoadedHoverContent.GenericPreview(file.extension)
    }
    val ext = file.extension.lowercase()
    return try {
        when (ext) {
            "jpg", "jpeg", "png", "webp", "bmp" -> {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                val w = boundsOpts.outWidth.coerceAtLeast(1)
                val h = boundsOpts.outHeight.coerceAtLeast(1)
                var sample = 1
                while (w / sample > 480 || h / sample > 480) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                if (bmp != null) {
                    LoadedHoverContent.ImagePreview(bmp, w, h)
                } else {
                    LoadedHoverContent.GenericPreview(ext)
                }
            }

            "csv", "tsv" -> {
                val delimiter = if (ext == "tsv") '\t' else ','
                val allLines = mutableListOf<String>()
                file.bufferedReader().use { reader ->
                    var count = 0
                    while (count < 50) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            allLines.add(line)
                            count++
                        }
                    }
                }
                val previewRows = allLines.take(4).map { row ->
                    row.split(delimiter).take(4).map { it.trim().trim('"') }
                }
                LoadedHoverContent.CsvTablePreview(previewRows, allLines.size)
            }

            "md", "markdown", "mdown", "mkd", "txt", "org", "adoc", "asciidoc", "rst", "log",
            "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "scss",
            "json", "xml", "yaml", "yml", "toml", "ini", "conf", "sh", "bash", "c", "cpp", "h",
            "hpp", "sql", "gradle", "properties", "go", "rs", "dart", "php", "rb", "lua" -> {
                val snippet = mutableListOf<String>()
                var truncated = false
                file.bufferedReader().use { reader ->
                    for (i in 0 until 8) {
                        val line = reader.readLine() ?: break
                        snippet.add(line.take(90))
                    }
                    if (reader.readLine() != null) truncated = true
                }
                LoadedHoverContent.TextSnippetPreview(snippet, truncated)
            }

            else -> LoadedHoverContent.GenericPreview(ext)
        }
    } catch (_: Exception) {
        LoadedHoverContent.GenericPreview(ext)
    }
}
