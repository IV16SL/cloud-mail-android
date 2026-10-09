package me.huanjue.cloudmail.ui.deleted

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.huanjue.cloudmail.R
import me.huanjue.cloudmail.ui.mail.EmailRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedScreen(
    viewModel: DeletedViewModel,
    onMenuClick: () -> Unit,
    onOpenEmail: (accountId: Long, emailId: Long, type: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }
    val pullRefreshState = rememberPullToRefreshState()

    // 每次进入页面都刷新，避免 ViewModel 缓存的空数据
    LaunchedEffect(Unit) {
        viewModel.load()
    }
    // ViewModel 外部清空搜索时同步清空输入框
    LaunchedEffect(state.searchKeyword) {
        if (state.searchKeyword.isEmpty() && searchText.isNotEmpty()) {
            searchText = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_deleted)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = null)
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
                onValueChange = {
                    searchText = it
                    viewModel.onSearchInput(it)
                },
                placeholder = { Text(stringResource(R.string.mailbox_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            PullToRefreshBox(
                state = pullRefreshState,
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 下拉刷新时只显示顶部小圈，不显示中间大圈
                    if (state.isLoading && !state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (state.emails.isEmpty()) {
                        Text(
                            text = stringResource(R.string.empty_deleted),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.emails, key = { it.emailId }) { email ->
                        // 左滑彻底删除，右滑恢复
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance * 0.5f },
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.permanentDelete(email.emailId)
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        viewModel.restore(email.emailId)
                                        true
                                    }
                                    else -> false
                                }
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                // 用 offset 即时判断方向，颜色跟手
                                val offset = try {
                                    dismissState.requireOffset()
                                } catch (e: Exception) {
                                    0f
                                }
                                val (bgColor, icon, alignment) = when {
                                    offset > 0 -> Triple(
                                        Color(0xFF4CAF50),
                                        Icons.Default.Restore,
                                        Alignment.CenterStart
                                    )
                                    offset < 0 -> Triple(
                                        Color.Red,
                                        Icons.Default.DeleteForever,
                                        Alignment.CenterEnd
                                    )
                                    else -> when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> Triple(
                                            Color(0xFF4CAF50),
                                            Icons.Default.Restore,
                                            Alignment.CenterStart
                                        )
                                        SwipeToDismissBoxValue.EndToStart -> Triple(
                                            Color.Red,
                                            Icons.Default.DeleteForever,
                                            Alignment.CenterEnd
                                        )
                                        else -> Triple(
                                            Color.Transparent,
                                            Icons.Default.DeleteForever,
                                            Alignment.CenterEnd
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(bgColor)
                                        .padding(16.dp),
                                    contentAlignment = alignment
                                ) {
                                    if (bgColor != Color.Transparent) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                EmailRow(
                                    email = email,
                                    onClick = {
                                        val accountId = state.currentAccount?.accountId ?: return@EmailRow
                                        onOpenEmail(accountId, email.emailId, 0)
                                    }
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
}
}
