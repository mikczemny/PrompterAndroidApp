package com.mikczemny.prompter.remote

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSecureTransportTest {
    @Test
    fun authenticatedClientReceivesEncryptedFrame() {
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteSecureServer("123456", port)
        val received = CountDownLatch(1)
        var result = ByteArray(0)
        val client = RemoteSecureClient("127.0.0.1", "123456", port)
        try {
            server.start()
            client.connect(
                onFrame = { result = it; received.countDown() },
                onConnectionChanged = { connected ->
                    if (connected) server.publishFrame(byteArrayOf(7, 8, 9))
                },
            )
            assertTrue(received.await(5, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(7, 8, 9), result)
        } finally {
            client.close()
            server.close()
        }
    }
}
