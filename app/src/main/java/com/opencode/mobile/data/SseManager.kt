package com.opencode.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

enum class SseState { IDLE, CONNECTING, LIVE, RETRYING }

// App-wide subscription to GET /event (first event is `server.connected`,
// then bus events: message.updated, message.part.updated with `delta`,
// session.status/idle, permission.updated/replied, todo.updated, ...).
// Auto-reconnects with backoff so the thread stays live.
class SseManager {
    private var job: Job? = null

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<ServerEvent> = _events

    private val _state = MutableStateFlow(SseState.IDLE)
    val state: StateFlow<SseState> = _state

    data class ServerEvent(val type: String, val raw: String)

    fun connect(scope: CoroutineScope, baseUrl: String, client: OkHttpClient, path: String = "event") {
        disconnect()
        val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        job = scope.launch {
            var delayMs = 1000L
            while (isActive) {
                _state.value = if (delayMs > 1000L) SseState.RETRYING else SseState.CONNECTING
                val opened = listenOnce(url, client)
                if (!isActive) break
                delayMs = if (opened) 1000L else minOf(delayMs * 2, 15000L)
                if (!opened) _events.tryEmit(ServerEvent("sse.error", "reconnecting"))
                delay(delayMs)
            }
            _state.value = SseState.IDLE
        }
    }

    /** Suspends until the stream drops. Returns true if it ever opened. */
    private suspend fun listenOnce(url: String, client: OkHttpClient): Boolean =
        suspendCancellableCoroutine { cont ->
            val opened = AtomicBoolean(false)
            val request = Request.Builder().url(url).build()
            val factory = EventSources.createFactory(client)
            val source = factory.newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    opened.set(true)
                    _state.value = SseState.LIVE
                    _events.tryEmit(ServerEvent("sse.open", ""))
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    val t = type?.ifBlank { null } ?: Realtime.typeOf(data) ?: "message"
                    _events.tryEmit(ServerEvent(t, data))
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    _events.tryEmit(ServerEvent("sse.error", t?.message ?: "stream failed"))
                    if (cont.isActive) cont.resume(opened.get())
                }

                override fun onClosed(eventSource: EventSource) {
                    _events.tryEmit(ServerEvent("sse.closed", ""))
                    if (cont.isActive) cont.resume(opened.get())
                }
            })
            cont.invokeOnCancellation {
                try {
                    source.cancel()
                } catch (_: Exception) {
                }
            }
        }

    fun disconnect() {
        job?.cancel()
        job = null
        _state.value = SseState.IDLE
    }
}
