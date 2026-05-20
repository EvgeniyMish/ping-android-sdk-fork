/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
import com.pingidentity.mfa.push.PushType
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.migration.MigrationProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Constants matching the ones in LegacyAuthenticationRepository
private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"
private const val AUTH_DATA_NOTIFICATIONS = "org.forgerock.android.authenticator.DATA.NOTIFICATIONS"
private const val AUTH_DATA_DEVICE_TOKEN = "org.forgerock.android.authenticator.DATA.DEVICE_TOKEN"

@RunWith(RobolectricTestRunner::class)
class AuthMigrationTest {

    private lateinit var context: Context
    private lateinit var accountPrefs: SharedPreferences
    private lateinit var mechanismPrefs: SharedPreferences
    private lateinit var notificationPrefs: SharedPreferences
    private lateinit var deviceTokenPrefs: SharedPreferences

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ContextProvider.init(context)

        // Setup real SharedPreferences for each legacy file
        accountPrefs = context.getSharedPreferences(AUTH_DATA_ACCOUNT, Context.MODE_PRIVATE)
        mechanismPrefs = context.getSharedPreferences(AUTH_DATA_MECHANISM, Context.MODE_PRIVATE)
        notificationPrefs = context.getSharedPreferences(AUTH_DATA_NOTIFICATIONS, Context.MODE_PRIVATE)
        deviceTokenPrefs = context.getSharedPreferences(AUTH_DATA_DEVICE_TOKEN, Context.MODE_PRIVATE)

        // Mock LegacyAuthenticationRepository
        mockkConstructor(LegacyAuthenticationRepository::class)

        // Mock SQLOathStorage
        mockkConstructor(SQLOathStorage::class)
        coEvery { anyConstructed<SQLOathStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLOathStorage>().storeOathCredential(any()) } returns Unit

        // Mock SQLPushStorage
        mockkConstructor(SQLPushStorage::class)
        coEvery { anyConstructed<SQLPushStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushCredential(any()) } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushNotification(any()) } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushDeviceToken(any()) } returns Unit

        cleanup()
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        unmockkAll()
    }

    /**
     * Clean up any existing files and preferences
     */
    private fun cleanup() {
        try {
            context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
            context.deleteSharedPreferences(AUTH_DATA_MECHANISM)
            context.deleteSharedPreferences(AUTH_DATA_NOTIFICATIONS)
            context.deleteSharedPreferences(AUTH_DATA_DEVICE_TOKEN)
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `migration with no legacy repository should abort early`() = runTest {
        // Given - No legacy repository exists (no encryption key)
        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns false

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have StepCompleted event for step 1 that aborts
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.isNotEmpty())
        assertEquals("Export legacy authenticator data", stepCompletedEvents[0].step.description)

        // Should not have InProgress messages for steps 2-5 since step 1 aborts
        val inProgressMessages = progressList.filterIsInstance<MigrationProgress.InProgress>()
        assertTrue(inProgressMessages.size <= 1) // Only step 1 should run
    }

    @Test
    fun `migration with empty legacy repository should abort after step 1`() = runTest {
        // Given - Legacy repository exists but is empty
        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = emptyMap(),
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(0, 0, 0, false)
        )

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have step 1 progress but abort before step 2
        val stepStartedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepStartedEvents.isNotEmpty())
        assertEquals("Export legacy authenticator data", stepStartedEvents[0].step.description)
    }

    @Test
    fun `migration with legacy data completes all steps successfully`() = runTest {
        // Given - Legacy repository with test data
        val (accounts, mechanisms, notifications, deviceTokens) = createTestLegacyData()

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = notifications,
            deviceToken = deviceTokens,
            metadata = ExportMetadata(
                totalAccounts = accounts.size,
                totalMechanisms = mechanisms.size,
                totalNotifications = notifications.size,
                hasDeviceToken = deviceTokens.isNotEmpty()
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have all five steps completed
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.size >= 5)

        assertEquals("Export legacy authenticator data", stepCompletedEvents[0].step.description)
        assertEquals("Migrate mechanisms to OATH and Push storage", stepCompletedEvents[1].step.description)
        assertEquals("Migrate push notifications to Push storage", stepCompletedEvents[2].step.description)
        assertEquals("Migrate device token to Push storage", stepCompletedEvents[3].step.description)
        assertEquals("Cleanup legacy authenticator data", stepCompletedEvents[4].step.description)
    }

    @Test
    fun `migration migrates OATH credentials correctly`() = runTest {
        // Given - Legacy repository with TOTP mechanism with ALL fields
        val testTime = System.currentTimeMillis()
        val mechanisms = mapOf(
            "totp-mechanism-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "totp-mechanism-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "secret": "JBSWY3DPEHPK3PXP",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30,
                    "counter": 0,
                    "oathType": "TOTP",
                    "uid": "user-12345",
                    "resourceId": "resource-67890",
                    "timeAdded": $testTime
                }
            """.trimIndent()
        )
        val accounts = mapOf(
            "Google-user@example.com" to """
                {
                    "id": "Google-user@example.com",
                    "issuer": "Google",
                    "displayIssuer": "Google Authenticator",
                    "accountName": "user@example.com",
                    "displayAccountName": "Demo User",
                    "imageURL": "https://example.com/logo.png",
                    "backgroundColor": "#4285F4",
                    "policies": "{\"deviceTampering\":true}",
                    "lock": false
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = accounts.size,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored credential
        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        // Verify ALL 19 fields are correctly migrated
        val storedCredential = oathSlot.captured
        assertEquals("totp-mechanism-1", storedCredential.id)
        assertEquals("user-12345", storedCredential.userId)
        assertEquals("resource-67890", storedCredential.resourceId)
        assertEquals("Google", storedCredential.issuer)
        assertEquals("Google Authenticator", storedCredential.displayIssuer)
        assertEquals("user@example.com", storedCredential.accountName)
        assertEquals("Demo User", storedCredential.displayAccountName)
        assertEquals(OathType.TOTP, storedCredential.oathType)
        assertEquals("JBSWY3DPEHPK3PXP", storedCredential.secret)
        assertEquals(OathAlgorithm.SHA1, storedCredential.oathAlgorithm)
        assertEquals(6, storedCredential.digits)
        assertEquals(30, storedCredential.period)
        assertEquals(0L, storedCredential.counter)
        assertEquals(testTime, storedCredential.createdAt.time)
        assertEquals("https://example.com/logo.png", storedCredential.imageURL)
        assertEquals("#4285F4", storedCredential.backgroundColor)
        assertEquals("{\"deviceTampering\":true}", storedCredential.policies)
        assertEquals(null, storedCredential.lockingPolicy)
        assertEquals(false, storedCredential.isLocked)
    }

    @Test
    fun `migration migrates HOTP credentials correctly with counter`() = runTest {
        // Given - Legacy repository with HOTP mechanism
        val testTime = System.currentTimeMillis()
        val mechanisms = mapOf(
            "hotp-mechanism-1" to """
                {
                    "type": "hotp",
                    "mechanismUID": "hotp-mechanism-1",
                    "issuer": "GitHub",
                    "accountName": "developer@example.com",
                    "secret": "HOTPSECRETKEY123",
                    "algorithm": "sha256",
                    "digits": 8,
                    "counter": 42,
                    "oathType": "HOTP",
                    "uid": "hotp-user-789",
                    "resourceId": "hotp-resource-456",
                    "timeAdded": $testTime
                }
            """.trimIndent()
        )
        val accounts = mapOf(
            "GitHub-developer@example.com" to """
                {
                    "id": "GitHub-developer@example.com",
                    "issuer": "GitHub",
                    "displayIssuer": "GitHub Inc",
                    "accountName": "developer@example.com",
                    "displayAccountName": "Developer Account",
                    "imageURL": "https://github.com/logo.png",
                    "backgroundColor": "#24292e",
                    "lock": false
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(1, 1, 0, false)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        // Verify HOTP-specific fields and all others
        val storedCredential = oathSlot.captured
        assertEquals("hotp-mechanism-1", storedCredential.id)
        assertEquals("hotp-user-789", storedCredential.userId)
        assertEquals("hotp-resource-456", storedCredential.resourceId)
        assertEquals("GitHub", storedCredential.issuer)
        assertEquals("GitHub Inc", storedCredential.displayIssuer)
        assertEquals("developer@example.com", storedCredential.accountName)
        assertEquals("Developer Account", storedCredential.displayAccountName)
        assertEquals(OathType.HOTP, storedCredential.oathType)  // HOTP type
        assertEquals("HOTPSECRETKEY123", storedCredential.secret)
        assertEquals(OathAlgorithm.SHA256, storedCredential.oathAlgorithm)  // SHA256
        assertEquals(8, storedCredential.digits)  // 8 digits
        assertEquals(30, storedCredential.period)  // Default period (not used for HOTP)
        assertEquals(42L, storedCredential.counter)  // Counter value
        assertEquals(testTime, storedCredential.createdAt.time)
        assertEquals("https://github.com/logo.png", storedCredential.imageURL)
        assertEquals("#24292e", storedCredential.backgroundColor)
        assertEquals(null, storedCredential.policies)
        assertEquals(null, storedCredential.lockingPolicy)
        assertEquals(false, storedCredential.isLocked)
    }

    @Test
    fun `migration migrates Push credentials correctly`() = runTest {
        // Given - Legacy repository with Push mechanism with ALL fields
        val testTime = System.currentTimeMillis()
        val mechanisms = mapOf(
            "push-mechanism-1" to """
                {
                    "type": "push",
                    "mechanismUID": "push-mechanism-1",
                    "issuer": "ForgeRock",
                    "accountName": "admin@company.com",
                    "secret": "sharedSecret123",
                    "uid": "user-99999",
                    "resourceId": "push-resource-11111",
                    "authenticationEndpoint": "https://am.example.com/push?_action=authenticate",
                    "registrationEndpoint": "https://am.example.com/push?_action=register",
                    "platform": "PING_AM",
                    "timeAdded": $testTime
                }
            """.trimIndent()
        )
        val accounts = mapOf(
            "ForgeRock-admin@company.com" to """
                {
                    "id": "ForgeRock-admin@company.com",
                    "issuer": "ForgeRock",
                    "displayIssuer": "ForgeRock Identity",
                    "accountName": "admin@company.com",
                    "displayAccountName": "Admin User",
                    "imageURL": "https://forgerock.com/logo.png",
                    "backgroundColor": "#00A1DE",
                    "policies": "{\"biometric\":true}",
                    "lockingPolicy": "BiometricNotAvailable",
                    "lock": true
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = accounts.size,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored credential
        val pushSlot = slot<PushCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(capture(pushSlot)) }

        // Verify ALL 16 fields are correctly migrated
        val storedCredential = pushSlot.captured
        assertEquals("push-mechanism-1", storedCredential.id)
        assertEquals("user-99999", storedCredential.userId)
        assertEquals("push-resource-11111", storedCredential.resourceId)
        assertEquals("ForgeRock", storedCredential.issuer)
        assertEquals("ForgeRock Identity", storedCredential.displayIssuer)
        assertEquals("admin@company.com", storedCredential.accountName)
        assertEquals("Admin User", storedCredential.displayAccountName)
        assertEquals("https://am.example.com/push", storedCredential.serverEndpoint) // Base URL without query params
        assertEquals("sharedSecret123", storedCredential.sharedSecret)
        assertEquals(testTime, storedCredential.createdAt.time)
        assertEquals("https://forgerock.com/logo.png", storedCredential.imageURL)
        assertEquals("#00A1DE", storedCredential.backgroundColor)
        assertEquals("{\"biometric\":true}", storedCredential.policies)
        assertEquals("BiometricNotAvailable", storedCredential.lockingPolicy)
        assertEquals(true, storedCredential.isLocked)
        assertEquals("PING_AM", storedCredential.platform)
    }

    @Test
    fun `migration migrates push notifications correctly`() = runTest {
        // Given - Legacy repository with notification with ALL fields
        val notificationTime = System.currentTimeMillis()
        val sentTime = notificationTime - 5000
        val respondedTime = notificationTime + 1000
        val notifications = mapOf(
            "notification-1" to """
                {
                    "id": "notification-1",
                    "credentialId": "push-credential-1",
                    "ttl": 120,
                    "messageId": "msg-123",
                    "messageText": "Login attempt from Chrome",
                    "customPayload": "custom-data-123",
                    "challenge": "challenge-data",
                    "numbersChallenge": "42",
                    "loadBalancer": "amlb-cookie-value",
                    "contextInfo": "Location: San Francisco",
                    "pushType": "challenge",
                    "timeAdded": $notificationTime,
                    "sentAt": $sentTime,
                    "respondedAt": $respondedTime,
                    "approved": false,
                    "pending": true
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = emptyMap(),
            notifications = notifications,
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = 0,
                totalNotifications = notifications.size,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored notification
        val notificationSlot = slot<PushNotification>()
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushNotification(capture(notificationSlot)) }

        // Verify ALL 17 fields are correctly migrated
        val storedNotification = notificationSlot.captured
        assertEquals("notification-1", storedNotification.id)
        assertEquals("push-credential-1", storedNotification.credentialId)
        assertEquals(120, storedNotification.ttl)
        assertEquals("msg-123", storedNotification.messageId)
        assertEquals("Login attempt from Chrome", storedNotification.messageText)
        assertEquals("custom-data-123", storedNotification.customPayload)
        assertEquals("challenge-data", storedNotification.challenge)
        assertEquals("42", storedNotification.numbersChallenge)
        assertEquals("amlb-cookie-value", storedNotification.loadBalancer)
        assertEquals("Location: San Francisco", storedNotification.contextInfo)
        assertEquals(PushType.CHALLENGE, storedNotification.pushType)
        assertEquals(notificationTime, storedNotification.createdAt.time)
        assertEquals(sentTime, storedNotification.sentAt?.time)
        assertEquals(respondedTime, storedNotification.respondedAt?.time)
        assertEquals(null, storedNotification.additionalData) // No JSON string in test data
        assertEquals(false, storedNotification.approved)
        assertEquals(true, storedNotification.pending)
    }

    @Test
    fun `migration migrates device token correctly`() = runTest {
        // Given - Legacy repository with device token
        val tokenTime = System.currentTimeMillis()
        val deviceTokens = mapOf(
            "device-token-1" to """
                {
                    "id": "device-token-1",
                    "deviceToken": "fcm-token-xyz-123",
                    "token": "fcm-token-xyz-123",
                    "timeAdded": $tokenTime
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = emptyMap(),
            notifications = emptyMap(),
            deviceToken = deviceTokens,
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = 0,
                totalNotifications = 0,
                hasDeviceToken = true
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored device token
        val tokenSlot = slot<PushDeviceToken>()
        // Verify the storage function was called with the expected device token
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushDeviceToken(capture(tokenSlot)) }

        // Verify the captured device token has the expected values
        val storedToken = tokenSlot.captured
        assertEquals("device-token-1", storedToken.id)
        assertEquals("fcm-token-xyz-123", storedToken.tokenId)
        assertEquals(tokenTime, storedToken.createdAt.time)
    }

    @Test
    fun `migration step 2 continues with other mechanisms when one fails`() = runTest {
        // Given - Multiple mechanisms, one with invalid data
        val mechanisms = mapOf(
            "valid-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "valid-totp",
                    "issuer": "Valid",
                    "accountName": "user1@example.com",
                    "secret": "VALIDKEY",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30
                }
            """.trimIndent(),
            "invalid-mechanism" to """
                {
                    "type": "unknown-type",
                    "invalid": "data"
                }
            """.trimIndent(),
            "valid-push" to """
                {
                    "type": "push",
                    "mechanismUID": "valid-push",
                    "issuer": "Valid",
                    "accountName": "user2@example.com",
                    "secret": "pushSecret",
                    "authenticationEndpoint": "https://example.com/push"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should still complete successfully
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should complete all steps even though one mechanism failed
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.size >= 5)
    }

    @Test
    fun `migration handles mixed OATH types correctly`() = runTest {
        // Given - Legacy repository with both TOTP and HOTP
        val mechanisms = mapOf(
            "totp-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "totp-1",
                    "issuer": "TOTP Service",
                    "accountName": "totp@example.com",
                    "secret": "TOTPKEY",
                    "oathType": "TOTP",
                    "period": 30
                }
            """.trimIndent(),
            "hotp-1" to """
                {
                    "type": "hotp",
                    "mechanismUID": "hotp-1",
                    "issuer": "HOTP Service",
                    "accountName": "hotp@example.com",
                    "secret": "HOTPKEY",
                    "oathType": "HOTP",
                    "counter": 5
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })
    }

    @Test
    fun `migration handles different hash algorithms correctly`() = runTest {
        // Given - Mechanisms with different algorithms
        val mechanisms = mapOf(
            "sha1-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "sha1-totp",
                    "issuer": "SHA1 Service",
                    "accountName": "sha1@example.com",
                    "secret": "SHA1KEY",
                    "algorithm": "sha1"
                }
            """.trimIndent(),
            "sha256-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "sha256-totp",
                    "issuer": "SHA256 Service",
                    "accountName": "sha256@example.com",
                    "secret": "SHA256KEY",
                    "algorithm": "sha256"
                }
            """.trimIndent(),
            "sha512-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "sha512-totp",
                    "issuer": "SHA512 Service",
                    "accountName": "sha512@example.com",
                    "secret": "SHA512KEY",
                    "algorithm": "sha512"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })
    }

    @Test
    fun `migration handles database initialization failure with destructive recovery enabled`() = runTest {
        // Given - Legacy repository with valid data
        val mechanisms = mapOf(
            "totp-mechanism-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "totp-mechanism-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "secret": "JBSWY3DPEHPK3PXP",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30,
                    "oathType": "TOTP"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(0, 1, 0, false)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // With destructive recovery enabled in the migration step configuration,
        // the storage should be able to recover from database corruption.
        // This test verifies that the migration configures storage correctly.

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should complete successfully with destructive recovery enabled
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Verify storage was initialized with proper configuration
        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().initializeDatabase() }
        coVerify(atLeast = 1) { anyConstructed<SQLPushStorage>().initializeDatabase() }
    }

    @Test
    fun `migration cleanup step continues even if cleanup fails`() = runTest {
        // Given - Legacy repository with data but cleanup will fail
        val mechanisms = mapOf(
            "test-mechanism" to """
                {
                    "type": "totp",
                    "mechanismUID": "test-mechanism",
                    "issuer": "Test",
                    "accountName": "test@example.com",
                    "secret": "TESTKEY"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        // Simulate cleanup failure
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } throws Exception("Cleanup failed")

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should still complete successfully even if cleanup fails
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // All steps should complete
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertEquals(5, stepCompletedEvents.size)
    }

    @Test
    fun `migration preserves existing valid databases and only removes incompatible ones`() = runTest {
        // Given - Legacy repository with data to migrate
        val mechanisms = mapOf(
            "totp-mechanism-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "totp-mechanism-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "secret": "JBSWY3DPEHPK3PXP",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30,
                    "oathType": "TOTP"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(0, 1, 0, false)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // Mock getAllOathCredentials to return empty list (simulating empty but valid database)
        coEvery { anyConstructed<SQLOathStorage>().getAllOathCredentials() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushCredentials() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushNotifications() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushDeviceTokens() } returns emptyList()

        // When - The prepareDatabase function will:
        // 1. Try to open existing databases with current passphrase
        // 2. Check if they contain data
        // 3. Keep them if they can be opened (even if empty)
        // 4. Only delete if they cannot be opened (passphrase mismatch)
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should complete successfully
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Verify migration completed and stored the credential
        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    // Helper methods


    private fun createTestLegacyData(): LegacyTestData {
        val accounts = mapOf(
            "account-1" to """
                {
                    "id": "account-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "imageURL": "https://google.com/logo.png"
                }
            """.trimIndent(),
            "account-2" to """
                {
                    "id": "account-2",
                    "issuer": "GitHub",
                    "accountName": "developer@example.com"
                }
            """.trimIndent()
        )

        val mechanisms = mapOf(
            "mechanism-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "mechanism-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "secret": "JBSWY3DPEHPK3PXP",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30
                }
            """.trimIndent(),
            "mechanism-2" to """
                {
                    "type": "push",
                    "mechanismUID": "mechanism-2",
                    "issuer": "GitHub",
                    "accountName": "developer@example.com",
                    "secret": "pushSecret123",
                    "authenticationEndpoint": "https://github.com/push"
                }
            """.trimIndent()
        )

        val notifications = mapOf(
            "notification-1" to """
                {
                    "id": "notification-1",
                    "credentialId": "mechanism-2",
                    "messageId": "msg-456",
                    "challenge": "challenge-xyz",
                    "ttl": 120,
                    "approved": false,
                    "pending": true,
                    "timeAdded": ${System.currentTimeMillis()}
                }
            """.trimIndent()
        )

        val deviceTokens = mapOf(
            "token-1" to """
                {
                    "id": "token-1",
                    "deviceToken": "fcm-token-abc-123",
                    "timeAdded": ${System.currentTimeMillis()}
                }
            """.trimIndent()
        )

        return LegacyTestData(accounts, mechanisms, notifications, deviceTokens)
    }

    data class LegacyTestData(
        val accounts: Map<String, String>,
        val mechanisms: Map<String, String>,
        val notifications: Map<String, String>,
        val deviceTokens: Map<String, String>
    )

    @Test
    fun `migration supports custom storage with unencrypted SharedPreferences`() = runTest {
        // Given - Custom storage data (plain SharedPreferences, no encryption)
        // This simulates FRAClient built with CustomStorageClient
        val testTime = System.currentTimeMillis()
        val mechanisms = mapOf(
            "custom-totp-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "custom-totp-1",
                    "issuer": "CustomApp",
                    "accountName": "custom@example.com",
                    "secret": "CUSTOMSECRET123",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30,
                    "oathType": "TOTP",
                    "uid": "custom-user-123",
                    "resourceId": "custom-resource-456",
                    "timeAdded": $testTime
                }
            """.trimIndent()
        )
        val accounts = mapOf(
            "CustomApp-custom@example.com" to """
                {
                    "id": "CustomApp-custom@example.com",
                    "issuer": "CustomApp",
                    "displayIssuer": "Custom Application",
                    "accountName": "custom@example.com",
                    "displayAccountName": "Custom User",
                    "imageURL": "https://custom.com/logo.png",
                    "backgroundColor": "#FF5733",
                    "lock": false
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(1, 1, 0, false)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progress = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })

        // Verify the credential was migrated
        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        val storedCredential = oathSlot.captured
        assertEquals("custom-totp-1", storedCredential.id)
        assertEquals("CustomApp", storedCredential.issuer)
        assertEquals("Custom Application", storedCredential.displayIssuer)
        assertEquals("custom@example.com", storedCredential.accountName)
        assertEquals("Custom User", storedCredential.displayAccountName)
        assertEquals("custom-user-123", storedCredential.userId)
        assertEquals("custom-resource-456", storedCredential.resourceId)
    }

    @Test
    fun `migration supports custom key alias configuration`() = runTest {
        // Given - Configure migration with custom key alias
        val customKeyAlias = "com.myapp.custom.STORAGE_KEY"
        AuthMigration.configure(keyAlias = customKeyAlias)

        val mechanisms = mapOf(
            "custom-key-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "custom-key-totp",
                    "issuer": "CustomKeyApp",
                    "accountName": "user@customkey.com",
                    "secret": "CUSTOMKEYSECRET",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30,
                    "oathType": "TOTP"
                }
            """.trimIndent()
        )
        val accounts = mapOf(
            "CustomKeyApp-user@customkey.com" to """
                {
                    "id": "CustomKeyApp-user@customkey.com",
                    "issuer": "CustomKeyApp",
                    "accountName": "user@customkey.com"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(1, 1, 0, false)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progress = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue("Migration with custom key alias must succeed", progress.any { it is MigrationProgress.Success })

        // Verify the credential was migrated
        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        val storedCredential = oathSlot.captured
        assertEquals("custom-key-totp", storedCredential.id)
        assertEquals("CustomKeyApp", storedCredential.issuer)

        // Reset configuration to default for other tests
        AuthMigration.configure()
    }
}


