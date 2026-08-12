package com.mikczemny.prompter.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** One saved audio recording, wherever it lives (app folder or a picked folder). */
data class Recording(
    val name: String,
    /** Playable/deletable location: a file:// or a SAF content:// document uri. */
    val uri: Uri,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * Where recordings go and how they are listed back.
 *
 * By default recordings land in the app's own external files dir, which needs no
 * permission and is visible in the Files app. The user may instead pick any
 * folder through the Storage Access Framework; when they have, that folder is
 * both the save target and the source the preview screen lists. Keeping the two
 * modes behind one type means the rest of the app never branches on it.
 *
 * The recorder always writes to a private temp file first (WAV needs random
 * access to patch its header); [save] moves the finished file to its home.
 */
class RecordingStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** App-owned fallback folder, used until the user picks one of their own. */
    private val defaultDir: File
        get() = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    /** A fresh temp file to record into, before the keep/discard decision. */
    fun newTempFile(name: String): File =
        File(File(context.cacheDir, "recordings").apply { mkdirs() }, name)

    /** The user-picked destination folder, or null when using the app folder. */
    fun folderUri(): Uri? = prefs.getString(KEY_TREE, null)?.let(Uri::parse)

    /** Persists the chosen folder. Caller must already hold a persistable grant. */
    fun setFolder(uri: Uri?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_TREE) else putString(KEY_TREE, uri.toString())
        }.apply()
    }

    /** Human-readable name of the current destination, for the settings row. */
    fun folderLabel(): String? {
        val uri = folderUri() ?: return null
        return DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
    }

    /** Moves a finished temp recording to its destination and returns it. */
    fun save(temp: File): Recording {
        val treeUri = folderUri()
        val mime = if (temp.extension.equals("mp4", ignoreCase = true)) {
            "video/mp4"
        } else {
            "audio/x-wav"
        }
        if (treeUri != null) {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
            val doc = dir?.createFile(mime, temp.name)
            if (doc != null) {
                context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                    temp.inputStream().use { it.copyTo(out) }
                }
                temp.delete()
                return Recording(
                    name = doc.name ?: temp.name,
                    uri = doc.uri,
                    sizeBytes = doc.length(),
                    lastModified = doc.lastModified(),
                )
            }
            // Fall through to the app folder if the picked one can't be written.
        }
        val dest = File(defaultDir, temp.name)
        temp.copyTo(dest, overwrite = true)
        temp.delete()
        return Recording(dest.name, Uri.fromFile(dest), dest.length(), dest.lastModified())
    }

    /** Discards a temp recording the user chose not to keep. */
    fun discard(temp: File) {
        temp.delete()
    }

    /** Lists saved recordings, newest first, from the active destination. */
    fun list(): List<Recording> {
        val treeUri = folderUri()
        val items = if (treeUri != null) {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            dir.listFiles()
                .filter { it.isFile && isRecording(it.name) }
                .map { Recording(it.name ?: "recording", it.uri, it.length(), it.lastModified()) }
        } else {
            defaultDir.listFiles().orEmpty()
                .filter { it.isFile && isRecording(it.name) }
                .map { Recording(it.name, Uri.fromFile(it), it.length(), it.lastModified()) }
        }
        return items.sortedByDescending { it.lastModified }
    }

    /** Deletes a saved recording, whichever backing store it came from. */
    fun delete(recording: Recording) {
        when (recording.uri.scheme) {
            "content" -> DocumentFile.fromSingleUri(context, recording.uri)?.delete()
            "file" -> recording.uri.path?.let { File(it).delete() }
        }
    }

    private fun isRecording(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return n.endsWith(".wav") || n.endsWith(".mp4")
    }

    private companion object {
        const val PREFS = "recordings"
        const val KEY_TREE = "tree_uri"
    }
}
