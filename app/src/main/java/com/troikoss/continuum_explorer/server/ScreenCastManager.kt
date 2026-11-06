package com.troikoss.continuum_explorer.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * ScreenCastManager
 *
 * Captures live device screen frames using Android's MediaProjection API and VirtualDisplay with ImageReader.
 * Compatible with Android 8 (API 26) through Android 15 (API 35):
 * - Registers MediaProjection.Callback on a dedicated background HandlerThread BEFORE calling
 *   createVirtualDisplay() (mandatory requirement on Android 14 / API 34+ and Android 15).
 * - Uses both ImageReader frame listener and periodic heartbeat encoding so static screens still
 *   immediately render for newly connected browser clients.
 */
class ScreenCastManager(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    companion object {
        private const val TAG = "ScreenCastManager"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by system")
            stopCapture()
        }
    }

    @Volatile
    private var isStreaming = false

    // Thread-safe cached latest JPEG frame
    @Volatile
    private var latestFrameBytes: ByteArray? = null

    // Callbacks for live WebSocket consumers
    private val frameListeners = java.util.concurrent.CopyOnWriteArrayList<(ByteArray) -> Unit>()

    fun isActive(): Boolean = isStreaming

    fun addFrameListener(listener: (ByteArray) -> Unit) {
        frameListeners.add(listener)
        latestFrameBytes?.let { listener(it) }
    }

    fun removeFrameListener(listener: (ByteArray) -> Unit) {
        frameListeners.remove(listener)
    }

    fun getLatestFrame(): ByteArray? = latestFrameBytes

    fun startCapture() {
        if (isStreaming) return
        isStreaming = true

        try {
            val thread = HandlerThread("ExplorerScreenCastThread").apply { start() }
            handlerThread = thread
            val handler = Handler(thread.looper)
            backgroundHandler = handler

            // CRITICAL for Android 14 (API 34) & Android 15 (API 35):
            // Must register a MediaProjection.Callback before calling createVirtualDisplay!
            mediaProjection.registerCallback(projectionCallback, handler)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
                metrics.densityDpi = context.resources.displayMetrics.densityDpi
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
            }

            val resSetting = com.troikoss.continuum_explorer.managers.SettingsManager.screenCastResolution.value
            val targetFps = com.troikoss.continuum_explorer.managers.SettingsManager.screenCastFps.value
            val rotationDegrees = com.troikoss.continuum_explorer.managers.SettingsManager.screenCastRotation.value
            val frameIntervalMs = (1000L / targetFps.coerceIn(5, 30)).coerceAtLeast(33L)

            var width = metrics.widthPixels.coerceAtLeast(360)
            var height = metrics.heightPixels.coerceAtLeast(640)
            val density = metrics.densityDpi.coerceAtLeast(160)

            val maxDim = when (resSetting) {
                "1080p" -> 1920
                "Native" -> 2560
                else -> 1280 // 720p default for fast, low-latency streaming
            }

            if (width > maxDim || height > maxDim) {
                val aspect = width.toFloat() / height.toFloat()
                if (width > height) {
                    width = maxDim
                    height = (maxDim / aspect).toInt()
                } else {
                    height = maxDim
                    width = (maxDim * aspect).toInt()
                }
            }
            // Ensure even dimensions required by virtual display surfaces
            width = (width / 2) * 2
            height = (height / 2) * 2

            Log.i(TAG, "Starting screen capture: ${width}x${height} @ ${targetFps}fps, rot=$rotationDegrees")

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ExplorerScreenCast",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            captureJob = scope.launch {
                var cachedBitmap: Bitmap? = null

                while (isActive && isStreaming) {
                    val startTime = System.currentTimeMillis()
                    val activeReader = imageReader ?: break

                    try {
                        val image = activeReader.acquireLatestImage()
                        if (image != null) {
                            try {
                                val planes = image.planes
                                val buffer: ByteBuffer = planes[0].buffer
                                buffer.rewind()
                                val pixelStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPadding = rowStride - pixelStride * width

                                val imgWidth = width + rowPadding / pixelStride
                                if (cachedBitmap == null || cachedBitmap.width != imgWidth || cachedBitmap.height != height) {
                                    cachedBitmap?.recycle()
                                    cachedBitmap = Bitmap.createBitmap(imgWidth, height, Bitmap.Config.ARGB_8888)
                                }

                                cachedBitmap.copyPixelsFromBuffer(buffer)
                            } finally {
                                image.close()
                            }

                            val sourceBmp = cachedBitmap
                            if (sourceBmp != null) {
                                var cleanBitmap = if (sourceBmp.width == width) {
                                    sourceBmp
                                } else {
                                    Bitmap.createBitmap(sourceBmp, 0, 0, width, height)
                                }

                                if (rotationDegrees > 0) {
                                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    val rotated = Bitmap.createBitmap(
                                        cleanBitmap, 0, 0, cleanBitmap.width, cleanBitmap.height, matrix, true
                                    )
                                    if (cleanBitmap != sourceBmp) {
                                        cleanBitmap.recycle()
                                    }
                                    cleanBitmap = rotated
                                }

                                val stream = ByteArrayOutputStream(65536)
                                cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 72, stream)
                                val jpegBytes = stream.toByteArray()

                                if (cleanBitmap != sourceBmp) {
                                    cleanBitmap.recycle()
                                }

                                latestFrameBytes = jpegBytes
                                notifyFrameListeners(jpegBytes)
                            }
                        } else {
                            // If screen hasn't changed, still push cached frame periodically so new clients see the screen
                            val existing = latestFrameBytes
                            if (existing != null && frameListeners.isNotEmpty()) {
                                notifyFrameListeners(existing)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame capture warning: ${e.message}")
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    val sleepTime = (frameIntervalMs - elapsed).coerceAtLeast(12L)
                    delay(sleepTime)
                }

                cachedBitmap?.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ScreenCastManager", e)
            stopCapture()
        }
    }

    private fun notifyFrameListeners(jpegBytes: ByteArray) {
        for (listener in frameListeners) {
            try {
                listener(jpegBytes)
            } catch (e: Exception) {
                Log.w(TAG, "Error in frame listener", e)
            }
        }
    }

    fun stopCapture() {
        if (!isStreaming && virtualDisplay == null && imageReader == null) return
        isStreaming = false
        captureJob?.cancel()
        captureJob = null
        frameListeners.clear()

        try {
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (_: Exception) {}

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

        try {
            handlerThread?.quitSafely()
            handlerThread = null
            backgroundHandler = null
        } catch (_: Exception) {}
    }
}
