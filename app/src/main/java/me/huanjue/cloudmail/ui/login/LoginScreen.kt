package me.huanjue.cloudmail.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.huanjue.cloudmail.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    isAddMode: Boolean = false,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val websiteConfig by viewModel.websiteConfig.collectAsState()
    val loginDone by viewModel.loginDone.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }
    var autoPrompted by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loginDone) {
        if (loginDone) {
            viewModel.consumeLoginDone()
            onLoginSuccess()
        }
    }

    // 首次启动没填服务器地址时自动弹出设置（添加账号模式下服务器已确定，不弹）
    LaunchedEffect(serverUrl) {
        if (!isAddMode && serverUrl.isBlank() && !autoPrompted) {
            autoPrompted = true
            showServerDialog = true
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is LoginUiState.Error) {
            scope.launch { snackbarHostState.showSnackbar(state.message) }
            viewModel.backToIdle()
        }
    }

    if (showServerDialog) {
        ServerUrlDialog(
            current = serverUrl,
            onDismiss = { showServerDialog = false },
            onSave = { input ->
                viewModel.saveServerUrl(input) { ok, err ->
                    if (ok) {
                        showServerDialog = false
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(err ?: "保存失败") }
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isAddMode) "添加账号" else websiteConfig?.title ?: "Cloud Mail") },
                navigationIcon = {
                    if (isAddMode) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = {
                    // 添加账号模式下服务器已确定，不显示服务器设置
                    if (!isAddMode) {
                        IconButton(onClick = { showServerDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "服务器设置")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val state = uiState
            if (state is LoginUiState.NeedTotp) {
                Text("两步验证", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("请输入验证器 App 中的 6 位验证码")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = totpCode,
                    onValueChange = { totpCode = it },
                    label = { Text("验证码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.loginTotp(state.preAuthToken, totpCode) },
                    enabled = totpCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("验证并登录")
                }
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App 图标",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.height(16.dp))
                Text("登录", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                if (serverUrl.isBlank()) {
                    Text(
                        "请先点右上角设置填写服务器地址",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.login(email, password) },
                    enabled = state !is LoginUiState.Loading &&
                        email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state is LoginUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text("登录")
                }
            }
        }
    }
}

@Composable
fun ServerUrlDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var input by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器地址") },
        text = {
            Column {
                Text("填写你部署的 cloud-mail 地址，所有接口都走这个地址。")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://mail.example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
