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

/**
 * Talks the OpenAI Chat Completions API against any user supplied base URL.
 *
 * This is the format used by OpenAI, OpenRouter, Groq, Together, Ollama,
 * LM Studio, MiniMax and many other services, so the app stays usable even
 * when the user does not have an Anthropic key.
 */
object OpenAiClient {

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

    fun cancelActive() {
        activeCall?.cancel()
        activeCall = null
    }

    /** Resolves the chat completions endpoint from a user supplied base URL. */
    fun endpointFor(baseUrl: String): String {
        var b = baseUrl.trim().trimEnd('/')
        if (b.isEmpty()) b = "https://api.openai.com/v1"
        if (b.endsWith("/chat/completions")) return b
        if (b.endsWith("/v1")) return "$b/chat/completions"
        return "$b/v1/chat/completions"
    }

    private fun extraHeaders(settings: AppSettings): Map<String, String> {
        val raw = settings.extraHeaders.trim()
        if (raw.isEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, String>()
            obj.keys().forEach { key -> out[key] = obj.optString(key) }
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
        if (settings.apiKey.isNotBlank()) {
            when (settings.authStyle) {
                Providers.AUTH_X_API_KEY -> builder.header("x-api-key", settings.apiKey)
                Providers.AUTH_BEARER -> builder.header("Authorization", "Bearer ${settings.apiKey}")
                else -> {
                    builder.header("Authorization", "Bearer ${settings.apiKey}")
                    builder.header("x-api-key", settings.apiKey)
                }
            }
        }
        extraHeaders(settings).forEach { (k, v) -> builder.header(k, v) }
        return builder
    }

    private fun contentParts(message: Message): JSONArray {
        val parts = JSONArray()
        if (message.text.isNotBlank()) {
            parts.put(JSONObject().put("type", "text").put("text", message.text))
        }
        message.attachments.forEach { att ->
            when (att.kind) {
                "image" -> {
                    val b64 = att.base64 ?: return@forEach
                    parts.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:${att.mediaType};base64,$b64")
                            )
                    )
                }
                else -> {
                    val t = att.text
                    if (!t.isNullOrBlank()) {
                        parts.put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", "<file name=\"${att.name}\">\n$t\n</file>")
                        )
                    } else {
                        parts.put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", "<file name=\"${att.name}\">\n(binary, tidak dapat dibaca)\n</file>")
                        )
                    }
                }
            }
        }
        return parts
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
        body.put("stream", stream)
        if (miniMax) {
            // Thinking tokens count against the limit, and MiniMax asks new
            // integrations to use max_completion_tokens.
            body.put("max_completion_tokens", MiniMaxSupport.effectiveMaxTokens(settings))
        } else {
            body.put("max_tokens", maxOf(settings.maxTokens, 1))
            body.put("temperature", settings.temperature.toDouble())
            body.put("top_p", settings.topP.toDouble())
        }
        if (miniMax) {
            MiniMaxSupport.thinkingParam(settings)?.let { body.put("thinking", it) }
            val effort = MiniMaxSupport.effortValue(settings.reasoningEffort)
            if (effort != null && MiniMaxSupport.supportsEffort(settings.model)) {
                body.put("reasoning_effort", effort)
            }
        }
        val messages = JSONArray()
        val systemPrompt = MiniMaxSupport.systemPromptFor(settings, tools.isNotEmpty())
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        chat.messages.filter { it.role == "user" || it.role == "assistant" }
            .filter {
                it.text.isNotBlank() || it.attachments.isNotEmpty() ||
                    it.toolCalls.isNotEmpty() || it.toolResults.isNotEmpty() ||
                    (it.role == "assistant" && it.thinking.isNotBlank())
            }
            .forEach { m ->
                // Tool results travel as their own "tool" messages.
                if (m.toolResults.isNotEmpty()) {
                    m.toolResults.forEach { result ->
                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", result.toolCallId)
                                .put("name", result.name)
                                .put("content", result.content)
                        )
                    }
                    return@forEach
                }
                val parts = contentParts(m)
                val obj = JSONObject().put("role", m.role)
                if (m.toolCalls.isNotEmpty()) {
                    obj.put("content", if (m.text.isBlank()) JSONObject.NULL else m.text)
                    val calls = JSONArray()
                    m.toolCalls.forEach { call ->
                        calls.put(
                            JSONObject()
                                .put("id", call.id)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", call.name)
                                        .put("arguments", call.arguments)
                                )
                        )
                    }
                    obj.put("tool_calls", calls)
                } else if (parts.length() == 1 && m.attachments.isEmpty()) {
                    obj.put("content", parts.getJSONObject(0).optString("text"))
                } else {
                    obj.put("content", parts)
                }
                // MiniMax needs the reasoning of the previous turn back to
                // keep the chain of thought uninterrupted.
                if (miniMax && MiniMaxSupport.thinkingActive(settings) &&
                    m.role == "assistant" && m.thinking.isNotBlank()
                ) {
                    obj.put("reasoning_content", m.thinking)
                }
                messages.put(obj)
            }
        body.put("messages", messages)
        if (tools.isNotEmpty()) {
            val list = JSONArray()
            tools.forEach { list.put(it.toOpenAiJson()) }
            body.put("tools", list)
            body.put("tool_choice", "auto")
        }
        if (useServerSearch) {
            body.put("web_search_options", JSONObject().put("search_context_size", "medium"))
        }
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
        val body = buildBody(
            chat,
            settings,
            stream = true,
            tools = tools,
            useServerSearch = useServerSearch,
        )
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
                    ?: throw ApiException("Respons kosong", response.code)
                val toolState = ToolCallState()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    handlePayload(payload, onDelta, toolState)
                }
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    private fun handlePayload(
        payload: String,
        onDelta: (StreamDelta) -> Unit,
        toolState: ToolCallState,
    ) {
        val obj = try {
            JSONObject(payload)
        } catch (e: Exception) {
            return
        }
        val error = obj.optJSONObject("error")
        if (error != null) {
            throw ApiException(error.optString("message").ifBlank { "Kesalahan API" })
        }
        val choices = obj.optJSONArray("choices") ?: return
        if (choices.length() == 0) return
        val choice = choices.getJSONObject(0)
        val delta = choice.optJSONObject("delta")
        if (delta != null) {
            val reasoning = delta.optString("reasoning_content")
            if (reasoning.isNotBlank()) onDelta(StreamDelta.Thinking(reasoning))
            val content = delta.optString("content")
            if (content.isNotBlank()) onDelta(StreamDelta.Text(content))
            val calls = delta.optJSONArray("tool_calls")
            if (calls != null) {
                for (i in 0 until calls.length()) {
                    val call = calls.optJSONObject(i) ?: continue
                    val index = call.optInt("index", i)
                    val function = call.optJSONObject("function")
                    toolState.update(
                        index = index,
                        id = call.optString("id"),
                        name = function?.optString("name").orEmpty(),
                        arguments = function?.optString("arguments").orEmpty(),
                    )                    ?.let { onDelta(it) }
                }
            }
            readAnnotations(delta.optJSONArray("annotations"))?.forEach { onDelta(it) }
        }
        val message = choice.optJSONObject("message")
        if (message != null) {
            val reasoning = message.optString("reasoning_content")
            if (reasoning.isNotBlank()) onDelta(StreamDelta.Thinking(reasoning))
            val content = message.optString("content")
            if (content.isNotBlank()) onDelta(StreamDelta.Text(content))
            readAnnotations(message.optJSONArray("annotations"))?.forEach { onDelta(it) }
        }
        val finish = choice.optString("finish_reason")
        if (finish.isNotBlank()) onDelta(StreamDelta.Status(finish))
    }

    /** Turns the url_citation annotations of a web search into sources. */
    private fun readAnnotations(array: JSONArray?): List<StreamDelta.Source>? {
        if (array == null || array.length() == 0) return null
        val out = mutableListOf<StreamDelta.Source>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val citation = item.optJSONObject("url_citation")
            val url = citation?.optString("url").orEmpty().ifBlank { item.optString("url") }
            if (url.isBlank()) continue
            val title = citation?.optString("title").orEmpty().ifBlank { url }
            out.add(StreamDelta.Source(title = title, url = url))
        }
        return out.ifEmpty { null }
    }

    /**
     * Collects the tool calls that stream in piece by piece.
     *
     * The first chunk of a call carries the id and the name, the later ones
     * only add characters to the arguments.
     */
    private class ToolCallState {
        private val ids = HashMap<Int, String>()
        private val names = HashMap<Int, String>()
        private val buffers = HashMap<Int, StringBuilder>()

        fun update(index: Int, id: String, name: String, arguments: String): StreamDelta.ToolCall? {
            if (id.isNotBlank()) ids[index] = id
            if (name.isNotBlank()) names[index] = name
            if (arguments.isNotBlank()) {
                buffers.getOrPut(index) { StringBuilder() }.append(arguments)
            }
            val callId = ids[index] ?: return null
            val callName = names[index] ?: return null
            return StreamDelta.ToolCall(
                id = callId,
                name = callName,
                arguments = buffers[index]?.toString() ?: "{}",
            )
        }
    }

    /** Simple connectivity probe used by the settings screen. */
    suspend fun testConnection(settings: AppSettings): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", settings.model)
            .put("max_tokens", 1)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", "ping"))
            )
        val request = baseRequest(settings, body).header("Accept", "application/json").build()
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
