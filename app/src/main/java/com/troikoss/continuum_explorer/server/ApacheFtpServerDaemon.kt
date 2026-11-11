package com.troikoss.continuum_explorer.server

import android.util.Log
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.impl.DefaultFtpServer
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.TransferRatePermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated Apache Mina FtpServer daemon.
 * Keeps FTP protocol logic, passive ports, user manager, and Ftplet telemetry self-contained
 * while exposing clean lifecycle hooks for [ServerManager].
 */
class ApacheFtpServerDaemon(
    val port: Int,
    private val rootDirectory: File,
    private val allowAnonymous: Boolean,
    private val username: String,
    private val password: String,
    private val allowWrite: Boolean
) {
    private var ftpServer: FtpServer? = null
    private val activeTransfers = AtomicInteger(0)
    private val connectedSessions = AtomicInteger(0)

    val isRunning: Boolean
        get() = ftpServer?.let { !it.isStopped && !it.isSuspended } == true

    fun getActiveConnectionsCount(): Int {
        return try {
            val impl = ftpServer as? DefaultFtpServer
            val listener = impl?.listeners?.values?.firstOrNull()
            listener?.activeSessions?.size ?: connectedSessions.get().coerceAtLeast(0)
        } catch (_: Exception) {
            connectedSessions.get().coerceAtLeast(0)
        }
    }

    fun hasActiveTransfers(): Boolean = activeTransfers.get() > 0

    /**
     * Starts the Apache FTP server or throws an exception if the port is unavailable or invalid.
     */
    @Throws(Exception::class)
    fun startOrThrow() {
        if (isRunning) return

        val serverFactory = FtpServerFactory()
        val listenerFactory = ListenerFactory()
        listenerFactory.port = port

        // Configure passive data ports for reliable firewall/NAT traversal on LAN
        val dataConfigFactory = DataConnectionConfigurationFactory()
        dataConfigFactory.passivePorts = "50000-50100"
        listenerFactory.dataConnectionConfiguration = dataConfigFactory.createDataConnectionConfiguration()

        serverFactory.addListener("default", listenerFactory.createListener())

        // Register Ftplet to track connected FTP clients, active file transfers, and activity logs
        val ftplets = LinkedHashMap<String, org.apache.ftpserver.ftplet.Ftplet>()
        ftplets["continuum_logger"] = object : org.apache.ftpserver.ftplet.DefaultFtplet() {
            override fun onConnect(session: FtpSession): FtpletResult {
                connectedSessions.incrementAndGet()
                val ip = session.clientAddress?.address?.hostAddress ?: "unknown"
                ContinuumHubService.recordLog(ip, "FTP", "CONNECT", "Session opened")
                return super.onConnect(session)
            }

            override fun onDisconnect(session: FtpSession): FtpletResult {
                connectedSessions.updateAndGet { (it - 1).coerceAtLeast(0) }
                val ip = session.clientAddress?.address?.hostAddress ?: "unknown"
                ContinuumHubService.recordLog(ip, "FTP", "DISCONNECT", "Session closed")
                return super.onDisconnect(session)
            }

            override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult {
                val ip = session.clientAddress?.address?.hostAddress ?: "unknown"
                val cmd = request.command?.uppercase() ?: ""
                val arg = request.argument ?: ""
                if (cmd in listOf("RETR", "STOR", "APPE")) {
                    activeTransfers.incrementAndGet()
                }
                if (cmd in listOf("USER", "RETR", "STOR", "DELE", "MKD", "RMD", "RNTO", "LIST", "MLSD")) {
                    val safeArg = if (cmd == "PASS") "*****" else arg
                    ContinuumHubService.recordLog(ip, "FTP", cmd, safeArg)
                }
                return super.beforeCommand(session, request)
            }

            override fun afterCommand(
                session: FtpSession,
                request: FtpRequest,
                reply: FtpReply
            ): FtpletResult {
                val cmd = request.command?.uppercase() ?: ""
                if (cmd in listOf("RETR", "STOR", "APPE")) {
                    activeTransfers.updateAndGet { (it - 1).coerceAtLeast(0) }
                }
                return super.afterCommand(session, request, reply)
            }
        }
        serverFactory.ftplets = ftplets

        // Setup User Manager
        val userManagerFactory = PropertiesUserManagerFactory()
        val userManager: UserManager = userManagerFactory.createUserManager()

        val authorities = mutableListOf<Authority>()
        if (allowWrite) {
            authorities.add(WritePermission())
        }
        authorities.add(ConcurrentLoginPermission(20, 10))
        authorities.add(TransferRatePermission(0, 0))

        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        }

        // Configure Authenticated User
        if (username.isNotBlank()) {
            val authUser = BaseUser().apply {
                name = username
                this.password = this@ApacheFtpServerDaemon.password
                homeDirectory = rootDirectory.absolutePath
                enabled = true
                this.authorities = authorities
            }
            userManager.save(authUser)
        }

        // Configure Anonymous User
        val anonUser = BaseUser().apply {
            name = "anonymous"
            homeDirectory = rootDirectory.absolutePath
            enabled = allowAnonymous
            this.authorities = authorities
        }
        userManager.save(anonUser)

        serverFactory.userManager = userManager

        try {
            val server = serverFactory.createServer()
            server.start()
            ftpServer = server
            Log.i("ApacheFtpServerDaemon", "FTP Server started on port $port serving ${rootDirectory.absolutePath}")
        } catch (e: FtpException) {
            ftpServer = null
            Log.e("ApacheFtpServerDaemon", "Failed to start FTP Server on port $port", e)
            throw e
        } catch (e: IOException) {
            ftpServer = null
            Log.e("ApacheFtpServerDaemon", "IOException starting FTP Server on port $port", e)
            throw e
        }
    }

    fun start() {
        try {
            startOrThrow()
        } catch (e: Exception) {
            Log.e("ApacheFtpServerDaemon", "Start error: ${e.message}", e)
        }
    }

    /**
     * Gracefully stops the FTP server, waiting up to [gracePeriodMs] if an active RETR/STOR
     * transfer is in progress before closing the socket listener.
     */
    fun stop(gracePeriodMs: Long = 1500L) {
        val server = ftpServer ?: return
        try {
            if (gracePeriodMs > 0L && activeTransfers.get() > 0) {
                // Suspend new incoming connections first while allowing active transfer a brief window
                try {
                    server.suspend()
                } catch (_: Exception) {}
                val deadline = System.currentTimeMillis() + gracePeriodMs
                while (activeTransfers.get() > 0 && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(75L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            server.stop()
            Log.i("ApacheFtpServerDaemon", "FTP Server stopped cleanly")
        } catch (e: Exception) {
            Log.e("ApacheFtpServerDaemon", "Error stopping FTP Server", e)
        } finally {
            ftpServer = null
            activeTransfers.set(0)
            connectedSessions.set(0)
        }
    }
}
