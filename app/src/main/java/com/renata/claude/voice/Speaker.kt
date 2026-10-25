package com.renata.claude.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Thin wrapper around [TextToSpeech] with a small queue.
 *
 * The engine initialises asynchronously, so text handed over before the engine
 * is ready is buffered and flushed on init. [onDrained] fires once the queue
 * runs empty, which is the signal the live voice loop uses to start listening
 * again.
 */
class Speaker(context: Context) {

    // Kept as a property: the constructor argument is not visible inside
    // member functions such as init().
    private val appContext: Context = context.applicationContext

    private var tts: TextToSpeech? = null
    private var ready = false
    private var broken = false
    private var inFlight = 0
    private var counter = 0
    private val pending = ArrayDeque<String>()

    /** Called when every queued utterance finished speaking. */
    var onDrained: (() -> Unit)? = null

    /** Called when the engine is unusable; [message] is safe to show. */
    var onFailure: ((String) -> Unit)? = null

    /** True once the engine finished initialising. */
    val isReady: Boolean get() = ready

    /** True when nothing is being spoken and nothing is queued. */
    val isIdle: Boolean get() = inFlight <= 0 && pending.isEmpty()

    /** Initialises the engine. Safe to call once per instance. */
    fun init(locale: Locale?) {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                broken = true
                onFailure?.invoke("Text-to-speech engine is not available on this device.")
                return@TextToSpeech
            }
            // The listener can only be attached once the engine exists, so it
            // has to happen inside this callback.
            attachProgressListener()
            applyLocale(locale)
            ready = true
            flushPending()
        }
    }

    private fun applyLocale(locale: Locale?) {
        val engine = tts ?: return
        if (locale != null) {
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // The requested voice is not installed; the default voice is
                // still usable, so this is not fatal.
                engine.setLanguage(Locale.getDefault())
            }
        } else {
            engine.setLanguage(Locale.getDefault())
        }
    }

    /** Queues [text] for speaking. Empty text is ignored. */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (broken) return
        if (!ready) {
            pending.addLast(clean)
            return
        }
        enqueue(clean)
    }

    private fun flushPending() {
        while (pending.isNotEmpty()) {
            enqueue(pending.removeFirst())
        }
    }

    private fun enqueue(text: String) {
        val engine = tts ?: return
        inFlight++
        val id = "live-${counter++}"
        try {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        } catch (e: Exception) {
            inFlight--
            onFailure?.invoke("Could not speak the reply: ${e.message}")
        }
    }

    /**
     * Waits until every queued utterance has been spoken.
     *
     * Bounded so a speech engine that never reports completion cannot block
     * the conversation loop forever.
     */
    suspend fun awaitIdle(timeoutMillis: Long = 60_000) {
        var waited = 0L
        while (!isIdle && waited < timeoutMillis) {
            delay(60)
            waited += 60
        }
    }

    /** Stops speaking and drops everything still queued. */
    fun stop() {
        val engine = tts ?: return
        inFlight = 0
        pending.clear()
        try {
            engine.stop()
        } catch (e: Exception) {
            // Nothing useful to do here; the engine is going away anyway.
        }
    }

    /** Releases the engine. */
    fun release() {
        pending.clear()
        inFlight = 0
        val engine = tts
        tts = null
        ready = false
        try {
            engine?.stop()
            engine?.shutdown()
        } catch (e: Exception) {
            // Ignore shutdown failures.
        }
    }

    private fun attachProgressListener() {
        val engine = tts ?: return
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                finishOne()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishOne()
            }
        })
    }

    private fun finishOne() {
        if (inFlight > 0) inFlight--
        if (isIdle) onDrained?.invoke()
    }
}
