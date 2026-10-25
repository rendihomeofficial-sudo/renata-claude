package com.renata.claude.data

/**
 * One entry point for every supported provider.
 *
 * The app speaks two wire formats (Anthropic Messages and OpenAI Chat
 * Completions); this facade picks the right client based on the user setting
 * so the rest of the app never has to care.
 */
object AiClient {

    fun cancelActive() {
        AnthropicClient.cancelActive()
        OpenAiClient.cancelActive()
    }

    suspend fun streamChat(
        chat: Chat,
        settings: AppSettings,
        onDelta: (StreamDelta) -> Unit,
        tools: List<ToolSpec> = emptyList(),
        useServerSearch: Boolean = false,
    ) {
        if (settings.apiFormat == Providers.FORMAT_OPENAI) {
            OpenAiClient.streamChat(chat, settings, onDelta, tools, useServerSearch)
        } else {
            AnthropicClient.streamChat(chat, settings, onDelta, tools, useServerSearch)
        }
    }

    suspend fun testConnection(settings: AppSettings): String {
        return if (settings.apiFormat == Providers.FORMAT_OPENAI) {
            OpenAiClient.testConnection(settings)
        } else {
            AnthropicClient.testConnection(settings)
        }
    }
}
