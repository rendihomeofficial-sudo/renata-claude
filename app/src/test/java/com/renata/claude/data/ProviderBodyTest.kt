package com.renata.claude.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two wire formats the app speaks.
 *
 * These tests check the request bodies only, so they run without a network
 * and without an API key.
 */
class ProviderBodyTest {

    private val settings = AppSettings(
        baseUrl = "https://example.com",
        apiKey = "kunci",
        model = "model-uji",
        apiFormat = Providers.FORMAT_OPENAI,
    )

    private val tool = ToolSpec(
        name = ToolRegistry.NAME_CALCULATE,
        description = "Hitung nilai",
        inputSchema = JSONObject().put("type", "object"),
    )

    private fun chatWith(vararg messages: Message) = Chat(messages = messages.toList())

    // --- OpenAI -----------------------------------------------------------

    @Test
    fun openAiBodySendsToolsAndToolChoice() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(Message(role = "user", text = "halo")),
            settings = settings,
            stream = true,
            tools = listOf(tool),
        )
        val tools = body.getJSONArray("tools")
        assertEquals(1, tools.length())
        assertEquals("function", tools.getJSONObject(0).getString("type"))
        assertEquals(
            ToolRegistry.NAME_CALCULATE,
            tools.getJSONObject(0).getJSONObject("function").getString("name"),
        )
        assertEquals("auto", body.getString("tool_choice"))
    }

    @Test
    fun openAiBodyWithoutToolsHasNoToolFields() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(Message(role = "user", text = "halo")),
            settings = settings,
            stream = true,
        )
        assertFalse(body.has("tools"))
        assertFalse(body.has("tool_choice"))
    }

    @Test
    fun openAiBodyKeepsAssistantToolCalls() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(
                Message(role = "user", text = "berapa?"),
                Message(
                    role = "assistant",
                    text = "",
                    toolCalls = listOf(
                        ToolCall(
                            id = "call_1",
                            name = ToolRegistry.NAME_CALCULATE,
                            arguments = """{"expression":"1+1"}""",
                        )
                    ),
                ),
            ),
            settings = settings,
            stream = true,
            tools = listOf(tool),
        )
        val messages = body.getJSONArray("messages")
        val assistant = messages.getJSONObject(messages.length() - 1)
        assertEquals("assistant", assistant.getString("role"))
        assertTrue("isi kosong harus dikirim sebagai null", assistant.isNull("content"))
        val calls = assistant.getJSONArray("tool_calls")
        assertEquals(1, calls.length())
        assertEquals("call_1", calls.getJSONObject(0).getString("id"))
        assertEquals(
            ToolRegistry.NAME_CALCULATE,
            calls.getJSONObject(0).getJSONObject("function").getString("name"),
        )
    }

    @Test
    fun openAiBodySendsToolResultsAsToolMessages() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(
                Message(role = "user", text = "berapa?"),
                Message(
                    role = "assistant",
                    toolCalls = listOf(ToolCall("call_1", ToolRegistry.NAME_CALCULATE, """{"expression":"1+1"}""")),
                ),
                Message(
                    role = "user",
                    text = "",
                    toolResults = listOf(
                        ToolResult(
                            toolCallId = "call_1",
                            name = ToolRegistry.NAME_CALCULATE,
                            content = "2 = 1+1",
                        )
                    ),
                ),
            ),
            settings = settings,
            stream = true,
            tools = listOf(tool),
        )
        val messages = body.getJSONArray("messages")
        val last = messages.getJSONObject(messages.length() - 1)
        assertEquals("tool", last.getString("role"))
        assertEquals("call_1", last.getString("tool_call_id"))
        assertEquals(ToolRegistry.NAME_CALCULATE, last.getString("name"))
        assertEquals("2 = 1+1", last.getString("content"))
    }

    @Test
    fun openAiBodyAddsWebSearchOptions() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(Message(role = "user", text = "cari")),
            settings = settings,
            stream = true,
            useServerSearch = true,
        )
        val search = body.getJSONObject("web_search_options")
        assertEquals("medium", search.getString("search_context_size"))
    }

    @Test
    fun openAiEndpointFollowsTheBaseUrl() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            OpenAiClient.endpointFor("https://example.com"),
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            OpenAiClient.endpointFor("https://example.com/v1"),
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            OpenAiClient.endpointFor("https://example.com/v1/chat/completions"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiClient.endpointFor(""),
        )
    }

    // --- Anthropic --------------------------------------------------------

    @Test
    fun anthropicBodySendsToolsWithInputSchema() {
        val body = AnthropicClient.buildBody(
            chat = chatWith(Message(role = "user", text = "halo")),
            settings = settings.copy(apiFormat = Providers.FORMAT_ANTHROPIC),
            stream = true,
            tools = listOf(tool),
        )
        val tools = body.getJSONArray("tools")
        assertEquals(1, tools.length())
        assertEquals(ToolRegistry.NAME_CALCULATE, tools.getJSONObject(0).getString("name"))
        assertEquals("object", tools.getJSONObject(0).getJSONObject("input_schema").getString("type"))
    }

    @Test
    fun anthropicBodyAddsTheServerSearchTool() {
        val body = AnthropicClient.buildBody(
            chat = chatWith(Message(role = "user", text = "cari")),
            settings = settings.copy(apiFormat = Providers.FORMAT_ANTHROPIC),
            stream = true,
            useServerSearch = true,
        )
        val tools = body.getJSONArray("tools")
        val search = tools.getJSONObject(tools.length() - 1)
        assertEquals("web_search_20250305", search.getString("type"))
        assertEquals("web_search", search.getString("name"))
        assertEquals(5, search.getInt("max_uses"))
    }

    @Test
    fun anthropicBodyKeepsToolUseAndToolResultBlocks() {
        val body = AnthropicClient.buildBody(
            chat = chatWith(
                Message(role = "user", text = "berapa?"),
                Message(
                    role = "assistant",
                    toolCalls = listOf(
                        ToolCall(
                            id = "toolu_1",
                            name = ToolRegistry.NAME_CALCULATE,
                            arguments = """{"expression":"1+1"}""",
                        )
                    ),
                ),
                Message(
                    role = "user",
                    toolResults = listOf(
                        ToolResult(
                            toolCallId = "toolu_1",
                            name = ToolRegistry.NAME_CALCULATE,
                            content = "2 = 1+1",
                        )
                    ),
                ),
            ),
            settings = settings.copy(apiFormat = Providers.FORMAT_ANTHROPIC),
            stream = true,
            tools = listOf(tool),
        )
        val messages = body.getJSONArray("messages")
        val assistantBlocks = messages.getJSONObject(1).getJSONArray("content")
        assertEquals("tool_use", assistantBlocks.getJSONObject(0).getString("type"))
        assertEquals("toolu_1", assistantBlocks.getJSONObject(0).getString("id"))
        assertEquals(
            "1+1",
            assistantBlocks.getJSONObject(0).getJSONObject("input").getString("expression"),
        )

        val userBlocks = messages.getJSONObject(2).getJSONArray("content")
        assertEquals("tool_result", userBlocks.getJSONObject(0).getString("type"))
        assertEquals("toolu_1", userBlocks.getJSONObject(0).getString("tool_use_id"))
        assertEquals("2 = 1+1", userBlocks.getJSONObject(0).getString("content"))
        assertFalse(userBlocks.getJSONObject(0).getBoolean("is_error"))
    }

    @Test
    fun anthropicBodyMarksFailedToolResults() {
        val body = AnthropicClient.buildBody(
            chat = chatWith(
                Message(
                    role = "user",
                    toolResults = listOf(
                        ToolResult(
                            toolCallId = "toolu_2",
                            name = ToolRegistry.NAME_CALCULATE,
                            content = "Ekspresi tidak bisa dihitung",
                            isError = true,
                        )
                    ),
                ),
            ),
            settings = settings.copy(apiFormat = Providers.FORMAT_ANTHROPIC),
            stream = true,
        )
        val block = body.getJSONArray("messages")
            .getJSONObject(0)
            .getJSONArray("content")
            .getJSONObject(0)
        assertTrue(block.getBoolean("is_error"))
    }

    @Test
    fun anthropicBodySkipsEmptyMessages() {
        val body = AnthropicClient.buildBody(
            chat = chatWith(
                Message(role = "user", text = "halo"),
                Message(role = "assistant", text = "   "),
            ),
            settings = settings.copy(apiFormat = Providers.FORMAT_ANTHROPIC),
            stream = true,
        )
        val messages: JSONArray = body.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("halo", messages.getJSONObject(0).getString("content"))
    }

    // --- MiniMax ----------------------------------------------------------

    private fun miniMaxSettings(
        model: String = "MiniMax-M3.1-Flash",
        format: String = Providers.FORMAT_OPENAI,
        thinking: Boolean = true,
        effort: String = "high",
    ) = AppSettings(
        baseUrl = "https://api.minimax.io",
        apiKey = "kunci",
        model = model,
        apiFormat = format,
        thinking = thinking,
        reasoningEffort = effort,
        maxTokens = 1024,
    )

    @Test
    fun miniMaxOpenAiBodyUsesTheMiniMaxParameters() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(Message(role = "user", text = "halo")),
            settings = miniMaxSettings(),
            stream = true,
            tools = listOf(tool),
        )
        assertTrue(body.getInt("max_completion_tokens") >= 8192)
        assertFalse("MiniMax menolak sampling", body.has("temperature"))
        assertFalse(body.has("top_p"))
        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"))
        assertEquals("high", body.getString("reasoning_effort"))
        assertTrue("thinking harus dipisah dari jawaban", body.getBoolean("reasoning_split"))
        val system = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("system", system.getString("role"))
        assertTrue(system.getString("content").contains("asisten AI profesional"))
    }

    @Test
    fun miniMaxOpenAiBodyKeepsTheReasoningOfThePreviousTurn() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(
                Message(role = "user", text = "berapa 2+2?"),
                Message(
                    role = "assistant",
                    text = "",
                    thinking = "langkah berpikir",
                    toolCalls = listOf(ToolCall("call_1", ToolRegistry.NAME_CALCULATE, "{}")),
                ),
            ),
            settings = miniMaxSettings(),
            stream = true,
            tools = listOf(tool),
        )
        val messages = body.getJSONArray("messages")
        val assistant = messages.getJSONObject(messages.length() - 1)
        assertEquals("langkah berpikir", assistant.getString("reasoning_content"))
        assertEquals(1, assistant.getJSONArray("tool_calls").length())
    }

    @Test
    fun miniMaxOpenAiBodySwitchesTheThinkingOffForM3() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(Message(role = "user", text = "halo")),
            settings = miniMaxSettings(model = "MiniMax-M3", thinking = false),
            stream = true,
        )
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertFalse(body.has("reasoning_effort"))
        assertTrue(body.getBoolean("reasoning_split"))
    }

    @Test
    fun miniMaxOpenAiBodySplitsTheThinkingForTheOlderModelToo() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(Message(role = "user", text = "halo")),
            settings = miniMaxSettings(model = "MiniMax-M3", thinking = true),
            stream = true,
        )
        assertTrue(body.getBoolean("reasoning_split"))
        assertFalse("hanya M3.1-Flash yang menerima kedalaman", body.has("reasoning_effort"))
    }

    @Test
    fun otherProvidersNeverGetTheMiniMaxParameters() {
        val body = OpenAiClient.buildBody(
            chat = chatWith(Message(role = "user", text = "halo")),
            settings = settings,
            stream = true,
        )
        assertFalse(body.has("reasoning_split"))
        assertFalse(body.has("reasoning_effort"))
        assertFalse(body.has("thinking"))
    }

    @Test
    fun miniMaxAnthropicBodyUsesTheOutputConfigEffort() {
        val body = AnthropicClient.buildBody(
            chat = chatWith(
                Message(role = "user", text = "berapa 2+2?"),
                Message(
                    role = "assistant",
                    text = "",
                    thinking = "langkah berpikir",
                    toolCalls = listOf(ToolCall("toolu_1", ToolRegistry.NAME_CALCULATE, "{}")),
                ),
            ),
            settings = miniMaxSettings(format = Providers.FORMAT_ANTHROPIC),
            stream = true,
            tools = listOf(tool),
        )
        assertEquals("high", body.getJSONObject("output_config").getString("effort"))
        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"))
        assertFalse("format Anthropic tidak memakai reasoning_split", body.has("reasoning_split"))
        assertTrue(body.getInt("max_tokens") >= 8192)
        val blocks = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("thinking", blocks.getJSONObject(0).getString("type"))
        assertEquals("langkah berpikir", blocks.getJSONObject(0).getString("thinking"))
        assertEquals("tool_use", blocks.getJSONObject(1).getString("type"))
    }

    @Test
    fun toolSpecConvertsToBothFormats() {
        assertEquals("calculate", tool.toAnthropicJson().getString("name"))
        assertEquals("object", tool.toAnthropicJson().getJSONObject("input_schema").getString("type"))
        assertEquals("function", tool.toOpenAiJson().getString("type"))
        assertEquals("calculate", tool.toOpenAiJson().getJSONObject("function").getString("name"))
        assertEquals(
            "object",
            tool.toOpenAiJson().getJSONObject("function").getJSONObject("parameters").getString("type"),
        )
    }
}
