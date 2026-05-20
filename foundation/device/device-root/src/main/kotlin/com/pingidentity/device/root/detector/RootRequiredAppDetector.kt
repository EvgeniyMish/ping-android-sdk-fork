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
 * Pre-configured tamper detector that identifies device compromise by checking for applications that require root access.
 *
 * This detector extends [PackageDetector] and specifically looks for installed packages of applications
 * that inherently require root access to function properly. The presence of these applications is a
 * strong indicator that the device has been rooted, as they would be non-functional on unrooted devices.
 *
 * The detector checks for several categories of root-dependent applications:
 *
 * **ROM Management Tools:**
 * - ROM Manager (com.koushikdutta.rommanager) - Custom ROM installation and management
 * - ROM Manager License (com.koushikdutta.rommanager.license) - Premium version
 *
 * **App Modification & Patching Tools:**
 * - Lucky Patcher (com.dimonvideo.luckypatcher, com.chelpus.lackypatch, com.chelpus.luckypatcher) - App modification and license bypassing
 * - Freedom (cc.madkite.freedom) - In-app purchase manipulation
 * - Game CIH (com.cih.game_cih) - Game modification tool
 * - Xmod Games (com.xmodgame) - Game modification platform
 *
 * **Security & Quarantine Tools:**
 * - App Quarantine (com.ramdroid.appquarantine, com.ramdroid.appquarantinepro) - App isolation and management
 *
 * **Alternative App Stores & Piracy Tools:**
 * - BlackMart (com.blackmartalpha, org.blackmart.market) - Alternative app marketplace
 * - Mobilism (org.mobilism.android) - App sharing community
 * - All In One Free (com.allinone.free) - Free app repository
 * - RepoTRoid (com.repodroid.app) - App repository
 *
 * **Framework Modification Tools:**
 * - EdXposed Manager (com.solohsu.android.edxp.manager, org.meowcat.edxposed.manager) - Xposed framework variants
 *
 * **Billing & License Bypass:**
 * - Various billing service modifications (com.android.vending.billing.InAppBillingService.*)
 * - Hack tools (org.creeplays.hack, com.baseappfull.fwd)
 *
 * These applications are designed to modify system behavior, bypass security restrictions, or provide
 * functionality that is only possible with elevated privileges. Their presence strongly suggests
 * device tampering and potential security risks.
 *
 * Example usage:
 * ```kotlin
 * val detector = RootRequiredAppDetector()
 * val isTampered = detector.analyze(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(RootRequiredAppDetector())
 *     }
 * }
 * ```
 *
 * @see PackageDetector
 */
object RootRequiredAppDetector : PackageDetector() {
    /**
     * Logger instance for logging detection process and results.
     */
    override var logger: Logger = Logger.WARN

    /**
     * Provides the list of application package names that require root access to function.
     *
     * Returns a comprehensive list of package names for applications that are designed
     * to work only on rooted devices. The presence of these applications indicates
     * the device has been rooted, as they would be non-functional otherwise.
     *
     * @return A list of root-dependent application package names
     */
    override fun getPackages(): List<String> = CURRENT_KNOWN_APPS_REQUIRE_ROOT

    /**
     * Determines if the device has been tampered with by checking for root-dependent applications.
     *
     * This method uses the parent implementation to check if any of the applications
     * that require root access are installed on the device.
     *
     * @param context The Android context used for package detection
     * @return `true` if any root-dependent applications are detected, `false` otherwise
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Checking for root-dependent apps")
        return super.analyze(context)
    }

    /**
     * Comprehensive list of application package names that require root access to function properly.
     *
     * This list includes applications across multiple categories that are designed to work
     * exclusively on rooted devices:
     *
     * **ROM Management:**
     * - `com.koushikdutta.rommanager` - ROM Manager: Custom ROM installation tool
     * - `com.koushikdutta.rommanager.license` - ROM Manager License: Premium version
     *
     * **App Modification & Patching:**
     * - `com.dimonvideo.luckypatcher` - Lucky Patcher: App modification and license bypass
     * - `com.chelpus.lackypatch` - Lucky Patcher variant
     * - `com.chelpus.luckypatcher` - Lucky Patcher alternative package
     * - `cc.madkite.freedom` - Freedom: In-app purchase manipulation
     * - `com.cih.game_cih` - Game CIH: Game modification tool
     * - `com.xmodgame` - Xmod Games: Game modification platform
     *
     * **Security & App Management:**
     * - `com.ramdroid.appquarantine` - App Quarantine: Application isolation
     * - `com.ramdroid.appquarantinepro` - App Quarantine Pro: Premium version
     *
     * **Alternative Markets & Piracy:**
     * - `com.blackmartalpha` - BlackMart Alpha: Alternative app store
     * - `org.blackmart.market` - BlackMart Market: Piracy-focused marketplace
     * - `com.allinone.free` - All In One Free: Free app repository
     * - `com.repodroid.app` - RepoTRoid: App repository
     * - `org.mobilism.android` - Mobilism: App sharing community
     *
     * **System Modification Frameworks:**
     * - `com.solohsu.android.edxp.manager` - EdXposed Manager: Framework manager
     * - `org.meowcat.edxposed.manager` - MEOWCat EdXposed: Alternative manager
     *
     * **Billing & License Bypass Tools:**
     * - Various billing service modifications and hack tools
     * - Custom system update spoofing applications
     *
     * The presence of any of these applications indicates sophisticated device modification
     * requiring root access and suggests potential security risks.
     */
    internal val CURRENT_KNOWN_APPS_REQUIRE_ROOT = listOf(
        "com.koushikdutta.rommanager",
        "com.koushikdutta.rommanager.license",
        "com.dimonvideo.luckypatcher",
        "com.chelpus.lackypatch",
        "com.ramdroid.appquarantine",
        "com.ramdroid.appquarantinepro",
        "com.android.vending.billing.InAppBillingService.COIN",
        "com.android.vending.billing.InAppBillingService.LUCK",
        "com.chelpus.luckypatcher",
        "com.blackmartalpha",
        "org.blackmart.market",
        "com.allinone.free",
        "com.repodroid.app",
        "org.creeplays.hack",
        "com.baseappfull.fwd",
        "com.zmapp",
        "com.dv.marketmod.installer",
        "org.mobilism.android",
        "com.android.wp.net.log",
        "com.android.camera.update",
        "cc.madkite.freedom",
        "com.solohsu.android.edxp.manager",
        "org.meowcat.edxposed.manager",
        "com.xmodgame",
        "com.cih.game_cih",
        "com.charles.lpoqasert",
        "catch_.me_.if_.you_.can_",
    )
}