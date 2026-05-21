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
 * CalDAV methods are backward-compatible: decodes existing base64-obfuscated
 * passwords by falling through to legacy decode on failure.
 *
 * PIN methods use a versioned prefix ("v1:") so the format is self-describing;
 * legacy plaintext PINs are handled transparently on verify.
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

    // ─────────────────────────────────────────────────────────────
    // CalDAV password methods (unchanged format — don't break userspace)
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // PIN methods — versioned format, backward-compatible with
    // legacy plaintext storage.
    // Format: "v1:" + base64(IV[12] + ciphertext[n])
    // ─────────────────────────────────────────────────────────────

    /**
     * Encrypt a kiosk PIN for storage.
     * Returns "v1:" + base64(IV + ciphertext), or "" for blank input.
     *
     * Use [verifyPin] for comparison — it handles both encrypted
     * and legacy plaintext formats transparently.
     */
    fun encryptPin(pin: String): String {
        if (pin.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(pin.toByteArray(Charsets.UTF_8))
        return "v1:" + Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Verify a stored PIN against user input.
     *
     * Handles three storage formats transparently:
     *   1. "v1:" + base64(IV + ciphertext) — current encrypted format
     *   2. Legacy base64-obfuscated (pre-encryption migration)
     *   3. Legacy plaintext (no encryption at all)
     *
     * Never throws. Returns false on any error or mismatch.
     */
    fun verifyPin(stored: String, input: String): Boolean {
        if (stored.isBlank() || input.isBlank()) return false

        // Format 1: versioned encrypted blob
        if (stored.startsWith("v1:")) {
            return try {
                val decoded = Base64.decode(stored.substring("v1:".length), Base64.DEFAULT)
                if (decoded.size < IV_LENGTH) return false
                val iv = decoded.copyOfRange(0, IV_LENGTH)
                val ct = decoded.copyOfRange(IV_LENGTH, decoded.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                String(cipher.doFinal(ct), Charsets.UTF_8) == input
            } catch (_: Exception) {
                false
            }
        }

        // Format 2/3: legacy plaintext or base64-obfuscated
        return stored == input
    }
}
