package com.troikoss.continuum_explorer.ui.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.troikoss.continuum_explorer.utils.FileExplorerState
import kotlin.math.roundToInt

/**
 * Redesigned 3-Mode Studio Music Player Overlay across Explorer:
 * 1. Persistent Bottom Mini-Player Bar (sleek Material 3 tonal dock with seek bar & mode switches)
 * 2. Redesigned Draggable Pop-Up Window Mode (Floating PiP window with interactive seek, +/-10s, speed & EQ pill)
 * 3. Fullscreen Studio Music Player (Responsive mobile & tablet/DeX split view, Rotating Vinyl mode, EQ Presets, Sleep Timer & Searchable Queue)
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
    var pipOffsetY by remember { mutableFloatStateOf(120f) }
    var isPipExpanded by remember { mutableStateOf(true) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    val sleepSec = AudioPlayerManager.sleepTimerRemainingSec
    val sleepTracks = AudioPlayerManager.sleepTrackCountRemaining
    val sleepStatusBadgeText = remember(sleepSec, sleepTracks) {
        when {
            sleepSec > 0 -> {
                val m = sleepSec / 60
                val s = sleepSec % 60
                String.format(java.util.Locale.US, "%02d:%02d", m, s)
            }
            sleepTracks > 0 -> "$sleepTracks trk"
            else -> ""
        }
    }

    if (showSleepDialog) {
        var customMinutesInput by remember { mutableStateOf("") }
        var customTracksInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF1DB954))
                    Text("Sleep Timer & Queue Stop", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (sleepSec > 0 || sleepTracks > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1DB954).copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (sleepSec > 0) {
                                        "Stopping in ${sleepSec / 60}m ${sleepSec % 60}s"
                                    } else {
                                        "Stopping after $sleepTracks track(s)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1DB954)
                                )
                                TextButton(
                                    onClick = { AudioPlayerManager.cancelAllSleepTimers() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Turn Off", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // 1. Stop After Custom Time
                    Text(
                        text = "Stop Playback After Time",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 30, 45, 60, 90).forEach { mins ->
                            FilterChip(
                                selected = sleepSec in ((mins - 1) * 60 + 1)..(mins * 60),
                                onClick = {
                                    AudioPlayerManager.setSleepTimerMinutes(mins)
                                    showSleepDialog = false
                                },
                                label = { Text("${mins}m", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutesInput,
                            onValueChange = { customMinutesInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Custom Minutes") },
                            placeholder = { Text("e.g. 25") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val mins = customMinutesInput.toIntOrNull() ?: 0
                                if (mins > 0) {
                                    AudioPlayerManager.setSleepTimerMinutes(mins)
                                    showSleepDialog = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Set Time")
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    // 2. Stop After N Queue Tracks
                    Text(
                        text = "Stop After N Queue Tracks",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 3, 5, 10, 15).forEach { nTracks ->
                            FilterChip(
                                selected = sleepTracks == nTracks,
                                onClick = {
                                    AudioPlayerManager.setSleepTrackCount(nTracks)
                                    showSleepDialog = false
                                },
                                label = {
                                    Text(
                                        if (nTracks == 1) "1 Track" else "$nTracks Tracks",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customTracksInput,
                            onValueChange = { customTracksInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Custom Queue Tracks (N)") },
                            placeholder = { Text("e.g. 4") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val count = customTracksInput.toIntOrNull() ?: 0
                                if (count > 0) {
                                    AudioPlayerManager.setSleepTrackCount(count)
                                    showSleepDialog = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Set Tracks")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                if (sleepSec > 0 || sleepTracks > 0) {
                    TextButton(
                        onClick = {
                            AudioPlayerManager.cancelAllSleepTimers()
                            showSleepDialog = false
                        }
                    ) {
                        Text("Disable Timer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // =====================================================================
        // 1. PERSISTENT BOTTOM MINI-PLAYER BAR
        // =====================================================================
        AnimatedVisibility(
            visible = mode == AudioPlayerDisplayMode.BOTTOM_BAR,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(max = 820.dp)
                .fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                tonalElevation = 10.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_bottom_bar")
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
                        AlbumArtBox(
                            metadata = metadata,
                            isPlaying = isPlaying,
                            isVinyl = false,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = metadata.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1DB954).copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = metadata.formatBadge.substringBefore(" "),
                                        color = Color(0xFF1DB954),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${metadata.artist} • ${formatAudioTime(posMs)} / ${formatAudioTime(durMs)}",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { AudioPlayerManager.playPrevious(context) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White
                            )
                        }

                        FilledIconButton(
                            onClick = { AudioPlayerManager.togglePlayPause() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF1DB954),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("music_bottom_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }

                        IconButton(
                            onClick = { AudioPlayerManager.playNext(context) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White
                            )
                        }

                        // Switch to Pop-Up Window Mode
                        IconButton(
                            onClick = {
                                AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FLOATING_PIP
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("music_switch_popup")
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                contentDescription = "Pop-Up Window Mode",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Expand to Fullscreen Studio
                        IconButton(
                            onClick = {
                                AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("music_switch_fullscreen")
                        ) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = "Fullscreen Player",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close Player
                        IconButton(
                            onClick = { AudioPlayerManager.stopAndClose() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Audio Player",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF1DB954),
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                }
            }
        }

        // =====================================================================
        // 2. REDESIGNED POP-UP WINDOW MODE (DRAGGABLE FLOATING PIP WINDOW)
        // =====================================================================
        if (mode == AudioPlayerDisplayMode.FLOATING_PIP) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF141418).copy(alpha = 0.97f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                tonalElevation = 12.dp,
                shadowElevation = 22.dp,
                modifier = Modifier
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .width(328.dp)
                    .testTag("music_popup_window")
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX = (pipOffsetX + dragAmount.x).coerceAtLeast(8f)
                            pipOffsetY = (pipOffsetY + dragAmount.y).coerceAtLeast(8f)
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Pop-up Window Header Bar (Drag Handle + Window Mode Controls)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        metadata.dominantColor.copy(alpha = 0.55f),
                                        Color(0xFF1E1E24)
                                    )
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = "Drag Window",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "MINI PLAYER",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Sleep Timer / Track Stop Button in Pop-Up Header
                            IconButton(
                                onClick = { showSleepDialog = true },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer & Track Limit",
                                    tint = if (sleepSec > 0 || sleepTracks > 0) Color(0xFF1DB954) else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            // Collapse/Expand Pop-up Details
                            IconButton(
                                onClick = { isPipExpanded = !isPipExpanded },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPipExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = "Toggle Compact Pop-Up",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Dock to Bottom Bar
                            IconButton(
                                onClick = {
                                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.BOTTOM_BAR
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.Dock,
                                    contentDescription = "Dock to Bottom Bar",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            // Expand to Fullscreen
                            IconButton(
                                onClick = {
                                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.OpenInFull,
                                    contentDescription = "Expand to Fullscreen",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            // Close
                            IconButton(
                                onClick = { AudioPlayerManager.stopAndClose() },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Player",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        // Artwork & Track Details
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AlbumArtBox(
                                metadata = metadata,
                                isPlaying = isPlaying,
                                isVinyl = AudioPlayerManager.isVinylArtworkStyle,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(if (AudioPlayerManager.isVinylArtworkStyle) CircleShape else RoundedCornerShape(14.dp))
                                    .clickable {
                                        AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY
                                    }
                            )
                            Spacer(Modifier.width(12.dp))
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
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${metadata.artist} • ${metadata.album}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1DB954).copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = metadata.formatBadge,
                                            color = Color(0xFF1DB954),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.clickable { AudioPlayerManager.cyclePlaybackSpeed() }
                                    ) {
                                        Text(
                                            text = "${AudioPlayerManager.playbackSpeed}x",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (sleepStatusBadgeText.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1DB954).copy(alpha = 0.22f),
                                            modifier = Modifier.clickable { showSleepDialog = true }
                                        ) {
                                            Text(
                                                text = "⏱ $sleepStatusBadgeText",
                                                color = Color(0xFF1DB954),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isPipExpanded) {
                            Spacer(Modifier.height(10.dp))

                            // Interactive Seek Slider inside Pop-Up Window
                            Slider(
                                value = posMs.toFloat().coerceIn(0f, durMs.toFloat()),
                                onValueChange = { AudioPlayerManager.seekTo(it.toLong()) },
                                valueRange = 0f..durMs.toFloat().coerceAtLeast(1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFF1DB954),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatAudioTime(posMs),
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 10.5.sp
                                )
                                Text(
                                    text = formatAudioTime(durMs),
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 10.5.sp
                                )
                            }

                            Spacer(Modifier.height(6.dp))
                        } else {
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
                            Spacer(Modifier.height(8.dp))
                        }

                        // Pop-Up Transport Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { AudioPlayerManager.toggleShuffle() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (AudioPlayerManager.isShuffleEnabled) Color(0xFF1DB954) else Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { AudioPlayerManager.playPrevious(context) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                            }

                            IconButton(
                                onClick = { AudioPlayerManager.seekRelative(-10_000L) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = { AudioPlayerManager.togglePlayPause() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF1DB954),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = { AudioPlayerManager.seekRelative(10_000L) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { AudioPlayerManager.playNext(context) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                            }

                            IconButton(
                                onClick = { AudioPlayerManager.cycleRepeatMode() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (AudioPlayerManager.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (AudioPlayerManager.repeatMode != Player.REPEAT_MODE_OFF) Color(0xFF1DB954) else Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3. REDESIGNED FULLSCREEN STUDIO MUSIC PLAYER
        // =====================================================================
        AnimatedVisibility(
            visible = mode == AudioPlayerDisplayMode.FULLSCREEN_SPOTIFY,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val bgGradient = Brush.verticalGradient(
                colors = listOf(
                    metadata.dominantColor,
                    Color(0xFF121216),
                    Color(0xFF09090B)
                )
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgGradient)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("music_fullscreen_player")
            ) {
                val isWide = maxWidth >= 680.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isWide) 28.dp else 20.dp, vertical = 12.dp)
                ) {
                    // Unified Top Bar: Minimize to Bottom Bar, Pop-Up Window, Album Header, Vinyl Toggle, Queue & Close
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
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FLOATING_PIP
                                }
                            ) {
                                Icon(
                                    Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Switch to Pop-Up Window",
                                    tint = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "STUDIO AUDIO PLAYER",
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
                            // Sleep Timer / Queue Limit Button in Top Bar
                            IconButton(onClick = { showSleepDialog = true }) {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer & Track Limit",
                                    tint = if (sleepSec > 0 || sleepTracks > 0) Color(0xFF1DB954) else Color.White.copy(alpha = 0.8f)
                                )
                            }
                            // Toggle Rotating Vinyl vs Square Artwork
                            IconButton(
                                onClick = {
                                    AudioPlayerManager.isVinylArtworkStyle = !AudioPlayerManager.isVinylArtworkStyle
                                }
                            ) {
                                Icon(
                                    Icons.Default.Album,
                                    contentDescription = "Toggle Vinyl Disc Mode",
                                    tint = if (AudioPlayerManager.isVinylArtworkStyle) Color(0xFF1DB954) else Color.White.copy(alpha = 0.8f)
                                )
                            }
                            if (!isWide) {
                                IconButton(onClick = { showQueueSheet = !showQueueSheet }) {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        contentDescription = "Folder Playlist Queue",
                                        tint = if (showQueueSheet) Color(0xFF1DB954) else Color.White
                                    )
                                }
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

                    Spacer(Modifier.height(12.dp))

                    if (isWide) {
                        // Tablet / DeX / Desktop 2-Column Split View
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1.1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AlbumArtBox(
                                    metadata = metadata,
                                    isPlaying = isPlaying,
                                    isVinyl = AudioPlayerManager.isVinylArtworkStyle,
                                    modifier = Modifier
                                        .sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
                                        .aspectRatio(1f)
                                        .shadow(
                                            24.dp,
                                            if (AudioPlayerManager.isVinylArtworkStyle) CircleShape else RoundedCornerShape(24.dp)
                                        )
                                        .clip(if (AudioPlayerManager.isVinylArtworkStyle) CircleShape else RoundedCornerShape(24.dp))
                                )
                                Spacer(Modifier.height(18.dp))
                                SpotifyControlsBlock(
                                    metadata = metadata,
                                    posMs = posMs,
                                    durMs = durMs,
                                    isPlaying = isPlaying,
                                    onOpenSleepDialog = { showSleepDialog = true },
                                    onToggleQueue = { showQueueSheet = !showQueueSheet }
                                )
                            }

                            // Persistent Searchable Folder Playlist Queue on Wide Screens
                            Surface(
                                color = Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(20.dp),
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
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(20.dp),
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
                                    isPlaying = isPlaying,
                                    isVinyl = AudioPlayerManager.isVinylArtworkStyle,
                                    modifier = Modifier
                                        .fillMaxWidth(0.76f)
                                        .aspectRatio(1f)
                                        .shadow(
                                            24.dp,
                                            if (AudioPlayerManager.isVinylArtworkStyle) CircleShape else RoundedCornerShape(24.dp)
                                        )
                                        .clip(if (AudioPlayerManager.isVinylArtworkStyle) CircleShape else RoundedCornerShape(24.dp))
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        SpotifyControlsBlock(
                            metadata = metadata,
                            posMs = posMs,
                            durMs = durMs,
                            isPlaying = isPlaying,
                            onOpenSleepDialog = { showSleepDialog = true },
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
    onOpenSleepDialog: () -> Unit,
    onToggleQueue: () -> Unit
) {
    val context = LocalContext.current
    val isShuffle = AudioPlayerManager.isShuffleEnabled
    val repeatMode = AudioPlayerManager.repeatMode
    val speed = AudioPlayerManager.playbackSpeed
    val eqPreset = AudioPlayerManager.eqPreset
    val sleepSec = AudioPlayerManager.sleepTimerRemainingSec
    val sleepTracks = AudioPlayerManager.sleepTrackCountRemaining

    Column(
        modifier = Modifier
            .widthIn(max = 580.dp)
            .fillMaxWidth()
    ) {
        // Track Title, Artist & Format/Speed Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${metadata.artist} • ${metadata.album}",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1DB954).copy(alpha = 0.16f)
                ) {
                    Text(
                        text = metadata.formatBadge,
                        color = Color(0xFF1DB954),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                OutlinedButton(
                    onClick = { AudioPlayerManager.cyclePlaybackSpeed() },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("${speed}x", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Audio Enhancer / Equalizer Preset Row + Sleep Timer & N-Track Stop Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color(0xFF1DB954),
                modifier = Modifier.size(16.dp)
            )
            AudioEqPreset.entries.forEach { preset ->
                val selected = eqPreset == preset
                FilterChip(
                    selected = selected,
                    onClick = { AudioPlayerManager.selectEqPreset(preset) },
                    label = { Text(preset.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1DB954).copy(alpha = 0.24f),
                        selectedLabelColor = Color(0xFF1DB954),
                        containerColor = Color.White.copy(alpha = 0.08f),
                        labelColor = Color.White.copy(alpha = 0.8f)
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp)
                )
            }

            // Sleep Timer & Queue Track Limit Pill
            FilterChip(
                selected = sleepSec > 0 || sleepTracks > 0,
                onClick = onOpenSleepDialog,
                leadingIcon = {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = {
                    Text(
                        text = when {
                            sleepSec > 0 -> "Sleep ${sleepSec / 60}m ${sleepSec % 60}s"
                            sleepTracks > 0 -> "Stop in $sleepTracks Track(s)"
                            else -> "Sleep / Queue Limit"
                        },
                        fontSize = 11.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF1DB954).copy(alpha = 0.24f),
                    selectedLabelColor = Color(0xFF1DB954),
                    containerColor = Color.White.copy(alpha = 0.08f),
                    labelColor = Color.White.copy(alpha = 0.8f)
                ),
                border = null,
                modifier = Modifier.height(28.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

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

        val remainingMs = (durMs - posMs).coerceAtLeast(0L)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatAudioTime(posMs),
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp
            )
            Text(
                text = "-${formatAudioTime(remainingMs)} / ${formatAudioTime(durMs)}",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // Transport Controls Row (Shuffle, Prev, -10s, Play/Pause, +10s, Next, Repeat)
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
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { AudioPlayerManager.seekRelative(-10_000L) },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    Icons.Default.Replay10,
                    contentDescription = "Rewind 10s",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }

            FilledIconButton(
                onClick = { AudioPlayerManager.togglePlayPause() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF1DB954),
                    contentColor = Color.Black
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(
                onClick = { AudioPlayerManager.seekRelative(10_000L) },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    Icons.Default.Forward10,
                    contentDescription = "Forward 10s",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = { AudioPlayerManager.playNext(context) },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
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

        Spacer(Modifier.height(6.dp))

        // Bottom Quick Switch Bar (Bottom Dock | Pop-Up Window | Queue count)
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
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Bottom Bar", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
            }

            TextButton(
                onClick = {
                    AudioPlayerManager.displayMode = AudioPlayerDisplayMode.FLOATING_PIP
                }
            ) {
                Icon(
                    Icons.Default.PictureInPictureAlt,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Pop-Up Window", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
            }

            TextButton(onClick = onToggleQueue) {
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Queue (${AudioPlayerManager.playlist.size})",
                    color = Color.White.copy(alpha = 0.78f),
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
    var filterQuery by remember { mutableStateOf("") }

    val filteredIndices = remember(playlist.size, filterQuery) {
        if (filterQuery.isBlank()) {
            playlist.indices.toList()
        } else {
            playlist.indices.filter { idx ->
                playlist[idx].name.contains(filterQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Folder Queue (${playlist.size} tracks)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            placeholder = { Text("Filter queue tracks...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (filterQuery.isNotEmpty()) {
                    IconButton(onClick = { filterQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF1DB954),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("music_queue_search")
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(filteredIndices) { _, index ->
                val item = playlist[index]
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
    isPlaying: Boolean,
    isVinyl: Boolean,
    modifier: Modifier = Modifier
) {
    val bmp = metadata.albumArt
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_angle"
    )

    Box(
        modifier = modifier
            .then(if (isVinyl && isPlaying) Modifier.rotate(rotation) else Modifier)
            .background(
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

        // Vinyl center spindle hole when Vinyl mode is active
        if (isVinyl) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF121216))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
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
