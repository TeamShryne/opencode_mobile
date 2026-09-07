package com.opencode.mobile.util

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory ring buffer of WebView console/network diagnostics,
 * surfaced in the Options sheet so failures can be diagnosed on-device.
 */
object WebDiagnostics {
    private const val MAX_LINES = 50

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _probe = MutableStateFlow<String?>(null)
    val probe: StateFlow<String?> = _probe.asStateFlow()

    fun setProbe(result: String?) {
        _probe.value = result?.take(500)
    }

    fun log(msg: String) {
        val stamped = msg.take(300)
        _lines.value = (_lines.value + stamped).takeLast(MAX_LINES)
    }

    fun clear() {
        _lines.value = emptyList()
        _probe.value = null
    }

    fun webViewVersion(context: Context): String {
        return try {
            val pkg = WebView.getCurrentWebViewPackage() ?: return "unknown"
            "${pkg.versionName ?: "?"} (${pkg.packageName})"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
