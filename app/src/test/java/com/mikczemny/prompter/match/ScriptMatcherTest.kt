package com.mikczemny.prompter.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for voice tracking. These exist because the thresholds in
 * [ScriptMatcher] and TextMatcher trade two failure modes against each other —
 * too loose and the prompter leaps over paragraphs on a coincidence, too tight
 * and it stalls on normal speech. Tuning one without a regression net silently
 * reintroduces the other.
 *
 * "three" appears twice on purpose, and every other word is unique, so the
 * expected alignment of any phrase below is unambiguous.
 */
private const val SCRIPT =
    "one two three four five " +
        "alpha bravo charlie delta echo foxtrot golf hotel india juliet " +
        "kilo lima mike november oscar " +
        "three papa quebec romeo sierra"

private val TOKENS = SCRIPT.split(" ")

private fun indexOfWord(word: String): Int = TOKENS.indexOf(word)

class ScriptMatcherTest {

    @Test
    fun `starts tracking from the opening words`() {
        val matcher = ScriptMatcher(SCRIPT)

        val state = matcher.pushTranscript("one two three")

        assertEquals(indexOfWord("three"), state.currentIndex)
    }

    /**
     * The reported field failure: saying something off-script that happens to
     * contain one word from later in the text used to teleport the prompter
     * there, skipping everything in between and never coming back.
     */
    @Test
    fun `a single coincidental word does not jump ahead`() {
        val matcher = ScriptMatcher(SCRIPT)
        matcher.jumpTo(2) // reading around "three"

        // "oscar" occurs only far ahead; one word is not evidence of anything.
        val state = matcher.pushTranscript("oscar")

        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `off-script chatter holds the position`() {
        val matcher = ScriptMatcher(SCRIPT)
        matcher.jumpTo(2)

        val state = matcher.pushTranscript("well anyway as I was saying")

        assertEquals(2, state.currentIndex)
    }

    /**
     * The flip side: a deliberate skip must still work, so a long clean phrase
     * from further down is allowed to move the pointer.
     */
    @Test
    fun `a long clean phrase can skip forward`() {
        val matcher = ScriptMatcher(SCRIPT)
        matcher.jumpTo(2)

        val state = matcher.pushTranscript("kilo lima mike november")

        assertEquals(indexOfWord("november"), state.currentIndex)
    }

    /**
     * Recovery after a bad jump. Before backward correction existed the pointer
     * only ever moved forward, so one false positive stranded the speaker for
     * the rest of the take.
     */
    @Test
    fun `a long clean phrase can pull the pointer back`() {
        val matcher = ScriptMatcher(SCRIPT)
        matcher.jumpTo(indexOfWord("oscar"))

        // Backtracking reaches only as far as the lookback window, so this
        // phrase sits just inside it.
        val state = matcher.pushTranscript("delta echo foxtrot golf")

        assertEquals(indexOfWord("golf"), state.currentIndex)
    }

    /**
     * Vosk resends the whole in-progress utterance on every partial result.
     * Feeding those cumulatively used to stuff the alignment buffer with
     * repeats of the same words, crowding out real context.
     */
    @Test
    fun `cumulative partials track the same as one final result`() {
        val viaPartials = ScriptMatcher(SCRIPT)
        viaPartials.pushTranscript("one", isFinal = false)
        viaPartials.pushTranscript("one two", isFinal = false)
        viaPartials.pushTranscript("one two three", isFinal = false)
        viaPartials.pushTranscript("one two three four", isFinal = true)

        val viaFinal = ScriptMatcher(SCRIPT)
        viaFinal.pushTranscript("one two three four", isFinal = true)

        assertEquals(viaFinal.getState().currentIndex, viaPartials.getState().currentIndex)
        assertEquals(indexOfWord("four"), viaPartials.getState().currentIndex)
    }

    /** Vosk revises hypotheses rather than only extending them. */
    @Test
    fun `a revised partial does not double-count the changed prefix`() {
        val matcher = ScriptMatcher(SCRIPT)
        matcher.pushTranscript("one to", isFinal = false)
        val state = matcher.pushTranscript("one two three", isFinal = false)

        assertEquals(indexOfWord("three"), state.currentIndex)
    }

    @Test
    fun `reading straight through advances monotonically to the end`() {
        val matcher = ScriptMatcher(SCRIPT)
        var previous = -1

        TOKENS.chunked(3).forEach { phrase ->
            val state = matcher.pushTranscript(phrase.joinToString(" "))
            assertTrue(
                "pointer went backwards at '${phrase.joinToString(" ")}'",
                state.currentIndex >= previous,
            )
            previous = state.currentIndex
        }

        assertEquals(TOKENS.lastIndex, previous)
    }

    @Test
    fun `jumpTo and reset move the pointer explicitly`() {
        val matcher = ScriptMatcher(SCRIPT)
        matcher.pushTranscript("one two three")

        matcher.jumpTo(10)
        assertEquals(10, matcher.getState().currentIndex)

        matcher.reset()
        assertEquals(-1, matcher.getState().currentIndex)
    }

    @Test
    fun `jumpTo clamps out-of-range targets`() {
        val matcher = ScriptMatcher(SCRIPT)

        matcher.jumpTo(9999)
        assertEquals(TOKENS.lastIndex, matcher.getState().currentIndex)

        matcher.jumpTo(-50)
        assertEquals(-1, matcher.getState().currentIndex)
    }
}

/**
 * The stage renders the script exactly as written and locates words by these
 * offsets. If tokenisation and the offsets ever disagree, the highlight lands
 * on the wrong word — visible only on a device, so it is pinned here.
 */
class TokenOffsetTest {

    @Test
    fun `offsets point at the token inside the original text`() {
        val text = "Hello   world\n\nSecond paragraph."

        val tokens = tokenizeScript(text)

        tokens.forEach { token ->
            assertEquals(
                "token ${token.index} (${token.raw}) has a wrong offset",
                token.raw,
                text.substring(token.start, token.start + token.raw.length),
            )
        }
        assertEquals(listOf("Hello", "world", "Second", "paragraph."), tokens.map { it.raw })
    }

    @Test
    fun `blank lines and repeated spaces do not shift offsets`() {
        val text = "\n\n   Ready?\n\n\n    Steady   go\n"

        val tokens = tokenizeScript(text)

        assertEquals(listOf("Ready?", "Steady", "go"), tokens.map { it.raw })
        tokens.forEach { token ->
            assertEquals(
                token.raw,
                text.substring(token.start, token.start + token.raw.length),
            )
        }
    }

    @Test
    fun `CJK characters are separate tokens with their own offsets`() {
        val text = "大家好 hello"

        val tokens = tokenizeScript(text)

        assertEquals(listOf("大", "家", "好", "hello"), tokens.map { it.raw })
        tokens.forEach { token ->
            assertEquals(
                token.raw,
                text.substring(token.start, token.start + token.raw.length),
            )
        }
    }

    @Test
    fun `matcher exposes one offset per display token`() {
        val matcher = ScriptMatcher("one two\nthree")

        assertEquals(matcher.displayTokens.size, matcher.tokenOffsets.size)
        assertEquals(listOf("one", "two", "three"), matcher.displayTokens)
        assertEquals(listOf(0, 4, 8), matcher.tokenOffsets.toList())
    }
}

class TextMatcherTest {

    @Test
    fun `alignment reports how many words actually matched`() {
        val result = alignToScript(
            spoken = listOf("alpha", "bravo", "charlie"),
            scriptWindow = listOf("alpha", "bravo", "charlie", "delta"),
        )

        requireNotNull(result)
        assertEquals(3, result.matchedWords)
        assertEquals(2, result.endIndex)
    }

    @Test
    fun `a lone weak match is not trusted`() {
        val result = alignToScript(
            spoken = listOf("zulu"),
            scriptWindow = listOf("alpha", "bravo", "zulu", "delta"),
        )

        assertEquals(null, result)
    }

    /** With equal evidence, the alignment nearer the anchor must win. */
    @Test
    fun `distance from the anchor breaks ties`() {
        val window = listOf("alpha", "bravo", "charlie", "delta", "echo", "alpha", "bravo")

        val near = alignToScript(listOf("alpha", "bravo"), window, anchor = 0)
        val far = alignToScript(listOf("alpha", "bravo"), window, anchor = 6)

        assertEquals(1, requireNotNull(near).endIndex)
        assertEquals(6, requireNotNull(far).endIndex)
    }

    @Test
    fun `misheard words still align`() {
        val result = alignToScript(
            spoken = listOf("alphaa", "bravoo"), // plausible recognizer noise
            scriptWindow = listOf("alpha", "bravo", "charlie"),
        )

        assertEquals(1, requireNotNull(result).endIndex)
    }
}

class FillerWordTest {

    @Test
    fun `pure disfluency sounds are recognized as filler`() {
        listOf(
            "um", "umm", "uhm", "uh", "uhh", "erm", "euh", "ehm", "ahm", "yyy", "eee",
        ).forEach { assertTrue("'$it' should be a filler word", isFillerWord(it)) }
    }

    /**
     * These double as filler in casual speech but can be real scripted lines
     * ("So, welcome back." / "It's, like, a big deal."), so they must survive
     * untouched even though a naive filler list would be tempted to include
     * them.
     */
    @Test
    fun `casual words that can be real script content are not filtered`() {
        listOf("like", "so", "well", "eh", "ah", "no")
            .forEach { assertTrue("'$it' should not be treated as filler", !isFillerWord(it)) }
    }

    @Test
    fun `a filler word inserted mid-utterance does not disrupt tracking`() {
        val withFiller = ScriptMatcher(SCRIPT)
        withFiller.pushTranscript("one um two three")

        val clean = ScriptMatcher(SCRIPT)
        clean.pushTranscript("one two three")

        assertEquals(clean.getState().currentIndex, withFiller.getState().currentIndex)
        assertEquals(indexOfWord("three"), withFiller.getState().currentIndex)
    }
}
