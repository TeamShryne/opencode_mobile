# Opencode Mobile — WebView wrapper for the opencode web UI

Simple Android app: a single full-screen `WebView` that opens your opencode web UI.
No native screens, no top bars — just the web UI.

**Build policy: GitHub Actions only.** No local `./gradlew` builds — every APK comes from CI
(`.github/workflows/android.yml`, triggered/watched via `gh`).

## Behavior

- `MainActivity` → `WebViewScreen` (one Compose screen, `Box(fillMaxSize)` + `AndroidView(WebView)`).
- WebView is full-screen edge-to-edge, no other UI elements.
- App feel (not website feel):
  - Pinch zoom disabled (`setSupportZoom(false)`, no built-in controls, viewport forced to
    `maximum-scale=1.0, user-scalable=no` via JS on every page finish).
  - Overscroll glow / stretch disabled (`OVER_SCROLL_NEVER`, no scrollbars).
  - Links stay inside the WebView, rotation doesn't reload (`configChanges`),
    keyboard uses `adjustResize`.
  - Basic-auth (`OPENCODE_SERVER_PASSWORD`) is answered automatically from saved credentials.
- Options are hidden behind a **shake gesture**:
  - Shake the phone → bottom sheet opens (with a short vibration).
  - Options: server URL, username/password, text size (50–200%), desktop-site toggle,
    Apply & load, Reload, Clear & reload, Back/Forward.
  - Persisted with DataStore (`WebPrefs`).
- System back navigates WebView history.

## Structure

- `MainActivity` — `enableEdgeToEdge()` + `WebViewScreen(app.webPrefs)`.
- `OpencodeApp` — exposes `WebPrefs` only.
- `data/WebPrefs.kt` — DataStore: `serverUrl`, `username`, `password`, `textZoom`, `desktopMode`.
- `ui/WebViewScreen.kt` — full-screen WebView + shake-to-open settings sheet.
- `util/ShakeDetector.kt` — accelerometer shake detector (2 shakes, ~2.7g).
- `ui/theme/Theme.kt` — Material3 theme (only used by the settings sheet).

## Run against a server

```bash
# on your dev machine (example):
opencode serve --hostname 0.0.0.0 --port 4096
# or whatever command serves your opencode web UI
```

In the app: shake → enter `http://<lan-ip>:4096` (+ user/pass if set) → Apply & load.

Cleartext HTTP allowed (LAN is plain HTTP) via `network_security_config.xml`.

## Builds — GitHub Actions only

Do **not** run `./gradlew` locally. Use:

```bash
gh workflow run "Android CI (only build path)" --ref master
gh run watch --exit-status
gh run download <id> -n opencode-mobile-debug
```

Workflow (`.github/workflows/android.yml`): JDK 17 (Temurin) + Android SDK + Gradle 8.7 wrapper, `:app:assembleDebug`, uploads `opencode-mobile-debug` APK artifact, build reports on failure.
