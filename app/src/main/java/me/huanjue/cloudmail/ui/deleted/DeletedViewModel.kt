package me.huanjue.cloudmail.ui.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.data.AppSettings
import me.huanjue.cloudmail.data.MailRepository
import me.huanjue.cloudmail.data.model.EmailItem
import me.huanjue.cloudmail.data.model.MailAccount

data class DeletedUiState(
    val emails: List<EmailItem> = emptyList(),
    val accounts: List<MailAccount> = emptyList(),
    val currentAccount: MailAccount? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchKeyword: String = "",
    val notice: String? = null
)

class DeletedViewModel(
    private val mailRepository: MailRepository,
    private val settings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeletedUiState())
    val uiState: StateFlow<DeletedUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val accounts = mailRepository.accounts()
                val account = accounts.firstOrNull()
                val keyword = _uiState.value.searchKeyword.takeIf { it.isNotBlank() }
                val emails = if (account != null) {
                    mailRepository.deletedEmails(account.accountId, keyword = keyword).list ?: emptyList()
                } else emptyList()
                _uiState.value = _uiState.value.copy(
                    emails = emails,
                    accounts = accounts,
                    currentAccount = account,
                    isLoading = false,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        load()
    }

    fun onSearchInput(keyword: String) {
        _uiState.value = _uiState.value.copy(searchKeyword = keyword)
        // 防抖：简单起见直接搜，数据量不大
        load()
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchKeyword = "")
        load()
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    /**
     * 乐观恢复：先立刻从列表移除并弹提示，后台再同步服务端；
     * 同步失败则把邮件恢复回原位。
     */
    fun restore(emailId: Long) {
        optimisticRemove(
            emailId = emailId,
            sync = { mailRepository.restoreEmails(listOf(emailId)) },
            noticeRes = me.huanjue.cloudmail.R.string.deleted_restored_notice
        )
    }

    /** 乐观彻底删除：同上 */
    fun permanentDelete(emailId: Long) {
        optimisticRemove(
            emailId = emailId,
            sync = { mailRepository.permanentDeleteEmails(listOf(emailId)) },
            noticeRes = me.huanjue.cloudmail.R.string.deleted_permanent_notice
        )
    }

    private fun optimisticRemove(
        emailId: Long,
        sync: suspend () -> Unit,
        noticeRes: Int
    ) {
        val current = _uiState.value.emails
        val index = current.indexOfFirst { it.emailId == emailId }
        if (index == -1) return
        val removed = current[index]
        _uiState.value = _uiState.value.copy(
            emails = current.filter { it.emailId != emailId }
        )
        viewModelScope.launch {
            try {
                sync()
                _uiState.value = _uiState.value.copy(
                    notice = settings.getString(noticeRes)
                )
            } catch (e: Exception) {
                // 失败：恢复回原位（保持和之前一致：不弹错，避免打断）
                val list = _uiState.value.emails.toMutableList()
                list.add(index.coerceAtMost(list.size), removed)
                _uiState.value = _uiState.value.copy(emails = list)
            }
        }
    }
}
