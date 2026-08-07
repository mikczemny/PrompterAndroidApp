package com.mikczemny.prompter.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import org.vosk.Recognizer
import java.io.File

/**
 * On-device continuous speech recognition backed by Vosk. Fully offline once
 * the selected language's model has been downloaded: no API keys, no account,
 * no network at recognition time. Emits (text, isFinal, timestampMs) for
 * partial and final results so the matcher can react as fast as possible.
 *
 * Owns its own [AudioRecord] rather than delegating to Vosk's SpeechService,
 * because the app captures the microphone exactly once and *tees* it: the same
 * PCM stream feeds recognition and, while recording, a WAV file. That is what
 * lets voice tracking and audio capture run together without two clients
 * fighting over the mic — see [startRecording]. The mic is captured at a
 * high-quality rate and downsampled to 16 kHz for Vosk (see [PcmResampler]),
 * so the recorded file keeps full fidelity.
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
    private var recognizer: Recognizer? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var captureThread: Thread? = null

    // Signals the capture loop to finish; the loop's finally block owns all
    // resource release, so stop() and interruptions never double-close.
    @Volatile
    private var running: Boolean = false

    private var lastPartial: String = ""

    // Guards the start/stop critical section so a Stop tap can never land in the
    // gap between "decided to start" and "mic actually capturing" and leave the
    // mic open with the UI showing idle. (Carried over from the P0-1 fix.)
    private val lifecycleLock = Any()

    // Serializes WAV writes (capture thread) against finalize (stopRecording).
    private val recordingLock = Any()

    @Volatile
    private var wavRecorder: WavRecorder? = null

    @Volatile
    private var cancelRequested: Boolean = false

    @Volatile
    var isListening: Boolean = false
        private set

    /** True while the microphone stream is also being written to a file. */
    val isRecording: Boolean
        get() = wavRecorder != null

    /**
     * Starts listening in [language]. Downloads the model on a background thread
     * on first use (reporting progress); subsequent starts are instant/offline.
     */
    fun start(language: Language) {
        if (isListening) return
        cancelRequested = false
        val thread = Thread { captureSession(language) }
        thread.start()
    }

    // Callers gate on RECORD_AUDIO at runtime (mic-permission flow in the UI)
    // before ever reaching start(); lint can't see across that hop.
    @SuppressLint("MissingPermission")
    private fun captureSession(language: Language) {
        var rec: Recognizer? = null
        var record: AudioRecord? = null
        try {
            val model = VoskModelManager.ensureModel(context, language) { status ->
                onModelStatus(status)
            }
            onModelStatus(null)

            rec = Recognizer(model, PcmResampler.TARGET_RATE.toFloat())
            record = openAudioRecord()
            val rate = record.sampleRate

            // Check-publish-start atomically under the same lock stop() uses, so
            // a Stop tap either sees the session fully live and ends it, or lands
            // first and this thread aborts before the mic ever opens.
            val started = synchronized(lifecycleLock) {
                if (cancelRequested) {
                    false
                } else {
                    recognizer = rec
                    audioRecord = record
                    captureThread = Thread.currentThread()
                    record.startRecording()
                    running = true
                    isListening = true
                    true
                }
            }
            if (!started) {
                record.release()
                rec.close()
                onListeningChanged(false)
                return
            }

            // Hold audio focus for its loss callback: if anything else grabs the
            // audio path, tear down so the mic is released and the UI stops
            // claiming it is tracking.
            audioFocus.request { handleInterruption() }
            onListeningChanged(true)

            captureLoop(rec, record, rate)
        } catch (t: Throwable) {
            onModelStatus(null)
            onError(t.message ?: "Could not start recognition")
        } finally {
            // The loop's owner releases everything, exactly once.
            audioFocus.abandon()
            synchronized(recordingLock) {
                wavRecorder?.runCatching { close() }
                wavRecorder = null
            }
            record?.runCatching {
                if (state == AudioRecord.STATE_INITIALIZED) stop()
                release()
            }
            rec?.runCatching { close() }
            synchronized(lifecycleLock) {
                if (captureThread === Thread.currentThread()) {
                    audioRecord = null
                    recognizer = null
                    captureThread = null
                    running = false
                    isListening = false
                }
            }
            lastPartial = ""
            onModelStatus(null)
            onListeningChanged(false)
        }
    }

    private fun captureLoop(rec: Recognizer, record: AudioRecord, rate: Int) {
        // ~100 ms blocks: small enough that stop() is felt promptly, large
        // enough to keep per-read overhead negligible.
        val block = ShortArray(rate / 10)
        while (running) {
            val n = record.read(block, 0, block.size)
            if (n <= 0) continue

            // Tee the raw, full-rate capture to the file first.
            synchronized(recordingLock) { wavRecorder?.write(block, n) }

            // Then feed a 16 kHz copy to recognition.
            val forVosk = PcmResampler.toVoskRate(block, n, rate)
            if (rec.acceptWaveForm(forVosk, forVosk.size)) {
                emit(rec.result, isFinal = true)
            } else {
                emit(rec.partialResult, isFinal = false)
            }
        }
        // Flush whatever the recognizer was still holding when listening ended.
        emit(rec.finalResult, isFinal = true)
    }

    /**
     * Picks the highest-quality capture rate the device supports from a short
     * preference list. 48 kHz downsamples to 16 kHz by a clean 3:1 ratio and is
     * the native rate on most phones; 16 kHz is the guaranteed floor and needs
     * no resampling at all.
     */
    @SuppressLint("MissingPermission")
    private fun openAudioRecord(): AudioRecord {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        for (rate in intArrayOf(48000, 44100, PcmResampler.TARGET_RATE)) {
            val minBuf = AudioRecord.getMinBufferSize(rate, channel, encoding)
            if (minBuf <= 0) continue
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                channel,
                encoding,
                // Room for several read blocks so a scheduling hiccup can't drop audio.
                maxOf(minBuf, rate / 10 * 2 * 4),
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        throw IllegalStateException("Could not open the microphone")
    }

    /**
     * Begins writing the live microphone stream to [file] as WAV, in addition to
     * feeding recognition. No-op unless already listening, since recording is a
     * tap on the same stream. Safe to call once per recording; call
     * [stopRecording] to finalize the file.
     */
    fun startRecording(file: File) {
        val record = audioRecord ?: return
        if (!isListening) return
        synchronized(recordingLock) {
            if (wavRecorder != null) return
            wavRecorder = WavRecorder(file, record.sampleRate)
        }
    }

    /** Finalizes the current recording's file, if any. Safe to call when idle. */
    fun stopRecording() {
        synchronized(recordingLock) {
            wavRecorder?.runCatching { close() }
            wavRecorder = null
        }
    }

    fun stop() {
        cancelRequested = true
        running = false
        stopRecording()
        // read() fills one ~100 ms block then returns, so the loop sees
        // running == false and exits within a block; the interrupt is just a
        // nudge for any interruptible wait on the way out.
        captureThread?.interrupt()
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

    private fun emit(json: String, isFinal: Boolean) {
        if (isFinal) {
            val text = extractField(json, "text")
            lastPartial = ""
            if (text.isNotBlank()) onResult(text, true, System.currentTimeMillis())
        } else {
            val partial = extractField(json, "partial")
            if (partial.isNotBlank() && partial != lastPartial) {
                lastPartial = partial
                onResult(partial, false, System.currentTimeMillis())
            }
        }
    }

    private fun extractField(json: String, field: String): String {
        return try {
            JSONObject(json).optString(field, "")
        } catch (e: Exception) {
            ""
        }
    }
}
