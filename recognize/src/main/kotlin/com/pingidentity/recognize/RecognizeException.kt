/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import io.keyless.sdk.errorshandling.KeylessSdkError

/**
 * Stable public error type for all Recognize SDK failures.
 *
 * Wraps [KeylessSdkError] and re-exposes the fields callers need without
 * requiring a direct dependency on the Keyless SDK types.
 *
 * @property code Numeric error code from [KeylessSdkError.code].
 * @property debuggingInfo Diagnostic map from [KeylessSdkError.debuggingInfo].
 *   Keys include `FLOW_ID_KEY`, `SESSION_ID_KEY`, `STACK_TRACE_KEY`, and
 *   `UNDERLYING_ERROR_MESSAGE_KEY`.
 */
class RecognizeException(
    val code: Int,
    override val message: String,
    val debuggingInfo: Map<String, String>,
    cause: Throwable? = null,
) : Exception(message, cause) {

    companion object {
        /**
         * Converts a [KeylessSdkError] into a [RecognizeException].
         *
         * @param error The raw Keyless SDK error.
         * @return A [RecognizeException] populated from [error].
         */
        fun from(error: KeylessSdkError): RecognizeException = RecognizeException(
            code = error.code,
            message = error.message ?: "UNKNOWN_ERROR",
            debuggingInfo = error.debuggingInfo,
            cause = error,
        )
    }
}

/**
 * Returns this throwable as a [RecognizeException], casting if already one or wrapping otherwise.
 */
internal fun Throwable.asRecognizeException(): RecognizeException =
    this as? RecognizeException
        ?: RecognizeException(code = -1, message = message ?: "UNKNOWN_ERROR", debuggingInfo = emptyMap(), cause = this)
