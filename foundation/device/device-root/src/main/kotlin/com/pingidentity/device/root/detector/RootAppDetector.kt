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
 * Pre-configured tamper detector that identifies device rooting by checking for known root management applications.
 *
 * This detector extends [PackageDetector] and specifically looks for installed packages of popular
 * root management applications. It maintains a comprehensive list of known root applications that
 * are commonly found on rooted Android devices.
 *
 * The detector checks for the following categories of root applications:
 *
 * **Classic Root Managers:**
 * - Superuser (com.noshufou.android.su) - Original Android root management
 * - Superuser Elite (com.noshufou.android.su.elite) - Premium version
 * - SuperSU (eu.chainfire.supersu) - Popular root access manager by Chainfire
 * - Koush Superuser (com.koushikdutta.superuser) - CyanogenMod integrated superuser
 *
 * **Alternative Root Solutions:**
 * - Third Party Superuser (com.thirdparty.superuser) - Generic superuser implementations
 * - YellowADB (com.yellowes.su) - Alternative root management
 * - Magisk (com.topjohnwu.magisk) - Modern systemless root solution
 *
 * **One-Click Root Tools:**
 * - KingRoot (com.kingroot.kinguser) - Popular one-click rooting tool
 * - Kingo Root (com.kingo.root) - Android rooting utility
 * - OneClickRoot (com.smedialink.oneclickroot) - Simplified rooting tool
 * - Root Master (com.zhiqupk.root.global) - Chinese rooting application
 * - Framaroot (com.alephzain.framaroot) - Exploit-based rooting tool
 *
 * This detector provides a reliable method for identifying rooted devices by checking the package
 * manager for installed applications, which is harder to bypass than some file-based detection methods.
 *
 * Example usage:
 * ```kotlin
 * val detector = RootAppDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(RootAppDetector())
 *     }
 * }
 * ```
 *
 * @see PackageDetector
 */
object RootAppDetector: PackageDetector() {
    /**
     * Logger instance for logging detector operations and results.
     *
     * Defaults to [Logger.Companion.WARN] level to capture warnings and errors.
     */
    override var logger: Logger = Logger.WARN

    /**
     * Provides the list of known root management application package names.
     *
     * Returns a comprehensive list of package names for applications that are commonly
     * used for root access management on Android devices. The presence of any of these
     * packages typically indicates the device has been rooted.
     *
     * @return A list of root management application package names
     */
    override fun getPackages(): List<String> = CURRENT_KNOWN_ROOT_APPS

    /**
     * Comprehensive list of known root management application package names.
     *
     * This list includes popular root management applications across different categories:
     *
     * **Classic Superuser Applications:**
     * - `com.noshufou.android.su` - Original Superuser app
     * - `com.noshufou.android.su.elite` - Superuser Elite version
     * - `eu.chainfire.supersu` - SuperSU by Chainfire
     * - `com.koushikdutta.superuser` - Koush's Superuser (CyanogenMod)
     * - `com.thirdparty.superuser` - Third-party Superuser implementations
     * - `com.yellowes.su` - YellowADB Superuser
     *
     * **Modern Root Solutions:**
     * - `com.topjohnwu.magisk` - Magisk systemless root
     *
     * **One-Click Rooting Tools:**
     * - `com.kingroot.kinguser` - KingRoot/KingUser
     * - `com.kingo.root` - Kingo Root
     * - `com.smedialink.oneclickroot` - OneClickRoot
     * - `com.zhiqupk.root.global` - Root Master (global version)
     * - `com.alephzain.framaroot` - Framaroot exploit tool
     *
     * This list is maintained to include the most commonly encountered root management
     * applications and should be updated as new tools become popular.
     */
    internal val CURRENT_KNOWN_ROOT_APPS = listOf(
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot",
    )

    override suspend fun analyze(context: Context): Double {
        logger.i("Running RootAppDetector")
        return super.analyze(context)
    }
}