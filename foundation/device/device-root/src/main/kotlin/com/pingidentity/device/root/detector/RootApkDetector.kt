/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import java.io.File

/**
 * Tamper detector that identifies device rooting by checking for root management APK files.
 *
 * This detector works by searching for APK files of popular root management applications
 * that are commonly installed in the system partition on rooted devices. Unlike package-based
 * detection that checks installed applications, this detector looks for the actual APK files
 * in system directories where they are typically placed by rooting tools.
 *
 * The detector checks for APK files of well-known root management applications:
 * - **Superuser.apk** - One of the original Android root management apps
 * - **SuperSU.apk** - Popular root access management application by Chainfire
 * - **magisk.apk** - Modern systemless root solution by topjohnwu
 *
 * These APK files are typically installed to `/system/app/` directory during the rooting
 * process and remain there even if the applications are hidden from the launcher or
 * package manager queries. This makes file-based detection more reliable than package-based
 * detection in some scenarios.
 *
 * The detection is performed by checking file existence in the filesystem, which:
 * - Can detect root apps even if they're hidden from package manager
 * - Works regardless of whether the apps are currently active
 * - Is difficult to bypass without modifying the filesystem directly
 * - Provides evidence of past rooting attempts even if tools were removed
 *
 * The scoring system returns a [Double] value:
 * - `1.0` indicates at least one root APK file was found (high confidence of tampering)
 * - `0.0` indicates no root APK files were found
 *
 * Example usage:
 * ```kotlin
 * val detector = RootApkDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(RootApkDetector())
 *     }
 * }
 * ```
 *
 * @see TamperDetector
 */
object RootApkDetector : TamperDetector {
    /**
     * Logger instance for logging detector operations and results.
     *
     * Defaults to [Logger.Companion.WARN] level to capture warnings and errors.
     */
    override var logger: Logger = Logger.WARN
    /**
     * Determines if the device has been tampered with by checking for root management APK files.
     *
     * This method searches for the presence of known root management application APK files
     * in system directories. If any of these files are found, it indicates the device
     * has been rooted or compromised.
     *
     * @param context The Android context (not used in this implementation but required by interface)
     * @return A confidence score where:
     *         - `1.0` indicates root management APK files were detected
     *         - `0.0` indicates no root APK files were found
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Running RootApkDetector")
        return if (exists(ROOT_APK)) {
            1.0
        } else {
            0.0
        }
    }

    /**
     * Checks if any of the specified APK files exist in the filesystem.
     *
     * This method iterates through a list of APK file paths and checks for their
     * existence using the File API. Returns true as soon as any file is found.
     * All exceptions are caught and handled gracefully to prevent crashes.
     *
     * @param apks List of APK file paths to check for existence
     * @return `true` if any of the specified APK files exist, `false` otherwise
     */
    private fun exists(apks: List<String>): Boolean {
        try {
            for (apk in apks) {
                logger.d("Checking for root APK: $apk")
                if (File(apk).exists()) {
                    logger.w("Root APK found: $apk")
                    return true
                }
            }
        } catch (exception: Exception) {
            logger.e("Failed to check apks", exception)
        }
        return false
    }

    /**
     * List of known root management APK file paths to check for tampering detection.
     *
     * These paths represent the typical installation locations for popular root
     * management applications in the Android system partition:
     *
     * - `/system/app/Superuser.apk` - Original Superuser root management app
     * - `/system/app/SuperSU.apk` - Popular SuperSU root access manager
     * - `/system/app/magisk.apk` - Modern Magisk systemless root solution
     *
     * These APK files are installed to the system partition during rooting and
     * typically remain there even if the applications are later hidden or removed
     * from normal application lists.
     */
    internal val ROOT_APK = listOf<String>(
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk",
        "/system/app/magisk.apk",
    )
}