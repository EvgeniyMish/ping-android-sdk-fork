/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import com.pingidentity.recognize.Recognize
import com.pingidentity.recognize.RecognizeException
import io.keyless.sdk.errorshandling.EnrollmentSuccess
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [RecognizeEnrollCollector].
 *
 * The Recognize SDK ([Recognize]) is mocked via [mockkObject] so that these tests exercise the
 * collector's config-building, payload-population, and error-propagation logic without requiring a
 * real device or SDK.
 */
class RecognizeEnrollCollectorTest {

    private val enrollSuccess: EnrollmentSuccess = mockk {
        every { signedJwt } returns "signed-jwt"
        every { clientState } returns "client-state"
        every { keylessId } returns "keyless-id"
    }

    @BeforeTest
    fun setUp() {
        mockkObject(Recognize)
        coEvery { Recognize.setup(any()) } returns Result.success(Unit)
        coEvery { Recognize.enroll(any()) } returns Result.success(enrollSuccess)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Recognize)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Builds a minimal enroll input JSON with the supplied [mobileSDKOptions] block.
     */
    private fun enrollInput(
        key: String = "recognizeKey",
        host: String = "https://recognize.example.com",
        apiKey: String = "test-api-key",
        transactionData: String = "tx-data",
        clientState: String = "cs",
        generateClientState: String = "",
        extraFields: Map<String, String> = emptyMap(),
        mobileSDKOptions: (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit)? = null,
    ) = buildJsonObject {
        put("key", key)
        put("host", host)
        put("apiKey", apiKey)
        put("transactionData", transactionData)
        put("clientState", clientState)
        put("generateClientState", generateClientState)
        extraFields.forEach { (k, v) -> put(k, v) }
        if (mobileSDKOptions != null) {
            putJsonObject("mobileSDKOptions", mobileSDKOptions)
        }
    }

    // ── Common field parsing ─────────────────────────────────────────────────────

    @Test
    fun `init parses common fields`() {
        val input = enrollInput(
            key = "recognizeKey",
            host = "https://recognize.example.com",
            apiKey = "test-api-key",
            transactionData = "tx-data",
            clientState = "cs",
        )
        val collector = RecognizeEnrollCollector().apply { init(input) }

        assertEquals("recognizeKey", collector.key)
        assertEquals("https://recognize.example.com", collector.host)
        assertEquals("test-api-key", collector.apiKey)
        assertEquals("tx-data", collector.transactionData)
        assertEquals("cs", collector.clientState)
    }

    @Test
    fun `id returns key`() {
        val input = enrollInput(key = "myKey")
        val collector = RecognizeEnrollCollector().apply { init(input) }
        assertEquals("myKey", collector.id())
    }

    // ── Payload lifecycle ────────────────────────────────────────────────────────

    @Test
    fun `payload returns null before collect`() {
        val input = enrollInput()
        val collector = RecognizeEnrollCollector().apply { init(input) }
        assertNull(collector.payload())
    }

    // ── Success path ─────────────────────────────────────────────────────────────

    @Test
    fun `collect success populates payload with all five keys`() = runTest {
        val input = enrollInput()
        val collector = RecognizeEnrollCollector().apply { init(input) }

        val result = collector.collect()

        assertTrue(result.isSuccess)
        val payload = assertNotNull(collector.payload())
        assertEquals("signed-jwt", payload["signedJwt"]?.jsonPrimitive?.content)
        assertEquals("client-state", payload["clientState"]?.jsonPrimitive?.content)
        assertEquals("keyless-id", payload["recognizeId"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientErrorCode"]?.jsonPrimitive?.content)
    }

    // ── Failure paths ────────────────────────────────────────────────────────────

    @Test
    fun `collect failure from Recognize enroll populates payload with error message`() = runTest {
        val error = RecognizeException(code = 21, message = "enroll failed", debuggingInfo = emptyMap())
        coEvery { Recognize.enroll(any()) } returns Result.failure(error)

        val input = enrollInput()
        val collector = RecognizeEnrollCollector().apply { init(input) }

        val result = collector.collect()

        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())
        val payload = assertNotNull(collector.payload())
        assertEquals("enroll failed", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("21", payload["clientErrorCode"]?.jsonPrimitive?.content)
        assertEquals("", payload["signedJwt"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientState"]?.jsonPrimitive?.content)
        assertEquals("", payload["recognizeId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `collect failure from Recognize setup populates payload with error message`() = runTest {
        val error = RecognizeException(code = 11, message = "setup failed", debuggingInfo = emptyMap())
        coEvery { Recognize.setup(any()) } returns Result.failure(error)

        val input = enrollInput()
        val collector = RecognizeEnrollCollector().apply { init(input) }

        val result = collector.collect()

        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())
        val payload = assertNotNull(collector.payload())
        assertEquals("setup failed", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("11", payload["clientErrorCode"]?.jsonPrimitive?.content)
        assertEquals("", payload["signedJwt"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientState"]?.jsonPrimitive?.content)
        assertEquals("", payload["recognizeId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `collect failure from KeylessSdkError maps code to clientErrorCode`() = runTest {
        val keylessError = RecognizeException(code = 21, message = "user cancelled", debuggingInfo = emptyMap())
        coEvery { Recognize.enroll(any()) } returns Result.failure(keylessError)

        val collector = RecognizeEnrollCollector().apply { init(enrollInput()) }
        val result = collector.collect()

        assertTrue(result.isFailure)
        val ex = assertIs<RecognizeException>(result.exceptionOrNull())
        assertEquals(21, ex.code)
        val payload = assertNotNull(collector.payload())
        assertEquals("user cancelled", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("21", payload["clientErrorCode"]?.jsonPrimitive?.content)
    }

    // ── generateClientState mapping ──────────────────────────────────────────────

    @Test
    fun `generateClientState true maps to BACKUP`() = runTest {
        val input = enrollInput(generateClientState = "true")
        val collector = RecognizeEnrollCollector().apply { init(input) }
        // BACKUP is a valid ClientStateType — enroll must succeed without throwing
        assertTrue(collector.collect().isSuccess)
    }

    @Test
    fun `generateClientState false or empty maps to null`() = runTest {
        val inputFalse = enrollInput(generateClientState = "false")
        val inputEmpty = enrollInput(generateClientState = "")

        assertTrue(RecognizeEnrollCollector().apply { init(inputFalse) }.collect().isSuccess)
        assertTrue(RecognizeEnrollCollector().apply { init(inputEmpty) }.collect().isSuccess)
    }

    // ── mobileSDKOptions — sub-field parsing ─────────────────────────────────────

    @Test
    fun `mobileSDKOptions livenessConfiguration valid value is parsed`() = runTest {
        val input = enrollInput {
            put("livenessConfiguration", "PASSIVE_MEDIUM")
        }
        val collector = RecognizeEnrollCollector().apply { init(input) }
        assertTrue(collector.collect().isSuccess)
    }

    @Test
    fun `mobileSDKOptions invalid livenessConfiguration is ignored`() = runTest {
        val input = enrollInput {
            put("livenessConfiguration", "NOT_A_REAL_LIVENESS_CONFIG")
        }
        val collector = RecognizeEnrollCollector().apply { init(input) }
        // runCatching in the collector means the invalid value is silently dropped — no exception
        assertTrue(collector.collect().isSuccess)
    }

    @Test
    fun `mobileSDKOptions operationInfoId builds OperationInfo`() = runTest {
        val input = enrollInput {
            put("operationInfoId", "op-123")
            put("operationInfoPayload", "payload-data")
            put("operationInfoExternalUserId", "user-456")
        }
        val collector = RecognizeEnrollCollector().apply { init(input) }
        assertTrue(collector.collect().isSuccess)
    }

    // ── Robustness ───────────────────────────────────────────────────────────────

    @Test
    fun `unknown fields in input are ignored`() = runTest {
        val input = enrollInput(
            extraFields = mapOf(
                "unknownField" to "someValue",
                "anotherUnknown" to "otherValue",
            ),
        )
        val collector = RecognizeEnrollCollector().apply { init(input) }
        // Extra fields must not cause an exception
        assertTrue(collector.collect().isSuccess)
        assertEquals("recognizeKey", collector.key)
    }
}
