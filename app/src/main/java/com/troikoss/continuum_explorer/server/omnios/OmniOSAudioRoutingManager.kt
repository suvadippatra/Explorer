package com.troikoss.continuum_explorer.server.omnios

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OmniOSAudioRoute(val id: String, val label: String) {
    RECEIVER("receiver", "Receiver"),
    HOST("host", "Host"),
    BOTH("both", "Both");

    companion object {
        fun fromId(id: String?): OmniOSAudioRoute = when (id?.lowercase()) {
            "host" -> HOST
            "both" -> BOTH
            else -> RECEIVER
        }
    }
}

data class OmniOSAudioState(
    val route: OmniOSAudioRoute = OmniOSAudioRoute.RECEIVER,
    val activeOutputLabel: String = "Device Speaker",
    val hostVolumePercent: Int = 75,
    val isPlaybackCaptureActive: Boolean = false
)

/**
 * Tri-route audio coordinator (`receiver` | `host` | `both`) for OmniOS Casting.
 * Synchronizes route preferences with [OmniOSAdminConfigStore] and inspects live Android
 * audio output devices, with optional Android 10+ [AudioPlaybackCaptureConfiguration] streaming.
 */
object OmniOSAudioRoutingManager {
    private const val TAG = "OmniOSAudioRouting"
    private const val SAMPLE_RATE = 44100
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_STEREO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    private var audioManager: AudioManager? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var localTrack: AudioTrack? = null

    @Volatile
    private var captureThread: Thread? = null

    @Volatile
    private var capturing: Boolean = false

    @Volatile
    var onCapturedAudioChunk: ((ByteArray) -> Unit)? = null

    private val _audioState = MutableStateFlow(OmniOSAudioState())
    val audioState: StateFlow<OmniOSAudioState> = _audioState.asStateFlow()

    fun init(context: Context) {
        audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val savedRoute = OmniOSAudioRoute.fromId(OmniOSAdminConfigStore.settings.value.audioRoute)
        _audioState.update { it.copy(route = savedRoute) }
        refreshDeviceAudioInfo()
    }

    fun setRoute(route: OmniOSAudioRoute) {
        OmniOSAdminConfigStore.setAudioRoute(route.id)
        _audioState.update { it.copy(route = route) }
        refreshDeviceAudioInfo()
    }

    fun syncRouteFromAdmin(routeId: String) {
        val parsed = OmniOSAudioRoute.fromId(routeId)
        _audioState.update { it.copy(route = parsed) }
    }

    fun refreshDeviceAudioInfo() {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPct = ((curVol * 100f) / maxVol).toInt().coerceIn(0, 100)

        val outputs = try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            emptyArray()
        }

        val label = when {
            outputs.any { it.type == AudioDeviceInfo.TYPE_HDMI || it.type == AudioDeviceInfo.TYPE_HDMI_ARC } -> "HDMI Output"
            outputs.any { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET } -> "USB Audio"
            outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET } -> "Bluetooth Audio"
            outputs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> "Wired Headphones"
            else -> "Device Speaker"
        }

        _audioState.update {
            it.copy(
                route = OmniOSAudioRoute.fromId(OmniOSAdminConfigStore.settings.value.audioRoute),
                activeOutputLabel = label,
                hostVolumePercent = volPct
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startPlaybackCapture(mediaProjection: MediaProjection): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "AudioPlaybackCapture requires Android 10+")
            return false
        }
        if (capturing) return true

        return try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(4096) * 2
            val inFormat = AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_IN)
                .build()

            val recorder = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setAudioFormat(inFormat)
                .setBufferSizeInBytes(minBuf)
                .build()

            val outTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            outTrack.play()
            recorder.startRecording()

            audioRecord = recorder
            localTrack = outTrack
            capturing = true
            _audioState.update { it.copy(isPlaybackCaptureActive = true) }

            captureThread = Thread {
                val buffer = ByteArray(2048)
                while (capturing) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val route = OmniOSAudioRoute.fromId(OmniOSAdminConfigStore.settings.value.audioRoute)
                        if (route == OmniOSAudioRoute.HOST || route == OmniOSAudioRoute.BOTH) {
                            outTrack.write(buffer, 0, read)
                        }
                        if (route == OmniOSAudioRoute.RECEIVER || route == OmniOSAudioRoute.BOTH) {
                            val packet = buffer.copyOf(read)
                            onCapturedAudioChunk?.invoke(packet)
                        }
                    }
                }
            }.apply {
                name = "OmniOS-AudioCapture"
                priority = Thread.MAX_PRIORITY
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "AudioPlaybackCapture failed: ${e.message}", e)
            stopPlaybackCapture()
            false
        }
    }

    fun stopPlaybackCapture() {
        capturing = false
        try {
            captureThread?.interrupt()
        } catch (_: Exception) {}
        captureThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            localTrack?.stop()
            localTrack?.release()
        } catch (_: Exception) {}
        localTrack = null

        _audioState.update { it.copy(isPlaybackCaptureActive = false) }
    }
}
