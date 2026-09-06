package com.opencode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.FileNode
import com.opencode.mobile.data.FileStatus
import com.opencode.mobile.data.OpencodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class FilesUiState(
    val loading: Boolean = false,
    val nodes: List<FileNode> = emptyList(),
    val status: List<FileStatus> = emptyList(),
    val preview: JsonObject? = null,
    val previewPath: String = "",
    val query: String = "",
    val searchHits: List<String> = emptyList(),
    val error: String? = null
)

class FilesViewModel(private val repo: OpencodeRepository) : ViewModel() {
    private val _ui = MutableStateFlow(FilesUiState())
    val ui: StateFlow<FilesUiState> = _ui

    fun browse(path: String? = null) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val nodes = repo.listFiles(path)
                val status = repo.fileStatus()
                _ui.value = _ui.value.copy(loading = false, nodes = nodes, status = status)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun preview(path: String) {
        viewModelScope.launch {
            try {
                val content = repo.fileContent(path)
                _ui.value = _ui.value.copy(preview = content, previewPath = path)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }

    fun search(query: String) {
        _ui.value = _ui.value.copy(query = query)
        if (query.isBlank()) {
            _ui.value = _ui.value.copy(searchHits = emptyList())
            return
        }
        viewModelScope.launch {
            try {
                _ui.value = _ui.value.copy(searchHits = repo.findFiles(query))
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
        }
    }
}
