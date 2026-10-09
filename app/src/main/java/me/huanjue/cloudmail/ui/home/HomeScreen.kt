package me.huanjue.cloudmail.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.huanjue.cloudmail.R
import androidx.lifecycle.viewmodel.compose.viewModel
import me.huanjue.cloudmail.CloudMailApp
import androidx.compose.ui.platform.LocalContext
import me.huanjue.cloudmail.ui.VmFactory
import me.huanjue.cloudmail.ui.draft.DraftsScreen
import me.huanjue.cloudmail.ui.mail.MailboxScreen
import me.huanjue.cloudmail.ui.mail.MailboxViewModel
import me.huanjue.cloudmail.ui.star.StarredScreen
import kotlinx.coroutines.launch

enum class DrawerDestination(
    @androidx.annotation.StringRes val titleRes: Int,
    val icon: ImageVector
) {
    INBOX(R.string.nav_inbox, Icons.Default.Inbox),
    SENT(R.string.nav_sent, Icons.AutoMirrored.Filled.Send),
    STARRED(R.string.nav_starred, Icons.Default.Star),
    DRAFTS(R.string.nav_drafts, Icons.Default.Description)
}

/** 左侧抽屉导航（对标网页版侧边栏） */
@Composable
fun HomeScreen(
    onOpenEmail: (accountId: Long, emailId: Long, type: Int) -> Unit,
    onCompose: (draftId: String?) -> Unit,
    onSettings: () -> Unit,
    onAddAccount: () -> Unit,
    sentRefreshNonce: Long = 0L
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val container = app.container

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // 用 rememberSaveable 存 tab，下钻到详情页再返回时能回到原 tab，而不是重置到收件箱
    var destinationOrdinal by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(DrawerDestination.INBOX.ordinal) }
    var destination
        get() = DrawerDestination.entries[destinationOrdinal]
        set(value) { destinationOrdinal = value.ordinal }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    // 两个邮箱 VM 都提升到这里：切换抽屉时复用；发信成功后能直接刷新已发送
    val inboxVm: MailboxViewModel = viewModel(
        key = "mailbox_0",
        factory = VmFactory { MailboxViewModel(container.mailRepository, container.settings, container.mailCache, 0) }
    )
    val sentVm: MailboxViewModel = viewModel(
        key = "mailbox_1",
        factory = VmFactory { MailboxViewModel(container.mailRepository, container.settings, container.mailCache, 1) }
    )

    // 写信页发送成功后带回来的刷新信号
    LaunchedEffect(sentRefreshNonce) {
        if (sentRefreshNonce > 0) sentVm.refresh()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AccountHeader(
                    onAddAccount = {
                        closeDrawer()
                        onAddAccount()
                    },
                    onSwitchAccount = { userId ->
                        scope.launch {
                            container.authRepository.switchSession(userId)
                            container.pgpManager.clearUserIdCache()
                            closeDrawer()
                        }
                    }
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                DrawerDestination.values().forEach { d ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(d.titleRes)) },
                        icon = { Icon(d.icon, contentDescription = null) },
                        selected = d == destination,
                        onClick = {
                            destination = d
                            closeDrawer()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = false,
                    onClick = {
                        closeDrawer()
                        onSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        when (destination) {
            DrawerDestination.INBOX, DrawerDestination.SENT -> {
                val vm = if (destination == DrawerDestination.INBOX) inboxVm else sentVm
                MailboxScreen(
                    viewModel = vm,
                    title = stringResource(destination.titleRes),
                    onMenuClick = { openDrawer() },
                    onOpenEmail = onOpenEmail,
                    onCompose = { onCompose(null) }
                )
            }
            DrawerDestination.STARRED -> {
                StarredScreen(
                    onMenuClick = { openDrawer() },
                    onOpenEmail = onOpenEmail
                )
            }
            DrawerDestination.DRAFTS -> {
                DraftsScreen(
                    onMenuClick = { openDrawer() },
                    onEditDraft = { draftId -> onCompose(draftId) },
                    onNewDraft = { onCompose(null) }
                )
            }
        }
    }
}
