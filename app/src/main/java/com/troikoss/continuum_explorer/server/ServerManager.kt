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
 * Mutually exclusive hosting mode enforced across the entire application.
 * Only one hosting mode may be active at any time: FTP Hosting, HTTP Hosting, or OmniOS Casting.
 */
enum class ActiveHostingMode {
    NONE,
    FTP,
    HTTP,
    CASTING
}

/**
 * Lifecycle state for an individual server protocol (FTP, HTTP, or OmniOS Casting).
 */
enum class ProtocolLifecycleState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

/**
 * Pluggable lifecycle bridge for the OmniOS Casting Host Engine.
 * Bound by the OmniOS subsystem so [ServerManager] can orchestrate mutually exclusive
 * transitions without circular dependencies.
 */
interface OmniOSCastingBridge {
    val isRunning: Boolean
    suspend fun startOrThrow(context: Context, port: Int)
    suspend fun stop(graceful: Boolean)
    fun getActiveConnectionsCount(): Int
}

/**
 * Snapshot status for an individual server instance (Apache FTP, Ktor HTTP, or OmniOS Casting).
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
 * Unified observable state across mutually exclusive FTP Hosting, HTTP Hosting, and OmniOS Casting.
 */
data class UnifiedServerState(
    val activeHostingMode: ActiveHostingMode = ActiveHostingMode.NONE,
    val isSyncPlayCompanionActive: Boolean = false,
    val http: ProtocolServerStatus = ProtocolServerStatus(protocolName = "HTTP", port = ServerManager.DEFAULT_HTTP_PORT),
    val ftp: ProtocolServerStatus = ProtocolServerStatus(protocolName = "FTP", port = 2121),
    val casting: ProtocolServerStatus = ProtocolServerStatus(protocolName = "OmniOS", port = ServerManager.DEFAULT_CASTING_PORT),
    val ipAddress: String = "127.0.0.1",
    val isScreenCasting: Boolean = false,
    val hubStartedAtMillis: Long = 0L,
    val lastGlobalError: String? = null
) {
    val isHttpRunning: Boolean
        get() = activeHostingMode == ActiveHostingMode.HTTP && http.isRunning

    val isHttpTransportRunning: Boolean
        get() = http.isRunning

    val isFtpRunning: Boolean
        get() = activeHostingMode == ActiveHostingMode.FTP && ftp.isRunning

    val isCastingRunning: Boolean
        get() = activeHostingMode == ActiveHostingMode.CASTING && casting.isRunning

    val isAnyRunning: Boolean
        get() = http.isRunning || ftp.isRunning || casting.isRunning

    val isAllRunning: Boolean
        get() = http.isRunning && ftp.isRunning

    val isTransitioning: Boolean
        get() = http.isTransitioning || ftp.isTransitioning || casting.isTransitioning
}

/**
 * Central ServerManager responsible for strictly mutually exclusive orchestration of:
 * - [ActiveHostingMode.FTP]: [ApacheFtpServerDaemon] (with optional internal SyncPlay companion transport)
 * - [ActiveHostingMode.HTTP]: [KtorEmbeddedServer] (Static Website Hosting)
 * - [ActiveHostingMode.CASTING]: OmniOS Host Engine via [OmniOSCastingBridge]
 *
 * Starting any hosting mode automatically drains and stops any previously active mode under
 * [coordinationMutex] to guarantee clean port ownership and predictable power/thermal behavior.
 */
object ServerManager {
    private const val TAG = "ServerManager"
    const val DEFAULT_HTTP_PORT = 8080
    const val DEFAULT_CASTING_PORT = 8080

    private val supervisorJob = SupervisorJob()
    private val managerScope = CoroutineScope(
        supervisorJob + Dispatchers.IO + CoroutineName("HostingServerManager")
    )

    // Mutexes prevent race conditions and duplicate server bindings on rapid UI toggles
    private val coordinationMutex = Mutex()
    private val httpMutex = Mutex()
    private val ftpMutex = Mutex()
    private val castingMutex = Mutex()

    @Volatile
    private var ktorServerInstance: KtorEmbeddedServer? = null

    @Volatile
    private var ftpServerInstance: ApacheFtpServerDaemon? = null

    @Volatile
    private var castingBridge: OmniOSCastingBridge? = null

    private var monitorJob: Job? = null

    private val _state = MutableStateFlow(
        UnifiedServerState(
            ipAddress = resolveLocalIpAddress()
        )
    )
    val state: StateFlow<UnifiedServerState> = _state.asStateFlow()

    /**
     * Registers the active OmniOS Casting backend engine.
     */
    fun registerCastingBridge(bridge: OmniOSCastingBridge) {
        castingBridge = bridge
    }

    /**
     * Strictly activates a single [ActiveHostingMode], shutting down any other active mode first.
     *
     * When [mode] is [ActiveHostingMode.FTP] and [withSyncPlayCompanion] is true (e.g., when
     * SyncPlay is launched from File Manager or FTP Hosting), the internal SyncPlay HTTP/WS
     * media transport is started alongside FTP without activating HTTP Website Hosting mode.
     */
    fun activateExclusiveMode(
        context: Context,
        mode: ActiveHostingMode,
        withSyncPlayCompanion: Boolean = false,
        castingPort: Int = DEFAULT_CASTING_PORT
    ) {
        val appContext = context.applicationContext
        if (mode != ActiveHostingMode.NONE) {
            ContinuumHubService.ensureForegroundServiceRunning(appContext)
        }
        managerScope.launch {
            coordinationMutex.withLock {
                refreshIpAddress()
                when (mode) {
                    ActiveHostingMode.NONE -> {
                        stopCastingInternal(graceful = true)
                        stopHttpInternal(graceful = true, clearSyncPlay = true)
                        stopFtpInternal(graceful = true, clearSyncPlay = true)
                        _state.update {
                            it.copy(
                                activeHostingMode = ActiveHostingMode.NONE,
                                isSyncPlayCompanionActive = false
                            )
                        }
                    }

                    ActiveHostingMode.FTP -> {
                        val needsClearSync = !withSyncPlayCompanion && _state.value.activeHostingMode != ActiveHostingMode.FTP
                        if (_state.value.casting.isRunning || _state.value.activeHostingMode == ActiveHostingMode.CASTING) {
                            stopCastingInternal(graceful = true)
                        }
                        if (!withSyncPlayCompanion && _state.value.http.isRunning) {
                            stopHttpInternal(graceful = true, clearSyncPlay = needsClearSync)
                        }
                        _state.update {
                            it.copy(
                                activeHostingMode = ActiveHostingMode.FTP,
                                isSyncPlayCompanionActive = withSyncPlayCompanion
                            )
                        }
                        startFtpInternal(appContext)
                        if (withSyncPlayCompanion) {
                            startHttpInternal(appContext)
                        }
                        if (!_state.value.ftp.isRunning && (!withSyncPlayCompanion || !_state.value.http.isRunning)) {
                            _state.update {
                                it.copy(
                                    activeHostingMode = ActiveHostingMode.NONE,
                                    isSyncPlayCompanionActive = false
                                )
                            }
                        }
                    }

                    ActiveHostingMode.HTTP -> {
                        if (_state.value.casting.isRunning || _state.value.activeHostingMode == ActiveHostingMode.CASTING) {
                            stopCastingInternal(graceful = true)
                        }
                        if (_state.value.ftp.isRunning || _state.value.activeHostingMode == ActiveHostingMode.FTP) {
                            stopFtpInternal(graceful = true, clearSyncPlay = true)
                        }
                        _state.update {
                            it.copy(
                                activeHostingMode = ActiveHostingMode.HTTP,
                                isSyncPlayCompanionActive = false
                            )
                        }
                        startHttpInternal(appContext)
                        if (!_state.value.http.isRunning) {
                            _state.update { it.copy(activeHostingMode = ActiveHostingMode.NONE) }
                        }
                    }

                    ActiveHostingMode.CASTING -> {
                        val preserveSyncPlay = SyncPlayManager.roomState.value.isActive
                        if (_state.value.http.isRunning) {
                            stopHttpInternal(graceful = true, clearSyncPlay = !preserveSyncPlay)
                        }
                        if (_state.value.ftp.isRunning) {
                            stopFtpInternal(graceful = true, clearSyncPlay = !preserveSyncPlay)
                        }
                        _state.update {
                            it.copy(
                                activeHostingMode = ActiveHostingMode.CASTING,
                                isSyncPlayCompanionActive = false
                            )
                        }
                        startCastingInternal(appContext, castingPort)
                        if (!_state.value.casting.isRunning) {
                            _state.update { it.copy(activeHostingMode = ActiveHostingMode.NONE) }
                        }
                    }
                }
                updateHubTimestampAndServiceState(appContext)
                if (_state.value.isAnyRunning) {
                    ensureHealthMonitorRunning(appContext)
                }
            }
        }
    }

    /**
     * Compatibility entry point that routes into [activateExclusiveMode]:
     * - `startFtp = true, startHttp = true` -> FTP Hosting with SyncPlay companion transport.
     * - `startFtp = true, startHttp = false` -> Exclusive FTP Hosting.
     * - `startHttp = true, startFtp = false` -> Exclusive HTTP Hosting.
     */
    fun startCoordinated(
        context: Context,
        startHttp: Boolean = true,
        startFtp: Boolean = true
    ) {
        when {
            startFtp && startHttp -> activateExclusiveMode(
                context = context,
                mode = ActiveHostingMode.FTP,
                withSyncPlayCompanion = true
            )
            startFtp -> activateExclusiveMode(context = context, mode = ActiveHostingMode.FTP)
            startHttp -> activateExclusiveMode(context = context, mode = ActiveHostingMode.HTTP)
            else -> stopCoordinated(context = context)
        }
    }

    /**
     * Coordinated graceful shutdown of whichever hosting mode is active.
     */
    fun stopCoordinated(
        context: Context? = null,
        graceful: Boolean = true,
        stopForegroundServiceIfIdle: Boolean = true
    ) {
        val appContext = context?.applicationContext
        managerScope.launch {
            coordinationMutex.withLock {
                stopCastingInternal(graceful = graceful)
                stopHttpInternal(graceful = graceful, clearSyncPlay = true)
                stopFtpInternal(graceful = graceful, clearSyncPlay = true)
                _state.update {
                    it.copy(
                        activeHostingMode = ActiveHostingMode.NONE,
                        isSyncPlayCompanionActive = false
                    )
                }
                updateHubTimestampAndServiceState(appContext, stopForegroundServiceIfIdle)
            }
        }
    }

    /**
     * Restarts whichever exclusive hosting mode is currently active (defaults to FTP Hosting if idle).
     */
    fun restartCoordinated(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            coordinationMutex.withLock {
                val current = _state.value
                val targetMode = when (current.activeHostingMode) {
                    ActiveHostingMode.NONE -> ActiveHostingMode.FTP
                    else -> current.activeHostingMode
                }
                val preserveCompanion = targetMode == ActiveHostingMode.FTP && current.isSyncPlayCompanionActive
                val castingPort = current.casting.port.takeIf { it > 0 } ?: DEFAULT_CASTING_PORT

                stopCastingInternal(graceful = true)
                stopHttpInternal(graceful = true, clearSyncPlay = !preserveCompanion)
                stopFtpInternal(graceful = true, clearSyncPlay = !preserveCompanion)
                delay(120L)
                refreshIpAddress()

                _state.update {
                    it.copy(
                        activeHostingMode = targetMode,
                        isSyncPlayCompanionActive = preserveCompanion
                    )
                }
                when (targetMode) {
                    ActiveHostingMode.FTP -> {
                        startFtpInternal(appContext)
                        if (preserveCompanion) startHttpInternal(appContext)
                    }
                    ActiveHostingMode.HTTP -> startHttpInternal(appContext)
                    ActiveHostingMode.CASTING -> startCastingInternal(appContext, castingPort)
                    ActiveHostingMode.NONE -> Unit
                }
                updateHubTimestampAndServiceState(appContext)
                if (_state.value.isAnyRunning) {
                    ensureHealthMonitorRunning(appContext)
                }
            }
        }
    }

    // --- EXCLUSIVE HTTP HOSTING CONTROLS ---

    fun startHttpServer(context: Context) {
        activateExclusiveMode(context, ActiveHostingMode.HTTP)
    }

    fun stopHttpServer(context: Context? = null, graceful: Boolean = true) {
        val appContext = context?.applicationContext
        managerScope.launch {
            coordinationMutex.withLock {
                stopHttpInternal(graceful = graceful, clearSyncPlay = true)
                if (_state.value.activeHostingMode == ActiveHostingMode.HTTP) {
                    _state.update { it.copy(activeHostingMode = ActiveHostingMode.NONE) }
                } else {
                    _state.update { it.copy(isSyncPlayCompanionActive = false) }
                }
                updateHubTimestampAndServiceState(appContext)
            }
        }
    }

    fun restartHttpServer(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            coordinationMutex.withLock {
                stopCastingInternal(graceful = true)
                stopFtpInternal(graceful = true, clearSyncPlay = true)
                stopHttpInternal(graceful = true, clearSyncPlay = true)
                delay(100L)
                refreshIpAddress()
                _state.update {
                    it.copy(
                        activeHostingMode = ActiveHostingMode.HTTP,
                        isSyncPlayCompanionActive = false
                    )
                }
                startHttpInternal(appContext)
                if (!_state.value.http.isRunning) {
                    _state.update { it.copy(activeHostingMode = ActiveHostingMode.NONE) }
                }
                updateHubTimestampAndServiceState(appContext)
                if (_state.value.isAnyRunning) {
                    ensureHealthMonitorRunning(appContext)
                }
            }
        }
    }

    // --- EXCLUSIVE FTP HOSTING CONTROLS ---

    fun startFtpServer(context: Context, withSyncPlayCompanion: Boolean = false) {
        activateExclusiveMode(context, ActiveHostingMode.FTP, withSyncPlayCompanion = withSyncPlayCompanion)
    }

    fun stopFtpServer(context: Context? = null, graceful: Boolean = true) {
        val appContext = context?.applicationContext
        managerScope.launch {
            coordinationMutex.withLock {
                val hadCompanion = _state.value.isSyncPlayCompanionActive
                stopFtpInternal(graceful = graceful, clearSyncPlay = true)
                if (hadCompanion) {
                    stopHttpInternal(graceful = graceful, clearSyncPlay = true)
                }
                if (_state.value.activeHostingMode == ActiveHostingMode.FTP) {
                    _state.update {
                        it.copy(
                            activeHostingMode = ActiveHostingMode.NONE,
                            isSyncPlayCompanionActive = false
                        )
                    }
                }
                updateHubTimestampAndServiceState(appContext)
            }
        }
    }

    fun restartFtpServer(context: Context) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            coordinationMutex.withLock {
                val keepCompanion = _state.value.isSyncPlayCompanionActive
                stopCastingInternal(graceful = true)
                stopFtpInternal(graceful = true, clearSyncPlay = !keepCompanion)
                if (!keepCompanion && _state.value.http.isRunning) {
                    stopHttpInternal(graceful = true, clearSyncPlay = true)
                }
                delay(100L)
                refreshIpAddress()
                _state.update {
                    it.copy(
                        activeHostingMode = ActiveHostingMode.FTP,
                        isSyncPlayCompanionActive = keepCompanion
                    )
                }
                startFtpInternal(appContext)
                if (keepCompanion && !_state.value.http.isRunning) {
                    startHttpInternal(appContext)
                }
                if (!_state.value.ftp.isRunning) {
                    _state.update {
                        it.copy(
                            activeHostingMode = ActiveHostingMode.NONE,
                            isSyncPlayCompanionActive = false
                        )
                    }
                }
                updateHubTimestampAndServiceState(appContext)
                if (_state.value.isAnyRunning) {
                    ensureHealthMonitorRunning(appContext)
                }
            }
        }
    }

    // --- EXCLUSIVE OMNIOS CASTING CONTROLS ---

    fun startCastingServer(context: Context, port: Int = DEFAULT_CASTING_PORT) {
        activateExclusiveMode(context, ActiveHostingMode.CASTING, castingPort = port)
    }

    fun stopCastingServer(context: Context? = null, graceful: Boolean = true) {
        val appContext = context?.applicationContext
        managerScope.launch {
            coordinationMutex.withLock {
                stopCastingInternal(graceful = graceful)
                if (_state.value.activeHostingMode == ActiveHostingMode.CASTING) {
                    _state.update { it.copy(activeHostingMode = ActiveHostingMode.NONE) }
                }
                updateHubTimestampAndServiceState(appContext)
            }
        }
    }

    fun restartCastingServer(context: Context, port: Int = DEFAULT_CASTING_PORT) {
        val appContext = context.applicationContext
        ContinuumHubService.ensureForegroundServiceRunning(appContext)
        managerScope.launch {
            coordinationMutex.withLock {
                stopHttpInternal(graceful = true, clearSyncPlay = false)
                stopFtpInternal(graceful = true, clearSyncPlay = false)
                stopCastingInternal(graceful = true)
                delay(100L)
                refreshIpAddress()
                _state.update {
                    it.copy(
                        activeHostingMode = ActiveHostingMode.CASTING,
                        isSyncPlayCompanionActive = false
                    )
                }
                startCastingInternal(appContext, port)
                if (!_state.value.casting.isRunning) {
                    _state.update { it.copy(activeHostingMode = ActiveHostingMode.NONE) }
                }
                updateHubTimestampAndServiceState(appContext)
                if (_state.value.isAnyRunning) {
                    ensureHealthMonitorRunning(appContext)
                }
            }
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
                ContinuumHubService.recordLog("127.0.0.1", "HTTP", "START", "HTTP Hosting online on :$DEFAULT_HTTP_PORT")
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

    private suspend fun stopHttpInternal(graceful: Boolean, clearSyncPlay: Boolean = true) {
        if (clearSyncPlay) {
            SyncPlayManager.stopSession(clearMediaHistory = true)
        }
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
                ContinuumHubService.recordLog("127.0.0.1", "HTTP", "STOP", "HTTP Hosting stopped cleanly")
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
                ContinuumHubService.recordLog("127.0.0.1", "FTP", "START", "FTP Hosting online on :$ftpPort")
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

    private suspend fun stopFtpInternal(graceful: Boolean, clearSyncPlay: Boolean = true) {
        if (clearSyncPlay) {
            SyncPlayManager.stopSession(clearMediaHistory = true)
        }
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
                ContinuumHubService.recordLog("127.0.0.1", "FTP", "STOP", "FTP Hosting stopped cleanly")
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

    private suspend fun startCastingInternal(appContext: Context, port: Int) {
        castingMutex.withLock {
            val bridge = castingBridge
            val safePort = port.coerceIn(1024, 65535)
            if (bridge == null) {
                val errMsg = "OmniOS Casting engine is not registered yet"
                _state.update {
                    it.copy(
                        casting = it.casting.copy(
                            state = ProtocolLifecycleState.ERROR,
                            port = safePort,
                            errorMessage = errMsg
                        ),
                        lastGlobalError = errMsg
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
                return
            }

            if (bridge.isRunning) {
                return
            }

            _state.update {
                it.copy(
                    casting = it.casting.copy(
                        state = ProtocolLifecycleState.STARTING,
                        port = safePort,
                        errorMessage = null
                    )
                )
            }

            try {
                bridge.startOrThrow(appContext, safePort)
                val now = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        casting = ProtocolServerStatus(
                            protocolName = "OmniOS",
                            state = ProtocolLifecycleState.RUNNING,
                            port = safePort,
                            startedAtMillis = now,
                            activeConnections = bridge.getActiveConnectionsCount(),
                            hasActiveTransfers = bridge.getActiveConnectionsCount() > 0,
                            errorMessage = null
                        ),
                        lastGlobalError = null
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
                ContinuumHubService.recordLog("127.0.0.1", "CAST", "START", "OmniOS Casting online on :$safePort")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start OmniOS Casting server on port $safePort", e)
                val errMsg = e.localizedMessage ?: "Failed to bind OmniOS port $safePort"
                _state.update {
                    it.copy(
                        casting = it.casting.copy(
                            state = ProtocolLifecycleState.ERROR,
                            port = safePort,
                            errorMessage = errMsg
                        ),
                        lastGlobalError = "Casting Error: $errMsg"
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
            }
        }
    }

    private suspend fun stopCastingInternal(graceful: Boolean) {
        castingMutex.withLock {
            val bridge = castingBridge
            val currentPort = _state.value.casting.port.takeIf { it > 0 } ?: DEFAULT_CASTING_PORT
            if (bridge == null || !bridge.isRunning) {
                _state.update {
                    it.copy(
                        casting = ProtocolServerStatus(
                            protocolName = "OmniOS",
                            state = ProtocolLifecycleState.STOPPED,
                            port = currentPort
                        )
                    )
                }
                ContinuumHubService.syncLegacyStaticFieldsFromManager(_state.value)
                return
            }

            _state.update {
                it.copy(casting = it.casting.copy(state = ProtocolLifecycleState.STOPPING))
            }

            try {
                bridge.stop(graceful = graceful)
                ContinuumHubService.recordLog("127.0.0.1", "CAST", "STOP", "OmniOS Casting stopped cleanly")
            } catch (e: Throwable) {
                Log.w(TAG, "Exception while stopping OmniOS Casting server: ${e.message}")
            } finally {
                _state.update {
                    it.copy(
                        casting = ProtocolServerStatus(
                            protocolName = "OmniOS",
                            state = ProtocolLifecycleState.STOPPED,
                            port = currentPort
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
        val uploadDropzone = File(SettingsManager.ftpUploadFolderPath.value).apply {
            if (!exists()) mkdirs()
        }
        return when (hostedMode) {
            FtpHostedMode.CUSTOM_FOLDER -> {
                if (customPath.isNotBlank()) {
                    val f = File(customPath)
                    if (f.exists()) (if (f.isDirectory) f else f.parentFile ?: uploadDropzone)
                    else uploadDropzone
                } else {
                    uploadDropzone
                }
            }
            FtpHostedMode.BATCH_FILES -> {
                val items = FtpBucketManager.bucketItems
                when {
                    items.size == 1 && items.first().isDirectory -> {
                        File(items.first().absolutePath).takeIf { it.exists() } ?: uploadDropzone
                    }
                    items.isNotEmpty() -> {
                        val firstParent = File(items.first().absolutePath).parentFile
                        firstParent?.takeIf { it.exists() } ?: uploadDropzone
                    }
                    else -> uploadDropzone
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
                val castConn = castingBridge?.getActiveConnectionsCount() ?: 0

                val ftpAlive = ftpServerInstance?.isRunning ?: false
                val updatedFtpState = if (s.ftp.state == ProtocolLifecycleState.RUNNING && !ftpAlive) {
                    ProtocolLifecycleState.ERROR
                } else {
                    s.ftp.state
                }

                val castAlive = castingBridge?.isRunning ?: false
                val updatedCastState = if (s.casting.state == ProtocolLifecycleState.RUNNING && !castAlive) {
                    ProtocolLifecycleState.ERROR
                } else {
                    s.casting.state
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
                        ),
                        casting = current.casting.copy(
                            state = updatedCastState,
                            activeConnections = castConn,
                            hasActiveTransfers = castConn > 0,
                            errorMessage = if (updatedCastState == ProtocolLifecycleState.ERROR) "OmniOS host stopped unexpectedly" else current.casting.errorMessage
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
