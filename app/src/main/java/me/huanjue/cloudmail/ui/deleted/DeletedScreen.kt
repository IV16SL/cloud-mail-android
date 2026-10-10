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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.huanjue.cloudmail.R
import me.huanjue.cloudmail.ui.mail.EmailRow
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch

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
            // 缩放动画是官方文档的自定义做法：默认 indicator 不带缩放，必须自己包一层
            val scaleFraction = {
                if (state.isRefreshing) 1f
                else LinearOutSlowInEasing.transform(pullRefreshState.distanceFraction).coerceIn(0f, 1f)
            }
            PullToRefreshBox(
                state = pullRefreshState,
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                scaleX = scaleFraction()
                                scaleY = scaleFraction()
                            }
                    ) {
                        PullToRefreshDefaults.Indicator(
                            state = pullRefreshState,
                            isRefreshing = state.isRefreshing
                        )
                    }
                }
            ) {
                // 只有初次加载（无数据）时显示大圈，下拉刷新用顶部小圈
                if (state.isLoading && state.emails.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.emails.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.empty_deleted)
                        )
                    }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.emails, key = { it.emailId }) { email ->
                        // 左滑彻底删除，右滑恢复：手写，只按 50% 位置触发
                        val offsetX = remember { Animatable(0f) }
                        val scope = rememberCoroutineScope()
                        var rowWidth by remember { mutableStateOf(0) }
                        val bgColor by remember {
                            derivedStateOf {
                                when {
                                    offsetX.value > 0 -> Color(0xFF4CAF50)
                                    offsetX.value < 0 -> Color.Red
                                    else -> Color.Transparent
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { rowWidth = it.width }
                                .draggable(
                                    state = rememberDraggableState { delta ->
                                        scope.launch { offsetX.snapTo(offsetX.value + delta) }
                                    },
                                    orientation = Orientation.Horizontal,
                                    onDragStopped = {
                                        scope.launch {
                                            if (rowWidth > 0) {
                                                when {
                                                    offsetX.value <= -rowWidth * 0.5f -> viewModel.permanentDelete(email.emailId)
                                                    offsetX.value >= rowWidth * 0.5f -> viewModel.restore(email.emailId)
                                                }
                                            }
                                            offsetX.animateTo(0f, tween(200))
                                        }
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(bgColor).padding(16.dp),
                                contentAlignment = if (offsetX.value >= 0) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = if (offsetX.value >= 0) Icons.Default.Restore else Icons.Default.DeleteForever,
                                    contentDescription = null, tint = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
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
