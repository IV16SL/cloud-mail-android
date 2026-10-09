package me.huanjue.cloudmail.ui.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.CloudMailApp
import me.huanjue.cloudmail.data.model.EmailItem
import me.huanjue.cloudmail.data.model.MailAccount

data class DeletedUiState(
    val emails: List<EmailItem> = emptyList(),
    val accounts: List<MailAccount> = emptyList(),
    val currentAccount: MailAccount? = null,
    val isLoading: Boolean = false
)

class DeletedViewModel : ViewModel() {
    private val repo = CloudMailApp.mailRepository

    private val _uiState = MutableStateFlow(DeletedUiState())
    val uiState: StateFlow<DeletedUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val accounts = repo.accounts()
                val account = accounts.firstOrNull()
                val emails = if (account != null) {
                    repo.deletedEmails(account.accountId).emails
                } else emptyList()
                _uiState.value = DeletedUiState(
                    emails = emails,
                    accounts = accounts,
                    currentAccount = account,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun restore(emailId: Long) {
        viewModelScope.launch {
            try {
                repo.restoreEmails(listOf(emailId))
                load()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun permanentDelete(emailId: Long) {
        viewModelScope.launch {
            try {
                repo.permanentDeleteEmails(listOf(emailId))
                load()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
