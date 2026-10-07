package me.huanjue.cloudmail.data.api

import android.os.Handler
import android.os.Looper
import me.huanjue.cloudmail.data.model.ApiException
import me.huanjue.cloudmail.data.model.ApiResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 网络层：
 * - Base URL 不写死。用户在登录页填写服务器地址后，所有请求经拦截器替换 scheme/host/port。
 * - token 以裸字符串放在 `Authorization` 请求头（无 Bearer 前缀）。
 * - 收到 401 时回调 onUnauthorized（切回登录页），在主线程执行。
 */
object NetworkModule {

    @Volatile
    var serverUrl: String = ""

    /**
     * API 路径前缀。cloud-mail 约定的生产部署把 API 挂在 `/api` 下
     *（前端 VITE_BASE_URL='/api'），但也有直接把 worker 挂根路径的部署，
     * 因此首次配置服务器时自动探测，见 detectApiPrefix。
     */
    @Volatile
    var apiPrefix: String = ""

    @Volatile
    var token: String? = null

    var onUnauthorized: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val base = serverUrl.trim().trimEnd('/').toHttpUrlOrNull()
                val request = if (base != null) {
                    val newPath = (apiPrefix.trimEnd('/') + "/" +
                        original.url.encodedPath.trimStart('/'))
                    val newUrl = original.url.newBuilder()
                        .scheme(base.scheme)
                        .host(base.host)
                        .port(base.port)
                        .encodedPath(newPath)
                        .build()
                    original.newBuilder().url(newUrl).build()
                } else {
                    original
                }
                val builder = request.newBuilder()
                token?.let { builder.header("Authorization", it) }
                chain.proceed(builder.build())
            }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401) {
                    mainHandler.post { onUnauthorized?.invoke() }
                }
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://placeholder.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private val probeClient by lazy { OkHttpClient() }

    /**
     * 探测服务器的 API 前缀：依次试 `/api` 与根路径，
     * 用公开接口 `/setting/websiteConfig` 判定（返回 {code,message,data} 即命中）。
     * @return 命中的前缀（"/api" 或 ""）
     * @throws IllegalStateException 都连不上时抛出
     */
    suspend fun detectApiPrefix(server: String): String = withContext(Dispatchers.IO) {
        val base = server.trim().trimEnd('/')
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        for (prefix in listOf("/api", "")) {
            try {
                val req = Request.Builder()
                    .url("$base$prefix/setting/websiteConfig")
                    .get()
                    .build()
                probeClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    // 命中 API：JSON 包里有 code 字段（静态前端回退的 index.html 没有）
                    val map: Map<String, Any?>? = try {
                        gson.fromJson<Map<String, Any?>>(body, mapType)
                    } catch (_: Exception) {
                        null
                    }
                    if (map?.containsKey("code") == true) return@withContext prefix
                }
            } catch (_: Exception) {
            }
        }
        throw IllegalStateException("连接不到服务器的 API，请检查地址是否正确")
    }
}

/** 解包统一响应体：code != 200 抛 ApiException */
suspend fun <T> unwrap(call: suspend () -> ApiResponse<T>): T {
    val resp = call()
    if (!resp.isOk) throw ApiException(resp.code, resp.message ?: "请求失败")
    return resp.data as T
}
