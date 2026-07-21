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
private val NON_WORD = Regex("[^a-z0-9ążźćęłńóśż]", RegexOption.IGNORE_CASE)

fun normalizeWord(w: String): String {
    val lowered = w.lowercase()
    val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFKD)
    return decomposed
        .replace(DIACRITICS, "")   // strip combining accents
        .replace(NON_WORD, "")
}

// ---------- Script tokenization ---------------------------------------

data class ScriptToken(val index: Int, val raw: String, val norm: String)

internal val WHITESPACE = Regex("\\s+")

fun tokenizeScript(rawText: String): List<ScriptToken> {
    val rawWords = rawText.split(WHITESPACE).filter { it.isNotEmpty() }
    return rawWords.mapIndexed { index, raw ->
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
