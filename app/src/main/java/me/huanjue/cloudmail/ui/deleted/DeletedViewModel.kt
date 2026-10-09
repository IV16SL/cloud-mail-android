package me.huanjue.cloudmail.ui.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.data.MailRepository
import me.huanjue.cloudmail.data.model.EmailItem
import me.huanjue.cloudmail.data.model.MailAccount

data class DeletedUiState(
    val emails: List<EmailItem> = emptyList(),
    val accounts: List<MailAccount> = emptyList(),
    val currentAccount: MailAccount? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchKeyword: String = ""
)

class DeletedViewModel(
    private val mailRepository: MailRepository
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

    fun restore(emailId: Long) {
        viewModelScope.launch {
            try {
                mailRepository.restoreEmails(listOf(emailId))
                load()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun permanentDelete(emailId: Long) {
        viewModelScope.launch {
            try {
                mailRepository.permanentDeleteEmails(listOf(emailId))
                load()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
