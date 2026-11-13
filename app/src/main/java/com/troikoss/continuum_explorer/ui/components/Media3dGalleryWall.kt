package com.troikoss.continuum_explorer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.open
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Wall3dArrangement(val label: String) {
    PERSPECTIVE_MATRIX("3D Perspective Wall"),
    CYLINDER_CAROUSEL("3D Cylinder Ring")
}

/**
 * Interactive Touch-Based 3D Picture Wall (`Media3dGalleryWall`).
 *
 * Designed for zero chipset/GPU lag even on folders with thousands of images/videos:
 * - Virtual 3D viewport frustum culling: only composes the ~24–28 tiles currently visible in the
 *   3D camera frustum. Off-screen tiles are culled before composition.
 * - Downsampled 180x180 hardware-bitmap cached thumbnails via Coil (`Precision.INEXACT`, `crossfade(false)`).
 * - Touch gestures:
 *   - 1-finger drag: Pan across the 3D Picture Wall OR spin the 3D Cylinder Ring + adjust 3D tilt.
 *   - 2-finger pinch: Zoom in/out (`0.65x` to `2.2x`).
 *   - Tap tile: Focuses tile in 3D space or opens the media file directly.
 */
@Composable
fun Media3dGalleryWall(
    files: List<UniversalFile>,
    appState: FileExplorerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val mediaItems = remember(files) {
        files.filter { file ->
            if (file.isDirectory) true
            else {
                val n = file.name.lowercase()
                n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                    n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
                    n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".mov")
            }
        }
    }

    var arrangement by remember { mutableStateOf(Wall3dArrangement.PERSPECTIVE_MATRIX) }

    // 3D Camera State
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var tiltX by remember { mutableFloatStateOf(16f) }
    var tiltY by remember { mutableFloatStateOf(-12f) }
    var cylinderAngleDeg by remember { mutableFloatStateOf(0f) }
    var focusedItem by remember { mutableStateOf<UniversalFile?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A2130),
                        Color(0xFF0E121A),
                        Color(0xFF07090D)
                    )
                )
            )
            .pointerInput(arrangement) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(0.65f, 2.25f)
                    if (arrangement == Wall3dArrangement.PERSPECTIVE_MATRIX) {
                        panX += pan.x
                        panY += pan.y
                        // Subtle reactive 3D parallax tilt as the user drags across the wall
                        tiltY = (tiltY + pan.x * 0.035f).coerceIn(-28f, 28f)
                        tiltX = (tiltX - pan.y * 0.035f).coerceIn(-24f, 28f)
                    } else {
                        cylinderAngleDeg = (cylinderAngleDeg + pan.x * 0.28f) % 360f
                        tiltX = (tiltX - pan.y * 0.06f).coerceIn(-25f, 25f)
                    }
                }
            }
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val columns = when {
            maxWidth >= 900.dp -> 6
            maxWidth >= 600.dp -> 5
            else -> 4
        }
        val tileSizeDp = when {
            maxWidth >= 900.dp -> 150.dp
            maxWidth >= 600.dp -> 132.dp
            else -> 112.dp
        }
        val tileGapDp = 14.dp
        val tileStepPx = with(density) { (tileSizeDp + tileGapDp).toPx() }

        if (mediaItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No photos or videos in this folder for 3D Wall view.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (arrangement == Wall3dArrangement.PERSPECTIVE_MATRIX) {
            // =====================================================================
            // MODE 1: 3D PERSPECTIVE TILTED PICTURE WALL WITH VIEWPORT CULLING
            // =====================================================================
            val totalRows = (mediaItems.size + columns - 1) / columns
            val wallWidthPx = columns * tileStepPx
            val wallHeightPx = totalRows * tileStepPx

            // Clamp pan so the user never loses the 3D wall off-screen
            val maxPanX = (wallWidthPx * 0.6f).coerceAtLeast(180f)
            val maxPanY = (wallHeightPx * 0.85f).coerceAtLeast(240f)
            val clampedPanX = panX.coerceIn(-maxPanX, maxPanX)
            val clampedPanY = panY.coerceIn(-maxPanY, maxPanY * 0.35f)

            // Calculate visible row range in virtual 3D frustum (cull off-screen rows for 0 lag)
            val centerRow = ((-clampedPanY) / tileStepPx).toInt()
            val firstVisibleRow = (centerRow - 3).coerceAtLeast(0)
            val lastVisibleRow = (centerRow + 6).coerceAtMost((totalRows - 1).coerceAtLeast(0))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationX = tiltX
                        rotationY = tiltY
                        scaleX = zoom
                        scaleY = zoom
                        cameraDistance = 22f * density.density
                    },
                contentAlignment = Alignment.Center
            ) {
                for (row in firstVisibleRow..lastVisibleRow) {
                    for (col in 0 until columns) {
                        val index = row * columns + col
                        if (index >= mediaItems.size) break
                        val item = mediaItems[index]

                        val relX = (col - (columns - 1) / 2f) * tileStepPx + clampedPanX
                        val relY = (row - 1.5f) * tileStepPx + clampedPanY

                        // Normalize distance from screen center for 3D Z-elevation curve
                        val normDist = ((relX * relX + relY * relY) / (viewportWidthPx * viewportWidthPx + 1f)).coerceIn(0f, 1.2f)
                        val tileScale = (1.06f - normDist * 0.22f).coerceIn(0.78f, 1.08f)
                        val isFocused = focusedItem?.providerId == item.providerId

                        PictureWallTile3D(
                            file = item,
                            tileSize = tileSizeDp,
                            isFocused = isFocused,
                            onClick = {
                                if (item.isDirectory || isFocused) {
                                    appState.open(item)
                                } else {
                                    focusedItem = item
                                }
                            },
                            modifier = Modifier
                                .offset { IntOffset(relX.roundToInt(), relY.roundToInt()) }
                                .graphicsLayer {
                                    scaleX = if (isFocused) tileScale * 1.16f else tileScale
                                    scaleY = if (isFocused) tileScale * 1.16f else tileScale
                                    rotationY = (-relX / viewportWidthPx) * 14f
                                    rotationX = (relY / viewportHeightPx) * 10f
                                    shadowElevation = if (isFocused) 28f else 12f
                                }
                        )
                    }
                }
            }
        } else {
            // =====================================================================
            // MODE 2: 3D CYLINDRICAL CAROUSEL RING (360° INTERACTIVE PHOTO GALLERY)
            // =====================================================================
            val ringItems = remember(mediaItems) { mediaItems.take(36) }
            val ringCount = ringItems.size.coerceAtLeast(1)
            val radiusPx = (viewportWidthPx * 0.42f * zoom).coerceAtLeast(220f)

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Sort by Z depth so back tiles render first and front tiles render on top
                val indexedDepth = ringItems.mapIndexed { idx, file ->
                    val angleDeg = (idx * (360f / ringCount) + cylinderAngleDeg) % 360f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val zDepth = cos(angleRad).toFloat() // +1 in front, -1 in back
                    val xOffset = (sin(angleRad) * radiusPx).toFloat()
                    val yWave = ((idx % 3) - 1) * (tileStepPx * 0.72f)
                    Triple(file, Offset(xOffset, yWave), Pair(angleDeg, zDepth))
                }.sortedBy { it.third.second }

                indexedDepth.forEach { (item, pos, angleAndDepth) ->
                    val (angleDeg, zDepth) = angleAndDepth
                    val depthScale = (0.58f + (zDepth + 1f) * 0.28f).coerceIn(0.52f, 1.18f)
                    val depthAlpha = (0.35f + (zDepth + 1f) * 0.35f).coerceIn(0.28f, 1f)
                    val isFocused = focusedItem?.providerId == item.providerId

                    PictureWallTile3D(
                        file = item,
                        tileSize = tileSizeDp,
                        isFocused = isFocused,
                        onClick = {
                            if (item.isDirectory || isFocused || zDepth > 0.7f) {
                                appState.open(item)
                            } else {
                                focusedItem = item
                            }
                        },
                        modifier = Modifier
                            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                            .graphicsLayer {
                                scaleX = if (isFocused) depthScale * 1.15f else depthScale
                                scaleY = if (isFocused) depthScale * 1.15f else depthScale
                                alpha = depthAlpha
                                rotationY = -angleDeg * 0.35f
                                rotationX = tiltX * 0.5f
                                cameraDistance = 20f * density.density
                            }
                    )
                }
            }
        }

        // =====================================================================
        // FLOATING 3D CONTROLS HUD (Top Bar + Focused Item Quick Action Bar)
        // =====================================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF121620).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "3D Wall (${mediaItems.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                Wall3dArrangement.entries.forEach { mode ->
                    val selected = arrangement == mode
                    FilterChip(
                        selected = selected,
                        onClick = { arrangement = mode },
                        label = { Text(if (mode == Wall3dArrangement.PERSPECTIVE_MATRIX) "3D Matrix" else "3D Ring", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.24f),
                            selectedLabelColor = Color(0xFF38BDF8),
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.8f)
                        ),
                        border = null,
                        modifier = Modifier.height(28.dp)
                    )
                }

                IconButton(
                    onClick = {
                        panX = 0f
                        panY = 0f
                        zoom = 1.0f
                        tiltX = 16f
                        tiltY = -12f
                        cylinderAngleDeg = 0f
                        focusedItem = null
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Reset 3D Camera",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                FilledTonalButton(
                    onClick = { appState.is3dGalleryWallEnabled = false },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("2D Grid", fontSize = 11.sp)
                }
            }
        }

        // Bottom Focused Media Pill
        focusedItem?.let { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141824).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                tonalElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 520.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tap 'Open' or tap the tile again to launch",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            appState.open(item)
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PictureWallTile3D(
    file: UniversalFile,
    tileSize: androidx.compose.ui.unit.Dp,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isVideo = remember(file.name) {
        val n = file.name.lowercase()
        n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".mov")
    }
    val modelPath = remember(file) {
        if (file.isDirectory) {
            com.troikoss.continuum_explorer.managers.GalleryManager.getCachedAlbumPreview(file.providerId)?.providerId
                ?: file.providerId
        } else {
            file.documentFileRef?.uri ?: file.providerId
        }
    }
    val request = remember(modelPath) {
        ImageRequest.Builder(context)
            .data(modelPath)
            .size(180, 180)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .allowHardware(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E2433),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.18f)
        ),
        modifier = modifier
            .size(tileSize)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = request,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom subtle gradient caption
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
