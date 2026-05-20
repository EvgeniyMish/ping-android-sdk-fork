/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.util

import com.pingidentity.pingonemfa.commons.PingOneMfaAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class AccountParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    fun parseAccounts(rawJson: String): List<PingOneMfaAccount> {
        val decoded: Map<String, RegionDto> = json.decodeFromString(rawJson)

        return decoded.flatMap { (region, regionDto) ->
            regionDto.users.map {
                PingOneMfaAccount(
                    region = region,
                    id = it.id.orEmpty(),
                    environment = it.environment?.id.orEmpty(),
                    deviceId = it.device?.id.orEmpty(),
                    username = it.username,
                    name = it.name?.given.orEmpty(),
                    family = it.name?.family.orEmpty()
                )
            }
        }
    }
}

@Serializable
internal data class RegionDto(
    val users: List<UserDto> = emptyList()
)

@Serializable
internal data class UserDto(
    val id: String? = null,
    val environment: IdContainer? = null,
    val device: IdContainer? = null,
    val username: String,
    val name: NameDto? = null
)

@Serializable
internal data class IdContainer(
    val id: String? = null
)

@Serializable
internal data class NameDto(
    val given: String? = null,
    val family: String? = null
)