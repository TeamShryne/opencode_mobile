package com.opencode.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.opencode.mobile.ui.components.EmptyState
import com.opencode.mobile.ui.components.PremiumTopBar
import com.opencode.mobile.ui.components.StatusDot
import com.opencode.mobile.ui.viewmodel.SessionsViewModel

@Composable
fun SessionsScreen(vm: SessionsViewModel, onOpen: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    var title by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        PremiumTopBar("Sessions", "GET/POST/PATCH/DELETE /session + fork/share/abort/diff/todo")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("New session title (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(onClick = { vm.create(title.ifBlank { null }, onOpen) }, modifier = Modifier.fillMaxWidth()) {
                Text("Create session (POST /session)")
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (ui.sessions.isEmpty() && !ui.loading) {
            EmptyState("No sessions yet", "Create one above, or continue from the TUI. Pull to refresh via the button below.")
        }
        LazyColumn {
            items(ui.sessions) { s ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.title.ifBlank { s.id.take(8) }, fontWeight = FontWeight.Bold)
                        Text(s.id, style = MaterialTheme.typography.labelSmall)
                        val st = ui.statuses[s.id]?.type ?: "idle"
                        StatusDot(st)
                        Button(onClick = { onOpen(s.id) }) { Text("Open chat") }
                        Button(onClick = { vm.delete(s.id) }) { Text("Delete") }
                    }
                }
            }
        }
        Button(onClick = { vm.refresh() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (ui.loading) "Loading…" else "Refresh (GET /session + /session/status)")
        }
    }
}
