package com.opencode.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.Agent
import com.opencode.mobile.data.FileDiff
import com.opencode.mobile.data.ModelRef
import com.opencode.mobile.data.OpencodeCommand
import com.opencode.mobile.data.Provider
import com.opencode.mobile.data.Todo

// ---------------------------------------------------------------------------
// Session extras: real pickers and viewers for every server feature.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    agents: List<Agent>,
    providers: List<Provider>,
    currentAgent: String,
    currentModel: ModelRef?,
    loading: Boolean,
    onPickAgent: (String) -> Unit,
    onPickModel: (String, String) -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Assistant & model",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Text("Assistant", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentAgent == "build",
                    onClick = { onPickAgent("build") },
                    label = { Text("Build") }
                )
                FilterChip(
                    selected = currentAgent == "plan",
                    onClick = { onPickAgent("plan") },
                    label = { Text("Plan") }
                )
                agents.filter { it.name != "build" && it.name != "plan" }.take(6).forEach { a ->
                    FilterChip(
                        selected = currentAgent == a.name,
                        onClick = { onPickAgent(a.name) },
                        label = { Text(a.name) }
                    )
                }
            }
            Text(
                if (currentAgent == "plan") "Plan suggests changes without making them."
                else "Build can read, edit and run commands.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search models") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (loading) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    val q = query.trim().lowercase()
                    items(
                        providers.filter {
                            q.isBlank() || it.name.lowercase().contains(q) || it.id.lowercase().contains(q) ||
                                it.models.values.any { m ->
                                    m.id.lowercase().contains(q) || m.name.lowercase().contains(q)
                                }
                        },
                        key = { it.id }
                    ) { p ->
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { expanded = if (expanded == p.id) null else p.id }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    p.name.ifBlank { p.id },
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${p.models.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    if (expanded == p.id) Icons.Filled.KeyboardArrowDown
                                    else Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (expanded == p.id) {
                                p.models.values.sortedBy { it.name.ifBlank { it.id } }.forEach { m ->
                                    val id = m.id.ifBlank { m.name }
                                    val selected = currentModel?.providerID == p.id &&
                                        currentModel.modelID == id
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { onPickModel(p.id, id) }
                                            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                m.name.ifBlank { id },
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                id,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selected) {
                                            Text(
                                                "✓",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUseDefault) { Text("Server default") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandSheet(
    commands: List<OpencodeCommand>,
    loading: Boolean,
    onRun: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf<OpencodeCommand?>(null) }
    var args by remember(picked) { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Run slash command", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (loading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else if (commands.isEmpty()) {
                Text(
                    "No custom commands on this server. Built-ins still work by typing / in the chat box.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(commands, key = { it.name }) { c ->
                        val selected = picked?.name == c.name
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { picked = if (selected) null else c }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "/${c.name}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                            if (c.description.isNotBlank()) {
                                Text(
                                    c.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            picked?.let { c ->
                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text("Arguments for /${c.name} (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { onRun(c.name, args) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Run /${c.name}") }
            }
        }
    }
}

@Composable
fun DiffsDialog(
    diffs: List<FileDiff>,
    loading: Boolean,
    onDismiss: () -> Unit
) {
    var open by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (loading) "Loading changes…"
                else if (diffs.isEmpty()) "No changes yet"
                else {
                    val add = diffs.sumOf { it.additions }
                    val del = diffs.sumOf { it.deletions }
                    "${diffs.size} files  +$add −$del"
                }
            )
        },
        text = {
            if (diffs.isEmpty() && !loading) {
                Text(
                    "Nothing changed in this session so far. Edits the assistant makes will show up here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(diffs, key = { it.file }) { d ->
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { open = if (open == d.file) null else d.file }
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.file, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "+${d.additions} −${d.deletions}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (open == d.file) Icons.Filled.KeyboardArrowDown
                                    else Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            }
                            if (open == d.file) {
                                if (d.before.isNotBlank() && d.before != d.after) {
                                    Text(
                                        "Before",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        d.before.take(3000),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Text(
                                    "After",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    d.after.take(4000).ifBlank { "(emptied)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosSheet(
    todos: List<Todo>,
    onDismiss: () -> Unit
) {
    val done = todos.count { it.status == "completed" }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Tasks ($done of ${todos.size} done)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(todos, key = { it.id }) { t ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        when (t.status) {
                            "completed" -> Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            "in_progress" -> CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            "cancelled" -> Text(
                                "✕",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> Text(
                                "○",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.content, style = MaterialTheme.typography.bodyMedium)
                            val meta = listOf(t.status.replace('_', ' '), t.priority)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
