package com.mikczemny.prompter.speech

/**
 * Resamples captured PCM down to the 16 kHz Vosk expects.
 *
 * The microphone is captured once, at the device's high-quality rate, so the
 * WAV written to disk keeps that fidelity. Vosk models only run at 16 kHz,
 * though, so the copy fed to the recognizer is downsampled here. Mono, 16-bit
 * signed samples throughout.
 *
 * Pure Kotlin with no Android dependency, so it is unit-tested on the JVM.
 */
internal object PcmResampler {

    const val TARGET_RATE = 16000

    /**
     * Returns the first [length] samples of [input], captured at [srcRate] Hz,
     * resampled to [TARGET_RATE]. When the source is already 16 kHz the samples
     * are returned unchanged.
     *
     * A fractional read cursor walks the source and linearly interpolates
     * between neighbouring samples. Each call is independent (no carry across
     * blocks), which leaves a negligible discontinuity at block edges — well
     * below what matters for recognition, and the recorded file is untouched by
     * this path anyway.
     */
    fun toVoskRate(input: ShortArray, length: Int, srcRate: Int): ShortArray {
        require(srcRate > 0) { "srcRate must be positive" }
        if (length <= 0) return ShortArray(0)
        if (srcRate == TARGET_RATE) return input.copyOf(length)

        val step = srcRate.toDouble() / TARGET_RATE
        val outLen = (length / step).toInt()
        val out = ShortArray(outLen)
        var pos = 0.0
        for (i in 0 until outLen) {
            val idx = pos.toInt()
            val frac = pos - idx
            val a = input[idx].toInt()
            val b = if (idx + 1 < length) input[idx + 1].toInt() else a
            out[i] = (a + (b - a) * frac).toInt().toShort()
            pos += step
        }
        return out
    }
}
