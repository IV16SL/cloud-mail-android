package me.huanjue.cloudmail.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.data.AppSettings
import me.huanjue.cloudmail.data.AuthRepository
import me.huanjue.cloudmail.data.LoginResult
import me.huanjue.cloudmail.data.api.NetworkModule
import me.huanjue.cloudmail.data.isValidServerUrl
import me.huanjue.cloudmail.data.model.ApiException
import me.huanjue.cloudmail.data.model.WebsiteConfig

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class NeedTotp(val preAuthToken: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val settings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _websiteConfig = MutableStateFlow<WebsiteConfig?>(null)
    val websiteConfig: StateFlow<WebsiteConfig?> = _websiteConfig.asStateFlow()

    /** 登录成功后置 true，界面侧导航到邮箱列表（消费一次） */
    private val _loginDone = MutableStateFlow(false)
    val loginDone: StateFlow<Boolean> = _loginDone.asStateFlow()

    fun consumeLoginDone() {
        _loginDone.value = false
    }

    init {
        viewModelScope.launch {
            settings.serverUrl.collect { url ->
                _serverUrl.value = url ?: ""
                if (!url.isNullOrBlank()) {
                    NetworkModule.serverUrl = url
                    loadWebsiteConfig()
                }
            }
        }
    }

    private fun loadWebsiteConfig() {
        viewModelScope.launch {
            try {
                _websiteConfig.value = authRepository.websiteConfig()
            } catch (_: Exception) {
                // 公开配置拉不到不阻塞登录
            }
        }
    }

    fun saveServerUrl(input: String, onResult: (Boolean, String?) -> Unit) {
        if (!isValidServerUrl(input)) {
            onResult(false, settings.getString(me.huanjue.cloudmail.R.string.login_server_invalid))
            return
        }
        viewModelScope.launch {
            try {
                val normalized = input.trim().trimEnd('/')
                // 先探测 API 前缀（/api 或根路径），连不上就直接报错
                val prefix = NetworkModule.detectApiPrefix(normalized)
                settings.setServerUrl(normalized)
                NetworkModule.serverUrl = normalized
                NetworkModule.apiPrefix = prefix
                NetworkModule.token = null
                _websiteConfig.value = null
                loadWebsiteConfig()
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun login(email: String, password: String) {
        if (_serverUrl.value.isBlank()) {
            _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.login_need_server))
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                when (val result = authRepository.login(email.trim(), password)) {
                    is LoginResult.Success -> _loginDone.value = true
                    is LoginResult.NeedTotp -> _uiState.value = LoginUiState.NeedTotp(result.preAuthToken)
                }
            } catch (e: ApiException) {
                _uiState.value = LoginUiState.Error(e.message)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.common_network_error, e.message ?: ""))
            }
        }
    }

    fun loginTotp(preAuthToken: String, code: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                authRepository.loginTotp(preAuthToken, code.trim())
                _loginDone.value = true
            } catch (e: ApiException) {
                _uiState.value = LoginUiState.Error(e.message)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.common_network_error, e.message ?: ""))
            }
        }
    }

    /**
     * 通行密钥登录。
     * @param activity 用于弹出 Credential Manager 系统界面的 Activity
     * @param capabilitiesPasskey 服务端是否支持 passkey（由界面层传入）
     */
    fun loginPasskey(activity: android.app.Activity, capabilitiesPasskey: Boolean) {
        if (!capabilitiesPasskey) {
            _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.login_passkey_unsupported))
            return
        }
        if (_serverUrl.value.isBlank()) {
            _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.login_need_server))
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val result = authRepository.loginPasskey { requestJson ->
                    PasskeyHelper.getAssertion(activity, requestJson)
                }
                when (result) {
                    is LoginResult.Success -> _loginDone.value = true
                    is LoginResult.NeedTotp -> _uiState.value =
                        LoginUiState.NeedTotp(result.preAuthToken)
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                // 用户主动取消，不打扰
                _uiState.value = LoginUiState.Idle
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.login_passkey_none))
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.login_passkey_unavailable, e.message ?: ""))
            } catch (e: ApiException) {
                _uiState.value = LoginUiState.Error(e.message)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(settings.getString(me.huanjue.cloudmail.R.string.login_passkey_failed, e.message ?: ""))
            }
        }
    }

    fun backToIdle() {
        _uiState.value = LoginUiState.Idle
    }
}
