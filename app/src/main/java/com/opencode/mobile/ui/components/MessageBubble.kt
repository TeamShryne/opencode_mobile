package com.opencode.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            runCatching { v.jsonPrimitive.content }.getOrNull()?.take(140) ?: ""
        } ?: ""
    val detail = (output.ifBlank { inputHint }).take(600)
    return ToolView(
        name = name,
        status = status,
        title = title.ifBlank { name },
        detail = detail,
        isError = status == "error"
    )
}

/**
 * Web-style thread rows: user messages are full-width plain text (no
 * bubble), assistant text renders as markdown, tool calls are expandable
 * cards with live status. Internal part kinds stay hidden.
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
        val text = message.parts.filter { partType(it) == "text" }
            .joinToString("\n\n") { partText(it) }.ifBlank { "(empty)" }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            MarkdownText(text)
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        val texts = message.parts.filter { partType(it) == "text" }.map { partText(it) }
            .filter { it.isNotBlank() }
        if (texts.isNotEmpty()) {
            MarkdownText(texts.joinToString("\n\n") + if (isLive) " ▍" else "")
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
            ToolCard(toolViewOf(part))
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
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Text(
                    text = err.toString().take(400),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (isLive && texts.isEmpty() && message.parts.none {
                partType(it) in setOf("tool", "file", "patch", "reasoning")
            } && message.info["error"] == null
        ) {
            ThinkingRow()
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

@Composable
private fun ToolCard(tool: ToolView) {
    // Running/failing tools start open so activity is visible; done ones collapse.
    var open by remember(tool.name, tool.status) {
        mutableStateOf(tool.status == "running" || tool.status == "pending" || tool.isError)
    }
    val headline = when (tool.status) {
        "pending" -> "Queued ${tool.title}"
        "running" -> "${tool.title} — running"
        "error" -> "${tool.title} — failed"
        else -> tool.title
    }
    Surface(
        color = if (tool.isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable { open = !open }
            ) {
                if (tool.status == "running" || tool.status == "pending") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tool.isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (open) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = open && tool.detail.isNotBlank()) {
                Text(
                    text = tool.detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (tool.isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (tool.isError) 12 else 20,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            text = "Thinking…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
