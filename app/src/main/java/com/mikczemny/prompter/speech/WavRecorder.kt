package com.mikczemny.prompter.speech

import java.io.File
import java.io.RandomAccessFile

/**
 * Streams 16-bit mono PCM to a WAV file.
 *
 * The recorder is fed the raw capture-rate samples (not the 16 kHz Vosk copy),
 * so the file keeps full fidelity. A WAV header needs byte counts that are only
 * known once recording ends, so it is written with zero placeholders up front
 * and patched on [close].
 *
 * Not thread-safe on its own; the caller serializes writes against close.
 */
internal class WavRecorder(private val file: File, private val sampleRate: Int) {

    private val out = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        out.setLength(0)
        writeHeader(dataBytes = 0)
    }

    /** Appends the first [length] samples of [samples] as little-endian 16-bit PCM. */
    fun write(samples: ShortArray, length: Int) {
        val bytes = ByteArray(length * 2)
        var b = 0
        for (i in 0 until length) {
            val s = samples[i].toInt()
            bytes[b++] = (s and 0xFF).toByte()
            bytes[b++] = ((s shr 8) and 0xFF).toByte()
        }
        out.write(bytes)
        dataBytes += bytes.size
    }

    /** Finalizes the header with the real sizes and closes the file. */
    fun close() {
        out.seek(0)
        writeHeader(dataBytes)
        out.close()
    }

    private fun writeHeader(dataBytes: Long) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        out.writeBytes("RIFF")
        writeIntLE((36 + dataBytes).toInt())   // ChunkSize
        out.writeBytes("WAVE")

        out.writeBytes("fmt ")
        writeIntLE(16)                          // Subchunk1Size (PCM)
        writeShortLE(1)                         // AudioFormat = PCM
        writeShortLE(channels)
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(blockAlign)
        writeShortLE(bitsPerSample)

        out.writeBytes("data")
        writeIntLE(dataBytes.toInt())           // Subchunk2Size
    }

    private fun writeIntLE(value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}
