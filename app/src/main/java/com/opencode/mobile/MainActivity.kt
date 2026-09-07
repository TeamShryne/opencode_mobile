package com.opencode.mobile

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.opencode.mobile.ui.WebViewScreen
import com.opencode.mobile.ui.theme.OpencodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Allows inspecting the WebView via desktop Chrome (chrome://inspect)
        // in debug builds to diagnose rendering issues.
        try {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        } catch (_: Exception) {
        }
        enableEdgeToEdge()
        // Let the WebView draw edge-to-edge behind transparent system bars.
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = false
            it.isAppearanceLightNavigationBars = false
        }
        val app = application as OpencodeApp
        setContent {
            OpencodeTheme {
                WebViewScreen(app.webPrefs)
            }
        }
    }
}
