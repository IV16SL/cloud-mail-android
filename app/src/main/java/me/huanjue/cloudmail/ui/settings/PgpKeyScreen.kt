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
import androidx.compose.ui.res.stringResource
import me.huanjue.cloudmail.R
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
            title = { Text(stringResource(R.string.pgp_import_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.pgp_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text(stringResource(R.string.pgp_import_placeholder)) },
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
                                snack(context.getString(R.string.pgp_import_success))
                            } catch (e: Exception) {
                                snack(context.getString(R.string.pgp_import_failed, e.message ?: ""))
                            } finally {
                                importing = false
                            }
                        }
                    },
                    enabled = !importing && importText.isNotBlank()
                ) { Text(if (importing) stringResource(R.string.pgp_importing) else stringResource(R.string.pgp_import)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImport = false },
                    enabled = !importing
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.pgp_delete_title)) },
            text = { Text(stringResource(R.string.pgp_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        pgpManager.deletePrivateKey()
                        keyInfo = null
                        snack(context.getString(R.string.pgp_deleted))
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 生成密钥对对话框
    if (showGenerate) {
        val passMismatch = genPass1 != genPass2
        AlertDialog(
            onDismissRequest = { if (!generating) showGenerate = false },
            title = { Text(stringResource(R.string.pgp_generate_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.pgp_generate_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.pgp_key_type), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = genKeyType == "RSA",
                            onClick = { genKeyType = "RSA" },
                            enabled = !generating
                        )
                        Text(stringResource(R.string.pgp_key_rsa), modifier = Modifier.clickable(enabled = !generating) { genKeyType = "RSA" })
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = genKeyType == "ED25519",
                            onClick = { genKeyType = "ED25519" },
                            enabled = !generating
                        )
                        Text(stringResource(R.string.pgp_key_ed25519), modifier = Modifier.clickable(enabled = !generating) { genKeyType = "ED25519" })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genName,
                        onValueChange = { genName = it },
                        label = { Text(stringResource(R.string.pgp_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genEmail,
                        onValueChange = { genEmail = it },
                        label = { Text(stringResource(R.string.pgp_email)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genPass1,
                        onValueChange = { genPass1 = it },
                        label = { Text(stringResource(R.string.pgp_passphrase)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genPass2,
                        onValueChange = { genPass2 = it },
                        label = { Text(stringResource(R.string.pgp_passphrase_repeat)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passMismatch
                    )
                    if (passMismatch) {
                        Text(
                            stringResource(R.string.pgp_passphrase_mismatch),
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
                                // 生成后把公钥推给服务端刷新缓存，避免等 7 天
                                try {
                                    app.container.mailRepository.pgpKeyUpload(pub)
                                } catch (_: Exception) {
                                }
                                snack(context.getString(R.string.pgp_generate_success))
                            } catch (e: Exception) {
                                snack(context.getString(R.string.pgp_generate_failed, e.message ?: ""))
                            } finally {
                                generating = false
                            }
                        }
                    },
                    enabled = !generating && genEmail.isNotBlank() && !passMismatch
                ) { Text(if (generating) stringResource(R.string.pgp_generating) else stringResource(R.string.pgp_generate)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGenerate = false },
                    enabled = !generating
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 生成成功后展示公钥（复制去发布）
    val pubKey = publicArmor
    if (pubKey != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { publicArmor = null },
            title = { Text(stringResource(R.string.pgp_public_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.pgp_public_desc),
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
                    snack(context.getString(R.string.pgp_copied))
                }) { Text(stringResource(R.string.pgp_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { publicArmor = null }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pgp_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    stringResource(R.string.pgp_no_key_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showImport = true }) {
                    Text(stringResource(R.string.pgp_import_key))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showGenerate = true }) {
                    Text(stringResource(R.string.pgp_generate_new))
                }
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pgp_has_key)) },
                    supportingContent = {
                        Text(
                            buildString {
                                if (info.userId.isNotBlank()) appendLine(info.userId)
                                appendLine(context.getString(R.string.pgp_fingerprint, formatFingerprint(info.fingerprint)))
                                append(context.getString(R.string.pgp_key_id, info.keyId))
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showImport = true }) {
                    Text(stringResource(R.string.pgp_reimport))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showGenerate = true }) {
                    Text(stringResource(R.string.pgp_generate_new))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(stringResource(R.string.pgp_delete_key), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.pgp_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 指纹按 4 位一组展示，方便肉眼核对 */
private fun formatFingerprint(fp: String): String =
    fp.chunked(4).joinToString(" ")
