package com.mikczemny.prompter.speech

import android.content.Context
import org.vosk.Model
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages the offline Vosk models. Models are NOT bundled in the APK — each is
 * downloaded on demand (once) from the official Alpha Cephei repo into
 * app-internal storage, then loaded from disk. This keeps the APK small and
 * lets the app support any number of markets/languages.
 *
 * After a language's model is downloaded once, that language works fully
 * offline forever.
 */
/** What the model is doing right now, for the UI overlay. */
sealed interface ModelStatus {
    /** Fetching over the network. [fraction] in 0..1, or -1 if size is unknown. */
    data class Downloading(val fraction: Float) : ModelStatus
    /** Unpacking / loading the model from disk (no network). */
    data object Preparing : ModelStatus
}

object VoskModelManager {

    private val loaded = HashMap<String, Model>()

    private fun modelDir(context: Context, lang: Language): File =
        File(File(context.filesDir, "models"), lang.code)

    /** True if the model for [lang] is already on disk and ready to load. */
    fun isModelReady(context: Context, lang: Language): Boolean {
        // A valid Vosk model always contains a conf/ subdirectory.
        return File(modelDir(context, lang), "conf").isDirectory
    }

    /**
     * Ensures the model for [lang] is present (downloading if needed) and
     * returns a loaded [Model]. Blocking — call off the main thread.
     *
     * @param onStatus reports whether we're downloading (with progress) or just
     *   preparing/loading a model that's already on disk.
     */
    @Synchronized
    fun ensureModel(
        context: Context,
        lang: Language,
        onStatus: (ModelStatus) -> Unit = {},
    ): Model {
        loaded[lang.code]?.let { return it }

        val dir = modelDir(context, lang)
        if (!isModelReady(context, lang)) {
            downloadAndUnpack(lang, dir, onStatus)
        }
        onStatus(ModelStatus.Preparing) // loading from disk, no network
        val model = Model(dir.absolutePath)
        loaded[lang.code] = model
        return model
    }

    private fun downloadAndUnpack(lang: Language, dir: File, onStatus: (ModelStatus) -> Unit) {
        onStatus(ModelStatus.Downloading(-1f))
        val tmpZip = File.createTempFile("vosk-${lang.code}", ".zip")
        try {
            val connection = (URL(lang.modelUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${connection.responseCode} pobierając model")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                tmpZip.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        readTotal += n
                        val frac = if (total > 0) readTotal.toFloat() / total else -1f
                        onStatus(ModelStatus.Downloading(frac))
                    }
                }
            }

            onStatus(ModelStatus.Preparing) // unpacking
            // Unpack into a temp dir first, then atomically swap in, so an
            // interrupted download never leaves a half-written model behind.
            val stagingDir = File(dir.parentFile, "${lang.code}.tmp")
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            unzipStrippingTopFolder(tmpZip, stagingDir)

            if (dir.exists()) dir.deleteRecursively()
            if (!stagingDir.renameTo(dir)) {
                // Fallback if rename across the same filesystem fails.
                stagingDir.copyRecursively(dir, overwrite = true)
                stagingDir.deleteRecursively()
            }
        } finally {
            tmpZip.delete()
        }
    }

    private fun unzipStrippingTopFolder(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Strip the leading "vosk-model-.../" folder from every path.
                val relPath = entry.name.substringAfter('/', "")
                if (relPath.isNotEmpty()) {
                    val outFile = File(targetDir, relPath)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zip.copyTo(out) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
