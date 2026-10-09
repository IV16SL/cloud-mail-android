package me.huanjue.cloudmail.ui.deleted

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.huanjue.cloudmail.R
import me.huanjue.cloudmail.ui.mail.EmailRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedScreen(
    viewModel: DeletedViewModel,
    onMenuClick: () -> Unit,
    onOpenEmail: (accountId: Long, emailId: Long, type: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_deleted)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.emails.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_deleted),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.emails, key = { it.emailId }) { email ->
                        Column {
                            EmailRow(
                                email = email,
                                onClick = {
                                    val accountId = state.currentAccount?.accountId ?: return@EmailRow
                                    onOpenEmail(accountId, email.emailId, 0)
                                }
                            )
                            // 恢复和彻底删除按钮
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                            ) {
                                IconButton(onClick = { viewModel.restore(email.emailId) }) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore")
                                }
                                IconButton(onClick = { viewModel.permanentDelete(email.emailId) }) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = "Delete forever",
                                        tint = androidx.compose.ui.graphics.Color.Red
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
