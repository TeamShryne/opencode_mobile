package com.opencode.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.ui.components.PremiumTopBar
import kotlinx.coroutines.launch

@Composable
fun InfraScreen(repo: OpencodeRepository) {
    var output by remember { mutableStateOf("PTY + TUI + logs live here.") }
    var ptyCmd by remember { mutableStateOf("") }
    var toastMsg by remember { mutableStateOf("Hello from Android") }
    var appendText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sseEvents by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PremiumTopBar("Terminal & TUI", "GET/POST /pty • POST /log • POST /tui/* • GET /event")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PTY (remote terminal)", fontWeight = FontWeight.Bold)
            OutlinedTextField(value = ptyCmd, onValueChange = { ptyCmd = it }, label = { Text("command (empty = default shell)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch {
                    val list = repo.ptys()
                    output = "PTYs (${list.size}):\n" + list.take(10).joinToString("\n") { "${it.id.take(6)} ${it.title} ${it.status} pid=${it.pid}" }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("List PTYs (GET /pty)") }
            Button(onClick = {
                scope.launch {
                    val p = repo.createPty(ptyCmd.ifBlank { null })
                    output = if (p == null) "create failed" else "created ${p.id} ${p.title} pid=${p.pid}"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Create PTY (POST /pty)") }
            Button(onClick = {
                scope.launch {
                    val ok = repo.log("opencode-mobile", "info", "ping from Android")
                    output = "POST /log -> $ok"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Send log (POST /log)") }

            Text("TUI remote control", fontWeight = FontWeight.Bold)
            OutlinedTextField(value = appendText, onValueChange = { appendText = it }, label = { Text("Append to TUI prompt") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { output = "append -> " + repo.tuiAppend(appendText) } }, modifier = Modifier.fillMaxWidth()) {
                Text("Append prompt (POST /tui/append-prompt)")
            }
            OutlinedTextField(value = toastMsg, onValueChange = { toastMsg = it }, label = { Text("Toast message") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { output = "toast -> " + repo.tuiToast(toastMsg, "success") } }, modifier = Modifier.fillMaxWidth()) {
                Text("Toast (POST /tui/show-toast)")
            }
            listOf("help", "sessions", "themes", "models", "submit", "clear").forEach { a ->
                Button(onClick = { scope.launch { output = "$a -> " + repo.tuiSimple(a) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("TUI: $a")
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Output", fontWeight = FontWeight.Bold)
                    Text(output, style = MaterialTheme.typography.bodySmall)
                    if (sseEvents.isNotBlank()) Text(sseEvents, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("Realtime: GET /event streams server.connected + message/session/permission/todo/file/pty/tui events. The chat auto-refreshes on those.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
