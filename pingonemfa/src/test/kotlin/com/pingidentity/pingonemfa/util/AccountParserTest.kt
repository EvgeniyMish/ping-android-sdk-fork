package com.pingidentity.pingonemfa.util

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountParserTest {

    private val parser = AccountParser()

    @Test
    fun `single region single user`() {
        val json = """
        {
           "NA": {
              "users": [
                {
                  "id": "u1",
                  "environment": { "id": "env1" },
                  "device": { "id": "d1" },
                    "username": "jdoe",
                  "name": { "given": "John", "family": "Doe" }
                }
              ]
           }
        }
        """

        val result = parser.parseAccounts(json)

        assertEquals(1, result.size)
        val account = result.first()

        assertEquals("NA", account.region)
        assertEquals("u1", account.id)
        assertEquals("env1", account.environment)
        assertEquals("d1", account.deviceId)
        assertEquals("John", account.name)
        assertEquals("Doe", account.family)
    }

    @Test
    fun `multiple regions multiple users`() {
        val json = """
    {
      "NA": {
        "users": [{ "id": "u1", "username": "jdoe" }]
      },
      "EU": {
        "users": [{ "id": "u2", "username": "asmith" }]
      }
    }
    """.trimIndent()

        val result = parser.parseAccounts(json)

        assertEquals(2, result.size)
        assertTrue(result.any { it.region == "NA" && it.id == "u1" })
        assertTrue(result.any { it.region == "EU" && it.id == "u2" })
    }

    @Test
    fun `missing users defaults to empty`() {
        val json = """
    {
      "NA": {}
    }
    """.trimIndent()

        val result = parser.parseAccounts(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `missing nested objects produce empty strings`() {
        val json = """
    {
      "NA": {
        "users": [{ "id": "u1", "username": "jdoe" }]
      }
    }
    """.trimIndent()

        val account = parser.parseAccounts(json).first()

        assertEquals("", account.environment)
        assertEquals("", account.deviceId)
        assertEquals("", account.name)
        assertEquals("", account.family)
    }

    @Test
    fun `partial name object`() {
        val json = """
    {
      "NA": {
        "users": [
          { "id": "u1", "username": "jdoe", "name": { "given": "Alice" } }
        ]
      }
    }
    """.trimIndent()

        val account = parser.parseAccounts(json).first()

        assertEquals("Alice", account.name)
        assertEquals("", account.family)
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """
    {
      "NA": {
        "users": [
          {
            "id": "u1",
            "unknown": "value",
            "username": "jdoe",  "environment": { "id": "env1", "extra": "x" }
          }
        ]
      }
    }
    """.trimIndent()

        val account = parser.parseAccounts(json).first()

        assertEquals("env1", account.environment)
    }

    @Test
    fun `large payload`() {
        val users = (1..100).joinToString(",") {
            """{ "id": "user-$it", "username": "user$it" }"""
        }

        val json = """
    {
      "NA": {
        "users": [$users]
      }
    }
    """.trimIndent()

        val result = parser.parseAccounts(json)

        assertEquals(100, result.size)
        assertEquals("user-1", result.first().id)
        assertEquals("user-100", result.last().id)
    }

    @Test
    fun `RegionDto is serializable`() {
        val json = Json { ignoreUnknownKeys = true }

        val original = mapOf(
            "NA" to RegionDto(
                users = listOf(
                    UserDto(
                        id = "u1",
                        environment = IdContainer("env"),
                        device = IdContainer("dev"),
                        username = "jdoe",
                        name = NameDto("John", "Doe")
                    )
                )
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Map<String, RegionDto>>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `throws on invalid json`() {
        val result = runCatching {
            parser.parseAccounts("""{ "NA": "invalid" }""")
        }
        assertTrue(result.isFailure)
    }
}