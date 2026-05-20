/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN

/**
 * Pre-configured tamper detector that identifies device rooting by checking for the `su` command.
 *
 * This detector extends [CommandDetector] and specifically looks for the presence of the `su`
 * (superuser) command in the system PATH. The `su` command is the fundamental component of
 * root access on Unix-like systems, including Android.
 *
 * **What is the `su` command:**
 * - **su** stands for "substitute user" or commonly "superuser"
 * - Allows a user to execute commands as another user, typically root
 * - Essential for any form of elevated privileges on Android devices
 * - Present on virtually all rooted Android devices
 *
 * **How the detection works:**
 * The detector uses the `which` command to check if `su` is available in the system PATH.
 * This method is effective because:
 * - The `su` binary must be in PATH to be usable by applications
 * - Standard Android installations do not include `su` in user-accessible locations
 * - Root management tools install `su` in system directories that are included in PATH
 *
 * **Why this indicates tampering:**
 * - Stock Android devices do not have `su` accessible to user applications
 * - The presence of `su` in PATH indicates the device has been rooted
 * - This is one of the most reliable indicators of root access
 *
 * This detector provides a lightweight and reliable method for detecting rooted devices
 * by checking for the most fundamental root access tool.
 *
 * Example usage:
 * ```kotlin
 * val detector = SuCommandDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(SuCommandDetector())
 *     }
 * }
 * ```
 *
 * @see CommandDetector
 */
object SuCommandDetector : CommandDetector() {
    /*
     * Logger instance for logging detector activities and results.
     */
    override var logger: Logger = Logger.WARN

    /**
     * Provides the array of commands to check for tampering detection.
     *
     * Returns an array containing only the `su` command, which is the primary
     * indicator of root access on Android devices.
     *
     * @return An array containing "su" as the command to detect
     */
    override fun getCommands(): Array<String> {
        return arrayOf(SU_COMMAND)
    }

    override suspend fun analyze(context: Context): Double {
        logger.i("Running SuCommandDetector")
        return super.analyze(context)
    }

    internal const val SU_COMMAND = "su"
}