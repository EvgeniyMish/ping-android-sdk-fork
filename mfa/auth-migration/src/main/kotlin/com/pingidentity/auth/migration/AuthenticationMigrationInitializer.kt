/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import androidx.startup.Initializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Initializes authenticator migration during app startup.
 *
 * Including the following dependency in your build.gradle.kts file:
 * ```kotlin
 * dependencies {
 *     implementation(project(":mfa:auth-migration"))
 * }
 * ```
 * This will trigger the automatic migration.
 * @see AuthMigration
 */
class AuthenticationMigrationInitializer : Initializer<Unit> {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Triggers migration in a background coroutine.
     */
    override fun create(context: Context) {
        scope.launch {
            try {
                AuthMigration.start(context)
            } catch (e: Exception) {
                // Ignore migration errors
                AuthMigration.logger.e("Authentication migration failed", e)
            }
        }
    }

    /**
     * Returns empty list as no dependencies required.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies
        return emptyList()
    }
}