package me.huanjue.cloudmail.data

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import me.huanjue.cloudmail.data.api.NetworkModule
import me.huanjue.cloudmail.data.api.unwrap
import me.huanjue.cloudmail.data.model.LoginData
import me.huanjue.cloudmail.data.model.LoginRequest
import me.huanjue.cloudmail.data.model.RegisterRequest
import me.huanjue.cloudmail.data.model.TotpLoginRequest
import me.huanjue.cloudmail.data.model.UserInfo
import me.huanjue.cloudmail.data.model.UserSession
import me.huanjue.cloudmail.data.model.WebsiteConfig

sealed interface LoginResult {
    data class Success(val token: String) : LoginResult
    data class NeedTotp(val preAuthToken: String) : LoginResult
}

class AuthRepository(private val settings: AppSettings) {

    private val api get() = NetworkModule.api
    private val gson = Gson()

    val sessions: Flow<List<UserSession>> = settings.sessions
    val activeSession: Flow<UserSession?> = settings.activeSession

    /** 登录：成功返回 token；用户开了 TOTP 则返回 NeedTotp 走第二步 */
    suspend fun login(email: String, password: String): LoginResult {
        val data: LoginData = unwrap { api.login(LoginRequest(email, password)) }
        if (data.needTotp == true && data.preAuthToken != null) {
            return LoginResult.NeedTotp(data.preAuthToken)
        }
        val token = data.token ?: throw kotlin.IllegalStateException(me.huanjue.cloudmail.CloudMailApp.appContext.getString(me.huanjue.cloudmail.R.string.err_login_no_token))
        return finishLogin(token)
    }

    /** TOTP 登录第二步 */
    suspend fun loginTotp(preAuthToken: String, code: String): LoginResult {
        val data: LoginData = unwrap { api.loginTotp(TotpLoginRequest(preAuthToken, code = code)) }
        val token = data.token ?: throw kotlin.IllegalStateException(me.huanjue.cloudmail.CloudMailApp.appContext.getString(me.huanjue.cloudmail.R.string.err_login_no_token))
        return finishLogin(token)
    }

    /**
     * 通行密钥（Passkey）登录。
     * @param getAssertion 由调用方用 Credential Manager 获取凭证：
     *   传入服务端下发的 PublicKeyCredentialRequestOptions JSON，
     *   返回凭证的 authenticationResponseJson
     */
    suspend fun loginPasskey(
        getAssertion: suspend (requestJson: String) -> String
    ): LoginResult {
        // 1. 拿 challenge（服务端同时返回 challengeId 用于校验）
        val optionsData: PasskeyLoginOptionsData = unwrap { api.passkeyLoginOptions() }
        val challengeId = optionsData.challengeId
            ?: throw kotlin.IllegalStateException(me.huanjue.cloudmail.CloudMailApp.appContext.getString(me.huanjue.cloudmail.R.string.err_no_challenge_id))
        val options = optionsData.options
            ?: throw kotlin.IllegalStateException(me.huanjue.cloudmail.CloudMailApp.appContext.getString(me.huanjue.cloudmail.R.string.err_no_options))
        // 2. options 转 JSON 给 Credential Manager
        val requestJson = gson.toJson(options)
        // 3. Credential Manager 获取凭证（调用方实现，抛异常则直接向上传）
        val assertionJson = getAssertion(requestJson)
        // 4. 解析为 Map 发给服务端校验
        @Suppress("UNCHECKED_CAST")
        val responseMap = gson.fromJson(assertionJson, Map::class.java) as Map<String, Any?>
        val data: LoginData = unwrap {
            api.passkeyLoginVerify(PasskeyLoginVerifyRequest(responseMap, challengeId))
        }
        val token = data.token ?: throw kotlin.IllegalStateException(me.huanjue.cloudmail.CloudMailApp.appContext.getString(me.huanjue.cloudmail.R.string.err_login_no_token))
        return finishLogin(token)
    }

    /** 拿 token 换用户信息，存为多账号会话并设为活动账号 */
    private suspend fun finishLogin(token: String): LoginResult {
        NetworkModule.token = token
        val info = unwrap { api.loginUserInfo() }
        settings.saveSession(
            UserSession(
                userId = info.userId,
                username = info.email.ifBlank { info.name ?: "" },
                token = token
            )
        )
        return LoginResult.Success(token)
    }

    suspend fun register(email: String, password: String, inviteCode: String?): Boolean {
        unwrap { api.register(RegisterRequest(email, password, inviteCode)) }
        return true
    }

    suspend fun websiteConfig(): WebsiteConfig =
        unwrap { api.websiteConfig() }

    suspend fun loginUserInfo(): UserInfo =
        unwrap { api.loginUserInfo() }

    /**
     * 退出当前账号（服务端注销 + 删除本地会话）。
     * 还有其他已登录账号时自动切换过去，返回 true；没有剩余时返回 false。
     */
    suspend fun logout(): Boolean {
        val active = settings.getActiveSession()
        try {
            // 注意：服务端注销必须用旧 token，所以放在换 token 之前
            unwrap { api.logout() }
        } catch (_: Exception) {
            // 服务端注销失败也不阻塞本地登出
        }
        if (active != null) {
            // 先算出下一个会话并换好 token，再删旧会话：
            // 删除会触发 activeSession 的监听者（邮箱列表/星标页）立即重载，
            // token 必须在那之前就是新的，否则请求会带着刚注销掉的旧 token 发出，
            // 撞上 401 还会连锁把新会话也删掉。
            val next = settings.getSessions()
                .filter { it.userId != active.userId }
                .firstOrNull()
            NetworkModule.token = next?.token
            settings.removeSession(active.userId)
            return next != null
        }
        settings.clearSessions()
        NetworkModule.token = null
        return false
    }

    /** 切换账号 */
    suspend fun switchSession(userId: Long): Boolean {
        // 先把 token 换成目标账号的，再写 DataStore：
        // activeSession 的监听者（邮箱列表/星标/草稿）在 DataStore 一更新就立即重载，
        // 而拦截器是按请求实时读 NetworkModule.token 的，顺序反了重载就会用旧 token 发出；
        // 更糟的是一次 refresh 里的多个请求可能混用新旧 token（旧 token 查账号列表、
        // 新 token 查邮件），导致列表空白或串号。
        val target = settings.getSessions().find { it.userId == userId } ?: return false
        NetworkModule.token = target.token
        return settings.setActiveUser(userId)
    }

    /** App 启动时恢复会话：有活动会话直接用（含 API 前缀探测）；旧版单 token 自动迁移 */
    suspend fun restoreSession(): Boolean {
        val serverUrl = settings.getServerUrl()
        if (serverUrl.isNullOrBlank()) return false
        return try {
            NetworkModule.serverUrl = serverUrl
            NetworkModule.apiPrefix = NetworkModule.detectApiPrefix(serverUrl)
            val active = settings.getActiveSession()
            if (active != null) {
                NetworkModule.token = active.token
                true
            } else {
                // 旧版单 token：先保证能进，顺手尝试迁移成会话
                val legacy = settings.getLegacyToken()
                if (legacy.isNullOrBlank()) return false
                NetworkModule.token = legacy
                try {
                    val info = unwrap { api.loginUserInfo() }
                    settings.saveSession(
                        UserSession(
                            userId = info.userId,
                            username = info.email.ifBlank { info.name ?: "" },
                            token = legacy
                        )
                    )
                } catch (_: Exception) {
                    // 迁移失败不阻塞本次启动，下次再试
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
