package com.troikoss.continuum_explorer.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.os.Environment
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.activities.MainActivity
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * ContinuumHubService
 *
 * Standalone Android foreground Service that manages:
 * 1. Ktor embedded server (Netty) on port 8080 with WebSockets and HTML DSL Dashboard.
 * 2. Apache FTP Server daemon on port 2121.
 * 3. MediaProjection screen casting pumped through `/screencast` WebSocket route.
 * 4. Resilient Foreground Service notification channel for external displays & Samsung DeX.
 */
class ContinuumHubService : Service() {

    companion object {
        private const val TAG = "ContinuumHubService"
        const val CHANNEL_ID = "continuum_hub_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.troikoss.continuum_explorer.server.ACTION_START"
        const val ACTION_STOP = "com.troikoss.continuum_explorer.server.ACTION_STOP"
        const val ACTION_START_SCREENCAST = "com.troikoss.continuum_explorer.server.ACTION_START_SCREENCAST"
        const val ACTION_STOP_SCREENCAST = "com.troikoss.continuum_explorer.server.ACTION_STOP_SCREENCAST"

        const val EXTRA_PROJECTION_RESULT_CODE = "extra_projection_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var startTimeMillis: Long = 0L
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
        var bytesTransferredTotal: Long = 0L

        @Volatile
        var currentDownloadSpeedKbps: Float = 0f

        @Volatile
        var currentUploadSpeedKbps: Float = 0f

        @Volatile
        var connectedClientsCount: Int = 1

        val speedHistory = java.util.concurrent.CopyOnWriteArrayList<Float>()

        val logEntries = java.util.concurrent.CopyOnWriteArrayList<ServerLogEntry>()
        val clientSessions = java.util.concurrent.ConcurrentHashMap<String, ClientSession>()

        fun addLog(clientIp: String, protocol: String, action: String, details: String = "", bytes: Long = 0L) {
            val entry = ServerLogEntry(clientIp = clientIp, protocol = protocol, action = action, details = details)
            logEntries.add(0, entry)
            while (logEntries.size > 200) {
                logEntries.removeAt(logEntries.size - 1)
            }

            val session = clientSessions.computeIfAbsent(clientIp) {
                ClientSession(clientIp = clientIp, protocol = protocol)
            }
            session.lastActive = System.currentTimeMillis()
            session.requestsCount++
            if (bytes > 0L) {
                session.bytesTransferred += bytes
                recordTransfer(bytes)
            }
            connectedClientsCount = clientSessions.size.coerceAtLeast(1)
        }

        fun clearLogs() {
            logEntries.clear()
            clientSessions.clear()
        }

        fun recordTransfer(bytes: Long, isUpload: Boolean = false) {
            if (bytes <= 0) return
            bytesTransferredTotal += bytes
            val kb = bytes / 1024f
            if (isUpload) {
                currentUploadSpeedKbps = (currentUploadSpeedKbps * 0.7f) + (kb * 0.3f)
            } else {
                currentDownloadSpeedKbps = (currentDownloadSpeedKbps * 0.7f) + (kb * 0.3f)
            }
            val currentRate = currentDownloadSpeedKbps + currentUploadSpeedKbps
            speedHistory.add(currentRate)
            while (speedHistory.size > 30) {
                speedHistory.removeAt(0)
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, ContinuumHubService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ContinuumHubService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun startScreenCast(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ContinuumHubService::class.java).apply {
                action = ACTION_START_SCREENCAST
                putExtra(EXTRA_PROJECTION_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopScreenCast(context: Context) {
            val intent = Intent(context, ContinuumHubService::class.java).apply {
                action = ACTION_STOP_SCREENCAST
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var ktorServer: KtorEmbeddedServer? = null
    private var ftpServer: ApacheFtpServerDaemon? = null
    private var screenCastManager: ScreenCastManager? = null
    private var mediaProjection: MediaProjection? = null

    inner class LocalBinder : Binder() {
        fun getService(): ContinuumHubService = this@ContinuumHubService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ContinuumHubService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                shutdownServers()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                startForegroundWithNotification()
                initializeServers()
            }

            ACTION_START_SCREENCAST -> {
                val resultCode = intent?.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
                val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startForegroundWithNotification(isProjection = true)
                    initializeServers()
                    setupMediaProjection(resultCode, data)
                }
            }

            ACTION_STOP_SCREENCAST -> {
                stopMediaProjection()
                updateNotification()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Continuum Hub Network & Cast Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs local HTTP dashboard, FTP daemon, and live screen casting"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun getFormattedUptime(): String {
        if (!isRunning || startTimeMillis <= 0L) return "00:00:00"
        val elapsedSec = ((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)
        val hours = elapsedSec / 3600
        val mins = (elapsedSec % 3600) / 60
        val secs = elapsedSec % 60
        return String.format("%02d:%02d:%02d", hours, mins, secs)
    }

    private fun buildNotification(isProjection: Boolean): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, ContinuumHubService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val statusText = "Run time: ${getFormattedUptime()}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Explorer")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_network_server)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Hub", stopPendingIntent)
            .build()
    }

    private fun startForegroundWithNotification(isProjection: Boolean = false) {
        val notification = buildNotification(isProjection)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (isProjection || isScreenCasting) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
            }
            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                Log.w(TAG, "Error starting foreground with specified type, falling back", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification(isScreenCasting)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun initializeServers() {
        if (isRunning) return
        isRunning = true
        startTimeMillis = System.currentTimeMillis()
        serverIpAddress = resolveLocalIpAddress()

        val port = SettingsManager.ftpPort.value
        val isAnon = SettingsManager.isFtpAnonymousAllowed.value
        val user = SettingsManager.ftpUsername.value
        val pass = SettingsManager.ftpPassword.value
        val allowWrite = SettingsManager.ftpWritePermission.value
        val hostedMode = SettingsManager.ftpHostedMode.value
        val customPath = SettingsManager.ftpHostedPath.value

        val targetRootDir = when (hostedMode) {
            FtpHostedMode.CUSTOM_FOLDER -> {
                if (customPath.isNotBlank() && java.io.File(customPath).exists()) java.io.File(customPath)
                else Environment.getExternalStorageDirectory()
            }
            FtpHostedMode.BATCH_FILES -> {
                com.troikoss.continuum_explorer.managers.FtpBucketManager.prepareBucketDirectory(applicationContext)
            }
            else -> Environment.getExternalStorageDirectory()
        }

        activeFtpPort = port

        serviceScope.launch {
            // Start Ktor HTTP & WebSockets Server
            ktorServer = KtorEmbeddedServer(
                context = applicationContext,
                port = 8080,
                rootDirectory = targetRootDir,
                getScreenCastManager = { screenCastManager }
            ).apply {
                start()
            }

            // Start Apache FTP Server daemon
            ftpServer = ApacheFtpServerDaemon(
                context = applicationContext,
                port = port,
                isAnonymousAllowed = isAnon,
                username = user,
                password = pass,
                allowWritePermission = allowWrite,
                rootDirectory = targetRootDir
            ).apply {
                start()
            }

            withContext(Dispatchers.Main) {
                updateNotification()
            }

            while (isActive && isRunning) {
                delay(1000L)
                withContext(Dispatchers.Main) {
                    if (isRunning) {
                        updateNotification()
                    }
                }
            }
        }
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val projection = mediaProjection ?: return
            screenCastManager = ScreenCastManager(applicationContext, projection).apply {
                startCapture()
            }
            isScreenCasting = true
            updateNotification()
            Log.i(TAG, "MediaProjection screencast initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection screencast", e)
        }
    }

    private fun stopMediaProjection() {
        isScreenCasting = false
        try {
            screenCastManager?.stopCapture()
            screenCastManager = null
            mediaProjection?.stop()
            mediaProjection = null
            Log.i(TAG, "MediaProjection screencast stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection", e)
        }
    }

    private fun shutdownServers() {
        isRunning = false
        startTimeMillis = 0L
        stopMediaProjection()

        val oldKtor = ktorServer
        val oldFtp = ftpServer
        ktorServer = null
        ftpServer = null

        // Stop heavy server sockets asynchronously on IO to eliminate UI lag
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                oldKtor?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ktor", e)
            }
            try {
                oldFtp?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ftp", e)
            }
        }

        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ContinuumHubService onDestroy")
        shutdownServers()
    }

    private fun resolveLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving IP", e)
        }
        return "127.0.0.1"
    }
}

data class ServerLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val protocol: String,
    val action: String,
    val details: String = ""
)

data class ClientSession(
    val clientIp: String,
    val protocol: String,
    val connectedAt: Long = System.currentTimeMillis(),
    var lastActive: Long = System.currentTimeMillis(),
    var requestsCount: Int = 1,
    var bytesTransferred: Long = 0L
)
