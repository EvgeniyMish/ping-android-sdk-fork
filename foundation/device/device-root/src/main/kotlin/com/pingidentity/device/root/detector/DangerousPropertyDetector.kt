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
 * Pre-configured tamper detector that checks for dangerous Android system properties.
 *
 * This detector extends [SystemPropertyDetector] and specifically looks for system properties
 * that indicate the device is running in a potentially compromised or insecure state.
 *
 * The detector checks for two critical security properties:
 *
 * **ro.debuggable=1**: Indicates the system is built with debugging enabled, which:
 * - Allows applications to be debugged even in production
 * - Enables additional system access and debugging tools
 * - Is typically only present in development or custom builds
 * - May indicate a custom ROM or modified firmware
 *
 * **ro.secure=0**: Indicates the system boot process is not secure, which:
 * - Allows unsigned system images to boot
 * - Bypasses Android Verified Boot security checks
 * - Is commonly found on rooted devices or custom firmware
 * - Represents a significant security vulnerability
 *
 * These properties are set during the Android build process and are difficult to modify
 * after compilation, making them reliable indicators of system integrity.
 *
 * The scoring system returns a [Double] value:
 * - `1.0` indicates at least one dangerous property was found (high confidence of tampering)
 * - `0.0` indicates no dangerous properties were found
 *
 * Example usage:
 * ```kotlin
 * val detector = DangerousPropertyDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(DangerousPropertyDetector())
 *     }
 * }
 * ```
 *
 * @see SystemPropertyDetector
 */
object DangerousPropertyDetector : SystemPropertyDetector() {
    /**
     * Logger instance for logging detector operations and results.
     *
     * Defaults to [Logger.Companion.WARN] level to capture warnings and errors.
     */
    override var logger: Logger = Logger.WARN
    /**
     * Provides the map of dangerous system properties to check for tampering detection.
     *
     * Returns a predefined set of property-value pairs that indicate potential security
     * compromises when present on the device:
     *
     * - `ro.debuggable` → `1`: System built with debug capabilities enabled
     * - `ro.secure` → `0`: System boot security is disabled
     *
     * @return A map of dangerous property names to their compromising values
     */
    override fun getProperties(): Map<String, String> {
        return mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0",
        )
    }

    override suspend fun analyze(context: Context): Double {
        logger.i("Running DangerousPropertyDetector")
        return super.analyze(context)
    }
}