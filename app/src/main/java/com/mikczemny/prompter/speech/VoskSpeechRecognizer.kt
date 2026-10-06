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

    private val sessions = CaptureSessionGate()

    // Published and read under sessions, including while stopping. Native
    // recognizers stay local to their capture thread and never cross to the UI.
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    // Only the capture thread may touch Vosk's utterance or its partial cache.
    private var lastPartial: String = ""

    // Serializes WAV writes (capture thread) against finalize (stopRecording).
    private val recordingLock = Any()

    @Volatile
    private var wavRecorder: WavRecorder? = null

    val isListening: Boolean
        get() = sessions.isListening

    /** True from an accepted start through preparation, capture and final cleanup. */
    val isActive: Boolean
        get() = sessions.isActive

    /** True while the microphone stream is also being written to a file. */
    val isRecording: Boolean
        get() = wavRecorder != null

    /**
     * Starts listening in [language]. Downloads the model on a background thread
     * on first use (reporting progress); subsequent starts are instant/offline.
     */
    fun start(language: Language) {
        synchronized(sessions) {
            val session = sessions.begin() ?: return
            try {
                val thread = Thread({ captureSession(session, language) }, "prompter-capture")
                captureThread = thread
                thread.start()
            } catch (t: Throwable) {
                // Thread creation can fail before captureSession owns cleanup.
                captureThread = null
                sessions.finish(session)
                onError(t.message ?: "Could not start recognition")
                onModelStatus(null)
                onListeningChanged(false)
            }
        }
    }

    // Callers gate on RECORD_AUDIO at runtime (mic-permission flow in the UI)
    // before ever reaching start(); lint can't see across that hop.
    @SuppressLint("MissingPermission")
    private fun captureSession(session: CaptureSessionGate.Session, language: Language) {
        var rec: Recognizer? = null
        var record: AudioRecord? = null
        try {
            if (!sessions.mayContinue(session)) return
            val model = VoskModelManager.ensureModel(context, language) { status ->
                synchronized(sessions) {
                    if (sessions.mayContinue(session)) onModelStatus(status)
                }
            }
            if (!sessions.mayContinue(session)) return

            val nativeRecognizer = Recognizer(model, PcmResampler.TARGET_RATE.toFloat())
            rec = nativeRecognizer
            val microphone = openAudioRecord()
            record = microphone
            val rate = microphone.sampleRate

            // Check-publish-start atomically under the same lock stop() uses, so
            // a Stop tap either sees the session fully live and ends it, or lands
            // first and this thread aborts before the mic ever opens.
            val started = synchronized(sessions) {
                val live = sessions.startListening(session) {
                    audioRecord = microphone
                    microphone.startRecording()
                }
                if (live) {
                    // Bind focus loss to this token; a queued callback from an
                    // abandoned request must not interrupt a later start.
                    audioFocus.request { handleInterruption(session) }
                    onModelStatus(null)
                    if (sessions.isListening(session)) onListeningChanged(true)
                }
                live
            }
            if (!started) return

            captureLoop(session, nativeRecognizer, microphone, rate)
        } catch (t: Throwable) {
            synchronized(sessions) {
                // An interrupted preparation/read is an expected result of
                // Stop, not a new failure to show after the user has cancelled.
                if (sessions.mayContinue(session)) {
                    onError(t.message ?: "Could not start recognition")
                }
            }
        } finally {
            // A capture failure also closes admission to startRecording before
            // cleanup drops its WAV lock; no delayed UI callback may open a new
            // writer against a microphone that is being released.
            sessions.requestStop(session)
            // The loop's owner releases everything, exactly once.
            runCatching { audioFocus.abandon() }
            synchronized(recordingLock) {
                wavRecorder?.runCatching { close() }
                wavRecorder = null
            }
            record?.let { captured ->
                runCatching {
                    if (captured.recordingState == AudioRecord.RECORDSTATE_RECORDING) captured.stop()
                }
                // A failed stop must not skip releasing the native recorder.
                runCatching { captured.release() }
            }
            rec?.runCatching { close() }
            lastPartial = ""
            synchronized(sessions) {
                if (captureThread === Thread.currentThread()) {
                    audioRecord = null
                    captureThread = null
                }
                sessions.finish(session)
                // Publish idle only after cleanup. Keep it ordered with begin:
                // a later session must never receive this session's idle event.
                onModelStatus(null)
                onListeningChanged(false)
            }
        }
    }

    private fun captureLoop(
        session: CaptureSessionGate.Session,
        rec: Recognizer,
        record: AudioRecord,
        rate: Int,
    ) {
        // ~100 ms blocks: small enough that stop() is felt promptly, large
        // enough to keep per-read overhead negligible.
        val block = ShortArray(rate / 10)
        while (sessions.mayContinue(session)) {
            val n = record.read(block, 0, block.size)
            if (n <= 0) continue
            if (!sessions.mayContinue(session)) break

            // Tee the raw, full-rate capture to the file first.
            synchronized(recordingLock) { wavRecorder?.write(block, n) }

            if (sessions.consumeReset(session)) {
                rec.reset()
                lastPartial = ""
                // This block may straddle a remote jump. Keep it in the WAV,
                // but do not let speech from before the jump move the pointer.
                continue
            }

            // Then feed a 16 kHz copy to recognition.
            val forVosk = PcmResampler.toVoskRate(block, n, rate)
            if (rec.acceptWaveForm(forVosk, forVosk.size)) {
                emit(session, rec.result, isFinal = true)
            } else {
                emit(session, rec.partialResult, isFinal = false)
            }
        }
        // Stop ends tracking immediately; flushing the old utterance here
        // could move the pointer after a Stop or a remote navigation action.
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
        synchronized(sessions) {
            val record = audioRecord ?: return
            if (!sessions.isListening) return
            synchronized(recordingLock) {
                if (wavRecorder != null) return
                wavRecorder = WavRecorder(file, record.sampleRate)
            }
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
        synchronized(sessions) {
            if (sessions.requestStop() == null) return
            stopRecording()
            // read() normally returns within a ~100 ms block. Interrupt preparation
            // when possible; a non-interruptible model load still cannot start
            // the microphone after this session's stop has been accepted.
            captureThread?.interrupt()
        }
    }

    /**
     * Discards the current utterance after a manual jump without stopping the
     * microphone or WAV capture. Native reset happens only on the capture
     * thread at the next audio block; in-flight results are suppressed now.
     */
    fun resetTranscript() {
        sessions.requestReset()
    }

    /**
     * Handles the audio path being taken over mid-session: stops recognition
     * (releasing the mic) and reports the interruption, but only once per live
     * session, since focus loss can fire more than once.
     */
    private fun handleInterruption(session: CaptureSessionGate.Session) {
        val interrupted = synchronized(sessions) {
            if (!sessions.isListening(session) || !sessions.requestStop(session)) {
                false
            } else {
                stopRecording()
                captureThread?.interrupt()
                true
            }
        }
        if (interrupted) onInterrupted()
    }

    private fun emit(session: CaptureSessionGate.Session, json: String, isFinal: Boolean) {
        synchronized(sessions) {
            if (!sessions.mayEmit(session)) return
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
    }

    private fun extractField(json: String, field: String): String {
        return try {
            JSONObject(json).optString(field, "")
        } catch (e: Exception) {
            ""
        }
    }
}
