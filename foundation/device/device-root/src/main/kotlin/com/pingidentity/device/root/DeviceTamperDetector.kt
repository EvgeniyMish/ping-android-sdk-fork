/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device

import com.pingidentity.android.ContextProvider
import com.pingidentity.device.root.detector.BuildTagsDetector
import com.pingidentity.device.root.detector.BusyBoxProgramFileDetector
import com.pingidentity.device.root.detector.DangerousPropertyDetector
import com.pingidentity.device.root.detector.NativeDetector
import com.pingidentity.device.root.detector.PermissionDetector
import com.pingidentity.device.root.detector.RootApkDetector
import com.pingidentity.device.root.detector.RootAppDetector
import com.pingidentity.device.root.detector.RootCloakingAppDetector
import com.pingidentity.device.root.detector.RootProgramFileDetector
import com.pingidentity.device.root.detector.RootRequiredAppDetector
import com.pingidentity.device.root.detector.SuCommandDetector
import com.pingidentity.device.root.detector.TamperDetector
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import com.pingidentity.utils.PingDsl
import kotlin.math.max


/**
 * Provides the default comprehensive set of tamper detectors for device security analysis.
 *
 * This function returns a configuration block that adds a complete suite of tamper detection
 * mechanisms to analyze various aspects of device compromise. The default detector set includes
 * multiple detection strategies to provide thorough coverage of common rooting and tampering methods.
 *
 * **Included Detectors:**
 *
 * **Build & System Property Analysis:**
 * - [BuildTagsDetector] - Checks for test-keys in build tags indicating unofficial firmware
 * - [DangerousPropertyDetector] - Examines system properties for insecure configurations
 *
 * **File System Analysis:**
 * - [BusyBoxProgramFileDetector] - Detects BusyBox Unix utilities package
 * - [RootProgramFileDetector] - Searches for core root binaries (su, magisk)
 * - [RootApkDetector] - Looks for root management APK files in system directories
 * - [NativeDetector] - Uses JNI-based low-level file detection
 *
 * **Application Analysis:**
 * - [RootAppDetector] - Identifies installed root management applications
 * - [RootRequiredAppDetector] - Detects apps that require root access to function
 * - [RootCloakingAppDetector] - Finds applications designed to hide root access
 *
 * **System Security Analysis:**
 * - [PermissionDetector] - Examines filesystem mount permissions for write access
 * - [SuCommandDetector] - Checks for superuser command availability in PATH
 *
 * This comprehensive approach provides multiple layers of detection, making it difficult
 * for sophisticated tampering attempts to evade all detection mechanisms simultaneously.
 *
 * @return A configuration block that adds all default tamper detectors to the detector list
 */
fun DefaultTamperDetector(): MutableList<TamperDetector>.() -> Unit = {
    add(BuildTagsDetector())
    add(BusyBoxProgramFileDetector)
    add(DangerousPropertyDetector)
    add(NativeDetector())
    add(PermissionDetector())
    add(RootApkDetector)
    add(RootAppDetector)
    add(RootRequiredAppDetector)
    add(RootCloakingAppDetector)
    add(RootProgramFileDetector)
    add(SuCommandDetector)
}

/**
 * DSL configuration class for device tamper detection settings.
 *
 * This class provides a domain-specific language for configuring various tamper detection
 * mechanisms. It allows developers to easily add and configure multiple detectors using
 * a fluent API approach.
 *
 * Example usage:
 * ```kotlin
 * val isTampered = analyze {
 *     detector {
 *         add(RootCommandDetector())
 *         add(SystemPropertyDetector())
 *         add(CustomDetector { checkCustomCondition() })
 *     }
 * }
 * ```
 *
 */
@PingDsl
class DeviceTamperConfig {
    /**
     * Internal list of tamper detectors that will be executed during analysis.
     */
    internal val tamperDetectors: MutableList<TamperDetector> = mutableListOf()

    /**
     * Logger instance for logging within the tamper detection process.
     *
     * This property allows customization of the logging behavior. By default, it is set to
     * log warnings and above. Developers can provide their own logger implementation to
     * integrate with existing logging frameworks or systems.
     */
    var logger: Logger = Logger.WARN

    /**
     * Configures the list of tamper detectors to be used in the analysis.
     *
     * This method accepts a configuration block that operates on the internal list
     * of detectors, allowing for easy addition and configuration of multiple
     * detection mechanisms.
     *
     * @param block Configuration block for adding and configuring detectors
     */
    fun detector(block: MutableList<TamperDetector>.() -> Unit) {
        tamperDetectors.apply(block)
    }
}

/**
 * Analyzes the device for signs of tampering using the configured detectors.
 *
 * This function provides the main entry point for device tamper detection. It creates
 * a configuration using the provided block, executes all configured detectors, and
 * returns a confidence score based on the results.
 *
 * The analysis runs asynchronously and can be called from coroutines. If no configuration
 * is provided, it uses the default detector set.
 *
 * Example usage:
 * ```kotlin
 * // Using default detectors
 * val isTampered = analyze()
 *
 * // Using custom detectors
 * val isTampered = analyze {
 *     detector {
 *         add(CommandDetector { arrayOf("su", "busybox") })
 *         add(SystemPropertyDetector { mapOf("ro.secure" to "0") })
 *     }
 * }
 * ```
 *
 * @param block Configuration block for setting up tamper detectors. Defaults to using [DefaultTamperDetector]
 * @return A confidence score where:
 *         - `1.0` indicates high confidence that tampering is detected
 *         - `0.0` indicates no tampering detected
 *         - Values between 0.0 and 1.0 indicate varying levels of suspicion
 * @throws SecurityException if detection cannot be performed due to security restrictions
 *
 */
suspend fun analyze(
    block: DeviceTamperConfig.() -> Unit = { }
): Double {
    val config = DeviceTamperConfig()
    config.tamperDetectors.forEach { it.logger = config.logger }
    config.apply(block)

    val detectors = config.tamperDetectors
    var max = 0.0
    for (detector in detectors) {
        config.logger.i("Running detector: ${detector::class.simpleName}")
        max = max(max, detector.analyze(ContextProvider.context))
        if (max >= 1) {
            return max
        }
    }
    return max
}
