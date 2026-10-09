package me.huanjue.cloudmail.data.api

import me.huanjue.cloudmail.data.model.*
import retrofit2.http.*

/**
 * 后端接口（Cloudflare Workers + Hono 纯 REST）。
 * Base URL 由用户在登录页右上角设置里填写，通过拦截器动态替换，见 NetworkModule。
 */
interface ApiService {

    // ---------- 服务器能力探测（fork 独有，public） ----------

    @GET("public/capabilities")
    suspend fun getCapabilities(): ApiResponse<ServerCapabilities>

    // ---------- 登录注册（public） ----------

    @POST("login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginData>

    @POST("login/totp")
    suspend fun loginTotp(@Body body: TotpLoginRequest): ApiResponse<LoginData>

    @POST("register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<Map<String, Any>>

    @GET("setting/websiteConfig")
    suspend fun websiteConfig(): ApiResponse<WebsiteConfig>

    // ---------- 登录后 ----------

    @DELETE("logout")
    suspend fun logout(): ApiResponse<Any>

    @GET("my/loginUserInfo")
    suspend fun loginUserInfo(): ApiResponse<UserInfo>

    // ---------- 邮件 ----------

    @GET("email/list")
    suspend fun emailList(@QueryMap params: Map<String, String>): ApiResponse<EmailListData>

    @GET("email/latest")
    suspend fun emailLatest(@QueryMap params: Map<String, String>): ApiResponse<List<EmailItem>>

    @PUT("email/read")
    suspend fun markRead(@Body body: IdListRequest): ApiResponse<Any>

    @DELETE("email/delete")
    suspend fun deleteEmails(@Query("emailIds") emailIds: String): ApiResponse<Any>

    @PUT("email/restore")
    suspend fun restoreEmails(@Body body: IdListRequest): ApiResponse<Any>

    @DELETE("email/permanent")
    suspend fun permanentDeleteEmails(@Query("emailIds") emailIds: String): ApiResponse<Any>

    @POST("email/send")
    suspend fun sendEmail(@Body body: SendEmailRequest): ApiResponse<List<EmailItem>>

    // ---------- 邮件账号 ----------

    @GET("account/list")
    suspend fun accountList(): ApiResponse<List<MailAccount>>

    @POST("account/add")
    suspend fun accountAdd(@Body body: AccountAddRequest): ApiResponse<MailAccount>

    @DELETE("account/delete")
    suspend fun accountDelete(@Query("accountId") accountId: Long): ApiResponse<Any>

    @PUT("account/setName")
    suspend fun accountSetName(@Body body: AccountNameRequest): ApiResponse<Any>

    // ---------- 星标 ----------

    @GET("star/list")
    suspend fun starList(@QueryMap params: Map<String, String>): ApiResponse<StarListData>

    @POST("star/add")
    suspend fun starAdd(@Body body: Map<String, Long>): ApiResponse<Any>

    @DELETE("star/cancel")
    suspend fun starCancel(@Query("emailId") emailId: Long): ApiResponse<Any>

    // ---------- 通行密钥（Passkey）登录 ----------

    @POST("passkey/login/options")
    suspend fun passkeyLoginOptions(): ApiResponse<PasskeyLoginOptionsData>

    @POST("passkey/login/verify")
    suspend fun passkeyLoginVerify(@Body body: PasskeyLoginVerifyRequest): ApiResponse<LoginData>

    // ---------- PGP ----------

    /** 查收件人公钥状态（服务端经 WKD → keys.openpgp.org 探测） */
    @GET("pgp/key-status")
    suspend fun pgpKeyStatus(@Query("email") email: String): ApiResponse<PgpKeyStatus>

    /** 上传公钥并刷新服务端缓存（App 生成钥匙后调用） */
    @POST("pgp/key-upload")
    suspend fun pgpKeyUpload(@Body body: Map<String, String>): ApiResponse<Map<String, Any>>
}
