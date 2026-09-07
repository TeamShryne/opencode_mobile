package com.opencode.mobile.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.opencode.mobile.util.WebDiagnostics
import kotlinx.coroutines.launch

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

// Disables pinch zoom while preserving the site's own viewport params
// (width, theme, viewport-fit, ...) so the layout keeps rendering as designed.
private const val NO_ZOOM_JS =
    "(function(){var m=document.querySelector('meta[name=viewport]');" +
        "if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');document.head.appendChild(m);}" +
        "var raw=m.getAttribute('content')||'';var keep=[];" +
        "raw.split(',').forEach(function(p){var q=p.trim();var l=q.toLowerCase();" +
        "if(!q)return;" +
        "if(l.indexOf('maximum-scale')===0||l.indexOf('user-scalable')===0)return;" +
        "keep.push(q);});" +
        "function has(prefix){for(var i=0;i<keep.length;i++)" +
        "{if(keep[i].toLowerCase().indexOf(prefix)===0)return true;}return false;}" +
        "if(!has('width'))keep.push('width=device-width');" +
        "if(!has('initial-scale'))keep.push('initial-scale=1.0');" +
        "if(!has('viewport-fit'))keep.push('viewport-fit=cover');" +
        "keep.push('maximum-scale=1.0');keep.push('user-scalable=no');" +
        "m.setAttribute('content',keep.join(', '));})();"

@SuppressLint("SetJavaScriptEnabled")
private fun buildWebView(
    context: Context,
    onHistoryChange: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinishedOk: () -> Unit,
    onPageError: (String) -> Unit,
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
                request: WebResourceRequest
            ): Boolean {
                // Stay inside the app.
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                WebDiagnostics.log("nav: $url")
                onPageStarted()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Enforce non-zoomable viewport even if the page allows scaling.
                view.evaluateJavascript(NO_ZOOM_JS, null)
                onHistoryChange(view.canGoBack(), view.canGoForward())
                onPageFinishedOk()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                onHistoryChange(view.canGoBack(), view.canGoForward())
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String,
                failingUrl: String
            ) {
                // Deprecated callback = main frame only.
                WebDiagnostics.log("network: $description ($errorCode) $failingUrl")
                onPageError("$description ($errorCode)")
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    val desc = error.description?.toString() ?: "Page failed to load"
                    WebDiagnostics.log("network: $desc ${request.url}")
                    onPageError(desc)
                } else {
                    // Subresource/XHR/fetch failures (e.g. API calls the page makes).
                    WebDiagnostics.log(
                        "subresource: ${error.description} ${request.url}"
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    val msg = "HTTP ${errorResponse.statusCode}"
                    WebDiagnostics.log("network: $msg ${request.url}")
                    onPageError(msg)
                } else if (errorResponse.statusCode >= 400) {
                    WebDiagnostics.log(
                        "network: HTTP ${errorResponse.statusCode} ${request.url}"
                    )
                }
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

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // Log data-ish requests (API/XHR/navigation) so we can see whether
                // the page even attempts to fetch its content. Static assets skipped.
                try {
                    val url = request.url?.toString() ?: ""
                    val path = request.url?.path ?: ""
                    val staticExt = listOf(
                        ".js", ".css", ".map", ".png", ".jpg", ".jpeg", ".gif",
                        ".svg", ".ico", ".woff", ".woff2", ".ttf", ".webp"
                    )
                    val isStatic = staticExt.any { path.endsWith(it, ignoreCase = true) }
                    if (!isStatic && !url.startsWith("data:") && !url.startsWith("blob:")) {
                        WebDiagnostics.log("req: ${request.method} $url")
                    }
                } catch (_: Exception) {
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                WebDiagnostics.log(
                    "console:${msg.messageLevel().name.lowercase()} " +
                        "${msg.message()} @ ${msg.sourceId()}:${msg.lineNumber()}"
                )
                return super.onConsoleMessage(msg)
            }
        }
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
    var loadedUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var pageLoaded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Draft values edited inside the sheet.
    var draftUrl by remember { mutableStateOf<String?>(null) }
    var draftUser by remember { mutableStateOf<String?>(null) }
    var draftPass by remember { mutableStateOf<String?>(null) }
    var draftZoom by remember { mutableStateOf<Int?>(null) }
    var draftDesktop by remember { mutableStateOf<Boolean?>(null) }

    // First-run setup field.
    var setupUrl by remember { mutableStateOf<String?>(null) }

    val webView = remember {
        buildWebView(
            context = context,
            onHistoryChange = { back, forward ->
                canGoBack = back
                canGoForward = forward
            },
            onPageStarted = {
                isLoading = true
                errorMsg = null
            },
            onPageFinishedOk = {
                isLoading = false
                // onReceivedError runs before onPageFinished for the same
                // navigation, so only count clean finishes as loaded.
                if (errorMsg == null) pageLoaded = true
            },
            onPageError = { desc ->
                isLoading = false
                // Keep the first error; onPageFinished follows right after.
                if (errorMsg == null) errorMsg = desc
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
            if (loadedUrl != null) {
                errorMsg = null
                isLoading = true
                webView.reload()
            }
        }
    }
    // Load whenever the saved server URL changes (fixes stale-URL black screen:
    // previously the first default URL won and the real saved URL was ignored).
    LaunchedEffect(saved.serverUrl) {
        val target = saved.serverUrl.trim().trimEnd('/')
        if (target.isNotBlank() && target != loadedUrl) {
            loadedUrl = target
            errorMsg = null
            isLoading = true
            webView.loadUrl(target)
        } else if (target.isBlank()) {
            isLoading = false
        }
    }

    fun retry() {
        val target = saved.serverUrl.trim().trimEnd('/')
        errorMsg = null
        isLoading = true
        if (target.isBlank()) return
        loadedUrl = target
        try {
            if (webView.url == null) webView.loadUrl(target) else webView.reload()
        } catch (_: Exception) {
            isLoading = false
            errorMsg = "WebView error"
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

    val attemptedUrl = loadedUrl ?: saved.serverUrl.trim().trimEnd('/')
    val isFirstRun = !saved.hasConfigured &&
        (saved.serverUrl.isBlank() ||
            saved.serverUrl.trim().trimEnd('/') == AppWebSettings.DEFAULT_SERVER_URL)

    // Nothing but the WebView on screen once it loads; setup/error cards only
    // overlay until the first page succeeds.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (!pageLoaded) {
            when {
                // First run: show setup immediately instead of a black screen
                // while the unreachable placeholder times out.
                isFirstRun && errorMsg == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Connect to opencode", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Enter the address of your opencode web UI on your network.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = setupUrl ?: saved.serverUrl,
                                    onValueChange = { setupUrl = it },
                                    label = { Text("Server URL") },
                                    placeholder = { Text("http://192.168.1.10:4096") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(onGo = {
                                        focusManager.clearFocus()
                                        val cleaned =
                                            (setupUrl ?: saved.serverUrl).trim().trimEnd('/')
                                        if (cleaned.isBlank()) return@KeyboardActions
                                        scope.launch {
                                            prefs.save(
                                                saved.copy(
                                                    serverUrl = cleaned,
                                                    hasConfigured = true
                                                )
                                            )
                                        }
                                    }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        val cleaned =
                                            (setupUrl ?: saved.serverUrl).trim().trimEnd('/')
                                        if (cleaned.isBlank()) return@Button
                                        scope.launch {
                                            prefs.save(
                                                saved.copy(
                                                    serverUrl = cleaned,
                                                    hasConfigured = true
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Connect")
                                }
                                TextButton(
                                    onClick = { showSheet = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("More options (user, password, text size)")
                                }
                            }
                        }
                    }
                }
                errorMsg != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Couldn't reach the server",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    attemptedUrl.ifBlank { "(no URL set)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    errorMsg ?: "Load failed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Check the address, make sure the server is running and this " +
                                        "phone is on the same network. Shake the phone anytime for options.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showSheet = true },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Options") }
                                    Button(
                                        onClick = { retry() },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Retry") }
                                }
                            }
                        }
                    }
                }
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Loading…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            if (attemptedUrl.isNotBlank()) {
                                Text(
                                    attemptedUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        } else if (errorMsg != null) {
            // A later in-app navigation failed: non-blocking banner.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            errorMsg ?: "Load failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { retry() }) { Text("Retry") }
                        TextButton(onClick = { errorMsg = null }) { Text("Hide") }
                    }
                }
            }
        }
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
                            desktopMode = (draftDesktop ?: saved.desktopMode),
                            hasConfigured = true
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
                                urlChanged && target.isNotBlank() -> {
                                    loadedUrl = target
                                    errorMsg = null
                                    isLoading = true
                                    webView.loadUrl(target)
                                }
                                authChanged || desktopChanged -> {
                                    errorMsg = null
                                    isLoading = true
                                    webView.reload()
                                }
                                // Text zoom applies via LaunchedEffect without a reload.
                            }
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
                        onClick = { retry() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reload") }
                    OutlinedButton(
                        onClick = {
                            webView.clearCache(true)
                            webView.clearFormData()
                            webView.clearHistory()
                            retry()
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
                TextButton(
                    onClick = {
                        val link = webView.url
                            ?: saved.serverUrl.trim().trimEnd('/').ifBlank { null }
                        if (link != null) {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open in external browser (compare rendering)") }

                DiagnosticsSection(
                    currentUrl = webView.url ?: loadedUrl ?: saved.serverUrl.trim(),
                    webView = webView
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(currentUrl: String?, webView: WebView) {
    val context = LocalContext.current
    val diagLines by WebDiagnostics.lines.collectAsState()
    val probe by WebDiagnostics.probe.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
        Text(
            "WebView: ${WebDiagnostics.webViewVersion(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!currentUrl.isNullOrBlank()) {
            Text(
                "URL: $currentUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (diagLines.isEmpty()) {
                    Text(
                        "No page errors captured yet. Reload the page, then check back here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    diagLines.takeLast(10).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { WebDiagnostics.clear() },
                modifier = Modifier.weight(1f)
            ) { Text("Clear") }
            OutlinedButton(
                onClick = {
                    val text = buildString {
                        append("WebView: ${WebDiagnostics.webViewVersion(context)}\n")
                        append("URL: ${currentUrl ?: "(none)"}\n")
                        append("Probe: ${probe ?: "(not run)"}\n")
                        diagLines.forEach { append(it).append('\n') }
                    }
                    try {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("diagnostics", text))
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copy") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    WebDiagnostics.clear()
                    WebDiagnostics.setProbe(null)
                    try {
                        webView.reload()
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Reload & capture") }
            OutlinedButton(
                onClick = {
                    try {
                        webView.evaluateJavascript(
                            "(function(){try{return JSON.stringify({html:" +
                                "document.documentElement.outerHTML.length,text:" +
                                "document.body?document.body.innerText.slice(0,300):''});}" +
                                "catch(e){return JSON.stringify({error:String(e)});}})()",
                            WebDiagnostics::setProbe
                        )
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Probe content") }
        }
        if (probe != null) {
            Text(
                "Probe: $probe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
