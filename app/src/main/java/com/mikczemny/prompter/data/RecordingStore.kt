package com.mikczemny.prompter.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
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

enum class RecordingDestination { APP, CUSTOM }

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

    init {
        // Cache files are unfinished takes (for example after a process kill),
        // never recordings the user chose to keep. Remove stale remnants so
        // sensitive audio/video does not accumulate invisibly.
        File(context.cacheDir, TEMP_DIR).listFiles().orEmpty().forEach { it.delete() }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** App-owned fallback used only if the configured provider becomes unavailable. */
    private val defaultDir: File
        get() = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    /** A fresh temp file to record into, before the keep/discard decision. */
    fun newTempFile(name: String): File =
        File(File(context.cacheDir, TEMP_DIR).apply { mkdirs() }, name)

    fun isConfigured(): Boolean {
        if (prefs.getInt(KEY_SETUP_VERSION, 0) < CURRENT_SETUP_VERSION) return false
        return when (destination()) {
            RecordingDestination.APP -> true
            RecordingDestination.CUSTOM -> {
                val uri = folderUri() ?: return false
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission && it.isWritePermission
                }
            }
            null -> false
        }
    }

    fun destination(): RecordingDestination? = prefs.getString(KEY_DESTINATION, null)
        ?.let { stored ->
            // Migrate the former Camera-folder choice to app storage. That
            // destination split MP4 and WAV across incompatible MediaStore
            // collections and could crash while saving audio on Android 10+.
            if (stored == "CAMERA") RecordingDestination.APP
            else runCatching { RecordingDestination.valueOf(stored) }.getOrNull()
        }
        ?: if (prefs.contains(KEY_TREE)) RecordingDestination.CUSTOM else null

    fun useAppFolder() {
        prefs.edit {
            putString(KEY_DESTINATION, RecordingDestination.APP.name)
            putInt(KEY_SETUP_VERSION, CURRENT_SETUP_VERSION)
            remove(KEY_TREE)
        }
    }

    /** The user-picked destination folder, or null outside custom-folder mode. */
    fun folderUri(): Uri? = prefs.getString(KEY_TREE, null)?.let(Uri::parse)

    /** Persists the chosen folder. Caller must already hold a persistable grant. */
    fun setFolder(uri: Uri) {
        prefs.edit {
            putString(KEY_DESTINATION, RecordingDestination.CUSTOM.name)
            putString(KEY_TREE, uri.toString())
            putInt(KEY_SETUP_VERSION, CURRENT_SETUP_VERSION)
        }
    }

    /** Human-readable name of the current destination, for the settings row. */
    fun folderLabel(): String? {
        return when (destination()) {
            RecordingDestination.APP -> "Prompter/recordings"
            RecordingDestination.CUSTOM -> {
                val uri = folderUri() ?: return null
                DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
            }
            null -> null
        }
    }

    /** Moves a finished temp recording to its destination and returns it. */
    fun save(temp: File): Recording {
        val mime = if (temp.extension.equals("mp4", ignoreCase = true)) {
            "video/mp4"
        } else {
            "audio/x-wav"
        }
        val treeUri = folderUri()
        if (destination() == RecordingDestination.CUSTOM && treeUri != null) {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
            val doc = dir?.createFile(mime, temp.name)
            if (doc != null) {
                val output = context.contentResolver.openOutputStream(doc.uri)
                if (output != null) output.use { out ->
                    temp.inputStream().use { it.copyTo(out) }
                } else {
                    // Do not leave an empty document behind when a provider
                    // accepts createFile but refuses the output stream.
                    doc.delete()
                    return saveToDefaultDirectory(temp)
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
        return saveToDefaultDirectory(temp)
    }

    private fun saveToDefaultDirectory(temp: File): Recording {
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
        val items = if (destination() == RecordingDestination.CUSTOM && treeUri != null) {
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
            "content" -> if (context.contentResolver.delete(recording.uri, null, null) == 0) {
                DocumentFile.fromSingleUri(context, recording.uri)?.delete()
            }
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
        const val KEY_DESTINATION = "destination"
        const val KEY_SETUP_VERSION = "setup_version"
        const val CURRENT_SETUP_VERSION = 2
        const val TEMP_DIR = "recordings"
    }
}
