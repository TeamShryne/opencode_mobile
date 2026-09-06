package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.Agent
import com.opencode.mobile.data.OpencodeCommand
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val loading: Boolean = false,
    val providers: List<Provider> = emptyList(),
    val connected: List<String> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val commands: List<OpencodeCommand> = emptyList(),
    val tools: List<String> = emptyList(),
    val error: String? = null
)

class CatalogViewModel(private val repo: OpencodeRepository) : ViewModel() {
    private val _ui = MutableStateFlow(CatalogUiState())
    val ui: StateFlow<CatalogUiState> = _ui

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val p = repo.providers()
                val agents = repo.agents()
                val commands = repo.commands()
                val tools = repo.tools()
                _ui.value = CatalogUiState(
                    loading = false,
                    providers = p?.all ?: emptyList(),
                    connected = p?.connected ?: emptyList(),
                    agents = agents,
                    commands = commands,
                    tools = tools
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }
}
