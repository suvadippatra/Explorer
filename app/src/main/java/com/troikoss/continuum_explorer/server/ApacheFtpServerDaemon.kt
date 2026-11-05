package com.troikoss.continuum_explorer.server

import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File

/**
 * ApacheFtpServerDaemon
 *
 * Runs an embedded Apache FtpServer daemon on port 2121.
 * Provides anonymous or authenticated FTP access to the device's external storage.
 */
class ApacheFtpServerDaemon(
    private val context: Context,
    private val port: Int = 2121,
    private val isAnonymousAllowed: Boolean = true,
    private val username: String = "explorer",
    private val password: String = "admin",
    private val allowWritePermission: Boolean = true,
    private val rootDirectory: File = Environment.getExternalStorageDirectory()
) {
    companion object {
        private const val TAG = "ApacheFtpServerDaemon"
    }

    private var server: FtpServer? = null

    fun start() {
        if (server != null) return

        try {
            val serverFactory = FtpServerFactory()
            val listenerFactory = ListenerFactory()

            listenerFactory.port = port
            serverFactory.addListener("default", listenerFactory.createListener())

            // Configure storage root directory
            val storageRoot = if (rootDirectory.exists()) rootDirectory else Environment.getExternalStorageDirectory()
            val userManager = serverFactory.userManager

            // Setup anonymous user if allowed
            if (isAnonymousAllowed) {
                val anonUser = BaseUser().apply {
                    name = "anonymous"
                    homeDirectory = storageRoot.absolutePath
                    val authorities = ArrayList<Authority>()
                    if (allowWritePermission) {
                        authorities.add(WritePermission())
                    }
                    setAuthorities(authorities)
                }
                userManager.save(anonUser)
            }

            // Setup authenticated user
            val authUser = BaseUser().apply {
                name = username.ifBlank { "explorer" }
                password = password.ifBlank { "admin" }
                homeDirectory = storageRoot.absolutePath
                val authorities = ArrayList<Authority>()
                if (allowWritePermission) {
                    authorities.add(WritePermission())
                }
                setAuthorities(authorities)
            }
            userManager.save(authUser)

            val ftpServer = serverFactory.createServer()
            ftpServer.start()
            server = ftpServer

            Log.i(TAG, "Apache FTP Server daemon started on port $port (Anon: $isAnonymousAllowed, User: ${authUser.name}) serving ${storageRoot.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Apache FTP Server daemon", e)
        }
    }

    fun stop() {
        try {
            server?.stop()
            server = null
            Log.i(TAG, "Apache FTP Server daemon stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Apache FTP Server daemon", e)
        }
    }
}
