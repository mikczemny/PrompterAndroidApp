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
    /** progress in 0..1 while downloading a model, -1 for an indeterminate stage. */
    private val onModelProgress: (downloading: Boolean, fraction: Float) -> Unit = { _, _ -> },
) {
    private var speechService: SpeechService? = null
    private var lastPartial: String = ""

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
                val model = VoskModelManager.ensureModel(context, language) { fraction ->
                    onModelProgress(true, fraction)
                }
                onModelProgress(false, 1f)

                // User tapped Stop while the model was still downloading/loading.
                if (cancelRequested) {
                    onListeningChanged(false)
                    return@Thread
                }

                val recognizer = Recognizer(model, SAMPLE_RATE)
                val service = SpeechService(recognizer, SAMPLE_RATE)
                service.startListening(listener)
                speechService = service
                isListening = true
                onListeningChanged(true)
            } catch (t: Throwable) {
                onModelProgress(false, 0f)
                onError(t.message ?: "Could not start recognition")
                onListeningChanged(false)
            }
        }.start()
    }

    fun stop() {
        cancelRequested = true
        speechService?.let { service ->
            service.stop()
            service.shutdown()
        }
        speechService = null
        isListening = false
        lastPartial = ""
        onModelProgress(false, 0f)
        onListeningChanged(false)
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
