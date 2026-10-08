package me.huanjue.cloudmail.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.data.api.NetworkModule
import me.huanjue.cloudmail.data.api.unwrap
import me.huanjue.cloudmail.data.model.ServerCapabilities

/**
 * 服务器能力探测。
 *
 * fork 后端提供 GET /api/capabilities，主仓库没有（404），按全 false 处理。
 * serverUrl 变化时自动重新探测。
 */
class CapabilitiesRepository(
    private val settings: AppSettings,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _capabilities = MutableStateFlow(ServerCapabilities.UPSTREAM)
    val capabilities: StateFlow<ServerCapabilities> = _capabilities.asStateFlow()

    init {
        scope.launch {
            settings.serverUrl.collectLatest { url ->
                _capabilities.value = if (url.isNullOrBlank()) {
                    ServerCapabilities.UPSTREAM
                } else {
                    fetch(url)
                }
            }
        }
    }

    private suspend fun fetch(url: String): ServerCapabilities {
        return try {
            // NetworkModule.api 会根据当前 serverUrl 动态替换 baseUrl
            unwrap { NetworkModule.api.getCapabilities() }
        } catch (_: Exception) {
            // 404（主仓库）、网络错误等，一律按上游基础功能处理
            ServerCapabilities.UPSTREAM
        }
    }

    /** 手动刷新（如下拉设置页） */
    suspend fun refresh() {
        val url = settings.getServerUrl()
        _capabilities.value = if (url.isNullOrBlank()) {
            ServerCapabilities.UPSTREAM
        } else {
            fetch(url)
        }
    }

    /**
     * 为指定 URL 探测 capabilities（不修改共享状态）。
     * 用于登录/添加账号页：输入框里的地址还没保存，不能依赖 settings.serverUrl。
     */
    suspend fun fetchForUrl(url: String): ServerCapabilities {
        if (url.isBlank()) return ServerCapabilities.UPSTREAM
        return try {
            val base = url.trim().trimEnd('/')
            // 先试 /api 前缀（生产环境），再试根路径
            for (prefix in listOf("/api", "")) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url("$base$prefix/public/capabilities")
                        .get()
                        .build()
                    okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        val json = com.google.gson.Gson().fromJson(body, Map::class.java)
                        val data = json["data"] as? Map<*, *>
                        if (data != null) {
                            return ServerCapabilities(
                                passkey = data["passkey"] as? Boolean ?: false,
                                totp = data["totp"] as? Boolean ?: false,
                                pgp = data["pgp"] as? Boolean ?: false
                            )
                        }
                    }
                } catch (_: Exception) { /* 试下一个前缀 */ }
            }
            ServerCapabilities.UPSTREAM
        } catch (_: Exception) {
            ServerCapabilities.UPSTREAM
        }
    }
}
