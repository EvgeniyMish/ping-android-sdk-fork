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

/**
 * Pre-configured tamper detector that checks Android build tags for signs of tampering.
 *
 * This detector examines the [Build.TAGS] property to determine if the device is running
 * on unofficial or test builds. Official Android releases are signed with release keys,
 * while custom ROMs, developer builds, and other unofficial firmware typically use test keys.
 *
 * The presence of "test-keys" in the build tags indicates:
 * - Custom ROM or modified firmware
 * - Developer/debug builds
 * - Unofficial Android distributions
 * - Potentially compromised system integrity
 *
 * This detector is implemented as a lazy-initialized singleton using the [TamperDetector]
 * factory function for optimal performance and memory usage.
 *
 * Example usage:
 * ```kotlin
 * val isTampered = analyze {
 *     detector {
 *         add(BuildTagsDetector)
 *     }
 * }
 * ```
 *
 * @see Build.TAGS
 * @see TamperDetector
 */
class BuildTagsDetector(
    private val androidBuildTagProvider: AndroidBuildTagProvider = DefaultAndroidBuildTagProvider(),
    override var logger: Logger = Logger.WARN,
) : TamperDetector {

    /**
     * Analyzes the device's build tags to detect tampering.
     *
     * Checks if the build tags contain the "test-keys" string, which is a strong indicator
     * of a non-official or modified Android build.
     *
     * @param context The Android context (not used in this implementation)
     * @return `1.0` if "test-keys" is found, `0.0` otherwise
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Running BuildTagsDetector")
        val buildTags = androidBuildTagProvider.getBuildTags()
        return if (buildTags?.contains(TEST_KEYS) == true) {
            logger.w("Build tags indicate tampering: $buildTags")
            1.0
        } else {
            logger.d("No tampering detected")
            0.0
        }
    }
}

/**
 * Constant representing the test key signature found in unofficial Android builds.
 *
 * This string is present in [Build.TAGS] when the Android system has been built
 * using test keys instead of official release keys, indicating a potentially
 * compromised or unofficial firmware.
 */
internal const val TEST_KEYS = "test-keys"

/**
 * Provider interface for accessing Android build tags.
 *
 * This abstraction allows for easier testing and mocking of build tag values.
 */
interface AndroidBuildTagProvider {
    /**
     * Returns the build tags string from the Android system.
     *
     * @return The build tags, or null if unavailable.
     */
    fun getBuildTags(): String? = null
}

/**
 * Default implementation of [AndroidBuildTagProvider] that returns the actual build tags.
 */
class DefaultAndroidBuildTagProvider : AndroidBuildTagProvider {
    override fun getBuildTags(): String? = Build.TAGS
}