package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.ConnectionSettings
import com.opencode.mobile.data.HealthResponse
import com.opencode.mobile.data.OpencodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val baseUrl: String = "http://192.168.1.10:4096",
    val username: String = "opencode",
    val password: String = "",
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val version: String = "",
    val error: String? = null
)

class ConnectionViewModel(private val repo: OpencodeRepository) : ViewModel() {
    private val _ui = MutableStateFlow(ConnectionUiState())
    val ui: StateFlow<ConnectionUiState> = _ui

    init {
        viewModelScope.launch {
            val saved = repo.loadSaved()
            _ui.value = _ui.value.copy(
                baseUrl = saved.baseUrl,
                username = saved.username,
                password = saved.password
            )
        }
    }

    fun update(baseUrl: String, username: String, password: String) {
        _ui.value = _ui.value.copy(baseUrl = baseUrl, username = username, password = password, error = null)
    }

    fun connect(onOk: () -> Unit) {
        val s = _ui.value
        viewModelScope.launch {
            _ui.value = s.copy(connecting = true, error = null)
            val res: Result<HealthResponse> = repo.connect(
                ConnectionSettings(s.baseUrl, s.username, s.password)
            )
            res.onSuccess { h ->
                _ui.value = s.copy(connecting = false, connected = true, version = h.version, error = null)
                onOk()
            }.onFailure { e ->
                _ui.value = s.copy(connecting = false, connected = false, error = e.message ?: "Connection failed")
            }
        }
    }
}
