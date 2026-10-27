package com.renata.claude.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class StreamDelta {
    data class Text(val text: String) : StreamDelta()
    data class Thinking(val text: String) : StreamDelta()

    /** The signature of a thinking block, needed to send the chain back. */
    data class ThinkingSignature(val signature: String) : StreamDelta()

    data class Status(val stopReason: String?) : StreamDelta()

    /** The model asked for a tool. [arguments] grows while the block streams. */
    data class ToolCall(val id: String, val name: String, val arguments: String) : StreamDelta()

    /** A source produced by the provider's server side web search. */
    data class Source(val title: String, val url: String, val text: String = "") : StreamDelta()
}

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

/**
 * Talks the Anthropic Messages API against any user supplied base URL.
 * No account, no login, no client side quota.
 */
object AnthropicClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    /** Aborts the in-flight streaming request, if any. */
    fun cancelActive() {
        activeCall?.cancel()
        activeCall = null
    }

    /** Resolves the messages endpoint from a user supplied base URL. */
    fun endpointFor(baseUrl: String): String {
        var b = baseUrl.trim().trimEnd('/')
        if (b.isEmpty()) b = "https://api.anthropic.com"
        if (b.endsWith("/messages")) return b
        if (b.endsWith("/v1")) return "$b/messages"
        return "$b/v1/messages"
    }

    private fun extraHeaders(settings: AppSettings): Map<String, String> {
        val raw = settings.extraHeaders.trim()
        if (raw.isEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, String>()
            obj.keys().forEach { key ->
                out[key] = obj.optString(key)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun baseRequest(settings: AppSettings, body: JSONObject): Request.Builder {
        val builder = Request.Builder()
            .url(endpointFor(settings.baseUrl))
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("anthropic-version", "2023-06-01")
        if (settings.apiKey.isNotBlank()) {
            when (settings.authStyle) {
                Providers.AUTH_X_API_KEY -> builder.header("x-api-key", settings.apiKey)
                Providers.AUTH_BEARER -> builder.header("Authorization", "Bearer ${settings.apiKey}")
                else -> {
                    builder.header("x-api-key", settings.apiKey)
                    builder.header("Authorization", "Bearer ${settings.apiKey}")
                }
            }
        }
        extraHeaders(settings).forEach { (k, v) -> builder.header(k, v) }
        return builder
    }

    private fun contentBlocks(message: Message, keepThinking: Boolean): JSONArray {
        val blocks = JSONArray()
        // The thinking block has to come first in an assistant turn, otherwise
        // the provider rejects the request.
        if (keepThinking && message.thinking.isNotBlank()) {
            val block = JSONObject()
                .put("type", "thinking")
                .put("thinking", message.thinking)
            if (message.thinkingSignature.isNotBlank()) {
                block.put("signature", message.thinkingSignature)
            }
            blocks.put(block)
        }
        if (message.text.isNotBlank()) {
            blocks.put(JSONObject().put("type", "text").put("text", message.text))
        }
        message.toolCalls.forEach { call ->
            val input = runCatching { JSONObject(call.arguments) }.getOrElse { JSONObject() }
            blocks.put(
                JSONObject()
                    .put("type", "tool_use")
                    .put("id", call.id)
                    .put("name", call.name)
                    .put("input", input)
            )
        }
        message.toolResults.forEach { result ->
            blocks.put(
                JSONObject()
                    .put("type", "tool_result")
                    .put("tool_use_id", result.toolCallId)
                    .put("content", result.content)
                    .put("is_error", result.isError)
            )
        }
        message.attachments.forEach { att ->
            when (att.kind) {
                "image" -> blocks.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", att.mediaType)
                                .put("data", att.base64 ?: "")
                        )
                )
                "document" -> blocks.put(
                    JSONObject()
                        .put("type", "document")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", att.mediaType)
                                .put("data", att.base64 ?: "")
                        )
                )
                else -> {
                    val t = att.text ?: ""
                    if (t.isNotBlank()) {
                        blocks.put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", "<file name=\"${att.name}\">\n$t\n</file>")
                        )
                    }
                }
            }
        }
        return blocks
    }

    fun buildBody(
        chat: Chat,
        settings: AppSettings,
        stream: Boolean,
        tools: List<ToolSpec> = emptyList(),
        useServerSearch: Boolean = false,
    ): JSONObject {
        val body = JSONObject()
        val miniMax = MiniMaxSupport.isMiniMax(settings.model)
        body.put("model", settings.model)
        body.put(
            "max_tokens",
            if (miniMax) {
                MiniMaxSupport.effectiveMaxTokens(settings)
            } else {
                maxOf(settings.maxTokens, if (settings.thinking) settings.thinkingBudget + 1024 else 1)
            }
        )
        if (!miniMax) {
            // MiniMax ignores both values anyway.
            body.put("temperature", if (settings.thinking) 1.0 else settings.temperature.toDouble())
            body.put("top_p", settings.topP.toDouble())
        }
        body.put("stream", stream)
        val systemPrompt = MiniMaxSupport.systemPromptFor(settings, tools.isNotEmpty())
        if (systemPrompt.isNotBlank()) {
            body.put("system", systemPrompt)
        }
        if (miniMax) {
            // MiniMax-M3.1-Flash rejects `disabled`, so the thinking parameter
            // is decided per model family.
            MiniMaxSupport.thinkingParam(settings)?.let { body.put("thinking", it) }
            val effort = MiniMaxSupport.effortValue(settings.reasoningEffort)
            if (effort != null && MiniMaxSupport.supportsEffort(settings.model)) {
                body.put("output_config", JSONObject().put("effort", effort))
            }
        } else if (settings.thinking) {
            body.put(
                "thinking",
                JSONObject().put("type", "enabled").put("budget_tokens", settings.thinkingBudget)
            )
        }
        val toolList = JSONArray()
        tools.forEach { toolList.put(it.toAnthropicJson()) }
        if (useServerSearch) {
            toolList.put(
                JSONObject()
                    .put("type", "web_search_20250305")
                    .put("name", "web_search")
                    .put("max_uses", 5)
            )
        }
        if (toolList.length() > 0) {
            body.put("tools", toolList)
            body.put("tool_choice", JSONObject().put("type", "auto"))
        }
        // The reasoning chain is only sent back when it can be sent back
        // safely: MiniMax does not need a signature, the Anthropic API does.
        val messages = JSONArray()
        chat.messages.filter { it.role == "user" || it.role == "assistant" }
            .filter {
                it.text.isNotBlank() || it.attachments.isNotEmpty() ||
                    it.toolCalls.isNotEmpty() || it.toolResults.isNotEmpty() ||
                    (it.role == "assistant" && it.thinking.isNotBlank())
            }
            .forEach { m ->
                val obj = JSONObject().put("role", m.role)
                val keepThinking = m.role == "assistant" &&
                    ((miniMax && MiniMaxSupport.thinkingActive(settings)) ||
                        m.thinkingSignature.isNotBlank())
                val blocks = contentBlocks(m, keepThinking)
                if (blocks.length() == 1 && m.attachments.isEmpty() &&
                    m.toolCalls.isEmpty() && m.toolResults.isEmpty() &&
                    m.thinking.isBlank()
                ) {
                    obj.put("content", m.text)
                } else {
                    obj.put("content", blocks)
                }
                messages.put(obj)
            }
        body.put("messages", messages)
        return body
    }

    /** Streams a completion, invoking [onDelta] for every text/thinking chunk. */
    suspend fun streamChat(
        chat: Chat,
        settings: AppSettings,
        onDelta: (StreamDelta) -> Unit,
        tools: List<ToolSpec> = emptyList(),
        useServerSearch: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val body = buildBody(chat, settings, stream = true, tools = tools, useServerSearch = useServerSearch)
        val request = baseRequest(settings, body).header("Accept", "text/event-stream").build()
        val call = client.newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw ApiException(describeHttpError(response.code, err), response.code)
            }
            val source = response.body?.source()
                ?: throw ApiException("Empty response body", response.code)
            val toolState = ToolState()
            var eventName = ""
            val data = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trim())
                    }
                    line.isBlank() -> {
                        if (data.isNotEmpty()) {
                            handleEvent(eventName, data.toString(), onDelta, toolState)
                            eventName = ""
                            data.setLength(0)
                        }
                    }
                }
            }
            if (data.isNotEmpty()) handleEvent(eventName, data.toString(), onDelta, toolState)
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    /** Keeps track of the tool blocks that are streaming in. */
    private class ToolState {
        private val calls = LinkedHashMap<Int, Pair<String, String>>()
        private val json = HashMap<Int, StringBuilder>()

        fun start(index: Int, id: String, name: String): StreamDelta.ToolCall {
            calls[index] = id to name
            json[index] = StringBuilder()
            return StreamDelta.ToolCall(id, name, "{}")
        }

        fun append(index: Int, partial: String): StreamDelta.ToolCall? {
            val (id, name) = calls[index] ?: return null
            val builder = json.getOrPut(index) { StringBuilder() }
            builder.append(partial)
            return StreamDelta.ToolCall(id, name, builder.toString())
        }
    }

    private fun handleEvent(
        eventName: String,
        payload: String,
        onDelta: (StreamDelta) -> Unit,
        toolState: ToolState,
    ) {
        if (payload == "[DONE]") return
        val obj = try {
            JSONObject(payload)
        } catch (e: Exception) {
            return
        }
        when (obj.optString("type", eventName)) {
            "content_block_start" -> {
                val block = obj.optJSONObject("content_block") ?: return
                val index = obj.optInt("index", 0)
                when (block.optString("type")) {
                    "thinking" -> {
                        val signature = block.optString("signature")
                        if (signature.isNotBlank()) {
                            onDelta(StreamDelta.ThinkingSignature(signature))
                        }
                        val initial = block.optString("thinking")
                        if (initial.isNotBlank()) onDelta(StreamDelta.Thinking(initial))
                    }
                    "tool_use" -> {
                        val id = block.optString("id")
                        val name = block.optString("name")
                        val input = block.optJSONObject("input")
                        val args = if (input != null && input.length() > 0) input.toString() else "{}"
                        onDelta(toolState.start(index, id, name).let { delta ->
                            if (args == "{}") delta else StreamDelta.ToolCall(delta.id, delta.name, args)
                        })
                    }
                    "citations" -> {
                        val citation = block.optJSONObject("citation")
                        val url = citation?.optString("url").orEmpty()
                        if (url.isNotBlank()) {
                            onDelta(
                                StreamDelta.Source(
                                    title = citation?.optString("title").orEmpty().ifBlank { url },
                                    url = url,
                                    text = citation?.optString("cited_text").orEmpty(),
                                )
                            )
                        }
                    }
                    "web_search_tool_result" -> {
                        val results = block.optJSONArray("content") ?: return
                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val url = item.optString("url")
                            if (url.isBlank()) continue
                            onDelta(
                                StreamDelta.Source(
                                    title = item.optString("title").ifBlank { url },
                                    url = url,
                                )
                            )
                        }
                    }
                }
            }
            "content_block_delta" -> {
                val delta = obj.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text_delta" -> onDelta(StreamDelta.Text(delta.optString("text")))
                    "thinking_delta" -> onDelta(StreamDelta.Thinking(delta.optString("thinking")))
                    "signature_delta" -> {
                        val signature = delta.optString("signature")
                        if (signature.isNotBlank()) {
                            onDelta(StreamDelta.ThinkingSignature(signature))
                        }
                    }
                    "input_json_delta" -> {
                        val index = obj.optInt("index", 0)
                        toolState.append(index, delta.optString("partial_json"))?.let { onDelta(it) }
                    }
                }
            }
            "message_delta" -> {
                val delta = obj.optJSONObject("delta")
                onDelta(StreamDelta.Status(delta?.optString("stop_reason")))
            }
            "error" -> {
                val err = obj.optJSONObject("error")
                throw ApiException(err?.optString("message") ?: "Unknown API error")
            }
        }
    }

    /** Simple connectivity probe used by the settings screen. */
    suspend fun testConnection(settings: AppSettings): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", settings.model)
            .put("max_tokens", 1)
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", "ping")
            ))
        val request = baseRequest(settings, body)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                "Koneksi OK (HTTP ${response.code})"
            } else {
                "Gagal (HTTP ${response.code}): " + describeHttpError(response.code, text)
            }
        }
    }

    private fun describeHttpError(code: Int, body: String): String {
        val parsed = try {
            val obj = JSONObject(body)
            val err = obj.optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("detail").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
        val hint = when (code) {
            401, 403 -> " - Kunci API ditolak. Periksa kunci di Pengaturan."
            404 -> " - Endpoint tidak ditemukan. Periksa Base URL."
            429 -> " - Dibatasi oleh penyedia."
            in 500..599 -> " - Kesalahan server penyedia."
            else -> ""
        }
        return (parsed ?: body.take(300).ifBlank { "HTTP $code" }) + hint
    }
}
