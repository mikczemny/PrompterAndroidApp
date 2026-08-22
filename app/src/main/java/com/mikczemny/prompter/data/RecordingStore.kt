package com.mikczemny.prompter.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

enum class RecordingDestination { CAMERA, CUSTOM }

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

    fun isConfigured(): Boolean = prefs.contains(KEY_DESTINATION) || prefs.contains(KEY_TREE)

    fun destination(): RecordingDestination? = prefs.getString(KEY_DESTINATION, null)
        ?.let { runCatching { RecordingDestination.valueOf(it) }.getOrNull() }
        ?: if (prefs.contains(KEY_TREE)) RecordingDestination.CUSTOM else null

    fun useCameraFolder() {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        prefs.edit {
            putString(KEY_DESTINATION, RecordingDestination.CAMERA.name)
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
        }
    }

    /** Human-readable name of the current destination, for the settings row. */
    fun folderLabel(): String? {
        return when (destination()) {
            RecordingDestination.CAMERA -> "DCIM/Camera"
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
        if (destination() == RecordingDestination.CAMERA && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveToCameraFolder(temp, mime)
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

    private fun saveToCameraFolder(temp: File, mime: String): Recording {
        val collection = if (mime.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, temp.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, CAMERA_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: return saveToDefaultDirectory(temp)
        return try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            val result = Recording(temp.name, uri, temp.length(), System.currentTimeMillis())
            temp.delete()
            result
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            saveToDefaultDirectory(temp)
        }
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
        if (destination() == RecordingDestination.CAMERA && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return listCameraFolder()
        }
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

    private fun listCameraFolder(): List<Recording> {
        val result = mutableListOf<Recording>()
        listOf(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        ).forEach { collection ->
            context.contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                ),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf(CAMERA_PATH, "prompter_%"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    result += Recording(
                        cursor.getString(nameColumn),
                        Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString()),
                        cursor.getLong(sizeColumn),
                        cursor.getLong(dateColumn) * 1000L,
                    )
                }
            }
        }
        return result.sortedByDescending { it.lastModified }
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
        const val TEMP_DIR = "recordings"
        val CAMERA_PATH: String = Environment.DIRECTORY_DCIM + "/Camera/"
    }
}
