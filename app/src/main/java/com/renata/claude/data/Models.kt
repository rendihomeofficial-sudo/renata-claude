package com.renata.claude.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A file or image attached to a user message. */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mediaType: String,
    /** "image", "document" or "text" */
    val kind: String,
    val base64: String? = null,
    val text: String? = null,
    val sizeBytes: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("mediaType", mediaType)
        put("kind", kind)
        base64?.let { put("base64", it) }
        text?.let { put("text", it) }
        put("sizeBytes", sizeBytes)
    }

    companion object {
        fun fromJson(o: JSONObject): Attachment = Attachment(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            mediaType = o.optString("mediaType", "application/octet-stream"),
            kind = o.optString("kind", "text"),
            base64 = if (o.has("base64")) o.optString("base64") else null,
            text = if (o.has("text")) o.optString("text") else null,
            sizeBytes = o.optLong("sizeBytes", 0L),
        )
    }
}

/** A single chat turn. */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    /** "user" or "assistant" */
    val role: String,
    val text: String = "",
    val thinking: String = "",
    val attachments: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val model: String? = null,
    val failed: Boolean = false,
    val stopReason: String? = null,
    /** Tool calls the assistant asked for in this turn. */
    val toolCalls: List<ToolCall> = emptyList(),
    /** Results of the tool calls, carried by the following user turn. */
    val toolResults: List<ToolResult> = emptyList(),
    /** Sources returned by the provider's web search tool. */
    val citations: List<Citation> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("text", text)
        put("thinking", thinking)
        put("attachments", JSONArray().also { arr -> attachments.forEach { arr.put(it.toJson()) } })
        put("createdAt", createdAt)
        model?.let { put("model", it) }
        put("failed", failed)
        stopReason?.let { put("stopReason", it) }
        put("toolCalls", JSONArray().also { arr -> toolCalls.forEach { arr.put(it.toJson()) } })
        put("toolResults", JSONArray().also { arr -> toolResults.forEach { arr.put(it.toJson()) } })
        put("citations", JSONArray().also { arr -> citations.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Message {
            val arr = o.optJSONArray("attachments") ?: JSONArray()
            return Message(
                id = o.optString("id", UUID.randomUUID().toString()),
                role = o.optString("role", "user"),
                text = o.optString("text", ""),
                thinking = o.optString("thinking", ""),
                attachments = (0 until arr.length()).map { Attachment.fromJson(arr.getJSONObject(it)) },
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                model = if (o.has("model")) o.optString("model") else null,
                failed = o.optBoolean("failed", false),
                stopReason = if (o.has("stopReason")) o.optString("stopReason") else null,
                toolCalls = (0 until (o.optJSONArray("toolCalls")?.length() ?: 0)).map {
                    ToolCall.fromJson(o.getJSONArray("toolCalls").getJSONObject(it))
                },
                toolResults = (0 until (o.optJSONArray("toolResults")?.length() ?: 0)).map {
                    ToolResult.fromJson(o.getJSONArray("toolResults").getJSONObject(it))
                },
                citations = (0 until (o.optJSONArray("citations")?.length() ?: 0)).map {
                    Citation.fromJson(o.getJSONArray("citations").getJSONObject(it))
                },
            )
        }
    }
}

/** A conversation. */
data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Percakapan baru",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("messages", JSONArray().also { arr -> messages.forEach { arr.put(it.toJson()) } })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("pinned", pinned)
    }

    companion object {
        fun fromJson(o: JSONObject): Chat {
            val arr = o.optJSONArray("messages") ?: JSONArray()
            return Chat(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Percakapan baru"),
                messages = (0 until arr.length()).map { Message.fromJson(arr.getJSONObject(it)) },
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                pinned = o.optBoolean("pinned", false),
            )
        }
    }
}

/**
 * Default persona for live voice: a warm friend, short answers, no markdown.
 */
const val DEFAULT_LIVE_VOICE_PROMPT =
    "Kamu adalah teman dekat yang sedang berbicara langsung dengan pengguna. Gunakan " +
        "kalimat pendek dan alami, satu atau dua klausa saja. Hangat dan perhatian, tapi " +
        "jangan berpura-pura merasakan hal yang tidak kamu rasakan. Jangan memakai " +
        "markdown, daftar, blok kode, emoji atau format khusus: semua yang kamu ucapkan " +
        "dibacakan dengan suara. Jika pengguna tampak sedih, temani dia dan tanyakan " +
        "apa yang dibutuhkannya, jangan buru-buru memberi solusi. Jawab dalam bahasa " +
        "yang dipakai pengguna."

/** Everything the user can customise. There is no account and no built-in quota. */
data class AppSettings(
    val baseUrl: String = "https://api.anthropic.com",
    val apiKey: String = "",
    val model: String = "claude-3-5-sonnet-latest",
    /** Id penyedia yang dipilih, lihat [Providers]. */
    val providerId: String = "anthropic",
    /** "anthropic" (Messages API) atau "openai" (Chat Completions API). */
    val apiFormat: String = Providers.FORMAT_ANTHROPIC,
    /** "x-api-key", "bearer" atau "both". */
    val authStyle: String = Providers.AUTH_X_API_KEY,
    val systemPrompt: String = "",
    val maxTokens: Int = 8192,
    val temperature: Float = 1.0f,
    val topP: Float = 1.0f,
    val themeMode: String = "system",
    val extraHeaders: String = "",
    val thinking: Boolean = false,
    val thinkingBudget: Int = 2048,
    val sendOnEnter: Boolean = false,
    /** Read the assistant's replies out loud in live voice mode. */
    val speakReplies: Boolean = true,
    /** BCP-47 tag for speech recognition and synthesis. Empty means the system default. */
    val voiceLanguage: String = "",
    /** System prompt used while the live voice screen is open. */
    val liveVoicePrompt: String = DEFAULT_LIVE_VOICE_PROMPT,
    /** Address of the version.json manifest used by the update check. */
    val updateManifestUrl: String = "",
    /** Lets the model call the local tools (clock, web fetch, artifacts, skills). */
    val toolsEnabled: Boolean = true,
    /** Uses the provider's server side web search and shows the sources. */
    val researchEnabled: Boolean = false,
    /** Requires the user to unlock the app before it can be used. */
    val lockEnabled: Boolean = false,
) {
    val apiKeySet: Boolean get() = apiKey.isNotBlank()
}
