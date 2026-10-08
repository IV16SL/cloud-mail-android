package me.huanjue.cloudmail.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * 语言切换：把 Context 的 locale 换成用户选择的语言。
 * 支持：system（跟随系统）、en、zh、zh-TW、ja、ko
 */
object LocaleHelper {

    fun wrap(context: Context, language: String): Context {
        if (language == "system") return context
        val locale = when (language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "zh-TW" -> Locale.TRADITIONAL_CHINESE
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "en" -> Locale.ENGLISH
            else -> return context
        }
        return wrapWithLocale(context, locale)
    }

    private fun wrapWithLocale(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }

    /** 语言代码 → 显示名称（用对应语言的自称） */
    fun displayName(code: String): String = when (code) {
        "system" -> "🌐" // 占位，实际显示用 stringResource
        "en" -> "English"
        "zh" -> "简体中文"
        "zh-TW" -> "繁體中文"
        "ja" -> "日本語"
        "ko" -> "한국어"
        else -> code
    }

    val supported = listOf("system", "en", "zh", "zh-TW", "ja", "ko")
}
