package com.renata.claude.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** What the live voice session is doing right now. */
sealed interface LiveVoiceState {
    data object Idle : LiveVoiceState

    /** Engine is warming up. */
    data object Preparing : LiveVoiceState

    /** Microphone is open, [partial] holds the running transcript. */
    data class Listening(val partial: String = "") : LiveVoiceState

    /** The transcript was sent, waiting for the model. */
    data object Thinking : LiveVoiceState

    /** The model is answering, [text] is what has arrived so far. */
    data class Speaking(val text: String = "") : LiveVoiceState

    /** Something went wrong, [message] is safe to show. */
    data class Error(val message: String) : LiveVoiceState
}

/**
 * Drives the hands-free conversation loop:
 *
 * listen -> send transcript -> wait for the reply -> speak it -> listen again.
 *
 * The model side is text only: the reply is streamed in through [LiveVoiceSink]
 * and read out with the on-device speech engine. That keeps the app compatible
 * with any Anthropic compatible endpoint, but it also means the conversation is
 * half duplex: the microphone is closed while the model answers.
 */
class LiveVoiceController(
    context: Context,
    private val scope: CoroutineScope,
    private val locale: Locale?,
    initialSpeakReplies: Boolean,
    private val onSend: (String) -> Unit,
) : LiveVoiceSink {

    /** Whether replies are read out loud. Can be toggled while talking. */
    var speakReplies: Boolean = initialSpeakReplies

    private val _state = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
    val state: StateFlow<LiveVoiceState> = _state.asStateFlow()

    /** The last thing the user said, kept for the UI. */
    var lastUserText: String = ""
        private set

    private val recognizer = VoiceRecognizer(context)
    private val speaker = Speaker(context)
    private val observer: Job

    private var cursor = SpeechCursor()
    private var replyComplete = false
    private var running = false

    init {
        speaker.onFailure = { message ->
            if (running) {
                _state.value = LiveVoiceState.Error(message)
                scope.launch(Dispatchers.Main.immediate) {
                    delay(2500)
                    if (running) resumeListening()
                }
            }
        }
        observer = scope.launch(Dispatchers.Main.immediate) {
            recognizer.state.collect { recognizerState ->
                when (recognizerState) {
                    is VoiceState.Listening ->
                        _state.value = LiveVoiceState.Listening(recognizerState.partial)

                    is VoiceState.Finished -> {
                        recognizer.destroySession()
                        handleTranscript(recognizerState.transcript)
                    }

                    is VoiceState.Error -> {
                        recognizer.destroySession()
                        handleRecognizerError(recognizerState.message)
                    }

                    VoiceState.Idle -> Unit
                }
            }
        }
    }

    /** Starts a live voice session. */
    fun start() {
        if (running) return
        running = true
        cursor = SpeechCursor()
        replyComplete = false
        lastUserText = ""
        _state.value = LiveVoiceState.Preparing
        speaker.init(locale)
        beginListening(delayMillis = 0)
    }

    /** Ends the session and releases the microphone and the speech engine. */
    fun stop() {
        running = false
        recognizer.destroySession()
        speaker.stop()
        _state.value = LiveVoiceState.Idle
    }

    /** Releases every resource. Call when leaving the screen. */
    fun release() {
        running = false
        observer.cancel()
        recognizer.release()
        speaker.release()
        _state.value = LiveVoiceState.Idle
    }

    /** Called for every text chunk the model streams out. */
    override fun onDelta(text: String) {
        if (!running) return
        _state.value = LiveVoiceState.Speaking(text)
        if (!speakReplies) return
        val fresh = cursor.take(text, flush = false)
        fresh.forEach { speaker.speak(it) }
    }

    /** Called once the model finished its reply. */
    override fun onComplete(text: String) {
        if (!running) return
        _state.value = LiveVoiceState.Speaking(text)
        replyComplete = true
        if (!speakReplies) {
            resumeListening()
            return
        }
        val remaining = cursor.take(text, flush = true)
        remaining.forEach { speaker.speak(it) }
        // Only reopen the microphone once the reply was read out loud, so the
        // app does not record its own voice.
        scope.launch(Dispatchers.Main.immediate) {
            speaker.awaitIdle()
            if (running && replyComplete) resumeListening()
        }
    }

    /** Called when the request failed, so the session can end cleanly. */
    override fun onFailed(message: String) {
        if (!running) return
        _state.value = LiveVoiceState.Error(message)
        replyComplete = true
        recognizer.destroySession()
        // The hands-free loop keeps going: show the problem, then listen again
        // so the user can retry without leaving the screen.
        scope.launch(Dispatchers.Main.immediate) {
            delay(2500)
            if (running) resumeListening()
        }
    }

    private fun handleTranscript(transcript: String) {
        if (!running) return
        recognizer.destroySession()
        lastUserText = transcript
        _state.value = LiveVoiceState.Thinking
        onSend(transcript)
    }

    private fun handleRecognizerError(message: String) {
        if (!running) return
        if (message.contains("catch that") || message.contains("No speech detected")) {
            // Nothing was said; just keep the microphone open.
            beginListening(delayMillis = 250)
            return
        }
        _state.value = LiveVoiceState.Error(message)
        recognizer.destroySession()
    }

    private fun resumeListening() {
        if (!running) return
        beginListening(delayMillis = 350)
    }

    private fun beginListening(delayMillis: Long) {
        scope.launch(Dispatchers.Main.immediate) {
            if (!running) return@launch
            if (delayMillis > 0) delay(delayMillis)
            if (!running) return@launch
            cursor = SpeechCursor()
            replyComplete = false
            _state.value = LiveVoiceState.Listening()
            recognizer.start(locale)
        }
    }
}
