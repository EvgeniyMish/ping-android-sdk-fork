/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import com.pingidentity.pingidsdkv2.PingOneGeo

/**
 * PingOne MFA service region used when configuring the native SDK.
 *
 * This enum is part of the pingonemfa public API. It intentionally mirrors the
 * native SDK regions without exposing the native PingOneGeo type.
 */
enum class Geo {
    NORTH_AMERICA,
    EUROPE,
    CANADA,
    AUSTRALIA,
    SINGAPORE;

    /**
     * Converts the public wrapper value to the native SDK value at the adapter boundary.
     *
     * Keep this mapping internal so apps can choose a PingOne region without taking a
     * compile-time dependency on native PingOne MFA SDK API types.
     */
    internal fun toPingOneGeo(): PingOneGeo =
        when (this) {
            NORTH_AMERICA -> PingOneGeo.NORTH_AMERICA
            EUROPE -> PingOneGeo.EUROPE
            CANADA -> PingOneGeo.CANADA
            AUSTRALIA -> PingOneGeo.AUSTRALIA
            SINGAPORE -> PingOneGeo.SINGAPORE
        }
}
