package com.opencode.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.mobile.OpencodeApp
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.SseState
import com.opencode.mobile.ui.viewmodel.ChatUiState
import com.opencode.mobile.ui.viewmodel.ChatViewModel
import com.opencode.mobile.ui.viewmodel.SessionsViewModel
import kotlinx.coroutines.launch

private fun <T : ViewModel> singleFactory(create: () -> T) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <M : ViewModel> create(modelClass: Class<M>): M = create() as M
    }

/**
 * Typical coding-agent home: history drawer + one thread + composer.
 * Advanced server features (share, branch, undo, stop, model override)
 * live behind the overflow menu and the assistant picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentHomeScreen(
    repo: OpencodeRepository,
    initialSessionId: String?,
    onDisconnect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val snacks = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val app = LocalContext.current.applicationContext as OpencodeApp

    // Live event stream for this server (auto-reconnects). Drives streaming
    // replies, tool states, statuses, todos and permission prompts.
    LaunchedEffect(repo.settings.baseUrl) { app.ensureSse() }
    val sseState by app.sse.state.collectAsState()

    val sessionsVm: SessionsViewModel =
        viewModel(factory = singleFactory { SessionsViewModel(repo, app.sse.events) })
    val sessionsUi by sessionsVm.ui.collectAsState()
    LaunchedEffect(Unit) { sessionsVm.refresh() }

    var selectedId by remember(initialSessionId) { mutableStateOf(initialSessionId) }
    var pending by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val chatVm: ChatViewModel? = selectedId?.let { id ->
        viewModel(key = "chat-$id", factory = singleFactory { ChatViewModel(repo, id, app.sse.events) })
    }
    val chatUi: ChatUiState by if (chatVm != null) chatVm.ui.collectAsState()
    else remember { mutableStateOf(ChatUiState()) }

    // Opened a chat (or just created one): either deliver the queued first
    // message or load the thread.
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        val first = pending
        pending = null
        if (first != null) chatVm?.sendSmart(first) else chatVm?.refresh()
    }

    val busy = chatUi.sending || chatUi.liveBusy ||
        (selectedId?.let { sessionsUi.statuses[it]?.type } == "busy")
    val title = selectedId?.let { id ->
        sessionsUi.sessions.firstOrNull { it.id == id }?.title?.ifBlank { null }
    } ?: "New chat"

    // Share link -> copy + confirm, same API as before.
    LaunchedEffect(chatUi.shareUrl) {
        chatUi.shareUrl?.let { url ->
            clipboard.setText(AnnotatedString(url))
            scope.launch { snacks.showSnackbar("Share link copied") }
        }
    }

    fun submit() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        val id = selectedId
        if (id == null) {
            pending = text
            sessionsVm.create(null) { newId -> selectedId = newId }
        } else {
            chatVm?.sendSmart(text)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            SessionsDrawer(
                sessions = sessionsUi.sessions,
                selectedId = selectedId,
                loading = sessionsUi.loading,
                serverLabel = repo.settings.baseUrl,
                onNew = {
                    selectedId = null
                    scope.launch { drawer.close() }
                },
                onOpen = {
                    selectedId = it
                    scope.launch { drawer.close() }
                },
                onDelete = { sessionsVm.delete(it) },
                onSwitchServer = onDisconnect
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snacks) },
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Chat history")
                            }
                        },
                        title = {
                            Column {
                                Text(title, maxLines = 1)
                                if (sseState != SseState.LIVE) {
                                    Text(
                                        "Reconnecting…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { selectedId = null }) {
                                Icon(Icons.Filled.Add, contentDescription = "New chat")
                            }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Share chat") },
                                    enabled = selectedId != null,
                                    onClick = { menuOpen = false; chatVm?.share() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    enabled = selectedId != null,
                                    onClick = { menuOpen = false; chatVm?.refresh() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stop response") },
                                    enabled = busy,
                                    onClick = { menuOpen = false; chatVm?.abort() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Switch server") },
                                    onClick = { menuOpen = false; onDisconnect() }
                                )
                            }
                        }
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (chatUi.todos.isNotEmpty()) {
                        val done = chatUi.todos.count { it.status == "completed" }
                        Text(
                            "$done of ${chatUi.todos.size} tasks done",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    chatUi.pendingPermissions.firstOrNull()?.let { perm ->
                        PermissionCard(
                            title = perm.title,
                            kind = perm.kind,
                            onAllow = { chatVm?.respondPermission(perm.id, "once") },
                            onAlways = { chatVm?.respondPermission(perm.id, "always") },
                            onDeny = { chatVm?.respondPermission(perm.id, "reject") }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { sheetOpen = true },
                            label = {
                                Text(chatUi.model?.let { "${it.providerID}/${it.modelID}" }
                                    ?: "Assistant: ${chatUi.agent}")
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask anything…  ( / for commands )") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 5
                        )
                        FilledIconButton(
                            onClick = { submit() },
                            enabled = input.isBlank().not() && !busy
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send")
                        }
                    }
                    chatUi.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedId == null && chatUi.messages.isEmpty()) {
                    WelcomePane(
                        onSuggestion = { input = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ChatThread(
                        messages = chatUi.messages,
                        streaming = busy,
                        onRevert = { msgId -> chatVm?.revert(msgId) },
                        onFork = { msgId ->
                            chatVm?.fork(msgId.ifBlank { null }) { newId ->
                                selectedId = newId
                                sessionsVm.refresh()
                            }
                        }
                    )
                }
            }
        }
    }

    if (sheetOpen && chatVm != null) {
        AssistantSheet(
            agent = chatUi.agent,
            modelText = chatUi.model?.let { "${it.providerID}/${it.modelID}" } ?: "",
            onDismiss = { sheetOpen = false },
            onSave = { agent, modelText ->
                chatVm.pickAgent(agent)
                val m = modelText.trim()
                if (m.isEmpty() || !m.contains("/")) chatVm.clearModel()
                else {
                    val (prov, mid) = m.split("/", limit = 2)
                    if (prov.isNotBlank() && mid.isNotBlank()) chatVm.pickModel(prov.trim(), mid.trim())
                    else chatVm.clearModel()
                }
                sheetOpen = false
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    kind: String,
    onAllow: () -> Unit,
    onAlways: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Needs your approval", fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                kind,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("Allow") }
                TextButton(onClick = onAlways) { Text("Always") }
                TextButton(onClick = onDeny) { Text("Deny") }
            }
        }
    }
}

@Composable
private fun WelcomePane(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "What should we build?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Connected to your opencode server. Just describe what you want.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onSuggestion("Explain how this project works") },
                label = { Text("Explain this project") })
            AssistChip(onClick = { onSuggestion("Find and fix the bug in ") },
                label = { Text("Fix a bug") })
            AssistChip(onClick = { onSuggestion("Add a new feature: ") },
                label = { Text("Add a feature") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantSheet(
    agent: String,
    modelText: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var picked by remember(agent) { mutableStateOf(agent.ifBlank { "build" }) }
    var model by remember(modelText) { mutableStateOf(modelText) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = picked == "build",
                    onClick = { picked = "build" },
                    label = { Text("Build") }
                )
                FilterChip(
                    selected = picked == "plan",
                    onClick = { picked = "plan" },
                    label = { Text("Plan") }
                )
            }
            Text(
                if (picked == "plan") "Plan suggests changes without making them."
                else "Build can read, edit and run commands.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model (optional)") },
                placeholder = { Text("e.g. anthropic/claude-sonnet-4-5") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSave(picked, "") }) { Text("Use server default") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onSave(picked, model) }) { Text("Done") }
                }
            Spacer(Modifier.height(16.dp))
        }
    }
}
