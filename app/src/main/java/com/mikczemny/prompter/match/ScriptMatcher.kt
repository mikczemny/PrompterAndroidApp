package com.mikczemny.prompter.match

/** Snapshot of the matcher state returned after each transcript chunk. */
data class MatchState(
    val currentIndex: Int,
    val totalWords: Int,
    val paused: Boolean,
    val wordsPerSecond: Double,
    val progress: Double,
)

/**
 * Stateful matcher that consumes recognized speech chunks and tracks the
 * speaker's position in the script. Ported from the JS ScriptMatcher class.
 */
class ScriptMatcher(scriptText: String) {

    private val tokens: List<ScriptToken> = tokenizeScript(scriptText)

    /** Raw display units, one per matchable token — render these so highlight
     *  indices returned in [MatchState.currentIndex] line up with what's shown. */
    val displayTokens: List<String> = tokens.map { it.raw }

    /**
     * Character offset of each token in the script text as it was given to this
     * matcher. Lets the caller render that text unaltered — keeping the writer's
     * line breaks and spacing — and still locate any token within it.
     */
    val tokenOffsets: IntArray = IntArray(tokens.size) { tokens[it].start }

    /**
     * Token indices currently visible in the viewport, in script-token index
     * space. Refreshed by the UI every frame from scroll position + layout.
     * Alignment never returns an index outside this range — a word that
     * isn't actually on screen can't win a match regardless of score. Null
     * (the default, and the state before the first layout pass) means
     * unrestricted.
     */
    var visibleRange: IntRange? = null

    private var currentIndex: Int = -1          // last confirmed matched word (-1 = not started)
    private val spokenBuffer = ArrayDeque<String>() // rolling window of normalized spoken words
    // Vosk re-sends the whole in-progress utterance on every partial result, so
    // we remember what we already consumed and only append the new tail.
    private var partialWords: List<String> = emptyList()
    private var lastAdvanceTs: Long = now()
    private var wordsPerSecEMA: Double = 0.0
    private val advanceHistory = ArrayDeque<Advance>() // for WPM calc

    private data class Advance(val ts: Long, val index: Int)

    fun reset() = jumpTo(-1)

    /**
     * Moves the confirmed pointer to [index] (-1 = before the first word) and
     * drops accumulated speech state, so tracking resumes cleanly from there.
     * Used by the on-screen restart control and tap-to-position.
     */
    fun jumpTo(index: Int) {
        currentIndex = index.coerceIn(-1, tokens.size - 1)
        spokenBuffer.clear()
        partialWords = emptyList()
        advanceHistory.clear()
        wordsPerSecEMA = 0.0
        lastAdvanceTs = now()
    }

    /**
     * Feed recognized text. [isFinal] distinguishes Vosk's cumulative partial
     * hypotheses from a settled utterance; both carry the full text so far, and
     * only the words past the previously seen prefix are new.
     */
    fun pushTranscript(text: String, isFinal: Boolean = true, timestamp: Long = now()): MatchState {
        val words = splitWords(text)
            .map { normalizeWord(it) }
            .filter { it.isNotEmpty() && !isFillerWord(it) }

        // Vosk revises hypotheses, so compare against the last partial rather
        // than assuming the new one is a strict extension.
        var shared = 0
        while (shared < words.size && shared < partialWords.size && words[shared] == partialWords[shared]) {
            shared++
        }
        val fresh = words.drop(shared)
        partialWords = if (isFinal) emptyList() else words

        if (fresh.isEmpty()) return getState(timestamp)

        spokenBuffer.addAll(fresh)
        while (spokenBuffer.size > SPOKEN_BUFFER_SIZE) {
            spokenBuffer.removeFirst()
        }

        tryAlign(timestamp)
        return getState(timestamp)
    }

    private fun tryAlign(timestamp: Long) {
        // A long silence after the last confirmed advance usually means the
        // speaker paused and then deliberately moved on — dragged the script,
        // skipped a paragraph, whatever. The normal lookahead is kept narrow
        // so ordinary speech can't leap across the page on a coincidence, but
        // widening it here lets a genuine skip resolve on its own instead of
        // requiring a manual tap. The "strong jump" evidence bar below is
        // unaffected, so this only expands what the aligner can *see*, not
        // what it's willing to *accept*.
        val lookahead =
            if (timestamp - lastAdvanceTs > WIDEN_AFTER_SILENCE_MS) WIDE_LOOKAHEAD_WORDS
            else LOOKAHEAD_WORDS

        var windowStart = maxOf(0, currentIndex + 1 - LOOKBACK_WORDS)
        var windowEnd = minOf(tokens.size, currentIndex + 1 + lookahead)
        visibleRange?.let { visible ->
            windowStart = maxOf(windowStart, visible.first)
            windowEnd = minOf(windowEnd, visible.last + 1)
        }
        if (windowStart >= windowEnd) return

        val scriptWindow = tokens.subList(windowStart, windowEnd).map { it.norm }
        val anchor = (currentIndex + 1) - windowStart
        val result = alignToScript(spokenBuffer.toList(), scriptWindow, anchor)
            ?: return // no confident match: hold position (speaker paused/off-script)

        // One coincidental word is not evidence of anything.
        if (result.matchedWords < MIN_MATCHED_WORDS) return

        val newIndex = windowStart + result.endIndex
        val jump = newIndex - currentIndex
        if (jump == 0) return

        // A short step forward is the normal case and needs no extra proof.
        // Leaping over a chunk of script, or backing up, has to be earned by a
        // long clean phrase — otherwise off-script chatter drags the prompter away.
        val strong = result.matchedWords >= STRONG_MATCH_WORDS && result.score >= STRONG_JUMP_SCORE
        if (jump !in 1..MAX_QUIET_JUMP && !strong) return

        if (jump < 0) advanceHistory.clear() // speed history is meaningless across a jump back
        currentIndex = newIndex
        advanceHistory.addLast(Advance(timestamp, newIndex))
        // keep ~4s of history
        while (advanceHistory.isNotEmpty() && timestamp - advanceHistory.first().ts >= 4000) {
            advanceHistory.removeFirst()
        }
        lastAdvanceTs = timestamp
    }

    /** Words-per-second estimated from recent advancement history. */
    private fun estimateWordsPerSecond(): Double {
        if (advanceHistory.size < 2) return 0.0
        val first = advanceHistory.first()
        val last = advanceHistory.last()
        val dt = (last.ts - first.ts) / 1000.0
        if (dt <= 0) return 0.0
        return (last.index - first.index) / dt
    }

    fun getState(timestamp: Long = now()): MatchState {
        val paused = timestamp - lastAdvanceTs > PAUSE_MS
        val instantWps = if (paused) 0.0 else estimateWordsPerSecond()

        // EMA so the scroll speed doesn't jitter word-to-word.
        wordsPerSecEMA += SPEED_SMOOTHING * (instantWps - wordsPerSecEMA)

        return MatchState(
            currentIndex = currentIndex,
            totalWords = tokens.size,
            paused = paused,
            wordsPerSecond = maxOf(0.0, wordsPerSecEMA),
            progress = if (tokens.isNotEmpty()) (currentIndex + 1).toDouble() / tokens.size else 0.0,
        )
    }

    companion object {
        private const val LOOKAHEAD_WORDS = 30   // how far forward we're willing to look
        private const val WIDE_LOOKAHEAD_WORDS = 200 // after a long silence (see WIDEN_AFTER_SILENCE_MS)
        private const val WIDEN_AFTER_SILENCE_MS = 5000L
        private const val LOOKBACK_WORDS = 12    // backtrack allowance (repeated phrase, retake)
        private const val SPOKEN_BUFFER_SIZE = 8 // how many recent spoken words we align with
        private const val PAUSE_MS = 1100        // no forward progress this long => stop scroll
        private const val SPEED_SMOOTHING = 0.15 // EMA factor for scroll speed (0..1)
        private const val MIN_MATCHED_WORDS = 2  // never move on a single-word coincidence
        private const val MAX_QUIET_JUMP = 12    // forward step accepted without extra evidence
        private const val STRONG_MATCH_WORDS = 4 // words a long jump / backtrack must match
        private const val STRONG_JUMP_SCORE = 26.0

        private fun now(): Long = System.currentTimeMillis()
    }
}
