package com.openlight.cal.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts CalDAV account passwords using AES-256/GCM
 * backed by Android Keystore (hardware-backed on supported devices).
 *
 * Backward-compatible: decodes existing base64-obfuscated passwords
 * by falling through to legacy decode on failure.
 *
 * No telemetry. No proprietary SDKs.
 */
class EncryptedPassword(context: Context) {

    companion object {
        private const val KEY_ALIAS = "openlight_caldav_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val IV_LENGTH = 12       // GCM standard IV size in bytes
    }

    private val key: SecretKey by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt a plaintext password.
     * Returns base64(IV + ciphertext).
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // GCM appends authentication tag to ciphertext automatically
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypt a password that was encrypted with [encrypt].
     * Falls back to legacy base64 decode, then to raw string.
     */
    fun decrypt(encoded: String): String {
        if (encoded.isBlank()) return ""
        // Try AES-GCM first (new format)
        return try {
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            if (decoded.size < IV_LENGTH) throw IllegalArgumentException("Too short for AES-GCM")
            val iv = decoded.copyOfRange(0, IV_LENGTH)
            val ciphertext = decoded.copyOfRange(IV_LENGTH, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            // Fallback: legacy base64 obfuscation
            try {
                String(Base64.decode(encoded, Base64.DEFAULT))
            } catch (_: Exception) {
                encoded // plaintext fallthrough
            }
        }
    }
}
