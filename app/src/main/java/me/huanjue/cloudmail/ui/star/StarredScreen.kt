package me.huanjue.cloudmail.ui.star

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.huanjue.cloudmail.R
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.CloudMailApp
import me.huanjue.cloudmail.data.model.ApiException
import me.huanjue.cloudmail.data.model.EmailItem
import me.huanjue.cloudmail.ui.mail.EmailRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(
    onMenuClick: () -> Unit,
    onOpenEmail: (accountId: Long, emailId: Long, type: Int) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val mailRepository = app.container.mailRepository

    var emails by remember { mutableStateOf<List<EmailItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }

    // 搜索过滤：主题、发件人
    val filteredEmails = remember(emails, searchText) {
        val kw = searchText.trim().lowercase()
        if (kw.isEmpty()) emails else emails.filter {
            (it.subject ?: "").lowercase().contains(kw) ||
            (it.fromName ?: "").lowercase().contains(kw) ||
            (it.fromEmail ?: "").lowercase().contains(kw)
        }
    }

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pageSize = 20

    val activeUserId by app.container.authRepository.activeSession
        .map { it?.userId }
        .collectAsState(initial = null)

    fun load(reset: Boolean) {
        if (!reset && (loadingMore || !hasMore)) return
        scope.launch {
            if (reset) loading = true else loadingMore = true
            try {
                // full=1：需要 accountId（打开详情用）
                val list = mailRepository.starredEmails(
                    cursorId = if (reset) null else emails.lastOrNull()?.emailId,
                    size = pageSize,
                    full = 1
                )
                emails = if (reset) list else emails + list
                hasMore = list.size >= pageSize
                error = null
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = context.getString(R.string.common_network_error, e.message ?: "")
            } finally {
                loading = false
                loadingMore = false
            }
        }
    }

    // 启动 + 切换登录账号后重载
    LaunchedEffect(activeUserId) {
        if (activeUserId != null) load(true)
    }

    LaunchedEffect(error) {
        error?.let {
            scope.launch { snackBarHostState.showSnackbar(it) }
            error = null
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= emails.size - 3) load(false)
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.starred_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(stringResource(R.string.mailbox_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.mailbox_search))
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.mailbox_clear))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when {
                loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                filteredEmails.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchText.isNotBlank()) stringResource(R.string.mailbox_no_search_result)
                        else stringResource(R.string.starred_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
            ) {
                items(filteredEmails, key = { it.emailId }) { email ->
                    EmailRow(
                        email = email,
                        onClick = { onOpenEmail(email.accountId, email.emailId, email.type) }
                    )
                    HorizontalDivider()
                }
                if (loadingMore) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
