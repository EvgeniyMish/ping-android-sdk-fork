/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Abstract base class for detecting device tampering by checking for the presence of specific commands.
 *
 * This detector works by attempting to locate commands that are commonly available on rooted or
 * compromised devices using the system's `which` command. Subclasses must provide the list of
 * commands to check for through the [getCommands] method.
 *
 * Common commands that might indicate tampering include:
 * - `su` (superuser access)
 * - `busybox` (advanced command-line tools)
 * - `magisk` (systemless root solution)
 * - Custom debugging or hacking tools
 *
 * The scoring system returns a [Double] value:
 * - `1.0` indicates at least one suspicious command was found (high confidence of tampering)
 * - `0.0` indicates no suspicious commands were found
 *
 * Example usage:
 * ```kotlin
 * class RootCommandDetector : CommandDetector() {
 *     override fun getCommands(): Array<String> {
 *         return arrayOf("su", "busybox", "magisk")
 *     }
 * }
 * ```
 *
 * @see TamperDetector
 */
abstract class CommandDetector : TamperDetector {
    /**
     * Checks if a specific command exists in the system PATH.
     *
     * This method uses the `which` command to determine if a given command is available
     * in the system. The presence of certain commands can indicate that the device has
     * been rooted or otherwise compromised.
     *
     * @param command The name of the command to check for
     * @return `true` if the command exists and is executable, `false` otherwise
     */
    private fun exists(command: String): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("which", command))
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            return !bufferedReader.readLine().isNullOrEmpty()
        } catch (e: Exception) {
            logger.e("Error checking for command: $command", e)
            return false
        } finally {
            process?.destroy()
        }
    }

    /**
     * Determines if the device has been tampered with by checking for suspicious commands.
     *
     * This method iterates through the list of commands provided by [getCommands] and
     * returns a confidence score based on whether any suspicious commands are found.
     * The presence of these commands typically indicates that the device has been rooted or compromised.
     *
     * @param context The Android context (not used in this implementation but required by interface)
     * @return A confidence score where:
     *         - `1.0` indicates suspicious commands were detected
     *         - `0.0` indicates no suspicious commands were found
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Running CommandDetector")
        for (command in getCommands()) {
            if (exists(command)) {
                logger.w("Command: $command found.")
                return 1.0
            }
        }
        logger.d("No suspicious commands found.")
        return 0.0
    }

    /**
     * Provides the list of commands to check for tampering detection.
     *
     * Subclasses must implement this method to return an array of command names
     * that should be checked for their presence on the system. These commands
     * are typically associated with rooted devices or security tools.
     *
     * @return An array of command names to check for
     */
    abstract fun getCommands(): Array<String>
}