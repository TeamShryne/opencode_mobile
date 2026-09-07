package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.Realtime
import com.opencode.mobile.data.Session
import com.opencode.mobile.data.SessionStatusResponse
import com.opencode.mobile.data.SseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SessionsUiState(
    val loading: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val statuses: Map<String, SessionStatusResponse> = emptyMap(),
    val error: String? = null
)

class SessionsViewModel(
    private val repo: OpencodeRepository,
    events: SharedFlow<SseManager.ServerEvent>? = null
) : ViewModel() {
    private val _ui = MutableStateFlow(SessionsUiState())
    val ui: StateFlow<SessionsUiState> = _ui
    private var liveStarted = false
    private var lastAutoRefresh = 0L

    init {
        if (events != null) startLive(events)
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val list = repo.sessions()
                val statuses = repo.sessionStatuses()
                _ui.value = SessionsUiState(loading = false, sessions = list.sortedByDescending {
                    it.id
                }, statuses = statuses)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun create(title: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val s = repo.createSession(title)
                refresh()
                onCreated(s.id)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteSession(id)
                refresh()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch {
            try {
                repo.renameSession(id, title)
                refresh()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    /** Live session list: statuses flip instantly, creates/deletes refresh the list. */
    private fun startLive(events: SharedFlow<SseManager.ServerEvent>) {
        if (liveStarted) return
        liveStarted = true
        viewModelScope.launch {
            events.collect { ev ->
                when (ev.type) {
                    "session.status", "session.idle" -> {
                        val p = Realtime.props(ev.raw) ?: return@collect
                        val (sid, t) = Realtime.status(p, ev.type) ?: return@collect
                        _ui.value = _ui.value.copy(
                            statuses = _ui.value.statuses + (sid to SessionStatusResponse(type = t))
                        )
                    }
                    "session.created", "session.deleted", "session.updated",
                    "session.renamed", "server.connected" -> autoRefresh()
                }
            }
        }
    }

    private fun autoRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefresh < 2000L) return
        lastAutoRefresh = now
        viewModelScope.launch {
            try {
                val list = repo.sessions()
                _ui.value = _ui.value.copy(sessions = list.sortedByDescending { it.id })
            } catch (_: Exception) {
            }
        }
    }
}
