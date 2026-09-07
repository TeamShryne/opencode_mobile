package com.opencode.mobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.opencode.mobile.data.WebPrefs
import com.opencode.mobile.data.WebSettings as AppWebSettings
import com.opencode.mobile.util.ShakeDetector
import kotlinx.coroutines.launch

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

// Forces the loaded page to be non-zoomable so the app feels native.
private const val NO_ZOOM_JS =
    "(function(){var m=document.querySelector('meta[name=viewport]');" +
        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
        "m.content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';})();"

@SuppressLint("SetJavaScriptEnabled")
private fun buildWebView(
    context: Context,
    onHistoryChange: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    authProvider: () -> Pair<String, String>
): WebView {
    return WebView(context).apply {
        // Full-screen, no website chrome feeling.
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isHapticFeedbackEnabled = false
        setBackgroundColor(android.graphics.Color.BLACK)

        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            // Disable pinch zoom entirely.
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        webViewClient = object : WebViewClient() {
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // Stay inside the app.
                return false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): Boolean {
                // Stay inside the app.
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Enforce non-zoomable viewport even if the page allows scaling.
                view.evaluateJavascript(NO_ZOOM_JS, null)
                onHistoryChange(view.canGoBack(), view.canGoForward())
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                onHistoryChange(view.canGoBack(), view.canGoForward())
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String
            ) {
                val (user, pass) = authProvider()
                if (pass.isNotBlank()) {
                    handler.proceed(user.ifBlank { "opencode" }, pass)
                } else {
                    super.onReceivedHttpAuthRequest(view, handler, host, realm)
                }
            }
        }
        webChromeClient = WebChromeClient()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(prefs: WebPrefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val saved by prefs.settings.collectAsState(initial = AppWebSettings())

    var showSheet by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var authHolder by remember { mutableStateOf(Pair("opencode", "")) }
    var initialLoadDone by remember { mutableStateOf(false) }

    // Draft values edited inside the sheet.
    var draftUrl by remember { mutableStateOf<String?>(null) }
    var draftUser by remember { mutableStateOf<String?>(null) }
    var draftPass by remember { mutableStateOf<String?>(null) }
    var draftZoom by remember { mutableStateOf<Int?>(null) }
    var draftDesktop by remember { mutableStateOf<Boolean?>(null) }

    val webView = remember {
        buildWebView(
            context = context,
            onHistoryChange = { back, forward ->
                canGoBack = back
                canGoForward = forward
            },
            authProvider = { authHolder }
        )
    }
    val defaultUa = remember(webView) { webView.settings.userAgentString }

    // Keep auth + zoom + UA in sync with saved prefs without reloading.
    LaunchedEffect(saved.username, saved.password) {
        authHolder = Pair(saved.username, saved.password)
    }
    LaunchedEffect(saved.textZoom) {
        if (webView.settings.textZoom != saved.textZoom) {
            webView.settings.textZoom = saved.textZoom
        }
    }
    LaunchedEffect(saved.desktopMode) {
        val want = if (saved.desktopMode) DESKTOP_UA else defaultUa
        if (webView.settings.userAgentString != want) {
            webView.settings.userAgentString = want
            if (initialLoadDone) webView.reload()
        }
    }
    // Initial load (and reload when the saved server URL changes from the sheet).
    LaunchedEffect(saved.serverUrl) {
        val target = saved.serverUrl.trim().trimEnd('/')
        if (target.isNotBlank() && !initialLoadDone) {
            webView.loadUrl(target)
            initialLoadDone = true
        }
    }

    // Shake -> open options. Vibrate lightly for feedback.
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val detector = ShakeDetector(onShake = {
            buzz(context)
            showSheet = true
        })
        detector.register(manager)
        onDispose { detector.unregister(manager) }
    }

    // Pause / resume WebView with the composition.
    DisposableEffect(webView) {
        webView.onResume()
        onDispose {
            webView.stopLoading()
            webView.onPause()
        }
    }

    BackHandler(enabled = canGoBack && !showSheet) {
        webView.goBack()
    }

    // Nothing but the WebView on screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Sync drafts when the sheet opens.
        LaunchedEffect(Unit) {
            if (draftUrl == null) draftUrl = saved.serverUrl
            if (draftUser == null) draftUser = saved.username
            if (draftPass == null) draftPass = saved.password
            if (draftZoom == null) draftZoom = saved.textZoom
            if (draftDesktop == null) draftDesktop = saved.desktopMode
        }
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                draftUrl = null
                draftUser = null
                draftPass = null
                draftZoom = null
                draftDesktop = null
            },
            sheetState = sheetState
        ) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Options", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Shake your phone anytime to open this panel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = draftUrl ?: saved.serverUrl,
                    onValueChange = { draftUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.10:4096") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = draftUser ?: saved.username,
                        onValueChange = { draftUser = it },
                        label = { Text("User") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draftPass ?: saved.password,
                        onValueChange = { draftPass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f)
                    )
                }

                val zoom = draftZoom ?: saved.textZoom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Text size", style = MaterialTheme.typography.titleSmall)
                    Text("$zoom%", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = zoom.toFloat(),
                    onValueChange = { draftZoom = it.toInt().coerceIn(50, 200) },
                    valueRange = 50f..200f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth()
                )

                val desktop = draftDesktop ?: saved.desktopMode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Desktop site", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = desktop,
                        onCheckedChange = { draftDesktop = it }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        val cleaned = (draftUrl ?: saved.serverUrl).trim().trimEnd('/')
                        val next = saved.copy(
                            serverUrl = cleaned.ifBlank { saved.serverUrl },
                            username = (draftUser ?: saved.username),
                            password = (draftPass ?: saved.password),
                            textZoom = (draftZoom ?: saved.textZoom).coerceIn(50, 200),
                            desktopMode = (draftDesktop ?: saved.desktopMode)
                        )
                        val urlChanged =
                            next.serverUrl.trim().trimEnd('/') != saved.serverUrl.trim().trimEnd('/')
                        val authChanged =
                            next.username != saved.username || next.password != saved.password
                        val desktopChanged = next.desktopMode != saved.desktopMode
                        scope.launch {
                            prefs.save(next)
                            val target = next.serverUrl.trim().trimEnd('/')
                            when {
                                urlChanged && target.isNotBlank() -> webView.loadUrl(target)
                                authChanged || desktopChanged -> webView.reload()
                                // Text zoom applies via LaunchedEffect without a reload.
                            }
                            initialLoadDone = true
                            showSheet = false
                            draftUrl = null
                            draftUser = null
                            draftPass = null
                            draftZoom = null
                            draftDesktop = null
                        }
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply & load")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { webView.reload() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reload") }
                    OutlinedButton(
                        onClick = {
                            webView.clearCache(true)
                            webView.clearFormData()
                            webView.clearHistory()
                            webView.reload()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear & reload") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = { if (webView.canGoBack()) webView.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                    TextButton(
                        onClick = { if (webView.canGoForward()) webView.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.weight(1f)
                    ) { Text("Forward") }
                }
            }
        }
    }
}

private fun buzz(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            v.vibrate(40)
        }
    } catch (_: Exception) {
        // Haptics are best-effort.
    }
}
