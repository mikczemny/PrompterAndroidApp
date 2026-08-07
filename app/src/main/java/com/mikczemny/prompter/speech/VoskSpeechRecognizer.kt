package com.mikczemny.prompter.speech

import android.content.Context
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * On-device continuous speech recognition backed by Vosk. Fully offline once
 * the selected language's model has been downloaded: no API keys, no account,
 * no network at recognition time. Mirrors the JS useSpeechRecognition hook's
 * contract — it emits (text, isFinal, timestampMs) for partial and final
 * results so the matcher can react as fast as possible.
 *
 * Requires RECORD_AUDIO granted before start(), and INTERNET the first time a
 * given language is used (to fetch its model).
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val onResult: (text: String, isFinal: Boolean, timestampMs: Long) -> Unit,
    private val onError: (message: String) -> Unit = {},
    private val onListeningChanged: (listening: Boolean) -> Unit = {},
    /** Non-null while a model is being fetched/prepared; null when idle/ready. */
    private val onModelStatus: (status: ModelStatus?) -> Unit = {},
    /**
     * Fired when recognition is torn down because the system audio path was
     * taken over (call, assistant, another recorder) rather than by a user Stop.
     * Listening has already ended by the time this runs.
     */
    private val onInterrupted: () -> Unit = {},
) {
    private val audioFocus = AudioFocusManager(context)

    @Volatile
    private var speechService: SpeechService? = null

    @Volatile
    private var recognizer: Recognizer? = null

    private var lastPartial: String = ""

    // Guards the start/stop critical section: start() checks cancelRequested
    // and publishes speechService/recognizer as one atomic step, and stop()
    // tears them down as another, so a Stop tap can never land in the gap
    // between "decided to start" and "assigned the service" and leave the mic
    // open with the UI showing idle.
    private val lifecycleLock = Any()

    @Volatile
    private var cancelRequested: Boolean = false

    @Volatile
    var isListening: Boolean = false
        private set

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val partial = hypothesis?.let { extractField(it, "partial") }.orEmpty()
            if (partial.isNotBlank() && partial != lastPartial) {
                lastPartial = partial
                onResult(partial, false, System.currentTimeMillis())
            }
        }

        override fun onResult(hypothesis: String?) {
            val text = hypothesis?.let { extractField(it, "text") }.orEmpty()
            lastPartial = ""
            if (text.isNotBlank()) {
                onResult(text, true, System.currentTimeMillis())
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = hypothesis?.let { extractField(it, "text") }.orEmpty()
            lastPartial = ""
            if (text.isNotBlank()) {
                onResult(text, true, System.currentTimeMillis())
            }
        }

        override fun onError(exception: Exception?) {
            onError(exception?.message ?: "Speech recognition error")
        }

        override fun onTimeout() {
            // Vosk fired its inactivity timeout; recognition keeps running.
        }
    }

    /**
     * Starts listening in [language]. Downloads the model on a background thread
     * on first use (reporting progress); subsequent starts are instant/offline.
     */
    fun start(language: Language) {
        if (isListening) return
        cancelRequested = false
        Thread {
            try {
                val model = VoskModelManager.ensureModel(context, language) { status ->
                    onModelStatus(status)
                }
                onModelStatus(null)

                val rec = Recognizer(model, SAMPLE_RATE)
                val service = SpeechService(rec, SAMPLE_RATE)

                // Check-publish-start all happen inside the same lock stop()
                // uses, so a Stop tap can never land between "decided to
                // start" and "mic actually opened" — either it sees the
                // service fully live and stops it, or it lands first and this
                // thread aborts before startListening() ever runs.
                val startedListening = synchronized(lifecycleLock) {
                    if (cancelRequested) {
                        false
                    } else {
                        recognizer = rec
                        speechService = service
                        service.startListening(listener)
                        isListening = true
                        true
                    }
                }

                if (!startedListening) {
                    service.shutdown()
                    rec.close()
                    onListeningChanged(false)
                    return@Thread
                }

                // Hold focus for its loss callback: if anything else grabs the
                // audio path, tear down so the mic is released and the UI stops
                // claiming it is tracking.
                audioFocus.request { handleInterruption() }

                onListeningChanged(true)
            } catch (t: Throwable) {
                onModelStatus(null)
                onError(t.message ?: "Could not start recognition")
                onListeningChanged(false)
            }
        }.start()
    }

    fun stop() {
        cancelRequested = true
        audioFocus.abandon()
        synchronized(lifecycleLock) {
            speechService?.let { service ->
                service.stop()
                service.shutdown()
            }
            recognizer?.close()
            speechService = null
            recognizer = null
            isListening = false
        }
        lastPartial = ""
        onModelStatus(null)
        onListeningChanged(false)
    }

    /**
     * Handles the audio path being taken over mid-session: stops recognition
     * (releasing the mic) and reports the interruption, but only once per live
     * session, since focus loss can fire more than once.
     */
    private fun handleInterruption() {
        if (!isListening) return
        stop()
        onInterrupted()
    }

    private fun extractField(json: String, field: String): String {
        return try {
            JSONObject(json).optString(field, "")
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000.0f
    }
}
