package me.huanjue.cloudmail

import android.app.Application
import android.content.Context
import me.huanjue.cloudmail.data.AppSettings
import me.huanjue.cloudmail.data.AuthRepository
import me.huanjue.cloudmail.data.CapabilitiesRepository
import me.huanjue.cloudmail.data.DraftRepository
import me.huanjue.cloudmail.data.MailCache
import me.huanjue.cloudmail.data.MailRepository
import me.huanjue.cloudmail.data.PgpManager

class CloudMailApp : Application() {

    lateinit var container: AppContainer
        private set

    companion object {
        // 全局 application context（已应用用户语言），供 data 层获取字符串资源
        @Volatile
        lateinit var appContext: Context
            private set
    }

    override fun attachBaseContext(base: Context) {
        // 应用用户选择的语言（同步读 SharedPreferences 镜像）
        val lang = try {
            base.getSharedPreferences("cloudmail_prefs_sync", Context.MODE_PRIVATE)
                .getString("language", "system") ?: "system"
        } catch (_: Exception) { "system" }
        super.attachBaseContext(me.huanjue.cloudmail.ui.theme.LocaleHelper.wrap(base, lang))
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        container = AppContainer(this)
        // 预热 WebView：提前初始化 Chromium 引擎，打开邮件详情更快
        try {
            android.webkit.WebView.setDataDirectorySuffix("main")
        } catch (_: Exception) {}
        Thread {
            try {
                val wv = android.webkit.WebView(this)
                wv.settings.javaScriptEnabled = false
                wv.destroy()
            } catch (_: Exception) {}
        }.start()
    }
}

/** 手动 DI 容器 */
class AppContainer(context: android.content.Context) {
    val settings = AppSettings(context.applicationContext)
    val authRepository = AuthRepository(settings)
    val mailRepository = MailRepository()
    val mailCache = MailCache(context.applicationContext)
    val draftRepository = DraftRepository(context.applicationContext)
    val pgpManager = PgpManager(context.applicationContext, settings)
    val capabilitiesRepository = CapabilitiesRepository(settings)
}
