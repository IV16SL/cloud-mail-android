package me.huanjue.cloudmail.ui.draft

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.CloudMailApp
import me.huanjue.cloudmail.data.Draft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onMenuClick: () -> Unit,
    onEditDraft: (draftId: String) -> Unit,
    onNewDraft: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val draftRepository = app.container.draftRepository
    val scope = rememberCoroutineScope()

    val activeUserId by app.container.authRepository.activeSession
        .collectAsState(initial = null)
    val drafts by draftRepository.drafts.collectAsState(initial = emptyList())

    // 只显示当前登录账号的草稿
    val myDrafts = drafts.filter { it.userId == 0L || it.userId == activeUserId?.userId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("草稿箱") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewDraft) {
                Icon(Icons.Default.Add, contentDescription = "写信")
            }
        }
    ) { padding ->
        if (myDrafts.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("没有草稿", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(myDrafts, key = { it.id }) { draft ->
                    DraftRow(
                        draft = draft,
                        onClick = { onEditDraft(draft.id) },
                        onDelete = {
                            scope.launch { draftRepository.delete(draft.id) }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DraftRow(
    draft: Draft,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.to.ifBlank { "(无收件人)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = draft.subject.ifBlank { "(无主题)" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = dateFmt.format(Date(draft.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除草稿",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
