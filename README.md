# Opencode Mobile — premium Android interface for `opencode serve`

Native Kotlin + Jetpack Compose client for the headless opencode server.
**Build policy: GitHub Actions only.** No local `./gradlew` builds — every APK comes from CI
(`.github/workflows/android.yml`, triggered/watched via `gh`).

## Researched: `opencode serve` API (v1.18.29, docs 2026-09-06)

Source: `https://opencode.ai/docs/server`, `/docs/sdk`, `/docs/cli`, SDK `types.gen.ts`,
plus `opencode serve --help` / `opencode --help`.

Start: `opencode serve [--port 4096] [--hostname 127.0.0.1] [--mdns] [--mdns-domain opencode.local] [--cors <origin>]`
Auth: `OPENCODE_SERVER_PASSWORD` (+ optional `OPENCODE_SERVER_USERNAME`, default `opencode`) → HTTP Basic on both `serve` and `web`.
Spec: `http://<host>:<port>/doc` (OpenAPI 3.1, also used to generate `@opencode-ai/sdk`).
Arch: TUI is just a client of the same server; `serve` runs headless for API access. `attach`/`--attach` reuses a running server.

### Endpoint coverage (all implemented in `data/OpencodeApi.kt`)

| Group | Method + Path | App surface |
|---|---|---|
| Global | `GET /global/health`, `GET /global/event` (SSE) | Connection check + version, live bus |
| Project | `GET /project`, `GET /project/current` | Dashboard project list |
| Path/VCS | `GET /path`, `GET /vcs` | Dashboard workdir + branch |
| Instance | `POST /instance/dispose` | (wired in repo) |
| Config | `GET /config`, `PATCH /config`, `GET /config/providers` | Dashboard config viewer |
| Provider | `GET /provider`, `GET /provider/auth`, `POST /provider/{id}/oauth/authorize`, `POST /provider/{id}/oauth/callback` | Models screen + `PUT /auth` helper |
| Sessions | `GET /session`, `POST /session`, `GET /session/status`, `GET/PATCH/DELETE /session/{id}`, `GET /session/{id}/children`, `GET /session/{id}/todo`, `POST /session/{id}/init`, `POST /session/{id}/fork`, `POST /session/{id}/abort`, `POST+DELETE /session/{id}/share`, `GET /session/{id}/diff`, `POST /session/{id}/summarize`, `POST /session/{id}/revert`, `POST /session/{id}/unrevert`, `POST /session/{id}/permissions/{permissionID}` | Sessions list + chat actions + todos + diffs + share links + permission replies |
| Messages | `GET /session/{id}/message`, `POST /session/{id}/message`, `GET /session/{id}/message/{messageID}`, `POST /session/{id}/prompt_async`, `POST /session/{id}/command`, `POST /session/{id}/shell` | Chat send/async/slash/shell, per-message revert/fork |
| Commands | `GET /command` | Models screen + `/cmd` in chat |
| Files | `GET /find?pattern=`, `GET /find/file?query=`, `GET /find/symbol?query=`, `GET /file?path=`, `GET /file/content?path=`, `GET /file/status` | Files browser/search/preview/status |
| Tools (exp) | `GET /experimental/tool/ids`, `GET /experimental/tool?provider=&model=` | Catalog tools row |
| LSP/Fmt/MCP | `GET /lsp`, `GET /formatter`, `GET /mcp`, `POST /mcp` | Dashboard infra cards |
| Agents | `GET /agent` | Models screen + chat agent picker state |
| Logging | `POST /log` | Infra “send log” |
| PTY | `GET /pty`, `POST /pty`, `GET/DELETE /pty/{id}` (from OpenAPI/SDK; powers terminal) | Infra terminal |
| TUI | `POST /tui/append-prompt|open-help|open-sessions|open-themes|open-models|submit-prompt|clear-prompt|execute-command|show-toast`, `GET /tui/control/next`, `POST /tui/control/response` | Infra TUI remote |
| Auth | `PUT /auth/{id}` | Providers auth helper |
| Events | `GET /event` SSE (`server.connected` first, then `message.updated`, `message.part.updated`+`delta`, `permission.updated`, `session.*`, `todo.updated`, `file.*`, `pty.*`, `tui.*`, …) | `SseManager` (okhttp-sse), chat auto-refresh |
| Docs | `GET /doc` | Link in connection help |

Message parts rendered: `text`, `reasoning`, `file`, `tool` (pending/running/completed/error), `step-start`/`step-finish`, `snapshot`, `patch`, `agent`, `retry`, `compaction`, `subtask`.
Permissions: `allow/ask/deny` (+ auto mode `--auto`); granular `bash`/`edit` patterns; per-agent overrides — surfaced as revert/fork/abort + permission-reply API.
Providers: 75+ via Models.dev + custom `provider` config; Zen/Go; `small_model`; OAuth + API keys in `auth.json`.
Agents: primary `build`/`plan`, subagents `general`/`explore`/`scout` (+ hidden `compaction`/`title`/`summary`); `@mention` routing; switch via Tab.

## App architecture

- `MainActivity` → `NavGraph` (Compose Navigation): Connection → Dashboard / Sessions → Chat / Files / Models / Infra(Terminal+TUI).
- `data/`: `OpencodeApi` (Retrofit, all routes), `ApiModels` (kotlinx.serialization, `ignoreUnknownKeys`), `ApiClient` (OkHttp + Basic auth + `X-Client`), `PreferencesRepository` (DataStore: baseUrl/username/password), `SseManager` (SSE `/event`), `OpencodeRepository` (one facade per docs group).
- `ui/`: Material3 dark-first premium theme, `PremiumTopBar`/`EmptyState`/`StatusDot`/`MessageBubble` (per-part cards, mono for tools/patches), ViewModels (`Connection/Sessions/Chat/Files/Catalog/Infra`) with StateFlow.
- Cleartext HTTP allowed (LAN `serve` is plain HTTP) via `network_security_config.xml`; DataStore excluded from cloud backup.

## Run against a server

```bash
# on your dev machine:
opencode serve --hostname 0.0.0.0 --port 4096
# optional auth:
OPENCODE_SERVER_PASSWORD=secret opencode serve --hostname 0.0.0.0 --port 4096
```

In the app: enter `http://<lan-ip>:4096` (+ user/pass) → Connect → Chats → new session → pick model/agent → send. `/cmd args` runs slash commands.

## Builds — GitHub Actions only

Do **not** run `./gradlew` locally. Use:

```bash
gh workflow run "Android CI (only build path)" --ref master
gh run watch --exit-status
gh run download <id> -n opencode-mobile-debug
```

Workflow (`.github/workflows/android.yml`): JDK 17 (Temurin) + Android SDK + Gradle 8.7 wrapper, `:app:assembleDebug`, uploads `opencode-mobile-debug` APK artifact, build reports on failure.
