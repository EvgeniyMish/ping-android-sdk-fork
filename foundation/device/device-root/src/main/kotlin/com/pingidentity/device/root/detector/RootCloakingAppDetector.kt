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
 * Pre-configured tamper detector that identifies device compromise by checking for root cloaking applications.
 *
 * This detector extends [PackageDetector] and specifically looks for installed packages of applications
 * designed to hide or cloak root access from other applications. The presence of these applications
 * is often a strong indicator that the device has been rooted, as they serve no purpose on unrooted devices.
 *
 * Root cloaking applications work by:
 * - Intercepting system calls that would reveal root access
 * - Hiding root binaries and files from detection
 * - Spoofing system properties to appear unrooted
 * - Blocking or modifying responses from root detection methods
 * - Using framework hooks (like Xposed) to manipulate app behavior
 *
 * The detector checks for the following categories of cloaking applications:
 *
 * **Direct Root Cloaking Tools:**
 * - RootCloak (com.devadvance.rootcloak) - Popular root hiding application
 * - RootCloak Plus (com.devadvance.rootcloakplus) - Enhanced version with more features
 * - Hide My Root (com.amphoras.hidemyroot) - Root concealment tool
 * - Hide My Root Ad-Free (com.amphoras.hidemyrootadfree) - Premium ad-free version
 * - Hide Root Premium (com.formyhm.hiderootPremium) - Commercial root hiding solution
 * - Hide Root (com.formyhm.hideroot) - Basic root hiding functionality
 *
 * **Framework-Based Cloaking:**
 * - Xposed Installer (de.robv.android.xposed.installer) - Framework for system modifications
 * - Substrate (com.saurik.substrate) - Runtime manipulation framework
 *
 * **Temporary Root Management:**
 * - Temp Root Remove (com.zachspong.temprootremovejb) - Temporary root access removal
 *
 * The presence of any of these applications suggests sophisticated attempts to hide device tampering,
 * which ironically makes them strong indicators of compromise.
 *
 * Example usage:
 * ```kotlin
 * val detector = RootCloakingAppDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(RootCloakingAppDetector())
 *     }
 * }
 * ```
 *
 * @see PackageDetector
 */
object RootCloakingAppDetector : PackageDetector() {
    /**
     * Provides the list of known root cloaking application package names.
     *
     * Returns a comprehensive list of package names for applications that are designed
     * to hide or cloak root access from other applications. The presence of these
     * applications strongly suggests the device has been rooted and efforts are being
     * made to conceal this fact.
     *
     * @return A list of root cloaking application package names
     */
    override fun getPackages(): List<String> = CURRENT_ROOT_CLOAKING_APPS

    /**
     * Logger instance for logging detector operations and results.
     *
     * Defaults to [Logger.Companion.WARN] level to capture warnings and errors.
     */
    override var logger: Logger = Logger.WARN

    /**
     * Comprehensive list of known root cloaking application package names.
     *
     * This list includes applications specifically designed to hide root access:
     *
     * **Direct Root Cloaking Applications:**
     * - `com.devadvance.rootcloak` - RootCloak: Popular root hiding tool
     * - `com.devadvance.rootcloakplus` - RootCloak Plus: Enhanced version with additional features
     * - `com.amphoras.hidemyroot` - Hide My Root: Root concealment application
     * - `com.amphoras.hidemyrootadfree` - Hide My Root Ad-Free: Premium version without advertisements
     * - `com.formyhm.hiderootPremium` - Hide Root Premium: Commercial root hiding solution
     * - `com.formyhm.hideroot` - Hide Root: Basic root hiding functionality
     *
     * **Framework-Based Modification Tools:**
     * - `de.robv.android.xposed.installer` - Xposed Framework: System-level modification framework
     * - `com.saurik.substrate` - Substrate: Runtime code manipulation framework
     *
     * **Temporary Root Management:**
     * - `com.zachspong.temprootremovejb` - Temp Root Remove JB: Temporary root access management
     *
     * These applications serve no legitimate purpose on unrooted devices, making their presence
     * a strong indicator of device tampering and attempts to conceal root access.
     */
    internal val CURRENT_ROOT_CLOAKING_APPS = listOf(
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot",
        "com.amphoras.hidemyrootadfree",
        "com.formyhm.hiderootPremium",
        "com.formyhm.hideroot",
    )

    override suspend fun analyze(context: Context): Double {
        logger.i("Running RootCloakingAppDetector")
        return super.analyze(context)
    }
}