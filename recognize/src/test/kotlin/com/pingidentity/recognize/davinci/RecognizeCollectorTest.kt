/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import com.pingidentity.recognize.Recognize
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

/**
 * Tests for the [RecognizeCollector] factory.
 *
 * [RecognizeCollector.init] reads `operationType` from the server JSON and returns a fully
 * initialised [RecognizeEnrollCollector] or [RecognizeAuthenticateCollector].
 */
class RecognizeCollectorTest {

    @BeforeTest
    fun setUp() {
        mockkObject(Recognize)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Recognize)
    }

    @Test
    fun `ENROLL operationType returns RecognizeEnrollCollector`() {
        val input = buildJsonObject {
            put("operationType", "ENROLL")
            put("key", "recognizeKey")
            put("host", "https://recognize.example.com")
            put("apiKey", "api-key-123")
        }
        val result = RecognizeCollector().init(input)

        assertIs<RecognizeEnrollCollector>(result)
        assertEquals("recognizeKey", (result as RecognizeEnrollCollector).key)
        assertEquals("https://recognize.example.com", result.host)
        assertEquals("api-key-123", result.apiKey)
    }

    @Test
    fun `AUTHENTICATE operationType returns RecognizeAuthenticateCollector`() {
        val input = buildJsonObject {
            put("operationType", "AUTHENTICATE")
            put("key", "recognizeKey")
            put("host", "https://recognize.example.com")
            put("apiKey", "api-key-456")
        }
        val result = RecognizeCollector().init(input)

        assertIs<RecognizeAuthenticateCollector>(result)
        assertEquals("recognizeKey", (result as RecognizeAuthenticateCollector).key)
        assertEquals("https://recognize.example.com", result.host)
        assertEquals("api-key-456", result.apiKey)
    }

    @Test
    fun `unknown operationType falls back to RecognizeEnrollCollector`() {
        val input = buildJsonObject {
            put("operationType", "UNKNOWN_OP")
        }

        assertIs<RecognizeEnrollCollector>(RecognizeCollector().init(input))
    }

    @Test
    fun `missing operationType falls back to RecognizeEnrollCollector`() {
        val input = buildJsonObject {
            put("key", "recognizeKey")
        }

        assertIs<RecognizeEnrollCollector>(RecognizeCollector().init(input))
    }

    @Test
    fun `blank operationType falls back to RecognizeEnrollCollector`() {
        val input = buildJsonObject {
            put("operationType", " ")
        }

        assertIs<RecognizeEnrollCollector>(RecognizeCollector().init(input))
    }
}
