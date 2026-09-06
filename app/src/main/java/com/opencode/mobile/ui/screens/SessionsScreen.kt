package com.opencode.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.Session

/** Slim history drawer: chats only, no server jargon. */
@Composable
fun SessionsDrawer(
    sessions: List<Session>,
    selectedId: String?,
    loading: Boolean,
    serverLabel: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSwitchServer: () -> Unit
) {
    ModalDrawerSheet {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (sessions.isEmpty() && !loading) {
                item {
                    Text(
                        "No chats yet. Start one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(sessions, key = { it.id }) { s ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            s.title.ifBlank { "Untitled chat" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = s.id == selectedId,
                    onClick = { onOpen(s.id) },
                    badge = {
                        IconButton(onClick = { onDelete(s.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                serverLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNew) { Text("New chat") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onSwitchServer) { Text("Switch server") }
            }
        }
    }
}
