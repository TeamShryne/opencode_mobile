package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.Session
import com.opencode.mobile.data.SessionStatusResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SessionsUiState(
    val loading: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val statuses: Map<String, SessionStatusResponse> = emptyMap(),
    val error: String? = null
)

class SessionsViewModel(private val repo: OpencodeRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SessionsUiState())
    val ui: StateFlow<SessionsUiState> = _ui

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
}
