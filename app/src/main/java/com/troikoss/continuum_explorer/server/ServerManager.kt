package com.troikoss.continuum_explorer.server

import android.content.Context
import android.os.Environment
import android.util.Log
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Lifecycle state for an individual server protocol (HTTP or FTP).
 */
enum class ProtocolLifecycleState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

/**
 * Snapshot status for an individual server instance (Ktor HTTP or Apache FTP).
 */
data class ProtocolServerStatus(
    val protocolName: String,
    val state: ProtocolLifecycleState = ProtocolLifecycleState.STOPPED,
    val port: Int = 0,
    val startedAtMillis: Long = 0L,
    val activeConnections: Int = 0,
    val hasActiveTransfers: Boolean = false,
    val errorMessage: String? = null
) {
    val isRunning: Boolean
        get() = state == ProtocolLifecycleState.RUNNING

    val isTransitioning: Boolean
        get() = state == ProtocolLifecycleState.STARTING || state == ProtocolLifecycleState.STOPPING
}

/**
 * Unified observable state across Ktor HTTP, Apache FTP, and Screen Mirroring.
 */
data class UnifiedServerState(
    val http: ProtocolServerStatus = ProtocolServerStatus(protocolName = "HTTP", port = 8080),
    val ftp: ProtocolServerStatus = ProtocolServerStatus(protocolName = "FTP", port = 2121),
    val ipAddress: String = "127.0.0.1",
    val isScreenCasting: Boolean = false,
    val hubStartedAtMillis: Long = 0L,
    val lastGlobalError: String? = null
) {
    val isHttpRunning: Boolean
        get() = http.isRunning

    val isFtpRunning: Boolean
        get() = ftp.isRunning

    val isAnyRunning: Boolean
        get() = http.isRunning || ftp.isRunning

    val isAllRunning: Boolean
        get() = http.isRunning && ftp.isRunning

    val isTransitioning: Boolean
        get() = http.isTransitioning || ftp.isTransitioning
}

/**
 * Central ServerManager responsible for the independent and coordinated startup, shutdown,
 * restart, state monitoring, error handling, and resource cleanup of:
 * - [KtorEmbeddedServer] (Ktor Netty HTTP / WebSocket / Web Portal / SyncPlay server)
 * - [ApacheFtpServerDaemon] (Apache Mina FTP server)
 *
 * Architectural Separation:
 * This manager coordinates lifecycles asynchronously using structured Kotlin concurrency and
 * [Mutex] synchronization while keeping the underlying HTTP and FTP server implementations
 * completely decoupled.
 */
object ServerManager {
    private const val TAG = "ServerManager"
    const val DEFAULT_HTTP_PORT = 8080

    private val supervisorJob = SupervisorJob()
    private val managerScope = CoroutineScope(
        supervisorJob + Dispatchers.IO + CoroutineName("ContinuumServerManager")
    )

    // Mutexes prevent race conditions and duplicate server bindings on rapid UI toggles
    private val coordinationMutex = Mutex()
    private val httpMutex = Mutex()
    private val ftpMutex = Mutex()

    @Volatile
    private var ktorServerInstance: KtorEmbeddedServer? = null

    @Volatile
    private var ftpServerInstance: ApacheFtpServerDaemon? = null

    private var monitorJob: Job? = null

    private val _state = MutableStateFlow(
        UnifiedServerState(
            ipAddress = resolveLocalIpAddress()
        )
    )
    val state: StateFlow<UnifiedServerState> = _state.asStateFlow()

    /**
     * Coordinated startup of both Ktor HTTP and Apache FTP servers (or a subset).
     * Automatically ensures [ContinuumHubService] foreground service is active to comply
     * with Android 8–15 background execution policies.
     */
    fun startCoordinated(
        context: Context,
        startHttp: Boolean = true,
        startFtp: Boolean = true
    ) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            coordinationMutex.withLock {
                refreshIpAddress()
                if (startHttp) {
                    startHttpInternal(appContext)
                }
                if (startFtp) {
                    startFtpInternal(appContext)
                }
                updateHubTimestampAndServiceState(appContext)
                ensureHealthMonitorRunning(appContext)
            }
        }
    }

    /**
     * Coordinated graceful shutdown of all running servers.
     */
    fun stopCoordinated(
        context: Context? = null,
        graceful: Boolean = true,
        stopForegroundServiceIfIdle: Boolean = true
    ) {
        val appContext = context?.applicationContext
        managerScope.launch {
            coordinationMutex.withLock {
                stopHttpInternal(graceful = graceful)
                stopFtpInternal(graceful = graceful)
                updateHubTimestampAndServiceState(appContext, stopForegroundServiceIfIdle)
            }
        }
    }

    /**
     * Coordinated restart of whichever servers are currently active (or both if none are active),
     * used when shared folder scope or network settings change.
     */
    fun restartCoordinated(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            coordinationMutex.withLock {
                val wasHttpActive = _state.value.http.isRunning || _state.value.http.state == ProtocolLifecycleState.ERROR
                val wasFtpActive = _state.value.ftp.isRunning || _state.value.ftp.state == ProtocolLifecycleState.ERROR
                val restartHttp = if (!wasHttpActive && !wasFtpActive) true else wasHttpActive
                val restartFtp = if (!wasHttpActive && !wasFtpActive) true else wasFtpActive

                if (restartHttp) {
                    stopHttpInternal(graceful = true)
                }
                if (restartFtp) {
                    stopFtpInternal(graceful = true)
                }
                delay(120L)
                refreshIpAddress()
                if (restartHttp) {
                    startHttpInternal(appContext)
                }
                if (restartFtp) {
                    startFtpInternal(appContext)
                }
                updateHubTimestampAndServiceState(appContext)
                ensureHealthMonitorRunning(appContext)
            }
        }
    }

    // --- INDEPENDENT HTTP SERVER CONTROLS ---

    fun startHttpServer(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            refreshIpAddress()
            startHttpInternal(appContext)
            updateHubTimestampAndServiceState(appContext)
            ensureHealthMonitorRunning(appContext)
        }
    }

    fun stopHttpServer(context: Context? = null, graceful: Boolean = true) {
        val appContext = context?.applicationContext
        managerScope.launch {
            stopHttpInternal(graceful = graceful)
            updateHubTimestampAndServiceState(appContext)
        }
    }

    fun restartHttpServer(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            stopHttpInternal(graceful = true)
            delay(100L)
            refreshIpAddress()
            startHttpInternal(appContext)
            updateHubTimestampAndServiceState(appContext)
            ensureHealthMonitorRunning(appContext)
        }
    }

    // --- INDEPENDENT FTP SERVER CONTROLS ---

    fun startFtpServer(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            refreshIpAddress()
            startFtpInternal(appContext)
            updateHubTimestampAndServiceState(appContext)
            ensureHealthMonitorRunning(appContext)
        }
    }

    fun stopFtpServer(context: Context? = null, graceful: Boolean = true) {
        val appContext = context?.applicationContext
        managerScope.launch {
            stopFtpInternal(graceful = graceful)
            updateHubTimestampAndServiceState(appContext)
        }
    }

    fun restartFtpServer(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            stopFtpInternal(graceful = true)
            delay(100L)
            refreshIpAddress()
            startFtpInternal(appContext)
            updateHubTimestampAndServiceState(appContext)
            ensureHealthMonitorRunning(appContext)
        }
    }

    fun updateScreenCastStatus(isCasting: Boolean) {
        _state.update { it.copy(isScreenCasting = isCasting) }
    }

    // --- INTERNAL MUTEX-GUARDED LIFECYCLE IMPLEMENTATIONS ---

    private suspend fun startHttpInternal(appContext: Context) {
        httpMutex.withLock {
            val existing = ktorServerInstance
            if (existing != null && existing.isRunning) {
                return
            }

            _state.update {
                it.copy(
                    http = it.http.copy(
                        state = ProtocolLifecycleState.STARTING,
                        port = DEFAULT_HTTP_PORT,
                        errorMessage = null
                    )
                )
            }

            try {
                // Clean up any stale instance first
                existing?.stop(gracePeriodMillis = 200L, timeoutMillis = 500L)
                ktorServerInstance = null

                val server = KtorEmbeddedServer(appContext, DEFAULT_HTTP_PORT)
                server.startOrThrow()
                ktorServerInstance = server
                val now = System.currentTimeMillis()

                _state.update {
                    it.copy(
                        http = ProtocolServerStatus(
                            protocolName = "HTTP",
                            state = ProtocolLifecycleState.RUNNING,
                            port = DEFAULT_HTTP_PORT,
                            startedAtMillis = now,
                            activeConnections = server.getActiveRequestsCount(),
                            hasActiveTransfers = server.getActiveRequestsCount() > 0,
                            errorMessage = null
                        ),
                        lastGlobalError = null
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
                ContinuumHubService.recordLog("127.0.0.1", "HTTP", "START", "Ktor HTTP/WS Server online on :$DEFAULT_HTTP_PORT")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start Ktor HTTP server", e)
                ktorServerInstance = null
                val errMsg = e.localizedMessage ?: "Failed to bind HTTP port $DEFAULT_HTTP_PORT"
                _state.update {
                    it.copy(
                        http = it.http.copy(
                            state = ProtocolLifecycleState.ERROR,
                            errorMessage = errMsg
                        ),
                        lastGlobalError = "HTTP Error: $errMsg"
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
            }
        }
    }

    private suspend fun stopHttpInternal(graceful: Boolean) {
        httpMutex.withLock {
            val existing = ktorServerInstance
            if (existing == null) {
                _state.update {
                    it.copy(
                        http = it.http.copy(
                            state = ProtocolLifecycleState.STOPPED,
                            startedAtMillis = 0L,
                            activeConnections = 0,
                            hasActiveTransfers = false
                        )
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
                return
            }

            _state.update {
                it.copy(http = it.http.copy(state = ProtocolLifecycleState.STOPPING))
            }

            try {
                if (graceful) {
                    existing.stop(gracePeriodMillis = 900L, timeoutMillis = 2200L)
                } else {
                    existing.stop(gracePeriodMillis = 100L, timeoutMillis = 400L)
                }
                ContinuumHubService.recordLog("127.0.0.1", "HTTP", "STOP", "Ktor HTTP Server stopped cleanly")
            } catch (e: Throwable) {
                Log.w(TAG, "Exception while stopping Ktor HTTP server: ${e.message}")
            } finally {
                ktorServerInstance = null
                _state.update {
                    it.copy(
                        http = ProtocolServerStatus(
                            protocolName = "HTTP",
                            state = ProtocolLifecycleState.STOPPED,
                            port = DEFAULT_HTTP_PORT,
                            startedAtMillis = 0L,
                            activeConnections = 0,
                            hasActiveTransfers = false,
                            errorMessage = null
                        )
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
            }
        }
    }

    private suspend fun startFtpInternal(appContext: Context) {
        ftpMutex.withLock {
            val existing = ftpServerInstance
            if (existing != null && existing.isRunning) {
                return
            }

            val ftpPort = SettingsManager.ftpPort.value.coerceIn(1024, 65535)
            val allowAnon = SettingsManager.isFtpAnonymousAllowed.value
            val ftpUser = SettingsManager.ftpUsername.value
            val ftpPass = SettingsManager.ftpPassword.value
            val allowWrite = SettingsManager.ftpAllowUpload.value
            val rootDir = resolveFtpRootDirectory(appContext)

            _state.update {
                it.copy(
                    ftp = it.ftp.copy(
                        state = ProtocolLifecycleState.STARTING,
                        port = ftpPort,
                        errorMessage = null
                    )
                )
            }

            try {
                existing?.stop(gracePeriodMs = 300L)
                ftpServerInstance = null

                val daemon = ApacheFtpServerDaemon(
                    port = ftpPort,
                    rootDirectory = rootDir,
                    allowAnonymous = allowAnon,
                    username = ftpUser,
                    password = ftpPass,
                    allowWrite = allowWrite
                )
                daemon.startOrThrow()
                ftpServerInstance = daemon
                val now = System.currentTimeMillis()

                _state.update {
                    it.copy(
                        ftp = ProtocolServerStatus(
                            protocolName = "FTP",
                            state = ProtocolLifecycleState.RUNNING,
                            port = ftpPort,
                            startedAtMillis = now,
                            activeConnections = daemon.getActiveConnectionsCount(),
                            hasActiveTransfers = daemon.hasActiveTransfers(),
                            errorMessage = null
                        ),
                        lastGlobalError = null
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
                ContinuumHubService.recordLog("127.0.0.1", "FTP", "START", "Apache FTP Server online on :$ftpPort")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start Apache FTP server on port $ftpPort", e)
                ftpServerInstance = null
                val errMsg = e.localizedMessage ?: "Failed to bind FTP port $ftpPort"
                _state.update {
                    it.copy(
                        ftp = it.ftp.copy(
                            state = ProtocolLifecycleState.ERROR,
                            port = ftpPort,
                            errorMessage = errMsg
                        ),
                        lastGlobalError = "FTP Error: $errMsg"
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
            }
        }
    }

    private suspend fun stopFtpInternal(graceful: Boolean) {
        ftpMutex.withLock {
            val existing = ftpServerInstance
            val ftpPort = SettingsManager.ftpPort.value.coerceIn(1024, 65535)
            if (existing == null) {
                _state.update {
                    it.copy(
                        ftp = it.ftp.copy(
                            state = ProtocolLifecycleState.STOPPED,
                            port = ftpPort,
                            startedAtMillis = 0L,
                            activeConnections = 0,
                            hasActiveTransfers = false
                        )
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
                return
            }

            _state.update {
                it.copy(ftp = it.ftp.copy(state = ProtocolLifecycleState.STOPPING))
            }

            try {
                existing.stop(gracePeriodMs = if (graceful) 1500L else 100L)
                ContinuumHubService.recordLog("127.0.0.1", "FTP", "STOP", "Apache FTP Server stopped cleanly")
            } catch (e: Throwable) {
                Log.w(TAG, "Exception while stopping Apache FTP server: ${e.message}")
            } finally {
                ftpServerInstance = null
                _state.update {
                    it.copy(
                        ftp = ProtocolServerStatus(
                            protocolName = "FTP",
                            state = ProtocolLifecycleState.STOPPED,
                            port = ftpPort,
                            startedAtMillis = 0L,
                            activeConnections = 0,
                            hasActiveTransfers = false,
                            errorMessage = null
                        )
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
            }
        }
    }

    private fun resolveFtpRootDirectory(context: Context): File {
        FtpBucketManager.initialize(context)
        val hostedMode = SettingsManager.ftpHostedMode.value
        val customPath = SettingsManager.ftpHostedPath.value
        return when (hostedMode) {
            FtpHostedMode.CUSTOM_FOLDER -> {
                if (customPath.isNotBlank()) {
                    val f = File(customPath)
                    if (f.exists()) (if (f.isDirectory) f else f.parentFile ?: Environment.getExternalStorageDirectory())
                    else Environment.getExternalStorageDirectory()
                } else {
                    Environment.getExternalStorageDirectory()
                }
            }
            FtpHostedMode.BATCH_FILES -> {
                val items = FtpBucketManager.bucketItems
                when {
                    items.size == 1 && items.first().isDirectory -> {
                        File(items.first().absolutePath).takeIf { it.exists() } ?: Environment.getExternalStorageDirectory()
                    }
                    items.isNotEmpty() -> {
                        val firstParent = File(items.first().absolutePath).parentFile
                        firstParent?.takeIf { it.exists() } ?: Environment.getExternalStorageDirectory()
                    }
                    else -> Environment.getExternalStorageDirectory()
                }
            }
            FtpHostedMode.FULL_STORAGE -> Environment.getExternalStorageDirectory()
        }
    }

    private fun updateHubTimestampAndServiceState(
        appContext: Context?,
        stopForegroundServiceIfIdle: Boolean = true
    ) {
        val current = _state.value
        val newHubStart = when {
            !current.isAnyRunning -> 0L
            current.hubStartedAtMillis > 0L -> current.hubStartedAtMillis
            else -> System.currentTimeMillis()
        }
        val updated = current.copy(
            ipAddress = resolveLocalIpAddress(),
            hubStartedAtMillis = newHubStart
        )
        _state.value = updated
        ContinuumHubService.syncLegacyStaticFieldsFromManager(updated)

        if (appContext != null) {
            if (!updated.isAnyRunning && !updated.isScreenCasting && stopForegroundServiceIfIdle) {
                ContinuumHubService.stopForegroundServiceWhenIdle(appContext)
            } else {
                ContinuumHubService.refreshNotificationIfActive()
            }
        }
    }

    private fun ensureHealthMonitorRunning(appContext: Context) {
        if (monitorJob?.isActive == true) return
        monitorJob = managerScope.launch {
            while (isActive) {
                delay(2000L)
                val s = _state.value
                if (!s.isAnyRunning && !s.isScreenCasting) {
                    break
                }
                val ip = resolveLocalIpAddress()
                val httpConn = ktorServerInstance?.getActiveRequestsCount() ?: 0
                val ftpConn = ftpServerInstance?.getActiveConnectionsCount() ?: 0
                val ftpTrans = ftpServerInstance?.hasActiveTransfers() ?: false

                // Detect unexpected daemon crash and reflect in state
                val ftpAlive = ftpServerInstance?.isRunning ?: false
                val updatedFtpState = if (s.ftp.state == ProtocolLifecycleState.RUNNING && !ftpAlive) {
                    ProtocolLifecycleState.ERROR
                } else {
                    s.ftp.state
                }

                _state.update { current ->
                    current.copy(
                        ipAddress = ip,
                        http = current.http.copy(
                            activeConnections = httpConn,
                            hasActiveTransfers = httpConn > 0
                        ),
                        ftp = current.ftp.copy(
                            state = updatedFtpState,
                            activeConnections = ftpConn,
                            hasActiveTransfers = ftpTrans,
                            errorMessage = if (updatedFtpState == ProtocolLifecycleState.ERROR) "FTP daemon stopped unexpectedly" else current.ftp.errorMessage
                        )
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
            }
        }
    }

    fun refreshIpAddress(): String {
        val ip = resolveLocalIpAddress()
        _state.update { it.copy(ipAddress = ip) }
        ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
        return ip
    }

    fun resolveLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr.startsWith("192.168.") || hostAddr.startsWith("10.") || hostAddr.startsWith("172.")) {
                            return hostAddr
                        }
                    }
                }
            }
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        return "127.0.0.1"
    }
}
