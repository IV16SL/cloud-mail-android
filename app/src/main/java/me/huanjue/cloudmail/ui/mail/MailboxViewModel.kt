package me.huanjue.cloudmail.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.data.AppSettings
import me.huanjue.cloudmail.data.MailCache
import me.huanjue.cloudmail.data.MailRepository
import me.huanjue.cloudmail.data.model.ApiException
import me.huanjue.cloudmail.data.model.EmailItem
import me.huanjue.cloudmail.data.model.MailAccount

data class MailboxUiState(
    val accounts: List<MailAccount> = emptyList(),
    val currentAccount: MailAccount? = null,
    val emails: List<EmailItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val type: Int = 0, // 0=收件箱 1=已发送
    val error: String? = null,
    val searchKeyword: String = ""
)

class MailboxViewModel(
    private val mailRepository: MailRepository,
    private val settings: AppSettings,
    private val mailCache: MailCache,
    initialType: Int = 0
) : ViewModel() {

    private val _uiState = MutableStateFlow(MailboxUiState(type = initialType))
    val uiState: StateFlow<MailboxUiState> = _uiState.asStateFlow()

    private val pageSize = 20

    /** 搜索框输入（防抖 300ms 后真正触发搜索） */
    private val _searchInput = MutableStateFlow("")

    init {
        // 冷启动：先读缓存秒开，再后台静默拉取最新
        viewModelScope.launch {
            val cached = loadCached()
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    accounts = cached.first,
                    currentAccount = cached.second,
                    emails = cached.third,
                    isLoading = false,
                    hasMore = cached.third.size >= pageSize
                )
                // 后台静默刷新，无感知更新
                refresh(silent = true)
            } else {
                refresh()
            }
        }
        // 切换登录账号后清空重载（新账号的邮箱账号/邮件都不同）
        viewModelScope.launch {
            settings.activeSession
                .map { it?.userId }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    _searchInput.value = ""
                    _uiState.value = MailboxUiState(type = _uiState.value.type)
                    refresh()
                }
        }
        // 搜索防抖：输入停止 300ms 后执行搜索
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _searchInput
                .debounce(300)
                .distinctUntilChanged()
                .collect { keyword ->
                    _uiState.value = _uiState.value.copy(searchKeyword = keyword)
                    refresh()
                }
        }
    }

    /** 搜索框输入变化（UI 层调用，防抖后触发 refresh） */
    fun onSearchInput(keyword: String) {
        _searchInput.value = keyword
    }

    /** 清空搜索，恢复正常列表 */
    fun clearSearch() {
        _searchInput.value = ""
        // debounce 会触发 refresh，这里直接同步清空避免等待
        _uiState.value = _uiState.value.copy(searchKeyword = "")
        refresh()
    }

    /** 从缓存读账号+邮件头（冷启动秒开用） */
    private suspend fun loadCached(): Triple<List<MailAccount>, MailAccount?, List<EmailItem>>? {
        return try {
            val accounts = mailRepository.accounts()
            val account = accounts.firstOrNull() ?: return null
            val emails = mailCache.get(account.accountId, _uiState.value.type)
                ?: return null
            Triple(accounts, account, emails)
        } catch (_: Exception) {
            null
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            try {
                val accounts = mailRepository.accounts()
                val account = _uiState.value.currentAccount
                    ?: accounts.firstOrNull()
                val keyword = _uiState.value.searchKeyword.takeIf { it.isNotBlank() }
                val emails = if (account != null) {
                    mailRepository.emails(
                        account.accountId,
                        size = pageSize,
                        type = _uiState.value.type,
                        keyword = keyword
                    ).list ?: emptyList()
                } else emptyList()
                // 非搜索时写缓存（搜索结果不缓存）
                if (keyword == null && account != null) {
                    mailCache.put(account.accountId, _uiState.value.type, emails)
                }
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    currentAccount = account,
                    emails = emails,
                    isLoading = false,
                    hasMore = emails.size >= pageSize,
                    error = if (account == null) settings.getString(me.huanjue.cloudmail.R.string.mailbox_no_account) else null
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = settings.getString(me.huanjue.cloudmail.R.string.common_network_error, e.message ?: ""))
            }
        }
    }

    fun switchAccount(account: MailAccount) {
        if (account.accountId == _uiState.value.currentAccount?.accountId) return
        _searchInput.value = ""
        _uiState.value = _uiState.value.copy(
            currentAccount = account,
            emails = emptyList(),
            hasMore = true,
            searchKeyword = ""
        )
        loadMore(reset = true)
    }

    fun loadMore(reset: Boolean = false) {
        val state = _uiState.value
        val account = state.currentAccount ?: return
        if (!reset && (state.isLoadingMore || !state.hasMore)) return
        viewModelScope.launch {
            if (!reset) _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val cursor = if (reset) null else _uiState.value.emails.lastOrNull()?.emailId
                val keyword = _uiState.value.searchKeyword.takeIf { it.isNotBlank() }
                val data = mailRepository.emails(
                    accountId = account.accountId,
                    cursorId = cursor,
                    size = pageSize,
                    type = _uiState.value.type,
                    keyword = keyword
                )
                val list = data.list ?: emptyList()
                val merged = if (reset) list else _uiState.value.emails + list
                _uiState.value = _uiState.value.copy(
                    emails = merged,
                    isLoadingMore = false,
                    hasMore = list.size >= pageSize,
                    error = null
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, error = settings.getString(me.huanjue.cloudmail.R.string.common_network_error, e.message ?: ""))
            }
        }
    }

    /** 打开详情时标记已读，并更新本地状态 */
    fun markRead(emailId: Long) {
        viewModelScope.launch {
            try {
                mailRepository.markRead(listOf(emailId))
                _uiState.value = _uiState.value.copy(
                    emails = _uiState.value.emails.map {
                        if (it.emailId == emailId) it.copy(unread = 1) else it
                    }
                )
            } catch (_: Exception) {
            }
        }
    }

    fun deleteEmail(emailId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                mailRepository.deleteEmails(listOf(emailId))
                _uiState.value = _uiState.value.copy(
                    emails = _uiState.value.emails.filter { it.emailId != emailId }
                )
                onDone()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = settings.getString(me.huanjue.cloudmail.R.string.common_network_error, e.message ?: ""))
            }
        }
    }

    fun toggleStar(email: EmailItem) {
        val newStar = if (email.isStar == 1) 0 else 1
        // 乐观更新
        _uiState.value = _uiState.value.copy(
            emails = _uiState.value.emails.map {
                if (it.emailId == email.emailId) it.copy(isStar = newStar) else it
            }
        )
        viewModelScope.launch {
            try {
                if (newStar == 1) mailRepository.starAdd(email.emailId)
                else mailRepository.starCancel(email.emailId)
            } catch (e: ApiException) {
                // 失败回滚
                _uiState.value = _uiState.value.copy(
                    emails = _uiState.value.emails.map {
                        if (it.emailId == email.emailId) it.copy(isStar = email.isStar) else it
                    },
                    error = e.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    emails = _uiState.value.emails.map {
                        if (it.emailId == email.emailId) it.copy(isStar = email.isStar) else it
                    },
                    error = settings.getString(me.huanjue.cloudmail.R.string.common_network_error, e.message ?: "")
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
