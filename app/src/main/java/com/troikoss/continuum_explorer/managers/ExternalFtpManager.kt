package com.troikoss.continuum_explorer.managers

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class SavedFtpServer(
    val id: String,
    val name: String,
    val host: String,
    val ftpPort: String = "2121",
    val webPort: String = "8080",
    val isAnonymous: Boolean = true,
    val username: String = "",
    val isConnected: Boolean = false
)

object ExternalFtpManager {
    private const val PREFS_NAME = "external_ftp_prefs"
    private const val KEY_SERVERS = "saved_ftp_servers"

    val recentServers = mutableStateListOf<SavedFtpServer>()

    fun init(context: Context) {
        if (recentServers.isNotEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SERVERS, null)
        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    recentServers.add(
                        SavedFtpServer(
                            id = obj.optString("id", System.currentTimeMillis().toString()),
                            name = obj.optString("name", "FTP Server"),
                            host = obj.optString("host", ""),
                            ftpPort = obj.optString("ftpPort", "2121"),
                            webPort = obj.optString("webPort", "8080"),
                            isAnonymous = obj.optBoolean("isAnonymous", true),
                            username = obj.optString("username", ""),
                            isConnected = false // Disconnected by default on fresh start
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun persist(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        recentServers.forEach { server ->
            val obj = JSONObject().apply {
                put("id", server.id)
                put("name", server.name)
                put("host", server.host)
                put("ftpPort", server.ftpPort)
                put("webPort", server.webPort)
                put("isAnonymous", server.isAnonymous)
                put("username", server.username)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    fun addOrUpdateServer(context: Context, server: SavedFtpServer) {
        val existingIndex = recentServers.indexOfFirst { it.id == server.id || (it.host == server.host && it.ftpPort == server.ftpPort) }
        if (existingIndex >= 0) {
            recentServers[existingIndex] = server
        } else {
            recentServers.add(0, server)
        }
        persist(context)
    }

    fun setConnectionStatus(id: String, isConnected: Boolean) {
        val index = recentServers.indexOfFirst { it.id == id }
        if (index >= 0) {
            val curr = recentServers[index]
            recentServers[index] = curr.copy(isConnected = isConnected)
        }
    }

    fun disconnectAll() {
        for (i in recentServers.indices) {
            if (recentServers[i].isConnected) {
                recentServers[i] = recentServers[i].copy(isConnected = false)
            }
        }
    }

    fun removeServer(context: Context, id: String) {
        recentServers.removeAll { it.id == id }
        persist(context)
    }
}
