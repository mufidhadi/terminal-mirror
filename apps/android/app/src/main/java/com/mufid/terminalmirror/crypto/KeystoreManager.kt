package com.mufid.terminalmirror.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

class KeystoreManager {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "terminal_mirror_key_"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Retrieves or generates a hardware-backed asymmetric key pair for the device.
     */
    fun getOrCreateDeviceKeyPair(alias: String = "primary_device"): KeyPair {
        val fullAlias = "$KEY_ALIAS_PREFIX$alias"

        if (keyStore.containsAlias(fullAlias)) {
            val entry = keyStore.getEntry(fullAlias, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                return KeyPair(entry.certificate.publicKey, entry.privateKey)
            }
        }

        return generateHardwareKeyPair(fullAlias)
    }

    private fun generateHardwareKeyPair(fullAlias: String): KeyPair {
        val kpg: KeyPairGenerator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) native KeyMint HAL v2 with Curve25519
            try {
                KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
            } catch (e: Exception) {
                // Fallback to standard EC P-256 if device OEM HAL omits Curve25519
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            }
        } else {
            // Android 10-12: Standard Hardware EC P-256
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        }

        val spec = KeyGenParameterSpec.Builder(
            fullAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).run {
            if (kpg.algorithm == KeyProperties.KEY_ALGORITHM_EC) {
                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            }
            setUserAuthenticationRequired(false) // Hardware protected without biometric lock on every frame
            build()
        }

        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    /**
     * Exports the device public key in Base64 string format for QR code pairing.
     */
    fun exportPublicKeyBase64(alias: String = "primary_device"): String {
        val keyPair = getOrCreateDeviceKeyPair(alias)
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }
}
