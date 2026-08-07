package com.mikczemny.prompter.speech

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/**
 * Thin wrapper around the platform audio-focus request, used purely as an
 * interruption signal rather than to duck or mix. A teleprompter never plays
 * audio, but holding focus is how the system tells us when something else takes
 * over the audio path — an incoming call, a voice assistant, another recorder —
 * at which point Vosk's microphone stream would otherwise go silent with no
 * error, leaving the UI insisting it is still tracking.
 *
 * Kept off the pure [ScriptMatcher] path on purpose: this is Android-only glue.
 */
internal class AudioFocusManager(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Focus callbacks are delivered on the main thread so the interruption
    // handler can tear down and touch listeners without hopping threads itself.
    private val mainHandler = Handler(Looper.getMainLooper())

    private var request: AudioFocusRequestCompat? = null

    /**
     * Requests audio focus, invoking [onLoss] once the next time focus is lost
     * for any reason. Returns whether focus was granted; recognition is allowed
     * to proceed either way, since the request exists for its loss callback, not
     * to gate the microphone.
     */
    fun request(onLoss: () -> Unit): Boolean {
        abandon()

        val attributes = AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
            .build()

        val req = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(
                { change ->
                    // Any loss — transient or permanent — means another audio
                    // client is now in front of us; for a recorder there is
                    // nothing sensible to duck, so every loss is an interruption.
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLoss()
                    }
                },
                mainHandler,
            )
            .build()

        request = req
        return AudioManagerCompat.requestAudioFocus(audioManager, req) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Releases focus if held. Safe to call when nothing is held. */
    fun abandon() {
        request?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
        request = null
    }
}
