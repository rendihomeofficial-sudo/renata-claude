package com.renata.claude.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Persists the user's custom configuration (API key, base URL, model, ...). */
object SettingsStore {

    private const val PREFS_NAME = "renata_claude_settings"
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())

    val state: StateFlow<AppSettings> = _state

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = read()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        write(next)
    }

    private fun read(): AppSettings {
        val d = prefs
        val preset = Providers.byId(d.getString("providerId", "anthropic") ?: "anthropic")
        return AppSettings(
            baseUrl = d.getString("baseUrl", "https://api.anthropic.com") ?: "https://api.anthropic.com",
            apiKey = d.getString("apiKey", "") ?: "",
            model = d.getString("model", "claude-3-5-sonnet-latest") ?: "claude-3-5-sonnet-latest",
            providerId = d.getString("providerId", "anthropic") ?: "anthropic",
            apiFormat = d.getString("apiFormat", preset?.apiFormat ?: Providers.FORMAT_ANTHROPIC)
                ?: Providers.FORMAT_ANTHROPIC,
            authStyle = d.getString("authStyle", preset?.authStyle ?: Providers.AUTH_X_API_KEY)
                ?: Providers.AUTH_X_API_KEY,
            systemPrompt = d.getString("systemPrompt", "") ?: "",
            maxTokens = d.getInt("maxTokens", 8192),
            temperature = d.getFloat("temperature", 1.0f),
            topP = d.getFloat("topP", 1.0f),
            themeMode = d.getString("themeMode", "system") ?: "system",
            extraHeaders = d.getString("extraHeaders", "") ?: "",
            thinking = d.getBoolean("thinking", false),
            thinkingBudget = d.getInt("thinkingBudget", 2048),
            reasoningEffort = d.getString("reasoningEffort", MiniMaxSupport.EFFORT_AUTO)
                ?: MiniMaxSupport.EFFORT_AUTO,
            sendOnEnter = d.getBoolean("sendOnEnter", false),
            speakReplies = d.getBoolean("speakReplies", true),
            voiceLanguage = d.getString("voiceLanguage", "") ?: "",
            liveVoicePrompt = d.getString("liveVoicePrompt", DEFAULT_LIVE_VOICE_PROMPT)
                ?: DEFAULT_LIVE_VOICE_PROMPT,
            updateManifestUrl = d.getString("updateManifestUrl", "") ?: "",
            toolsEnabled = d.getBoolean("toolsEnabled", true),
            researchEnabled = d.getBoolean("researchEnabled", false),
            lockEnabled = d.getBoolean("lockEnabled", false),
        )
    }

    private fun write(s: AppSettings) {
        val d = prefs
        d.edit()
            .putString("baseUrl", s.baseUrl)
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putString("providerId", s.providerId)
            .putString("apiFormat", s.apiFormat)
            .putString("authStyle", s.authStyle)
            .putString("systemPrompt", s.systemPrompt)
            .putInt("maxTokens", s.maxTokens)
            .putFloat("temperature", s.temperature)
            .putFloat("topP", s.topP)
            .putString("themeMode", s.themeMode)
            .putString("extraHeaders", s.extraHeaders)
            .putBoolean("thinking", s.thinking)
            .putInt("thinkingBudget", s.thinkingBudget)
            .putString("reasoningEffort", s.reasoningEffort)
            .putBoolean("sendOnEnter", s.sendOnEnter)
            .putBoolean("speakReplies", s.speakReplies)
            .putString("voiceLanguage", s.voiceLanguage)
            .putString("liveVoicePrompt", s.liveVoicePrompt)
            .putString("updateManifestUrl", s.updateManifestUrl)
            .putBoolean("toolsEnabled", s.toolsEnabled)
            .putBoolean("researchEnabled", s.researchEnabled)
            .putBoolean("lockEnabled", s.lockEnabled)
            .apply()
    }
}
