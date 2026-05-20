/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

/**
 * Metadata for a paired PingOne MFA account returned by
 * [com.pingidentity.pingonemfa.commons.PingOneMFA.getDeviceInfo].
 *
 * @property region Region key from the PingOne response (e.g. `"NA"`, `"EU"`).
 * @property id PingOne user ID.
 * @property deviceId Device ID associated with this pairing within PingOne.
 * @property environment PingOne environment ID.
 * @property username The account's login username as returned by the PingOne server.
 * @property name User's given (first) name, or null if not provided by the server.
 * @property family User's family (last) name, or null if not provided by the server.
 */
data class PingOneMfaAccount(
    val region: String,
    val id: String,
    val deviceId: String,
    val environment: String,
    val username: String,
    val name: String?,
    val family: String?
)