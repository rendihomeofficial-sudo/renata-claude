package com.renata.claude.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Snapshot of what the speech recognizer is doing right now. */
sealed interface VoiceState {
    data object Idle : VoiceState

    /** Microphone is open, [partial] holds the in-progress transcript. */
    data class Listening(val partial: String = "") : VoiceState

    /** Recognition finished with a final transcript. */
    data class Finished(val transcript: String) : VoiceState

    /** Recognition failed; [message] is safe to show to the user. */
    data class Error(val message: String) : VoiceState
}

/**
 * Thin wrapper around [SpeechRecognizer].
 *
 * Must be created and used from the main thread. All state changes are
 * published through [state] so Compose can observe them.
 *
 * The recogniser is designed to be used in a loop: [start] tears down any
 * previous session first, so the microphone can be opened again and again
 * without leaking recogniser instances. Callbacks coming from a torn down
 * session are ignored, which keeps a late result from a previous turn from
 * hijacking the current one.
 */
class VoiceRecognizer(private val context: Context) {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    /** True when the device exposes a speech recognition service. */
    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Opens the microphone. Safe to call when already listening.
     *
     * @param locale the language to recognise, or null for the device default.
     */
    fun start(locale: Locale? = null) {
        if (!isAvailable()) {
            _state.value = VoiceState.Error("No speech recognition service on this device.")
            return
        }
        if (_state.value is VoiceState.Listening) return

        // Always start from a clean session: a leftover recogniser would keep
        // the microphone busy and reject the new request.
        destroySession()

        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            _state.value = VoiceState.Error("Could not start the microphone: ${e.message}")
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (locale != null) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            }
            // Stop after a short silence so the loop can continue on its own.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        _state.value = VoiceState.Listening()
        try {
            created.startListening(intent)
        } catch (e: Exception) {
            _state.value = VoiceState.Error("Could not start the microphone: ${e.message}")
        }
    }

    /** Stops listening; a final result may still arrive afterwards. */
    fun stop() {
        val current = recognizer
        if (current != null) {
            try {
                current.stopListening()
            } catch (_: Exception) {
                // Ignore: the service may already be gone.
            }
        }
    }

    /**
     * Tears the current session down completely: the microphone is released
     * and any late callback from this session is ignored.
     */
    fun destroySession() {
        val current = recognizer
        recognizer = null
        if (current != null) {
            try {
                current.cancel()
            } catch (_: Exception) {
                // Ignore.
            }
            try {
                current.destroy()
            } catch (_: Exception) {
                // Ignore.
            }
        }
        if (_state.value is VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    /** Releases the underlying recognizer. Call when leaving the screen. */
    fun release() {
        destroySession()
        _state.value = VoiceState.Idle
    }

    /** Resets to [VoiceState.Idle] after a result has been handled. */
    fun reset() {
        if (_state.value !is VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            _state.value = VoiceState.Error(messageFor(error))
        }

        override fun onResults(results: Bundle?) {
            val candidates = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = VoiceInput.pickBest(candidates)
            _state.value = if (transcript.isNullOrBlank()) {
                VoiceState.Error("I could not catch that. Please try again.")
            } else {
                VoiceState.Finished(transcript)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val candidates = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = VoiceInput.pickBest(candidates).orEmpty()
            _state.value = VoiceState.Listening(partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is required for voice input."
        SpeechRecognizer.ERROR_NETWORK -> "Network error while recognising speech."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "I could not catch that. Please try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recogniser is busy, try again."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Voice input failed."
    }
}
