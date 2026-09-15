package com.mikczemny.prompter.match

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownScriptMatcherTest {

    @Test
    fun `markdown structure markers are ignored by voice tracking`() {
        val script = """
            # MODUŁ II

            - Drony wspierają działania ratownicze.
            > Operator zachowuje odpowiedzialność za lot.

            ---
        """.trimIndent()

        val matcher = ScriptMatcher(script)

        assertEquals(
            listOf(
                "MODUŁ",
                "II",
                "Drony",
                "wspierają",
                "działania",
                "ratownicze.",
                "Operator",
                "zachowuje",
                "odpowiedzialność",
                "za",
                "lot.",
            ),
            matcher.displayTokens,
        )

        val state = matcher.pushTranscript("drony wspierają działania ratownicze")
        assertEquals(5, state.currentIndex)
    }
}
