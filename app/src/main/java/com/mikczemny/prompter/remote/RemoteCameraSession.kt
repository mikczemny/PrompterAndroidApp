package com.mikczemny.prompter.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.Closeable

class RemoteCameraSession : Closeable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var client: RemoteSecureClient? = null

    var connected by mutableStateOf(false)
        private set
    var frame by mutableStateOf<Bitmap?>(null)
        private set

    fun connect(host: String, pairingCode: String) {
        client?.close()
        connected = false
        client = RemoteSecureClient(host, pairingCode).also { connection ->
            connection.connect(
                onFrame = { bytes ->
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@connect
                    mainHandler.post {
                        frame = decoded
                    }
                },
                onConnectionChanged = { value -> mainHandler.post { connected = value } },
            )
        }
    }

    override fun close() {
        client?.close()
        client = null
        connected = false
        frame?.recycle()
        frame = null
    }
}
