package me.huanjue.cloudmail.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.huanjue.cloudmail.data.model.EmailItem

/**
 * 邮件列表头缓存：冷启动秒开。
 *
 * 策略：
 * - 按 accountId + type（0=收件箱 1=已发送）分别缓存前 25 封邮件头
 * - App 启动时先读缓存立刻展示，无 loading 转圈
 * - 展示缓存的同时后台静默拉取最新，拿到后更新 UI 并覆盖缓存
 * - 搜索、切换账号时不走缓存（直接走网络）
 */
class MailCache(private val context: Context) {

    companion object {
        private const val MAX_CACHED = 25
        private fun key(accountId: Long, type: Int) =
            stringPreferencesKey("mail_cache_${accountId}_${type}")
    }

    private val gson = Gson()
    private val listType = object : TypeToken<List<EmailItem>>() {}.type

    /** 读缓存，没有返回 null */
    suspend fun get(accountId: Long, type: Int): List<EmailItem>? {
        return try {
            val json = context.dataStore.data
                .map { it[key(accountId, type)] }
                .first()
                ?: return null
            val list: List<EmailItem> = gson.fromJson(json, listType) ?: return null
            list.take(MAX_CACHED).ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /** 写缓存（只存前 25 封） */
    suspend fun put(accountId: Long, type: Int, emails: List<EmailItem>) {
        try {
            val json = gson.toJson(emails.take(MAX_CACHED))
            context.dataStore.edit { it[key(accountId, type)] = json }
        } catch (_: Exception) {
            // 缓存失败不影响主流程
        }
    }

    /** 清指定账号的缓存（登出/删账号时调用） */
    suspend fun clear(accountId: Long) {
        try {
            context.dataStore.edit {
                it.remove(key(accountId, 0))
                it.remove(key(accountId, 1))
            }
        } catch (_: Exception) { }
    }
}
