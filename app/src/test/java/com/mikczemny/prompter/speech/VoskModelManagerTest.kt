package com.mikczemny.prompter.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The model archive is the app's one untrusted input: it is fetched from a
 * third-party host and handed to native code. These tests pin the two refusals
 * that stop a swapped archive from writing wherever it likes or filling the
 * device.
 */
class VoskModelManagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun writeZip(target: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `unpacks a model archive and strips the top-level folder`() {
        val zip = temp.newFile("model.zip")
        writeZip(
            zip,
            listOf(
                "vosk-model-small-pl-0.22/conf/model.conf" to "sample=16000".toByteArray(),
                "vosk-model-small-pl-0.22/am/final.mdl" to byteArrayOf(1, 2, 3),
            ),
        )
        val out = temp.newFolder("out")

        VoskModelManager.unzipStrippingTopFolder(zip, out)

        assertEquals("sample=16000", File(out, "conf/model.conf").readText())
        assertTrue(File(out, "am/final.mdl").isFile)
    }

    /**
     * Zip Slip: an entry whose path climbs out of the target directory. Without
     * the canonical-path check this writes anywhere the app's uid can reach.
     */
    @Test
    fun `refuses an entry that escapes the target directory`() {
        val zip = temp.newFile("evil.zip")
        writeZip(
            zip,
            listOf("top/../../../escaped.txt" to "owned".toByteArray()),
        )
        val out = temp.newFolder("out2")

        val error = assertThrows(IOException::class.java) {
            VoskModelManager.unzipStrippingTopFolder(zip, out)
        }

        assertTrue(
            "unexpected message: ${error.message}",
            error.message!!.contains("escapes"),
        )
        assertFalse(
            "the escaping entry must not have been written anywhere",
            temp.root.walkTopDown().any { it.name == "escaped.txt" },
        )
    }

    @Test
    fun `refuses an archive with absurdly many entries`() {
        val zip = temp.newFile("many.zip")
        writeZip(
            zip,
            (0..10_001).map { "top/file$it.bin" to byteArrayOf(0) },
        )
        val out = temp.newFolder("out3")

        val error = assertThrows(IOException::class.java) {
            VoskModelManager.unzipStrippingTopFolder(zip, out)
        }

        assertTrue(
            "unexpected message: ${error.message}",
            error.message!!.contains("too many entries"),
        )
    }
}
