package com.troikoss.continuum_explorer.ui.preview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.audio.AudioEqPreset
import com.troikoss.continuum_explorer.ui.audio.AudioPlayerDisplayMode
import com.troikoss.continuum_explorer.ui.audio.AudioPlayerManager
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stereoscopic 3D Video Conversion Modes supported in real time:
 * - OFF: Standard 2D Video
 * - RED_GREEN: Red-Green Anaglyph 3D (Left Eye = Red, Right Eye = Green)
 * - RED_CYAN: Red-Cyan Anaglyph 3D (Left Eye = Red, Right Eye = Cyan)
 * - SIDE_BY_SIDE: Split Side-by-Side (SBS) 3D for VR / 3D Displays
 */
enum class Video3dMode(val label: String, val badge: String) {
    OFF("2D Standard", "2D"),
    RED_GREEN("Red-Green 3D", "3D R/G"),
    RED_CYAN("Red-Cyan 3D", "3D R/C"),
    SIDE_BY_SIDE("Side-by-Side 3D", "3D SBS")
}

data class VideoTrackChoice(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

private enum class SwipeGestureType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK
}

/**
 * Modifier that converts any TextureView-backed video surface into a real-time
 * Red-Green 3D, Red-Cyan 3D, or Side-by-Side (SBS) 3D stereoscopic video!
 */
fun Modifier.stereoscopic3dEffect(
    mode: Video3dMode,
    parallaxDp: Float = 6f
): Modifier = this.drawWithContent {
    if (mode == Video3dMode.OFF) {
        drawContent()
        return@drawWithContent
    }

    val bounds = Rect(0f, 0f, size.width, size.height)
    val offsetPx = parallaxDp.dp.toPx()

    if (mode == Video3dMode.SIDE_BY_SIDE) {
        drawIntoCanvas { canvas ->
            // Left Eye (Left 50% of screen)
            canvas.save()
            canvas.clipRect(0f, 0f, size.width / 2f, size.height)
            canvas.scale(0.5f, 1f)
            this@drawWithContent.drawContent()
            canvas.restore()

            // Right Eye (Right 50% of screen)
            canvas.save()
            canvas.clipRect(size.width / 2f, 0f, size.width, size.height)
            canvas.translate(size.width / 2f, 0f)
            canvas.scale(0.5f, 1f)
            this@drawWithContent.drawContent()
            canvas.restore()
        }
        return@drawWithContent
    }

    // Left Eye = Red channel
    val leftEyeRedMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    // Right Eye = Green channel (for Red-Green 3D) or Green+Blue Cyan channel (for Red-Cyan 3D)
    val rightEyeMatrix = if (mode == Video3dMode.RED_GREEN) {
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0.15f, 0.85f, 0f, 0f, 0f,
                0f, 0.15f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    } else {
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    val leftPaint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(leftEyeRedMatrix)
    }
    val rightPaint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(rightEyeMatrix)
        blendMode = BlendMode.Screen
    }

    drawIntoCanvas { canvas ->
        // Base layer
        canvas.saveLayer(bounds, Paint())

        // 1. Draw Left-Eye Red Pass shifted left by -offsetPx
        canvas.saveLayer(bounds, leftPaint)
        canvas.translate(-offsetPx, 0f)
        this@drawWithContent.drawContent()
        canvas.restore()

        // 2. Draw Right-Eye Green/Cyan Pass shifted right by +offsetPx with Screen additive blend
        canvas.saveLayer(bounds, rightPaint)
        canvas.translate(offsetPx, 0f)
        this@drawWithContent.drawContent()
        canvas.restore()

        canvas.restore()
    }
}

/**
 * Redesigned Dedicated Video Player with:
 * - Audio Track Selection & Switching
 * - Caption / Subtitle Track Selection & Switching + Live SubtitleView
 * - Playback Speed Selector (0.25x - 3.0x)
 * - Real-Time Red-Green 3D / Red-Cyan 3D / Side-by-Side 3D Video Conversion + Adjustable 3D Depth
 * - Interactive Swipe Gestures (Left vertical = Brightness, Right vertical = Volume, Horizontal = Smooth Seek, Double-Tap = +/-10s)
 * - Zero Black-Screen TextureView Surface Binding (Android 8 - Android 15)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DedicatedVideoPlayerView(
    file: UniversalFile,
    appState: FileExplorerState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = view.context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uri = remember(file) { getUriForUniversalFile(context, file) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var resolutionLabel by remember(file) { mutableStateOf<String?>(null) }

    // 3D Mode & Parallax Depth
    var mode3d by remember { mutableStateOf(Video3dMode.OFF) }
    var depth3dDp by remember { mutableFloatStateOf(6f) }

    // Menus
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var show3dMenu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    // Audio & Subtitle Tracks
    var audioTracks by remember { mutableStateOf<List<VideoTrackChoice>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<VideoTrackChoice>>(emptyList()) }
    var currentCues by remember { mutableStateOf<CueGroup?>(null) }

    // Swipe Gesture State (Brightness, Volume, Seek)
    var activeGesture by remember { mutableStateOf(SwipeGestureType.NONE) }
    var brightnessLevel by remember {
        val currentWindowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (currentWindowBrightness in 0.05f..1f) currentWindowBrightness else 0.75f)
    }
    var volumeLevel by remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf((curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f))
    }
    var seekPreviewPos by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var skipIndicatorText by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            if (uri != null) {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
    }

    fun rebuildTracks(tracks: Tracks) {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        audioTracks = audioGroups.mapIndexed { idx, group ->
            val fmt = group.getTrackFormat(0)
            val lang = fmt.language?.let { Locale(it).displayLanguage }?.takeIf { it.isNotBlank() }
            val channelInfo = if (fmt.channelCount > 0) "${fmt.channelCount}ch" else null
            val mime = fmt.sampleMimeType?.substringAfter('/')?.uppercase()
            val label = listOfNotNull(
                fmt.label ?: lang ?: "Audio Track ${idx + 1}",
                channelInfo,
                mime
            ).joinToString(" • ")
            VideoTrackChoice(label = label, groupIndex = idx, trackIndex = 0, isSelected = group.isSelected)
        }

        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        subtitleTracks = textGroups.mapIndexed { idx, group ->
            val fmt = group.getTrackFormat(0)
            val lang = fmt.language?.let { Locale(it).displayLanguage }?.takeIf { it.isNotBlank() }
            val label = fmt.label ?: lang ?: "Caption Track ${idx + 1}"
            VideoTrackChoice(label = label, groupIndex = idx, trackIndex = 0, isSelected = group.isSelected)
        }
    }

    // Extract video dimensions asynchronously
    LaunchedEffect(file) {
        resolutionLabel = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    val fRef = file.fileRef
                    if (fRef != null && fRef.exists()) {
                        retriever.setDataSource(fRef.absolutePath)
                    } else if (uri != null) {
                        retriever.setDataSource(context, uri)
                    }
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    if (w != null && h != null && w > 0 && h > 0) {
                        videoAspectRatio = w.toFloat() / h.toFloat()
                        "${w}×${h}"
                    } else null
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }

    LaunchedEffect(skipIndicatorText) {
        if (skipIndicatorText != null) {
            delay(750)
            skipIndicatorText = null
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    rebuildTracks(exoPlayer.currentTracks)
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                rebuildTracks(tracks)
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                    resolutionLabel = "${videoSize.width}×${videoSize.height}"
                }
            }
            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            appState.isVideoFullScreen = false
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_surface")
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width * 0.4f) {
                            val next = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(next)
                            currentPos = next
                            skipIndicatorText = "⏪ -10s"
                        } else if (offset.x > size.width * 0.6f) {
                            val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                            val next = (exoPlayer.currentPosition + 10_000L).coerceAtMost(if (maxDur > 0) maxDur else Long.MAX_VALUE)
                            exoPlayer.seekTo(next)
                            currentPos = next
                            skipIndicatorText = "⏩ +10s"
                        } else {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    }
                )
            }
            .pointerInput(duration) {
                var startX = 0f
                var totalDx = 0f
                var totalDy = 0f
                var initialPosMs = 0L

                detectDragGestures(
                    onDragStart = { startOffset ->
                        startX = startOffset.x
                        totalDx = 0f
                        totalDy = 0f
                        initialPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                        seekPreviewPos = initialPosMs
                        seekDeltaMs = 0L
                        activeGesture = SwipeGestureType.NONE
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y

                        if (activeGesture == SwipeGestureType.NONE) {
                            if (abs(totalDx) > 18f || abs(totalDy) > 18f) {
                                activeGesture = if (abs(totalDx) > abs(totalDy) * 1.25f) {
                                    SwipeGestureType.SEEK
                                } else if (startX < size.width * 0.45f) {
                                    SwipeGestureType.BRIGHTNESS
                                } else {
                                    SwipeGestureType.VOLUME
                                }
                            }
                        }

                        when (activeGesture) {
                            SwipeGestureType.BRIGHTNESS -> {
                                val delta = -dragAmount.y / size.height.coerceAtLeast(1)
                                brightnessLevel = (brightnessLevel + delta * 1.35f).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = brightnessLevel
                                    win.attributes = lp
                                }
                            }
                            SwipeGestureType.VOLUME -> {
                                val delta = -dragAmount.y / size.height.coerceAtLeast(1)
                                volumeLevel = (volumeLevel + delta * 1.35f).coerceIn(0f, 1f)
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val targetStreamVol = (volumeLevel * maxVol).roundToInt().coerceIn(0, maxVol)
                                runCatching {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
                                }
                                isMuted = volumeLevel <= 0.01f
                                exoPlayer.volume = volumeLevel
                            }
                            SwipeGestureType.SEEK -> {
                                val maxDur = exoPlayer.duration.coerceAtLeast(60_000L)
                                val seekSpan = maxDur.coerceAtMost(180_000L) // full width swipe = up to 3 mins
                                val deltaMs = ((totalDx / size.width.coerceAtLeast(1)) * seekSpan).toLong()
                                seekDeltaMs = deltaMs
                                seekPreviewPos = (initialPosMs + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(1L))
                            }
                            SwipeGestureType.NONE -> {}
                        }
                    },
                    onDragEnd = {
                        if (activeGesture == SwipeGestureType.SEEK) {
                            exoPlayer.seekTo(seekPreviewPos)
                            currentPos = seekPreviewPos
                        }
                        activeGesture = SwipeGestureType.NONE
                    },
                    onDragCancel = {
                        activeGesture = SwipeGestureType.NONE
                    }
                )
            }
    ) {
        if (uri != null) {
            // TextureView-backed AspectRatioFrameLayout so stereoscopic 3D anaglyph shaders & transforms work on Android 8 - 15 with zero black screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .stereoscopic3dEffect(mode = mode3d, parallaxDp = depth3dDp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        AspectRatioFrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.resizeMode = resizeMode
                            setAspectRatio(videoAspectRatio)

                            val textureView = TextureView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                keepScreenOn = true
                            }
                            addView(textureView)
                            exoPlayer.setVideoTextureView(textureView)

                            val subtitleView = SubtitleView(ctx).apply {
                                tag = "SUBTITLE_VIEW"
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setUserDefaultStyle()
                                setUserDefaultTextSize()
                            }
                            addView(subtitleView)
                        }
                    },
                    update = { container ->
                        if (container.resizeMode != resizeMode) {
                            container.resizeMode = resizeMode
                        }
                        container.setAspectRatio(videoAspectRatio.coerceAtLeast(0.2f))
                        val subView = container.findViewWithTag<SubtitleView>("SUBTITLE_VIEW")
                        subView?.setCues(currentCues?.cues ?: emptyList())
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Software brightness dimmer overlay when user dims below 30%
            if (brightnessLevel < 0.35f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (0.35f - brightnessLevel) * 1.4f))
                )
            }
        } else {
            Text(
                "Unable to load video stream",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Live Swipe Gesture HUD Pill (Brightness, Volume, or Seek Scrubbing)
        AnimatedVisibility(
            visible = activeGesture != SwipeGestureType.NONE || skipIndicatorText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xD9121216),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                        .widthIn(min = 160.dp, max = 240.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        skipIndicatorText != null -> {
                            Text(
                                text = skipIndicatorText!!,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        activeGesture == SwipeGestureType.BRIGHTNESS -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.BrightnessHigh, contentDescription = null, tint = Color(0xFFFACC15))
                                Text(
                                    text = "Brightness ${(brightnessLevel * 100).roundToInt()}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { brightnessLevel },
                                color = Color(0xFFFACC15),
                                trackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                        activeGesture == SwipeGestureType.VOLUME -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (volumeLevel <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "Volume ${(volumeLevel * 100).roundToInt()}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { volumeLevel },
                                color = Color(0xFF38BDF8),
                                trackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                        activeGesture == SwipeGestureType.SEEK -> {
                            val sign = if (seekDeltaMs >= 0) "+" else "-"
                            val absSec = abs(seekDeltaMs) / 1000L
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (seekDeltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954)
                                )
                                Text(
                                    text = "${formatDurationMs(seekPreviewPos)} ($sign${absSec}s)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (duration > 0) (seekPreviewPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                                },
                                color = Color(0xFF1DB954),
                                trackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }

        if (showControls) {
            // Top Bar: File Name, Resolution Badge, 3D Mode Selector, Aspect Ratio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (appState.isVideoFullScreen) {
                        IconButton(onClick = { appState.isVideoFullScreen = false }) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column {
                        Text(
                            text = file.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Swipe Left: Brightness • Right: Volume • Horizontal: Seek",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    resolutionLabel?.let { res ->
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = res,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 3D Video Mode Toggle Button (2D / Red-Green 3D / Red-Cyan 3D / SBS 3D)
                    Box {
                        Surface(
                            color = if (mode3d != Video3dMode.OFF) Color(0xFFEF4444).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
                            border = BorderStroke(
                                1.dp,
                                if (mode3d != Video3dMode.OFF) Color(0xFF22C55E) else Color.White.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .testTag("video_3d_mode_button")
                                .clickable { show3dMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.MovieFilter,
                                    contentDescription = "3D Video Mode",
                                    tint = if (mode3d != Video3dMode.OFF) Color(0xFF4ADE80) else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = mode3d.badge,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = show3dMenu,
                            onDismissRequest = { show3dMenu = false }
                        ) {
                            Text(
                                text = "3D Stereoscopic Mode",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Video3dMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label) },
                                    trailingIcon = {
                                        if (mode3d == m) Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        mode3d = m
                                        show3dMenu = false
                                    }
                                )
                            }
                            if (mode3d == Video3dMode.RED_GREEN || mode3d == Video3dMode.RED_CYAN) {
                                HorizontalDivider()
                                Text(
                                    text = "3D Depth Offset (${depth3dDp.roundToInt()}px)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                listOf(3f to "Subtle (3px)", 6f to "Standard (6px)", 10f to "Deep (10px)").forEach { (d, lbl) ->
                                    DropdownMenuItem(
                                        text = { Text(lbl) },
                                        trailingIcon = {
                                            if (depth3dDp == d) Icon(Icons.Default.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            depth3dDp = d
                                            show3dMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Aspect Ratio Toggle (Fit / Crop / Stretch)
                    TextButton(
                        onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                else -> "Fit"
                            },
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Bottom Controls Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Seekbar Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        formatDurationMs(currentPos),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { ratio ->
                            val newPos = (ratio * duration).toLong()
                            exoPlayer.seekTo(newPos)
                            currentPos = newPos
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        formatDurationMs(duration),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                // Track / Speed / Loop / 3D Quick Action Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Audio Track Selector Pill
                    Box {
                        AssistChip(
                            onClick = { showAudioMenu = true },
                            leadingIcon = {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            },
                            label = {
                                val activeAudio = audioTracks.firstOrNull { it.isSelected }?.label ?: "Audio (${audioTracks.size.coerceAtLeast(1)})"
                                Text(activeAudio, color = Color.White, fontSize = 11.sp, maxLines = 1)
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.12f)
                            ),
                            border = null,
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("video_audio_track_button")
                        )
                        DropdownMenu(
                            expanded = showAudioMenu,
                            onDismissRequest = { showAudioMenu = false }
                        ) {
                            Text(
                                text = "Audio Tracks",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            if (audioTracks.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Default Audio Stream") },
                                    trailingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = { showAudioMenu = false }
                                )
                            } else {
                                audioTracks.forEachIndexed { idx, track ->
                                    DropdownMenuItem(
                                        text = { Text(track.label) },
                                        trailingIcon = {
                                            if (track.isSelected) Icon(Icons.Default.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            val audioGroups = exoPlayer.currentTracks.groups.filter {
                                                it.type == C.TRACK_TYPE_AUDIO && it.isSupported
                                            }
                                            if (idx < audioGroups.size) {
                                                val override = TrackSelectionOverride(audioGroups[idx].mediaTrackGroup, 0)
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(override)
                                                    .build()
                                            }
                                            showAudioMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Captions / Subtitles Selector Pill
                    Box {
                        val activeSub = subtitleTracks.firstOrNull { it.isSelected }
                        AssistChip(
                            onClick = { showSubtitleMenu = true },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = if (activeSub != null) Color(0xFF4ADE80) else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    activeSub?.label ?: "Captions: Off",
                                    color = if (activeSub != null) Color(0xFF4ADE80) else Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.12f)
                            ),
                            border = null,
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("video_caption_track_button")
                        )
                        DropdownMenu(
                            expanded = showSubtitleMenu,
                            onDismissRequest = { showSubtitleMenu = false }
                        ) {
                            Text(
                                text = "Captions & Subtitles",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            val subsOff = subtitleTracks.none { it.isSelected }
                            DropdownMenuItem(
                                text = { Text("Off") },
                                trailingIcon = {
                                    if (subsOff) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                        .setPreferredTextLanguage(null)
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .build()
                                    currentCues = null
                                    showSubtitleMenu = false
                                }
                            )
                            if (subtitleTracks.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No embedded subtitle tracks found") },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                subtitleTracks.forEachIndexed { idx, track ->
                                    DropdownMenuItem(
                                        text = { Text(track.label) },
                                        trailingIcon = {
                                            if (track.isSelected) Icon(Icons.Default.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            val textGroups = exoPlayer.currentTracks.groups.filter {
                                                it.type == C.TRACK_TYPE_TEXT && it.isSupported
                                            }
                                            if (idx < textGroups.size) {
                                                val override = TrackSelectionOverride(textGroups[idx].mediaTrackGroup, 0)
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(override)
                                                    .build()
                                            }
                                            showSubtitleMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Playback Speed Selector Pill
                    Box {
                        AssistChip(
                            onClick = { showSpeedMenu = true },
                            leadingIcon = {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            },
                            label = {
                                Text("Speed ${playbackSpeed}x", color = Color.White, fontSize = 11.sp)
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.12f)
                            ),
                            border = null,
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("video_speed_button")
                        )
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text(if (speed == 1.0f) "1.0x (Normal)" else "${speed}x") },
                                    trailingIcon = {
                                        if (playbackSpeed == speed) Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Loop Toggle Chip
                    AssistChip(
                        onClick = {
                            isLooping = !isLooping
                            exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = null,
                                tint = if (isLooping) Color(0xFF4ADE80) else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(if (isLooping) "Loop: On" else "Loop: Off", color = Color.White, fontSize = 11.sp)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        border = null,
                        modifier = Modifier.height(28.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Main Transport Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else volumeLevel.coerceAtLeast(0.2f)
                    }) {
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconButton(onClick = {
                            val next = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(next)
                            currentPos = next
                            skipIndicatorText = "⏪ -10s"
                        }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        FilledIconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = {
                            val next = (exoPlayer.currentPosition + 10_000L).coerceAtMost(duration.coerceAtLeast(0L))
                            exoPlayer.seekTo(next)
                            currentPos = next
                            skipIndicatorText = "⏩ +10s"
                        }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, "Open Video With")) }
                                    .onFailure { Toast.makeText(context, "No external video player found", Toast.LENGTH_SHORT).show() }
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "External Player", tint = Color.White)
                        }

                        IconButton(onClick = {
                            appState.isVideoFullScreen = !appState.isVideoFullScreen
                        }) {
                            Icon(
                                if (appState.isVideoFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated In-Tab Audio Player with Quick Launch into the 3-Mode Studio Music Player
 * (Bottom Bar, Pop-Up Window, or Fullscreen Studio) + Equalizer Presets & Lifecycle-Aware Playback.
 */
@Composable
fun DedicatedAudioPlayerView(
    file: UniversalFile,
    appState: FileExplorerState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uri = remember(file) { getUriForUniversalFile(context, file) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    var trackTitle by remember(file) { mutableStateOf(file.name.substringBeforeLast('.')) }
    var trackArtist by remember(file) { mutableStateOf("Audio Track") }
    var trackAlbum by remember(file) { mutableStateOf<String?>(null) }
    var trackBitrate by remember(file) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            if (uri != null) {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
    }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    val fRef = file.fileRef
                    if (fRef != null && fRef.exists()) {
                        retriever.setDataSource(fRef.absolutePath)
                    } else if (uri != null) {
                        retriever.setDataSource(context, uri)
                    }
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                    if (!title.isNullOrBlank()) trackTitle = title
                    if (!artist.isNullOrBlank()) trackArtist = artist
                    if (!album.isNullOrBlank()) trackAlbum = album
                    if (bitrate != null && bitrate > 0) trackBitrate = "${bitrate / 1000} kbps"
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vinyl / Album Art Disc
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(84.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = listOfNotNull(
                        trackArtist,
                        trackAlbum,
                        file.name.substringAfterLast('.', "").uppercase().ifEmpty { null },
                        trackBitrate,
                        appState.formatSize(file.length)
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { ratio ->
                        val newPos = (ratio * duration).toLong()
                        exoPlayer.seekTo(newPos)
                        currentPos = newPos
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDurationMs(currentPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDurationMs(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isLooping = !isLooping
                        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    }) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Loop",
                            tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        val next = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(next)
                        currentPos = next
                    }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Back 10s", modifier = Modifier.size(30.dp))
                    }

                    FilledIconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.size(62.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = {
                        val next = (exoPlayer.currentPosition + 10_000L).coerceAtMost(duration.coerceAtLeast(0L))
                        exoPlayer.seekTo(next)
                        currentPos = next
                    }) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(30.dp))
                    }

                    Box {
                        TextButton(onClick = { showSpeedMenu = true }) {
                            Text("${playbackSpeed}x", fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Quick Launch into Pop-Up Window or Fullscreen Studio Overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            exoPlayer.pause()
                            AudioPlayerManager.playTrack(
                                context = context,
                                track = file,
                                folderCandidates = appState.files,
                                initialMode = AudioPlayerDisplayMode.FLOATING_PIP
                            )
                            AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FLOATING_PIP
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pop-Up Mode", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            exoPlayer.pause()
                            AudioPlayerManager.playTrack(
                                context = context,
                                track = file,
                                folderCandidates = appState.files,
                                initialMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                            )
                            AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fullscreen Studio", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
