package me.huanjue.cloudmail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.CloudMailApp
import me.huanjue.cloudmail.data.PgpKeyInfo

/**
 * PGP 私钥管理（对标网页版个人信息页的私钥导入）。
 * 私钥只存本机 EncryptedSharedPreferences，按登录账号隔离，绝不上传服务器。
 * 解密收到的 PGP 邮件需要先在这里导入与发件方对应的私钥。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PgpKeyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val pgpManager = app.container.pgpManager
    val scope = rememberCoroutineScope()

    var keyInfo by remember { mutableStateOf<PgpKeyInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showImport by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    // 生成密钥对
    var showGenerate by remember { mutableStateOf(false) }
    var genName by remember { mutableStateOf("") }
    var genEmail by remember { mutableStateOf("") }
    var genPass1 by remember { mutableStateOf("") }
    var genPass2 by remember { mutableStateOf("") }
    var genKeyType by remember { mutableStateOf("RSA") }
    var generating by remember { mutableStateOf(false) }
    var publicArmor by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    fun snack(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    fun reload() {
        scope.launch {
            loading = true
            keyInfo = try {
                pgpManager.getKeyInfo()
            } catch (_: Exception) {
                null
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { if (!importing) showImport = false },
            title = { Text("导入 PGP 私钥") },
            text = {
                Column {
                    Text(
                        "粘贴私钥的 armor 文本（-----BEGIN PGP PRIVATE KEY BLOCK----- 开头）。" +
                            "可以从网页版用的同一把钥匙复制过来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("-----BEGIN PGP PRIVATE KEY BLOCK-----") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        enabled = !importing
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            importing = true
                            try {
                                val info = pgpManager.importPrivateKey(importText)
                                keyInfo = info
                                importText = ""
                                showImport = false
                                snack("私钥导入成功")
                            } catch (e: Exception) {
                                snack("导入失败：${e.message}")
                            } finally {
                                importing = false
                            }
                        }
                    },
                    enabled = !importing && importText.isNotBlank()
                ) { Text(if (importing) "导入中…" else "导入") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImport = false },
                    enabled = !importing
                ) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除私钥") },
            text = { Text("确定删除本机保存的 PGP 私钥吗？删除后将无法再解密收到的加密邮件。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        pgpManager.deletePrivateKey()
                        keyInfo = null
                        snack("私钥已删除")
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // 生成密钥对对话框
    if (showGenerate) {
        val passMismatch = genPass1 != genPass2
        AlertDialog(
            onDismissRequest = { if (!generating) showGenerate = false },
            title = { Text("生成 PGP 密钥对") },
            text = {
                Column {
                    Text(
                        "在 App 内生成新的 PGP 密钥对，私钥自动保存到本机。公钥需要你手动发布（比如 keys.openpgp.org），否则别人查不到你的公钥、没法给你发加密邮件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("密钥类型", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = genKeyType == "RSA",
                            onClick = { genKeyType = "RSA" },
                            enabled = !generating
                        )
                        Text("RSA 3072（兼容性最好）", modifier = Modifier.clickable(enabled = !generating) { genKeyType = "RSA" })
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = genKeyType == "ED25519",
                            onClick = { genKeyType = "ED25519" },
                            enabled = !generating
                        )
                        Text("Ed25519（更小更快）", modifier = Modifier.clickable(enabled = !generating) { genKeyType = "ED25519" })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genName,
                        onValueChange = { genName = it },
                        label = { Text("姓名（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genEmail,
                        onValueChange = { genEmail = it },
                        label = { Text("邮箱 *") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genPass1,
                        onValueChange = { genPass1 = it },
                        label = { Text("口令（可选，不设则无口令）") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genPass2,
                        onValueChange = { genPass2 = it },
                        label = { Text("重复口令") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passMismatch
                    )
                    if (passMismatch) {
                        Text(
                            "两次输入的口令不一致",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            generating = true
                            try {
                                val pw = genPass1.takeIf { it.isNotEmpty() }?.toCharArray()
                                val (info, pub) = pgpManager.generateKeyPair(genName, genEmail, pw, genKeyType)
                                pw?.fill('0')
                                keyInfo = info
                                publicArmor = pub
                                genName = ""; genEmail = ""; genPass1 = ""; genPass2 = ""; genKeyType = "RSA"
                                showGenerate = false
                                snack("密钥对生成成功")
                            } catch (e: Exception) {
                                snack("生成失败：${e.message}")
                            } finally {
                                generating = false
                            }
                        }
                    },
                    enabled = !generating && genEmail.isNotBlank() && !passMismatch
                ) { Text(if (generating) "生成中…" else "生成") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGenerate = false },
                    enabled = !generating
                ) { Text("取消") }
            }
        )
    }

    // 生成成功后展示公钥（复制去发布）
    val pubKey = publicArmor
    if (pubKey != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { publicArmor = null },
            title = { Text("公钥（请发布）") },
            text = {
                Column {
                    Text(
                        "这是你的公钥。把它发布到 keys.openpgp.org（或配置 WKD），别人才能查到并给你发加密邮件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pubKey,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(pubKey))
                    snack("公钥已复制")
                }) { Text("复制公钥") }
            },
            dismissButton = {
                TextButton(onClick = { publicArmor = null }) { Text("关闭") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("PGP 私钥") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val info = keyInfo
            if (info == null) {
                Text(
                    "还没有导入 PGP 私钥。导入后才能解密别人用你的公钥加密的邮件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showImport = true }) {
                    Text("导入私钥")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showGenerate = true }) {
                    Text("生成新密钥对")
                }
            } else {
                ListItem(
                    headlineContent = { Text("已导入私钥") },
                    supportingContent = {
                        Text(
                            buildString {
                                if (info.userId.isNotBlank()) appendLine(info.userId)
                                appendLine("指纹：${formatFingerprint(info.fingerprint)}")
                                append("Key ID：${info.keyId}")
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showImport = true }) {
                    Text("重新导入")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showGenerate = true }) {
                    Text("生成新密钥对")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("删除私钥", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "说明：私钥只保存在本机加密存储中，不会上传到服务器；" +
                    "按登录账号隔离，切换账号后需要各自导入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 指纹按 4 位一组展示，方便肉眼核对 */
private fun formatFingerprint(fp: String): String =
    fp.chunked(4).joinToString(" ")
