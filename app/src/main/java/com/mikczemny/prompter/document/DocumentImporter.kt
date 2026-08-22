package com.mikczemny.prompter.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mikczemny.prompter.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
        val fileName = displayName(context, uri)
            ?: context.getString(R.string.doc_fallback_name)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = context.contentResolver.getType(uri).orEmpty()

        // Legacy .doc is a completely different, binary format — worth naming
        // explicitly, because "unsupported file" would send someone hunting for
        // a bug that is really a five-second Save As.
        if (extension == "doc" || mimeType == "application/msword") {
            return Outcome.Failure(context.getString(R.string.doc_error_legacy_doc))
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
            } ?: return Outcome.Failure(
                context.getString(R.string.doc_error_could_not_open, fileName)
            )

            if (text.isBlank()) {
                Outcome.Failure(context.getString(R.string.doc_error_no_text, fileName))
            } else {
                Outcome.Success(fileName = fileName, text = text)
            }
        } catch (e: DocumentException) {
            // A recognised failure from an extractor: resolve its reason to a
            // localized message here, where the app resources live.
            Outcome.Failure(context.getString(messageFor(e.reason)))
        } catch (e: Exception) {
            Outcome.Failure(context.getString(R.string.doc_error_could_not_read, fileName))
        }
    }

    private fun messageFor(reason: DocumentException.Reason): Int = when (reason) {
        DocumentException.Reason.PDF_PASSWORD_PROTECTED -> R.string.doc_error_pdf_protected
        DocumentException.Reason.NOT_A_WORD_DOCUMENT -> R.string.doc_error_not_word
        DocumentException.Reason.UNREADABLE_WORD_DOCUMENT -> R.string.doc_error_word_unreadable
    }

    private fun extractPdf(context: Context, stream: InputStream): String {
        // PDFBox ships its fonts and glyph tables as Android assets and has to
        // be pointed at them before the first parse.
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(stream).use { document ->
            if (document.isEncrypted) {
                throw DocumentException(DocumentException.Reason.PDF_PASSWORD_PROTECTED)
            }
            return PDFTextStripper().getText(document)
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return uri.lastPathSegment
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getString(0)
        }
        return uri.lastPathSegment
    }
}
