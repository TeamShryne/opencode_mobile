package com.opencode.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

// Subscribes to GET /event (and /global/event). First event is
// `server.connected`, then bus events: message.updated,
// message.part.updated (with `delta` for streaming), session.status/idle,
// permission.updated, todo.updated, file.edited, session.created/updated/
// deleted/diff/error, tui.*, pty.*, etc.
class SseManager {
    private var eventSource: EventSource? = null
    private var job: Job? = null

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<ServerEvent> = _events

    data class ServerEvent(val type: String, val raw: String)

    fun connect(scope: CoroutineScope, baseUrl: String, client: OkHttpClient, path: String = "event") {
        disconnect()
        job = scope.launch(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
            val request = Request.Builder().url(url).build()
            val factory = EventSources.createFactory(client)
            factory.newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    scope.launch { _events.emit(ServerEvent("sse.open", "")) }
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    scope.launch {
                        val t = type?.ifBlank { "message" } ?: "message"
                        _events.emit(ServerEvent(t, data))
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    scope.launch {
                        _events.emit(ServerEvent("sse.error", t?.message ?: "connection failed"))
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    scope.launch { _events.emit(ServerEvent("sse.closed", "")) }
                }
            }).also { eventSource = it }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        try {
            eventSource?.cancel()
        } catch (_: Exception) {
        }
        eventSource = null
    }
}
