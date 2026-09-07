package com.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private const val UrlTag = "url"

/** A paragraph that can contain clickable links. */
@Composable
private fun LinkedText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    // Explicit theme color: ClickableText must not fall back to black in dark mode.
    val onSurface = MaterialTheme.colorScheme.onSurface
    val merged = if (style.color == Color.Unspecified) style.copy(color = onSurface) else style
    ClickableText(
        text = text,
        style = merged,
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations(UrlTag, offset, offset).firstOrNull()?.let {
                runCatching { uriHandler.openUri(it.item) }
            }
        }
    )
}

/**
 * Markdown-lite rendering like the web client: fenced code blocks become
 * dark cards with a language label + copy button; inline code, bold,
 * headers, lists, quotes and links render inline. Tables stay plain text.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { splitBlocks(text) }
    Column(modifier = modifier, ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeCard(block)
                is MdBlock.Text -> MdParagraph(block.text)
            }
        }
    }
}

private sealed interface MdBlock {
    data class Code(val lang: String, val code: String) : MdBlock
    data class Text(val text: String) : MdBlock
}

private fun splitBlocks(text: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    var rest = text
    while (true) {
        val start = rest.indexOf("```")
        if (start < 0) {
            if (rest.isNotBlank()) out += MdBlock.Text(rest.trim('\n'))
            break
        }
        val head = rest.substring(0, start)
        if (head.isNotBlank()) out += MdBlock.Text(head.trim('\n'))
        val afterFence = rest.substring(start + 3)
        val nl = afterFence.indexOf('\n')
        val lang: String
        val body: String
        if (nl < 0) {
            lang = afterFence.trim()
            body = ""
            rest = ""
        } else {
            lang = afterFence.substring(0, nl).trim()
            val end = afterFence.indexOf("\n```", nl)
            if (end < 0) {
                body = afterFence.substring(nl + 1)
                rest = ""
            } else {
                body = afterFence.substring(nl + 1, end)
                rest = afterFence.substring(end + 4)
            }
        }
        out += MdBlock.Code(lang, body.trimEnd('\n'))
        if (rest.isEmpty()) break
    }
    return out
}

@Composable
private fun CodeCard(block: MdBlock.Code) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = block.lang.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(block.code))
                }) {
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MdParagraph(text: String) {
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                val content = trimmed.drop(level).trim().trimStart('#').trim()
                val style = when (level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                }
                Text(
                    inlineSpans(content),
                    style = style.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                i++
            }
            trimmed.startsWith(">") -> {
                val quote = buildString {
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        if (isNotEmpty()) append('\n')
                        append(lines[i].trimStart().removePrefix(">").trimStart())
                        i++
                    }
                }
                LinkedText(
                    inlineSpans(quote),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || listNumber(trimmed) != null -> {
                Column(Modifier.padding(vertical = 2.dp)) {
                    while (i < lines.size) {
                        val t = lines[i].trimStart()
                        val marker = when {
                            t.startsWith("- ") || t.startsWith("* ") -> "•"
                            else -> listNumber(t)?.let { "$it." } ?: break
                        }
                        val content = t.substringAfter(' ').let {
                            if (marker == "•") t.drop(2) else t.substringAfter(' ')
                        }
                        Row(Modifier.padding(vertical = 1.dp)) {
                            Text(marker, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            LinkedText(
                                inlineSpans(content),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        i++
                    }
                }
            }
            trimmed.isBlank() -> {
                Spacer(Modifier.padding(3.dp))
                i++
            }
            else -> {
                val para = buildString {
                    while (i < lines.size) {
                        val t = lines[i]
                        val ts = t.trimStart()
                        if (ts.isBlank() || ts.startsWith("#") || ts.startsWith(">")
                            || ts.startsWith("- ") || ts.startsWith("* ") || listNumber(ts) != null
                        ) break
                        if (isNotEmpty()) append('\n')
                        append(t)
                        i++
                    }
                }
                LinkedText(
                    inlineSpans(para),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

private fun listNumber(s: String): Int? {
    val dot = s.indexOf(". ")
    if (dot in 1..3) return s.substring(0, dot).toIntOrNull()
    return null
}

/** Inline **bold**, `code` and [links](url). */
private fun inlineSpans(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end < 0) {
                        append(text.substring(i)); i = text.length
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendInline(text.substring(i + 2, end))
                        }
                        i = end + 2
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) {
                        append(text.substring(i)); i = text.length
                    } else {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                text[i] == '[' -> {
                    val mid = text.indexOf("](", i)
                    if (mid < 0) {
                        append(text[i]); i++
                    } else {
                        val end = text.indexOf(')', mid + 2)
                        if (end < 0) {
                            append(text[i]); i++
                        } else {
                            val label = text.substring(i + 1, mid)
                            val url = text.substring(mid + 2, end)
                            pushStringAnnotation(UrlTag, url)
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF4C8DFF),
                                    textDecoration = TextDecoration.Underline
                                )
                            ) { appendInline(label) }
                            pop()
                            i = end + 1
                        }
                    }
                }
                else -> {
                    append(text[i]); i++
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    // Nested inline code inside bold: keep it simple, render literally.
    var i = 0
    while (i < text.length) {
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end < 0) {
                append(text.substring(i)); return
            }
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(text.substring(i + 1, end))
            }
            i = end + 1
        } else {
            append(text[i]); i++
        }
    }
}
