/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.otp

/**
 * The current one-time passcode returned by
 * [com.pingidentity.pingonemfa.commons.PingOneMFA.getOneTimePasscode].
 *
 * @property code The current TOTP passcode string (typically 6 digits).
 * @property secondsRemaining Seconds until the code expires, computed at call time.
 *   Clamped to `0` if the code is already past its validity window.
 *   Re-call [com.pingidentity.pingonemfa.commons.PingOneMFA.getOneTimePasscode] when this
 *   reaches zero to receive the next code.
 */
data class OtpCodeInfo(
    val code: String,
    val secondsRemaining: Int
)