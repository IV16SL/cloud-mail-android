package me.huanjue.cloudmail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.CloudMailApp
import androidx.compose.ui.platform.LocalContext
import me.huanjue.cloudmail.ui.login.ServerUrlDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onPgpKey: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val container = app.container
    val scope = rememberCoroutineScope()

    val serverUrl by container.settings.serverUrl.collectAsState(initial = "")
    var showServerDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val snackBarHostState = remember { SnackbarHostState() }
    // 服务器能力（fork 独有功能开关）
    val capabilities by container.capabilitiesRepository.capabilities.collectAsState()
    // 进入设置页时主动刷新一次，避免启动时序问题导致 PGP 选项不显示
    LaunchedEffect(Unit) {
        try { container.capabilitiesRepository.refresh() } catch (_: Exception) {}
    }
    // PGP 总开关（按用户隔离）
    var pgpEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        pgpEnabled = try { container.pgpManager.isPgpEnabled() } catch (_: Exception) { true }
    }

    if (showServerDialog) {
        ServerUrlDialog(
            current = serverUrl ?: "",
            onDismiss = { showServerDialog = false },
            onSave = { input ->
                scope.launch {
                    try {
                        container.settings.setServerUrl(input)
                        showServerDialog = false
                        // 换服务器 = 换账号，必须重登
                        onLogout()
                    } catch (e: Exception) {
                        scope.launch { snackBarHostState.showSnackbar("保存失败：${e.message}") }
                    }
                }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch {
                        val hasMore = container.authRepository.logout()
                        if (hasMore) onBack() else onLogout()
                    }
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            ListItem(
                headlineContent = { Text("服务器地址") },
                supportingContent = { Text(serverUrl ?: "") },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.clickable { showServerDialog = true }
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            // 主题
            Text(
                "外观",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val themeMode by container.settings.themeMode.collectAsState(initial = "system")
            var showThemeDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("主题") },
                supportingContent = { Text(
                    when (themeMode) {
                        "light" -> "浅色"
                        "dark" -> "深色"
                        else -> "跟随系统"
                    }
                ) },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.clickable { showThemeDialog = true }
            )
            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("选择主题") },
                    text = {
                        Column {
                            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                container.settings.setThemeMode(value)
                                                showThemeDialog = false
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = themeMode == value,
                                        onClick = {
                                            scope.launch {
                                                container.settings.setThemeMode(value)
                                                showThemeDialog = false
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
            HorizontalDivider()
            // 主题色跟随系统（Material You 动态取色）
            val dynamicColor by container.settings.dynamicColor.collectAsState(initial = false)
            ListItem(
                headlineContent = { Text("主题色跟随系统") },
                supportingContent = { Text("开启后使用壁纸取色（Android 12+），关闭则用图标蓝") },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { checked ->
                            scope.launch {
                                try { container.settings.setDynamicColor(checked) } catch (_: Exception) {}
                            }
                        }
                    )
                }
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            // PGP 统一入口：总开关 + 私钥管理（仅后端支持时显示）
            if (capabilities.pgp) {
                Text(
                    "PGP 加密",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                ListItem(
                    headlineContent = { Text("启用 PGP") },
                    supportingContent = { Text("关闭后隐藏写信页 PGP 开关与解密入口") },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = pgpEnabled,
                            onCheckedChange = { checked ->
                                pgpEnabled = checked
                                scope.launch {
                                    try { container.pgpManager.setPgpEnabled(checked) } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("PGP 私钥") },
                    supportingContent = { Text("导入 / 生成私钥以解密收到的加密邮件") },
                    leadingContent = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    modifier = Modifier.clickable { onPgpKey() }
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }
            ListItem(
                headlineContent = { Text("退出登录") },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { showLogoutConfirm = true }
            )
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                "Cloud Mail for Android V0.1.1 (Beta)",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
