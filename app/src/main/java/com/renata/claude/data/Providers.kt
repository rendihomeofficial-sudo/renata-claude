package com.renata.claude.data

/**
 * A ready made provider configuration.
 *
 * The app talks to two wire formats:
 *  - "anthropic": the Anthropic Messages API (`/v1/messages`), also used by
 *    MiniMax, GitHub Copilot gateways and many self hosted proxies.
 *  - "openai": the OpenAI Chat Completions API (`/chat/completions`), used by
 *    OpenAI itself, OpenRouter, Groq, Together, Ollama, LM Studio, MiniMax
 *    and a long tail of other services.
 */
data class ProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val apiFormat: String,
    val authStyle: String,
    val models: List<String>,
    val keyHint: String,
)

object Providers {

    const val FORMAT_ANTHROPIC = "anthropic"
    const val FORMAT_OPENAI = "openai"

    const val AUTH_X_API_KEY = "x-api-key"
    const val AUTH_BEARER = "bearer"
    const val AUTH_BOTH = "both"

    val presets: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "anthropic",
            label = "Anthropic (Claude)",
            baseUrl = "https://api.anthropic.com",
            apiFormat = FORMAT_ANTHROPIC,
            authStyle = AUTH_X_API_KEY,
            models = listOf(
                "claude-sonnet-4-5",
                "claude-opus-4-1",
                "claude-haiku-4-5",
                "claude-3-5-sonnet-latest",
            ),
            keyHint = "Kunci API dari console.anthropic.com",
        ),
        ProviderPreset(
            id = "minimax-anthropic",
            label = "MiniMax (format Anthropic)",
            baseUrl = "https://api.minimax.io/anthropic",
            apiFormat = FORMAT_ANTHROPIC,
            authStyle = AUTH_BOTH,
            models = listOf("MiniMax-M3.1-Flash", "MiniMax-M3"),
            keyHint = "Kunci API MiniMax dari platform.minimax.io",
        ),
        ProviderPreset(
            id = "minimax-openai",
            label = "MiniMax (format OpenAI)",
            baseUrl = "https://api.minimax.io/v1",
            apiFormat = FORMAT_OPENAI,
            authStyle = AUTH_BEARER,
            models = listOf("MiniMax-M3.1-Flash", "MiniMax-M3", "MiniMax-M2.7", "MiniMax-M2.5"),
            keyHint = "Kunci API MiniMax dari platform.minimax.io",
        ),
        ProviderPreset(
            id = "openai",
            label = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiFormat = FORMAT_OPENAI,
            authStyle = AUTH_BEARER,
            models = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini", "o4-mini"),
            keyHint = "Kunci API dari platform.openai.com",
        ),
        ProviderPreset(
            id = "openrouter",
            label = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiFormat = FORMAT_OPENAI,
            authStyle = AUTH_BEARER,
            models = listOf("anthropic/claude-sonnet-4.5", "google/gemini-2.5-pro", "meta-llama/llama-3.3-70b-instruct"),
            keyHint = "Kunci API dari openrouter.ai",
        ),
        ProviderPreset(
            id = "groq",
            label = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiFormat = FORMAT_OPENAI,
            authStyle = AUTH_BEARER,
            models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
            keyHint = "Kunci API dari console.groq.com",
        ),
        ProviderPreset(
            id = "together",
            label = "Together AI",
            baseUrl = "https://api.together.xyz/v1",
            apiFormat = FORMAT_OPENAI,
            authStyle = AUTH_BEARER,
            models = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo"),
            keyHint = "Kunci API dari api.together.ai",
        ),
        ProviderPreset(
            id = "ollama",
            label = "Ollama (lokal)",
            baseUrl = "http://10.0.2.2:11434/v1",
            apiFormat = FORMAT_OPENAI,
            authStyle = AUTH_BEARER,
            models = listOf("llama3.2", "qwen2.5-coder"),
            keyHint = "Tidak perlu kunci, jalankan Ollama di perangkat",
        ),
        ProviderPreset(
            id = "lmstudio",
            label = "LM Studio (lokal)",
            baseUrl = "http://10.0.2.2:1234/v1",
            apiFormat = FORMAT_OPENAI,
            authStyle = AUTH_BEARER,
            models = listOf("local-model"),
            keyHint = "Tidak perlu kunci, aktifkan server lokal LM Studio",
        ),
        ProviderPreset(
            id = "custom",
            label = "Kustom (URL sendiri)",
            baseUrl = "",
            apiFormat = FORMAT_ANTHROPIC,
            authStyle = AUTH_X_API_KEY,
            models = emptyList(),
            keyHint = "Isi Base URL sendiri",
        ),
    )

    fun byId(id: String): ProviderPreset? = presets.firstOrNull { it.id == id }

    fun labelOf(id: String): String = byId(id)?.label ?: "Kustom"
}
