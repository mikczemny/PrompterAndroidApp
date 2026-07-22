package com.mikczemny.prompter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScriptStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): ScriptStore = ScriptStore(File(temp.root, "scripts"))

    @Test
    fun `saves and reads a script back unchanged`() {
        val store = store()
        val body = "Good morning.\n\nSecond paragraph.\n  indented line"

        val saved = store.save(body)
        val loaded = store.list().single()

        assertEquals(saved.id, loaded.id)
        assertEquals(body, loaded.text)
        assertEquals("Good morning.", loaded.title)
    }

    /** Layout is meaningful on the stage, so it has to survive a round trip. */
    @Test
    fun `preserves blank lines and trailing whitespace`() {
        val store = store()
        val body = "One\n\n\nTwo\n"

        store.save(body)

        assertEquals(body, store.list().single().text)
    }

    @Test
    fun `saving with an existing id overwrites rather than duplicating`() {
        val store = store()
        val first = store.save("Original text")

        store.save("Replacement text", id = first.id)

        val all = store.list()
        assertEquals(1, all.size)
        assertEquals("Replacement text", all.single().text)
    }

    @Test
    fun `lists newest first`() {
        val store = store()
        val older = store.save("First script")
        File(temp.root, "scripts/${older.id}.script").setLastModified(1_000_000L)
        val newer = store.save("Second script")
        File(temp.root, "scripts/${newer.id}.script").setLastModified(2_000_000L)

        assertEquals(listOf(newer.id, older.id), store.list().map { it.id })
    }

    @Test
    fun `deletes a script`() {
        val store = store()
        val saved = store.save("Disposable")

        store.delete(saved.id)

        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `an empty directory lists nothing`() {
        assertTrue(store().list().isEmpty())
    }

    /** A title lives on the first line, so a newline in it would eat the body. */
    @Test
    fun `strips newlines from an explicit title`() {
        val store = store()

        store.save(text = "Body text", title = "Line one\nline two")

        val loaded = store.list().single()
        assertEquals("Line one line two", loaded.title)
        assertEquals("Body text", loaded.text)
    }
}

class TitleFromTest {

    @Test
    fun `uses the first non-blank line`() {
        assertEquals("Quarterly review", titleFrom("\n\n  Quarterly review  \nmore text"))
    }

    @Test
    fun `shortens a long opening line at a word boundary`() {
        val title = titleFrom(
            "Good morning everyone and welcome to what will be a very long opening sentence"
        )

        assertTrue("should be elided: $title", title.endsWith("…"))
        assertTrue("should be short: ${title.length}", title.length <= 61)
        assertTrue("should not cut mid-word: $title", title.startsWith("Good morning everyone"))
    }

    @Test
    fun `falls back when there is nothing to name it after`() {
        assertEquals("Untitled script", titleFrom("   \n\n  "))
    }
}
