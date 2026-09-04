package com.mikczemny.prompter.remote

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RemoteSecureServer(
    private val pairingCode: String,
    private val port: Int = DEFAULT_PORT,
) : Closeable {
    private data class Peer(val socket: Socket, val output: DataOutputStream, val key: ByteArray)

    private val running = AtomicBoolean(false)
    private val peers = CopyOnWriteArrayList<Peer>()
    private var serverSocket: ServerSocket? = null

    init {
        require(RemoteProtocol.validPairingCode(pairingCode))
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val ready = CountDownLatch(1)
        thread(name = "remote-secure-server", isDaemon = true) {
            try {
                ServerSocket(port).also {
                    serverSocket = it
                    ready.countDown()
                }.use { server ->
                    while (running.get()) {
                        val socket = server.accept().apply { soTimeout = HANDSHAKE_TIMEOUT_MS }
                        thread(name = "remote-secure-handshake", isDaemon = true) { authenticate(socket) }
                    }
                }
            } catch (_: Exception) {
                // Closing the server socket is the normal shutdown path.
            } finally {
                ready.countDown()
                running.set(false)
            }
        }
        check(ready.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS) && serverSocket != null) {
            "Remote camera server could not start"
        }
    }

    fun publishFrame(jpeg: ByteArray) {
        if (jpeg.isEmpty() || jpeg.size > MAX_FRAME_BYTES) return
        peers.forEach { peer ->
            runCatching {
                val payload = RemoteCrypto.encrypt(peer.key, jpeg)
                synchronized(peer.output) { peer.output.writePacket(payload) }
            }.onFailure { remove(peer) }
        }
    }

    private fun authenticate(socket: Socket) {
        try {
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val keyPair = RemoteCrypto.generateKeyPair()
            output.writeInt(RemoteProtocol.VERSION)
            output.writePacket(keyPair.public.encoded)
            val peerPublicKey = input.readPacket(MAX_PUBLIC_KEY_BYTES)
            val key = RemoteCrypto.deriveSessionKey(keyPair, peerPublicKey, pairingCode)
            val clientProof = RemoteCrypto.decrypt(key, input.readPacket(MAX_AUTH_PACKET_BYTES))
            require(MessageDigest.isEqual(clientProof, CLIENT_PROOF))
            socket.soTimeout = 0
            val peer = Peer(socket, output, key)
            peers += peer
            runCatching { output.writePacket(RemoteCrypto.encrypt(key, SERVER_PROOF)) }
                .onFailure { remove(peer) }
                .getOrThrow()
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private fun remove(peer: Peer) {
        peers.remove(peer)
        runCatching { peer.socket.close() }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        peers.forEach(::remove)
    }

    companion object {
        const val DEFAULT_PORT = 45821
        const val MAX_FRAME_BYTES = 2 * 1024 * 1024
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private const val START_TIMEOUT_MS = 2_000L
        private const val MAX_PUBLIC_KEY_BYTES = 256
        private const val MAX_AUTH_PACKET_BYTES = 256
        private val CLIENT_PROOF = "Prompter client v1".toByteArray(Charsets.US_ASCII)
        private val SERVER_PROOF = "Prompter camera v1".toByteArray(Charsets.US_ASCII)
    }
}

class RemoteSecureClient(
    private val host: String,
    private val pairingCode: String,
    private val port: Int = RemoteSecureServer.DEFAULT_PORT,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var socket: Socket? = null

    init {
        require(RemoteProtocol.validPairingCode(pairingCode))
    }

    fun connect(onFrame: (ByteArray) -> Unit, onConnectionChanged: (Boolean) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        thread(name = "remote-secure-client", isDaemon = true) {
            try {
                Socket().also { socket = it }.use { client ->
                    client.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    client.soTimeout = HANDSHAKE_TIMEOUT_MS
                    val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                    val output = DataOutputStream(BufferedOutputStream(client.getOutputStream()))
                    require(input.readInt() == RemoteProtocol.VERSION)
                    val serverPublicKey = input.readPacket(MAX_PUBLIC_KEY_BYTES)
                    val keyPair = RemoteCrypto.generateKeyPair()
                    output.writePacket(keyPair.public.encoded)
                    val key = RemoteCrypto.deriveSessionKey(keyPair, serverPublicKey, pairingCode)
                    output.writePacket(RemoteCrypto.encrypt(key, CLIENT_PROOF))
                    val serverProof = RemoteCrypto.decrypt(key, input.readPacket(MAX_AUTH_PACKET_BYTES))
                    require(MessageDigest.isEqual(serverProof, SERVER_PROOF))
                    client.soTimeout = READ_TIMEOUT_MS
                    onConnectionChanged(true)
                    while (running.get()) {
                        val encrypted = input.readPacket(MAX_ENCRYPTED_FRAME_BYTES)
                        onFrame(RemoteCrypto.decrypt(key, encrypted))
                    }
                }
            } catch (_: Exception) {
                // The UI presents one connection state for rejection and network loss.
            } finally {
                running.set(false)
                onConnectionChanged(false)
            }
        }
    }

    override fun close() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        fun newPairingCode(): String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_PUBLIC_KEY_BYTES = 256
        private const val MAX_AUTH_PACKET_BYTES = 256
        private const val MAX_ENCRYPTED_FRAME_BYTES = RemoteSecureServer.MAX_FRAME_BYTES + 64
        private val CLIENT_PROOF = "Prompter client v1".toByteArray(Charsets.US_ASCII)
        private val SERVER_PROOF = "Prompter camera v1".toByteArray(Charsets.US_ASCII)
    }
}

private fun DataOutputStream.writePacket(payload: ByteArray) {
    writeInt(payload.size)
    write(payload)
    flush()
}

private fun DataInputStream.readPacket(maxBytes: Int): ByteArray {
    val size = readInt()
    require(size in 1..maxBytes)
    return ByteArray(size).also(::readFully)
}
