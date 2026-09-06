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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.ui.components.EmptyState
import com.opencode.mobile.ui.components.PremiumTopBar
import com.opencode.mobile.ui.viewmodel.CatalogViewModel

@Composable
fun ProvidersScreen(vm: CatalogViewModel, onPickModel: (String, String) -> Unit) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    Column(Modifier.fillMaxSize()) {
        PremiumTopBar("Models & Agents", "GET /provider • /config/providers • /agent • /command • /experimental/tool/ids")
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Providers (${ui.providers.size}) — connected: ${ui.connected.joinToString()}", fontWeight = FontWeight.Bold)
                    Text("Auth: PUT /auth/{id}, OAuth: POST /provider/{id}/oauth/*", style = MaterialTheme.typography.labelSmall)
                }
            }
            items(ui.providers) { p ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.name.ifBlank { p.id }, fontWeight = FontWeight.Bold)
                        Text("id=${p.id} • ${p.models.size} models • source=${p.source}", style = MaterialTheme.typography.labelSmall)
                        p.models.values.sortedBy { it.name }.take(6).forEach { m ->
                            Button(onClick = { onPickModel(p.id, m.id) }) {
                                Text("Use ${m.name.ifBlank { m.id }}")
                            }
                        }
                        if (p.models.size > 6) Text("+ ${p.models.size - 6} more", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Text("Agents (${ui.agents.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }
            items(ui.agents) { a ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(a.name, fontWeight = FontWeight.Bold)
                        Text((a.description ?: "") + " • mode=" + a.mode, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("Commands (${ui.commands.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }
            items(ui.commands) { c ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("/" + c.name, fontWeight = FontWeight.Bold)
                        Text(c.description ?: c.template.take(160), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                if (ui.tools.isNotEmpty()) {
                    Text("Tools (${ui.tools.size}): " + ui.tools.take(12).joinToString(), modifier = Modifier.padding(16.dp))
                } else if (!ui.loading) {
                    EmptyState("Loading catalog", "Fetching providers, agents, commands and tools…")
                }
            }
        }
        Button(onClick = { vm.refresh() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (ui.loading) "Loading…" else "Refresh catalog")
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
    }
}
