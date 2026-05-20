/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
import com.pingidentity.mfa.push.PushType
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.migration.MigrationStep
import com.pingidentity.migration.MigrationStepResult.CONTINUE
import com.pingidentity.migration.MigrationStepResult.ABORT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Date

private const val OTP_AUTH = "otpauth"
private const val TOTP = "totp"
private const val HOTP = "hotp"
private const val PUSH = "push"
private const val PUSH_AUTH = "pushauth"
private const val ACCOUNTS = "accounts"
private const val MECHANISMS = "mechanisms"
private const val NOTIFICATIONS = "notifications"
private const val DEVICE_TOKEN = "deviceToken"
/** Push storage instance shared across migration steps. */
private lateinit var pushStorage: SQLPushStorage
/** OATH storage instance shared across migration steps. */
private lateinit var oathStorage: SQLOathStorage
/** Build the json object with non-null fields  */
private val json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
    explicitNulls = false
}


/**
 * Step 1: Export legacy authenticator data from Legacy SDK.
 *
 * Supports both:
 * - Default storage (encrypted SharedPreferences)
 * - Custom storage (unencrypted SharedPreferences)
 *
 * The repository automatically detects which format is in use.
 * Uses AuthMigration.config for custom key alias configuration.
 */
val step1 = MigrationStep(
    description = "Export legacy authenticator data"
) {
    logger.i("Checking for legacy authenticator data")
    val repo = LegacyAuthenticationRepository(context, AuthMigration.config)

    if (!repo.isExists()) {
        logger.i("No legacy repository found, skipping migration")
        return@MigrationStep ABORT
    }
    logger.i("Legacy repository exists, proceeding with migration")

    val exportedData = repo.exportAllData()
    logger.i("Successfully exported ${exportedData.accounts.size} accounts, ${exportedData.mechanisms.size} mechanisms, ${exportedData.notifications.size} notifications, ${exportedData.deviceToken.size} device tokens")

    val accounts = exportedData.accounts
    val mechanisms = exportedData.mechanisms
    val notifications = exportedData.notifications
    val deviceToken = exportedData.deviceToken

    if (accounts.isEmpty() && mechanisms.isEmpty() && notifications.isEmpty() && deviceToken.isEmpty()) {
        logger.i("No data found in legacy repository, skipping migration")
        return@MigrationStep ABORT
    }

    state[ACCOUNTS] = accounts
    state[MECHANISMS] = mechanisms
    state[NOTIFICATIONS] = notifications
    state[DEVICE_TOKEN] = deviceToken

    CONTINUE
}

/**
 * Step 2: Migrate OATH and Push mechanisms to new storage.
 */
val step2 = MigrationStep(
    description = "Migrate mechanisms to OATH and Push storage"
) {
    logger.i("Starting mechanisms migration to new storage")

    val mechanisms = getValue<Map<String, String>>(MECHANISMS) ?: run {
        logger.w("No mechanisms found in state, skipping")
        return@MigrationStep CONTINUE
    }

    val accounts = getValue<Map<String, String>>(ACCOUNTS) ?: emptyMap()

    // Safely check and prepare database environment before migration
    prepareDatabase(
        context = this@MigrationStep.context,
        logger = logger,
    )

    // Create storage instances with destructive recovery enabled for migration
    // This allows recovery from corrupted databases that may occur if app is force-closed during migration
    oathStorage = SQLOathStorage {
        context = this@MigrationStep.context
        allowDestructiveRecovery = true
        backupOnError = true
        autoRestoreFromBackup = true
    }
    oathStorage.initializeDatabase()
    pushStorage = SQLPushStorage {
        context = this@MigrationStep.context
        allowDestructiveRecovery = true
        backupOnError = true
        autoRestoreFromBackup = true
    }
    pushStorage.initializeDatabase()


    var oathCount = 0
    var pushCount = 0

    mechanisms.forEach { (mechanismId, mechanismJson) ->
        try {
            val mechanismData = json.parseToJsonElement(mechanismJson).jsonObject
            when (val type = mechanismData["type"]?.jsonPrimitive?.content) {
                OTP_AUTH, TOTP, HOTP -> {
                    // Migrate OATH/TOTP mechanism
                    val mechanismIdValue = mechanismData["mechanismUID"]?.jsonPrimitive?.content
                        ?: mechanismData["id"]?.jsonPrimitive?.content
                        ?: mechanismId

                    // First extract issuer and accountName from mechanism to construct Account ID
                    // Account ID format: issuer + "-" + accountName (from legacy Account.java)
                    val mechanismIssuer = mechanismData["issuer"]?.jsonPrimitive?.content ?: ""
                    val mechanismAccountName = mechanismData["accountName"]?.jsonPrimitive?.content ?: ""
                    val accountId = "$mechanismIssuer-$mechanismAccountName"

                    // Find associated account using the constructed Account ID
                    val accountJson = accounts[accountId]
                    val accountData = accountJson?.let { json.parseToJsonElement(it).jsonObject }

                    // Account-only fields (no fallback to mechanism)
                    val issuer = accountData?.get("issuer")?.jsonPrimitive?.content ?: mechanismIssuer
                    val accountName = accountData?.get("accountName")?.jsonPrimitive?.content ?: mechanismAccountName
                    val displayIssuer = accountData?.get("displayIssuer")?.jsonPrimitive?.content ?: issuer
                    val displayAccountName = accountData?.get("displayAccountName")?.jsonPrimitive?.content ?: accountName
                    val imageURL = accountData?.get("imageURL")?.jsonPrimitive?.content
                    val backgroundColor = accountData?.get("backgroundColor")?.jsonPrimitive?.content
                    val policies = accountData?.get("policies")?.jsonPrimitive?.content
                    val lockingPolicy = accountData?.get("lockingPolicy")?.jsonPrimitive?.content
                    val isLocked = accountData?.get("lock")?.jsonPrimitive?.content?.toBoolean() ?: false
                    val createdAt = accountData?.get("timeAdded")?.jsonPrimitive?.content?.toLongOrNull()
                        ?: mechanismData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull()

                    // Mechanism-only fields (no fallback to account)
                    val userId = mechanismData["uid"]?.jsonPrimitive?.content
                    val resourceId = mechanismData["resourceId"]?.jsonPrimitive?.content

                    val oathCredential = OathCredential(
                        id = mechanismIdValue,  // Use mechanismUID as the credential ID
                        userId = userId,
                        resourceId = resourceId,
                        issuer = issuer,
                        displayIssuer = displayIssuer,
                        accountName = accountName,
                        displayAccountName = displayAccountName,
                        oathType = OathType.fromString(mechanismData["oathType"]?.jsonPrimitive?.content ?: TOTP),
                        secret = mechanismData["secret"]?.jsonPrimitive?.content ?: "",
                        oathAlgorithm = when (mechanismData["algorithm"]?.jsonPrimitive?.content) {
                            "sha256", "SHA256" -> OathAlgorithm.SHA256
                            "sha512", "SHA512" -> OathAlgorithm.SHA512
                            else -> OathAlgorithm.SHA1
                        },
                        digits = mechanismData["digits"]?.jsonPrimitive?.content?.toIntOrNull() ?: 6,
                        period = mechanismData["period"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30,
                        counter = mechanismData["counter"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        createdAt = createdAt?.let { Date(it) } ?: Date(),
                        imageURL = imageURL,
                        backgroundColor = backgroundColor,
                        policies = policies,
                        lockingPolicy = lockingPolicy,
                        isLocked = isLocked
                    )

                    oathStorage.storeOathCredential(oathCredential)
                    oathCount++
                    logger.d("Migrated OATH credential: $issuer - $accountName")
                }

                PUSH, PUSH_AUTH -> {
                    // Migrate Push mechanism
                    val mechanismIdValue = mechanismData["mechanismUID"]?.jsonPrimitive?.content
                        ?: mechanismData["id"]?.jsonPrimitive?.content
                        ?: mechanismId

                    // First extract issuer and accountName from mechanism to construct Account ID
                    // Account ID format: issuer + "-" + accountName (from legacy Account.java)
                    val mechanismIssuer = mechanismData["issuer"]?.jsonPrimitive?.content ?: ""
                    val mechanismAccountName = mechanismData["accountName"]?.jsonPrimitive?.content ?: ""
                    val accountId = "$mechanismIssuer-$mechanismAccountName"

                    // Find associated account using the constructed Account ID
                    val accountJson = accounts[accountId]
                    val accountData = accountJson?.let { json.parseToJsonElement(it).jsonObject }

                    // Account-only fields (no fallback to mechanism)
                    val issuer = accountData?.get("issuer")?.jsonPrimitive?.content ?: mechanismIssuer
                    val accountName = accountData?.get("accountName")?.jsonPrimitive?.content ?: mechanismAccountName
                    val displayIssuer = accountData?.get("displayIssuer")?.jsonPrimitive?.content ?: issuer
                    val displayAccountName = accountData?.get("displayAccountName")?.jsonPrimitive?.content ?: accountName
                    val imageURL = accountData?.get("imageURL")?.jsonPrimitive?.content
                    val backgroundColor = accountData?.get("backgroundColor")?.jsonPrimitive?.content
                    val policies = accountData?.get("policies")?.jsonPrimitive?.content
                    val lockingPolicy = accountData?.get("lockingPolicy")?.jsonPrimitive?.content
                    val isLocked = accountData?.get("lock")?.jsonPrimitive?.content?.toBoolean() ?: false
                    val createdAt = accountData?.get("timeAdded")?.jsonPrimitive?.content?.toLongOrNull()
                        ?: mechanismData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull()

                    // Mechanism-only fields (no fallback to account)
                    val userId = mechanismData["uid"]?.jsonPrimitive?.content
                    val resourceId = mechanismData["resourceId"]?.jsonPrimitive?.content
                        ?: mechanismIdValue  // Fallback to mechanismUID if resourceId not present
                    val platform = mechanismData["platform"]?.jsonPrimitive?.content ?: "PING_AM"

                    // Extract base endpoint (remove query parameters like ?_action=authenticate)
                    val rawEndpoint = mechanismData["authenticationEndpoint"]?.jsonPrimitive?.content
                        ?: mechanismData["registrationEndpoint"]?.jsonPrimitive?.content
                        ?: mechanismData["endpoint"]?.jsonPrimitive?.content
                        ?: ""
                    val serverEndpoint = if (rawEndpoint.contains("?")) {
                        rawEndpoint.substringBefore("?")
                    } else {
                        rawEndpoint
                    }

                    val pushCredential = PushCredential(
                        id = mechanismIdValue,  // Use mechanismUID as the credential ID
                        userId = userId,
                        resourceId = resourceId,
                        issuer = issuer,
                        displayIssuer = displayIssuer,
                        accountName = accountName,
                        displayAccountName = displayAccountName,
                        serverEndpoint = serverEndpoint,
                        sharedSecret = mechanismData["secret"]?.jsonPrimitive?.content ?: "",
                        createdAt = createdAt?.let { Date(it) } ?: Date(),
                        imageURL = imageURL,
                        backgroundColor = backgroundColor,
                        policies = policies,
                        lockingPolicy = lockingPolicy,
                        isLocked = isLocked,
                        platform = platform
                    )

                    pushStorage.storePushCredential(pushCredential)
                    pushCount++
                    logger.d("Migrated Push credential: $issuer - $accountName")
                }

                else -> {
                    logger.w("Unknown mechanism type: $type")
                }
            }
        } catch (e: Exception) {
            logger.e("Failed to migrate mechanism $mechanismId", e)
        }
    }

    logger.i("Migrated $oathCount OATH credentials and $pushCount Push credentials")

    CONTINUE
}

/**
 * Step 3: Migrate push notifications to Push storage.
 */
val step3 = MigrationStep(
    description = "Migrate push notifications to Push storage"
) {
    logger.i("Starting push notifications migration")

    val notifications = getValue<Map<String, String>>(NOTIFICATIONS) ?: run {
        logger.w("No notifications found in state, skipping")
        return@MigrationStep CONTINUE
    }

    var count = 0

    notifications.forEach { (notificationId, notificationJson) ->
        try {
            val notificationData = json.parseToJsonElement(notificationJson).jsonObject

            val pushNotification = PushNotification(
                id = notificationId,
                credentialId = notificationData["credentialId"]?.jsonPrimitive?.content ?: "",
                ttl = notificationData["ttl"]?.jsonPrimitive?.content?.toIntOrNull() ?: 120,
                messageId = notificationData["messageId"]?.jsonPrimitive?.content ?: notificationId,
                messageText = notificationData["messageText"]?.jsonPrimitive?.content
                    ?: notificationData["message"]?.jsonPrimitive?.content,
                customPayload = notificationData["customPayload"]?.jsonPrimitive?.content,
                challenge = notificationData["challenge"]?.jsonPrimitive?.content ?: "",
                numbersChallenge = notificationData["numbersChallenge"]?.jsonPrimitive?.content,
                loadBalancer = notificationData["loadBalancer"]?.jsonPrimitive?.content
                    ?: notificationData["amlbCookie"]?.jsonPrimitive?.content,
                contextInfo = notificationData["contextInfo"]?.jsonPrimitive?.content,
                pushType = notificationData["pushType"]?.jsonPrimitive?.content?.let { type ->
                    try {
                        PushType.fromString(type)
                    } catch (e: Exception) {
                        PushType.DEFAULT
                    }
                } ?: PushType.DEFAULT,
                createdAt = Date(notificationData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: notificationData["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: System.currentTimeMillis()),
                sentAt = notificationData["sentAt"]?.jsonPrimitive?.content?.toLongOrNull()?.let { Date(it) },
                respondedAt = notificationData["respondedAt"]?.jsonPrimitive?.content?.toLongOrNull()?.let { Date(it) },
                additionalData = notificationData["additionalData"]?.jsonPrimitive?.content?.let { dataStr ->
                    try {
                        json.decodeFromString<Map<String, Any>>(dataStr)
                    } catch (e: Exception) {
                        null
                    }
                },
                approved = notificationData["approved"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                pending = notificationData["pending"]?.jsonPrimitive?.content?.toBoolean() ?: true
            )

            pushStorage.storePushNotification(pushNotification)
            count++
            logger.d("Migrated push notification: $notificationId")
        } catch (e: Exception) {
            logger.e("Failed to migrate notification $notificationId", e)
        }
    }

    logger.i("Migrated $count push notifications")

    CONTINUE
}

/**
 * Step 4: Migrate device token to Push storage.
 */
val step4 = MigrationStep(
    description = "Migrate device token to Push storage"
) {
    logger.i("Starting device token migration")

    val deviceTokenMap = getValue<Map<String, String>>(DEVICE_TOKEN) ?: run {
        logger.w("No device token found in state, skipping")
        return@MigrationStep CONTINUE
    }

    deviceTokenMap.forEach { (_, tokenJson) ->
        try {
            val tokenData = json.parseToJsonElement(tokenJson).jsonObject

            val pushDeviceToken = PushDeviceToken(
                id = tokenData["id"]?.jsonPrimitive?.content ?: "",
                tokenId = tokenData["deviceToken"]?.jsonPrimitive?.content
                    ?: tokenData["token"]?.jsonPrimitive?.content
                    ?: tokenData["tokenId"]?.jsonPrimitive?.content
                    ?: "",
                createdAt = Date(tokenData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()),
            )

            pushStorage.storePushDeviceToken(pushDeviceToken)
            logger.i("Migrated device token successfully")
        } catch (e: Exception) {
            logger.e("Failed to migrate device token", e)
        }
    }

    CONTINUE
}

/**
 * Step 5: Cleanup legacy authenticator data.
 */
val step5 = MigrationStep(
    description = "Cleanup legacy authenticator data"
) {
    logger.i("Cleaning up legacy authenticator data")

    try {
        val repo = LegacyAuthenticationRepository(context, AuthMigration.config)
        repo.deleteLegacyData()
        logger.i("Successfully deleted legacy authenticator data")
        oathStorage.closeDatabase()
        logger.i("Successfully closed oath storage database")
        pushStorage.closeDatabase()
        logger.i("Successfully closed push storage database")
    } catch (e: Exception) {
        logger.e("Failed to cleanup legacy data", e)
        // Don't fail the migration if cleanup fails
    }

    CONTINUE
}

/**
 * Prepares the database environment for migration by safely handling any existing database files.
 *
 * @param context The Android context used to access database paths and storage APIs.
 * @param logger The logger instance for tracking database operations and decisions.
 *
 * @see SQLOathStorage
 * @see SQLPushStorage
 */
private suspend fun prepareDatabase(
    context: Context,
    logger: Logger,
) {
    logger.i("Checking existing databases before migration")

    // Check OATH database
    val oathDbPath = context.getDatabasePath("pingidentity_oath.db")
    if (oathDbPath.exists()) {
        logger.i("Found existing OATH database at: ${oathDbPath.absolutePath}")

        val shouldDeleteOath = try {
            // Try to open the database with current passphrase to check if it's valid
            val testOathStorage = SQLOathStorage {
                this.context = context
                allowDestructiveRecovery = false // Don't allow auto-recovery during check
            }

            testOathStorage.initializeDatabase()
            val existingCredentials = testOathStorage.getAllOathCredentials()
            testOathStorage.closeDatabase()

            if (existingCredentials.isNotEmpty()) {
                logger.i("OATH database contains ${existingCredentials.size} existing credentials - preserving database")
                false // Keep database with existing data
            } else {
                logger.i("OATH database is empty - deleting to avoid passphrase conflicts")
                true // Delete empty database to avoid conflicts
            }
        } catch (e: Exception) {
            // Database exists but cannot be opened with current passphrase
            logger.w("OATH database exists but cannot be opened (likely encrypted with different passphrase): ${e.message}")
            logger.i("Will delete incompatible OATH database to allow migration to proceed")
            true // Delete incompatible database
        }

        if (shouldDeleteOath) {
            try {
                context.deleteDatabase("pingidentity_oath.db")
                logger.i("Deleted incompatible OATH database")
            } catch (e: Exception) {
                logger.e("Failed to delete OATH database: ${e.message}", e)
            }
        }
    } else {
        logger.i("No existing OATH database found - will create fresh database")
    }

    // Check Push database
    val pushDbPath = context.getDatabasePath("pingidentity_push.db")
    if (pushDbPath.exists()) {
        logger.i("Found existing Push database at: ${pushDbPath.absolutePath}")

        val shouldDeletePush = try {
            // Try to open the database with current passphrase to check if it's valid
            val testPushStorage = SQLPushStorage {
                this.context = context
                allowDestructiveRecovery = false // Don't allow auto-recovery during check
            }

            testPushStorage.initializeDatabase()
            val existingCredentials = testPushStorage.getAllPushCredentials()
            val existingNotifications = testPushStorage.getAllPushNotifications()
            val existingTokens = testPushStorage.getAllPushDeviceTokens()
            testPushStorage.closeDatabase()

            val totalItems = existingCredentials.size + existingNotifications.size + existingTokens.size

            if (totalItems > 0) {
                logger.i("Push database contains existing data (${existingCredentials.size} credentials, ${existingNotifications.size} notifications, ${existingTokens.size} tokens) - preserving database")
                false // Keep database with existing data
            } else {
                logger.i("Push database is empty - deleting to avoid passphrase conflicts")
                true // Delete empty database to avoid conflicts
            }
        } catch (e: Exception) {
            // Database exists but cannot be opened with current passphrase
            logger.w("Push database exists but cannot be opened (likely encrypted with different passphrase): ${e.message}")
            logger.i("Will delete incompatible Push database to allow migration to proceed")
            true // Delete incompatible database
        }

        if (shouldDeletePush) {
            try {
                context.deleteDatabase("pingidentity_push.db")
                logger.i("Deleted incompatible Push database")
            } catch (e: Exception) {
                logger.e("Failed to delete Push database: ${e.message}", e)
            }
        }
    } else {
        logger.i("No existing Push database found - will create fresh database")
    }
}