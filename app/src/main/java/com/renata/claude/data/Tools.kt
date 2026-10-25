package com.renata.claude.data

import org.json.JSONObject

/**
 * A tool the model asked to run.
 *
 * The id comes from the provider: `toolu_...` on Anthropic, `call_...` on
 * OpenAI. It is echoed back with the result so the provider can match them.
 */
data class ToolCall(
    val id: String,
    val name: String,
    /** Raw JSON arguments as sent by the model. */
    val arguments: String = "{}",
) {
    fun argument(name: String): String {
        val obj = runCatching { JSONObject(arguments) }.getOrNull() ?: return ""
        return obj.optString(name, "").ifBlank { obj.optString(name.lowercase(), "") }
    }

    fun prettyArguments(): String = runCatching {
        JSONObject(arguments).toString(2)
    }.getOrDefault(arguments)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("arguments", arguments)

    companion object {
        fun fromJson(o: JSONObject): ToolCall = ToolCall(
            id = o.optString("id"),
            name = o.optString("name"),
            arguments = o.optString("arguments", "{}"),
        )
    }
}

/** The outcome of a tool, sent back to the model. */
data class ToolResult(
    val toolCallId: String,
    val name: String,
    val content: String,
    val isError: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("toolCallId", toolCallId)
        .put("name", name)
        .put("content", content)
        .put("isError", isError)

    companion object {
        fun fromJson(o: JSONObject): ToolResult = ToolResult(
            toolCallId = o.optString("toolCallId"),
            name = o.optString("name"),
            content = o.optString("content", ""),
            isError = o.optBoolean("isError", false),
        )
    }
}

/**
 * A source returned by the provider's web search tool.
 *
 * Anthropic returns these as `citations` blocks and inside
 * `web_search_tool_result`; both are folded into this small model.
 */
data class Citation(
    val title: String,
    val url: String,
    val text: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("url", url)
        .put("text", text)

    companion object {
        fun fromJson(o: JSONObject): Citation = Citation(
            title = o.optString("title"),
            url = o.optString("url"),
            text = o.optString("text", ""),
        )
    }
}

/**
 * Declaration of a tool that can be exposed to the model.
 *
 * [inputSchema] is a plain JSON Schema object, exactly as the Anthropic
 * Messages API and the OpenAI Chat Completions API expect it.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    /** When true the user must approve the call before it runs. */
    val requiresApproval: Boolean = false,
) {
    fun toAnthropicJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("description", description)
        .put("input_schema", inputSchema)

    fun toOpenAiJson(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", inputSchema)
        )
}
