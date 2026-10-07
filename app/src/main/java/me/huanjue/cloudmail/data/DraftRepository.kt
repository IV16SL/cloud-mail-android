package me.huanjue.cloudmail.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** 本地草稿（无后端接口，和网页版一样只存本地），按登录用户隔离 */
data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val userId: Long = 0,
    val accountId: Long = 0,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun isEmpty(): Boolean =
        to.isBlank() && cc.isBlank() && bcc.isBlank() &&
            subject.isBlank() && body.isBlank()
}

class DraftRepository(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private val DRAFTS = stringPreferencesKey("drafts")
    }

    private val gson = Gson()
    private val listType = object : TypeToken<List<Draft>>() {}.type

    val drafts: Flow<List<Draft>> = appContext.dataStore.data.map { prefs ->
        val json = prefs[DRAFTS] ?: return@map emptyList<Draft>()
        try {
            gson.fromJson<List<Draft>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(draft: Draft) {
        appContext.dataStore.edit { prefs ->
            val current = readAll(prefs[DRAFTS])
            val updated = (listOf(draft) + current.filter { it.id != draft.id })
                .sortedByDescending { it.updatedAt }
            prefs[DRAFTS] = gson.toJson(updated)
        }
    }

    suspend fun delete(id: String) {
        appContext.dataStore.edit { prefs ->
            val updated = readAll(prefs[DRAFTS]).filter { it.id != id }
            prefs[DRAFTS] = gson.toJson(updated)
        }
    }

    private fun readAll(json: String?): List<Draft> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<Draft>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
