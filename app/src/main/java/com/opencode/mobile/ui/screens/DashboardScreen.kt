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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.ui.components.PremiumTopBar
import com.opencode.mobile.ui.components.StatusDot
import com.opencode.mobile.ui.viewmodel.InfraViewModel

@Composable
fun DashboardScreen(vm: InfraViewModel) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PremiumTopBar("Overview", "GET /global/health • /project • /path • /vcs • /config • /mcp • /lsp • /pty")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Workspace", fontWeight = FontWeight.Bold)
                    Text("dir: ${ui.currentDir.ifBlank { "—" }}", fontFamily = FontFamily.Monospace)
                    Text("branch: ${ui.branch.ifBlank { "—" }}", fontFamily = FontFamily.Monospace)
                    Text("projects: ${ui.projects.size}", style = MaterialTheme.typography.bodySmall)
                    ui.projects.take(3).forEach { Text(it.worktree, style = MaterialTheme.typography.labelSmall) }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MCP servers (${ui.mcp.size})", fontWeight = FontWeight.Bold)
                    if (ui.mcp.isEmpty()) Text("none / not reachable", style = MaterialTheme.typography.bodySmall)
                    ui.mcp.entries.take(10).forEach { (k, v) ->
                        StatusDot("${k}: ${v.status}")
                    }
                    Text("LSP: ${ui.lsp.size} • Formatters: ${ui.formatters.size} • PTY: ${ui.ptys.size}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Config (GET /config)", fontWeight = FontWeight.Bold)
                    Text(vm.prettyConfig().take(2500), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { vm.refresh() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.loading) "Loading…" else "Refresh overview")
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
