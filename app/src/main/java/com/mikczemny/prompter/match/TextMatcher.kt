package com.mikczemny.prompter.match

import java.text.Normalizer

/**
 * Core synchronization engine for the teleprompter, ported from the JS
 * prototype's textMatcher.js.
 *
 * Web/on-device speech recognition gives noisy, imperfect transcripts
 * (skipped words, misheard words, filler words). A plain substring search
 * against the script would freeze on the first mismatch. Instead we treat
 * "recent spoken words" and "a window of the script" as two sequences and run
 * local sequence alignment (Smith-Waterman) which tolerates substitutions and
 * gaps on both sides and returns the best-scoring alignment even when imperfect.
 */

// ---------- Word-level similarity -----------------------------------

/** Classic Levenshtein edit distance, O(m*n), fine for single words. */
private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    val m = a.length
    val n = b.length
    if (m == 0) return n
    if (n == 0) return m

    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)

    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(
                prev[j] + 1,        // deletion
                curr[j - 1] + 1,    // insertion
                prev[j - 1] + cost  // substitution
            )
        }
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[n]
}

/** 1.0 = identical, 0.0 = completely different. */
fun wordSimilarity(a: String, b: String): Double {
    if (a == b) return 1.0
    val maxLen = maxOf(a.length, b.length)
    if (maxLen == 0) return 1.0
    return 1.0 - levenshtein(a, b).toDouble() / maxLen
}

private val DIACRITICS = Regex("[\\u0300-\\u036f]")
// Keep any Unicode letter or digit (Latin, Cyrillic, CJK, Devanagari, ...);
// drop punctuation/symbols. This is what makes the matcher language-agnostic.
private val NON_WORD = Regex("[^\\p{L}\\p{N}]")

fun normalizeWord(w: String): String {
    val lowered = w.lowercase()
    val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFKD)
    return decomposed
        .replace(DIACRITICS, "")   // strip combining accents (Latin etc.)
        .replace(NON_WORD, "")
}

// ---------- Script tokenization ---------------------------------------

data class ScriptToken(val index: Int, val raw: String, val norm: String)

internal val WHITESPACE = Regex("\\s+")

/** True for CJK ideographs and Japanese kana, which are written without spaces. */
private fun isCjk(ch: Char): Boolean {
    val c = ch.code
    return (c in 0x4E00..0x9FFF) ||   // CJK Unified Ideographs
        (c in 0x3400..0x4DBF) ||       // CJK Extension A
        (c in 0x3040..0x309F) ||       // Hiragana
        (c in 0x30A0..0x30FF) ||       // Katakana
        (c in 0xF900..0xFAFF)          // CJK Compatibility Ideographs
}

/**
 * Splits text into matchable units. Space-delimited languages yield one unit
 * per word; CJK/kana yield one unit per character (those scripts have no word
 * spaces), so alignment stays fine-grained across languages. Shared by the
 * script tokenizer and the spoken-transcript path so their indices line up.
 */
fun splitWords(text: String): List<String> {
    val out = ArrayList<String>()
    for (chunk in text.split(WHITESPACE)) {
        if (chunk.isEmpty()) continue
        val sb = StringBuilder()
        for (ch in chunk) {
            if (isCjk(ch)) {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString()); sb.setLength(0)
                }
                out.add(ch.toString())
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
    }
    return out
}

/** True if a display token is a single CJK/kana character (rendered without a trailing space). */
fun isCjkToken(token: String): Boolean = token.length == 1 && isCjk(token[0])

fun tokenizeScript(rawText: String): List<ScriptToken> {
    return splitWords(rawText).mapIndexed { index, raw ->
        ScriptToken(index = index, raw = raw, norm = normalizeWord(raw))
    }
}

// ---------- Local alignment (Smith-Waterman variant) -------------------

private const val MATCH_SIM_THRESHOLD = 0.6   // below this, treat as mismatch
private const val MATCH_SCORE = 8.0
private const val MISMATCH_PENALTY = -4.0
private const val GAP_PENALTY = -3.0
private const val MIN_ACCEPT_SCORE = 6.0      // minimum alignment score to trust

data class AlignResult(val endIndex: Int, val score: Double)

/**
 * Aligns [spoken] (recent recognized words, normalized) against [scriptWindow]
 * (a slice of script tokens, normalized) and returns the best-matching end
 * index within scriptWindow, or null if nothing scored high enough to trust
 * (keep current position — speaker likely paused or off-script for a moment).
 */
fun alignToScript(spoken: List<String>, scriptWindow: List<String>): AlignResult? {
    val m = spoken.size
    val n = scriptWindow.size
    if (m == 0 || n == 0) return null

    // H[i][j] = best local-alignment score ending at spoken[i-1], script[j-1]
    val h = Array(m + 1) { DoubleArray(n + 1) }

    var bestScore = 0.0
    var bestJ = 0

    for (i in 1..m) {
        for (j in 1..n) {
            val sim = wordSimilarity(spoken[i - 1], scriptWindow[j - 1])
            val matchScore = if (sim >= MATCH_SIM_THRESHOLD) MATCH_SCORE * sim else MISMATCH_PENALTY

            val diag = h[i - 1][j - 1] + matchScore
            val up = h[i - 1][j] + GAP_PENALTY     // gap in script (extra/filler spoken word)
            val left = h[i][j - 1] + GAP_PENALTY   // gap in speech (script word skipped)

            val value = maxOf(0.0, diag, up, left)
            h[i][j] = value

            if (value > bestScore) {
                bestScore = value
                bestJ = j
            }
        }
    }

    if (bestScore < MIN_ACCEPT_SCORE) return null
    return AlignResult(endIndex = bestJ - 1, score = bestScore) // index into scriptWindow
}
