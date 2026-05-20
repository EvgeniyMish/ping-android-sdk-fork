/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Configuration for legacy authenticator data migration.
 *
 * @property keyAlias The AndroidKeyStore alias used for encrypting SharedPreferences.
 *                    Default: "org.forgerock.android.authenticator.KEYS" (ForgeRock SDK default)
 *                    Custom storage implementations may use different aliases.
 */
data class LegacyAuthenticationConfig(
    val keyAlias: String = DEFAULT_KEY_ALIAS
) {
    companion object {
        /** Default key alias used by ForgeRock Authenticator SDK */
        const val DEFAULT_KEY_ALIAS = "org.forgerock.android.authenticator.KEYS"
    }
}

/**
 * Exported authenticator data from Legacy SDK.
 */
@Serializable
data class ExportedData(
    val accounts: Map<String, String>,
    val mechanisms: Map<String, String>,
    val notifications: Map<String, String>,
    val deviceToken: Map<String, String>,
    val metadata: ExportMetadata
)

/**
 * Metadata about exported authenticator data.
 */
@Serializable
data class ExportMetadata(
    val totalAccounts: Int,
    val totalMechanisms: Int,
    val totalNotifications: Int,
    val hasDeviceToken: Boolean
)

private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"
private const val AUTH_DATA_NOTIFICATIONS = "org.forgerock.android.authenticator.DATA.NOTIFICATIONS"
private const val AUTH_DATA_DEVICE_TOKEN = "org.forgerock.android.authenticator.DATA.DEVICE_TOKEN"


/**
 * Repository for reading and exporting Legacy SDK authenticator data.
 * Supports both encrypted (default storage) and unencrypted (custom storage) data.
 *
 * @param context Android application context
 * @param config Configuration including custom key alias for encrypted storage
 */
class LegacyAuthenticationRepository(
    private val context: Context,
    private val config: LegacyAuthenticationConfig = LegacyAuthenticationConfig()
) {

    private val decryptor = LegacyAuthenticationDecryptor(
        context,
        config.keyAlias
    )

    /**
     * Checks if legacy data exists (either encrypted or unencrypted).
     */
    suspend fun isExists(): Boolean = withContext(Dispatchers.IO) {
        // Check for encrypted data (has encryption key)
        if (decryptor.keyExists()) {
            return@withContext true
        }

        // Check for unencrypted data (custom storage - plain SharedPreferences)
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        return@withContext listOf(
            AUTH_DATA_ACCOUNT,
            AUTH_DATA_MECHANISM,
            AUTH_DATA_NOTIFICATIONS,
            AUTH_DATA_DEVICE_TOKEN
        ).any { prefName ->
            File(prefsDir, "$prefName.xml").exists()
        }
    }

    /**
     * Export all FR Authenticator data from Legacy SDK.
     * Supports both encrypted (default) and unencrypted (custom storage) data.
     */
    suspend fun exportAllData(): ExportedData = withContext(Dispatchers.IO) {
        try {
            // Try encrypted data first (default storage)
            val isEncrypted = decryptor.keyExists()

            val accounts = readPreferences(AUTH_DATA_ACCOUNT, isEncrypted)
            val mechanisms = readPreferences(AUTH_DATA_MECHANISM, isEncrypted)
            val notifications = readPreferences(AUTH_DATA_NOTIFICATIONS, isEncrypted)
            val deviceToken = readPreferences(AUTH_DATA_DEVICE_TOKEN, isEncrypted)

            ExportedData(
                accounts = accounts,
                mechanisms = mechanisms,
                notifications = notifications,
                deviceToken = deviceToken,
                metadata = ExportMetadata(
                    totalAccounts = accounts.size,
                    totalMechanisms = mechanisms.size,
                    totalNotifications = notifications.size,
                    hasDeviceToken = deviceToken.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to export legacy data: ${e.message}", e)
            throw e
        }
    }

    /**
     * Reads SharedPreferences data, handling both encrypted and unencrypted formats.
     */
    private fun readPreferences(preferenceName: String, isEncrypted: Boolean): Map<String, String> {
        // Check if file exists
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFile = File(prefsDir, "$preferenceName.xml")

        if (!prefsFile.exists()) {
            return emptyMap()
        }

        return try {
            if (isEncrypted) {
                // Decrypt encrypted SharedPreferences (default storage)
                AuthMigration.logger.d("Reading encrypted data from $preferenceName")
                decryptor.decryptAll(preferenceName)
            } else {
                // Read plain SharedPreferences (custom storage)
                AuthMigration.logger.d("Reading unencrypted data from $preferenceName")
                readPlainSharedPreferences(preferenceName)
            }
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to read $preferenceName: ${e.message}", e)
            emptyMap()
        }
    }

    /**
     * Reads plain (unencrypted) SharedPreferences used by custom storage.
     */
    private fun readPlainSharedPreferences(preferenceName: String): Map<String, String> {
        val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val result = mutableMapOf<String, String>()

        prefs.all.forEach { (key, value) ->
            if (value is String) {
                result[key] = value
            }
        }

        AuthMigration.logger.d("Read ${result.size} entries from plain SharedPreferences: $preferenceName")
        return result
    }

    /**
     * Delete legacy data after successful migration.
     * Handles both encrypted (default storage) and unencrypted (custom storage) data.
     */
    suspend fun deleteLegacyData() = withContext(Dispatchers.IO) {
        // Delete SharedPreferences files (works for both encrypted and unencrypted)
        context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
        context.deleteSharedPreferences(AUTH_DATA_MECHANISM)
        context.deleteSharedPreferences(AUTH_DATA_NOTIFICATIONS)
        context.deleteSharedPreferences(AUTH_DATA_DEVICE_TOKEN)

        // Delete encryption key if it exists (only for default storage)
        try {
            if (decryptor.keyExists()) {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(config.keyAlias)
                AuthMigration.logger.d("Deleted legacy encryption key: ${config.keyAlias}")
            }
        } catch (e: Exception) {
            AuthMigration.logger.w("Failed to delete encryption key (may not exist): ${e.message}")
        }
    }
}