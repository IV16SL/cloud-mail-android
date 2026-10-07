package me.huanjue.cloudmail

import android.app.Application
import me.huanjue.cloudmail.data.AppSettings
import me.huanjue.cloudmail.data.AuthRepository
import me.huanjue.cloudmail.data.CapabilitiesRepository
import me.huanjue.cloudmail.data.DraftRepository
import me.huanjue.cloudmail.data.MailRepository
import me.huanjue.cloudmail.data.PgpManager

class CloudMailApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 手动 DI 容器 */
class AppContainer(context: android.content.Context) {
    val settings = AppSettings(context.applicationContext)
    val authRepository = AuthRepository(settings)
    val mailRepository = MailRepository()
    val draftRepository = DraftRepository(context.applicationContext)
    val pgpManager = PgpManager(context.applicationContext, settings)
    val capabilitiesRepository = CapabilitiesRepository(settings)
}
