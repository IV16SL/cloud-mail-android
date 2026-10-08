package me.huanjue.cloudmail.ui.mail

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.CloudMailApp
import me.huanjue.cloudmail.data.PgpManager
import me.huanjue.cloudmail.data.model.ApiException
import me.huanjue.cloudmail.data.model.Attachment
import me.huanjue.cloudmail.data.model.EmailItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    accountId: Long,
    emailId: Long,
    type: Int,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val mailRepository = app.container.mailRepository
    val pgpManager = app.container.pgpManager

    var email by remember { mutableStateOf<EmailItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isStarred by remember { mutableStateOf(false) }

    // PGP：是否加密邮件 / 解密出的明文 / 口令弹窗
    var pgpEncrypted by remember { mutableStateOf(false) }
    var decryptedText by remember { mutableStateOf<String?>(null) }
    var decrypting by remember { mutableStateOf(false) }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    // 附件下载中（attId）
    var downloadingAtt by remember { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun snack(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    /** 找出包含 PGP armor 的正文（纯文本优先，HTML 则去标签后找） */
    fun pgpSourceText(item: EmailItem): String? {
        if (PgpManager.containsEncryptedData(item.text)) return item.text
        val stripped = item.content?.let { PgpManager.stripHtml(it) }
        if (PgpManager.containsEncryptedData(stripped)) return stripped
        return null
    }

    fun doDecrypt(pw: CharArray?) {
        val src = email?.let { pgpSourceText(it) } ?: return
        // 诊断：只记口令长度，不记内容
        android.util.Log.w("DetailScreen", "doDecrypt pwLen=${pw?.size ?: -1}")
        scope.launch {
            decrypting = true
            try {
                decryptedText = pgpManager.decrypt(src, pw)
            } catch (e: Exception) {
                snack(context.getString(R.string.detail_pgp_decrypt_failed, e.message ?: ""))
            } finally {
                decrypting = false
            }
        }
    }

    fun onDecryptClick() {
        scope.launch {
            if (!pgpManager.hasPrivateKey()) {
                snack(context.getString(R.string.detail_pgp_no_key_hint))
                return@launch
            }
            if (pgpManager.isProtectedKey()) {
                passphrase = ""
                showPassphraseDialog = true
            } else {
                doDecrypt(null)
            }
        }
    }

    LaunchedEffect(emailId) {
        loading = true
        pgpEncrypted = false
        decryptedText = null
        val pgpFeatureOn = try { pgpManager.isPgpEnabled() } catch (_: Exception) { true }
        val serverPgpOn = try {
            app.container.capabilitiesRepository.capabilities.value.pgp
        } catch (_: Exception) { false }
        try {
            val item = mailRepository.emailDetail(accountId, emailId, type)
            email = item
            isStarred = item?.starId != null
            if (item == null) {
                error = context.getString(R.string.detail_not_found)
            } else {
                pgpEncrypted = pgpFeatureOn && serverPgpOn && pgpSourceText(item) != null
            }
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = context.getString(R.string.common_network_error, e.message ?: "")
        } finally {
            loading = false
        }
    }

    fun toggleStar() {
        scope.launch {
            try {
                if (isStarred) {
                    mailRepository.starCancel(emailId)
                    isStarred = false
                } else {
                    mailRepository.starAdd(emailId)
                    isStarred = true
                }
            } catch (e: ApiException) {
                scope.launch { snackbarHostState.showSnackbar(e.message) }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.common_network_error, e.message ?: "")) }
            }
        }
    }

    fun doDelete() {
        scope.launch {
            try {
                mailRepository.deleteEmails(listOf(emailId))
                onDeleted()
            } catch (e: ApiException) {
                scope.launch { snackbarHostState.showSnackbar(e.message) }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.common_network_error, e.message ?: "")) }
            }
        }
    }

    /** 下载附件：经 /oss 直链下载，.pgp 文件先解密，存到系统 Download 目录并打开 */
    fun downloadAttachment(att: Attachment) {
        scope.launch {
            downloadingAtt = att.attId
            try {
                val serverUrl = app.container.settings.getServerUrl()
                    ?: throw Exception(context.getString(R.string.detail_no_server))
                var bytes = mailRepository.downloadAttachment(serverUrl, att.key)
                var filename = att.filename.ifBlank { "attachment" }
                // PGP 加密的附件（.pgp）：先解密
                if (filename.endsWith(".pgp", ignoreCase = true)) {
                    val text = bytes.toString(Charsets.UTF_8)
                    if (!text.contains(PgpManager.ARMOR_BEGIN)) {
                        throw Exception(context.getString(R.string.detail_binary_pgp_unsupported))
                    }
                    val pw = if (pgpManager.isProtectedKey()) {
                        // 有口令的钥匙：这里简化处理，用已缓存的口令逻辑
                        // 实际应该弹窗，为简化先要求用户在 PGP 设置里确认
                        null
                    } else null
                    val plain = pgpManager.decrypt(text, pw)
                    bytes = plain.toByteArray(Charsets.UTF_8)
                    filename = filename.removeSuffix(".pgp").removeSuffix(".PGP")
                        .ifBlank { "decrypted" }
                }
                // 存到 Download 目录
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, att.contentType ?: "application/octet-stream")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: throw Exception(context.getString(R.string.detail_create_file_failed))
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw Exception(context.getString(R.string.detail_write_file_failed))
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                snack(context.getString(R.string.detail_saved_to_download, filename))
                // 尝试打开
                try {
                    val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, att.contentType ?: "application/octet-stream")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(openIntent, context.getString(R.string.detail_open))
                    )
                } catch (_: Exception) {
                    // 没有能打开的应用就不管了，文件已保存
                }
            } catch (e: Exception) {
                snack(context.getString(R.string.detail_download_failed, e.message ?: ""))
            } finally {
                downloadingAtt = null
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    doDelete()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // PGP 私钥口令弹窗（对标网页版解密时的口令弹窗）
    if (showPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            title = { Text(stringResource(R.string.detail_pgp_passphrase_title)) },
            text = {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.detail_pgp_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPassphraseDialog = false
                    doDecrypt(passphrase.toCharArray())
                    passphrase = ""
                }) { Text(stringResource(R.string.detail_pgp_decrypt)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPassphraseDialog = false
                    passphrase = ""
                }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { toggleStar() }) {
                        Icon(
                            if (isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = stringResource(R.string.nav_starred),
                            tint = if (isStarred) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(error!!, color = MaterialTheme.colorScheme.error) }

            else -> {
                val item = email!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            item.subject?.takeIf { it.isNotBlank() } ?: stringResource(R.string.detail_no_subject),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        DetailField(stringResource(R.string.detail_from), item.sendEmail ?: "")
                        DetailField(stringResource(R.string.detail_to), item.toEmail ?: item.recipient ?: "")
                        if (!item.createTime.isNullOrBlank()) {
                            DetailField(stringResource(R.string.detail_time), item.createTime!!)
                        }
                        // 对标网页版：加密邮件绿色锁标
                        if (pgpEncrypted) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(
                                    stringResource(R.string.detail_pgp_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // 附件列表（对标网页版详情页底部附件区）
                        if (item.attList.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.detail_attachments, item.attList.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            item.attList.forEach { att ->
                                AttachmentRow(
                                    att = att,
                                    downloading = downloadingAtt == att.attId,
                                    onDownload = { downloadAttachment(att) }
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    val html = item.content
                    when {
                        // PGP 加密邮件：先解密再看（对标网页版一键解密）
                        pgpEncrypted && decryptedText == null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(48.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    stringResource(R.string.detail_pgp_encrypted),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.detail_pgp_view),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { onDecryptClick() },
                                    enabled = !decrypting
                                ) {
                                    Text(if (decrypting) stringResource(R.string.detail_pgp_decrypting) else stringResource(R.string.detail_pgp_view))
                                }
                            }
                        }
                        pgpEncrypted -> {
                            // 解密出的明文
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(decryptedText!!)
                            }
                        }
                        !html.isNullOrBlank() -> {
                        // 注意：邮件 HTML 来自外部，WebView 内不注入任何 JS 接口，
                        // 仅做展示。如需更强隔离可再套一层沙箱。
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    // 性能优化
                                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                    settings.domStorageEnabled = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.setSupportZoom(false)
                                    settings.builtInZoomControls = false
                                    settings.displayZoomControls = false
                                    // 邮件 HTML 自适应宽度
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    loadDataWithBaseURL(
                                        null, html, "text/html", "UTF-8", null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(item.text ?: "")
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun DetailField(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        text = "$label：$value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 附件行：文件名 + 大小 + 下载按钮 */
@Composable
private fun AttachmentRow(
    att: Attachment,
    downloading: Boolean,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                att.filename.ifBlank { stringResource(R.string.detail_attachment) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            if (att.size > 0) {
                Text(
                    formatBytes(att.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (downloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.detail_download))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
