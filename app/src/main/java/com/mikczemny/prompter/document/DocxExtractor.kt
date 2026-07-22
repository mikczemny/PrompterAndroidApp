package com.mikczemny.prompter.document

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Pulls the readable text out of a .docx file.
 *
 * A Word document is a ZIP whose `word/document.xml` holds the body, so this
 * needs no library — Apache POI would add tens of megabytes and a desugaring
 * headache to do the same job. Parsing is streamed with SAX rather than loading
 * a DOM, because an imported document is arbitrary user input and holding the
 * whole tree in memory buys nothing here.
 *
 * Only text, paragraph breaks and line breaks are kept. Fonts, colours and
 * styling are deliberately discarded: the prompter renders one typeface at one
 * size, and a script's shape on the page is what matters for reading aloud.
 *
 * Legacy binary `.doc` is a different format entirely and is not supported.
 */
object DocxExtractor {

    private const val DOCUMENT_ENTRY = "word/document.xml"

    /** Guards against a malformed or hostile archive expanding without bound. */
    private const val MAX_CHARS = 2_000_000

    /**
     * @throws IOException if [input] is not a Word document, or its body is
     *   missing or unreadable.
     */
    fun extractText(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == DOCUMENT_ENTRY) {
                    // The entry stream is handed to the parser directly; closing
                    // it is the ZipInputStream's job, not the parser's.
                    return parseBody(zip)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        throw IOException("Not a Word document — $DOCUMENT_ENTRY is missing")
    }

    private fun parseBody(stream: InputStream): String {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // An imported document is untrusted. Refusing DOCTYPE declarations
            // shuts off external entity resolution, so a crafted file cannot
            // make the parser read local files or reach out over the network.
            //
            // Every hardening call is guarded: JAXP implementations differ in
            // what they support, and Android's throws outright on some of these
            // rather than ignoring them. A parser that cannot be hardened is
            // still better than an import that cannot run — Android's Expat
            // backend does not resolve external entities by default anyway.
            hardenQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
            hardenQuietly("http://xml.org/sax/features/external-general-entities", false)
            hardenQuietly("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching { isXIncludeAware = false }
        }

        val handler = WordBodyHandler()
        try {
            factory.newSAXParser().parse(stream, handler)
        } catch (e: Exception) {
            throw IOException("Could not read the Word document: ${e.message}", e)
        }
        return handler.text()
    }

    private fun SAXParserFactory.hardenQuietly(feature: String, value: Boolean) {
        runCatching { setFeature(feature, value) }
    }

    /**
     * Walks WordprocessingML, keeping only what affects how the script reads.
     * Element names are matched on their local part so the `w:` prefix, which a
     * generator is free to rename, does not matter.
     */
    private class WordBodyHandler : DefaultHandler() {
        private val out = StringBuilder()
        private var inTextRun = false
        private var truncated = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            when (localName) {
                "t" -> inTextRun = true
                "br", "cr" -> append("\n")
                "tab" -> append("\t")
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (localName) {
                "t" -> inTextRun = false
                // A blank line between paragraphs is what makes an imported wall
                // of text readable on the stage.
                "p" -> append("\n\n")
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (!inTextRun || ch == null) return
            append(String(ch, start, length))
        }

        private fun append(value: String) {
            if (truncated) return
            if (out.length + value.length > MAX_CHARS) {
                truncated = true
                return
            }
            out.append(value)
        }

        fun text(): String = tidyParagraphs(out.toString())
    }
}

private val TRAILING_SPACES = Regex("[ \\t]+(\\r?\\n)")
private val EXCESS_BLANK_LINES = Regex("(\\r?\\n){3,}")

/**
 * Word emits an empty paragraph for every stray return, which arrives here as a
 * run of blank lines. Collapsing them to a single blank line keeps the writer's
 * paragraph structure without the gaps swallowing whole screens on the stage.
 */
internal fun tidyParagraphs(raw: String): String =
    raw.replace("\r\n", "\n")
        .replace(TRAILING_SPACES, "$1")
        .replace(EXCESS_BLANK_LINES, "\n\n")
        .trim()
