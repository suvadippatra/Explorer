package com.troikoss.continuum_explorer.server

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.app.NotificationCompat
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.activities.MainActivity
import java.io.ByteArrayOutputStream

data class ServerLogEntry(
    val id: Long,
    val timestamp: Long,
    val clientIp: String,
    val protocol: String, // "HTTP", "FTP", "WS", or "SYNC"
    val action: String,
    val details: String
)

data class ClientSessionStats(
    val clientIp: String,
    val protocol: String,
    val firstSeen: Long,
    var lastSeen: Long,
    var requestsCount: Int,
    var bytesTransferred: Long
)

/**
 * Android Foreground Service responsible for keeping the process alive under Android 8–15
 * background-execution policies while [ServerManager] orchestrates Ktor HTTP and Apache FTP
 * lifecycles and optional live MJPEG Screen Mirroring.
 */
class ContinuumHubService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var lastFrameTimestamp = 0L

    companion object {
        const val CHANNEL_ID = "ContinuumHubChannel"
        const val NOTIFICATION_ID = 8081

        const val ACTION_START = "com.troikoss.continuum_explorer.action.START_HUB"
        const val ACTION_STOP = "com.troikoss.continuum_explorer.action.STOP_HUB"
        const val ACTION_RELOAD = "com.troikoss.continuum_explorer.action.RELOAD_HUB"
        const val ACTION_ENSURE_FOREGROUND = "com.troikoss.continuum_explorer.action.ENSURE_FOREGROUND"
        const val ACTION_STOP_IF_IDLE = "com.troikoss.continuum_explorer.action.STOP_IF_IDLE"
        const val ACTION_START_SCREENCAST = "com.troikoss.continuum_explorer.action.START_SCREENCAST"
        const val ACTION_STOP_SCREENCAST = "com.troikoss.continuum_explorer.action.STOP_SCREENCAST"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isScreenCasting: Boolean = false
            private set

        @Volatile
        var serverIpAddress: String = "127.0.0.1"
            private set

        @Volatile
        var activeFtpPort: Int = 2121
            private set

        @Volatile
        var startTimeMillis: Long = 0L
            private set

        @Volatile
        var latestScreencastJpeg: ByteArray? = null
            private set

        @Volatile
        private var activeServiceInstance: ContinuumHubService? = null

        // Live client tracking & server logs
        val logEntries = mutableStateListOf<ServerLogEntry>()
        val clientSessions = mutableStateMapOf<String, ClientSessionStats>()
        private var logIdCounter = 1L

        @Synchronized
        fun recordLog(
            clientIp: String,
            protocol: String,
            action: String,
            details: String,
            bytes: Long = 0L
        ) {
            val now = System.currentTimeMillis()
            val entry = ServerLogEntry(
                id = logIdCounter++,
                timestamp = now,
                clientIp = clientIp,
                protocol = protocol,
                action = action,
                details = details
            )
            Snapshot.withMutableSnapshot {
                if (logEntries.size >= 250) {
                    logEntries.removeAt(logEntries.lastIndex)
                }
                logEntries.add(0, entry)

                val existing = clientSessions[clientIp]
                if (existing != null) {
                    clientSessions[clientIp] = existing.copy(
                        lastSeen = now,
                        requestsCount = existing.requestsCount + 1,
                        bytesTransferred = existing.bytesTransferred + bytes,
                        protocol = if (existing.protocol.contains(protocol)) existing.protocol else "${existing.protocol}/$protocol"
                    )
                } else {
                    clientSessions[clientIp] = ClientSessionStats(
                        clientIp = clientIp,
                        protocol = protocol,
                        firstSeen = now,
                        lastSeen = now,
                        requestsCount = 1,
                        bytesTransferred = bytes
                    )
                }
            }
        }

        @Synchronized
        fun clearLogs() {
            Snapshot.withMutableSnapshot {
                logEntries.clear()
                clientSessions.clear()
            }
        }

        internal fun syncLegacyStaticFieldsFromManager(state: UnifiedServerState) {
            isRunning = state.isAnyRunning
            serverIpAddress = state.ipAddress
            activeFtpPort = state.ftp.port
            startTimeMillis = state.hubStartedAtMillis
        }

        internal fun refreshNotificationIfActive() {
            activeServiceInstance?.refreshForegroundNotification()
        }

        fun ensureForegroundServiceRunning(context: Context) {
            try {
                val intent = Intent(context, ContinuumHubService::class.java).apply {
                    action = ACTION_ENSURE_FOREGROUND
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("ContinuumHubService", "Could not start foreground service: ${e.message}")
            }
        }

        fun stopForegroundServiceWhenIdle(context: Context) {
            try {
                val intent = Intent(context, ContinuumHubService::class.java).apply {
                    action = ACTION_STOP_IF_IDLE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun startService(context: Context) {
            ServerManager.startCoordinated(context, startHttp = true, startFtp = true)
        }

        fun stopService(context: Context) {
            ServerManager.stopCoordinated(context, graceful = true, stopForegroundServiceIfIdle = true)
        }

        fun reloadIfRunning(context: Context) {
            if (ServerManager.state.value.isAnyRunning || isRunning) {
                ServerManager.restartCoordinated(context)
            }
        }

        fun startScreenCast(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ContinuumHubService::class.java).apply {
                action = ACTION_START_SCREENCAST
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopScreenCast(context: Context) {
            if (!isScreenCasting) return
            val intent = Intent(context, ContinuumHubService::class.java).apply {
                action = ACTION_STOP_SCREENCAST
            }
            context.startService(intent)
        }

        fun getLocalIpAddress(): String = ServerManager.resolveLocalIpAddress()
    }

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENSURE_FOREGROUND -> {
                promoteToForeground(includeMediaProjection = isScreenCasting)
            }

            ACTION_STOP_IF_IDLE -> {
                if (!ServerManager.state.value.isAnyRunning && !isScreenCasting) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                stopScreenCastInternal()
                ServerManager.stopCoordinated(applicationContext, graceful = true, stopForegroundServiceIfIdle = false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP_SCREENCAST -> {
                stopScreenCastInternal()
                if (ServerManager.state.value.isAnyRunning) {
                    promoteToForeground(includeMediaProjection = false)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_STICKY
            }

            ACTION_START_SCREENCAST -> {
                val resCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resCode == Activity.RESULT_OK && resData != null) {
                    promoteToForeground(includeMediaProjection = true)
                    if (!ServerManager.state.value.isHttpRunning) {
                        ServerManager.startCoordinated(applicationContext, startHttp = true, startFtp = true)
                    }
                    startScreenCastInternal(resCode, resData)
                }
                return START_STICKY
            }

            ACTION_RELOAD -> {
                promoteToForeground(includeMediaProjection = isScreenCasting)
                ServerManager.restartCoordinated(applicationContext)
                return START_STICKY
            }

            ACTION_START, null -> {
                promoteToForeground(includeMediaProjection = isScreenCasting)
                ServerManager.startCoordinated(applicationContext, startHttp = true, startFtp = true)
            }
        }

        return START_STICKY
    }

    internal fun refreshForegroundNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {}
    }

    private fun promoteToForeground(includeMediaProjection: Boolean) {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val fgType = if (includeMediaProjection) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, fgType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgType = if (includeMediaProjection) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, fgType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e("ContinuumHubService", "Failed to start foreground with projection type, falling back to DATA_SYNC", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (fallbackErr: Throwable) {
                Log.e("ContinuumHubService", "Fallback startForeground failed", fallbackErr)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun startScreenCastInternal(resultCode: Int, resultData: Intent) {
        stopScreenCastInternal()
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpManager.getMediaProjection(resultCode, resultData) ?: return
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopScreenCastInternal()
                }
            }, Handler(mainLooper))

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val resSetting = SettingsManager.screenCastResolution.value
            val scale = when (resSetting) {
                "720p" -> (720f / minOf(metrics.widthPixels, metrics.heightPixels)).coerceAtMost(1f)
                "1080p" -> (1080f / minOf(metrics.widthPixels, metrics.heightPixels)).coerceAtMost(1f)
                else -> 1f
            }
            val width = ((metrics.widthPixels * scale).toInt() / 2 * 2).coerceAtLeast(360)
            val height = ((metrics.heightPixels * scale).toInt() / 2 * 2).coerceAtLeast(640)
            val density = metrics.densityDpi

            val thread = HandlerThread("ContinuumScreencastThread")
            thread.start()
            val handler = Handler(thread.looper)
            captureThread = thread
            captureHandler = handler

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                val image = try { ir.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
                try {
                    val fps = SettingsManager.screenCastFps.value.coerceIn(5, 60)
                    val minIntervalMs = 1000L / fps
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTimestamp < minIntervalMs) {
                        return@setOnImageAvailableListener
                    }
                    lastFrameTimestamp = now

                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    var bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    if (rowPadding > 0) {
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        bitmap.recycle()
                        bitmap = cropped
                    }

                    val rot = SettingsManager.screenCastRotation.value
                    if (rot != 0) {
                        val matrix = Matrix().apply { postRotate(rot.toFloat()) }
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        bitmap.recycle()
                        bitmap = rotated
                    }

                    val jpegQuality = SettingsManager.screenCastQuality.value.coerceIn(30, 95)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                    bitmap.recycle()
                    latestScreencastJpeg = baos.toByteArray()
                } catch (e: Exception) {
                    Log.w("ContinuumHubService", "Frame encode error", e)
                } finally {
                    try { image.close() } catch (_: Exception) {}
                }
            }, handler)

            virtualDisplay = projection.createVirtualDisplay(
                "ContinuumHubScreenCast",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            isScreenCasting = true
            ServerManager.updateScreenCastStatus(true)
            recordLog("127.0.0.1", "HTTP", "SCREENCAST_START", "${width}x${height} @ ${SettingsManager.screenCastFps.value}fps")
        } catch (e: Exception) {
            Log.e("ContinuumHubService", "Failed to start ScreenCast", e)
            stopScreenCastInternal()
        }
    }

    private fun stopScreenCastInternal() {
        isScreenCasting = false
        ServerManager.updateScreenCastStatus(false)
        latestScreencastJpeg = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        try { captureThread?.quitSafely() } catch (_: Exception) {}
        captureThread = null
        captureHandler = null
    }

    override fun onDestroy() {
        if (activeServiceInstance === this) {
            activeServiceInstance = null
        }
        stopScreenCastInternal()
        ServerManager.stopCoordinated(applicationContext, graceful = true, stopForegroundServiceIfIdle = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Continuum Hub Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the local HTTP Web Portal, SyncPlay & FTP Server"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ContinuumHubService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val st = ServerManager.state.value
        val activeProtocols = buildList {
            if (st.isHttpRunning) add("HTTP :${st.http.port}")
            if (st.isFtpRunning) add("FTP :${st.ftp.port}")
            if (isScreenCasting) add("Screencast")
        }.joinToString(" • ").ifEmpty { "Ready" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Continuum Server Active (${st.ipAddress})")
            .setContentText(activeProtocols)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop All", pendingStop)
            .build()
    }
}
