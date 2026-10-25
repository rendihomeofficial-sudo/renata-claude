package com.renata.claude.voice

/**
 * Receives the model's answer while a live voice session is running.
 *
 * Implemented by [LiveVoiceController] and registered by the view model so the
 * streamed text can be spoken out loud as it arrives.
 */
interface LiveVoiceSink {
    /** Called for every chunk; [text] is the whole answer so far. */
    fun onDelta(text: String)

    /** Called once the answer is complete; [text] is the full answer. */
    fun onComplete(text: String)

    /** Called when the request failed; [message] is safe to show. */
    fun onFailed(message: String)
}
