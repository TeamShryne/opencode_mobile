package com.opencode.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// opencode `serve` API models.
// Mirrors https://opencode.ai/docs/server + SDK types.gen.ts.
// Unknown fields are ignored so the app survives server upgrades.
// ---------------------------------------------------------------------------

@Serializable
data class HealthResponse(
    val healthy: Boolean = false,
    val version: String = ""
)

@Serializable
data class Project(
    val id: String = "",
    val worktree: String = "",
    val vcsDir: String? = null,
    val vcs: String? = null
)

@Serializable
data class PathInfo(
    val state: String = "",
    val config: String = "",
    val worktree: String = "",
    val directory: String = ""
)

@Serializable
data class VcsInfo(
    val branch: String = ""
)

@Serializable
data class ProviderModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0
)

@Serializable
data class ProviderModelLimit(
    val context: Long = 0,
    val output: Long = 0
)

@Serializable
data class ProviderModel(
    val id: String = "",
    val providerID: String = "",
    val name: String = "",
    val cost: ProviderModelCost = ProviderModelCost(),
    val limit: ProviderModelLimit = ProviderModelLimit(),
    val status: String = "active"
)

@Serializable
data class Provider(
    val id: String = "",
    val name: String = "",
    val source: String = "",
    val env: List<String> = emptyList(),
    val models: Map<String, ProviderModel> = emptyMap()
)

@Serializable
data class ProviderListResponse(
    val all: List<Provider> = emptyList(),
    val connected: List<String> = emptyList(),
    val `default`: Map<String, String> = emptyMap()
)

@Serializable
data class ConfigProvidersResponse(
    val providers: List<Provider> = emptyList(),
    val `default`: Map<String, String> = emptyMap()
)

@Serializable
data class ProviderAuthMethod(
    val type: String = "",
    val label: String = ""
)

@Serializable
data class ProviderAuthAuthorization(
    val url: String = "",
    val method: String = "auto",
    val instructions: String = ""
)

@Serializable
data class Session(
    val id: String = "",
    val projectID: String = "",
    val directory: String = "",
    val parentID: String? = null,
    val title: String = "",
    val version: String = "",
    val share: SessionShare? = null
)

@Serializable
data class SessionShare(val url: String = "")

@Serializable
data class SessionStatusResponse(
    val type: String = "idle",
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null
)

@Serializable
data class Todo(
    val id: String = "",
    val content: String = "",
    val status: String = "pending",
    val priority: String = "medium"
)

@Serializable
data class FileDiff(
    val file: String = "",
    val before: String = "",
    val after: String = "",
    val additions: Int = 0,
    val deletions: Int = 0
)

@Serializable
data class OpencodeCommand(
    val name: String = "",
    val description: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val template: String = "",
    val subtask: Boolean = false
)

@Serializable
data class Agent(
    val name: String = "",
    val description: String? = null,
    val mode: String = "all",
    val builtIn: Boolean = false,
    val color: String? = null,
    val model: AgentModel? = null
)

@Serializable
data class AgentModel(
    val modelID: String = "",
    val providerID: String = ""
)

@Serializable
data class FileNode(
    val name: String = "",
    val path: String = "",
    val absolute: String = "",
    val type: String = "file",
    val ignored: Boolean = false
)

@Serializable
data class FileStatus(
    val path: String = "",
    val added: Int = 0,
    val removed: Int = 0,
    val status: String = "modified"
)

@Serializable
data class WorkspaceSymbol(
    val name: String = "",
    val kind: Int = 0
)

@Serializable
data class FindTextMatch(
    val path: String = "",
    val line_number: Int = 0,
    val lines: JsonObject? = null
)

@Serializable
data class McpStatus(
    val status: String = "connected",
    val error: String? = null
)

@Serializable
data class LspStatus(
    val id: String = "",
    val name: String = "",
    val root: String = "",
    val status: String = "connected"
)

@Serializable
data class FormatterStatus(
    val name: String = "",
    val extensions: List<String> = emptyList(),
    val enabled: Boolean = false
)

@Serializable
data class Pty(
    val id: String = "",
    val title: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val cwd: String = "",
    val status: String = "running",
    val pid: Int = 0
)

@Serializable
data class Permission(
    val id: String = "",
    val type: String = "",
    val sessionID: String = "",
    val messageID: String = "",
    val callID: String? = null,
    val title: String = "",
    val time: PermissionTime? = null
)

@Serializable
data class PermissionTime(val created: Long = 0)

// --- Message envelopes: info + parts are intentionally JsonObject-based ---
// Parts union (text/reasoning/file/tool/step-start/step-finish/snapshot/patch/
// agent/retry/compaction/subtask) evolves fast; UI parses `type` field.

@Serializable
data class SessionMessageDto(
    val info: JsonObject,
    val parts: List<JsonObject> = emptyList()
)

@Serializable
data class CreateSessionRequest(
    val parentID: String? = null,
    val title: String? = null
)

@Serializable
data class UpdateSessionRequest(
    val title: String? = null
)

@Serializable
data class ModelRef(
    val providerID: String,
    val modelID: String
)

@Serializable
data class TextPartInput(
    val type: String = "text",
    val text: String,
    val synthetic: Boolean? = null
)

@Serializable
data class PromptRequest(
    val messageID: String? = null,
    val model: ModelRef? = null,
    val agent: String? = null,
    val noReply: Boolean? = null,
    val system: String? = null,
    val tools: Map<String, Boolean>? = null,
    val parts: List<TextPartInput>
)

@Serializable
data class CommandExecuteRequest(
    val messageID: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val command: String,
    val arguments: String = ""
)

@Serializable
data class ShellRequest(
    val agent: String,
    val model: ModelRef? = null,
    val command: String
)

@Serializable
data class ForkRequest(val messageID: String? = null)

@Serializable
data class InitRequest(
    val messageID: String,
    val providerID: String,
    val modelID: String
)

@Serializable
data class SummarizeRequest(
    val providerID: String,
    val modelID: String
)

@Serializable
data class RevertRequest(
    val messageID: String,
    val partID: String? = null
)

@Serializable
data class PermissionRespondRequest(
    val response: String
)

// Wire shapes (verified against openapi.json + live /event traffic):
// permission.asked -> PermissionRequestDto directly; permission.replied ->
// { sessionID, requestID }. question.asked -> QuestionRequestDto;
// question.replied/rejected -> { sessionID, requestID }.
// question reply body: answers in order, each an array of selected labels.
@Serializable
data class PermissionRequestDto(
    val id: String = "",
    val sessionID: String = "",
    val permission: String = "",
    val patterns: List<String> = emptyList(),
    val metadata: JsonObject? = null,
    val always: List<String> = emptyList()
)

@Serializable
data class QuestionOptionDto(
    val label: String = "",
    val description: String = ""
)

@Serializable
data class QuestionInfoDto(
    val question: String = "",
    val header: String = "",
    val options: List<QuestionOptionDto> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = false
)

@Serializable
data class QuestionRequestDto(
    val id: String = "",
    val sessionID: String = "",
    val questions: List<QuestionInfoDto> = emptyList()
)

@Serializable
data class QuestionReplyRequest(
    val answers: List<List<String>>
)

@Serializable
data class LogRequest(
    val service: String,
    val level: String,
    val message: String,
    val extra: JsonObject? = null
)

@Serializable
data class TuiCommandRequest(val command: String)

@Serializable
data class TuiToastRequest(
    val message: String,
    val title: String? = null,
    val variant: String = "info"
)

@Serializable
data class TuiAppendRequest(val text: String)

@Serializable
data class McpAddRequest(
    val name: String,
    val config: JsonObject
)

@Serializable
data class OAuthCallbackRequest(
    val code: String? = null,
    val state: String? = null,
    val url: String? = null
)

@Serializable
data class PtyCreateRequest(
    val command: String? = null,
    val args: List<String> = emptyList(),
    val cwd: String? = null,
    val title: String? = null
)
