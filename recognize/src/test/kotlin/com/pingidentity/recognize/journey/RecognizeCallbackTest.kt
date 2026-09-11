/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import android.graphics.Bitmap
import com.pingidentity.recognize.Recognize
import com.pingidentity.recognize.RecognizeException
import io.keyless.sdk.errorshandling.AuthenticationSuccess
import io.keyless.sdk.errorshandling.EnrollmentSuccess
import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.enroll.BiomEnrollConfig
import io.keyless.sdk.configurations.auth.BiomAuthConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [RecognizeCallback] and its concrete delegates.
 *
 * [RecognizeCallback.init] is a factory: it reads `operationType` from the server JSON and
 * returns either a [PingOneRecognizeEnrollCallback] or a [PingOneRecognizeAuthenticateCallback],
 * both fully initialised with the same [JsonObject].
 */
class RecognizeCallbackTest {

    private val enrollSuccess: EnrollmentSuccess = mockk {
        every { signedJwt } returns "signed-jwt"
        every { clientState } returns "client-state"
        every { keylessId } returns "keyless-id"
        every { enrollmentFrame } returns null
    }

    private val authSuccess: AuthenticationSuccess = mockk {
        every { signedJwt } returns "signed-jwt"
        every { clientState } returns "client-state"
        every { authenticationFrame } returns null
    }

    @BeforeTest
    fun setUp() {
        mockkObject(Recognize)
        coEvery { Recognize.setup(any()) } returns Result.success(Unit)
        coEvery { Recognize.enroll(any()) } returns Result.success(enrollSuccess)
        coEvery { Recognize.authenticate(any()) } returns Result.success(authSuccess)
        coEvery { Recognize.validateUserAndDeviceActive() } returns Result.success(Unit)
        every { Recognize.getDevicePublicSigningKey() } returns Result.success("device-public-key")
        every { Recognize.getUserId() } returns Result.success("user-id")
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Recognize)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Enroll input slots: signedJwt, clientState, recognizeId, clientError, clientErrorCode */
    private fun enrollCallbackJson(): JsonObject = Json.parseToJsonElement(
        """
        {
          "type": "PingOneRecognizeCallback",
          "output": [
            { "name": "operationType",       "value": "ENROLL" },
            { "name": "host",                "value": "https://recognize.example.com" },
            { "name": "apiKey",              "value": "test-api-key" },
            { "name": "transactionData",     "value": "tx-data" },
            { "name": "clientState",         "value": "cs" },
            { "name": "generateClientState", "value": "" }
          ],
          "input": [
            { "name": "IDToken1signedJwt",       "value": "" },
            { "name": "IDToken1clientState",     "value": "" },
            { "name": "IDToken1recognizeId",     "value": "" },
            { "name": "IDToken1clientError",     "value": "" },
            { "name": "IDToken1clientErrorCode", "value": "" }
          ]
        }
        """
    ) as JsonObject

    /** Auth input slots: signedJwt, clientState, recognizeId, devicePublicSigningKey, clientError, clientErrorCode */
    private fun authCallbackJson(): JsonObject = Json.parseToJsonElement(
        """
        {
          "type": "PingOneRecognizeCallback",
          "output": [
            { "name": "operationType",       "value": "AUTHENTICATE" },
            { "name": "host",                "value": "https://recognize.example.com" },
            { "name": "apiKey",              "value": "test-api-key" },
            { "name": "transactionData",     "value": "tx-data" },
            { "name": "clientState",         "value": "" },
            { "name": "generateClientState", "value": "" }
          ],
          "input": [
            { "name": "IDToken1signedJwt",              "value": "" },
            { "name": "IDToken1clientState",            "value": "" },
            { "name": "IDToken1recognizeId",            "value": "" },
            { "name": "IDToken1devicePublicSigningKey", "value": "" },
            { "name": "IDToken1clientError",            "value": "" },
            { "name": "IDToken1clientErrorCode",        "value": "" }
          ]
        }
        """
    ) as JsonObject

    // ── Factory dispatch ─────────────────────────────────────────────────────────

    @Test
    fun `ENROLL operationType returns PingOneRecognizeEnrollCallback`() {
        val result = RecognizeCallback().init(enrollCallbackJson())
        assertIs<PingOneRecognizeEnrollCallback>(result)
    }

    @Test
    fun `AUTHENTICATE operationType returns PingOneRecognizeAuthenticateCallback`() {
        val result = RecognizeCallback().init(authCallbackJson())
        assertIs<PingOneRecognizeAuthenticateCallback>(result)
    }

    @Test
    fun `unknown operationType throws IllegalArgumentException`() {
        val json = Json.parseToJsonElement(
            """{ "type": "PingOneRecognizeCallback", "output": [{ "name": "operationType", "value": "UNKNOWN" }], "input": [] }"""
        ) as JsonObject
        val ex = runCatching { RecognizeCallback().init(json) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    @Test
    fun `missing operationType throws IllegalArgumentException`() {
        val json = Json.parseToJsonElement(
            """{ "type": "PingOneRecognizeCallback", "output": [], "input": [] }"""
        ) as JsonObject
        val ex = runCatching { RecognizeCallback().init(json) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    // ── generateClientState mapping ──────────────────────────────────────────────

    @Test
    fun `generateClientState true maps to ClientStateType BACKUP for enroll`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",       "value": "ENROLL" },
                { "name": "host",                "value": "h" },
                { "name": "apiKey",              "value": "k" },
                { "name": "generateClientState", "value": "true" }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        // enroll() should succeed without throwing — BACKUP is a valid ClientStateType
        assertTrue(callback.enroll().isSuccess)
    }

    @Test
    fun `generateClientState true maps to ClientStateType BACKUP for auth`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",       "value": "AUTHENTICATE" },
                { "name": "host",                "value": "h" },
                { "name": "apiKey",              "value": "k" },
                { "name": "generateClientState", "value": "true" }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject
        val callback = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
    }

    // ── Enroll — success path ────────────────────────────────────────────────────

    @Test
    fun `enroll success writes signedJwt and recognizeId to input`() = runTest {
        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        val result = callback.enroll()
        assertTrue(result.isSuccess)
        assertEquals("device-public-key", result.getOrThrow().devicePublicSigningKey)

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("signed-jwt",  inputs[0].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("client-state",inputs[1].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("keyless-id",  inputs[2].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("",            inputs[3].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("",            inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
    }

    // ── Enroll — failure path ────────────────────────────────────────────────────

    @Test
    fun `enroll failure from Recognize_enroll writes error to input`() = runTest {
        val error = RecognizeException(code = 21, message = "enroll failed", debuggingInfo = emptyMap())
        coEvery { Recognize.enroll(any()) } returns Result.failure(error)

        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        val result = callback.enroll()
        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("", inputs[0].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("", inputs[1].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("", inputs[2].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("enroll failed", inputs[3].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("21", inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `enroll failure from Recognize_setup writes error to input`() = runTest {
        val error = RecognizeException(code = 11, message = "setup failed", debuggingInfo = emptyMap())
        coEvery { Recognize.setup(any()) } returns Result.failure(error)

        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        val result = callback.enroll()
        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("setup failed", inputs[3].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("11", inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `enroll failure from KeylessSdkError writes code to clientErrorCode input`() = runTest {
        val keylessError = RecognizeException(code = 21, message = "user cancelled", debuggingInfo = emptyMap())
        coEvery { Recognize.enroll(any()) } returns Result.failure(keylessError)

        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        val result = callback.enroll()
        assertTrue(result.isFailure)
        val ex = assertIs<RecognizeException>(result.exceptionOrNull())
        assertEquals(21, ex.code)

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("user cancelled", inputs[3].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("21", inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
    }

    // ── Authenticate — success path ──────────────────────────────────────────────

    @Test
    fun `authenticate success writes signedJwt and clientState to input`() = runTest {
        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate()
        assertTrue(result.isSuccess)
        assertEquals("user-id", result.getOrThrow().recognizeId)

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("signed-jwt",   inputs[0].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("client-state", inputs[1].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("user-id",       inputs[2].jsonObject["value"]!!.jsonPrimitive.content) // recognizeId
        assertEquals("device-public-key", inputs[3].jsonObject["value"]!!.jsonPrimitive.content) // devicePublicSigningKey
        assertEquals("",             inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("",             inputs[5].jsonObject["value"]!!.jsonPrimitive.content)
    }

    // ── Authenticate — failure path ──────────────────────────────────────────────

    @Test
    fun `authenticate failure from Recognize_authenticate writes error to input`() = runTest {
        val error = RecognizeException(code = 21, message = "auth failed", debuggingInfo = emptyMap())
        coEvery { Recognize.authenticate(any()) } returns Result.failure(error)

        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate()
        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("", inputs[0].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("", inputs[1].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("", inputs[2].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("", inputs[3].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("auth failed", inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("21", inputs[5].jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `authenticate failure from Recognize_setup writes error to input`() = runTest {
        val error = RecognizeException(code = 11, message = "setup failed", debuggingInfo = emptyMap())
        coEvery { Recognize.setup(any()) } returns Result.failure(error)

        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate()
        assertTrue(result.isFailure)
        assertIs<RecognizeException>(result.exceptionOrNull())

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("setup failed", inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("11", inputs[5].jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `authenticate failure from KeylessSdkError writes code to clientErrorCode input`() = runTest {
        val keylessError = RecognizeException(code = 42, message = "liveness failed", debuggingInfo = emptyMap())
        coEvery { Recognize.authenticate(any()) } returns Result.failure(keylessError)

        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate()
        assertTrue(result.isFailure)
        val ex = assertIs<RecognizeException>(result.exceptionOrNull())
        assertEquals(42, ex.code)

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("liveness failed", inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("42", inputs[5].jsonObject["value"]!!.jsonPrimitive.content)
    }

    // ── clientState enrollment-check branch ─────────────────────────────────

    private fun authWithClientStateJson(clientStateValue: String = "stored-cs"): JsonObject =
        Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",       "value": "AUTHENTICATE" },
                { "name": "host",                "value": "https://recognize.example.com" },
                { "name": "apiKey",              "value": "test-api-key" },
                { "name": "transactionData",     "value": "tx-data" },
                { "name": "clientState",         "value": "$clientStateValue" },
                { "name": "generateClientState", "value": "" }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject

    @Test
    fun `clientState present and user enrolled calls authenticate not enroll`() = runTest {
        coEvery { Recognize.validateUserAndDeviceActive() } returns Result.success(Unit)

        val callback = RecognizeCallback().init(authWithClientStateJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate()
        assertTrue(result.isSuccess)
        assertEquals("user-id", result.getOrThrow().recognizeId)
        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("user-id", inputs[2].jsonObject["value"]!!.jsonPrimitive.content)

        coVerify(exactly = 1) { Recognize.authenticate(any()) }
        coVerify(exactly = 0) { Recognize.enroll(any()) }
    }

    @Test
    fun `clientState present and user not enrolled calls enroll with clientState`() = runTest {
        coEvery { Recognize.validateUserAndDeviceActive() } returns Result.failure(IOException("not enrolled"))
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)

        val callback = RecognizeCallback().init(authWithClientStateJson("my-client-state")) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate()
        assertTrue(result.isSuccess)
        assertEquals("keyless-id", result.getOrThrow().recognizeId)
        assertEquals("device-public-key", result.getOrThrow().devicePublicSigningKey)

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("keyless-id", inputs[2].jsonObject["value"]!!.jsonPrimitive.content)

        coVerify(exactly = 0) { Recognize.authenticate(any()) }
        coVerify(exactly = 1) { Recognize.enroll(any()) }
        assertEquals("my-client-state", enrollSlot.captured.clientState)
    }

    @Test
    fun `clientState absent skips validateUserAndDeviceActive and calls authenticate`() = runTest {
        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)

        coVerify(exactly = 0) { Recognize.validateUserAndDeviceActive() }
        coVerify(exactly = 1) { Recognize.authenticate(any()) }
    }

    @Test
    fun `clientState present and enroll fails writes error to input`() = runTest {
        val error = RecognizeException(code = 21, message = "enroll from client state failed", debuggingInfo = emptyMap())
        coEvery { Recognize.validateUserAndDeviceActive() } returns Result.failure(IOException("not enrolled"))
        coEvery { Recognize.enroll(any()) } returns Result.failure(error)

        val callback = RecognizeCallback().init(authWithClientStateJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate()
        assertTrue(result.isFailure)

        val inputs = callback.payload()["input"]!!.jsonArray
        assertEquals("enroll from client state failed", inputs[4].jsonObject["value"]!!.jsonPrimitive.content)
    }

    // ── Common fields parsed by AbstractRecognizeCallback ───────────────────────

    @Test
    fun `common output fields are parsed into callback properties`() {
        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        assertEquals("https://recognize.example.com", callback.host)
        assertEquals("test-api-key", callback.apiKey)
        assertEquals("tx-data", callback.transactionData)
        assertEquals("cs", callback.clientState)
    }

    // ── mobileSDKOptions — auth-specific fields ──────────────────────────────────

    @Test
    fun `auth reads presentationStyle from mobileSDKOptions key presentationStyle`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType", "value": "AUTHENTICATE" },
                { "name": "host",          "value": "h" },
                { "name": "apiKey",        "value": "k" },
                { "name": "mobileSDKOptions", "value": { "presentationStyle": "NO_CAMERA_PREVIEW" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject
        // Should succeed — NO_CAMERA_PREVIEW is valid; the old bug would silently ignore it
        // (it was reading key "presentation" instead of "presentationStyle")
        val callback = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
    }

    // ── mobileSDKOptions — enroll-specific fields ────────────────────────────────

    @Test
    fun `enroll reads numberOfEnrollmentCircuits from mobileSDKOptions`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType", "value": "ENROLL" },
                { "name": "host",          "value": "h" },
                { "name": "apiKey",        "value": "k" },
                { "name": "mobileSDKOptions", "value": { "numberOfEnrollmentCircuits": "3" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        coVerify { Recognize.setup(match<SetupConfig> { it.numberOfEnrollmentCircuits == 3 }) }
    }

    // ── audience wiring ──────────────────────────────────────────────────────────

    @Test
    fun `enroll audience is forwarded to JwtSigningInfo`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType", "value": "ENROLL" },
                { "name": "host",          "value": "h" },
                { "name": "apiKey",        "value": "k" },
                { "name": "audience",      "value": "my-audience" }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals("my-audience", enrollSlot.captured.jwtSigningInfo?.audience)
    }

    @Test
    fun `auth audience is forwarded to JwtSigningInfo`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType", "value": "AUTHENTICATE" },
                { "name": "host",          "value": "h" },
                { "name": "apiKey",        "value": "k" },
                { "name": "audience",      "value": "my-audience" }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject
        val authSlot = slot<BiomAuthConfig>()
        coEvery { Recognize.authenticate(capture(authSlot)) } returns Result.success(authSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertEquals("my-audience", authSlot.captured.jwtSigningInfo?.audience)
    }

    // ── enroll mobileSDKOptions — remaining fields ───────────────────────────────

    @Test
    fun `enroll clientState empty string is not forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType", "value": "ENROLL" },
                { "name": "host",          "value": "h" },
                { "name": "apiKey",        "value": "k" },
                { "name": "clientState",   "value": "" }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(null, enrollSlot.captured.clientState)
    }

    @Test
    fun `enroll operationInfo is null when no operationInfo keys present`() = runTest {
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(null, enrollSlot.captured.operationInfo)
    }

    @Test
    fun `enroll mobileSDKOptions livenessEnvironmentAware is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "ENROLL" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "livenessEnvironmentAware": "true" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertTrue(enrollSlot.captured.livenessEnvironmentAware)
    }

    @Test
    fun `enroll mobileSDKOptions cameraDelaySeconds is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "ENROLL" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "cameraDelaySeconds": "5" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(5, enrollSlot.captured.cameraDelaySeconds)
    }

    @Test
    fun `enroll mobileSDKOptions showSuccessFeedback is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "ENROLL" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "showSuccessFeedback": "false" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(false, enrollSlot.captured.showSuccessFeedback)
    }

    @Test
    fun `enroll mobileSDKOptions showFailureFeedback is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "ENROLL" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "showFailureFeedback": "false" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(false, enrollSlot.captured.showFailureFeedback)
    }

    @Test
    fun `enroll mobileSDKOptions showInstructionsScreen is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "ENROLL" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "showInstructionsScreen": "false" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(false, enrollSlot.captured.showInstructionsScreen)
    }

    @Test
    fun `enroll with retrieveSelfie true sets shouldRetrieveEnrollmentFrame on BiomEnrollConfig`() = runTest {
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll { retrieveSelfie = true }.isSuccess)
        assertTrue(enrollSlot.captured.shouldRetrieveEnrollmentFrame)
    }

    @Test
    fun `enroll with retrieveSelfie false sets shouldRetrieveEnrollmentFrame false on BiomEnrollConfig`() = runTest {
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(false, enrollSlot.captured.shouldRetrieveEnrollmentFrame)
    }

    @Test
    fun `enroll selfie is returned in RecognizeSuccess when frame is present`() = runTest {
        val bitmap = mockk<Bitmap>()
        coEvery { Recognize.enroll(any()) } returns Result.success(mockk {
            every { signedJwt } returns "signed-jwt"
            every { clientState } returns "client-state"
            every { keylessId } returns "keyless-id"
            every { enrollmentFrame } returns bitmap
        })
        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        val result = callback.enroll { retrieveSelfie = true }
        assertTrue(result.isSuccess)
        assertEquals(bitmap, result.getOrThrow().selfie)
    }

    @Test
    fun `enroll mobileSDKOptions presentation maps to presentationStyle`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "ENROLL" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "presentation": "OVERLAY" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",       "value": "" },
                { "name": "IDToken1clientState",     "value": "" },
                { "name": "IDToken1recognizeId",     "value": "" },
                { "name": "IDToken1clientError",     "value": "" },
                { "name": "IDToken1clientErrorCode", "value": "" }
              ]
            }
            """
        ) as JsonObject
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(
            io.keyless.sdk.configurations.enroll.PresentationStyle.OVERLAY,
            enrollSlot.captured.presentationStyle
        )
    }

    @Test
    fun `enroll numberOfEnrollmentCircuits absent uses SDK default`() = runTest {
        val setupSlot = slot<SetupConfig>()
        coEvery { Recognize.setup(capture(setupSlot)) } returns Result.success(Unit)
        val callback = RecognizeCallback().init(enrollCallbackJson()) as PingOneRecognizeEnrollCallback
        assertTrue(callback.enroll().isSuccess)
        assertEquals(SetupConfig.DEFAULT_ENROLLMENT_CIRCUIT_NUMBER, setupSlot.captured.numberOfEnrollmentCircuits)
    }

    // ── auth mobileSDKOptions — remaining fields ─────────────────────────────────

    @Test
    fun `auth operationInfo is null when no operationInfo keys present`() = runTest {
        val authSlot = slot<BiomAuthConfig>()
        coEvery { Recognize.authenticate(capture(authSlot)) } returns Result.success(authSuccess)
        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertEquals(null, authSlot.captured.operationInfo)
    }

    @Test
    fun `auth mobileSDKOptions livenessEnvironmentAware is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "AUTHENTICATE" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "livenessEnvironmentAware": "true" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject
        val authSlot = slot<BiomAuthConfig>()
        coEvery { Recognize.authenticate(capture(authSlot)) } returns Result.success(authSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertTrue(authSlot.captured.livenessEnvironmentAware)
    }

    @Test
    fun `auth mobileSDKOptions cameraDelaySeconds is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "AUTHENTICATE" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "cameraDelaySeconds": "3" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject
        val authSlot = slot<BiomAuthConfig>()
        coEvery { Recognize.authenticate(capture(authSlot)) } returns Result.success(authSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertEquals(3, authSlot.captured.cameraDelaySeconds)
    }

    @Test
    fun `auth mobileSDKOptions showSuccessFeedback is forwarded`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "AUTHENTICATE" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "showSuccessFeedback": "false" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject
        val authSlot = slot<BiomAuthConfig>()
        coEvery { Recognize.authenticate(capture(authSlot)) } returns Result.success(authSuccess)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertEquals(false, authSlot.captured.showSuccessFeedback)
    }

    @Test
    fun `authenticate with retrieveSelfie true sets shouldRetrieveAuthenticationFrame on BiomAuthConfig`() = runTest {
        val authSlot = slot<BiomAuthConfig>()
        coEvery { Recognize.authenticate(capture(authSlot)) } returns Result.success(authSuccess)
        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate { retrieveSelfie = true }.isSuccess)
        assertTrue(authSlot.captured.shouldRetrieveAuthenticationFrame)
    }

    @Test
    fun `authenticate with retrieveSelfie false sets shouldRetrieveAuthenticationFrame false`() = runTest {
        val authSlot = slot<BiomAuthConfig>()
        coEvery { Recognize.authenticate(capture(authSlot)) } returns Result.success(authSuccess)
        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertEquals(false, authSlot.captured.shouldRetrieveAuthenticationFrame)
    }

    @Test
    fun `authenticate selfie is returned in RecognizeSuccess when frame is present`() = runTest {
        val bitmap = mockk<Bitmap>()
        coEvery { Recognize.authenticate(any()) } returns Result.success(mockk {
            every { signedJwt } returns "signed-jwt"
            every { clientState } returns "client-state"
            every { authenticationFrame } returns bitmap
        })
        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate { retrieveSelfie = true }
        assertTrue(result.isSuccess)
        assertEquals(bitmap, result.getOrThrow().selfie)
    }

    @Test
    fun `authenticate enroll from clientState with retrieveSelfie true returns enrollment selfie`() = runTest {
        val bitmap = mockk<Bitmap>()
        coEvery { Recognize.validateUserAndDeviceActive() } returns Result.failure(IOException("not enrolled"))
        coEvery { Recognize.enroll(any()) } returns Result.success(mockk {
            every { signedJwt } returns "signed-jwt"
            every { clientState } returns "client-state"
            every { keylessId } returns "keyless-id"
            every { enrollmentFrame } returns bitmap
        })
        val callback = RecognizeCallback().init(authWithClientStateJson()) as PingOneRecognizeAuthenticateCallback
        val result = callback.authenticate { retrieveSelfie = true }
        assertTrue(result.isSuccess)
        assertEquals(bitmap, result.getOrThrow().selfie)
    }

    @Test
    fun `authenticate enroll from clientState with retrieveSelfie true sets shouldRetrieveEnrollmentFrame`() = runTest {
        coEvery { Recognize.validateUserAndDeviceActive() } returns Result.failure(IOException("not enrolled"))
        val enrollSlot = slot<BiomEnrollConfig>()
        coEvery { Recognize.enroll(capture(enrollSlot)) } returns Result.success(enrollSuccess)
        val callback = RecognizeCallback().init(authWithClientStateJson()) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate { retrieveSelfie = true }.isSuccess)
        assertTrue(enrollSlot.captured.shouldRetrieveEnrollmentFrame)
    }

    @Test
    fun `auth mobileSDKOptions numberOfEnrollmentCircuits absent uses SDK default`() = runTest {
        val setupSlot = slot<SetupConfig>()
        coEvery { Recognize.setup(capture(setupSlot)) } returns Result.success(Unit)
        val callback = RecognizeCallback().init(authCallbackJson()) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertEquals(SetupConfig.DEFAULT_ENROLLMENT_CIRCUIT_NUMBER, setupSlot.captured.numberOfEnrollmentCircuits)
    }

    @Test
    fun `auth mobileSDKOptions numberOfEnrollmentCircuits is forwarded to SetupConfig`() = runTest {
        val json = Json.parseToJsonElement(
            """
            {
              "type": "PingOneRecognizeCallback",
              "output": [
                { "name": "operationType",    "value": "AUTHENTICATE" },
                { "name": "host",             "value": "h" },
                { "name": "apiKey",           "value": "k" },
                { "name": "mobileSDKOptions", "value": { "numberOfEnrollmentCircuits": "5" } }
              ],
              "input": [
                { "name": "IDToken1signedJwt",              "value": "" },
                { "name": "IDToken1clientState",            "value": "" },
                { "name": "IDToken1recognizeId",            "value": "" },
                { "name": "IDToken1devicePublicSigningKey", "value": "" },
                { "name": "IDToken1clientError",            "value": "" },
                { "name": "IDToken1clientErrorCode",        "value": "" }
              ]
            }
            """
        ) as JsonObject
        val setupSlot = slot<SetupConfig>()
        coEvery { Recognize.setup(capture(setupSlot)) } returns Result.success(Unit)
        val callback = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback
        assertTrue(callback.authenticate().isSuccess)
        assertEquals(5, setupSlot.captured.numberOfEnrollmentCircuits)
    }

}
