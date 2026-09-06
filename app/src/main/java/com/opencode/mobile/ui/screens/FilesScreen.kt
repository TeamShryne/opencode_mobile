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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.ui.components.PremiumTopBar
import com.opencode.mobile.ui.viewmodel.FilesViewModel

@Composable
fun FilesScreen(vm: FilesViewModel) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.browse(null) }
    Column(Modifier.fillMaxSize()) {
        PremiumTopBar("Files", "GET /file • /file/content • /file/status • /find • /find/file • /find/symbol")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.query, onValueChange = { vm.search(it) },
                label = { Text("Find files (GET /find/file?query=)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            if (ui.searchHits.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Results (${ui.searchHits.size})", fontWeight = FontWeight.Bold)
                        ui.searchHits.take(20).forEach { Text(it, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (ui.status.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Git status (${ui.status.size})", fontWeight = FontWeight.Bold)
                            ui.status.take(15).forEach {
                                Text("${it.status} ${it.path} (+${it.added}/-${it.removed})", fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
            items(ui.nodes) { n ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text((if (n.type == "directory") "Dir " else "File ") + n.name, fontWeight = FontWeight.SemiBold)
                        Text(n.path, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        if (n.type == "file") {
                            Button(onClick = { vm.preview(n.path) }) { Text("Read (GET /file/content)") }
                        } else {
                            Button(onClick = { vm.browse(n.path) }) { Text("Open") }
                        }
                    }
                }
            }
        }
        if (ui.preview != null) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Preview: ${ui.previewPath}", fontWeight = FontWeight.Bold)
                    Text(ui.preview.toString().take(2000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
    }
}
