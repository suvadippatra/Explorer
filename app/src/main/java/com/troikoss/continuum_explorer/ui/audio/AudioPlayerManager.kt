package com.troikoss.continuum_explorer.ui.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AudioPlayerDisplayMode {
    HIDDEN,
    BOTTOM_BAR,
    FLOATING_PIP,
    FULLSCREEN_SPOTIFY
}

data class AudioTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArt: Bitmap?,
    val dominantColor: Color,
    val formatBadge: String = "AUDIO"
)

/**
 * Singleton Audio Player Manager powering the 3-Mode Music Player:
 * 1. Persistent Bottom Mini-Player Bar
 * 2. Draggable Floating PiP Mini-Card over files
 * 3. Spotify-Style Fullscreen Music Player with folder playlist queue
 */
object AudioPlayerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null

    var displayMode by mutableStateOf(AudioPlayerDisplayMode.HIDDEN)
    var currentTrack by mutableStateOf<UniversalFile?>(null)
        private set
    var currentMetadata by mutableStateOf(
        AudioTrackMetadata(
            title = "No Track Selected",
            artist = "Unknown Artist",
            album = "Explorer Audio",
            albumArt = null,
            dominantColor = Color(0xFF1DB954),
            formatBadge = "AUDIO"
        )
    )
        private set

    val playlist = mutableStateListOf<UniversalFile>()
    var currentTrackIndex by mutableIntStateOf(-1)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var currentPositionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    var isShuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var playbackSpeed by mutableFloatStateOf(1.0f)
        private set

    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma", "amr")

    fun isAudioFile(file: UniversalFile): Boolean {
        if (file.isDirectory) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private fun ensurePlayer(context: Context): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing
        val created = ExoPlayer.Builder(context.applicationContext).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@AudioPlayerManager.isPlaying = playing
                    if (playing) startProgressPolling() else stopProgressPolling()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val d = duration
                    if (d > 0) durationMs = d
                    if (playbackState == Player.STATE_ENDED) {
                        handleTrackEnded(context.applicationContext)
                    }
                }
            })
        }
        exoPlayer = created
        return created
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                    if (player.duration > 0) {
                        durationMs = player.duration
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playTrack(
        context: Context,
        track: UniversalFile,
        folderCandidates: List<UniversalFile> = emptyList(),
        initialMode: AudioPlayerDisplayMode = AudioPlayerDisplayMode.BOTTOM_BAR
    ) {
        val audioSiblings = folderCandidates.filter { isAudioFile(it) }
        playlist.clear()
        if (audioSiblings.isNotEmpty() && audioSiblings.any { it.absolutePath == track.absolutePath }) {
            playlist.addAll(audioSiblings)
        } else {
            playlist.add(track)
        }
        val idx = playlist.indexOfFirst { it.absolutePath == track.absolutePath }.coerceAtLeast(0)
        currentTrackIndex = idx
        if (displayMode == AudioPlayerDisplayMode.HIDDEN) {
            displayMode = initialMode
        }
        loadAndStartTrack(context.applicationContext, playlist[idx])
    }

    fun playTrackAtIndex(context: Context, index: Int) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        loadAndStartTrack(context.applicationContext, playlist[index])
    }

    private fun loadAndStartTrack(context: Context, track: UniversalFile) {
        currentTrack = track
        currentPositionMs = 0L
        durationMs = 0L

        val player = ensurePlayer(context)
        val uri: Uri? = track.fileRef?.let { Uri.fromFile(it) } ?: track.documentFileRef?.uri
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.playbackParameters = PlaybackParameters(playbackSpeed)
            player.prepare()
            player.play()
        }

        // Extract ID3 metadata & embedded artwork asynchronously
        scope.launch {
            val meta = withContext(Dispatchers.IO) {
                extractMetadata(context, track)
            }
            currentMetadata = meta
        }
    }

    private fun extractMetadata(context: Context, track: UniversalFile): AudioTrackMetadata {
        val retriever = MediaMetadataRetriever()
        val fallbackTitle = track.name.substringBeforeLast('.')
        val extUpper = track.name.substringAfterLast('.', "AUDIO").uppercase()
        return try {
            val fileRef = track.fileRef
            val docUri = track.documentFileRef?.uri
            if (fileRef != null && fileRef.exists()) {
                retriever.setDataSource(fileRef.absolutePath)
            } else if (docUri != null) {
                retriever.setDataSource(context, docUri)
            }
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: "Local Audio"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?: track.fileRef?.parentFile?.name
                ?: "Explorer Music"

            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            val badge = if (bitrateBps != null && bitrateBps > 0) {
                "$extUpper • ${bitrateBps / 1000} kbps"
            } else {
                extUpper
            }

            val pictureBytes = retriever.embeddedPicture
            val bitmap = if (pictureBytes != null) {
                BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
            } else null

            val dominant = if (bitmap != null) {
                sampleDominantColor(bitmap)
            } else {
                Color(0xFF1E3A5F)
            }

            AudioTrackMetadata(
                title = title,
                artist = artist,
                album = album,
                albumArt = bitmap,
                dominantColor = dominant,
                formatBadge = badge
            )
        } catch (_: Exception) {
            AudioTrackMetadata(
                title = fallbackTitle,
                artist = "Local Audio",
                album = track.fileRef?.parentFile?.name ?: "Explorer Music",
                albumArt = null,
                dominantColor = Color(0xFF1E3A5F),
                formatBadge = extUpper
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun sampleDominantColor(bitmap: Bitmap): Color {
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
            var r = 0L
            var g = 0L
            var b = 0L
            var count = 0
            for (x in 0 until scaled.width) {
                for (y in 0 until scaled.height) {
                    val px = scaled.getPixel(x, y)
                    r += android.graphics.Color.red(px)
                    g += android.graphics.Color.green(px)
                    b += android.graphics.Color.blue(px)
                    count++
                }
            }
            if (count == 0) Color(0xFF1E3A5F)
            else Color(
                red = ((r / count).toInt().coerceIn(24, 180)) / 255f,
                green = ((g / count).toInt().coerceIn(24, 180)) / 255f,
                blue = ((b / count).toInt().coerceIn(24, 180)) / 255f
            )
        } catch (_: Exception) {
            Color(0xFF1E3A5F)
        }
    }

    private fun handleTrackEnded(context: Context) {
        when (repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
            Player.REPEAT_MODE_ALL -> {
                playNext(context)
            }
            else -> {
                if (playlist.size > 1 && currentTrackIndex < playlist.lastIndex) {
                    playNext(context)
                } else {
                    isPlaying = false
                }
            }
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
        currentPositionMs = target
        exoPlayer?.seekTo(target)
    }

    fun playNext(context: Context) {
        if (playlist.isEmpty()) return
        val nextIndex = if (isShuffleEnabled && playlist.size > 1) {
            playlist.indices.filter { it != currentTrackIndex }.random()
        } else {
            (currentTrackIndex + 1) % playlist.size
        }
        playTrackAtIndex(context, nextIndex)
    }

    fun playPrevious(context: Context) {
        if (playlist.isEmpty()) return
        if (currentPositionMs > 3500L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (currentTrackIndex - 1 >= 0) currentTrackIndex - 1 else playlist.lastIndex
        playTrackAtIndex(context, prevIndex)
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val nextIdx = (speeds.indexOfFirst { it == playbackSpeed } + 1) % speeds.size
        playbackSpeed = speeds[nextIdx]
        exoPlayer?.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    fun stopAndClose() {
        stopProgressPolling()
        exoPlayer?.stop()
        isPlaying = false
        displayMode = AudioPlayerDisplayMode.HIDDEN
        currentTrack = null
    }
}
