package com.mikczemny.prompter.data

import java.io.File
import java.util.UUID

/** A script saved on the device. [updatedAt] is epoch millis. */
data class SavedScript(
    val id: String,
    val title: String,
    val text: String,
    val updatedAt: Long,
)

/**
 * Stores scripts as plain files under [directory], one file per script: the
 * first line is the title, everything after it is the body.
 *
 * There is no database and no serialisation library here on purpose. Scripts
 * are text, a handful of them at most, and the only metadata worth keeping is a
 * title and a timestamp — the filesystem already records the latter. Keeping it
 * to files means the store is a plain object testable on the JVM, and a script
 * stays readable with any text editor if anything ever goes wrong.
 */
class ScriptStore(private val directory: File) {

    /** Newest first, since the last thing edited is nearly always the next thing wanted. */
    fun list(): List<SavedScript> {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            ?: return emptyList()

        return files
            .mapNotNull { file -> runCatching { read(file) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }

    fun save(text: String, title: String = titleFrom(text), id: String? = null): SavedScript {
        directory.mkdirs()
        val scriptId = id ?: UUID.randomUUID().toString()
        val file = File(directory, scriptId + EXTENSION)
        // The title occupies the first line, so any newlines inside it would
        // silently become part of the body on the way back in.
        val safeTitle = title.replace(NEWLINES, " ").trim().ifEmpty { UNTITLED }
        file.writeText(safeTitle + "\n" + text)
        return SavedScript(scriptId, safeTitle, text, file.lastModified())
    }

    fun delete(id: String) {
        File(directory, id + EXTENSION).delete()
    }

    private fun read(file: File): SavedScript {
        val content = file.readText()
        val split = content.indexOf('\n')
        val title = if (split >= 0) content.substring(0, split) else content
        val text = if (split >= 0) content.substring(split + 1) else ""
        return SavedScript(
            id = file.name.removeSuffix(EXTENSION),
            title = title.ifEmpty { UNTITLED },
            text = text,
            updatedAt = file.lastModified(),
        )
    }

    private companion object {
        const val EXTENSION = ".script"
        const val UNTITLED = "Untitled script"
        val NEWLINES = Regex("[\\r\\n]+")
    }
}

/** Longer than this and a title stops being scannable in a list. */
private const val MAX_TITLE_LENGTH = 60

/**
 * Uses the script's opening line as its name. People title their scripts by
 * starting them, and asking for a name before saving is friction nobody wants
 * while setting up a shoot.
 */
fun titleFrom(text: String): String {
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isEmpty()) return "Untitled script"
    if (firstLine.length <= MAX_TITLE_LENGTH) return firstLine
    // Cut at a word boundary so the title does not end mid-word.
    val cut = firstLine.take(MAX_TITLE_LENGTH)
    val lastSpace = cut.lastIndexOf(' ')
    return (if (lastSpace > MAX_TITLE_LENGTH / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
}
