package com.opencode.mobile.ui.navigation

object Routes {
    const val CONNECTION = "connection"
    const val HOME = "home"
    const val DASHBOARD = "dashboard"
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val FILES = "files"
    const val PROVIDERS = "providers"
    const val CONFIG = "config"
    const val INFRA = "infra"
    const val TUI = "tui"

    fun chat(sessionId: String) = "chat/$sessionId"
}
