package com.mikczemny.prompter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingGeometryTest {
    private val lines = floatArrayOf(24f, 24f, 80f, 80f, 80f, 192f, 248f, 248f)

    @Test
    fun `line controls skip blank lines and repeated word offsets`() {
        assertEquals(2, lineJumpTarget(lines, 0, 1))
        assertEquals(5, lineJumpTarget(lines, 3, 1))
        assertEquals(2, lineJumpTarget(lines, 5, -1))
        assertEquals(-1, lineJumpTarget(lines, 1, -1))
        assertEquals(-1, lineJumpTarget(lines, -1, -1))
        assertEquals(7, lineJumpTarget(lines, 7, 1))
    }

    @Test
    fun `drag resolves to first token on the reading line`() {
        assertEquals(0, tokenAtReadingLine(lines, 0f))
        assertEquals(2, tokenAtReadingLine(lines, 120f))
        assertEquals(5, tokenAtReadingLine(lines, 192f))
        assertEquals(6, tokenAtReadingLine(lines, 500f))
    }

    @Test
    fun `page controls follow actual viewport and still move with large fonts`() {
        assertEquals(5, pageJumpTarget(lines, 0, 1, 240f))
        assertEquals(-1, pageJumpTarget(lines, 2, -1, 240f))
        assertEquals(5, pageJumpTarget(lines, 2, 1, 20f))
    }

    @Test
    fun `input before layout or in an empty script is harmless`() {
        assertEquals(-1, lineJumpTarget(floatArrayOf(), -1, 1))
        assertEquals(-1, lineJumpTarget(floatArrayOf(Float.NaN), -1, 1))
        assertEquals(-1, tokenAtReadingLine(lines, Float.NaN))
        assertEquals(-1, pageJumpTarget(lines, 0, 1, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `margins scale with viewport and camera reservation swaps under mirror`() {
        assertEquals(ReadingInsets(80f, 80f), readingInsets(1000f, 8f, 0f, 0f, 160f))
        assertEquals(ReadingInsets(40f, 40f), readingInsets(500f, 8f, 0f, 0f, 160f))
        val left = readingInsets(1000f, 8f, 350f, 0f, 160f)
        val right = readingInsets(1000f, 8f, 0f, 350f, 160f)
        assertEquals(left.start, right.end, 0f)
        assertEquals(left.end, right.start, 0f)
        assertEquals(350f, left.start, 0f)
    }

    @Test
    fun `oversized preview and margins keep a readable minimum`() {
        listOf(
            readingInsets(320f, 40f, 0f, 0f, 160f),
            readingInsets(320f, 40f, 300f, 0f, 160f),
            readingInsets(320f, 40f, 300f, 300f, 160f),
        ).forEach {
            assertTrue(it.start >= 0f && it.end >= 0f)
            assertTrue(320f - it.start - it.end >= 159.99f)
        }
    }
}
