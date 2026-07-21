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

    private var currentIndex: Int = -1          // last confirmed matched word (-1 = not started)
    private val spokenBuffer = ArrayDeque<String>() // rolling window of normalized spoken words
    private var lastAdvanceTs: Long = now()
    private var wordsPerSecEMA: Double = 0.0
    private val advanceHistory = ArrayDeque<Advance>() // for WPM calc

    private data class Advance(val ts: Long, val index: Int)

    fun reset() {
        currentIndex = -1
        spokenBuffer.clear()
        advanceHistory.clear()
        wordsPerSecEMA = 0.0
        lastAdvanceTs = now()
    }

    /**
     * Feed a chunk of newly recognized text (partial or final). Returns the
     * updated state.
     */
    fun pushTranscript(text: String, timestamp: Long = now()): MatchState {
        val words = splitWords(text)
            .map { normalizeWord(it) }
            .filter { it.isNotEmpty() }

        if (words.isEmpty()) return getState(timestamp)

        spokenBuffer.addAll(words)
        while (spokenBuffer.size > SPOKEN_BUFFER_SIZE) {
            spokenBuffer.removeFirst()
        }

        tryAlign(timestamp)
        return getState(timestamp)
    }

    private fun tryAlign(timestamp: Long) {
        val windowStart = maxOf(0, currentIndex + 1 - LOOKBACK_WORDS)
        val windowEnd = minOf(tokens.size, currentIndex + 1 + LOOKAHEAD_WORDS)
        if (windowStart >= windowEnd) return

        val scriptWindow = tokens.subList(windowStart, windowEnd).map { it.norm }
        val result = alignToScript(spokenBuffer.toList(), scriptWindow)
            ?: return // no confident match: hold position (speaker paused/off-script)

        val newIndex = windowStart + result.endIndex

        // Only move forward; never regress the confirmed pointer to avoid
        // visual jumping backwards on a false positive.
        if (newIndex > currentIndex) {
            currentIndex = newIndex
            advanceHistory.addLast(Advance(timestamp, newIndex))
            // keep ~4s of history
            while (advanceHistory.isNotEmpty() && timestamp - advanceHistory.first().ts >= 4000) {
                advanceHistory.removeFirst()
            }
            lastAdvanceTs = timestamp
        }
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
        private const val LOOKAHEAD_WORDS = 60   // how far forward we're willing to jump
        private const val LOOKBACK_WORDS = 8     // small backtrack allowance (repeated phrase)
        private const val SPOKEN_BUFFER_SIZE = 8 // how many recent spoken words we align with
        private const val PAUSE_MS = 1100        // no forward progress this long => stop scroll
        private const val SPEED_SMOOTHING = 0.15 // EMA factor for scroll speed (0..1)

        private fun now(): Long = System.currentTimeMillis()
    }
}
