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
 * Pre-configured tamper detector that identifies device rooting by checking for root program files.
 *
 * This detector extends [FileDetector] and specifically looks for the presence of core root
 * management program files in the device filesystem. These files are essential components
 * of rooted Android systems and their presence is a strong indicator of device compromise.
 *
 * The detector searches for the following critical root program files:
 *
 * **su (Superuser Binary):**
 * - The fundamental root access binary that grants elevated privileges
 * - Present on virtually all rooted Android devices
 * - Allows applications and users to execute commands with root permissions
 * - Typically installed in system directories like `/system/bin/`, `/system/xbin/`, `/su/bin/`
 *
 * **magisk:**
 * - Binary component of the popular Magisk systemless root solution
 * - Provides advanced root management with hiding capabilities
 * - Offers systemless modifications that don't alter system partition
 * - Includes MagiskHide functionality to conceal root from other applications
 *
 * These files are searched across all known system paths including standard Android directories,
 * custom root installation locations, and paths from the system PATH environment variable.
 * The detection is performed at the filesystem level, making it difficult to bypass without
 * actually removing or hiding the files.
 *
 * This detector provides reliable root detection as these program files are essential for
 * root functionality and cannot be easily hidden without breaking root access entirely.
 *
 * Example usage:
 * ```kotlin
 * val detector = RootProgramFileDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(RootProgramFileDetector())
 *     }
 * }
 * ```
 *
 * @see FileDetector
 */
object RootProgramFileDetector : FileDetector() {

    override var logger: Logger = Logger.WARN
    /**
     * Provides the list of root program filenames to check for tampering detection.
     *
     * Returns a list of essential root program files that are commonly found on
     * rooted Android devices:
     *
     * - `su` - The superuser binary that provides root access privileges
     * - `magisk` - The Magisk systemless root management binary
     *
     * @return A list of critical root program filenames to search for
     */
    override fun getFilenames(): List<String> = CURRENT_KNOWN_ROOT_PROGRAM_FILES

    internal val CURRENT_KNOWN_ROOT_PROGRAM_FILES = listOf(
        "su",
        "magisk",
    )

    override suspend fun analyze(context: Context): Double {
        logger.i("Running RootProgramFileDetector")
        return super.analyze(context)
    }
}