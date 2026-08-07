package com.mikczemny.prompter.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmResamplerTest {

    @Test
    fun `passes 16 kHz through unchanged`() {
        val input = shortArrayOf(1, 2, 3, 4, 5)
        val out = PcmResampler.toVoskRate(input, input.size, 16000)
        assertEquals(input.toList(), out.toList())
    }

    @Test
    fun `48 kHz downsamples by three to one`() {
        val input = ShortArray(4800) // 100 ms at 48 kHz
        val out = PcmResampler.toVoskRate(input, input.size, 48000)
        assertEquals(1600, out.size) // 100 ms at 16 kHz
    }

    @Test
    fun `44_1 kHz downsamples to the expected length`() {
        val input = ShortArray(4410) // 100 ms at 44.1 kHz
        val out = PcmResampler.toVoskRate(input, input.size, 44100)
        assertEquals(1600, out.size)
    }

    @Test
    fun `honours the length argument, not the array size`() {
        val input = ShortArray(48000)
        val out = PcmResampler.toVoskRate(input, 4800, 48000)
        assertEquals(1600, out.size)
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(0, PcmResampler.toVoskRate(ShortArray(0), 0, 48000).size)
    }

    /** A rising ramp should stay monotonic through linear interpolation. */
    @Test
    fun `preserves a monotonic ramp`() {
        val input = ShortArray(3000) { (it / 3).toShort() }
        val out = PcmResampler.toVoskRate(input, input.size, 48000)
        for (i in 1 until out.size) {
            assertTrue("sample $i dipped: ${out[i - 1]} -> ${out[i]}", out[i] >= out[i - 1])
        }
    }
}
