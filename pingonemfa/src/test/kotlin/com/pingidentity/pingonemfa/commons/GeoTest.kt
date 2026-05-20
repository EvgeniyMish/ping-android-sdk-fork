/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import com.pingidentity.pingidsdkv2.PingOneGeo
import org.junit.Test
import kotlin.test.assertEquals

class GeoTest {

    @Test
    fun `toPingOneGeo maps all public geos to native PingOneGeo values`() {
        val mappings = mapOf(
            Geo.NORTH_AMERICA to PingOneGeo.NORTH_AMERICA,
            Geo.EUROPE to PingOneGeo.EUROPE,
            Geo.CANADA to PingOneGeo.CANADA,
            Geo.AUSTRALIA to PingOneGeo.AUSTRALIA,
            Geo.SINGAPORE to PingOneGeo.SINGAPORE
        )

        mappings.forEach { (geo, expectedPingOneGeo) ->
            assertEquals(expectedPingOneGeo, geo.toPingOneGeo())
        }
    }
}
