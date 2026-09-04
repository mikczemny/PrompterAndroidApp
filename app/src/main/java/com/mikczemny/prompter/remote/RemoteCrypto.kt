package com.mikczemny.prompter.remote

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Session crypto for camera traffic. ECDH provides a unique secret for each
 * connection; the user-visible pairing code authenticates that exchange.
 */
object RemoteCrypto {
    private val random = SecureRandom()

    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(256, random)
    }.generateKeyPair()

    fun deriveSessionKey(
        ownKeyPair: KeyPair,
        peerPublicKey: ByteArray,
        pairingCode: String,
    ): ByteArray {
        require(RemoteProtocol.validPairingCode(pairingCode))
        require(peerPublicKey.size in MIN_PUBLIC_KEY_BYTES..MAX_PUBLIC_KEY_BYTES)
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicKey))
        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(ownKeyPair.private)
            doPhase(peer, true)
            generateSecret()
        }
        val salt = MessageDigest.getInstance("SHA-256").digest(pairingCode.toByteArray(Charsets.US_ASCII))
        return hkdf(sharedSecret, salt, SESSION_INFO, AES_KEY_BYTES)
    }

    fun encrypt(key: ByteArray, plainText: ByteArray): ByteArray {
        require(key.size == AES_KEY_BYTES)
        require(plainText.size <= MAX_PLAIN_TEXT_BYTES)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipherText = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            doFinal(plainText)
        }
        return nonce + cipherText
    }

    fun decrypt(key: ByteArray, payload: ByteArray): ByteArray {
        require(key.size == AES_KEY_BYTES)
        require(payload.size in (NONCE_BYTES + TAG_BYTES)..MAX_ENCRYPTED_BYTES)
        val nonce = payload.copyOfRange(0, NONCE_BYTES)
        val cipherText = payload.copyOfRange(NONCE_BYTES, payload.size)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            doFinal(cipherText)
        }
    }

    private fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance(HMAC).run {
            init(SecretKeySpec(salt, HMAC))
            doFinal(input)
        }
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = Mac.getInstance(HMAC).run {
                init(SecretKeySpec(extract, HMAC))
                update(previous)
                update(info)
                update(counter.toByte())
                doFinal()
            }
            val count = minOf(previous.size, length - offset)
            previous.copyInto(output, offset, 0, count)
            offset += count
            counter++
        }
        return output
    }

    private const val HMAC = "HmacSHA256"
    private const val AES_KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_BITS = TAG_BYTES * 8
    private const val MIN_PUBLIC_KEY_BYTES = 64
    private const val MAX_PUBLIC_KEY_BYTES = 256
    private const val MAX_PLAIN_TEXT_BYTES = 2 * 1024 * 1024
    private const val MAX_ENCRYPTED_BYTES = MAX_PLAIN_TEXT_BYTES + NONCE_BYTES + TAG_BYTES
    private val SESSION_INFO = "Prompter Remote Camera v1".toByteArray(Charsets.US_ASCII)
}
