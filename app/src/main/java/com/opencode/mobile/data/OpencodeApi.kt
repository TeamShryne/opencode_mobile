package com.opencode.mobile.data

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// ---------------------------------------------------------------------------
// Full `opencode serve` HTTP surface (https://opencode.ai/docs/server).
// Every group below maps 1:1 to a docs section so the app can do everything
// the TUI / web client / SDK can do.
// ---------------------------------------------------------------------------
interface OpencodeApi {
    // Global
    @GET("global/health")
    suspend fun health(): HealthResponse

    // Project / path / VCS / instance
    @GET("project")
    suspend fun projects(@Query("directory") directory: String? = null): List<Project>

    @GET("project/current")
    suspend fun currentProject(@Query("directory") directory: String? = null): Project

    @GET("path")
    suspend fun path(): PathInfo

    @GET("vcs")
    suspend fun vcs(): VcsInfo

    @POST("instance/dispose")
    suspend fun disposeInstance(): Boolean

    // Config
    @GET("config")
    suspend fun getConfig(@Query("directory") directory: String? = null): JsonObject

    @PATCH("config")
    suspend fun updateConfig(@Body body: JsonObject): JsonObject

    @GET("config/providers")
    suspend fun configProviders(): ConfigProvidersResponse

    // Provider
    @GET("provider")
    suspend fun providers(): ProviderListResponse

    @GET("provider/auth")
    suspend fun providerAuth(): Map<String, List<ProviderAuthMethod>>

    @POST("provider/{id}/oauth/authorize")
    suspend fun oauthAuthorize(@Path("id") id: String): ProviderAuthAuthorization

    @POST("provider/{id}/oauth/callback")
    suspend fun oauthCallback(
        @Path("id") id: String,
        @Body body: OAuthCallbackRequest
    ): Boolean

    // Sessions
    @GET("session")
    suspend fun sessions(): List<Session>

    @POST("session")
    suspend fun createSession(@Body body: CreateSessionRequest = CreateSessionRequest()): Session

    @GET("session/status")
    suspend fun sessionStatuses(): Map<String, SessionStatusResponse>

    @GET("session/{id}")
    suspend fun session(@Path("id") id: String): Session

    @DELETE("session/{id}")
    suspend fun deleteSession(@Path("id") id: String): Boolean

    @PATCH("session/{id}")
    suspend fun updateSession(
        @Path("id") id: String,
        @Body body: UpdateSessionRequest
    ): Session

    @GET("session/{id}/children")
    suspend fun sessionChildren(@Path("id") id: String): List<Session>

    @GET("session/{id}/todo")
    suspend fun sessionTodos(@Path("id") id: String): List<Todo>

    @POST("session/{id}/init")
    suspend fun sessionInit(
        @Path("id") id: String,
        @Body body: InitRequest
    ): Boolean

    @POST("session/{id}/fork")
    suspend fun forkSession(
        @Path("id") id: String,
        @Body body: ForkRequest = ForkRequest()
    ): Session

    @POST("session/{id}/abort")
    suspend fun abortSession(@Path("id") id: String): Boolean

    @POST("session/{id}/share")
    suspend fun shareSession(@Path("id") id: String): Session

    @DELETE("session/{id}/share")
    suspend fun unshareSession(@Path("id") id: String): Session

    @GET("session/{id}/diff")
    suspend fun sessionDiff(
        @Path("id") id: String,
        @Query("messageID") messageID: String? = null
    ): List<FileDiff>

    @POST("session/{id}/summarize")
    suspend fun summarizeSession(
        @Path("id") id: String,
        @Body body: SummarizeRequest
    ): Boolean

    @POST("session/{id}/revert")
    suspend fun revertSession(
        @Path("id") id: String,
        @Body body: RevertRequest
    ): Boolean

    @POST("session/{id}/unrevert")
    suspend fun unrevertSession(@Path("id") id: String): Boolean

    @POST("session/{id}/permissions/{permissionID}")
    suspend fun respondPermission(
        @Path("id") id: String,
        @Path("permissionID") permissionID: String,
        @Body body: PermissionRespondRequest
    ): Boolean

    // Messages
    @GET("session/{id}/message")
    suspend fun messages(
        @Path("id") id: String,
        @Query("limit") limit: Int? = null
    ): List<SessionMessageDto>

    @POST("session/{id}/message")
    suspend fun prompt(
        @Path("id") id: String,
        @Body body: PromptRequest
    ): SessionMessageDto

    @GET("session/{id}/message/{messageID}")
    suspend fun message(
        @Path("id") id: String,
        @Path("messageID") messageID: String
    ): SessionMessageDto

    @POST("session/{id}/prompt_async")
    suspend fun promptAsync(
        @Path("id") id: String,
        @Body body: PromptRequest
    ): Response<Unit>

    @POST("session/{id}/command")
    suspend fun runCommand(
        @Path("id") id: String,
        @Body body: CommandExecuteRequest
    ): SessionMessageDto

    @POST("session/{id}/shell")
    suspend fun runShell(
        @Path("id") id: String,
        @Body body: ShellRequest
    ): SessionMessageDto

    // Commands
    @GET("command")
    suspend fun commands(): List<OpencodeCommand>

    // Files
    @GET("find")
    suspend fun findText(@Query("pattern") pattern: String): List<FindTextMatch>

    @GET("find/file")
    suspend fun findFiles(
        @Query("query") query: String,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int? = null
    ): List<String>

    @GET("find/symbol")
    suspend fun findSymbols(@Query("query") query: String): List<WorkspaceSymbol>

    @GET("file")
    suspend fun listFiles(@Query("path") path: String? = null): List<FileNode>

    @GET("file/content")
    suspend fun fileContent(@Query("path") path: String): JsonObject

    @GET("file/status")
    suspend fun fileStatus(): List<FileStatus>

    // Experimental tools
    @GET("experimental/tool/ids")
    suspend fun toolIds(): List<String>

    @GET("experimental/tool")
    suspend fun toolList(
        @Query("provider") provider: String,
        @Query("model") model: String
    ): JsonObject

    // LSP / formatter / MCP
    @GET("lsp")
    suspend fun lsp(): List<LspStatus>

    @GET("formatter")
    suspend fun formatters(): List<FormatterStatus>

    @GET("mcp")
    suspend fun mcp(): Map<String, McpStatus>

    @POST("mcp")
    suspend fun addMcp(@Body body: McpAddRequest): McpStatus

    // Agents
    @GET("agent")
    suspend fun agents(): List<Agent>

    // Logging
    @POST("log")
    suspend fun log(@Body body: LogRequest): Boolean

    // PTY (in OpenAPI spec / SDK, powers the terminal)
    @GET("pty")
    suspend fun ptys(): List<Pty>

    @POST("pty")
    suspend fun createPty(@Body body: PtyCreateRequest = PtyCreateRequest()): Pty

    @GET("pty/{id}")
    suspend fun pty(@Path("id") id: String): Pty

    @DELETE("pty/{id}")
    suspend fun removePty(@Path("id") id: String): Boolean

    // TUI remote control
    @POST("tui/append-prompt")
    suspend fun tuiAppend(@Body body: TuiAppendRequest): Boolean

    @POST("tui/open-help")
    suspend fun tuiHelp(): Boolean

    @POST("tui/open-sessions")
    suspend fun tuiSessions(): Boolean

    @POST("tui/open-themes")
    suspend fun tuiThemes(): Boolean

    @POST("tui/open-models")
    suspend fun tuiModels(): Boolean

    @POST("tui/submit-prompt")
    suspend fun tuiSubmit(): Boolean

    @POST("tui/clear-prompt")
    suspend fun tuiClear(): Boolean

    @POST("tui/execute-command")
    suspend fun tuiCommand(@Body body: TuiCommandRequest): Boolean

    @POST("tui/show-toast")
    suspend fun tuiToast(@Body body: TuiToastRequest): Boolean

    // Auth
    @PUT("auth/{id}")
    suspend fun setAuth(
        @Path("id") id: String,
        @Body body: JsonObject
    ): Boolean
}
