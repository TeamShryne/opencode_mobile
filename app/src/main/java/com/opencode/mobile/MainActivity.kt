package com.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.opencode.mobile.ui.WebViewScreen
import com.opencode.mobile.ui.theme.OpencodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
