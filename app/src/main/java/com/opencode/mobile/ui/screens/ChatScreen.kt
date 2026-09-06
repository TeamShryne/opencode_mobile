package com.opencode.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.ui.components.MessageBubble
import com.opencode.mobile.ui.viewmodel.ChatViewModel
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ChatScreen(vm: ChatViewModel, sessionId: String, onForked: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(sessionId) { vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Session ${sessionId.take(8)}", fontWeight = FontWeight.Bold)
                Text(
                    "Model: ${ui.model?.providerID ?: "server default"}/${ui.model?.modelID ?: ""}  •  Agent: ${ui.agent}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "POST /session/{id}/message streams via GET /event (message.part.updated deltas). Slash-commands use POST /command path; shell uses POST /shell.",
                    style = MaterialTheme.typography.labelSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.abort() }) { Text("Abort") }
                    TextButton(onClick = { vm.share() }) { Text("Share") }
                    TextButton(onClick = { vm.refresh() }) { Text("Refresh") }
                }
                ui.shareUrl?.let { Text("Shared: $it", color = MaterialTheme.colorScheme.secondary) }
                if (ui.todos.isNotEmpty()) {
                    Text("Todos (${ui.todos.size}):", fontWeight = FontWeight.SemiBold)
                    ui.todos.take(5).forEach { t ->
                        Text("• [${t.status}] ${t.content.take(120)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(ui.messages) { m ->
                val msgId = m.info["id"]?.jsonPrimitive?.content ?: ""
                MessageBubble(
                    message = m,
                    onRevert = { vm.revert(msgId) },
                    onFork = { vm.fork(msgId.ifBlank { null }, onForked) }
                )
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message, /command args, or !shell…") },
                label = { Text(if (ui.sending) "Sending…" else "Prompt") }
            )
            Button(
                onClick = {
                    val t = input.trim()
                    if (t.startsWith("/")) {
                        val parts = t.removePrefix("/").split(" ", limit = 2)
                        vm.runSlashCommand(parts[0], parts.getOrElse(1) { "" })
                    } else {
                        vm.send(t)
                    }
                    input = ""
                },
                enabled = !ui.sending && input.isNotBlank()
            ) { Text("Send") }
        }
    }
}
