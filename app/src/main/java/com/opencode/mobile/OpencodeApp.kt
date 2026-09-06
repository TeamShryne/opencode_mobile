package com.opencode.mobile

import android.app.Application
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.PreferencesRepository
import com.opencode.mobile.data.SseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OpencodeApp : Application() {
    lateinit var prefs: PreferencesRepository
    lateinit var repo: OpencodeRepository
    val sse = SseManager()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(this)
        repo = OpencodeRepository(prefs)
    }

    /** (Re)connect the live event stream with the current server + credentials. */
    fun ensureSse() {
        val s = repo.settings
        if (s.baseUrl.isBlank()) return
        sse.connect(appScope, s.baseUrl, repo.sseClient())
    }
}
