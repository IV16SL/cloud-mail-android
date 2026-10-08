package me.huanjue.cloudmail.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.huanjue.cloudmail.data.model.UserSession

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cloudmail")

/** 本地持久化：服务器地址 + 多账号登录会话 */
class AppSettings(private val context: Context) {

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val TOKEN = stringPreferencesKey("token") // 旧版单 token，迁移用
        private val SESSIONS = stringPreferencesKey("user_sessions")
        private val ACTIVE_USER = longPreferencesKey("active_user_id")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val LANGUAGE = stringPreferencesKey("language")
    }

    /** 语言：system（跟随系统，默认）、en、zh、zh-TW、ja、ko */
    val language: Flow<String> = context.dataStore.data.map { it[LANGUAGE] ?: "system" }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[LANGUAGE] = lang }
    }

    /** 同步读取语言（用于 attachBaseContext，不能用 suspend） */
    fun getLanguageSync(): String {
        return try {
            val prefs = context.getSharedPreferences("cloudmail_prefs_sync", Context.MODE_PRIVATE)
            prefs.getString("language", null)
                ?: runBlockingRead()
        } catch (_: Exception) { "system" }
    }

    private fun runBlockingRead(): String {
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.map { it[LANGUAGE] ?: "system" }.first()
            }
        } catch (_: Exception) { "system" }
    }

    /** 语言变更时同步一份到 SharedPreferences，供 attachBaseContext 快速读取 */
    suspend fun setLanguageWithSync(lang: String) {
        setLanguage(lang)
        try {
            context.getSharedPreferences("cloudmail_prefs_sync", Context.MODE_PRIVATE)
                .edit().putString("language", lang).apply()
        } catch (_: Exception) {}
    }

    /** 主题模式：system（跟随系统，默认）、light、dark */
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun getThemeMode(): String = themeMode.first()

    /** 主题色跟随系统（Material You 动态取色，默认关闭，用图标蓝） */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    private val gson = Gson()

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[SERVER_URL] }

    // ---------- 多账号会话 ----------

    private fun readSessions(json: String?): List<UserSession> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, Array<UserSession>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val sessions: Flow<List<UserSession>> =
        context.dataStore.data.map { readSessions(it[SESSIONS]) }

    val activeSession: Flow<UserSession?> = context.dataStore.data.map { prefs ->
        val list = readSessions(prefs[SESSIONS])
        val activeId = prefs[ACTIVE_USER]
        list.find { it.userId == activeId } ?: list.firstOrNull()
    }

    suspend fun getSessions(): List<UserSession> = sessions.first()

    suspend fun getActiveSession(): UserSession? = activeSession.first()

    /** 新增或更新会话，并设为活动账号 */
    suspend fun saveSession(session: UserSession) {
        context.dataStore.edit { prefs ->
            val list = readSessions(prefs[SESSIONS])
                .filter { it.userId != session.userId } + session
            prefs[SESSIONS] = gson.toJson(list)
            prefs[ACTIVE_USER] = session.userId
            prefs.remove(TOKEN)
        }
    }

    suspend fun setActiveUser(userId: Long): Boolean {
        var ok = false
        context.dataStore.edit { prefs ->
            if (readSessions(prefs[SESSIONS]).any { it.userId == userId }) {
                prefs[ACTIVE_USER] = userId
                ok = true
            }
        }
        return ok
    }

    /** 删除会话；返回新的活动会话，没有剩余则返回 null */
    suspend fun removeSession(userId: Long): UserSession? {
        var next: UserSession? = null
        context.dataStore.edit { prefs ->
            val list = readSessions(prefs[SESSIONS]).filter { it.userId != userId }
            prefs[SESSIONS] = gson.toJson(list)
            next = if (list.isEmpty()) {
                prefs.remove(ACTIVE_USER)
                null
            } else {
                val keep = list.find { it.userId == prefs[ACTIVE_USER] } ?: list.first()
                prefs[ACTIVE_USER] = keep.userId
                keep
            }
        }
        return next
    }

    suspend fun clearSessions() {
        context.dataStore.edit {
            it.remove(SESSIONS)
            it.remove(ACTIVE_USER)
            it.remove(TOKEN)
        }
    }

    /** 旧版单 token（未迁移时） */
    suspend fun getLegacyToken(): String? =
        context.dataStore.data.map { it[TOKEN] }.first()

    // ---------- 服务器 ----------

    suspend fun getServerUrl(): String? = serverUrl.first()

    suspend fun setServerUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = normalized
            // 换服务器后所有会话作废
            prefs.remove(SESSIONS)
            prefs.remove(ACTIVE_USER)
            prefs.remove(TOKEN)
        }
    }

    // ---------- 旧版单 token 兼容（已废弃，保留给迁移逻辑） ----------

    @Deprecated("多账号改用 sessions")
    suspend fun getToken(): String? = getLegacyToken()

    @Deprecated("多账号改用 saveSession")
    suspend fun setToken(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(TOKEN) else prefs[TOKEN] = value
        }
    }

    @Deprecated("多账号改用 clearSessions")
    suspend fun clearToken() {
        context.dataStore.edit { it.remove(TOKEN) }
    }

    /** 非 UI 层（ViewModel/Repository）获取本地化字符串 */
    fun getString(resId: Int, vararg args: Any): String {
        return try {
            if (args.isEmpty()) context.getString(resId)
            else context.getString(resId, *args)
        } catch (_: Exception) { "" }
    }
}

/** 服务器地址格式校验：https 开头（本地调试放行 http://localhost 与 10.0.2.2） */
fun isValidServerUrl(input: String): Boolean {
    val url = input.trim().trimEnd('/')
    if (url.isEmpty()) return false
    return url.startsWith("https://") ||
        url.startsWith("http://localhost") ||
        url.startsWith("http://10.0.2.2") ||
        url.startsWith("http://127.0.0.1")
}
