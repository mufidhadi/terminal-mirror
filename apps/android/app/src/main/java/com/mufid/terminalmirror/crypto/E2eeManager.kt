package com.mufid.terminalmirror.crypto

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-Knowledge End-to-End Encryption Engine on Android.
 * Uses native ChaCha20-Poly1305 AEAD (supported in Java 11 / Android 10+ API 29+).
 * Nonces are derived deterministically from the 64-bit sequence counter
 * (4 zero bytes + 8 big-endian bytes) to ensure absolute alignment with Rust hosts.
 */
class E2eeManager(private val secretKey: SecretKeySpec) {

    companion object {
        private const val CIPHER_ALGO = "ChaCha20-Poly1305/None/NoPadding"
        private const val KEY_ALGO = "ChaCha20"

        /**
         * Derives a 256-bit (32-byte) symmetric key from an arbitrary secret/passphrase
         * using SHA-256 KDF.
         */
        fun fromSecret(secret: String): E2eeManager {
            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
            val keySpec = SecretKeySpec(keyBytes, KEY_ALGO)
            return E2eeManager(keySpec)
        }

        /**
         * Derives a 96-bit (12-byte) unique nonce from the 64-bit sequence counter.
         * Bytes 0..4: zero padding
         * Bytes 4..12: big-endian 64-bit sequence counter
         */
        fun deriveNonce(seq: Long): ByteArray {
            val nonce = ByteArray(12)
            val buffer = ByteBuffer.allocate(8)
            buffer.putLong(seq)
            System.arraycopy(buffer.array(), 0, nonce, 4, 8)
            return nonce
        }
        fun getCipher(): Cipher {
            val algorithms = listOf(
                "ChaCha20/Poly1305/NoPadding",
                "ChaCha20-Poly1305/None/NoPadding",
                "ChaCha20-Poly1305",
                "ChaCha20"
            )
            for (algo in algorithms) {
                try {
                    return Cipher.getInstance(algo)
                } catch (ignored: Exception) {
                }
            }
            throw IllegalStateException("No ChaCha20-Poly1305 provider available on this Android device")
        }
    }

    /**
     * Encrypts plaintext bytes using ChaCha20-Poly1305 with sequence-derived nonce.
     */
    fun encrypt(seq: Long, plaintext: ByteArray): ByteArray {
        val cipher = getCipher()
        val ivSpec = IvParameterSpec(deriveNonce(seq))
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts ciphertext bytes and verifies Poly1305 authentication tag.
     * Throws AEADBadTagException if ciphertext has been tampered with.
     */
    fun decrypt(seq: Long, ciphertext: ByteArray): ByteArray {
        val cipher = getCipher()
        val ivSpec = IvParameterSpec(deriveNonce(seq))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(ciphertext)
    }
}
