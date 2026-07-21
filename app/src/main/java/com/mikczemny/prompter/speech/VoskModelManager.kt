package com.mikczemny.prompter.speech

import android.content.Context
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream

/**
 * Ensures the offline Vosk model bundled under assets/model-pl is available on
 * the filesystem (Vosk needs a directory path, not an asset stream) and loads
 * it. The first launch unpacks the model into app-internal storage; subsequent
 * launches reuse it.
 */
object VoskModelManager {

    private const val ASSET_MODEL_DIR = "model-pl"
    private const val UNPACKED_DIR_NAME = "vosk-model-pl"

    @Volatile
    private var cachedModel: Model? = null

    /** Loads the model, unpacking from assets on first use. May block on I/O. */
    @Synchronized
    fun loadModel(context: Context): Model {
        cachedModel?.let { return it }

        val targetDir = File(context.filesDir, UNPACKED_DIR_NAME)
        if (!isUnpacked(targetDir)) {
            unpackAsset(context, ASSET_MODEL_DIR, targetDir)
        }

        val model = Model(targetDir.absolutePath)
        cachedModel = model
        return model
    }

    private fun isUnpacked(dir: File): Boolean {
        // A valid Vosk model always contains a conf/ subdirectory.
        return File(dir, "conf").isDirectory
    }

    private fun unpackAsset(context: Context, assetPath: String, targetDir: File) {
        val assets = context.assets
        val children = assets.list(assetPath) ?: emptyArray()

        if (children.isEmpty()) {
            // It's a file: copy it.
            targetDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(targetDir).use { output -> input.copyTo(output) }
            }
            return
        }

        // It's a directory: recurse.
        targetDir.mkdirs()
        for (child in children) {
            unpackAsset(context, "$assetPath/$child", File(targetDir, child))
        }
    }
}
