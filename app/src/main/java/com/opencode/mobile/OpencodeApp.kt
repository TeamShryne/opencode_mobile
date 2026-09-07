package com.opencode.mobile

import android.app.Application
import com.opencode.mobile.data.WebPrefs

class OpencodeApp : Application() {
    lateinit var webPrefs: WebPrefs

    override fun onCreate() {
        super.onCreate()
        webPrefs = WebPrefs(this)
    }
}
