package me.huanjue.cloudmail.data.model

import com.google.gson.annotations.SerializedName

/** 统一响应包：{ code, message, data } */
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null
) {
    val isOk: Boolean get() = code == 200
}

/** 业务异常：code != 200 时抛出，UI 层直接展示 message */
class ApiException(val code: Int, override val message: String) : Exception(message)

// ---------- 登录 ----------

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginData(
    val token: String? = null,
    val needTotp: Boolean? = null,
    val preAuthToken: String? = null
)

data class TotpLoginRequest(
    val preAuthToken: String,
    val code: String? = null,
    val recoveryCode: String? = null
)

/** POST /passkey/login/options 返回：WebAuthn 请求选项 + challengeId */
data class PasskeyLoginOptionsData(
    val options: Map<String, Any?>? = null,
    val challengeId: String? = null
)

/** POST /passkey/login/verify 请求体 */
data class PasskeyLoginVerifyRequest(
    val response: Map<String, Any?>,
    val challengeId: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val code: String? = null,
    val token: String? = null
)

data class WebsiteConfig(
    val register: Int = 0,
    val title: String? = null,
    val regKey: Int = 0,
    val loginDomain: Int = 0,
    val domainList: List<String>? = null,
    val minEmailPrefix: Int = 0,
    val notice: Int = 0,
    val noticeTitle: String? = null,
    val noticeContent: String? = null
)

// ---------- 用户 ----------

data class UserInfo(
    val userId: Long = 0,
    val email: String = "",
    val name: String? = null,
    val role: RoleInfo? = null,
    val permKeys: List<String>? = null,
    val avatar: String? = null
) {
    fun hasPerm(key: String): Boolean = permKeys?.contains(key) == true
}

/** 本地多账号会话（同一服务器下的多个登录用户） */
data class UserSession(
    val userId: Long = 0,
    val username: String = "",
    val token: String = ""
)

data class RoleInfo(
    val roleId: Long = 0,
    val name: String? = null
)

// ---------- 邮件 ----------

data class EmailItem(
    val emailId: Long = 0,
    val accountId: Long = 0,
    val sendEmail: String? = null,
    val name: String? = null,
    val subject: String? = null,
    val recipient: String? = null,
    val toEmail: String? = null,
    val type: Int = 0,          // 0=收件 1=发件
    val status: Int = 0,        // 0=接收中 1=已发送 2=已送达 3=退信 4=被投诉 5=延迟 6=保存中
    val unread: Int = 0,        // 注意：0=未读 1=已读（后端 emailConst.unread）
    val createTime: String? = null,
    val text: String? = null,   // 摘要（截断300字）
    val listText: String? = null, // 列表实际返回的摘要字段（后端会删掉 text/content）
    val content: String? = null,// full=1 时才有，HTML
    val cc: String? = null,     // JSON 数组字符串
    val bcc: String? = null,    // JSON 数组字符串
    val code: String? = null,   // 正文识别出的验证码
    val starId: Any? = null,
    val isStar: Int = 0,      // 后端返回 1=已星标 0=未星标
    val attList: List<Attachment> = emptyList()  // 附件列表（详情接口返回）
) {
    val isUnread: Boolean get() = unread == 0
}

/** 附件（收） */
data class Attachment(
    val attId: Long = 0,
    val key: String = "",        // oss 对象名，下载拼 {SERVER}/oss/<key>
    val filename: String = "",
    val size: Long = 0,
    val contentType: String? = null
)

/** 附件（发）：content 为 base64 */
data class SendAttachment(
    val content: String,         // base64
    val filename: String,
    val size: Long,
    val contentType: String
)

data class EmailListData(
    val list: List<EmailItem>? = null,
    val total: Int = 0,
    val latestEmail: LatestEmailInfo? = null
)

/** /star/list 返回 {list: [...]} */
data class StarListData(
    val list: List<EmailItem>? = null
)

data class LatestEmailInfo(
    val emailId: Long = 0,
    val accountId: Long = 0,
    val userId: Long = 0
)

data class IdListRequest(
    val emailIds: List<Long>
)

data class SendEmailRequest(
    val accountId: Long,
    val name: String? = null,
    val sendType: String = "",           // ""=新邮件 "reply"=回复 "forward"=转发
    val receiveEmail: List<String> = emptyList(),
    val ccEmail: List<String> = emptyList(),
    val bccEmail: List<String> = emptyList(),
    val subject: String = "",
    val text: String = "",
    val content: String = "",
    val emailId: Long? = null,           // 回复/转发时带原邮件 ID
    val pgpEncrypt: Boolean = false,
    val attachments: List<SendAttachment> = emptyList()
)

// ---------- 邮件账号 ----------

data class MailAccount(
    val accountId: Long = 0,
    val email: String = "",
    val name: String? = null,
    val status: Int = 0,
    val allReceive: Int = 0,
    val sort: Int = 0
)

data class AccountAddRequest(
    val email: String,
    val token: String? = null
)

data class AccountNameRequest(
    val accountId: Long,
    val name: String
)

// ---------- PGP ----------

/** 收件人公钥状态：GET /pgp/key-status?email= */
data class PgpKeyStatus(
    val email: String = "",
    val found: Boolean = false,
    val fingerprint: String? = null
)

// ---------- 服务器能力探测 ----------

/**
 * 后端能力：GET /api/capabilities（fork 独有）。
 * 主仓库没有这个接口，404 时按全 false 处理（基础功能）。
 */
data class ServerCapabilities(
    val passkey: Boolean = false,
    val totp: Boolean = false,
    val pgp: Boolean = false
) {
    companion object {
        /** 上游主仓库默认：无 fork 独有功能 */
        val UPSTREAM = ServerCapabilities()
    }
}
