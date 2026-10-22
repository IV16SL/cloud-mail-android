package me.huanjue.cloudmail.ui.login

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通行密钥（Passkey）Credential Manager 封装。
 *
 * 流程：服务端下发 PublicKeyCredentialRequestOptions JSON →
 * Credential Manager 弹出系统 UI 让用户选择通行密钥 →
 * 返回 authenticationResponseJson 给服务端校验。
 */
object PasskeyHelper {

    /**
     * 获取 passkey 断言。
     * @param activity 用于弹出系统凭证选择界面的 Activity（需在主线程调用 getCredential）
     * @param requestJson 服务端下发的 PublicKeyCredentialRequestOptions JSON 字符串
     * @return 凭证的 authenticationResponseJson（可直接发给服务端）
     * @throws androidx.credentials.exceptions.GetCredentialCancellationException 用户取消
     * @throws androidx.credentials.exceptions.NoCredentialException 设备上无可用通行密钥
     * @throws androidx.credentials.exceptions.GetCredentialException 其他失败
     */
    suspend fun getAssertion(activity: Activity, requestJson: String): String =
        withContext(Dispatchers.Main) {
            val credentialManager = CredentialManager.create(activity)
            // 登录按钮场景：不要静默直取，必须弹账号选择 UI（Android 14+ 默认的
            // immediately-available 流程在无即时凭证时会直接失败，不走选择界面）
            val option = GetPublicKeyCredentialOption(
                requestJson,
                preferImmediatelyAvailableCredentials = false
            )
            val request = GetCredentialRequest(listOf(option))
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is PublicKeyCredential) {
                credential.authenticationResponseJson
            } else {
                throw kotlin.IllegalStateException(me.huanjue.cloudmail.CloudMailApp.appContext.getString(me.huanjue.cloudmail.R.string.login_passkey_bad_type, credential.type))
            }
        }
}
