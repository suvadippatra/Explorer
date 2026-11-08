package com.troikoss.continuum_explorer.ui.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.troikoss.continuum_explorer.utils.FileExplorerState
import kotlin.math.roundToInt

/**
 * Renders the 3-Mode Music Player Overlay across Explorer:
 * 1. Persistent Bottom Mini-Player Bar (default when tapping an audio file)
 * 2. Draggable Floating PiP Mini-Card over files
 * 3. Spotify-Style Fullscreen Music Player with dynamic artwork gradient & folder queue
 */
@Composable
fun SpotifyAudioPlayerOverlay(
    appState: FileExplorerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mode = AudioPlayerManager.displayMode
    val track = AudioPlayerManager.currentTrack
    if (mode == AudioPlayerDisplayMode.HIDDEN || track == null) return

    val metadata = AudioPlayerManager.currentMetadata
    val isPlaying = AudioPlayerManager.isPlaying
    val posMs = AudioPlayerManager.currentPositionMs
    val durMs = AudioPlayerManager.durationMs.coerceAtLeast(1L)
    val progress = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)

    var pipOffsetX by remember { mutableFloatStateOf(24f) }
    var pipOffsetY by remember { mutableFloatStateOf(160f) }
    var showQueueSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Persistent Bottom Mini-Player Bar
        AnimatedVisibility(
            visible = mode == AudioPlayerDisplayMode.BOTTOM_BAR,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF18181B),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork thumbnail
                        AlbumArtBox(
                            metadata = metadata,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                        Spacer(Modifier.width(12.dp))

                        // Track Title & Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = metadata.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${metadata.artist} • ${formatAudioTime(posMs)} / ${formatAudioTime(durMs)}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Previous
                        IconButton(
                            onClick = { AudioPlayerManager.playPrevious(context) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White
                            )
                        }

                        // Play / Pause
                        FilledIconButton(
                            onClick = { AudioPlayerManager.togglePlayPause() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF1DB954),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }

                        // Next
                        IconButton(
                            onClick = { AudioPlayerManager.playNext(context) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White
                            )
                        }

                        // Minimize to Floating PiP Mini-Card
                        IconButton(
                            onClick = {
                                AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FLOATING_PIP
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                contentDescription = "Minimize to Floating PiP Card",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Expand to Spotify Fullscreen
                        IconButton(
                            onClick = {
                                AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = "Expand to Fullscreen Player",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Close Player
                        IconButton(
                            onClick = { AudioPlayerManager.stopAndClose() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Stop and Close Audio Player",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF1DB954),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // 2. Draggable Floating PiP Mini-Card over files
        if (mode == AudioPlayerDisplayMode.FLOATING_PIP) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF18181B).copy(alpha = 0.96f),
                tonalElevation = 10.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .width(276.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX = (pipOffsetX + dragAmount.x).coerceAtLeast(8f)
                            pipOffsetY = (pipOffsetY + dragAmount.y).coerceAtLeast(8f)
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AlbumArtBox(
                            metadata = metadata,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                                }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                                }
                        ) {
                            Text(
                                text = metadata.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = metadata.artist,
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { AudioPlayerManager.stopAndClose() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Player",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF1DB954),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Restore to Bottom Mini-Player Bar
                        TextButton(
                            onClick = {
                                AudioPlayerManager.displayMode = AudioPlayerDisplayMode.BOTTOM_BAR
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Dock,
                                contentDescription = "Dock to Bottom Bar",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Bar", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { AudioPlayerManager.playPrevious(context) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                            }
                            FilledIconButton(
                                onClick = { AudioPlayerManager.togglePlayPause() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF1DB954),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { AudioPlayerManager.playNext(context) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                            }
                        }

                        // Expand to Fullscreen Spotify Player
                        IconButton(
                            onClick = {
                                AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = "Fullscreen Spotify Player",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Spotify-Style Fullscreen Music Player
        AnimatedVisibility(
            visible = mode == AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val bgGradient = Brush.verticalGradient(
                colors = listOf(
                    metadata.dominantColor,
                    Color(0xFF121214),
                    Color(0xFF09090B)
                )
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgGradient)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val isWide = maxWidth > 720.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    // Top Bar: Minimize to Bottom Bar, Minimize to Floating PiP, Folder Name, Queue Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.BOTTOM_BAR
                                }
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimize to Bottom Bar",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FLOATING_PIP
                                }
                            ) {
                                Icon(
                                    Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Minimize to Floating PiP",
                                    tint = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PLAYING FROM FOLDER",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = metadata.album,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showQueueSheet = !showQueueSheet }) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = "Folder Playlist Queue",
                                    tint = if (showQueueSheet) Color(0xFF1DB954) else Color.White
                                )
                            }
                            IconButton(onClick = { AudioPlayerManager.stopAndClose() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Player",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (isWide) {
                        // Tablet / DeX Side-by-Side Spotify Layout (Artwork & Controls on Left, Queue on Right)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1.1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AlbumArtBox(
                                    metadata = metadata,
                                    modifier = Modifier
                                        .sizeIn(maxWidth = 320.dp, maxHeight = 320.dp)
                                        .aspectRatio(1f)
                                        .shadow(24.dp, RoundedCornerShape(20.dp))
                                        .clip(RoundedCornerShape(20.dp))
                                )
                                Spacer(Modifier.height(24.dp))
                                SpotifyControlsBlock(
                                    metadata = metadata,
                                    posMs = posMs,
                                    durMs = durMs,
                                    isPlaying = isPlaying,
                                    onToggleQueue = { showQueueSheet = !showQueueSheet }
                                )
                            }

                            // Persistent Folder Playlist Queue on Wide Screens
                            Surface(
                                color = Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight()
                            ) {
                                PlaylistQueueContent(
                                    onSelectTrack = { idx ->
                                        AudioPlayerManager.playTrackAtIndex(context, idx)
                                    }
                                )
                            }
                        }
                    } else {
                        // Mobile Portrait Layout
                        if (showQueueSheet) {
                            Surface(
                                color = Color.White.copy(alpha = 0.07f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                PlaylistQueueContent(
                                    onSelectTrack = { idx ->
                                        AudioPlayerManager.playTrackAtIndex(context, idx)
                                    }
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                AlbumArtBox(
                                    metadata = metadata,
                                    modifier = Modifier
                                        .fillMaxWidth(0.84f)
                                        .aspectRatio(1f)
                                        .shadow(24.dp, RoundedCornerShape(20.dp))
                                        .clip(RoundedCornerShape(20.dp))
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        SpotifyControlsBlock(
                            metadata = metadata,
                            posMs = posMs,
                            durMs = durMs,
                            isPlaying = isPlaying,
                            onToggleQueue = { showQueueSheet = !showQueueSheet }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyControlsBlock(
    metadata: AudioTrackMetadata,
    posMs: Long,
    durMs: Long,
    isPlaying: Boolean,
    onToggleQueue: () -> Unit
) {
    val context = LocalContext.current
    val isShuffle = AudioPlayerManager.isShuffleEnabled
    val repeatMode = AudioPlayerManager.repeatMode
    val speed = AudioPlayerManager.playbackSpeed

    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${metadata.artist} • ${metadata.album}",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Playback Speed Pill
            OutlinedButton(
                onClick = { AudioPlayerManager.cyclePlaybackSpeed() },
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("${speed}x", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Seek Slider
        Slider(
            value = posMs.toFloat().coerceIn(0f, durMs.toFloat()),
            onValueChange = { AudioPlayerManager.seekTo(it.toLong()) },
            valueRange = 0f..durMs.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF1DB954),
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatAudioTime(posMs),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
            Text(
                text = formatAudioTime(durMs),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // Transport Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { AudioPlayerManager.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f)
                )
            }

            IconButton(
                onClick = { AudioPlayerManager.playPrevious(context) },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            FilledIconButton(
                onClick = { AudioPlayerManager.togglePlayPause() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF1DB954),
                    contentColor = Color.Black
                ),
                modifier = Modifier.size(68.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = { AudioPlayerManager.playNext(context) },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = { AudioPlayerManager.cycleRepeatMode() }) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat Mode",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Bottom Quick Switch Bar (Dock to Bottom Bar | Floating PiP | Queue count)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.BOTTOM_BAR
                }
            ) {
                Icon(
                    Icons.Default.Dock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Bottom Mini-Bar", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }

            TextButton(
                onClick = {
                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FLOATING_PIP
                }
            ) {
                Icon(
                    Icons.Default.PictureInPictureAlt,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Floating PiP", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }

            TextButton(onClick = onToggleQueue) {
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Queue (${AudioPlayerManager.playlist.size})",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PlaylistQueueContent(
    onSelectTrack: (Int) -> Unit
) {
    val playlist = AudioPlayerManager.playlist
    val currentIdx = AudioPlayerManager.currentTrackIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Folder Audio Queue (${playlist.size} tracks)",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(playlist) { index, item ->
                val isCurrent = index == currentIdx
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) Color(0xFF1DB954).copy(alpha = 0.22f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTrack(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrent) Icons.Default.Equalizer else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isCurrent) Color(0xFF1DB954) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name.substringBeforeLast('.'),
                                color = if (isCurrent) Color(0xFF1DB954) else Color.White,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.name.substringAfterLast('.', "").uppercase(),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtBox(
    metadata: AudioTrackMetadata,
    modifier: Modifier = Modifier
) {
    val bmp = metadata.albumArt
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    metadata.dominantColor,
                    Color(0xFF27272A)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = metadata.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxSize(0.52f)
            )
        }
    }
}

private fun formatAudioTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
