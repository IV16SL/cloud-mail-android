package me.huanjue.cloudmail.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.huanjue.cloudmail.CloudMailApp
import me.huanjue.cloudmail.data.model.MailAccount

/** 抽屉顶部的账号切换头（仿 Gmail）：点开展示已登录账号 + 添加账号 */
@Composable
fun AccountHeader(
    onAddAccount: () -> Unit,
    onSwitchAccount: (userId: Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val container = app.container

    val sessions by container.authRepository.sessions.collectAsState(initial = emptyList())
    val activeSession by container.authRepository.activeSession.collectAsState(initial = null)
    val serverUrl by container.settings.serverUrl.collectAsState(initial = null)

    // 当前登录用户的邮箱账号列表（含网页版设置的显示名 name）
    var accounts by remember { mutableStateOf<List<MailAccount>>(emptyList()) }
    LaunchedEffect(activeSession?.userId) {
        accounts = try {
            container.mailRepository.accounts()
        } catch (_: Exception) {
            emptyList()
        }
    }
    // 显示名：按邮箱匹配账号的 name（网页版可设置），没有则回退到登录邮箱
    val displayName = activeSession?.username?.let { email ->
        accounts.find { it.email.equals(email, ignoreCase = true) }
            ?.name?.takeIf { it.isNotBlank() }
    }

    // 头像：从后端获取用户头像（base64）
    var avatarBase64 by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeSession?.userId) {
        avatarBase64 = try {
            container.authRepository.loginUserInfo().avatar?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
    // base64 转 Bitmap（放后台线程，避免阻塞主线程导致抽屉动画卡顿）
    var avatarBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(avatarBase64) {
        avatarBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val base64Data = avatarBase64?.substringAfter(",", avatarBase64 ?: "")
                if (base64Data.isNullOrBlank()) null
                else {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
    }

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像：有自定义头像显示图片，否则显示名首字
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = avatarBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "avatar",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = (displayName ?: activeSession?.username)?.firstOrNull()?.uppercase() ?: "C",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName ?: activeSession?.username ?: "Cloud Mail",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // 有显示名时第二行显示邮箱，否则显示服务器地址
                    text = if (displayName != null) activeSession?.username ?: "" else serverUrl ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "切换账号"
            )
        }

        if (expanded) {
            sessions.forEach { session ->
                val selected = session.userId == activeSession?.userId
                NavigationDrawerItem(
                    label = {
                        Text(
                            session.username,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        if (selected) Icon(Icons.Default.Check, contentDescription = null)
                        else Icon(Icons.Default.Person, contentDescription = null)
                    },
                    selected = selected,
                    onClick = {
                        expanded = false
                        if (!selected) onSwitchAccount(session.userId)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            NavigationDrawerItem(
                label = { Text("添加账号") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                selected = false,
                onClick = {
                    expanded = false
                    onAddAccount()
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
