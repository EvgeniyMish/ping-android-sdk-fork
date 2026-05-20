/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import com.pingidentity.pingidsdkv2.PingOneSDKError

/**
 * Exception thrown by suspend variants of the PingOne MFA APIs when an operation fails.
 *
 * The native [PingOneSDKError] type is intentionally not exposed, so hosting apps do not need
 * a direct dependency on the `pingidsdkv2` AAR. All available error information is surfaced
 * via the standard [message] property.
 *
 * ### Usage
 * ```kotlin
 * result.onFailure { e ->
 *     Log.e("MFA", e.message)
 * }
 * ```
 */
class PingOneMFAException(message: String?) : Exception(message) {

    // Internal factory constructors — accept native SDK type but keep them off the public API surface entirely.
    internal constructor(error: PingOneSDKError) : this(
        "Code=${error.code} \"${error.message}\" UserInfo=${error.userInfo}"
    )

    internal constructor(cause: Exception) : this(cause.message ?: "Unknown error")
}