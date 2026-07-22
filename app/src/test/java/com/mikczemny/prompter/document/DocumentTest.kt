package com.mikczemny.prompter.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds a minimal but structurally real .docx in memory. */
private fun docx(bodyXml: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write("<Types/>".toByteArray())
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write(
            """<?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>$bodyXml</w:body>
            </w:document>
            """.trimIndent().toByteArray()
        )
        zip.closeEntry()
    }
    return out.toByteArray()
}

private fun paragraph(vararg runs: String) =
    "<w:p>" + runs.joinToString("") { "<w:r><w:t>$it</w:t></w:r>" } + "</w:p>"

class DocxExtractorTest {

    @Test
    fun `reads paragraphs and separates them with a blank line`() {
        val file = docx(paragraph("Good morning everyone.") + paragraph("Let's begin."))

        val text = DocxExtractor.extractText(ByteArrayInputStream(file))

        assertEquals("Good morning everyone.\n\nLet's begin.", text)
    }

    /** Word splits a sentence across runs whenever formatting or spelling state changes. */
    @Test
    fun `joins text runs inside one paragraph`() {
        val file = docx(paragraph("Today we ", "will look at ", "the numbers."))

        val text = DocxExtractor.extractText(ByteArrayInputStream(file))

        assertEquals("Today we will look at the numbers.", text)
    }

    @Test
    fun `keeps explicit line breaks inside a paragraph`() {
        val file = docx("<w:p><w:r><w:t>First line</w:t><w:br/><w:t>second line</w:t></w:r></w:p>")

        val text = DocxExtractor.extractText(ByteArrayInputStream(file))

        assertEquals("First line\nsecond line", text)
    }

    @Test
    fun `collapses the empty paragraphs Word leaves behind`() {
        val file = docx(
            paragraph("One.") + "<w:p/><w:p/><w:p/>" + paragraph("Two.")
        )

        val text = DocxExtractor.extractText(ByteArrayInputStream(file))

        assertEquals("One.\n\nTwo.", text)
    }

    @Test
    fun `rejects a file that is not a Word document`() {
        val notDocx = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val error = runCatching {
            DocxExtractor.extractText(ByteArrayInputStream(notDocx))
        }.exceptionOrNull()

        assertTrue("expected an IOException, got $error", error is IOException)
    }
}

class ScriptFormattingTest {

    @Test
    fun `puts each sentence on its own line`() {
        val text = oneSentencePerLine(
            "Good morning. Today we review the numbers. Let's begin!"
        )

        assertEquals(
            "Good morning.\nToday we review the numbers.\nLet's begin!",
            text,
        )
    }

    @Test
    fun `keeps paragraphs apart`() {
        val text = oneSentencePerLine("One. Two.\n\nThree. Four.")

        assertEquals("One.\nTwo.\n\nThree.\nFour.", text)
    }

    /** Breaking on every full stop would shred abbreviations and numbers. */
    @Test
    fun `does not break inside abbreviations or decimals`() {
        val text = oneSentencePerLine("Dr. Smith reported 3.5 million in revenue.")

        assertEquals("Dr. Smith reported 3.5 million in revenue.", text)
    }

    @Test
    fun `does not break after a Polish abbreviation`() {
        val text = oneSentencePerLine("Weźmy np. ten przykład. Teraz dalej.")

        assertEquals("Weźmy np. ten przykład.\nTeraz dalej.", text)
    }

    @Test
    fun `does not break after an initial`() {
        val text = oneSentencePerLine("Rozmawiałem z J. Kowalskim wczoraj. Potwierdził to.")

        assertEquals("Rozmawiałem z J. Kowalskim wczoraj.\nPotwierdził to.", text)
    }

    @Test
    fun `handles question and exclamation marks`() {
        val text = oneSentencePerLine("Ready? Set! Go.")

        assertEquals("Ready?\nSet!\nGo.", text)
    }

    @Test
    fun `leaves text without sentence breaks untouched`() {
        val text = oneSentencePerLine("no terminal punctuation here")

        assertEquals("no terminal punctuation here", text)
    }
}

class TextReflowTest {

    /**
     * The point of the whole exercise: PDF line breaks come from the page
     * margin, not the writer, and must not survive onto the stage.
     */
    @Test
    fun `rejoins lines that a page margin broke`() {
        val extracted = """
            Good morning everyone, and welcome
            to the quarterly review. Today we
            will look at the numbers.

            Let's begin with revenue.
        """.trimIndent()

        val text = reflowWrappedLines(extracted)

        assertEquals(
            "Good morning everyone, and welcome to the quarterly review. " +
                "Today we will look at the numbers.\n\nLet's begin with revenue.",
            text,
        )
    }

    @Test
    fun `repairs a word hyphenated across a line break`() {
        val text = reflowWrappedLines("an expla-\nnation of the results")

        assertEquals("an explanation of the results", text)
    }

    @Test
    fun `leaves a real dash alone`() {
        val text = reflowWrappedLines("the result --\nand this matters -- was clear")

        assertEquals("the result -- and this matters -- was clear", text)
    }

    @Test
    fun `collapses runs of blank lines to one paragraph break`() {
        val text = reflowWrappedLines("First.\n\n\n\n\nSecond.")

        assertEquals("First.\n\nSecond.", text)
    }
}
