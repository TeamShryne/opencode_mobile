package com.opencode.mobile.data

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Single repository fronting the whole `serve` surface so ViewModels stay thin.
// Mirrors the docs groups: global/project/config/provider/session/message/
// command/file/tool/lsp/mcp/agent/log/pty/tui/auth.
class OpencodeRepository(
    private val prefs: PreferencesRepository
) {
    var api: OpencodeApi? = null
        private set
    var settings: ConnectionSettings = ConnectionSettings()
        private set

    suspend fun connect(s: ConnectionSettings): Result<HealthResponse> {
        settings = s
        prefs.save(s)
        api = ApiClient.create(s.baseUrl, s.username, s.password)
        return try {
            Result.success(requireApi().health())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadSaved(): ConnectionSettings {
        val s = prefs.settings.first()
        settings = s
        api = ApiClient.create(s.baseUrl, s.username, s.password)
        return s
    }

    fun sseClient() = ApiClient.sseClient(settings.username, settings.password)

    private fun requireApi(): OpencodeApi =
        api ?: ApiClient.create(settings.baseUrl, settings.username, settings.password)
            .also { api = it }

    // -- global/project/path/vcs -------------------------------------------
    suspend fun projects() = requireApi().projects()
    suspend fun currentProject() = runCatching { requireApi().currentProject() }.getOrNull()
    suspend fun path() = runCatching { requireApi().path() }.getOrNull()
    suspend fun vcs() = runCatching { requireApi().vcs() }.getOrNull()
    suspend fun disposeInstance() = runCatching { requireApi().disposeInstance() }.getOrDefault(false)

    // -- config/provider ----------------------------------------------------
    suspend fun config(): JsonObject? = runCatching { requireApi().getConfig() }.getOrNull()
    suspend fun saveConfig(obj: JsonObject): Boolean =
        runCatching { requireApi().updateConfig(obj); true }.getOrDefault(false)
    suspend fun configProviders() = runCatching { requireApi().configProviders() }.getOrNull()
    suspend fun providers() = runCatching { requireApi().providers() }.getOrNull()
    suspend fun providerAuth() = runCatching { requireApi().providerAuth() }.getOrDefault(emptyMap())

    // -- sessions ------------------------------------------------------------
    suspend fun sessions() = requireApi().sessions()
    suspend fun createSession(title: String?): Session =
        requireApi().createSession(CreateSessionRequest(title = title?.ifBlank { null }))
    suspend fun deleteSession(id: String) = requireApi().deleteSession(id)
    suspend fun renameSession(id: String, title: String) =
        requireApi().updateSession(id, UpdateSessionRequest(title))
    suspend fun sessionStatuses() =
        runCatching { requireApi().sessionStatuses() }.getOrDefault(emptyMap())
    suspend fun children(id: String) = runCatching { requireApi().sessionChildren(id) }.getOrDefault(emptyList())
    suspend fun todos(id: String) = runCatching { requireApi().sessionTodos(id) }.getOrDefault(emptyList())
    suspend fun diff(id: String) = runCatching { requireApi().sessionDiff(id) }.getOrDefault(emptyList())
    suspend fun abort(id: String) = runCatching { requireApi().abortSession(id) }.getOrDefault(false)
    suspend fun fork(id: String, messageID: String?) =
        requireApi().forkSession(id, ForkRequest(messageID?.ifBlank { null }))
    suspend fun share(id: String) = requireApi().shareSession(id)
    suspend fun unshare(id: String) = requireApi().unshareSession(id)
    suspend fun revert(id: String, messageID: String) =
        requireApi().revertSession(id, RevertRequest(messageID))
    suspend fun unrevert(id: String) = runCatching { requireApi().unrevertSession(id) }.getOrDefault(false)
    suspend fun summarize(id: String, providerID: String, modelID: String) =
        requireApi().summarizeSession(id, SummarizeRequest(providerID, modelID))
    suspend fun initSession(id: String, messageID: String, providerID: String, modelID: String) =
        requireApi().sessionInit(id, InitRequest(messageID, providerID, modelID))
    suspend fun respondPermission(sessionID: String, permissionID: String, response: String) =
        requireApi().respondPermission(sessionID, permissionID, PermissionRespondRequest(response))

    suspend fun pendingPermissions() =
        runCatching { requireApi().pendingPermissions() }.getOrDefault(emptyList())

    suspend fun pendingQuestions() =
        runCatching { requireApi().pendingQuestions() }.getOrDefault(emptyList())

    suspend fun replyQuestion(requestID: String, answers: List<List<String>>) =
        runCatching { requireApi().replyQuestion(requestID, QuestionReplyRequest(answers)) }.getOrDefault(false)

    suspend fun rejectQuestion(requestID: String) =
        runCatching { requireApi().rejectQuestion(requestID) }.getOrDefault(false)

    // -- messages -------------------------------------------------------------
    suspend fun messages(id: String) = requireApi().messages(id)
    suspend fun send(id: String, text: String, model: ModelRef?, agent: String?) =
        requireApi().prompt(
            id,
            PromptRequest(model = model, agent = agent?.ifBlank { null }, parts = listOf(TextPartInput(text = text)))
        )
    suspend fun sendAsync(id: String, text: String, model: ModelRef?, agent: String?) =
        runCatching {
            requireApi().promptAsync(
                id,
                PromptRequest(model = model, agent = agent?.ifBlank { null }, parts = listOf(TextPartInput(text = text)))
            ); true
        }.getOrDefault(false)
    suspend fun runCommand(id: String, command: String, args: String, agent: String?, model: ModelRef?) =
        requireApi().runCommand(id, CommandExecuteRequest(agent = agent?.ifBlank { null }, model = model, command = command, arguments = args))
    suspend fun runShell(id: String, agent: String, command: String, model: ModelRef?) =
        requireApi().runShell(id, ShellRequest(agent, model, command))

    // -- commands/agents -------------------------------------------------------
    suspend fun commands() = runCatching { requireApi().commands() }.getOrDefault(emptyList())
    suspend fun agents() = runCatching { requireApi().agents() }.getOrDefault(emptyList())

    // -- files ------------------------------------------------------------------
    suspend fun listFiles(path: String?) = runCatching { requireApi().listFiles(path) }.getOrDefault(emptyList())
    suspend fun fileContent(path: String) = runCatching { requireApi().fileContent(path) }.getOrNull()
    suspend fun fileStatus() = runCatching { requireApi().fileStatus() }.getOrDefault(emptyList())
    suspend fun findText(pattern: String) = runCatching { requireApi().findText(pattern) }.getOrDefault(emptyList())
    suspend fun findFiles(query: String) = runCatching { requireApi().findFiles(query) }.getOrDefault(emptyList())
    suspend fun findSymbols(query: String) = runCatching { requireApi().findSymbols(query) }.getOrDefault(emptyList())

    // -- infra -------------------------------------------------------------------
    suspend fun tools() = runCatching { requireApi().toolIds() }.getOrDefault(emptyList())
    suspend fun lsp() = runCatching { requireApi().lsp() }.getOrDefault(emptyList())
    suspend fun formatters() = runCatching { requireApi().formatters() }.getOrDefault(emptyList())
    suspend fun mcp() = runCatching { requireApi().mcp() }.getOrDefault(emptyMap())
    suspend fun ptys() = runCatching { requireApi().ptys() }.getOrDefault(emptyList())
    suspend fun createPty(command: String?) =
        runCatching { requireApi().createPty(PtyCreateRequest(command = command?.ifBlank { null })) }.getOrNull()

    suspend fun log(service: String, level: String, message: String) =
        runCatching { requireApi().log(LogRequest(service, level, message)) }.getOrDefault(false)

    // -- tui ----------------------------------------------------------------------
    suspend fun tuiAppend(text: String) = runCatching { requireApi().tuiAppend(TuiAppendRequest(text)) }.getOrDefault(false)
    suspend fun tuiToast(message: String, variant: String) =
        runCatching { requireApi().tuiToast(TuiToastRequest(message = message, variant = variant)) }.getOrDefault(false)
    suspend fun tuiSimple(action: String): Boolean = runCatching {
        val a = requireApi()
        when (action) {
            "help" -> a.tuiHelp()
            "sessions" -> a.tuiSessions()
            "themes" -> a.tuiThemes()
            "models" -> a.tuiModels()
            "submit" -> a.tuiSubmit()
            "clear" -> a.tuiClear()
            else -> false
        }
    }.getOrDefault(false)

    suspend fun setAuth(id: String, key: String): Boolean = runCatching {
        requireApi().setAuth(id, buildJsonObject { put("type", "api"); put("key", key) }); true
    }.getOrDefault(false)
}
