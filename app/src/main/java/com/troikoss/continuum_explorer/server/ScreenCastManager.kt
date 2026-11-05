package com.troikoss.continuum_explorer.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * ScreenCastManager
 *
 * Captures screen frames using Android's MediaProjection API and VirtualDisplay with ImageReader.
 * Encodes captured frames into JPEG byte arrays and pumps them down WebSocket / streaming listeners.
 */
class ScreenCastManager(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    companion object {
        private const val TAG = "ScreenCastManager"
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
        private const val FRAME_RATE_MS = 100L // ~10-15 fps for smooth, low latency network stream
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isStreaming = false

    // Thread-safe cached latest frame
    @Volatile
    private var latestFrameBytes: ByteArray? = null

    // Callbacks for live consumers
    private val frameListeners = java.util.concurrent.CopyOnWriteArrayList<(ByteArray) -> Unit>()

    fun addFrameListener(listener: (ByteArray) -> Unit) {
        frameListeners.add(listener)
        // Send last cached frame immediately if available
        latestFrameBytes?.let { listener(it) }
    }

    fun removeFrameListener(listener: (ByteArray) -> Unit) {
        frameListeners.remove(listener)
    }

    fun getLatestFrame(): ByteArray? = latestFrameBytes

    fun startCapture() {
        if (isStreaming) return
        isStreaming = true

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val resSetting = com.troikoss.continuum_explorer.managers.SettingsManager.screenCastResolution.value
        val targetFps = com.troikoss.continuum_explorer.managers.SettingsManager.screenCastFps.value
        val rotationDegrees = com.troikoss.continuum_explorer.managers.SettingsManager.screenCastRotation.value
        val frameIntervalMs = (1000L / targetFps.coerceIn(5, 60)).coerceAtLeast(16L)

        var width = metrics.widthPixels
        var height = metrics.heightPixels
        val density = metrics.densityDpi

        val maxDim = when (resSetting) {
            "1080p" -> 1920
            "Native" -> 3840
            else -> 1280 // 720p
        }

        if ((width > maxDim || height > maxDim) && resSetting != "Native") {
            val aspect = width.toFloat() / height.toFloat()
            if (width > height) {
                width = maxDim
                height = (maxDim / aspect).toInt()
            } else {
                height = maxDim
                width = (maxDim * aspect).toInt()
            }
        }
        // Ensure even dimensions
        width = (width / 2) * 2
        height = (height / 2) * 2

        Log.d(TAG, "Initializing screen capture: ${width}x${height} at $targetFps FPS, $rotationDegrees° rotation")

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ContinuumScreenCast",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null
        )

        captureJob = scope.launch {
            var cachedBitmap: Bitmap? = null

            while (isActive && isStreaming) {
                val startTime = System.currentTimeMillis()
                val reader = imageReader ?: break

                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer: ByteBuffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val imgWidth = width + rowPadding / pixelStride
                        if (cachedBitmap == null || cachedBitmap.width != imgWidth || cachedBitmap.height != height) {
                            cachedBitmap?.recycle()
                            cachedBitmap = Bitmap.createBitmap(imgWidth, height, Bitmap.Config.ARGB_8888)
                        }

                        cachedBitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        // Crop if row padding was present
                        var cleanBitmap = if (rowPadding == 0) {
                            cachedBitmap
                        } else {
                            Bitmap.createBitmap(cachedBitmap, 0, 0, width, height)
                        }

                        // Apply screen rotation if requested
                        if (rotationDegrees > 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                            val rotated = Bitmap.createBitmap(cleanBitmap, 0, 0, cleanBitmap.width, cleanBitmap.height, matrix, true)
                            if (cleanBitmap != cachedBitmap) {
                                cleanBitmap.recycle()
                            }
                            cleanBitmap = rotated
                        }

                        // Compress to JPEG for WebSocket / browser transmission
                        val stream = ByteArrayOutputStream()
                        cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                        val jpegBytes = stream.toByteArray()

                        if (cleanBitmap != cachedBitmap) {
                            cleanBitmap.recycle()
                        }

                        latestFrameBytes = jpegBytes
                        for (listener in frameListeners) {
                            try {
                                listener(jpegBytes)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error in frame listener", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error acquiring frame", e)
                }

                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = (frameIntervalMs - elapsed).coerceAtLeast(5L)
                delay(sleepTime)
            }

            cachedBitmap?.recycle()
        }
    }

    fun stopCapture() {
        isStreaming = false
        captureJob?.cancel()
        captureJob = null
        frameListeners.clear()

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtual display", e)
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing image reader", e)
        }
    }
}
