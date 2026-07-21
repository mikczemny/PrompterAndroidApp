package com.mikczemny.prompter.speech

import android.content.Context
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * On-device continuous speech recognition backed by Vosk. Fully offline: no
 * network, no API keys. Mirrors the JS useSpeechRecognition hook's contract —
 * it emits (text, isFinal, timestampMs) for both partial and final results so
 * the matcher can react as fast as possible.
 *
 * Requires the RECORD_AUDIO permission to already be granted before start().
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val onResult: (text: String, isFinal: Boolean, timestampMs: Long) -> Unit,
    private val onError: (message: String) -> Unit = {},
    private val onListeningChanged: (listening: Boolean) -> Unit = {},
) {
    private var speechService: SpeechService? = null
    private var lastPartial: String = ""

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
            onError(exception?.message ?: "Nieznany błąd rozpoznawania mowy")
        }

        override fun onTimeout() {
            // Vosk fired its inactivity timeout; recognition keeps running.
        }
    }

    /** Starts listening. Loads the model on a background thread if needed. */
    fun start() {
        if (isListening) return
        Thread {
            try {
                val model = VoskModelManager.loadModel(context)
                val recognizer = Recognizer(model, SAMPLE_RATE)
                val service = SpeechService(recognizer, SAMPLE_RATE)
                service.startListening(listener)
                speechService = service
                isListening = true
                onListeningChanged(true)
            } catch (t: Throwable) {
                onError(t.message ?: "Nie udało się uruchomić rozpoznawania")
                onListeningChanged(false)
            }
        }.start()
    }

    fun stop() {
        speechService?.let { service ->
            service.stop()
            service.shutdown()
        }
        speechService = null
        isListening = false
        lastPartial = ""
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
