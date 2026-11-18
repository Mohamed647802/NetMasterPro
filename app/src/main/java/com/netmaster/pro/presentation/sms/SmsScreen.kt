package com.netmaster.pro.presentation.sms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netmaster.pro.R
import com.netmaster.pro.data.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmsScreen(
    viewModel: SmsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val ussdResult by viewModel.ussdResult.collectAsState()
    val pendingDeleteId by viewModel.pendingDeleteId.collectAsState()

    var showComposeDialog by remember { mutableStateOf(false) }
    var showUssdSheet by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Delete confirmation dialog
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.sms_delete_confirm_title)) },
            text = { Text(stringResource(R.string.sms_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete(pendingDeleteId!!) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Compose dialog
    if (showComposeDialog) {
        ComposeMessageDialog(
            onSend = { to, body ->
                viewModel.sendSms(to, body)
                showComposeDialog = false
            },
            onDismiss = { showComposeDialog = false }
        )
    }

    // USSD bottom sheet
    if (showUssdSheet) {
        UssdSheet(
            ussdResult = ussdResult,
            onSendUssd = { viewModel.sendUssd(it) },
            onDismiss = {
                showUssdSheet = false
                viewModel.clearUssdResult()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sms_screen_title)) },
                actions = {
                    IconButton(onClick = { viewModel.loadMessages() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { showUssdSheet = true }) {
                        Icon(Icons.Default.Phone, contentDescription = stringResource(R.string.sms_ussd_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showComposeDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.sms_compose_new))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab: Inbox / Sent
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.sms_tab_inbox)) },
                    icon = { Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.sms_tab_sent)) },
                    icon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (uiState) {
                is SmsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is SmsUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.sms_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is SmsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = (uiState as SmsUiState.Error).message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                is SmsUiState.Success -> {
                    val allMessages = (uiState as SmsUiState.Success).messages
                    val filtered = if (selectedTab == 0)
                        allMessages.filter { it.isInbox }
                    else
                        allMessages.filter { !it.isInbox }

                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = filtered,
                            key = { it.id }
                        ) { message ->
                            SwipeToDeleteMessage(
                                message = message,
                                onDelete = { viewModel.requestDelete(message.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteMessage(
    message: SmsMessage,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    AnimatedVisibility(
        visible = visible,
        exit = slideOutHorizontally(animationSpec = tween(300)) { -it },
        modifier = modifier
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            },
            enableDismissFromStartToEnd = false
        ) {
            SmsMessageCard(message = message)
        }
    }
}

@Composable
private fun SmsMessageCard(message: SmsMessage) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!message.isRead && message.isInbox)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.from,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.Normal
                        )
                        if (!message.isRead && message.isInbox) {
                            Badge(
                                modifier = Modifier.padding(start = 6.dp)
                            ) { Text("") }
                        }
                    }
                    Text(
                        text = dateFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComposeMessageDialog(
    onSend: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var to by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sms_compose_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text(stringResource(R.string.sms_compose_to)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.sms_compose_message)) },
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (to.isNotBlank() && body.isNotBlank()) onSend(to, body) },
                enabled = to.isNotBlank() && body.isNotBlank()
            ) {
                Text(stringResource(R.string.sms_send_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

data class UssdPreset(val name: String, val code: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UssdSheet(
    ussdResult: UssdResult,
    onSendUssd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        UssdPreset("Balance", "*100#"),
        UssdPreset("Data Bundle", "*140#"),
        UssdPreset("My Number", "*111*8#"),
        UssdPreset("Recharge", "*150*VOUCHER#"),
        UssdPreset("Offers", "*4444#")
    )
    var customCode by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.sms_ussd_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Presets
            Text(stringResource(R.string.sms_ussd_presets), style = MaterialTheme.typography.labelMedium)
            presets.forEach { preset ->
                Card(
                    onClick = { onSendUssd(preset.code) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(preset.name, fontWeight = FontWeight.Medium)
                        Text(preset.code, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Divider()

            // Custom code
            Text(stringResource(R.string.sms_ussd_custom), style = MaterialTheme.typography.labelMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customCode,
                    onValueChange = { customCode = it },
                    label = { Text(stringResource(R.string.sms_ussd_enter_code)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Button(
                    onClick = { if (customCode.isNotBlank()) onSendUssd(customCode) },
                    enabled = customCode.isNotBlank()
                ) {
                    Text(stringResource(R.string.sms_send_button))
                }
            }

            // Result
            when (ussdResult) {
                is UssdResult.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UssdResult.Response -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.sms_ussd_response),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(ussdResult.reply, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is UssdResult.Error -> {
                    Text(
                        ussdResult.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {}
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
