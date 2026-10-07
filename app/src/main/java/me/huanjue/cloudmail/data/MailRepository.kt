package me.huanjue.cloudmail.data

import me.huanjue.cloudmail.data.api.NetworkModule
import me.huanjue.cloudmail.data.api.unwrap
import me.huanjue.cloudmail.data.model.EmailItem
import me.huanjue.cloudmail.data.model.EmailListData
import me.huanjue.cloudmail.data.model.IdListRequest
import me.huanjue.cloudmail.data.model.MailAccount
import me.huanjue.cloudmail.data.model.PgpKeyStatus
import me.huanjue.cloudmail.data.model.SendEmailRequest
import okhttp3.OkHttpClient
import okhttp3.Request

class MailRepository {

    private val api get() = NetworkModule.api
    private val downloadClient by lazy { OkHttpClient() }

    /**
     * 下载附件。/oss/ 路径匿名可访问，URL 为 {SERVER}/oss/<key>（不带 /api 前缀）。
     */
    suspend fun downloadAttachment(serverUrl: String, key: String): ByteArray =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = serverUrl.trim().trimEnd('/') + "/oss/" + key.trimStart('/')
            val req = Request.Builder().url(url).get().build()
            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("下载失败：HTTP ${resp.code}")
                resp.body?.bytes() ?: throw Exception("下载内容为空")
            }
        }

    suspend fun accounts(): List<MailAccount> =
        unwrap { api.accountList() }

    /**
     * 邮件列表（游标分页）。
     * @param cursorId 上一页最后一条的 emailId；timeSort=1 时传第一条的
     * @param timeSort 0=倒序（默认） 1=正序
     */
    suspend fun emails(
        accountId: Long,
        cursorId: Long? = null,
        timeSort: Int = 0,
        size: Int = 20,
        type: Int = 0,
        full: Int = 0
    ): EmailListData {
        val params = mutableMapOf(
            "accountId" to accountId.toString(),
            "timeSort" to timeSort.toString(),
            "size" to size.toString(),
            "type" to type.toString(),
            "full" to full.toString()
        )
        cursorId?.let { params["emailId"] = it.toString() }
        return unwrap { api.emailList(params) }
    }

    /**
     * 取单封邮件全文。后端游标是开区间（lt/gt），没有按 ID 查单封的接口，
     * 用 emailId-1 + 正序 + size=1 的技巧拿到目标邮件（含 content）。
     */
    suspend fun emailDetail(accountId: Long, emailId: Long, type: Int = 0): EmailItem? {
        val data = emails(
            accountId = accountId,
            cursorId = emailId - 1,
            timeSort = 1,
            size = 1,
            type = type,
            full = 1
        )
        return data.list?.firstOrNull { it.emailId == emailId }
            ?: data.list?.firstOrNull()
    }

    suspend fun markRead(emailIds: List<Long>) {
        if (emailIds.isEmpty()) return
        unwrap { api.markRead(IdListRequest(emailIds)) }
    }

    suspend fun deleteEmails(emailIds: List<Long>) {
        if (emailIds.isEmpty()) return
        unwrap { api.deleteEmails(emailIds.joinToString(",")) }
    }

    suspend fun sendEmail(request: SendEmailRequest) {
        unwrap { api.sendEmail(request) }
    }

    /** 星标列表（游标分页，倒序）。full=1 时带 accountId（打开详情用）。 */
    suspend fun starredEmails(
        cursorId: Long? = null,
        size: Int = 20,
        full: Int = 0
    ): List<EmailItem> {
        val params = mutableMapOf(
            "size" to size.toString(),
            "full" to full.toString()
        )
        cursorId?.let { params["emailId"] = it.toString() }
        return unwrap { api.starList(params) }.list ?: emptyList()
    }

    suspend fun starAdd(emailId: Long) {
        unwrap { api.starAdd(mapOf("emailId" to emailId)) }
    }

    suspend fun starCancel(emailId: Long) {
        unwrap { api.starCancel(emailId) }
    }

    /** 查收件人是否有 PGP 公钥（写信页 PGP 开关用） */
    suspend fun pgpKeyStatus(email: String): PgpKeyStatus =
        unwrap { api.pgpKeyStatus(email) }
}
