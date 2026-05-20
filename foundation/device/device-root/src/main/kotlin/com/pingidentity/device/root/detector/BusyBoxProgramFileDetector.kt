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
 * Pre-configured tamper detector that identifies device rooting by checking for the BusyBox program file.
 *
 * This detector extends [FileDetector] and specifically looks for the presence of the `busybox`
 * binary in the device filesystem. BusyBox is a comprehensive Unix utilities package that combines
 * many common POSIX utilities into a single executable, making it a popular choice for rooted
 * Android devices and custom firmware.
 *
 * **What is BusyBox:**
 * - A collection of Unix utilities in a single binary
 * - Provides commands like `ls`, `cp`, `mv`, `grep`, `awk`, `sed`, and many others
 * - Significantly smaller than having separate binaries for each utility
 * - Commonly included in custom ROMs, rooting tools, and embedded systems
 * - Often referred to as "The Swiss Army Knife of Embedded Linux"
 *
 * **Why BusyBox indicates tampering:**
 * - Stock Android devices do not include BusyBox by default
 * - BusyBox is typically installed as part of rooting procedures
 * - Custom ROMs and firmware often include BusyBox for enhanced functionality
 * - Rooting tools frequently install BusyBox to provide additional command-line utilities
 * - Its presence suggests system-level modifications have been made
 *
 * **Detection methodology:**
 * The detector searches for the `busybox` binary across all known system paths including:
 * - Standard system directories (`/system/bin/`, `/system/xbin/`)
 * - Root-specific locations (`/su/bin/`, `/data/local/bin/`)
 * - All paths from the system PATH environment variable
 * - Custom installation directories used by rooting tools
 *
 * This detector provides reliable tamper detection as BusyBox is commonly installed
 * on rooted devices but absent from stock Android installations.
 *
 * Example usage:
 * ```kotlin
 * val detector = BusyBoxProgramFileDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(BusyBoxProgramFileDetector())
 *     }
 * }
 * ```
 *
 * @see FileDetector
 */
object BusyBoxProgramFileDetector : FileDetector() {
    /**
     * Provides the list of filenames to check for BusyBox detection.
     *
     * Returns a list containing only "busybox" as the target binary to search for
     * across all system paths.
     *
     * @return A list containing BUSYBOX_PROGRAM_FILE_DETECTOR_NAME as the filename to detect
     */
    override fun getFilenames(): List<String> = listOf(BUSYBOX_PROGRAM_FILE_DETECTOR_NAME)

    /**
     * Logger instance for logging detection events and information.
     *
     * Defaults to WARN level logging. Can be customized as needed.
     */
    override var logger: Logger = Logger.WARN

    /**
     * Detector name key used for identification and logging purposes.
     */
    internal const val BUSYBOX_PROGRAM_FILE_DETECTOR_NAME = "busybox"

    override suspend fun analyze(context: Context): Double {
        logger.i("Running BusyBoxProgramFileDetector")
        return super.analyze(context)
    }
}