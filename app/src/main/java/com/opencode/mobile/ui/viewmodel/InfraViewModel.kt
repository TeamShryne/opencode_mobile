package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.FormatterStatus
import com.opencode.mobile.data.LspStatus
import com.opencode.mobile.data.McpStatus
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.Project
import com.opencode.mobile.data.Pty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class InfraUiState(
    val loading: Boolean = false,
    val projects: List<Project> = emptyList(),
    val currentDir: String = "",
    val branch: String = "",
    val configText: String = "",
    val mcp: Map<String, McpStatus> = emptyMap(),
    val lsp: List<LspStatus> = emptyList(),
    val formatters: List<FormatterStatus> = emptyList(),
    val ptys: List<Pty> = emptyList(),
    val error: String? = null
)

class InfraViewModel(private val repo: OpencodeRepository) : ViewModel() {
    private val _ui = MutableStateFlow(InfraUiState())
    val ui: StateFlow<InfraUiState> = _ui

    private var rawConfig: JsonObject? = null

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val projects = repo.projects()
                val path = repo.path()
                val vcs = repo.vcs()
                val cfg = repo.config()
                rawConfig = cfg
                _ui.value = InfraUiState(
                    loading = false,
                    projects = projects,
                    currentDir = path?.directory ?: "",
                    branch = vcs?.branch ?: "",
                    configText = cfg?.toString() ?: "{}",
                    mcp = repo.mcp(),
                    lsp = repo.lsp(),
                    formatters = repo.formatters(),
                    ptys = repo.ptys()
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun prettyConfig(): String {
        val cfg = rawConfig ?: return _ui.value.configText
        // Light pretty-print without extra deps.
        return cfg.toString()
            .replace(",", ",\n")
            .replace("{", "{\n")
            .replace("}", "\n}")
    }
}

fun JsonObject.stringField(name: String): String =
    this[name]?.jsonPrimitive?.content ?: ""
