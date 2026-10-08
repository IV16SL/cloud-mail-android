package me.huanjue.cloudmail.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 邮件标签页：收件箱 / 已发送 / 草稿箱 / 星标
 * 放在 TopAppBar 的 title 位置，替代原来的邮箱地址
 */
@Composable
fun MailTabs(
    current: DrawerDestination,
    onSelect: (DrawerDestination) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        DrawerDestination.values().forEach { d ->
            TextButton(onClick = { onSelect(d) }) {
                Text(
                    text = stringResource(d.titleRes),
                    style = if (d == current) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (d == current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
