package com.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.SessionMessageDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun partType(p: JsonObject): String =
    p["type"]?.jsonPrimitive?.content ?: "unknown"

private fun partText(p: JsonObject): String =
    p["text"]?.jsonPrimitive?.content
        ?: p["output"]?.jsonPrimitive?.content
        ?: p["error"]?.jsonPrimitive?.content
        ?: ""

private data class ToolView(
    val name: String,
    val status: String,
    val title: String,
    val detail: String,
    val isError: Boolean
)

/** Reads the real ToolPart shape: { tool, state: { status, title?, input?, output?, error? } }. */
private fun toolViewOf(part: JsonObject): ToolView {
    val name = part["tool"]?.jsonPrimitive?.content ?: "tool"
    val state = part["state"] as? JsonObject
    val status = state?.get("status")?.jsonPrimitive?.content
        ?: part["status"]?.jsonPrimitive?.content ?: "completed"
    val title = state?.get("title")?.jsonPrimitive?.content
        ?: part["title"]?.jsonPrimitive?.content ?: ""
    val output = state?.get("output")?.jsonPrimitive?.content
        ?: state?.get("error")?.jsonPrimitive?.content
        ?: partText(part)
    val inputHint = (state?.get("input") as? JsonObject)
        ?.entries?.firstOrNull()?.let { (_, v) ->
            v.jsonPrimitive.content.take(140)
        } ?: ""
    val detail = (output.ifBlank { inputHint }).take(400)
    return ToolView(
        name = name,
        status = status,
        title = title.ifBlank { name },
        detail = detail,
        isError = status == "error"
    )
}

/**
 * Typical coding-agent message rendering: user bubble on the right,
 * assistant as plain full-width text, tool activity as one-line rows.
 * Internal part kinds (steps, snapshots, retries, compaction) stay hidden.
 */
@Composable
fun MessageBubble(
    message: SessionMessageDto,
    onRevert: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    isLive: Boolean = false
) {
    val role = message.info["role"]?.jsonPrimitive?.content ?: "unknown"
    if (role == "user") {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = message.parts.filter { partType(it) == "text" }
                        .joinToString("\n\n") { partText(it) }.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val texts = message.parts.filter { partType(it) == "text" }.map { partText(it) }
            .filter { it.isNotBlank() }
        if (texts.isNotEmpty()) {
            Text(
                text = texts.joinToString("\n\n") + if (isLive) " ▍" else "",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        message.parts.filter { partType(it) == "reasoning" }.forEach { part ->
            val t = partText(part).take(400)
            if (t.isNotBlank()) {
                Text(
                    text = t,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        message.parts.filter { partType(it) == "tool" }.forEach { part ->
            val tool = toolViewOf(part)
            Surface(
                color = if (tool.isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    val headline = when (tool.status) {
                        "pending" -> "Queued ${tool.title}"
                        "running" -> "${tool.title} — running…"
                        "error" -> "${tool.title} — failed"
                        else -> tool.title
                    }
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tool.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (tool.status == "running" || tool.status == "pending") {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    if (tool.detail.isNotBlank()) {
                        Text(
                            text = tool.detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (tool.isError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (tool.isError) 6 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
        message.parts.filter { partType(it) == "file" }.forEach { part ->
            val name = part["filename"]?.jsonPrimitive?.content
                ?: part["url"]?.jsonPrimitive?.content ?: "file"
            Text(
                text = "Attached $name",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        message.parts.filter { partType(it) == "patch" }.forEach { part ->
            val files = part["files"]?.jsonArray
            val label = if (files != null) "${files.size} file(s)" else "files"
            Text(
                text = "Updated $label",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        message.info["error"]?.let { err ->
            Text(
                text = err.toString().take(300),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (texts.isEmpty() && message.parts.none {
                partType(it) in setOf("tool", "file", "patch", "reasoning")
            } && message.info["error"] == null
        ) {
            Text(
                text = "Working…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onRevert != null || onFork != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                onRevert?.let {
                    TextButton(onClick = it) {
                        Text("Undo", style = MaterialTheme.typography.labelSmall)
                    }
                }
                onFork?.let {
                    TextButton(onClick = it) {
                        Text("Branch off here", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
