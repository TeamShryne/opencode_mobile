package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.ModelRef
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.SessionMessageDto
import com.opencode.mobile.data.Todo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

data class ChatUiState(
    val loading: Boolean = false,
    val sending: Boolean = false,
    val messages: List<SessionMessageDto> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val model: ModelRef? = null,
    val agent: String = "build",
    val error: String? = null,
    val shareUrl: String? = null
)

class ChatViewModel(
    private val repo: OpencodeRepository,
    private val sessionId: String
) : ViewModel() {
    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val msgs = repo.messages(sessionId)
                val todos = repo.todos(sessionId)
                _ui.value = _ui.value.copy(loading = false, messages = msgs, todos = todos)
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

    fun send(text: String) {
        if (text.isBlank() || _ui.value.sending) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(sending = true, error = null)
            try {
                repo.send(sessionId, text, _ui.value.model, _ui.value.agent.ifBlank { null })
                refresh()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            } finally {
                _ui.value = _ui.value.copy(sending = false)
            }
        }
    }

    fun abort() {
        viewModelScope.launch { repo.abort(sessionId); refresh() }
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
}

fun SessionMessageDto.role(): String =
    info["role"]?.jsonPrimitive?.content ?: "unknown"

fun SessionMessageDto.textPreview(): String {
    val first = parts.firstOrNull { (it["type"]?.jsonPrimitive?.content) == "text" }
    return first?.get("text")?.jsonPrimitive?.content?.take(500) ?: "(no text)"
}
