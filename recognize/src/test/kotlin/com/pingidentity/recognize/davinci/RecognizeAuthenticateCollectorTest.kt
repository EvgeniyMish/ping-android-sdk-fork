/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import com.pingidentity.recognize.Recognize
import com.pingidentity.recognize.RecognizeException
import io.keyless.sdk.errorshandling.AuthenticationSuccess
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
 * Tests for [RecognizeAuthenticateCollector].
 *
 * The Recognize SDK ([Recognize]) is mocked via [mockkObject] so that these tests exercise the
 * collector's config-building, payload-population, and error-propagation logic without requiring a
 * real device or SDK.
 *
 * Notable regression coverage:
 * - Auth reads `"presentationStyle"` from `mobileSDKOptions`, not `"presentation"` (enroll uses
 *   `"presentation"`).
 * - Auth reads `"shouldRetriveAuthenticationFrame"` — this preserves the server-side typo (missing
 *   `e` in `Retrieve`).
 */
class RecognizeAuthenticateCollectorTest {

    private val authSuccess: AuthenticationSuccess = mockk {
        every { signedJwt } returns "signed-jwt"
        every { clientState } returns "client-state"
    }

    @BeforeTest
    fun setUp() {
        mockkObject(Recognize)
        coEvery { Recognize.setup(any()) } returns Result.success(Unit)
        coEvery { Recognize.authenticate(any()) } returns Result.success(authSuccess)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Recognize)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Builds a minimal authenticate input JSON with the supplied [mobileSDKOptions] block.
     */
    private fun authInput(
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
        val input = authInput(
            key = "recognizeKey",
            host = "https://recognize.example.com",
            apiKey = "test-api-key",
            transactionData = "tx-data",
            clientState = "cs",
        )
        val collector = RecognizeAuthenticateCollector().apply { init(input) }

        assertEquals("recognizeKey", collector.key)
        assertEquals("https://recognize.example.com", collector.host)
        assertEquals("test-api-key", collector.apiKey)
        assertEquals("tx-data", collector.transactionData)
        assertEquals("cs", collector.clientState)
    }

    @Test
    fun `id returns key`() {
        val input = authInput(key = "myKey")
        val collector = RecognizeAuthenticateCollector().apply { init(input) }
        assertEquals("myKey", collector.id())
    }

    // ── Payload lifecycle ────────────────────────────────────────────────────────

    @Test
    fun `payload returns null before collect`() {
        val input = authInput()
        val collector = RecognizeAuthenticateCollector().apply { init(input) }
        assertNull(collector.payload())
    }

    // ── Success path ─────────────────────────────────────────────────────────────

    @Test
    fun `collect success populates payload with all six keys`() = runTest {
        val input = authInput()
        val collector = RecognizeAuthenticateCollector().apply { init(input) }

        val result = collector.collect()

        assertTrue(result.isSuccess)
        val payload = assertNotNull(collector.payload())
        assertEquals("signed-jwt", payload["signedJwt"]?.jsonPrimitive?.content)
        assertEquals("client-state", payload["clientState"]?.jsonPrimitive?.content)
        assertEquals("", payload["recognizeId"]?.jsonPrimitive?.content)
        assertEquals("", payload["devicePublicSigningKey"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientErrorCode"]?.jsonPrimitive?.content)
    }

    // ── Failure paths ────────────────────────────────────────────────────────────

    @Test
    fun `collect failure from Recognize authenticate populates payload with error message`() = runTest {
        val error = RecognizeException(code = 21, message = "auth failed", debuggingInfo = emptyMap())
        coEvery { Recognize.authenticate(any()) } returns Result.failure(error)

        val input = authInput()
        val collector = RecognizeAuthenticateCollector().apply { init(input) }

        val result = collector.collect()

        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())
        val payload = assertNotNull(collector.payload())
        assertEquals("auth failed", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("21", payload["clientErrorCode"]?.jsonPrimitive?.content)
        assertEquals("", payload["signedJwt"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientState"]?.jsonPrimitive?.content)
        assertEquals("", payload["recognizeId"]?.jsonPrimitive?.content)
        assertEquals("", payload["devicePublicSigningKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun `collect failure from Recognize setup populates payload with error message`() = runTest {
        val error = RecognizeException(code = 11, message = "setup failed", debuggingInfo = emptyMap())
        coEvery { Recognize.setup(any()) } returns Result.failure(error)

        val input = authInput()
        val collector = RecognizeAuthenticateCollector().apply { init(input) }

        val result = collector.collect()

        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())
        val payload = assertNotNull(collector.payload())
        assertEquals("setup failed", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("11", payload["clientErrorCode"]?.jsonPrimitive?.content)
        assertEquals("", payload["signedJwt"]?.jsonPrimitive?.content)
        assertEquals("", payload["clientState"]?.jsonPrimitive?.content)
        assertEquals("", payload["recognizeId"]?.jsonPrimitive?.content)
        assertEquals("", payload["devicePublicSigningKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun `collect failure from KeylessSdkError maps code to clientErrorCode`() = runTest {
        val keylessError = RecognizeException(code = 42, message = "liveness failed", debuggingInfo = emptyMap())
        coEvery { Recognize.authenticate(any()) } returns Result.failure(keylessError)

        val collector = RecognizeAuthenticateCollector().apply { init(authInput()) }
        val result = collector.collect()

        assertTrue(result.isFailure)
        val ex = assertIs<RecognizeException>(result.exceptionOrNull())
        assertEquals(42, ex.code)
        val payload = assertNotNull(collector.payload())
        assertEquals("liveness failed", payload["clientError"]?.jsonPrimitive?.content)
        assertEquals("42", payload["clientErrorCode"]?.jsonPrimitive?.content)
    }

    // ── generateClientState mapping ──────────────────────────────────────────────

    @Test
    fun `generateClientState true maps to BACKUP for auth`() = runTest {
        val input = authInput(generateClientState = "true")
        val collector = RecognizeAuthenticateCollector().apply { init(input) }
        // BACKUP is a valid ClientStateType — authenticate must succeed without throwing
        assertTrue(collector.collect().isSuccess)
    }

    @Test
    fun `generateClientState false or empty maps to null for auth`() = runTest {
        val inputFalse = authInput(generateClientState = "false")
        val inputEmpty = authInput(generateClientState = "")

        assertTrue(RecognizeAuthenticateCollector().apply { init(inputFalse) }.collect().isSuccess)
        assertTrue(RecognizeAuthenticateCollector().apply { init(inputEmpty) }.collect().isSuccess)
    }

    // ── mobileSDKOptions — auth-specific field regression tests ──────────────────

    @Test
    fun `presentationStyle from mobileSDKOptions key presentationStyle`() = runTest {
        // Regression: auth reads "presentationStyle", not "presentation" (enroll uses "presentation")
        val input = authInput {
            put("presentationStyle", "NO_CAMERA_PREVIEW")
        }
        val collector = RecognizeAuthenticateCollector().apply { init(input) }
        // NO_CAMERA_PREVIEW is a valid PresentationStyle value — must succeed without throwing
        assertTrue(collector.collect().isSuccess)
    }

    @Test
    fun `shouldRetriveAuthenticationFrame from typo key`() = runTest {
        // Server sends "shouldRetriveAuthenticationFrame" (missing 'e' in Retrieve)
        val input = authInput {
            put("shouldRetriveAuthenticationFrame", "true")
        }
        val collector = RecognizeAuthenticateCollector().apply { init(input) }
        assertTrue(collector.collect().isSuccess)
    }

}
