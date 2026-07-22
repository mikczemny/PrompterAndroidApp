package com.mikczemny.prompter.document

/**
 * Turns extracted document text into something worth reading aloud from.
 *
 * PDF extraction returns one line per *visual* line, because that is all a PDF
 * records — the line breaks come from where the text hit the right margin, not
 * from where the writer meant to pause. Rendered on a prompter at 44sp those
 * breaks land in the middle of sentences and read terribly.
 *
 * So within a paragraph the lines are rejoined, and only blank lines are
 * treated as real breaks. Words split across a line by a hyphen are put back
 * together, since a hyphen at end of line is nearly always the extractor's
 * doing rather than the writer's.
 */
internal fun reflowWrappedLines(raw: String): String {
    val normalised = raw.replace("\r\n", "\n").replace('\r', '\n')

    return normalised
        .split(BLANK_LINE)
        .map { paragraph -> joinParagraphLines(paragraph) }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
        .trim()
}

private fun joinParagraphLines(paragraph: String): String {
    val builder = StringBuilder()

    paragraph.split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            if (builder.isEmpty()) {
                builder.append(line)
                return@forEach
            }
            // "expla-\nnation" is one word broken by the page edge, not a
            // hyphenated compound the writer typed.
            if (builder.endsWith("-") && !builder.endsWith("--")) {
                builder.setLength(builder.length - 1)
                builder.append(line)
            } else {
                builder.append(' ').append(line)
            }
        }

    return builder.toString()
}

private val BLANK_LINE = Regex("\\n[ \\t]*\\n+")
