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
 * Native JNI-based detector for identifying device tampering through file system checks.
 *
 * This detector extends [FileDetector] and uses native C/C++ code through JNI (Java Native Interface)
 * to perform more sophisticated and harder-to-bypass file existence checks. The native implementation
 * can potentially detect files even when Java-level file system access is restricted or monitored.
 *
 * The detector specifically looks for the "su" (superuser) binary, which is the primary indicator
 * of root access on Android devices. By using native code, it can:
 * - Bypass Java-level security restrictions
 * - Perform low-level file system operations
 * - Be more difficult for anti-detection tools to hook or bypass
 *
 * The native library "tool-file" must be available and properly linked for this detector to function.
 * If the library fails to load, the detector gracefully degrades and returns 0.0 for all checks.
 *
 * The scoring system returns a [Double] value:
 * - `1.0` indicates at least one suspicious file was found (high confidence of tampering)
 * - `0.0` indicates no suspicious files were found or native library unavailable
 *
 * Example usage:
 * ```kotlin
 * val detector = NativeDetector()
 * val isTampered = detector.isTampered(context)
 * ```
 *
 * @see FileDetector
 */
class NativeDetector(override var logger: Logger = Logger.WARN) : FileDetector() {
    /**
     * Provides the list of filenames to check for using native detection.
     *
     * Currently configured to detect the "su" binary, which is the most common
     * indicator of root access on Android devices.
     *
     * @return A list containing "su" as the primary root detection target
     */
    override fun getFilenames(): List<String> = listOf("su")

    /**
     * Native JNI method that checks for file existence using C/C++ code.
     *
     * This method is implemented in the native "tool-file" library and performs
     * low-level file system checks that may be harder to detect or bypass than
     * Java-based file operations.
     *
     * @param pathArray Array of file paths to check for existence
     * @return Number of files found, or 0 if none exist
     * @throws UnsatisfiedLinkError if the native library is not properly linked
     */
    external fun exists(pathArray: Array<Any>): Int

    /**
     * Determines if the device has been tampered with using native file detection.
     *
     * This method creates a comprehensive list of file paths by combining all system paths
     * with all target filenames, then uses the native [exists] method to check for their
     * presence. If the native library is not loaded, the method returns 0.0.
     *
     * The detection process:
     * 1. Checks if the native library is loaded
     * 2. Generates all possible file paths to check
     * 3. Calls the native exists() method
     * 4. Returns confidence score based on findings
     *
     * @param context The Android context (inherited from interface but not used)
     * @return A confidence score where:
     *         - `1.0` indicates tampering detected via native checks
     *         - `0.0` indicates no tampering detected or native library unavailable
     */
    override suspend fun analyze(context: Context): Double {
        logger.i("Running NativeDetector")
        if (!libraryLoaded) return 0.0
        logger.d("Checking for native library file paths")
        val pathList = PATHS.flatMap { path ->
            getFilenames().map { filename ->
                path + filename
            }
        }.toTypedArray()

        val pathsAsAny = Array<Any>(pathList.size) { i -> pathList[i] }

        return runCatching {
            if (exists(pathsAsAny) > 0) {
                logger.w("Native library path found.")
                1.0
            } else {
                logger.d("Native library path not found.")
                0.0
            }
        }.getOrElse { exception ->
            if (exception is UnsatisfiedLinkError) {
                logger.e("Native library not linked, disabling NativeDetector", exception)
            }
            0.0
        }
    }

    companion object {
        /**
         * Flag indicating whether the native library was successfully loaded.
         *
         * This flag is set during class initialization and determines whether
         * native detection methods can be used. If false, all detection calls
         * will return 0.0 to avoid crashes.
         */
        private var libraryLoaded = false

        /**
         * Static initializer that attempts to load the native "tool-file" library.
         *
         * This block runs when the class is first loaded and tries to link the
         * native library required for JNI operations. If loading fails, the
         * [libraryLoaded] flag remains false and the detector will be disabled.
         *
         * The library loading is wrapped in a try-catch block to handle cases
         * where the native library is not available or compatible with the device.
         */
        init {
            val logger: Logger = Logger.WARN

            try {
                System.loadLibrary("tool-file")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                logger.e("Unable to link to tool-file library", e)
                libraryLoaded = false
            }
        }
    }
}