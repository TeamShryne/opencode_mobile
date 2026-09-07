package com.opencode.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.SessionMessageDto
import com.opencode.mobile.ui.components.MessageBubble
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

/**
 * Web-style thread: centered max-width column, sticky session title
 * (tap to rename), smart stick-to-bottom that never yanks the user while
 * reading, plus a jump-to-latest button when scrolled far up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatThread(
    messages: List<SessionMessageDto>,
    streaming: Boolean = false,
    title: String,
    onRename: (String) -> Unit,
    onRevert: (String) -> Unit,
    onFork: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var stuck by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    fun jumpToLatest() {
        scope.launch {
            runCatching { listState.scrollToItem(maxOf(0, messages.size - 1)) }
        }
    }

    // Track viewport position: stuck only when the last item is fully visible.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) {
                true to false
            } else {
                val last = info.visibleItemsInfo.lastOrNull()
                val atBottom = last != null && last.index >= total - 1 &&
                    (last.offset + last.size) <= info.viewportEndOffset + 8
                val itemsAbove = total - 1 - (last?.index ?: 0)
                atBottom to (itemsAbove > 4 && !atBottom)
            }
        }.distinctUntilChanged().collect { (bottom, jump) ->
            stuck = bottom
            showJump = jump
        }
    }

    // Follow the stream only while stuck; content growth (same item count,
    // longer text) also re-pins to the bottom.
    val contentStamp = messages.size * 1_000_000L +
        messages.sumOf { it.parts.size }.toLong()
    LaunchedEffect(contentStamp, stuck, streaming) {
        if (stuck && messages.isNotEmpty()) listState.scrollToItem(messages.size)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            stickyHeader {
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                        .clickable { renaming = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.fillMaxWidth().widthIn(max = 680.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title.ifBlank { "New chat" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            items(
                items = messages,
                key = { m -> m.info["id"]?.jsonPrimitive?.content ?: m.hashCode().toString() }
            ) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
                        val msgId = m.info["id"]?.jsonPrimitive?.content ?: ""
                        val isLastAssistant = streaming &&
                            m == messages.lastOrNull() &&
                            (m.info["role"]?.jsonPrimitive?.content != "user")
                        MessageBubble(
                            message = m,
                            onRevert = { onRevert(msgId) },
                            onFork = { onFork(msgId) },
                            isLive = isLastAssistant
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showJump,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            FloatingActionButton(
                onClick = { jumpToLatest() },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest")
            }
        }
    }

    if (renaming) {
        var draft by remember(title) { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    if (draft.isNotBlank()) onRename(draft.trim())
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel") }
            }
        )
    }
}
