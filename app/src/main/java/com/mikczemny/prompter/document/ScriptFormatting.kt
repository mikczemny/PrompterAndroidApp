package com.mikczemny.prompter.document

/**
 * Puts each sentence on its own line, paragraph by paragraph.
 *
 * This is the single most useful thing you can do to an imported script. Prose
 * written for the page runs sentences together; read aloud from a prompter, one
 * sentence per line gives the eye a landing point and the speaker a natural
 * place to breathe. It also suits the voice tracker, whose alignment window is
 * easier to reason about when lines match units of speech.
 *
 * A break needs terminal punctuation, then whitespace, then something that
 * starts a sentence. A full stop that ends a known abbreviation or a single
 * initial does not count, so "Dr. Smith" and "J. Kowalski" survive intact.
 * It will still be wrong sometimes — which is why this is an action the user
 * invokes and can edit afterwards, never something applied silently on import.
 */
internal fun oneSentencePerLine(text: String): String =
    text.replace("\r\n", "\n")
        .split(PARAGRAPH_BREAK)
        .map { paragraph -> breakSentences(paragraph.trim()) }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
        .trim()

private fun breakSentences(paragraph: String): String {
    val out = StringBuilder(paragraph.length + 16)
    var i = 0

    while (i < paragraph.length) {
        val ch = paragraph[i]
        out.append(ch)

        if (ch in TERMINAL_PUNCTUATION) {
            var next = i + 1
            while (next < paragraph.length && (paragraph[next] == ' ' || paragraph[next] == '\t')) {
                next++
            }
            val hasGap = next > i + 1
            val startsNewSentence = next < paragraph.length && startsSentence(paragraph[next])
            // Only a full stop is ambiguous; nothing abbreviates with "?" or "!".
            val isAbbreviation = ch == '.' && endsWithAbbreviation(out)

            if (hasGap && startsNewSentence && !isAbbreviation) {
                out.append('\n')
                i = next
                continue
            }
        }
        i++
    }
    return out.toString()
}

/** An opening quote, a capital or a digit is how a new sentence tends to start. */
private fun startsSentence(ch: Char): Boolean =
    ch.isUpperCase() || ch.isDigit() || ch in SENTENCE_OPENERS

/**
 * Whether the word immediately before the trailing full stop in [builder] is an
 * abbreviation rather than the end of a sentence.
 */
private fun endsWithAbbreviation(builder: StringBuilder): Boolean {
    val stop = builder.length - 1 // the '.' just appended
    var start = stop
    while (start > 0 && builder[start - 1].isLetter()) start--
    val word = builder.substring(start, stop)

    if (word.isEmpty()) return false
    // A lone letter before a full stop is an initial: "J. Kowalski".
    if (word.length == 1) return true
    return word.lowercase() in ABBREVIATIONS
}

private const val TERMINAL_PUNCTUATION = ".!?…"
private const val SENTENCE_OPENERS = "\"'“„«‘"

/**
 * Abbreviations that take a full stop mid-sentence. Polish and English are
 * covered because those are what this app is actually used in; the cost of a
 * miss is one stray line break, which the user can delete.
 */
private val ABBREVIATIONS = setOf(
    // Polish
    "np", "itd", "itp", "tzn", "tj", "ok", "ul", "al", "św", "godz", "nr", "cz",
    "dr", "prof", "mgr", "inż", "hab", "gen", "płk", "por", "zob", "str", "tzw",
    "ww", "wg", "pt", "ps", "min", "sek", "tys", "mln", "mld", "zł", "pl",
    // English
    "mr", "mrs", "ms", "st", "jr", "sr", "vs", "etc", "eg", "ie", "approx",
    "dept", "est", "fig", "inc", "ltd", "no", "vol", "pp", "am", "pm",
)

private val PARAGRAPH_BREAK = Regex("\\n[ \\t]*\\n+")
