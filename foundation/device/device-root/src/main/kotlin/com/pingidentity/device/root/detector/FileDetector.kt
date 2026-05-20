/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import java.io.File

/**
 * Abstract base class for detecting device tampering by checking for the presence of specific files.
 *
 * This detector works by searching for files that are commonly found on rooted or compromised devices
 * across various system directories. Subclasses must provide the list of suspicious filenames to
 * check for through the [getFilenames] method.
 *
 * The detector searches through a comprehensive list of system paths including:
 * - Standard system directories (/system/bin/, /system/xbin/)
 * - Root-specific locations (/su/bin/, /system/usr/we-need-root/)
 * - Local data directories (/data/local/, /data/local/bin/)
 * - Cache and development directories
 * - All paths from the system PATH environment variable
 *
 * Common files that might indicate tampering include:
 * - `su` (superuser binary)
 * - `busybox` (comprehensive Unix utilities)
 * - `magisk` (systemless root manager)
 * - Root management app binaries
 * - Custom recovery tools
 *
 * The scoring system returns a [Double] value:
 * - `1.0` indicates at least one suspicious file was found (high confidence of tampering)
 * - `0.0` indicates no suspicious files were found
 *
 * Example usage:
 * ```kotlin
 * class RootFileDetector : FileDetector() {
 *     override fun getFilenames(): List<String> {
 *         return listOf("su", "busybox", "magisk", "supersu")
 *     }
 * }
 * ```
 *
 * @see TamperDetector
 */
abstract class FileDetector : TamperDetector {
    /**
     * Determines if the device has been tampered with by checking for suspicious files.
     *
     * This method searches for any of the filenames returned by [getFilenames] across
     * all known system paths. If any suspicious file is found, the device is considered
     * to be tampered with.
     *
     * @param context The Android context (not used in this implementation but required by interface)
     * @return A confidence score where:
     *         - `1.0` indicates suspicious files were detected
     *         - `0.0` indicates no suspicious files were found
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Running FileDetector")
        return if (getFilenames().any { exists(it) }) {
            logger.w("Suspicious files found")
            1.0
        } else {
            logger.d("Suspicious files not found")
            0.0
        }
    }

    /**
     * Checks if a specific file exists in any of the system paths.
     *
     * This method searches through all available system paths (both predefined and
     * from the PATH environment variable) to determine if the specified file exists.
     *
     * @param fileName The name of the file to search for
     * @return `true` if the file exists in any searched path, `false` otherwise
     */
    private fun exists(fileName: String) = getPaths().any {
        File(it, fileName).exists()
    }

    /**
     * Retrieves all system paths where suspicious files might be located.
     *
     * This method combines the predefined list of common system directories with
     * all paths from the system's PATH environment variable to create a comprehensive
     * list of locations to search.
     *
     * @return A list of directory paths to search, or empty list if PATH is unavailable
     */
    private fun getPaths(): List<String> {
        val paths = ArrayList(PATHS)
        val sysPaths = System.getenv("PATH")

        if (sysPaths.isNullOrEmpty()) {
            return emptyList()
        }

        for (path in sysPaths.split(':')) {
            var currentPath = path
            if (!currentPath.endsWith('/')) {
                currentPath += '/'
            }

            if (!paths.contains(currentPath)) {
                paths.add(currentPath)
            }
        }

        return paths
    }

    /**
     * Provides the list of filenames to check for tampering detection.
     *
     * Subclasses must implement this method to return a list of filenames
     * that should be searched for across the system. These files are typically
     * associated with rooted devices, custom firmware, or security tools.
     *
     * @return A list of suspicious filenames to search for
     */
    abstract fun getFilenames(): List<String>

    companion object {
        /**
         * Predefined list of common system directories where suspicious files might be located.
         *
         * This list includes standard Android system directories, root-specific locations,
         * and other paths commonly used by rooting tools and custom firmware.
         *
         * The paths include:
         * - `/data/local/` - Local data storage
         * - `/data/local/bin/`, `/data/local/xbin/` - Local executable directories
         * - `/sbin/` - System binaries directory
         * - `/su/bin/` - Superuser binaries directory
         * - `/system/bin/`, `/system/xbin/` - System executable directories
         * - `/system/bin/.ext/`, `/system/bin/failsafe/` - Extended system directories
         * - `/system/sd/xbin/` - SD card system executables
         * - `/system/usr/we-need-root/` - Root access utilities
         * - `/cache/`, `/data/`, `/dev/` - Cache, data, and device directories
         */
        internal val PATHS = listOf(
            "/data/local/",
            "/data/local/bin/",
            "/data/local/xbin/",
            "/sbin/",
            "/su/bin/",
            "/system/bin/",
            "/system/bin/.ext/",
            "/system/bin/failsafe/",
            "/system/sd/xbin/",
            "/system/usr/we-need-root/",
            "/system/xbin/",
            "/cache/",
            "/data/",
            "/dev/"
        )
    }
}