[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Authenticator Migration

The Authenticator Migration module provides automatic migration capabilities for upgrading legacy FR Authenticator data to the new unified OATH and Push storage format. This module ensures seamless transitions between SDK versions while preserving user accounts, TOTP/HOTP credentials, push notifications, and device tokens.

---

## Table of Contents

- [Integrating the SDK into your project](#integrating-the-sdk-into-your-project)
- [Migration Overview](#migration-overview)
- [Automatic Migration](#automatic-migration)
- [Migration Steps](#migration-steps)
- [Manual Migration](#manual-migration)
- [Monitoring Migration Progress](#monitoring-migration-progress)
- [Error Handling](#error-handling)
- [Troubleshooting](#troubleshooting)

---

## Integrating the SDK into your project

To add the Authenticator Migration module as a dependency to your project, include the following in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":mfa:auth-migration"))
}
```

---

## Migration Overview

The Authenticator Migration module automatically migrates legacy FR Authenticator data during application startup. It handles the following migration scenarios:

- **OATH Credentials**: Migrates TOTP and HOTP mechanisms from legacy format to new SQLOathStorage
- **Push Credentials**: Migrates Push authentication mechanisms to new SQLPushStorage
- **Push Notifications**: Migrates pending push notifications and their state
- **Device Tokens**: Migrates FCM device tokens for push notifications
- **Cleanup**: Removes legacy storage files and encryption keys after successful migration

### What Gets Migrated

| Legacy Component | Target Component | Description |
|------------------|------------------|-------------|
| Legacy Accounts | OATH/Push Credentials | Account metadata (issuer, account name, images) |
| Legacy TOTP/HOTP Mechanisms | SQLOathStorage | TOTP/HOTP credentials with secrets and configuration |
| Legacy Push Mechanisms | SQLPushStorage | Push credentials with endpoints and shared secrets |
| Legacy Notifications | SQLPushStorage | Pending push notifications and their approval state |
| Legacy Device Token | SQLPushStorage | FCM device registration tokens |
| Legacy Encryption Keys | - | Cleaned up from AndroidKeyStore after migration |

---

## Automatic Migration

The migration process is can be automatically triggered during application startup by importing the module. No manual intervention will be required.

### Configuration

Add the following in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":mfa:auth-migration"))
}
```

The migration will automatically:
1. Check for legacy FR Authenticator data during app startup
2. Decrypt and export data from legacy encrypted SharedPreferences
3. Migrate OATH credentials, Push credentials, notifications, and device tokens
4. Clean up legacy files and encryption keys after successful migration
5. Skip migration if no legacy data exists

---

## Migration Steps

The migration process consists of five sequential steps:

### Step 1: Export Legacy Authenticator Data
- Verifies existence of legacy encryption key in AndroidKeyStore
- Decrypts data from four legacy SharedPreferences files:
  - `org.forgerock.android.authenticator.DATA.ACCOUNT`
  - `org.forgerock.android.authenticator.DATA.MECHANISM`
  - `org.forgerock.android.authenticator.DATA.NOTIFICATIONS`
  - `org.forgerock.android.authenticator.DATA.DEVICE_TOKEN`
- Uses standalone decryptor (no Legacy SDK dependency required)
- Aborts migration if no legacy data is found

### Step 2: Migrate OATH and Push Mechanisms
- Parses mechanism JSON data to determine type (TOTP/HOTP/Push)
- Creates OathCredential objects for TOTP/HOTP mechanisms:
  - Preserves secret, algorithm (SHA1/SHA256/SHA512), digits, period
  - Maintains issuer, account name, and visual customizations
- Creates PushCredential objects for Push mechanisms:
  - Preserves server endpoint, shared secret
  - Maintains account information and visual customizations
- Stores credentials in SQLOathStorage and SQLPushStorage respectively

### Step 3: Migrate Push Notifications
- Transfers pending push notifications to SQLPushStorage
- Preserves notification state (approved, pending)
- Maintains challenge data and message IDs
- Keeps notification timestamps

### Step 4: Migrate Device Token
- Migrates FCM device registration token to SQLPushStorage
- Preserves device ID and token information
- Maintains communication tokens if present

### Step 5: Cleanup Legacy Data
- Deletes all four legacy SharedPreferences files
- Removes legacy encryption key from AndroidKeyStore
- Cleanup failures don't abort the migration (non-critical)

---

## Manual Migration

While migration is automatic, you can also trigger it manually:

```kotlin
// Manual migration trigger
lifecycleScope.launch {
    AuthMigration.start(context)
}
```

### Monitoring Migration Progress

```kotlin
AuthMigration.migration.migrate(context).collect { progress ->
    when (progress) {
        is MigrationProgress.Started -> {
            Log.d("Migration", "Authenticator migration started")
        }
        
        is MigrationProgress.InProgress -> {
            Log.d("Migration", "Step ${progress.currentStep}/${progress.totalSteps}: ${progress.message}")
        }
        
        is MigrationProgress.StepCompleted -> {
            Log.d("Migration", "Completed: ${progress.step.description}")
        }
        
        is MigrationProgress.Success -> {
            Log.d("Migration", "Migration completed successfully")
        }
        
        is MigrationProgress.Error -> {
            Log.e("Migration", "Migration failed", progress.error)
        }
    }
}
```

---

## Error Handling

The migration framework includes robust error handling:

### Individual Mechanism Failures
```kotlin
try {
    // Migration logic
    oathStorage.storeOathCredential(oathCredential)
    logger.d("Migrated OATH credential: $issuer - $accountName")
} catch (e: Exception) {
    logger.e("Failed to migrate mechanism $mechanismId", e)
    // Continue with other mechanisms even if one fails
}
```

- Failures in individual mechanism migration don't stop the entire process
- Errors are logged for debugging purposes
- Partial migrations are supported

### Decryption Failures
The migration uses a standalone decryptor that:
- Handles both AES symmetric and RSA-wrapped keys
- Verifies MAC signatures for data integrity
- Gracefully handles corrupted or missing data
- Returns empty maps for non-existent files

### Migration Abortion
- Migration automatically aborts if no legacy encryption key is found
- No errors are thrown for clean installations
- Subsequent app starts skip migration checks after successful completion

---

## Legacy Encryption Format

The Legacy SDK's FR Authenticator used a custom encryption format:

1. **Data Wrapping**: Values stored as `{"type": X, "value": Y}` JSON
2. **Encryption**: AES/GCM with 12-byte IV and 128-bit authentication tag
3. **MAC**: HMAC-SHA256 for additional integrity verification
4. **Key Storage**: Master key in AndroidKeyStore with alias `org.forgerock.android.authenticator.KEYS`
5. **Encoding**: Base64 encoding for storage in SharedPreferences

The migration decryptor reverses this process without requiring the Legacy SDK.

---

## Data Mapping

### OATH Credentials

| Legacy Field | New Field | Notes |
|-------------|-----------|-------|
| `issuer` | `issuer` | Preserved exactly |
| `accountName` | `accountName` | Preserved exactly |
| `secret` | `secret` | Base32-encoded shared secret |
| `algorithm` | `oathAlgorithm` | Maps SHA1/SHA256/SHA512 |
| `digits` | `digits` | Typically 6 or 8 |
| `period` | `period` | For TOTP (default 30s) |
| `counter` | `counter` | For HOTP |
| `oathType` | `oathType` | Maps to TOTP or HOTP enum |
| `imageURL` | `imageURL` | Optional logo URL |
| `backgroundColor` | `backgroundColor` | Optional UI customization |

### Push Credentials

| Legacy Field | New Field | Notes |
|-------------|-----------|-------|
| `issuer` | `issuer` | Preserved exactly |
| `accountName` | `accountName` | Preserved exactly |
| `authenticationEndpoint` | `serverEndpoint` | Server URL for push |
| `secret` | `sharedSecret` | Cryptographic secret |
| `imageURL` | `imageURL` | Optional logo URL |
| `backgroundColor` | `backgroundColor` | Optional UI customization |

### Push Notifications

| Legacy Field | New Field | Notes |
|-------------|-----------|-------|
| `messageId` | `messageId` | Unique notification ID |
| `challenge` | `challenge` | Authentication challenge |
| `mechanismUID` | `credentialId` | Links to push credential |
| `ttl` | `ttl` | Time to live in seconds |
| `approved` | `approved` | User approval status |
| `pending` | `pending` | Processing state |
| `timeAdded` | `createdAt` | Notification timestamp |

---

## Custom Storage Migration

**If you are using custom storage implementations for OATH or Push authentication, this automatic migration will NOT handle your custom data.**

This migration module only handles the default Ping SDK storage implementations:
- Default SQLOathStorage (SQLite-based OATH storage)
- Default SQLPushStorage (SQLite-based Push storage)
- Legacy FR Authenticator encrypted SharedPreferences

### Custom Storage Requirements

If your application uses custom storage implementations, you must:

1. **Write your own migration logic** to handle your custom data format
2. **Implement migration before** the automatic migration runs
3. **Test your custom migration thoroughly** with real user data
4. **Ensure data integrity** during the migration process

---

## Troubleshooting

### Common Issues

**Migration Not Running**
- Verify the AuthenticationMigrationInitializer is properly configured in AndroidManifest.xml
- Check that the auth-migration dependency is included
- Ensure app has proper file system permissions

**Legacy Data Not Found**
- This is normal for new installations or users who never used FR Authenticator
- Migration will abort with ABORT result (not an error)
- No action required

**Decryption Failures**
- Check if the legacy encryption key exists in AndroidKeyStore
- Verify SharedPreferences files are not corrupted
- Ensure the app hasn't been reinstalled (encryption keys are lost on reinstall)

**Storage Initialization Errors**
- Verify SQLOathStorage and SQLPushStorage are properly initialized
- Check database permissions and storage availability
- Review SQLite database version compatibility

### Debug Logging

Enable debug logging to monitor migration progress:

```kotlin
// Migration uses the standard Ping Logger
Logger.logger = Logger.STANDARD
```

Check logs for migration status:
```
Ping SDK: Checking for legacy authenticator data
Ping SDK: Legacy repository exists, proceeding with migration
Ping SDK: Successfully exported 3 accounts, 5 mechanisms, 2 notifications, 1 device tokens
Ping SDK: Starting mechanisms migration to new storage
Ping SDK: Migrated OATH credential: ForgeRock - user@example.com
Ping SDK: Migrated Push credential: PingAM - admin@company.com
Ping SDK: Migrated 3 OATH credentials and 2 Push credentials
Ping SDK: Starting push notifications migration
Ping SDK: Migrated 2 push notifications
Ping SDK: Starting device token migration
Ping SDK: Migrated device token successfully
Ping SDK: Cleaning up legacy authenticator data
Ping SDK: Successfully deleted legacy authenticator data
Ping SDK: Migration completed successfully
```

### Migration State

The migration maintains state between steps:
- Exported data is stored in migration context
- Storage instances are initialized once and reused
- Failed migrations can be retried on next app start
- Successful migrations are marked complete to prevent re-running

---

## Migration Safety

The migration process is designed to be safe and non-destructive:

- **Backup Approach**: Legacy data is read before any modifications
- **Rollback Safety**: Original data remains until migration succeeds
- **Idempotent**: Can be run multiple times safely (only runs once)
- **Partial Recovery**: Individual mechanism failures don't affect others
- **Cleanup Safety**: Legacy data is only deleted after successful migration

### Data Integrity

- All cryptographic secrets maintain their security properties
- User authentication credentials are preserved exactly
- Account associations remain intact
- No data is lost during migration process
- Encryption keys are properly managed throughout

### Performance Considerations

- Migration runs on background thread (Dispatchers.Default)
- Doesn't block app startup
- Typically completes in 1-5 seconds for normal datasets
- Scales linearly with number of accounts/mechanisms

---

## Example Migration Scenario

### Before Migration (Legacy SDK)
```
Legacy SharedPreferences:
├── org.forgerock.android.authenticator.DATA.ACCOUNT
│   ├── "ForgeRock-user@example.com"
│   └── "PingAM-admin@company.com"
├── org.forgerock.android.authenticator.DATA.MECHANISM
│   ├── "ForgeRock-user@example.com-totp"
│   ├── "ForgeRock-user@example.com-push"
│   └── "PingAM-admin@company.com-totp"
├── org.forgerock.android.authenticator.DATA.NOTIFICATIONS
│   └── "notification-uuid-123"
└── org.forgerock.android.authenticator.DATA.DEVICE_TOKEN
    └── "deviceToken"

AndroidKeyStore:
└── "org.forgerock.android.authenticator.KEYS"
```

### After Migration (New SDK)
```
SQLOathStorage (SQLite):
├── OathCredential(issuer="ForgeRock", account="user@example.com", type=TOTP)
└── OathCredential(issuer="PingAM", account="admin@company.com", type=TOTP)

SQLPushStorage (SQLite):
├── PushCredential(issuer="ForgeRock", account="user@example.com")
├── PushNotification(messageId="notification-uuid-123", pending=true)
└── PushDeviceToken(tokenId="fcm-token-xyz")

Legacy Files: [Deleted]
Legacy KeyStore Entry: [Deleted]
```

---

## Requirements

- **Dependencies**:
  - `mfa:oath` - OATH credential storage
  - `mfa:push` - Push credential and notification storage
  - `foundation:migration` - Migration framework
  - `foundation:legacy-migration` - Legacy decryption utilities
  - AndroidX Startup library

---

## Additional Resources

- [Migration Framework Documentation](../../foundation/migration/README.md)
- [OATH Storage Documentation](../oath/README.md)
- [Push Storage Documentation](../push/README.md)

---

## License

This module is part of the Ping Identity SDK for Android and is subject to the MIT License. See the LICENSE file for details.

---

