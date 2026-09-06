package com.opencode.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.SessionMessageDto
import com.opencode.mobile.ui.components.MessageBubble
import kotlinx.serialization.json.jsonPrimitive

/** Plain scrolling thread used by the home screen. Auto-sticks to the newest message. */
@Composable
fun ChatThread(
    messages: List<SessionMessageDto>,
    onRevert: (String) -> Unit,
    onFork: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = messages,
            key = { m -> m.info["id"]?.jsonPrimitive?.content ?: m.hashCode().toString() }
        ) { m ->
            val msgId = m.info["id"]?.jsonPrimitive?.content ?: ""
            MessageBubble(
                message = m,
                onRevert = { onRevert(msgId) },
                onFork = { onFork(msgId) }
            )
        }
    }
}
