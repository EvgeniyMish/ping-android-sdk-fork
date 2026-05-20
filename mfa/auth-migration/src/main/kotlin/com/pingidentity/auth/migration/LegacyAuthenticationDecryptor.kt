/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Standalone decryptor for Legacy ForgeRock SDK's SecuredSharedPreferences.
 *
 * ## How it works
 *
 * The Legacy SDK uses a custom encryption format:
 * 1. Data is wrapped in JSON: `{"type": X, "value": Y}`
 * 2. Converted to UTF-8 bytes
 * 3. Encrypted with AES/GCM using a key from Android KeyStore
 * 4. MAC is prepended for integrity verification
 * 5. Base64 encoded for storage
 *
 * This class reverses that process to extract the original data.
 *
 * @param context Android context for accessing SharedPreferences
 * @param keyAlias The KeyStore alias used to encrypt the data
 */
class LegacyAuthenticationDecryptor(
    private val context: Context,
    private val keyAlias: String = "org.forgerock.android.authenticator.KEYS"
) {

    companion object {
        private const val AES_GCM_NO_PADDING = "AES/GCM/NOPADDING"

        private const val RSA_ECB_OAEP_PADDING = "RSA/ECB/OAEPPadding"

        private const val HMAC_SHA256 = "HmacSHA256"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Initialization vector length in bytes. */
        private const val IV_LENGTH = 12

        // Type codes from Legacy SDK
        private const val STRING_TYPE = 0

        private const val STRING_SET_TYPE = 1
        private const val INT_TYPE = 2
        private const val LONG_TYPE = 3
        private const val FLOAT_TYPE = 4
        private const val BOOLEAN_TYPE = 5
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /** MAC instance for verifying data integrity. */
    private val mac: Mac by lazy {
        Mac.getInstance(HMAC_SHA256).apply {
            val sk = SecretKeySpec(keyAlias.toByteArray(StandardCharsets.UTF_8), HMAC_SHA256)
            init(sk)
        }
    }

    /** MAC output length in bytes. */
    private val macLength: Int
        get() = mac.macLength

    /**
     * Checks if the encryption key exists in the Android KeyStore.
     *
     * @return true if the key exists, false otherwise
     */
    fun keyExists(): Boolean {
        return try {
            keyStore.containsAlias(keyAlias)
        } catch (e: Exception) {
            AuthMigration.logger.e("Error checking key existence", e)
            false
        }
    }

    /**
     * Decrypts all values from a Legacy SDK SharedPreferences file.
     *
     * @param preferenceName The name of the SharedPreferences file (without .xml)
     * @return Map of keys to decrypted JSON string values, or empty map on error
     */
    fun decryptAll(preferenceName: String): Map<String, String> {
        val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val allData = prefs.all
        val decryptedMap = mutableMapOf<String, String>()

        allData.forEach { (key, value) ->
            // Skip the key alias entry itself
            if (key == keyAlias) return@forEach

            try {
                if (value is String) {
                    decryptValue(value)?.let {
                        decryptedMap[key] = it
                    }
                }
            } catch (e: Exception) {
                AuthMigration.logger.w("Failed to decrypt value for key: $key", e)
                // Continue processing other values
            }
        }

        return decryptedMap
    }

    /**
     * Decrypts a single Base64-encoded encrypted value.
     *
     * @param encryptedBase64 The Base64-encoded encrypted string
     * @return The decrypted value (unwrapped from metadata JSON), or null on error
     */
    private fun decryptValue(encryptedBase64: String?): String? {
        if (encryptedBase64.isNullOrBlank()) return null

        return try {
            // Step 1: Base64 decode
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)

            // Step 2: Decrypt the bytes
            val decryptedBytes = decryptBytes(encryptedBytes)

            // Step 3: Convert to UTF-8 string
            val jsonString = String(decryptedBytes, StandardCharsets.UTF_8)

            // Step 4: Parse metadata JSON and extract value
            unwrapValue(jsonString)
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to decrypt value", e)
            null
        }
    }

    /**
     * Decrypts raw encrypted bytes using the Legacy SDK's encryption format.
     *
     * Format: [MAC][IV][EncryptedSecretKey (if RSA)][EncryptedData]
     *
     * @param encryptedData The encrypted byte array
     * @return The decrypted byte array
     */
    private fun decryptBytes(encryptedData: ByteArray): ByteArray {
        // Extract components
        val macFromMessage = encryptedData.copyOfRange(0, macLength)
        val iv = encryptedData.copyOfRange(macLength, macLength + IV_LENGTH)
        val encryptedPayload = encryptedData.copyOfRange(macLength + IV_LENGTH, encryptedData.size)

        // Verify MAC
        val computedMac = mac.doFinal(encryptedPayload)
        if (!computedMac.contentEquals(macFromMessage)) {
            throw SecurityException("MAC signature verification failed")
        }

        // Get the secret key (might be symmetric or asymmetric)
        val symmetricKey = getSecretKey(encryptedPayload)

        // Decrypt using AES/GCM
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val ivParams: AlgorithmParameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, symmetricKey.secretKey, ivParams)

        // Skip the embedded secret key bytes if present
        val dataToDecrypt = encryptedPayload.copyOfRange(
            symmetricKey.embeddedKeySize,
            encryptedPayload.size
        )

        return cipher.doFinal(dataToDecrypt)
    }

    /**
     * Retrieves the secret key from the KeyStore.
     * Handles both symmetric (AES) and asymmetric (RSA) key scenarios.
     *
     * @param encryptedPayload The encrypted payload (may contain embedded key)
     * @return SymmetricKeyInfo containing the key and embedded key size
     */
    private fun getSecretKey(encryptedPayload: ByteArray): SymmetricKeyInfo {
        val entry = keyStore.getEntry(keyAlias, null)
            ?: throw IllegalStateException("Key not found in KeyStore: $keyAlias")

        return when (entry) {
            is KeyStore.SecretKeyEntry -> {
                // Direct AES key from KeyStore
                SymmetricKeyInfo(entry.secretKey, 0)
            }
            is KeyStore.PrivateKeyEntry -> {
                // RSA key - need to extract embedded AES key
                extractEmbeddedSecretKey(entry.privateKey, encryptedPayload)
            }
            else -> {
                throw IllegalStateException("Unsupported key type in KeyStore")
            }
        }
    }

    /**
     * Extracts the embedded AES secret key from encrypted data when RSA was used.
     *
     * Format: [4 bytes: length][encrypted AES key]
     *
     * @param privateKey The RSA private key from KeyStore
     * @param encryptedPayload The payload containing the embedded key
     * @return SymmetricKeyInfo with the decrypted AES key
     */
    private fun extractEmbeddedSecretKey(
        privateKey: PrivateKey,
        encryptedPayload: ByteArray
    ): SymmetricKeyInfo {
        // First 4 bytes contain the length of the encrypted secret key
        val keyLength = ByteBuffer.wrap(encryptedPayload.copyOfRange(0, 4)).int
        val encryptedSecretKey = encryptedPayload.copyOfRange(4, 4 + keyLength)

        // Decrypt the AES key using RSA
        val cipher = Cipher.getInstance(RSA_ECB_OAEP_PADDING)
        cipher.init(
            Cipher.DECRYPT_MODE,
            privateKey,
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT
            )
        )

        val decryptedKeyBytes = cipher.doFinal(encryptedSecretKey)
        val secretKey = SecretKeySpec(decryptedKeyBytes, "AES")

        // Return the key and the size of embedded key data to skip
        return SymmetricKeyInfo(secretKey, 4 + keyLength)
    }

    /**
     * Unwraps a value from the Legacy SDK's metadata JSON format.
     *
     * Format: {"type": <TYPE_CODE>, "value": <ACTUAL_VALUE>}
     *
     * @param jsonString The metadata JSON string
     * @return The unwrapped value as a string
     */
    private fun unwrapValue(jsonString: String): String {
        val json = JSONObject(jsonString)
        val type = json.getInt("type")

        return when (type) {
            STRING_TYPE -> json.getString("value")
            STRING_SET_TYPE -> {
                val array = json.getJSONArray("value")
                val set = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    set.add(array.getString(i))
                }
                // Return as JSON array string
                JSONArray(set).toString()
            }
            INT_TYPE -> json.getInt("value").toString()
            LONG_TYPE -> json.getLong("value").toString()
            FLOAT_TYPE -> json.getDouble("value").toString()
            BOOLEAN_TYPE -> json.getBoolean("value").toString()
            else -> throw IllegalArgumentException("Unknown type code: $type")
        }
    }

    /**
     * Holds secret key and its embedded size in encrypted payload.
     */
    private data class SymmetricKeyInfo(
        val secretKey: SecretKey,
        val embeddedKeySize: Int
    )
}