package com.troikoss.continuum_explorer.ui.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
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

enum class AudioEqPreset(val label: String) {
    FLAT("Flat"),
    BASS_BOOST("Bass Boost"),
    VOCAL("Vocal"),
    ACOUSTIC("Acoustic"),
    SPATIAL("Spatial Hi-Fi")
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
 * 2. Redesigned Draggable Pop-Up Window (Floating PiP)
 * 3. Fullscreen Studio Music Player with Vinyl Mode, Equalizer Presets, Sleep Timer & Folder Queue
 */
object AudioPlayerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

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
    var eqPreset by mutableStateOf(AudioEqPreset.FLAT)
        private set
    var sleepTimerRemainingSec by mutableIntStateOf(0)
        private set
    var sleepTrackCountRemaining by mutableIntStateOf(0)
        private set
    var isVinylArtworkStyle by mutableStateOf(false)

    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma", "amr")

    fun isAudioFile(file: UniversalFile): Boolean {
        if (file.isDirectory) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
                    if (playbackState == Player.STATE_READY) {
                        applyAudioEffects(audioSessionId, eqPreset)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        handleTrackEnded(context.applicationContext)
                    }
                }
            })
        }
        exoPlayer = created
        return created
    }

    private fun applyAudioEffects(audioSessionId: Int, preset: AudioEqPreset) {
        if (audioSessionId <= 0) return
        runCatching {
            if (equalizer == null) {
                equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            }
            if (bassBoost == null) {
                bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            }
            val eq = equalizer ?: return@runCatching
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLvl = range[0].toInt()
            val maxLvl = range[1].toInt()
            val mid = (minLvl + maxLvl) / 2
            val boost = (maxLvl * 0.65f).toInt().coerceIn(minLvl, maxLvl)
            val slightBoost = (maxLvl * 0.35f).toInt().coerceIn(minLvl, maxLvl)
            val slightCut = (minLvl * 0.25f).toInt().coerceIn(minLvl, maxLvl)

            for (b in 0 until bands) {
                val band = b.toShort()
                val target = when (preset) {
                    AudioEqPreset.FLAT -> mid
                    AudioEqPreset.BASS_BOOST -> if (b == 0) boost else if (b == 1) slightBoost else mid
                    AudioEqPreset.VOCAL -> if (b in 1..(bands - 2).coerceAtLeast(1)) boost else slightCut
                    AudioEqPreset.ACOUSTIC -> if (b == 0 || b == bands - 1) slightBoost else mid
                    AudioEqPreset.SPATIAL -> if (b == 0 || b == bands - 1) boost else slightCut
                }
                eq.setBandLevel(band, target.toShort())
            }
            bassBoost?.setStrength(
                when (preset) {
                    AudioEqPreset.BASS_BOOST -> 750.toShort()
                    AudioEqPreset.SPATIAL -> 450.toShort()
                    else -> 0.toShort()
                }
            )
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun selectEqPreset(preset: AudioEqPreset) {
        eqPreset = preset
        exoPlayer?.let { applyAudioEffects(it.audioSessionId, preset) }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            sleepTimerRemainingSec = 0
            return
        }
        sleepTrackCountRemaining = 0
        sleepTimerRemainingSec = minutes * 60
        sleepTimerJob = scope.launch {
            while (isActive && sleepTimerRemainingSec > 0) {
                delay(1000L)
                sleepTimerRemainingSec--
            }
            if (sleepTimerRemainingSec <= 0) {
                exoPlayer?.pause()
                isPlaying = false
            }
        }
    }

    fun setSleepTrackCount(tracks: Int) {
        val clean = tracks.coerceAtLeast(0)
        sleepTrackCountRemaining = clean
        if (clean > 0) {
            sleepTimerJob?.cancel()
            sleepTimerRemainingSec = 0
        }
    }

    fun cancelAllSleepTimers() {
        sleepTimerJob?.cancel()
        sleepTimerRemainingSec = 0
        sleepTrackCountRemaining = 0
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
        if (sleepTrackCountRemaining > 0) {
            sleepTrackCountRemaining--
            if (sleepTrackCountRemaining <= 0) {
                exoPlayer?.pause()
                isPlaying = false
                return
            }
        }
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

    fun seekRelative(deltaMs: Long) {
        val target = (currentPositionMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(1L))
        seekTo(target)
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

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer?.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val nextIdx = (speeds.indexOfFirst { it == playbackSpeed } + 1) % speeds.size
        setSpeed(speeds[nextIdx])
    }

    fun stopAndClose() {
        stopProgressPolling()
        sleepTimerJob?.cancel()
        sleepTimerRemainingSec = 0
        sleepTrackCountRemaining = 0
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        exoPlayer?.stop()
        isPlaying = false
        displayMode = AudioPlayerDisplayMode.HIDDEN
        currentTrack = null
    }
}
