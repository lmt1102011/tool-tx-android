package com.lmt.tooltx.bridge

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class PythonBridge(private val context: Context) {

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    private val py by lazy { Python.getInstance() }

    fun login(username: String, password: String): Map<String, Any?> {
        return try {
            val result = py.getModule("auth").callAttr("login", username, password)
            val uid = result.callAttr("__getitem__", "uid").toString()
            val data = parseMap(result.callAttr("__getitem__", "data"))
            mapOf("ok" to true, "uid" to uid, "data" to data)
        } catch (e: Exception) {
            mapOf("ok" to false, "error" to e.message)
        }
    }

    fun register(username: String, password: String, name: String): Map<String, Any?> {
        return try {
            py.getModule("auth").callAttr("register", username, password, name)
            mapOf("ok" to true)
        } catch (e: Exception) {
            mapOf("ok" to false, "error" to e.message)
        }
    }

    fun setSessionPath(path: String) {
        try {
            py.getModule("auth").callAttr("set_session_path", path)
        } catch (_: Exception) {}
    }

    fun logout() {
        try {
            py.getModule("auth").callAttr("logout")
        } catch (_: Exception) {}
    }

    fun getSession(): Map<String, Any?>? {
        return try {
            val s = py.getModule("auth").callAttr("get_session")
            if (s == null) null else parseMap(s)
        } catch (_: Exception) {
            null
        }
    }

    fun refreshToken(): String? {
        return try {
            val t = py.getModule("auth").callAttr("refresh_token")
            if (t == null) null else t.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun forceRefreshToken(): String? {
        return try {
            val t = py.getModule("auth").callAttr("force_refresh_token")
            if (t == null) null else t.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun getLastPanel(): Map<String, Any?> {
        return try {
            val p = py.getModule("socket_client").callAttr("last_panel")
            if (p == null) mapOf() else parseMap(p)
        } catch (_: Exception) {
            mapOf()
        }
    }

    fun getLastKick(): String? {
        return try {
            val k = py.getModule("socket_client").callAttr("last_kick")
            if (k == null) null else k.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun clearKick() {
        try {
            py.getModule("socket_client").callAttr("clear_kick")
        } catch (_: Exception) {}
    }

    fun isLoggedIn(): Boolean {
        return try {
            py.getModule("auth").callAttr("is_logged_in").toBoolean()
        } catch (_: Exception) {
            false
        }
    }

    fun getUserData(): Map<String, Any?> {
        return try {
            val data = py.getModule("auth").callAttr("user_data")
            parseMap(data)
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    fun getPicks(): Int {
        return try {
            py.getModule("fb").callAttr("picks", py.getModule("auth").callAttr("user_data")).toInt()
        } catch (_: Exception) {
            -1
        }
    }

    fun connectSocket(url: String, token: String) {
        try {
            py.getModule("socket_client").callAttr("connect", url, token)
            writeBugLog("main", "connectSocket: $url")
        } catch (e: Exception) {
            writeBugLog("main", "connectSocket FAIL: ${e.message}")
        }
    }

    fun disconnectSocket() {
        try {
            py.getModule("socket_client").callAttr("disconnect")
        } catch (_: Exception) {}
    }

    fun isSocketConnected(): Boolean {
        return try {
            py.getModule("socket_client").callAttr("is_connected").toBoolean()
        } catch (_: Exception) {
            false
        }
    }

    fun discoverServer(): String? {
        return try {
            val url = py.getModule("config").callAttr("discover_server")
            if (url == null) null else url.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun getBugLog(): String {
        return try {
            val t = py.getModule("applog").callAttr("dump")
            if (t == null) "" else t.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun clearBugLog() {
        try {
            py.getModule("applog").callAttr("clear")
        } catch (_: Exception) {}
    }

    fun writeBugLog(tag: String, msg: String) {
        try {
            py.getModule("applog").callAttr("log", tag, msg)
        } catch (_: Exception) {}
    }

    fun startFork(serverUrl: String, code: String): Boolean {
        return try {
            py.getModule("agent").callAttr("start_fork", serverUrl, code).toBoolean()
        } catch (_: Exception) {
            false
        }
    }

    fun startAgent(serverUrl: String, code: String): Boolean {
        return try {
            py.getModule("agent").callAttr("start_agent", serverUrl, code).toBoolean()
        } catch (e: Exception) {
            writeBugLog("main", "startAgent FAIL: ${e.message}")
            false
        }
    }

    fun stopAgent() {
        try {
            py.getModule("agent").callAttr("stop")
        } catch (_: Exception) {}
    }

    fun agentStatus(): Map<String, Any?> {
        return try {
            parseMap(py.getModule("agent").callAttr("status"))
        } catch (_: Exception) {
            mapOf()
        }
    }

    fun getAgentPair(serverUrl: String): String? {
        return try {
            val code = py.getModule("socket_client").callAttr("agent_pair", serverUrl)
            if (code == null) null else code.toString()
        } catch (e: Exception) {
            writeBugLog("main", "getAgentPair FAIL: ${e.message}")
            null
        }
    }

    fun listUsers(): Map<String, Any?> {
        return try {
            val users = py.getModule("auth").callAttr("list_users")
            parseMap(users)
        } catch (_: Exception) {
            mapOf()
        }
    }

    fun getRate(): Int {
        return try {
            py.getModule("auth").callAttr("get_rate").toInt()
        } catch (_: Exception) {
            5000
        }
    }

    fun setRate(rate: Int) {
        try {
            py.getModule("auth").callAttr("set_rate", rate)
        } catch (_: Exception) {}
    }

    fun addUser(username: String, password: String, picks: Int, role: String = "user") {
        try {
            py.getModule("auth").callAttr("register_with_picks", username, password, "", picks, role)
        } catch (_: Exception) {}
    }

    fun updateBalance(uid: String, balance: Int) {
        try {
            py.getModule("auth").callAttr("update_balance", uid, balance)
        } catch (_: Exception) {}
    }

    fun updateRole(uid: String, role: String) {
        try {
            py.getModule("auth").callAttr("update_role", uid, role)
        } catch (_: Exception) {}
    }

    fun deleteUser(uid: String) {
        try {
            py.getModule("auth").callAttr("delete_user", uid)
        } catch (_: Exception) {}
    }

    private fun parseMap(obj: com.chaquo.python.PyObject?): Map<String, Any?> {
        if (obj == null) return mapOf()
        return try {
            val src = obj.asMap()
            val result = mutableMapOf<String, Any?>()
            for ((k, v) in src) {
                result[k.toString()] = v
            }
            result
        } catch (_: Exception) {
            mapOf()
        }
    }
}
