package com.mikczemny.prompter.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream

/**
 * Reads a document the user picked and returns it as prompter-ready text.
 *
 * Files arrive through the storage access framework, so the app needs no
 * storage permission and never sees anything the user did not explicitly hand
 * it. Everything here runs off the main thread — a hundred-page PDF takes real
 * time to extract.
 */
object DocumentImporter {

    /** MIME types offered to the system file picker. */
    val SUPPORTED_MIME_TYPES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/markdown",
    )

    sealed interface Outcome {
        data class Success(val fileName: String, val text: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun import(context: Context, uri: Uri): Outcome {
        val fileName = displayName(context, uri) ?: "document"
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = context.contentResolver.getType(uri).orEmpty()

        // Legacy .doc is a completely different, binary format — worth naming
        // explicitly, because "unsupported file" would send someone hunting for
        // a bug that is really a five-second Save As.
        if (extension == "doc" || mimeType == "application/msword") {
            return Outcome.Failure(
                "Old .doc files aren't supported. Open it in Word and save as .docx."
            )
        }

        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                when {
                    extension == "pdf" || mimeType == "application/pdf" ->
                        reflowWrappedLines(extractPdf(context, stream))

                    extension == "docx" || mimeType.endsWith("wordprocessingml.document") ->
                        DocxExtractor.extractText(stream)

                    else -> tidyParagraphs(stream.readBytes().decodeToString())
                }
            } ?: return Outcome.Failure("Could not open $fileName.")

            if (text.isBlank()) {
                Outcome.Failure(
                    "No text found in $fileName. A scanned PDF holds pictures of " +
                        "words, not words — it would need OCR first."
                )
            } else {
                Outcome.Success(fileName = fileName, text = text)
            }
        } catch (e: IOException) {
            Outcome.Failure(e.message ?: "Could not read $fileName.")
        } catch (e: Exception) {
            Outcome.Failure("Could not read $fileName: ${e.message}")
        }
    }

    private fun extractPdf(context: Context, stream: InputStream): String {
        // PDFBox ships its fonts and glyph tables as Android assets and has to
        // be pointed at them before the first parse.
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(stream).use { document ->
            if (document.isEncrypted) {
                throw IOException("That PDF is password-protected.")
            }
            return PDFTextStripper().getText(document)
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
            }
        return uri.lastPathSegment
    }
}
