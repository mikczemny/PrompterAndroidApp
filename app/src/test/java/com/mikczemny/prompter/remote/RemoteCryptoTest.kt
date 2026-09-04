package com.mikczemny.prompter.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.AEADBadTagException

class RemoteCryptoTest {
    @Test
    fun peersDeriveSameKeyAndDecryptFrame() {
        val camera = RemoteCrypto.generateKeyPair()
        val prompter = RemoteCrypto.generateKeyPair()
        val cameraKey = RemoteCrypto.deriveSessionKey(camera, prompter.public.encoded, "123456")
        val prompterKey = RemoteCrypto.deriveSessionKey(prompter, camera.public.encoded, "123456")
        assertArrayEquals(cameraKey, prompterKey)

        val frame = ByteArray(4096) { (it % 251).toByte() }
        assertArrayEquals(frame, RemoteCrypto.decrypt(prompterKey, RemoteCrypto.encrypt(cameraKey, frame)))
    }

    @Test(expected = AEADBadTagException::class)
    fun wrongPairingCodeCannotDecrypt() {
        val camera = RemoteCrypto.generateKeyPair()
        val prompter = RemoteCrypto.generateKeyPair()
        val cameraKey = RemoteCrypto.deriveSessionKey(camera, prompter.public.encoded, "123456")
        val wrongKey = RemoteCrypto.deriveSessionKey(prompter, camera.public.encoded, "654321")
        RemoteCrypto.decrypt(wrongKey, RemoteCrypto.encrypt(cameraKey, byteArrayOf(1, 2, 3)))
    }

    @Test
    fun eachEncryptedPayloadUsesUniqueNonce() {
        val first = RemoteCrypto.generateKeyPair()
        val second = RemoteCrypto.generateKeyPair()
        val key = RemoteCrypto.deriveSessionKey(first, second.public.encoded, "123456")
        assertFalse(RemoteCrypto.encrypt(key, byteArrayOf(1)).contentEquals(RemoteCrypto.encrypt(key, byteArrayOf(1))))
    }
}
