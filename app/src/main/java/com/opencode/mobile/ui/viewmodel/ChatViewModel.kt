package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.ModelRef
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.PendingPermission
import com.opencode.mobile.data.Realtime
import com.opencode.mobile.data.SessionMessageDto
import com.opencode.mobile.data.SseManager
import com.opencode.mobile.data.Todo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ChatUiState(
    val loading: Boolean = false,
    val sending: Boolean = false,
    val liveBusy: Boolean = false,
    val messages: List<SessionMessageDto> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val pendingPermissions: List<PendingPermission> = emptyList(),
    val model: ModelRef? = null,
    val agent: String = "build",
    val error: String? = null,
    val shareUrl: String? = null
)

class ChatViewModel(
    private val repo: OpencodeRepository,
    private val sessionId: String,
    events: SharedFlow<SseManager.ServerEvent>? = null
) : ViewModel() {
    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui

    init {
        if (events != null) {
            viewModelScope.launch {
                events.collect { ev -> onEvent(ev.type, ev.raw) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val msgs = repo.messages(sessionId)
                val todos = repo.todos(sessionId)
                _ui.value = _ui.value.copy(
                    loading = false, sending = false, messages = msgs, todos = todos
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun pickModel(providerID: String, modelID: String) {
        _ui.value = _ui.value.copy(model = ModelRef(providerID, modelID))
    }

    fun pickAgent(agent: String) {
        _ui.value = _ui.value.copy(agent = agent)
    }

    fun clearModel() {
        _ui.value = _ui.value.copy(model = null)
    }

    /** Single entry point for the composer: slash commands go to /command, else chat. */
    fun sendSmart(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        if (t.startsWith("/")) {
            val parts = t.removePrefix("/").split(" ", limit = 2)
            runSlashCommand(parts[0], parts.getOrElse(1) { "" })
        } else {
            send(t)
        }
    }

    /**
     * True streaming send: the user message appears instantly, the prompt is
     * fired with `prompt_async` (returns immediately), and the reply streams
     * in through the live event feed. Falls back to a blocking call when the
     * async path is unavailable.
     */
    fun send(text: String) {
        if (text.isBlank() || _ui.value.sending) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(sending = true, error = null)
            insertLocalUser(text)
            val live = repo.sendAsync(sessionId, text, _ui.value.model, _ui.value.agent.ifBlank { null })
            if (!live) {
                try {
                    repo.send(sessionId, text, _ui.value.model, _ui.value.agent.ifBlank { null })
                    refresh()
                } catch (e: Exception) {
                    removeLocal()
                    _ui.value = _ui.value.copy(error = e.message)
                } finally {
                    _ui.value = _ui.value.copy(sending = false)
                }
            }
            // Otherwise the stream drives the UI; sending clears on session.idle.
        }
    }

    fun abort() {
        viewModelScope.launch {
            repo.abort(sessionId)
            _ui.value = _ui.value.copy(sending = false, liveBusy = false)
            refresh()
        }
    }

    fun respondPermission(permissionId: String, response: String) {
        viewModelScope.launch {
            try {
                repo.respondPermission(sessionId, permissionId, response, null)
                _ui.value = _ui.value.copy(
                    pendingPermissions = _ui.value.pendingPermissions.filterNot { it.id == permissionId }
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    fun share() {
        viewModelScope.launch {
            try {
                val s = repo.share(sessionId)
                _ui.value = _ui.value.copy(shareUrl = s.share?.url)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    fun fork(messageID: String?, onForked: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val s = repo.fork(sessionId, messageID)
                onForked(s.id)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    fun revert(messageID: String) {
        viewModelScope.launch {
            try {
                repo.revert(sessionId, messageID); refresh()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    fun runSlashCommand(command: String, args: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(sending = true)
            try {
                repo.runCommand(sessionId, command.trimStart('/'), args, _ui.value.agent, _ui.value.model)
                refresh()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            } finally {
                _ui.value = _ui.value.copy(sending = false)
            }
        }
    }

    // -- live event feed -----------------------------------------------------

    private fun onEvent(type: String, raw: String) {
        when (type) {
            "message.part.updated" -> onPartUpdated(raw)
            "message.updated" -> onMessageUpdated(raw)
            "message.removed" -> onMessageRemoved(raw)
            "message.part.removed" -> onPartRemoved(raw)
            "session.status", "session.idle" -> onStatus(raw, type)
            "permission.updated" -> onPermission(raw)
            "permission.replied" -> onPermissionReplied(raw)
            "todo.updated" -> onTodos(raw)
            "session.error" -> onSessionError(raw)
        }
    }

    private fun forSession(raw: String): JsonObject? {
        val p = Realtime.props(raw) ?: return null
        val sid = Realtime.sessionIdOf(p) ?: return null
        return if (sid == sessionId) p else null
    }

    private fun onPartUpdated(raw: String) {
        val p = forSession(raw) ?: return
        val part = Realtime.part(p) ?: return
        val mid = Realtime.str(part, "messageID", "messageId") ?: return
        val pid = Realtime.str(part, "id") ?: return
        val delta = Realtime.delta(p)
        _ui.value = _ui.value.copy(messages = upsertPart(_ui.value.messages, mid, pid, part, delta))
    }

    private fun onMessageUpdated(raw: String) {
        val p = forSession(raw) ?: return
        val info = Realtime.messageInfo(p) ?: return
        val mid = Realtime.str(info, "id") ?: return
        val cur = _ui.value.messages
        val idx = cur.indexOfFirst { Realtime.str(it.info, "id") == mid }
        _ui.value = _ui.value.copy(
            messages = if (idx < 0) cur + SessionMessageDto(info, emptyList())
            else cur.toMutableList().also { it[idx] = cur[idx].copy(info = info) }
        )
    }

    private fun onMessageRemoved(raw: String) {
        val p = Realtime.props(raw) ?: return
        val (sid, mid, _) = Realtime.removedMessage(p)
        if (sid != sessionId || mid.isBlank()) return
        _ui.value = _ui.value.copy(
            messages = _ui.value.messages.filterNot { Realtime.str(it.info, "id") == mid }
        )
    }

    private fun onPartRemoved(raw: String) {
        val p = Realtime.props(raw) ?: return
        val (sid, mid, pid) = Realtime.removedMessage(p)
        if (sid != sessionId || mid.isBlank() || pid.isNullOrBlank()) return
        _ui.value = _ui.value.copy(
            messages = _ui.value.messages.map { m ->
                if (Realtime.str(m.info, "id") != mid) m
                else m.copy(parts = m.parts.filterNot { Realtime.str(it, "id") == pid })
            }
        )
    }

    private fun onStatus(raw: String, type: String) {
        val p = Realtime.props(raw) ?: return
        val (sid, t) = Realtime.status(p, type) ?: return
        if (sid != sessionId) return
        if (t == "idle") {
            val wasSending = _ui.value.sending
            _ui.value = _ui.value.copy(liveBusy = false)
            if (wasSending) refresh() // reconcile streamed parts with server state
        } else {
            _ui.value = _ui.value.copy(liveBusy = true)
        }
    }

    private fun onPermission(raw: String) {
        val p = Realtime.props(raw) ?: return
        val perm = Realtime.permission(p) ?: return
        if (perm.sessionId != sessionId) return
        val cur = _ui.value.pendingPermissions.filterNot { it.id == perm.id }
        _ui.value = _ui.value.copy(pendingPermissions = cur + perm)
    }

    private fun onPermissionReplied(raw: String) {
        val p = Realtime.props(raw) ?: return
        val sid = Realtime.sessionIdOf(p)
        if (sid != null && sid != sessionId) return
        val pid = Realtime.str(p, "permissionID", "permissionId", "id") ?: return
        _ui.value = _ui.value.copy(
            pendingPermissions = _ui.value.pendingPermissions.filterNot { it.id == pid }
        )
    }

    private fun onTodos(raw: String) {
        val p = forSession(raw) ?: return
        Realtime.todos(p)?.let { todos ->
            _ui.value = _ui.value.copy(todos = todos)
        }
    }

    private fun onSessionError(raw: String) {
        val p = forSession(raw) ?: return
        val msg = Realtime.str(p, "message", "error") ?: raw.take(200)
        _ui.value = _ui.value.copy(error = msg, sending = false, liveBusy = false)
    }

    private fun upsertPart(
        messages: List<SessionMessageDto>,
        messageId: String,
        partId: String,
        incoming: JsonObject,
        delta: String?
    ): List<SessionMessageDto> {
        val idx = messages.indexOfFirst { Realtime.str(it.info, "id") == messageId }
        if (idx < 0) {
            val shell = SessionMessageDto(
                info = buildJsonObject {
                    put("id", messageId)
                    put("role", "assistant")
                    put("sessionID", sessionId)
                },
                parts = listOf(mergePart(null, incoming, delta))
            )
            return messages + shell
        }
        val msg = messages[idx]
        val pIdx = msg.parts.indexOfFirst { Realtime.str(it, "id") == partId }
        val merged = mergePart(msg.parts.getOrNull(pIdx), incoming, delta)
        val parts = if (pIdx < 0) msg.parts + merged
        else msg.parts.toMutableList().also { it[pIdx] = merged }
        return messages.toMutableList().also { it[idx] = msg.copy(parts = parts) }
    }

    private fun mergePart(old: JsonObject?, incoming: JsonObject, delta: String?): JsonObject {
        if (delta == null) return incoming
        val base = old ?: incoming
        val oldText = base["text"]?.jsonPrimitive?.content ?: ""
        return JsonObject(base + ("text" to JsonPrimitive(oldText + delta)))
    }

    private fun insertLocalUser(text: String) {
        val stamp = System.currentTimeMillis().toString()
        val msg = SessionMessageDto(
            info = buildJsonObject {
                put("id", "local-$stamp")
                put("role", "user")
                put("sessionID", sessionId)
            },
            parts = listOf(
                buildJsonObject {
                    put("id", "local-$stamp-p0")
                    put("type", "text")
                    put("text", text)
                }
            )
        )
        _ui.value = _ui.value.copy(messages = _ui.value.messages + msg)
    }

    private fun removeLocal() {
        _ui.value = _ui.value.copy(
            messages = _ui.value.messages.filterNot {
                (Realtime.str(it.info, "id") ?: "").startsWith("local-")
            }
        )
    }
}

fun SessionMessageDto.role(): String =
    info["role"]?.jsonPrimitive?.content ?: "unknown"

fun SessionMessageDto.textPreview(): String {
    val first = parts.firstOrNull { (it["type"]?.jsonPrimitive?.content) == "text" }
    return first?.get("text")?.jsonPrimitive?.content?.take(500) ?: "(no text)"
}
