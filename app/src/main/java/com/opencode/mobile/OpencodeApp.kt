package com.opencode.mobile

import android.app.Application
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.data.PreferencesRepository
import com.opencode.mobile.data.SseManager

class OpencodeApp : Application() {
    lateinit var prefs: PreferencesRepository
    lateinit var repo: OpencodeRepository
    val sse = SseManager()

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(this)
        repo = OpencodeRepository(prefs)
    }
}
