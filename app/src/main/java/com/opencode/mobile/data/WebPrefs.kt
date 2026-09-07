package com.opencode.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.webDataStore by preferencesDataStore(name = "webview_prefs")

data class WebSettings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val username: String = "opencode",
    val password: String = "",
    val textZoom: Int = 100,
    val desktopMode: Boolean = false
) {
    companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.1.10:4096"
    }
}

class WebPrefs(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val userKey = stringPreferencesKey("username")
    private val passKey = stringPreferencesKey("password")
    private val zoomKey = intPreferencesKey("text_zoom")
    private val desktopKey = booleanPreferencesKey("desktop_mode")

    val settings: Flow<WebSettings> = context.webDataStore.data.map { p ->
        WebSettings(
            serverUrl = p[urlKey] ?: WebSettings.DEFAULT_SERVER_URL,
            username = p[userKey] ?: "opencode",
            password = p[passKey] ?: "",
            textZoom = (p[zoomKey] ?: 100).coerceIn(50, 200),
            desktopMode = p[desktopKey] ?: false
        )
    }

    suspend fun save(s: WebSettings) {
        context.webDataStore.edit { p ->
            p[urlKey] = s.serverUrl.trim().trimEnd('/')
            p[userKey] = s.username
            p[passKey] = s.password
            p[zoomKey] = s.textZoom.coerceIn(50, 200)
            p[desktopKey] = s.desktopMode
        }
    }
}
