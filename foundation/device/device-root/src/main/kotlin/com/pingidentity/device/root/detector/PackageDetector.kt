/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import android.content.pm.PackageManager

/**
 * Abstract base class for detecting device tampering by checking for the presence of specific installed packages.
 *
 * This detector works by examining the list of installed applications to identify packages that are commonly
 * associated with rooted devices, security tools, or other potentially compromising software. Subclasses must
 * provide the list of suspicious package names through the [getPackages] method.
 *
 * The detector uses the Android PackageManager to query for installed applications, which provides a reliable
 * way to detect software that might indicate device compromise. This method can detect:
 * - Root management applications (SuperSU, Magisk Manager, etc.)
 * - Security testing tools and penetration testing apps
 * - Emulator detection bypassing tools
 * - Custom recovery applications
 * - Developer tools that shouldn't be on production devices
 *
 * Common suspicious packages include:
 * - `eu.chainfire.supersu` (SuperSU root manager)
 * - `com.topjohnwu.magisk` (Magisk root manager)
 * - `com.koushikdutta.superuser` (Superuser root app)
 * - `com.zachspong.temprootremovejb` (Root removal tools)
 * - `com.ramdroid.appquarantine` (App quarantine tools)
 *
 * The scoring system returns a [Double] value:
 * - `1.0` indicates at least one suspicious package was found (high confidence of tampering)
 * - `0.0` indicates no suspicious packages were found
 *
 * Example usage:
 * ```kotlin
 * class RootPackageDetector : PackageDetector() {
 *     override fun getPackages(): List<String> {
 *         return listOf(
 *             "eu.chainfire.supersu",
 *             "com.topjohnwu.magisk",
 *             "com.koushikdutta.superuser"
 *         )
 *     }
 * }
 * ```
 *
 * @see TamperDetector
 */
abstract class PackageDetector : TamperDetector {
    /**
     * Checks if any of the specified packages are installed on the device.
     *
     * This method iterates through the provided list of package names and uses the
     * Android PackageManager to determine if each package is installed. Returns true
     * as soon as any suspicious package is found.
     *
     * @param context The Android context used to access the PackageManager
     * @param packages List of package names to check for installation
     * @return `true` if any of the specified packages are installed, `false` otherwise
     */
    fun exists(context: Context, packages: List<String>): Boolean {
        val packageManager = context.packageManager
        for (packageName in packages) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                logger.w("Package found: $packageName")
                return true
            } catch (_: PackageManager.NameNotFoundException) {
                logger.d("Package not found: $packageName")
            }
        }
        return false
    }

    /**
     * Determines if the device has been tampered with by checking for suspicious installed packages.
     *
     * This method uses the package list provided by [getPackages] to check if any known
     * tampering-related applications are installed on the device. If any suspicious package
     * is found, the device is considered to be compromised.
     *
     * @param context The Android context used for package detection
     * @return A confidence score where:
     *         - `1.0` indicates suspicious packages were detected
     *         - `0.0` indicates no suspicious packages were found
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Running PackageDetector")
        return if (exists(context, getPackages())) {
            1.0
        } else {
            0.0
        }
    }

    /**
     * Provides the list of suspicious package names to check for tampering detection.
     *
     * Subclasses must implement this method to return a list of package names that
     * should be checked for their presence on the device. These packages are typically
     * associated with rooted devices, security tools, or other potentially compromising software.
     *
     * @return A list of suspicious package names to search for
     */
    internal abstract fun getPackages(): List<String>
}