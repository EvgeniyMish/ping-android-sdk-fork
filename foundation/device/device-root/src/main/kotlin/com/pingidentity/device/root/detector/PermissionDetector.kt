/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import android.os.Build
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import java.util.Scanner

/**
 * Tamper detector that identifies device compromise by examining filesystem mount permissions.
 *
 * This detector works by analyzing the output of the `mount` command to determine if
 * critical system directories are mounted with write permissions. On a secure Android device,
 * system directories should be mounted as read-only to prevent unauthorized modifications.
 *
 * The detector checks for read-write ("rw") mount options on the following critical paths:
 * - `/system` - Core Android system files
 * - `/system/bin` - System binary executables
 * - `/system/sbin` - System super-user binaries
 * - `/system/xbin` - Extended system binaries
 * - `/vendor/bin` - Vendor-specific binaries
 * - `/sbin` - System binaries directory
 * - `/etc` - System configuration files
 *
 * If any of these directories are mounted with write permissions, it typically indicates:
 * - The device has been rooted
 * - System partition has been remounted as writable
 * - Custom firmware or ROM is installed
 * - Security mechanisms have been bypassed
 *
 * The detector handles different Android SDK versions as the `mount` command output
 * format varies between Android Marshmallow (API 23) and later versions.
 *
 * The scoring system returns a [Double] value:
 * - `1.0` indicates at least one critical path is writable (high confidence of tampering)
 * - `0.0` indicates all critical directories are properly read-only
 *
 * Example usage:
 * ```kotlin
 * val detector = PermissionDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(PermissionDetector())
 *     }
 * }
 * ```
 *
 * @see TamperDetector
 */
class PermissionDetector(
    private val androidBuildSdkProvider: AndroidBuildSdkProvider = DefaultAndroidBuildSdkProvider(),
    override var logger: Logger = Logger.WARN,
) : TamperDetector {

    /**
     * Determines if the device has been tampered with by checking filesystem mount permissions.
     *
     * This method executes the `mount` command and analyzes each mount point to determine
     * if any critical system directories are mounted with write permissions. The analysis
     * accounts for different output formats between Android SDK versions.
     *
     * For Android SDK > 23 (Marshmallow), the mount format is:
     * `device mountpoint fstype options1 options2 (options3)`
     *
     * For Android SDK <= 23, the format is:
     * `device mountpoint fstype options`
     *
     * @param context The Android context (not used in this implementation but required by interface)
     * @return A confidence score where:
     *         - `1.0` indicates critical system directories are mounted as writable
     *         - `0.0` indicates all critical directories are properly read-only
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Running PermissionDetector")
        val sdkVersion = androidBuildSdkProvider.getSdkVersion()
        for (line in mountReader()) {
            val args = line.trim().split(Regex("\\s+"))
            if (args.size < 4 || args.size < 6) {
                logger.e("Error formatting mount line: $line")
                continue
            }
            var mountPoint: String
            var mountOptions: String
            if (sdkVersion > Build.VERSION_CODES.M) {
                mountPoint = args[2]
                mountOptions = args[5]
            } else {
                mountPoint = args[1]
                mountOptions = args[3]
            }

            for (pathToCheck in NOT_WRITABLE_PATH) {
                if (mountPoint.equals(pathToCheck, true)) {
                    if (sdkVersion > Build.VERSION_CODES.M) {
                        mountOptions = mountOptions.replace("(", "")
                        mountOptions = mountOptions.replace(")", "")
                    }

                    for (option in mountOptions.split(",")) {
                        if (option.equals("rw", true)) {
                            return 1.0
                        }
                    }
                }
            }
        }
        return 0.0
    }

    /**
     * Reads and parses the output of the `mount` command.
     *
     * This method executes the system `mount` command to retrieve information about
     * all currently mounted filesystems. The output is parsed into individual lines
     * for analysis of mount points and their permissions.
     *
     * @return A list of strings representing each line of the mount command output,
     *         or an empty list if the command fails or cannot be executed
     */
    private fun mountReader(): List<String> {
        try {
            val inputStream = Runtime.getRuntime().exec("mount").inputStream
            if (inputStream == null) return emptyList()
            return Scanner(inputStream).useDelimiter("\\A").next().split("\n")
        } catch (exception: Exception) {
            logger.e("Error reading mount points", exception)
            return emptyList()
        }
    }

    companion object {
        /**
         * List of critical system paths that should never be writable on a secure device.
         *
         * These directories contain essential system files, binaries, and configurations
         * that must remain read-only to maintain system integrity and security:
         *
         * - `/system` - Main Android system partition
         * - `/system/bin` - Core system executable binaries
         * - `/system/sbin` - System super-user binaries
         * - `/system/xbin` - Extended system binaries and utilities
         * - `/vendor/bin` - Vendor-specific executable binaries
         * - `/sbin` - System binaries directory (alternative location)
         * - `/etc` - System configuration and settings files
         */
        private val NOT_WRITABLE_PATH = listOf(
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/sbin",
            "/etc",
        )
    }
}

/**
 * Provider interface for Android Build SDK version information.
 *
 * This interface abstracts access to the Android SDK version to enable testing
 * and provide flexibility in determining the current Android version. Different
 * implementations can return different SDK versions for testing various Android
 * version-specific behaviors.
 *
 * The SDK version is used by [PermissionDetector] to handle different mount
 * command output formats between Android versions.
 *
 */
interface AndroidBuildSdkProvider {
    /**
     * Returns the Android SDK version.
     *
     * @return The SDK version as an integer. Default implementation returns
     *         [Build.VERSION_CODES.M] (API level 23) for compatibility.
     */
    fun getSdkVersion(): Int = Build.VERSION_CODES.M
}

/**
 * Default implementation of [AndroidBuildSdkProvider] that returns the actual device SDK version.
 *
 * This implementation provides access to the real Android SDK version of the current device
 * by returning [Build.VERSION.SDK_INT]. This is the standard implementation used in
 * production code.
 *

 * @see AndroidBuildSdkProvider
 */
class DefaultAndroidBuildSdkProvider : AndroidBuildSdkProvider {
    /**
     * Returns the actual Android SDK version of the current device.
     *
     * @return The current device's SDK version from [Build.VERSION.SDK_INT]
     */
    override fun getSdkVersion(): Int = Build.VERSION.SDK_INT
}