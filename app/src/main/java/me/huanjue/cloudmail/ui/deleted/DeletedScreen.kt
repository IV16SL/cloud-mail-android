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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 操作成功的一次性文字提示（底部弹出）
    LaunchedEffect(state.notice) {
        state.notice?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearNotice()
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        val haptics = LocalHapticFeedback.current
                        var rowWidth by remember { mutableStateOf(0) }
                        var pastThreshold by remember { mutableStateOf(0) } // -1=左 0=无 1=右
                        val bgColor by remember {
                            derivedStateOf {
                                when {
                                    offsetX.value > 0 -> Color(0xFF34A853)
                                    offsetX.value < 0 -> Color(0xFFD93025)
                                    else -> Color.Transparent
                                }
                            }
                        }
                        val swipingRight = offsetX.value >= 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { rowWidth = it.width }
                                .draggable(
                                    state = rememberDraggableState { delta ->
                                        scope.launch {
                                            val newOffset = offsetX.value + delta
                                            // 划过 50% 阈值时震动一下（Gmail 式反馈）
                                            val crossed = if (rowWidth > 0) {
                                                when {
                                                    newOffset <= -rowWidth * 0.5f -> -1
                                                    newOffset >= rowWidth * 0.5f -> 1
                                                    else -> 0
                                                }
                                            } else 0
                                            if (crossed != pastThreshold) {
                                                pastThreshold = crossed
                                                if (crossed != 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            offsetX.snapTo(newOffset)
                                        }
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
                                            pastThreshold = 0
                                            offsetX.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                )
                        ) {
                            // Gmail 式底色：图标 + 动作文字
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(bgColor)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = if (swipingRight) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (swipingRight) Icons.Default.Restore else Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(
                                            if (swipingRight) R.string.swipe_restore
                                            else R.string.swipe_delete_forever
                                        ),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
