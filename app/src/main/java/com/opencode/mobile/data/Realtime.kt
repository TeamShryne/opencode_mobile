package com.opencode.mobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PendingPermission(
    val id: String,
    val sessionId: String,
    val messageId: String,
    val kind: String,
    val title: String
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

    /** Event name when the SSE `event:` field is missing. */
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

    fun todos(p: JsonObject): List<Todo>? {
        val arr: JsonArray = (p["todos"] as? JsonArray) ?: return null
        return runCatching { json.decodeFromJsonElement<List<Todo>>(arr) }.getOrNull()
    }

    fun permission(p: JsonObject): PendingPermission? {
        val perm = (p["permission"] as? JsonObject) ?: p
        val id = str(perm, "id") ?: return null
        return PendingPermission(
            id = id,
            sessionId = str(perm, "sessionID", "sessionId") ?: "",
            messageId = str(perm, "messageID", "messageId") ?: "",
            kind = str(perm, "type") ?: "permission",
            title = str(perm, "title") ?: "Approval needed"
        )
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
