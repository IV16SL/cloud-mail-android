package me.huanjue.cloudmail.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.data.AppSettings
import me.huanjue.cloudmail.data.Draft
import me.huanjue.cloudmail.data.DraftRepository
import me.huanjue.cloudmail.data.MailRepository
import me.huanjue.cloudmail.data.model.ApiException
import me.huanjue.cloudmail.data.model.MailAccount
import me.huanjue.cloudmail.data.model.SendAttachment
import me.huanjue.cloudmail.data.model.SendEmailRequest

sealed interface ComposeUiState {
    data object Idle : ComposeUiState
    data object Sending : ComposeUiState
    data object Sent : ComposeUiState
    data class Error(val message: String) : ComposeUiState
}

class ComposeViewModel(
    private val mailRepository: MailRepository,
    private val draftRepository: DraftRepository,
    private val settings: AppSettings,
    private val draftId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComposeUiState>(ComposeUiState.Idle)
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    private val _accounts = MutableStateFlow<List<MailAccount>>(emptyList())
    val accounts: StateFlow<List<MailAccount>> = _accounts.asStateFlow()

    private val _editingDraft = MutableStateFlow<Draft?>(null)
    val editingDraft: StateFlow<Draft?> = _editingDraft.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _accounts.value = mailRepository.accounts()
            } catch (_: Exception) {
            }
        }
        if (draftId != null) {
            viewModelScope.launch {
                _editingDraft.value =
                    draftRepository.drafts.first().find { it.id == draftId }
            }
        }
    }

    fun send(
        accountId: Long,
        to: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String,
        pgpEncrypt: Boolean = false,
        attachments: List<SendAttachment> = emptyList()
    ) {
        val recipients = parseAddresses(to)
        if (recipients.isEmpty()) {
            _uiState.value = ComposeUiState.Error("收件人不能为空")
            return
        }
        _uiState.value = ComposeUiState.Sending
        viewModelScope.launch {
            try {
                val text = body.trim()
                mailRepository.sendEmail(
                    SendEmailRequest(
                        accountId = accountId,
                        sendType = "",
                        receiveEmail = recipients,
                        ccEmail = parseAddresses(cc),
                        bccEmail = parseAddresses(bcc),
                        subject = subject.trim(),
                        text = text,
                        content = text.split("\n").joinToString("<br>"),
                        pgpEncrypt = pgpEncrypt,
                        attachments = attachments
                    )
                )
                // 发送成功后删掉正在编辑的草稿
                _editingDraft.value?.let { draftRepository.delete(it.id) }
                _uiState.value = ComposeUiState.Sent
            } catch (e: ApiException) {
                _uiState.value = ComposeUiState.Error(e.message)
            } catch (e: Exception) {
                _uiState.value = ComposeUiState.Error("网络错误：${e.message}")
            }
        }
    }

    /** 返回时调用：有内容就存草稿 */
    fun saveDraftIfNeeded(
        accountId: Long?,
        to: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String
    ) {
        val draft = Draft(
            id = _editingDraft.value?.id ?: java.util.UUID.randomUUID().toString(),
            accountId = accountId ?: 0,
            to = to.trim(),
            cc = cc.trim(),
            bcc = bcc.trim(),
            subject = subject.trim(),
            body = body,
            updatedAt = System.currentTimeMillis()
        )
        if (draft.isEmpty()) return
        viewModelScope.launch {
            val userId = settings.getActiveSession()?.userId ?: 0
            draftRepository.save(draft.copy(userId = userId))
        }
    }

    fun backToIdle() {
        _uiState.value = ComposeUiState.Idle
    }

    companion object {
        /** 支持逗号/中文逗号/分号/换行分隔的地址输入 */
        fun parseAddresses(input: String): List<String> {
            return input.split(",", "，", ";", "；", "\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
