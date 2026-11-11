package com.troikoss.continuum_explorer.ui.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.ui.preview.Video3dMode
import com.troikoss.continuum_explorer.ui.preview.stereoscopic3dEffect
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.utils.getSiblingFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Represents a selectable audio or subtitle track shown in the menu. */
data class TrackOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

/** All supported aspect-ratio resize modes with display names. */
@androidx.media3.common.util.UnstableApi
enum class ResizeMode(val labelRes: Int, val value: Int) {
    FIT(R.string.media_fit, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL(R.string.media_fill, AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM(R.string.media_zoom, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIXED_WIDTH(R.string.media_fixed_w, AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_HEIGHT(R.string.media_fixed_h, AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
}

private enum class ActivitySwipeType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK
}

class VideoPlayerActivity : FullscreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUri = intent.data?.toString() ?: intent.getStringExtra("VIDEO_URI")

        setContent {
            FileExplorerTheme {
                VideoPlayerScreen(
                    initialVideoUri = videoUri,
                    onToggleFullscreen = { toggleFullscreen() }
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VideoPlayerScreen(
    initialVideoUri: String?,
    onToggleFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = (LocalView.current.context as? Activity)
    val focusRequester = remember { FocusRequester() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // ── Playback state ──────────────────────────────────────────────────────
    var isPlaying by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var hasPrev by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var currentCues by remember { mutableStateOf<CueGroup?>(null) }

    // ── 3D Mode & Parallax ──────────────────────────────────────────────────
    var mode3d by remember { mutableStateOf(Video3dMode.OFF) }
    var depth3dDp by remember { mutableFloatStateOf(6f) }

    // ── Swipe Gesture HUD State ─────────────────────────────────────────────
    var activeSwipe by remember { mutableStateOf(ActivitySwipeType.NONE) }
    var brightnessLevel by remember {
        val b = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (b in 0.05f..1f) b else 0.75f)
    }
    var seekPreviewPos by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }

    // ── UI visibility ───────────────────────────────────────────────────────
    var isUiVisible by remember { mutableStateOf(true) }
    var hideTimerTrigger by remember { mutableIntStateOf(0) }
    var isHoveringControls by remember { mutableStateOf(false) }

    // ── Skip / play / pause indicators ─────────────────────────────────────
    var showSkipBackIndicator by remember { mutableStateOf(false) }
    var showSkipForwardIndicator by remember { mutableStateOf(false) }
    var showPauseIndicator by remember { mutableStateOf(false) }
    var showPlayIndicator by remember { mutableStateOf(false) }

    // ── Options menu ────────────────────────────────────────────────────────
    var optionsMenuExpanded by remember { mutableStateOf(false) }
    var optionsScreen by remember { mutableStateOf("MAIN") }

    // ── Volume ──────────────────────────────────────────────────────────────
    var volume by remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf((curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f))
    }

    // ── Playback speed ──────────────────────────────────────────────────────
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    // ── Aspect ratio ────────────────────────────────────────────────────────
    var resizeMode by remember { mutableStateOf(ResizeMode.FIT) }

    // ── Loop ────────────────────────────────────────────────────────────────
    var loopEnabled by remember { mutableStateOf(false) }

    // ── Thumbnail preview ────────────────────────────────────────────────────
    val thumbnailCache = remember { mutableStateMapOf<Long, ImageBitmap>() }
    var seekbarHoverFraction by remember { mutableStateOf<Float?>(null) }
    var thumbnailAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var currentVideoUri by remember { mutableStateOf(initialVideoUri) }

    // ── Tracks ──────────────────────────────────────────────────────────────
    var audioTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }

    fun buildTrackLists(tracks: Tracks) {
        audioTracks = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
            .mapIndexed { gi, group ->
                val fmt = group.getTrackFormat(0)
                val lang = fmt.language?.let { Locale(it).displayLanguage } ?: ""
                val label = when {
                    fmt.label != null -> fmt.label!!
                    lang.isNotEmpty() -> lang
                    else -> resources.getString(R.string.media_audio_track, gi + 1)
                }
                TrackOption(label, gi, 0, group.isSelected)
            }

        subtitleTracks = tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            .mapIndexed { gi, group ->
                val fmt = group.getTrackFormat(0)
                val lang = fmt.language?.let { Locale(it).displayLanguage } ?: ""
                val label = when {
                    fmt.label != null -> fmt.label!!
                    lang.isNotEmpty() -> lang
                    else -> resources.getString(R.string.media_subtitle_track, gi + 1)
                }
                TrackOption(label, gi, 0, group.isSelected)
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onEvents(player: Player, events: Player.Events) {
                hasNext = player.hasNextMediaItem()
                hasPrev = player.hasPreviousMediaItem()
            }
            override fun onTracksChanged(tracks: Tracks) { buildTrackLists(tracks) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentVideoUri = mediaItem?.localConfiguration?.uri?.toString()
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                }
            }
            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(loopEnabled) {
        exoPlayer.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(volume) {
        exoPlayer.volume = volume
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackParameters(PlaybackParameters(playbackSpeed))
    }

    LaunchedEffect(initialVideoUri) {
        if (initialVideoUri != null) {
            val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov")
            val allVideos = getSiblingFiles(context, initialVideoUri, videoExtensions)
            allVideos.forEach { uri -> exoPlayer.addMediaItem(MediaItem.fromUri(uri)) }
            val targetName = Uri.parse(initialVideoUri).lastPathSegment
            val startIndex = if (targetName != null)
                allVideos.indexOfFirst { Uri.parse(it).lastPathSegment == targetName }.coerceAtLeast(0)
            else 0
            exoPlayer.seekTo(startIndex, 0L)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            hasNext = exoPlayer.hasNextMediaItem()
            hasPrev = exoPlayer.hasPreviousMediaItem()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                delay(250)
            }
        }
    }

    LaunchedEffect(isUiVisible, hideTimerTrigger, isPlaying, isHoveringControls, optionsMenuExpanded) {
        if (isUiVisible && isPlaying && !isHoveringControls && !optionsMenuExpanded) {
            delay(3500)
            isUiVisible = false
        }
    }

    LaunchedEffect(optionsMenuExpanded) {
        if (!optionsMenuExpanded) optionsScreen = "MAIN"
    }

    LaunchedEffect(showSkipBackIndicator) { if (showSkipBackIndicator) { delay(600); showSkipBackIndicator = false } }
    LaunchedEffect(showSkipForwardIndicator) { if (showSkipForwardIndicator) { delay(600); showSkipForwardIndicator = false } }
    LaunchedEffect(showPauseIndicator) { if (showPauseIndicator) { delay(600); showPauseIndicator = false } }
    LaunchedEffect(showPlayIndicator) { if (showPlayIndicator) { delay(600); showPlayIndicator = false } }

    LaunchedEffect(currentVideoUri) {
        thumbnailCache.clear()
        thumbnailAspectRatio = 16f / 9f
        val uri = currentVideoUri ?: return@LaunchedEffect
        while (exoPlayer.duration <= 0L) { delay(200) }
        val duration = exoPlayer.duration
        val count = 36
        val interval = duration / count
        val retriever = MediaMetadataRetriever()
        try {
            val (thumbW, thumbH) = withContext(Dispatchers.IO) {
                retriever.setDataSource(context, Uri.parse(uri))
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 160
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 90
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) Pair(h, w) else Pair(w, h)
            }
            val aspect = thumbW.toFloat() / thumbH.toFloat().coerceAtLeast(1f)
            thumbnailAspectRatio = aspect
            val scaledW = 160
            val scaledH = (scaledW / aspect).toInt().coerceAtLeast(1)
            for (i in 0..count) {
                val ms = (i * interval).coerceAtMost(duration)
                val bmp = withContext(Dispatchers.IO) {
                    retriever.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (bmp != null) {
                    val scaled = withContext(Dispatchers.IO) {
                        val s = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true)
                        if (s !== bmp) bmp.recycle()
                        s
                    }
                    thumbnailCache[ms] = scaled.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        } finally {
            withContext(Dispatchers.IO) { runCatching { retriever.release() } }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .focusRequester(focusRequester)
                .focusable()
                .videoKeyboardControls(
                    exoPlayer = exoPlayer,
                    volume = volume,
                    onVolumeChange = { volume = it },
                    onToggleFullscreen = onToggleFullscreen,
                    onShowSkipBackIndicator = { showSkipBackIndicator = true },
                    onShowSkipForwardIndicator = { showSkipForwardIndicator = true },
                    onShowPauseIndicator = { showPauseIndicator = true },
                    onShowPlayIndicator = { showPlayIndicator = true },
                    onShowUi = { isUiVisible = true; hideTimerTrigger++ },
                    activity = activity
                )
        ) {
            // ── Video Surface with Real-Time 3D Stereoscopic Effect & Swipe Gestures ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .videoGestures(
                        isUiVisible = isUiVisible,
                        isPlaying = isPlaying,
                        exoPlayer = exoPlayer,
                        onUiVisibilityChange = { isUiVisible = it },
                        onHideTimerTrigger = { hideTimerTrigger++ },
                        onToggleFullscreen = onToggleFullscreen,
                        onShowPauseIndicator = { showPauseIndicator = true },
                        onShowPlayIndicator = { showPlayIndicator = true },
                        onShowSkipBackIndicator = { showSkipBackIndicator = true },
                        onShowSkipForwardIndicator = { showSkipForwardIndicator = true },
                        onShowUi = { isUiVisible = true; hideTimerTrigger++ }
                    )
                    .pointerInput(totalDuration) {
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
                                activeSwipe = ActivitySwipeType.NONE
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDx += dragAmount.x
                                totalDy += dragAmount.y

                                if (activeSwipe == ActivitySwipeType.NONE) {
                                    if (abs(totalDx) > 18f || abs(totalDy) > 18f) {
                                        activeSwipe = if (abs(totalDx) > abs(totalDy) * 1.25f) {
                                            ActivitySwipeType.SEEK
                                        } else if (startX < size.width * 0.45f) {
                                            ActivitySwipeType.BRIGHTNESS
                                        } else {
                                            ActivitySwipeType.VOLUME
                                        }
                                    }
                                }

                                when (activeSwipe) {
                                    ActivitySwipeType.BRIGHTNESS -> {
                                        val delta = -dragAmount.y / size.height.coerceAtLeast(1)
                                        brightnessLevel = (brightnessLevel + delta * 1.35f).coerceIn(0.05f, 1.0f)
                                        activity?.window?.let { win ->
                                            val lp = win.attributes
                                            lp.screenBrightness = brightnessLevel
                                            win.attributes = lp
                                        }
                                    }
                                    ActivitySwipeType.VOLUME -> {
                                        val delta = -dragAmount.y / size.height.coerceAtLeast(1)
                                        volume = (volume + delta * 1.35f).coerceIn(0f, 1f)
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val targetStreamVol = (volume * maxVol).roundToInt().coerceIn(0, maxVol)
                                        runCatching {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
                                        }
                                    }
                                    ActivitySwipeType.SEEK -> {
                                        val maxDur = exoPlayer.duration.coerceAtLeast(60_000L)
                                        val seekSpan = maxDur.coerceAtMost(180_000L)
                                        val deltaMs = ((totalDx / size.width.coerceAtLeast(1)) * seekSpan).toLong()
                                        seekDeltaMs = deltaMs
                                        seekPreviewPos = (initialPosMs + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(1L))
                                    }
                                    ActivitySwipeType.NONE -> {}
                                }
                            },
                            onDragEnd = {
                                if (activeSwipe == ActivitySwipeType.SEEK) {
                                    exoPlayer.seekTo(seekPreviewPos)
                                    currentPosition = seekPreviewPos
                                }
                                activeSwipe = ActivitySwipeType.NONE
                            },
                            onDragCancel = {
                                activeSwipe = ActivitySwipeType.NONE
                            }
                        )
                    }
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
                            this.resizeMode = resizeMode.value
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
                        container.resizeMode = resizeMode.value
                        container.setAspectRatio(videoAspectRatio.coerceAtLeast(0.2f))
                        val subView = container.findViewWithTag<SubtitleView>("SUBTITLE_VIEW")
                        subView?.setCues(currentCues?.cues ?: emptyList())
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Software dimmer when brightness < 35%
            if (brightnessLevel < 0.35f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (0.35f - brightnessLevel) * 1.4f))
                )
            }

            // ── Swipe Gesture Live HUD ──────────────────────────────────────
            AnimatedVisibility(
                visible = activeSwipe != ActivitySwipeType.NONE,
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
                        when (activeSwipe) {
                            ActivitySwipeType.BRIGHTNESS -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.BrightnessHigh, null, tint = Color(0xFFFACC15))
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
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                            ActivitySwipeType.VOLUME -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (volume <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        null,
                                        tint = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = "Volume ${(volume * 100).roundToInt()}%",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { volume },
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                            ActivitySwipeType.SEEK -> {
                                val sign = if (seekDeltaMs >= 0) "+" else "-"
                                val absSec = abs(seekDeltaMs) / 1000L
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (seekDeltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        null,
                                        tint = Color(0xFF1DB954)
                                    )
                                    Text(
                                        text = "${formatTime(seekPreviewPos)} ($sign${absSec}s)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (totalDuration > 0L) (seekPreviewPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                                    },
                                    color = Color(0xFF1DB954),
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                            ActivitySwipeType.NONE -> {}
                        }
                    }
                }
            }

            // ── Indicators ──────────────────────────────────────────────────
            AnimatedVisibility(visible = showPauseIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PauseCircle, stringResource(R.string.media_paused),
                    tint = Color.White, modifier = Modifier.size(96.dp))
            }
            AnimatedVisibility(visible = showPlayIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PlayCircle, stringResource(R.string.media_playing),
                    tint = Color.White, modifier = Modifier.size(96.dp))
            }
            AnimatedVisibility(visible = showSkipBackIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.3f).align(Alignment.CenterStart)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }
            AnimatedVisibility(visible = showSkipForwardIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.3f).align(Alignment.CenterEnd)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }

            // ── Top Bar (Back Button + Title + 3D Mode Pill) ────────────────
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.80f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = { activity?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = currentVideoUri?.let { Uri.parse(it).lastPathSegment } ?: "Video Player",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Quick 3D Mode Cycle Button
                    Surface(
                        color = if (mode3d != Video3dMode.OFF) Color(0xFFEF4444).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (mode3d != Video3dMode.OFF) Color(0xFF22C55E) else Color.White.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val modes = Video3dMode.entries
                            mode3d = modes[(modes.indexOf(mode3d) + 1) % modes.size]
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.MovieFilter,
                                contentDescription = "3D Mode",
                                tint = if (mode3d != Video3dMode.OFF) Color(0xFF4ADE80) else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = mode3d.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Bottom controls ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.type == PointerType.Mouse }) {
                                        isHoveringControls = when (event.type) {
                                            PointerEventType.Enter -> true
                                            PointerEventType.Exit -> false
                                            else -> isHoveringControls
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = { hideTimerTrigger++ }) }
                ) {
                    if (seekbarHoverFraction != null && totalDuration > 0L) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val p = measurable.measure(constraints.copy(minHeight = 0))
                                    layout(constraints.maxWidth, 0) { p.place(0, -p.height) }
                                }
                        ) {
                            val fraction = seekbarHoverFraction!!
                            val hoverMs = (fraction * totalDuration).toLong()
                            val bitmap = thumbnailCache.keys
                                .minByOrNull { k -> if (k >= hoverMs) k - hoverMs else hoverMs - k }
                                ?.let { thumbnailCache[it] }
                            val previewW = (135.dp * thumbnailAspectRatio).coerceIn(80.dp, 240.dp)
                            val previewImageH = previewW / thumbnailAspectRatio
                            val padDp = 8.dp
                            val thumbXDp = padDp + (maxWidth - padDp * 2) * fraction
                            val previewXDp = (thumbXDp - previewW / 2).coerceIn(0.dp, maxWidth - previewW)
                            Column(
                                modifier = Modifier
                                    .offset(x = previewXDp)
                                    .width(previewW)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xDD000000)),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(previewImageH)
                                    )
                                } else {
                                    Box(Modifier.fillMaxWidth().height(previewImageH).background(Color.DarkGray))
                                }
                                Text(
                                    text = formatTime(hoverMs),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Seekbar track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(horizontal = 8.dp)
                            .nonFocusable()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.type == PointerType.Mouse }) {
                                            when (event.type) {
                                                PointerEventType.Move -> seekbarHoverFraction =
                                                    (event.changes.first().position.x / size.width).coerceIn(0f, 1f)
                                                PointerEventType.Exit -> seekbarHoverFraction = null
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(totalDuration) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    currentPosition = (fraction * totalDuration).toLong()
                                    exoPlayer.seekTo(currentPosition)
                                    hideTimerTrigger++
                                }
                            }
                            .pointerInput("drag", totalDuration) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        seekbarHoverFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                        seekbarHoverFraction = fraction
                                        currentPosition = (fraction * totalDuration).toLong()
                                        exoPlayer.seekTo(currentPosition)
                                        hideTimerTrigger++
                                    },
                                    onDragEnd = { seekbarHoverFraction = null },
                                    onDragCancel = { seekbarHoverFraction = null }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val trackH = 3.dp.toPx()
                            val cy = size.height / 2f
                            val fraction = if (totalDuration > 0L) currentPosition.toFloat() / totalDuration else 0f
                            val activeX = (fraction * size.width).coerceIn(0f, size.width)
                            val thumbR = if (seekbarHoverFraction != null) 6.dp.toPx() else 4.dp.toPx()
                            drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, cy), Offset(size.width, cy), trackH, cap = StrokeCap.Round)
                            if (activeX > 0f) drawLine(Color.Red, Offset(0f, cy), Offset(activeX, cy), trackH, cap = StrokeCap.Round)
                            drawCircle(Color.White, thumbR, Offset(activeX, cy))
                        }
                    }

                    // Controls row
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) { exoPlayer.pause(); showPauseIndicator = true }
                                else { exoPlayer.play(); showPlayIndicator = true }
                            },
                            modifier = Modifier.nonFocusable()
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = stringResource(if (isPlaying) R.string.media_paused else R.string.media_playing),
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        IconButton(onClick = { if (hasPrev) exoPlayer.seekToPreviousMediaItem() }) {
                            Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous),
                                tint = if (hasPrev) Color.White else Color.Gray,
                                modifier = Modifier.size(36.dp))
                        }

                        IconButton(onClick = { if (hasNext) exoPlayer.seekToNextMediaItem() }) {
                            Icon(Icons.Default.SkipNext, stringResource(R.string.next),
                                tint = if (hasNext) Color.White else Color.Gray,
                                modifier = Modifier.size(36.dp))
                        }

                        IconButton(
                            onClick = { volume = if (volume > 0f) 0f else 1f },
                            modifier = Modifier.nonFocusable()
                        ) {
                            Icon(
                                imageVector = when {
                                    volume == 0f -> Icons.AutoMirrored.Filled.VolumeOff
                                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                    else -> Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = stringResource(R.string.media_volume),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = "${formatTime(currentPosition)} / ${formatTime(totalDuration)}",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.weight(1f))

                        if (playbackSpeed != 1f) {
                            Text(
                                text = "${playbackSpeed}×",
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        // ── Options button + multi-screen dropdown ──────────
                        Box {
                            IconButton(onClick = { optionsMenuExpanded = true }) {
                                Icon(Icons.Default.Settings, stringResource(R.string.options),
                                    tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            DropdownMenu(
                                expanded = optionsMenuExpanded,
                                onDismissRequest = { optionsMenuExpanded = false }
                            ) {
                                when (optionsScreen) {
                                    "MAIN" -> {
                                        // 3D Stereoscopic Mode
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Default.MovieFilter, null) },
                                            text = { Text("3D Video Mode") },
                                            trailingIcon = {
                                                Row {
                                                    Text(mode3d.label, color = Color.Gray)
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "3D" }
                                        )

                                        // Speed
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Default.FastForward, null) },
                                            text = { Text(stringResource(R.string.menu_speed)) },
                                            trailingIcon = {
                                                Row {
                                                    Text("${playbackSpeed}×", color = Color.Gray)
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "SPEED" }
                                        )

                                        // Audio track
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Default.Audiotrack, null) },
                                            text = { Text(stringResource(R.string.menu_audio_track)) },
                                            trailingIcon = {
                                                Row {
                                                    Text(
                                                        audioTracks.firstOrNull { it.isSelected }?.label
                                                            ?: audioTracks.firstOrNull()?.label
                                                            ?: "Default",
                                                        color = Color.Gray
                                                    )
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "AUDIO" }
                                        )

                                        // Subtitles
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                                            text = { Text(stringResource(R.string.menu_subtitles)) },
                                            trailingIcon = {
                                                Row {
                                                    val sel = subtitleTracks.firstOrNull { it.isSelected }
                                                    Text(sel?.label ?: stringResource(R.string.off), color = Color.Gray)
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "SUBTITLES" }
                                        )

                                        // Aspect ratio
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Default.AspectRatio, null) },
                                            text = { Text(stringResource(R.string.menu_aspect_ratio)) },
                                            trailingIcon = {
                                                Row {
                                                    Text(stringResource(resizeMode.labelRes), color = Color.Gray)
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "ASPECT" }
                                        )

                                        HorizontalDivider()

                                        // Loop toggle
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Default.Repeat, null,
                                                    tint = if (loopEnabled) Color.Red else Color.Gray)
                                            },
                                            text = { Text(if (loopEnabled) stringResource(R.string.media_loop_on) else stringResource(R.string.media_loop_off)) },
                                            trailingIcon = {
                                                if (loopEnabled) Icon(Icons.Default.Check, null)
                                            },
                                            onClick = {
                                                loopEnabled = !loopEnabled
                                                optionsMenuExpanded = false
                                            }
                                        )
                                    }

                                    "3D" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()
                                        Video3dMode.entries.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text(m.label) },
                                                trailingIcon = {
                                                    if (mode3d == m) Icon(Icons.Default.Check, null)
                                                },
                                                onClick = {
                                                    mode3d = m
                                                    optionsMenuExpanded = false
                                                }
                                            )
                                        }
                                    }

                                    "SPEED" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)
                                        speeds.forEach { speed ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(if (speed == 1f) stringResource(R.string.media_speed_normal) else "${speed}×")
                                                },
                                                trailingIcon = {
                                                    if (playbackSpeed == speed) Icon(Icons.Default.Check, null)
                                                },
                                                onClick = {
                                                    playbackSpeed = speed
                                                    optionsMenuExpanded = false
                                                }
                                            )
                                        }
                                    }

                                    "AUDIO" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        if (audioTracks.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.media_no_audio_tracks)) },
                                                enabled = false,
                                                onClick = {}
                                            )
                                        } else {
                                            audioTracks.forEachIndexed { i, track ->
                                                DropdownMenuItem(
                                                    text = { Text(track.label) },
                                                    trailingIcon = {
                                                        if (track.isSelected) Icon(Icons.Default.Check, null)
                                                    },
                                                    onClick = {
                                                        val groups = exoPlayer.currentTracks.groups
                                                        val audioGroups = groups.filter {
                                                            it.type == C.TRACK_TYPE_AUDIO && it.isSupported
                                                        }
                                                        if (i < audioGroups.size) {
                                                            val override = TrackSelectionOverride(
                                                                audioGroups[i].mediaTrackGroup, 0
                                                            )
                                                            exoPlayer.trackSelectionParameters =
                                                                exoPlayer.trackSelectionParameters
                                                                    .buildUpon()
                                                                    .setOverrideForType(override)
                                                                    .build()
                                                        }
                                                        optionsMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    "SUBTITLES" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        val subtitlesOff = subtitleTracks.none { it.isSelected }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.off)) },
                                            trailingIcon = {
                                                if (subtitlesOff) Icon(Icons.Default.Check, null)
                                            },
                                            onClick = {
                                                exoPlayer.trackSelectionParameters =
                                                    exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                        .setPreferredTextLanguage(null)
                                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                        .build()
                                                currentCues = null
                                                optionsMenuExpanded = false
                                            }
                                        )

                                        subtitleTracks.forEachIndexed { i, track ->
                                            DropdownMenuItem(
                                                text = { Text(track.label) },
                                                trailingIcon = {
                                                    if (track.isSelected) Icon(Icons.Default.Check, null)
                                                },
                                                onClick = {
                                                    val groups = exoPlayer.currentTracks.groups
                                                    val textGroups = groups.filter {
                                                        it.type == C.TRACK_TYPE_TEXT && it.isSupported
                                                    }
                                                    if (i < textGroups.size) {
                                                        val override = TrackSelectionOverride(
                                                            textGroups[i].mediaTrackGroup, 0
                                                        )
                                                        exoPlayer.trackSelectionParameters =
                                                            exoPlayer.trackSelectionParameters
                                                                .buildUpon()
                                                                .setOverrideForType(override)
                                                                .build()
                                                    }
                                                    optionsMenuExpanded = false
                                                }
                                            )
                                        }
                                    }

                                    "ASPECT" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        ResizeMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(mode.labelRes)) },
                                                trailingIcon = {
                                                    if (resizeMode == mode) Icon(Icons.Default.Check, null)
                                                },
                                                onClick = {
                                                    resizeMode = mode
                                                    optionsMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(onClick = { onToggleFullscreen() }) {
                            Icon(Icons.Default.Fullscreen, stringResource(R.string.menu_fullscreen),
                                tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatTime(ms: Long): String {
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

fun Modifier.videoGestures(
    isUiVisible: Boolean,
    isPlaying: Boolean,
    exoPlayer: ExoPlayer,
    onUiVisibilityChange: (Boolean) -> Unit,
    onHideTimerTrigger: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowPauseIndicator: () -> Unit,
    onShowPlayIndicator: () -> Unit,
    onShowSkipBackIndicator: () -> Unit,
    onShowSkipForwardIndicator: () -> Unit,
    onShowUi: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()

    var lastClickTime by remember { mutableLongStateOf(0L) }
    var pendingClickJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapX by remember { mutableFloatStateOf(0f) }

    val currentIsUiVisible by rememberUpdatedState(isUiVisible)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnUiVisibilityChange by rememberUpdatedState(onUiVisibilityChange)
    val currentOnHideTimerTrigger by rememberUpdatedState(onHideTimerTrigger)
    val currentOnToggleFullscreen by rememberUpdatedState(onToggleFullscreen)

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val firstChange = event.changes.firstOrNull() ?: continue
                    val isTouch = event.changes.any { it.type == PointerType.Touch }
                    val isStylus = event.changes.any { it.type == PointerType.Stylus }

                    if ((isTouch || isStylus) && event.type == PointerEventType.Press) {
                        val currentTime = System.currentTimeMillis()
                        val tapX = firstChange.position.x
                        val width = size.width.toFloat()

                        if (currentTime - lastTapTime < 300) {
                            pendingClickJob?.cancel()
                            pendingClickJob = null
                            when {
                                tapX < width * 0.3f -> {
                                    val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                    exoPlayer.seekTo(newPos)
                                    onShowSkipBackIndicator()
                                    onShowUi()
                                }
                                tapX > width * 0.7f -> {
                                    val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(newPos)
                                    onShowSkipForwardIndicator()
                                    onShowUi()
                                }
                                else -> {
                                    onShowUi()
                                    if (currentIsPlaying) { exoPlayer.pause(); onShowPauseIndicator() }
                                    else { exoPlayer.play(); onShowPlayIndicator() }
                                }
                            }
                        } else {
                            pendingClickJob?.cancel()
                            pendingClickJob = scope.launch {
                                delay(300)
                                if (currentIsUiVisible) currentOnUiVisibilityChange(false)
                                else { currentOnUiVisibilityChange(true); currentOnHideTimerTrigger() }
                            }
                        }
                        lastTapTime = currentTime
                        lastTapX = tapX
                    }
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val firstChange = event.changes.firstOrNull() ?: continue
                    val isMouse = event.changes.any { it.type == PointerType.Mouse }
                    val isPrimary = event.buttons.isPrimaryPressed

                    if (firstChange.type == PointerType.Mouse && event.type == PointerEventType.Move) {
                        currentOnUiVisibilityChange(true)
                        currentOnHideTimerTrigger()
                    }

                    if (isMouse && event.type == PointerEventType.Press && isPrimary) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 300) {
                            pendingClickJob?.cancel()
                            pendingClickJob = null
                            currentOnToggleFullscreen()
                        } else {
                            pendingClickJob?.cancel()
                            pendingClickJob = scope.launch {
                                delay(300)
                                currentOnUiVisibilityChange(true)
                                currentOnHideTimerTrigger()
                                if (currentIsPlaying) { exoPlayer.pause(); onShowPauseIndicator() }
                                else { exoPlayer.play(); onShowPlayIndicator() }
                            }
                        }
                        lastClickTime = currentTime
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
}

fun Modifier.videoKeyboardControls(
    exoPlayer: ExoPlayer,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowSkipBackIndicator: () -> Unit,
    onShowSkipForwardIndicator: () -> Unit,
    onShowPauseIndicator: () -> Unit,
    onShowPlayIndicator: () -> Unit,
    onShowUi: () -> Unit,
    activity: Activity?
): Modifier = this.onKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (keyEvent.key) {
        Key.Spacebar, Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> {
            onShowUi()
            if (exoPlayer.isPlaying) { exoPlayer.pause(); onShowPauseIndicator() }
            else { exoPlayer.play(); onShowPlayIndicator() }
            true
        }
        Key.DirectionRight, Key.MediaFastForward -> {
            onShowUi()
            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
            onShowSkipForwardIndicator()
            true
        }
        Key.DirectionLeft, Key.MediaRewind -> {
            onShowUi()
            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
            onShowSkipBackIndicator()
            true
        }
        Key.DirectionUp -> {
            onVolumeChange((volume + 0.1f).coerceAtMost(1f)); true
        }
        Key.DirectionDown -> {
            onVolumeChange((volume - 0.1f).coerceAtLeast(0f)); true
        }
        Key.F -> { onToggleFullscreen(); true }
        Key.M -> { onVolumeChange(if (volume > 0f) 0f else 1f); true }
        Key.W -> {
            if (keyEvent.isCtrlPressed) {
                activity?.finish()
                true
            } else false
        }
        else -> false
    }
}

fun Modifier.nonFocusable() = this.focusProperties { canFocus = false }