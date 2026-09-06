package com.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.SessionMessageDto
import kotlinx.serialization.json.jsonPrimitive

private fun partType(p: kotlinx.serialization.json.JsonObject): String =
    p["type"]?.jsonPrimitive?.content ?: "unknown"

private fun partText(p: kotlinx.serialization.json.JsonObject): String {
    val t = p["text"]?.jsonPrimitive?.content
    if (t != null) return t
    val out = p["output"]?.jsonPrimitive?.content
    if (out != null) return out
    val err = p["error"]?.jsonPrimitive?.content
    if (err != null) return "Error: $err"
    val title = p["title"]?.jsonPrimitive?.content
    val tool = p["tool"]?.jsonPrimitive?.content
    if (title != null || tool != null) return listOfNotNull(tool, title).joinToString(" — ")
    return p.toString().take(600)
}

@Composable
fun MessageBubble(
    message: SessionMessageDto,
    onRevert: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null
) {
    val role = message.info["role"]?.jsonPrimitive?.content ?: "unknown"
    val isUser = role == "user"
    val container = if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isUser) "You" else "Opencode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                val cost = message.info["cost"]?.jsonPrimitive?.content
                if (cost != null && !isUser) {
                    Text(
                        text = "cost $cost",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (message.parts.isEmpty()) {
                Text(
                    "(empty message)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            message.parts.forEach { part ->
                when (partType(part)) {
                    "text" -> Text(
                        text = partText(part),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    "reasoning" -> Text(
                        text = "Reasoning: " + partText(part).take(800),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    "tool" -> {
                        val tool = part["tool"]?.jsonPrimitive?.content ?: "tool"
                        val state = part["state"]?.toString()?.take(120) ?: ""
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    "Tool: $tool",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    partText(part).take(900),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (state.isNotBlank()) {
                                    Text(
                                        state,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    "file" -> Text(
                        "Attachment: " + (part["filename"]?.jsonPrimitive?.content
                            ?: part["url"]?.jsonPrimitive?.content
                            ?: "file"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    "patch" -> Text(
                        "Patch: " + (part["files"]?.toString()?.take(400) ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    "agent" -> Text(
                        "Agent: " + (part["name"]?.jsonPrimitive?.content ?: ""),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    "retry", "compaction", "step-start", "step-finish", "snapshot", "subtask" ->
                        Text(
                            partType(part) + ": " + partText(part).take(400),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    else -> Text(
                        partType(part) + ": " + partText(part).take(400),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            if (onRevert != null || onFork != null) {
                Row {
                    onRevert?.let {
                        TextButton(onClick = it) { Text("Revert") }
                    }
                    onFork?.let {
                        TextButton(onClick = it) { Text("Fork here") }
                    }
                }
            }
        }
    }
}
