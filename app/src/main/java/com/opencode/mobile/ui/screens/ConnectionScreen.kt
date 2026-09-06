package com.opencode.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.ui.viewmodel.ConnectionViewModel

@Composable
fun ConnectionScreen(vm: ConnectionViewModel, onConnected: () -> Unit) {
    val ui by vm.ui.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Opencode", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text(
            "Your AI coding assistant, running on your computer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On your computer, run:", fontWeight = FontWeight.Bold)
                Text(
                    "opencode serve --hostname 0.0.0.0 --port 4096",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Then enter its address below. Both devices need to be on the same network.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        OutlinedTextField(
            value = ui.baseUrl, onValueChange = { vm.update(it, ui.username, ui.password) },
            label = { Text("Server address") }, placeholder = { Text("http://192.168.1.10:4096") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = ui.username, onValueChange = { vm.update(ui.baseUrl, it, ui.password) },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = ui.password, onValueChange = { vm.update(ui.baseUrl, ui.username, it) },
            label = { Text("Password (only if you set one)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { vm.connect(onConnected) },
            enabled = !ui.connecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (ui.connecting) CircularProgressIndicator(Modifier.size(20.dp))
            else Text("Connect")
        }
        if (ui.connected) {
            Text("Connected", color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}
