/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.migration.Migration

/**
 * Main object for FR Authenticator data migration.
 * Orchestrates the migration of accounts, mechanisms, notifications, and device tokens.
 */
object AuthMigration {
    val logger = Logger.STANDARD

    /**
     * Configuration for the migration process.
     * Set this before starting migration if using custom storage with a different key alias.
     */
    @Volatile
    var config: LegacyAuthenticationConfig = LegacyAuthenticationConfig()

    /** The migration configuration that defines the sequence of migration steps. */
    val migration = Migration {
        logger = this@AuthMigration.logger
        step(step1)  // Export legacy authenticator data
        step(step2)  // Migrate mechanisms to OATH and Push storage
        step(step3)  // Migrate push notifications to Push storage
        step(step4)  // Migrate device token to Push storage
        step(step5)  // Cleanup legacy authenticator data
    }

    /**
     * Configures the migration with custom settings.
     *
     * @param keyAlias The AndroidKeyStore alias used for encrypted storage.
     *                 Default: "org.forgerock.android.authenticator.KEYS"
     *
     * ## Example - Custom Storage with Different Key Alias
     *
     * ```kotlin
     * // Before starting migration, configure the key alias
     * AuthMigration.configure(keyAlias = "com.myapp.custom.KEY_ALIAS")
     *
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
     * }
     * ```
     */
    fun configure(keyAlias: String = LegacyAuthenticationConfig.DEFAULT_KEY_ALIAS) {
        config = LegacyAuthenticationConfig(keyAlias = keyAlias)
        logger.i("Migration configured with key alias: $keyAlias")
    }

    /**
     * Starts the authenticator migration process.
     *
     * This suspending function executes all configured migration steps in sequence,
     * emitting progress updates through a Flow. Each step is executed only once and
     * will be skipped on subsequent invocations.
     *
     * The migration is idempotent and safe to run multiple times.
     *
     * @param context The Android context used to access storage services.
     * ## Example
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
     * }
     * ```
     *
     * @see com.pingidentity.migration.MigrationProgress
     */
    suspend fun start(context: Context) {
        migration.migrate(context).collect { progress ->
            when (progress) {
                is com.pingidentity.migration.MigrationProgress.Started -> {
                    // Handle migration started
                    logger.i("Migration started")
                }

                is com.pingidentity.migration.MigrationProgress.InProgress -> {
                    // Handle migration in progress
                    logger.i("Migration in progress: Step ${progress.currentStep} of ${progress.totalSteps} - ${progress.message}")
                }

                is com.pingidentity.migration.MigrationProgress.Error -> {
                    // Handle migration error
                    logger.i("Migration error: ${progress.error.message}")
                }

                is com.pingidentity.migration.MigrationProgress.Success -> {
                    // Handle migration completed
                    logger.i("Migration completed successfully: ${progress.message}")
                }

                is com.pingidentity.migration.MigrationProgress.StepCompleted -> {
                    // Handle step completed
                    logger.i("Step completed: ${progress.step.description}")
                }
            }
        }
    }
}