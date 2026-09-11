/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.utils.PingDsl

/**
 * Configuration for [PingOneRecognizeEnrollCallback.enroll].
 *
 * Example:
 * ```kotlin
 * callback.enroll {
 *     retrieveSelfie = true
 * }
 * ```
 */
@PingDsl
class RecognizeEnrollConfig {
    /** When true, the enrollment frame (selfie) is captured and returned in [RecognizeSuccess.selfie]. */
    var retrieveSelfie: Boolean = false
}

/**
 * Configuration for [PingOneRecognizeAuthenticateCallback.authenticate].
 *
 * Example:
 * ```kotlin
 * callback.authenticate {
 *     retrieveSelfie = true
 * }
 * ```
 */
@PingDsl
class RecognizeAuthenticateConfig {
    /** When true, the authentication frame (selfie) is captured and returned in [RecognizeSuccess.selfie]. */
    var retrieveSelfie: Boolean = false
}
