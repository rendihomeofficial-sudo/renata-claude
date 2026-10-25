package com.renata.claude.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renata.claude.data.AiClient
import com.renata.claude.data.AppSettings
import com.renata.claude.data.Attachment
import com.renata.claude.data.Chat
import com.renata.claude.data.ChatStore
import com.renata.claude.data.Citation
import com.renata.claude.data.DEFAULT_LIVE_VOICE_PROMPT
import com.renata.claude.data.Message
import com.renata.claude.data.SettingsStore
import com.renata.claude.data.StreamDelta
import com.renata.claude.data.ToolCall
import com.renata.claude.data.ToolRegistry
import com.renata.claude.voice.LiveVoiceSink
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds every conversation, drives streaming and persists chats.
 * There is no account, no server side quota and no usage counter.
 */
class AppViewModel : ViewModel() {

    /** How many times the model may ask for a tool before the answer is cut. */
    private companion object {
        const val MAX_TOOL_TURNS = 5
    }

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var job: Job? = null

    /**
     * History of the current live voice session. It is kept apart from the
     * saved conversations: live voice is a side conversation with its own
     * prompt and it must not disturb the chat list.
     */
    private val _liveMessages = MutableStateFlow<List<Message>>(emptyList())
    val liveMessages: StateFlow<List<Message>> = _liveMessages.asStateFlow()

    private var liveJob: Job? = null

    /** The screen that is currently rendering the live voice session. */
    private var liveSink: LiveVoiceSink? = null

    init {
        reload()
    }

    fun reload() {
        _chats.value = ChatStore.list()
        val current = _currentChat.value ?: return
        if (!_isStreaming.value) {
            _chats.value.firstOrNull { it.id == current.id }?.let { _currentChat.value = it }
        }
    }

    fun newChat() {
        if (_isStreaming.value) return
        _currentChat.value = null
    }

    fun openChat(id: String) {
        if (_isStreaming.value) return
        _chats.value.firstOrNull { it.id == id }?.let { _currentChat.value = it }
    }

    fun deleteChat(id: String) {
        ChatStore.delete(id)
        if (_currentChat.value?.id == id) _currentChat.value = null
        reload()
    }

    fun renameChat(id: String, title: String) {
        val chat = _chats.value.firstOrNull { it.id == id } ?: return
        val updated = chat.copy(
            title = title.trim().ifBlank { "Percakapan tanpa judul" },
            updatedAt = System.currentTimeMillis()
        )
        ChatStore.save(updated)
        if (_currentChat.value?.id == id) _currentChat.value = updated
        reload()
    }

    fun togglePin(id: String) {
        val chat = _chats.value.firstOrNull { it.id == id } ?: return
        val updated = chat.copy(pinned = !chat.pinned, updatedAt = System.currentTimeMillis())
        ChatStore.save(updated)
        if (_currentChat.value?.id == id) _currentChat.value = updated
        reload()
    }

    fun clearAllChats() {
        ChatStore.deleteAll()
        _currentChat.value = null
        reload()
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** Clears the live voice history when the session ends. */
    fun resetLiveSession() {
        liveJob?.cancel()
        liveJob = null
        _liveMessages.value = emptyList()
    }

    /** Registers the live voice screen that receives the streamed answer. */
    fun attachLiveSink(sink: LiveVoiceSink?) {
        liveSink = sink
    }

    /**
     * Sends one utterance of the live voice session to the model and streams
     * the answer back to the attached sink so it can be spoken out loud.
     */
    fun sendLive(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val sink = liveSink ?: return
        val settings = SettingsStore.state.value
        if (!settings.apiKeySet) {
            sink.onFailed("Isi kunci API di Pengaturan terlebih dahulu.")
            return
        }
        val history = _liveMessages.value + Message(role = "user", text = trimmed)
        // The assistant turn is added empty and filled while the answer streams
        // in, so the transcript on screen always matches what was said.
        _liveMessages.value = history + Message(role = "assistant", text = "")
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            val reply = StringBuilder()
            val liveSettings = settings.copy(
                systemPrompt = settings.liveVoicePrompt.ifBlank { DEFAULT_LIVE_VOICE_PROMPT },
                thinking = false,
                maxTokens = minOf(settings.maxTokens, 1024),
            )
            val chat = Chat(title = "Suara langsung", messages = history)
            try {
                AiClient.streamChat(
                    chat = chat,
                    settings = liveSettings,
                    onDelta = { delta ->
                        if (delta is StreamDelta.Text) {
                            reply.append(delta.text)
                            sink.onDelta(reply.toString())
                            updateLiveReply(reply.toString())
                        }
                    },
                )
                val answer = reply.toString()
                updateLiveReply(answer)
                sink.onComplete(answer)
            } catch (e: Exception) {
                sink.onFailed(e.message ?: "Permintaan gagal.")
            }
        }
    }

    /** Replaces the text of the assistant turn that is currently streaming in. */
    private fun updateLiveReply(text: String) {
        val messages = _liveMessages.value
        if (messages.isEmpty()) return
        val last = messages.lastIndex
        if (messages[last].role != "assistant") return
        _liveMessages.value = messages.toMutableList().also {
            it[last] = it[last].copy(text = text)
        }
    }

    fun send(text: String, attachments: List<Attachment>) {
        if (_isStreaming.value) return
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        val settings = SettingsStore.state.value
        if (settings.apiKey.isBlank()) {
            _notice.value = "Isi kunci API di Pengaturan terlebih dahulu."
            return
        }
        val base = _currentChat.value
        val userMessage = Message(role = "user", text = trimmed, attachments = attachments)
        val updated = (base ?: Chat(title = titleFor(trimmed))).copy(
            title = base?.title ?: titleFor(trimmed),
            messages = (base?.messages ?: emptyList()) + userMessage,
            updatedAt = System.currentTimeMillis()
        )
        _currentChat.value = updated
        ChatStore.save(updated)
        reload()
        runAssistant(updated, settings)
    }

    /** Repeats the last user turn without duplicating it. */
    fun regenerate() {
        if (_isStreaming.value) return
        val chat = _currentChat.value ?: return
        val lastUser = chat.messages.indexOfLast { it.role == "user" }
        if (lastUser < 0) return
        val trimmed = chat.copy(messages = chat.messages.take(lastUser + 1), updatedAt = System.currentTimeMillis())
        _currentChat.value = trimmed
        ChatStore.save(trimmed)
        reload()
        runAssistant(trimmed, SettingsStore.state.value)
    }

    fun stop() {
        AiClient.cancelActive()
        job?.cancel()
        job = null
        _isStreaming.value = false
        _currentChat.value?.let { ChatStore.save(it) }
    }

    private fun runAssistant(chat: Chat, settings: AppSettings) {
        val tools = if (settings.toolsEnabled) ToolRegistry.specs() else emptyList()
        val withPlaceholder = chat.copy(
            messages = chat.messages + Message(role = "assistant", text = "", model = settings.model),
            updatedAt = System.currentTimeMillis()
        )
        _currentChat.value = withPlaceholder
        ChatStore.save(withPlaceholder)

        job = viewModelScope.launch {
            _isStreaming.value = true
            try {
                var working = withPlaceholder
                var turn = 0
                while (turn < MAX_TOOL_TURNS) {
                    turn += 1
                    val calls = LinkedHashMap<String, ToolCall>()
                    val sources = LinkedHashMap<String, Citation>()
                    AiClient.streamChat(
                        chat = working,
                        settings = settings,
                        onDelta = { delta ->
                            when (delta) {
                                is StreamDelta.Text ->
                                    updateLastAssistant { it.copy(text = it.text + delta.text) }
                                is StreamDelta.Thinking ->
                                    updateLastAssistant { it.copy(thinking = it.thinking + delta.text) }
                                is StreamDelta.Status ->
                                    updateLastAssistant { it.copy(stopReason = delta.stopReason) }
                                is StreamDelta.ToolCall ->
                                    calls[delta.id] = ToolCall(delta.id, delta.name, delta.arguments)
                                is StreamDelta.Source ->
                                    sources[delta.url] = Citation(delta.title, delta.url, delta.text)
                            }
                        },
                        tools = tools,
                        useServerSearch = settings.researchEnabled,
                    )
                    val toolCalls = calls.values.toList()
                    val citations = sources.values.toList()
                    updateLastAssistant { it.copy(toolCalls = toolCalls, citations = citations) }
                    if (toolCalls.isEmpty() || tools.isEmpty()) break

                    // The model asked for a tool: run it and send the result back.
                    val results = toolCalls.map { ToolRegistry.execute(it) }
                    val withResults = working.copy(
                        messages = working.messages +
                            Message(role = "user", text = "", toolResults = results) +
                            Message(role = "assistant", text = "", model = settings.model),
                        updatedAt = System.currentTimeMillis()
                    )
                    working = withResults
                    _currentChat.value = withResults
                    ChatStore.save(withResults)
                }
                updateLastAssistant { it.copy(failed = false) }
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                updateLastAssistant {
                    val body = if (it.text.isBlank()) message else "${it.text}\n\n⚠ $message"
                    it.copy(text = body, failed = true)
                }
                _notice.value = message
            } finally {
                _isStreaming.value = false
                _currentChat.value?.let { ChatStore.save(it) }
                reload()
            }
        }
    }

    private fun updateLastAssistant(transform: (Message) -> Message) {
        _currentChat.value = _currentChat.value?.let { chat ->
            val idx = chat.messages.indexOfLast { it.role == "assistant" }
            if (idx < 0) return@let chat
            val messages = chat.messages.toMutableList()
            messages[idx] = transform(messages[idx])
            chat.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun titleFor(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return if (clean.isEmpty()) "Percakapan baru" else clean.take(40)
    }
}
