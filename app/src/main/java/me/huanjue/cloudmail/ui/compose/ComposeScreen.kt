package me.huanjue.cloudmail.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.CloudMailApp
import me.huanjue.cloudmail.data.model.SendAttachment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    viewModel: ComposeViewModel,
    onSent: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val mailRepository = app.container.mailRepository

    val uiState by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val editingDraft by viewModel.editingDraft.collectAsState()

    var fromAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var showCc by remember { mutableStateOf(false) }
    var showBcc by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var draftLoaded by remember { mutableStateOf(false) }

    // PGP：开关 + 各收件人公钥状态（email -> 有无公钥）
    var pgpEnabled by remember { mutableStateOf(false) }
    var keyStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var checkingKeys by remember { mutableStateOf(false) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    // PGP 功能总开关（设置页控制）+ 后端能力（主仓库无 PGP 则隐藏）
    var pgpFeatureEnabled by remember { mutableStateOf(true) }
    val serverPgpSupported by app.container.capabilitiesRepository.capabilities
        .collectAsState()

    LaunchedEffect(Unit) {
        pgpFeatureEnabled = try {
            app.container.pgpManager.isPgpEnabled()
        } catch (_: Exception) {
            true
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun snack(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    /** 当前所有收件人（去重） */
    fun allRecipients(): List<String> =
        (ComposeViewModel.parseAddresses(to) +
            ComposeViewModel.parseAddresses(cc) +
            ComposeViewModel.parseAddresses(bcc)).distinct()

    suspend fun queryKeyStatus(emails: List<String>): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        for (email in emails) {
            result[email] = try {
                mailRepository.pgpKeyStatus(email).found
            } catch (_: Exception) {
                false
            }
        }
        return result
    }

    // PGP 开启时，收件人变化后防抖查询公钥状态（对标网页版收件人标签上的锁标）
    LaunchedEffect(pgpEnabled, to, cc, bcc) {
        if (!pgpEnabled) {
            keyStatus = emptyMap()
            return@LaunchedEffect
        }
        val emails = allRecipients()
        if (emails.isEmpty()) {
            keyStatus = emptyMap()
            return@LaunchedEffect
        }
        delay(600)
        checkingKeys = true
        keyStatus = queryKeyStatus(emails)
        checkingKeys = false
    }

    /** 发送：PGP 模式下先确保每个收件人都有公钥（后端也会再拦截一次） */
    // 附件：已选文件的 Uri 列表
    var attachments by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var encodingAttachments by remember { mutableStateOf(false) }

    val pickFiles = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            attachments = (attachments + uris).distinct()
        }
    }

    /** 把 Uri 读成 base64 的 SendAttachment（IO 线程） */
    suspend fun encodeAttachments(uris: List<android.net.Uri>): List<SendAttachment> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            uris.mapNotNull { uri ->
                try {
                    val cr = context.contentResolver
                    val name = cr.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                    } ?: "attachment"
                    val size = cr.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (cursor.moveToFirst() && idx >= 0) cursor.getLong(idx) else 0L
                    } ?: 0L
                    if (size > 20 * 1024 * 1024) {
                        throw Exception("文件 $name 超过 20MB 限制")
                    }
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("无法读取文件 $name")
                    val mime = cr.getType(uri) ?: "application/octet-stream"
                    SendAttachment(
                        content = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                        filename = name,
                        size = bytes.size.toLong(),
                        contentType = mime
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }

    fun doSend() {
        val accountId = fromAccount?.accountId
        if (accountId == null) {
            snack("还没有可用的发件账号")
            return
        }
        scope.launch {
            encodingAttachments = true
            val atts = try {
                encodeAttachments(attachments)
            } catch (e: Exception) {
                snack("附件处理失败：${e.message}")
                encodingAttachments = false
                return@launch
            }
            encodingAttachments = false
            if (!pgpEnabled || !pgpFeatureEnabled || !serverPgpSupported.pgp) {
                viewModel.send(accountId, to, cc, bcc, subject, body, pgpEncrypt = false, attachments = atts)
                return@launch
            }
            val emails = allRecipients()
            if (emails.isEmpty()) {
                snack("收件人不能为空")
                return@launch
            }
            checkingKeys = true
            val status = queryKeyStatus(emails)
            keyStatus = status
            checkingKeys = false
            val missing = status.filter { !it.value }.keys
            if (missing.isNotEmpty()) {
                snack("以下收件人没有 PGP 公钥，无法加密发送：${missing.joinToString("、")}")
                return@launch
            }
            // 加密由服务端完成（openpgp，按收件人公钥逐个加密），发件箱存明文
            viewModel.send(accountId, to, cc, bcc, subject, body, pgpEncrypt = true, attachments = atts)
        }
    }

    // 草稿预填（只填一次）
    LaunchedEffect(editingDraft, accounts) {
        val draft = editingDraft
        if (draft != null && !draftLoaded) {
            draftLoaded = true
            to = draft.to
            cc = draft.cc
            bcc = draft.bcc
            if (cc.isNotBlank()) showCc = true
            if (bcc.isNotBlank()) showBcc = true
            subject = draft.subject
            body = draft.body
            accounts.find { it.accountId == draft.accountId }?.let { fromAccount = it }
        }
    }

    // 系统返回键：有未保存内容先弹窗确认
    fun hasContent(): Boolean =
        to.isNotBlank() || cc.isNotBlank() || bcc.isNotBlank() ||
            subject.isNotBlank() || body.isNotBlank()

    /** 相对正在编辑的草稿没有改动时，不用再问 */
    fun isUnchangedFromDraft(): Boolean {
        val d = editingDraft ?: return false
        return to.trim() == d.to && cc.trim() == d.cc && bcc.trim() == d.bcc &&
            subject.trim() == d.subject && body == d.body &&
            (fromAccount?.accountId ?: 0L) == d.accountId
    }

    fun saveAndBack() {
        viewModel.saveDraftIfNeeded(fromAccount?.accountId, to, cc, bcc, subject, body)
        onBack()
    }

    fun backWithConfirm() {
        if (!hasContent() || isUnchangedFromDraft()) {
            // 空内容（本来也不会存）或相对草稿无改动：直接退出
            onBack()
        } else {
            showSaveDraftDialog = true
        }
    }
    BackHandler { backWithConfirm() }

    LaunchedEffect(accounts) {
        if (fromAccount == null) fromAccount = accounts.firstOrNull()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ComposeUiState.Sent -> {
                viewModel.backToIdle()
                onSent()
            }
            is ComposeUiState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                viewModel.backToIdle()
            }
            else -> Unit
        }
    }

    val sending = uiState is ComposeUiState.Sending

    // 退出写信页：有未保存内容时确认是否存草稿
    if (showSaveDraftDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDraftDialog = false },
            title = { Text("保存草稿？") },
            text = { Text("是否把当前内容保存到草稿箱？") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDraftDialog = false
                    saveAndBack()
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        // 不保存：已有草稿保留原样，直接退出
                        showSaveDraftDialog = false
                        onBack()
                    }) { Text("不保存") }
                    TextButton(onClick = { showSaveDraftDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("写信") },
                navigationIcon = {
                    IconButton(onClick = { backWithConfirm() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { doSend() },
                        enabled = !sending && !checkingKeys
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 发件账号
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = fromAccount?.email ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("发件人") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.email) },
                            onClick = {
                                fromAccount = account
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("收件人（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            // 抄送 / 密送：两个按钮并排，各自独立展开，展开后右侧有 X 关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!showCc) {
                    TextButton(onClick = { showCc = true }) { Text("抄送") }
                }
                if (!showBcc) {
                    TextButton(onClick = { showBcc = true }) { Text("密送") }
                }
            }
            if (showCc) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = cc,
                        onValueChange = { cc = it },
                        label = { Text("抄送（逗号分隔）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showCc = false; cc = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭抄送")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (showBcc) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = bcc,
                        onValueChange = { bcc = it },
                        label = { Text("密送（逗号分隔）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showBcc = false; bcc = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭密送")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("主题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            // PGP 开关（对标网页版编辑器工具栏的 PGP toggle；设置页总开关+后端能力控制显隐）
            if (pgpFeatureEnabled && serverPgpSupported.pgp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (pgpEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PGP 加密", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "收件人需有公钥，由服务器加密发送",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pgpEnabled,
                        onCheckedChange = { pgpEnabled = it }
                    )
                }
            }
            if (pgpEnabled && pgpFeatureEnabled && serverPgpSupported.pgp) {
                Spacer(Modifier.height(4.dp))
                val emails = allRecipients()
                val missing = keyStatus.filter { !it.value }.keys
                val statusText = when {
                    checkingKeys -> "正在查询收件人公钥…"
                    emails.isEmpty() -> "请先填写收件人"
                    keyStatus.size < emails.size -> "正在查询收件人公钥…"
                    missing.isEmpty() -> if (emails.size == 1) "✓ 收件人有公钥，可以加密发送"
                        else "✓ ${emails.size} 个收件人有公钥，可以加密发送"
                    else -> "✗ 没有公钥：${missing.joinToString("、")}"
                }
                val statusColor = when {
                    checkingKeys || emails.isEmpty() || keyStatus.size < emails.size ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                    missing.isEmpty() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("正文") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
            Spacer(Modifier.height(8.dp))
            // 附件：选择按钮 + 已选文件列表
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { pickFiles.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("附件")
                }
                if (attachments.isNotEmpty()) {
                    Text(
                        "${attachments.size} 个文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            attachments.forEach { uri ->
                val name = remember(uri) {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else uri.lastPathSegment
                    } ?: "附件"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name ?: "附件",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(onClick = { attachments = attachments - uri }) {
                        Icon(Icons.Default.Close, contentDescription = "移除附件")
                    }
                }
            }
        }
    }
}
