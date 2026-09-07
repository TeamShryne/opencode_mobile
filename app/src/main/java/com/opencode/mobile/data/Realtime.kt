package com.opencode.mobile.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PendingPermission(
    val id: String,
    val sessionId: String,
    /** e.g. "bash", "edit", "webfetch" */
    val kind: String,
    val title: String,
    val patterns: List<String> = emptyList()
)

data class PendingQuestion(
    val id: String,
    val sessionId: String,
    val questions: List<QuestionInfoDto>
)

data class PartDelta(
    val messageId: String,
    val partId: String,
    val field: String,
    val delta: String
)

/** Tolerant parsing for `GET /event` payloads (properties object or full envelope). */
object Realtime {
    private val json
        get() = ApiClient.json

    fun props(raw: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return null
        return (root["properties"] as? JsonObject) ?: root
    }

    /** Event name when the SSE `event:` field is missing (the server omits it). */
    fun typeOf(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return null
        return root["type"]?.jsonPrimitive?.content
    }

    fun str(obj: JsonObject, vararg names: String): String? {
        for (n in names) {
            obj[n]?.jsonPrimitive?.content?.let { return it }
        }
        return null
    }

    /** sessionID carried by most event shapes (top level, info, part, status...). */
    fun sessionIdOf(p: JsonObject): String? {
        str(p, "sessionID", "sessionId")?.let { return it }
        (p["info"] as? JsonObject)?.let { str(it, "sessionID", "sessionId")?.let { s -> return s } }
        (p["part"] as? JsonObject)?.let { str(it, "sessionID", "sessionId")?.let { s -> return s } }
        return null
    }

    fun messageInfo(p: JsonObject): JsonObject? = p["info"] as? JsonObject

    fun part(p: JsonObject): JsonObject? = p["part"] as? JsonObject

    fun delta(p: JsonObject): String? = p["delta"]?.jsonPrimitive?.content

    /** message.part.delta -> { messageID, partID, field, delta } (no sessionID). */
    fun partDelta(p: JsonObject): PartDelta? {
        val mid = str(p, "messageID", "messageId") ?: return null
        val pid = str(p, "partID", "partId") ?: return null
        val field = str(p, "field") ?: "text"
        val d = str(p, "delta") ?: return null
        return PartDelta(mid, pid, field, d)
    }

    fun todos(p: JsonObject): List<Todo>? {
        val arr: JsonArray = (p["todos"] as? JsonArray) ?: return null
        return runCatching { json.decodeFromJsonElement(ListSerializer(Todo.serializer()), arr) }.getOrNull()
    }

    /** permission.asked properties ARE the PermissionRequest: {id, sessionID, permission, patterns, metadata}. */
    fun permission(p: JsonObject): PendingPermission? {
        val id = str(p, "id") ?: return null
        if (!id.startsWith("per")) return null
        val meta = p["metadata"] as? JsonObject
        val title = meta?.let { str(it, "title") }
            ?: str(p, "title")
            ?: str(p, "permission")
            ?: "Approval needed"
        val patterns = (p["patterns"] as? JsonArray)
            ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            .orEmpty()
        return PendingPermission(
            id = id,
            sessionId = str(p, "sessionID", "sessionId") ?: "",
            kind = str(p, "permission", "type") ?: "permission",
            title = title,
            patterns = patterns
        )
    }

    /** permission.replied/question.replied/question.rejected -> { sessionID, requestID }. */
    fun repliedRequestId(p: JsonObject): String? =
        str(p, "requestID", "requestId", "id")

    /** question.asked properties ARE the QuestionRequest: {id, sessionID, questions[]}. */
    fun question(p: JsonObject): PendingQuestion? {
        val id = str(p, "id") ?: return null
        if (!id.startsWith("que")) return null
        val sid = str(p, "sessionID", "sessionId") ?: return null
        val arr = (p["questions"] as? JsonArray) ?: return null
        val qs = runCatching {
            json.decodeFromJsonElement(ListSerializer(QuestionInfoDto.serializer()), arr)
        }.getOrNull() ?: return null
        return PendingQuestion(id, sid, qs)
    }

    /** session.error -> { sessionID, error: { name, data: { message } } }. */
    fun sessionError(p: JsonObject): Pair<String, String>? {
        val sid = sessionIdOf(p) ?: str(p, "id") ?: return null
        val err = p["error"] as? JsonObject
        val msg = err?.let { e ->
            val data = e["data"] as? JsonObject
            str(data ?: e, "message") ?: str(e, "name")
        } ?: str(p, "message") ?: return null
        return sid to msg
    }

    /** (sessionId, statusType) for session.status / session.idle events. */
    fun status(p: JsonObject, eventType: String): Pair<String, String>? {
        val sid = sessionIdOf(p) ?: str(p, "id") ?: return null
        val t = when (eventType) {
            "session.idle" -> "idle"
            else -> (p["status"] as? JsonObject)?.let { str(it, "type") } ?: str(p, "type")
                ?: str(p, "status") ?: return null
        }
        return sid to t
    }

    /** (sessionId, messageId, partId?) for message.removed / message.part.removed. */
    fun removedMessage(p: JsonObject): Triple<String, String, String?> {
        val sid = sessionIdOf(p) ?: ""
        val mid = str(p, "messageID", "messageId") ?: ""
        val pid = str(p, "partID", "partId")
        return Triple(sid, mid, pid)
    }
}
