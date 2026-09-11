/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.davinci

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKError
import com.pingidentity.pingidsdkv2.types.PairingInfo
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobilePairingCollectorTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockPairingInfo = mockk<PairingInfo>(relaxed = true)

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        // Route ContextProvider.context to a mock — PingOneMFA.pair passes it to PingOne.pair.
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext

        // Mock PingOne static functions (Java class).
        mockkStatic("com.pingidentity.pingidsdkv2.PingOne")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── init / basic accessors ─────────────────────────────────────────────────

    @Test
    fun `init reads key and pairingKey from input`() {
        val collector = MobilePairingCollector()
        collector.init(
            buildJsonObject {
                put("type", "MOBILE_PAIRING")
                put("key", "mobilePairing")
                put("pairingKey", "01693783940919")
            }
        )
        assertEquals("mobilePairing", collector.key)
        assertEquals("01693783940919", collector.pairingKey)
    }

    @Test
    fun `init returns this for chaining`() {
        val collector = MobilePairingCollector()
        val result = collector.init(buildJsonObject {
            put("key", "mobilePairing")
            put("pairingKey", "pk-1")
        })
        assertEquals(collector, result)
    }

    @Test
    fun `init throws IllegalArgumentException when pairingKey is absent`() {
        val collector = MobilePairingCollector()
        assertFailsWith<IllegalArgumentException> {
            collector.init(buildJsonObject { put("key", "mobilePairing") })
        }
    }

    @Test
    fun `init throws IllegalArgumentException when pairingKey is empty`() {
        val collector = MobilePairingCollector()
        assertFailsWith<IllegalArgumentException> {
            collector.init(buildJsonObject {
                put("key", "mobilePairing")
                put("pairingKey", "")
            })
        }
    }

    @Test
    fun `init throws IllegalArgumentException when pairingKey is JSON null`() {
        val collector = MobilePairingCollector()
        assertFailsWith<IllegalArgumentException> {
            collector.init(buildJsonObject {
                put("key", "mobilePairing")
                put("pairingKey", JsonNull)
            })
        }
    }

    // ── init: non-primitive JSON values degrade to empty string ────────────────

    @Test
    fun `init treats object-valued pairingKey as missing and throws the controlled error`() {
        // Regression guard: previously init read pairingKey with jsonPrimitive, which throws
        // IllegalArgumentException("JsonPrimitive") from kotlinx-serialization when the field
        // is present but non-primitive. Malformed server input must instead flow into the
        // collector's own validation with its descriptive message.
        val collector = MobilePairingCollector()
        val e = assertFailsWith<IllegalArgumentException> {
            collector.init(buildJsonObject {
                put("key", "mobilePairing")
                put("pairingKey", buildJsonObject { put("nested", "value") })
            })
        }
        assertTrue(
            e.message!!.contains("pairingKey is required"),
            "expected the collector's validation message, got: ${e.message}",
        )
    }

    @Test
    fun `init treats array-valued pairingKey as missing`() {
        val collector = MobilePairingCollector()
        assertFailsWith<IllegalArgumentException> {
            collector.init(buildJsonObject {
                put("key", "mobilePairing")
                put("pairingKey", buildJsonArray { add(JsonPrimitive("pk-1")) })
            })
        }
    }

    @Test
    fun `init treats non-primitive key as absent and defaults to empty string`() {
        // key stays lenient: any non-string value (object/array/null) must degrade to "",
        // never the literal "null" (JsonNull is a JsonPrimitive whose content is "null").
        val collector = MobilePairingCollector()
        collector.init(buildJsonObject {
            put("key", buildJsonObject { put("nested", "value") })
            put("pairingKey", "pk-1")
        })
        assertEquals("", collector.key)
        assertEquals("pk-1", collector.pairingKey)
    }

    @Test
    fun `init treats JSON null key as empty string not the literal null`() {
        // Guards the stringOrEmpty behaviour: contentOrNull (not content) must be used so
        // JsonNull yields null -> "", not the string "null".
        val collector = MobilePairingCollector()
        collector.init(buildJsonObject {
            put("key", JsonNull)
            put("pairingKey", "pk-1")
        })
        assertEquals("", collector.key)
        assertEquals("pk-1", collector.pairingKey)
    }

    @Test
    fun `init keeps key lenient and still defaults to empty when absent`() {
        // Only pairingKey is validated; a missing `key` degrades to "" as before.
        val collector = MobilePairingCollector()
        collector.init(buildJsonObject { put("pairingKey", "pk-1") })
        assertEquals("", collector.key)
        assertEquals("pk-1", collector.pairingKey)
    }

    @Test
    fun `id returns key`() {
        val collector = MobilePairingCollector().apply {
            init(buildJsonObject {
                put("key", "mobilePairing")
                put("pairingKey", "pk-1")
            })
        }
        assertEquals("mobilePairing", collector.id())
    }

    @Test
    fun `eventType is action`() {
        val collector = MobilePairingCollector()
        assertEquals("action", collector.eventType())
    }

    @Test
    fun `collector implements Submittable`() {
        val collector: Submittable = MobilePairingCollector()
        assertEquals("action", collector.eventType())
    }

    @Test
    fun `payload is null before collect or cancel`() {
        val collector = MobilePairingCollector().apply {
            init(buildJsonObject {
                put("key", "mobilePairing")
                put("pairingKey", "pk-1")
            })
        }
        assertNull(collector.payload())
    }

    // ── collect() success ──────────────────────────────────────────────────────

    @Test
    fun `collect stores CLAIMED payload on success`() = runTest {
        stubPairSuccess()

        val collector = newCollector(pairingKey = "pk-1")
        val result = collector.collect()

        assertTrue(result.isSuccess)
        val payload = collector.payload()
        assertNotNull(payload)
        assertEquals("CLAIMED", payload.string("status"))
    }

    @Test
    fun `collect payload is not double-wrapped under mobilePairing key`() = runTest {
        // Regression guard: earlier revision returned { "mobilePairing": { "status": "CLAIMED" } }
        // which the core would then place under formData["mobilePairing"] — producing
        // formData.mobilePairing.mobilePairing.status.
        stubPairSuccess()

        val collector = newCollector()
        collector.collect()

        val payload = collector.payload()!!
        assertNull(payload["mobilePairing"], "payload must not wrap itself under 'mobilePairing'")
        assertNotNull(payload["status"])
    }

    // ── collect() native failure ───────────────────────────────────────────────

    @Test
    fun `collect maps native error code to numeric string and preserves message`() = runTest {
        stubPairError(PingOneSDKError(10005, "Invalid pairing key"))

        val collector = newCollector()
        val result = collector.collect()

        assertTrue(result.isFailure)
        val error = collector.payload()!!.jsonObject("error")
        assertEquals("10005", error.string("code"))
        assertEquals("Invalid pairing key", error.string("message"))
    }

    @Test
    fun `collect falls back to INTERNAL_ERROR when native call throws unexpected exception`() = runTest {
        // Unexpected exception path: PingOneMFA.pair catches it and wraps as PingOneMFAException(cause);
        // internalErrorsList is null, so the collector must map to INTERNAL_ERROR.
        every {
            PingOne.pair(any(), any(), any())
        } throws RuntimeException("boom")

        val collector = newCollector()
        val result = collector.collect()

        assertTrue(result.isFailure)
        val error = collector.payload()!!.jsonObject("error")
        assertEquals("INTERNAL_ERROR", error.string("code"))
        assertEquals("boom", error.string("message"))
    }

    @Test
    fun `collect preserves numeric code and message across multiple native error codes`() = runTest {
        // Guards against accidentally hardcoding a single code in mapError.
        stubPairError(PingOneSDKError(10013, "Pairing already running"))

        val collector = newCollector()
        collector.collect()

        val error = collector.payload()!!.jsonObject("error")
        assertEquals("10013", error.string("code"))
        assertEquals("Pairing already running", error.string("message"))
    }

    // ── cancel() ───────────────────────────────────────────────────────────────

    @Test
    fun `cancel sets USER_CANCELED payload with default message`() {
        val collector = newCollector()
        collector.cancel()

        val error = collector.payload()!!.jsonObject("error")
        assertEquals("USER_CANCELLED", error.string("code"))
        assertEquals("User canceled the pairing flow", error.string("message"))
    }

    @Test
    fun `cancel uses custom message when provided`() {
        val collector = newCollector()
        collector.cancel("Timed out")

        val error = collector.payload()!!.jsonObject("error")
        assertEquals("Timed out", error.string("message"))
    }

    // ── race: cancel during in-flight collect ──────────────────────────────────

    @Test
    fun `cancel during collect preserves cancellation payload even after native success`() = runTest {
        val gate = CompletableDeferred<Unit>()
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKPairingCallback>(2)
            // Suspend delivery of the native callback until the gate opens.
            gate.invokeOnCompletion { callback.onComplete(mockPairingInfo, null) }
        }

        val collector = newCollector()
        val job = async { collector.collect() }

        // Simulate the user cancelling while pair() is still in flight.
        collector.cancel("Left the screen")
        // Now let the native pair complete with success.
        gate.complete(Unit)

        val result = job.await()
        // collect() still returns the native result so callers can log/telemetry it.
        assertTrue(result.isSuccess)
        // But payload preserves the cancellation.
        val error = collector.payload()!!.jsonObject("error")
        assertEquals("USER_CANCELLED", error.string("code"))
        assertEquals("Left the screen", error.string("message"))
    }

    @Test
    fun `cancel during collect preserves cancellation payload even after native failure`() = runTest {
        val gate = CompletableDeferred<Unit>()
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKPairingCallback>(2)
            gate.invokeOnCompletion {
                callback.onComplete(null, PingOneSDKError(10005, "bad key"))
            }
        }

        val collector = newCollector()
        val job = async { collector.collect() }

        collector.cancel()
        gate.complete(Unit)

        val result = job.await()
        assertTrue(result.isFailure)
        val error = collector.payload()!!.jsonObject("error")
        assertEquals("USER_CANCELLED", error.string("code"))
    }

    @Test
    fun `cancel before collect prevents collect from overwriting the cancellation payload`() = runTest {
        // Guards the synchronized-block invariant: cancel() sets both `cancelled = true`
        // and the USER_CANCELLED payload under `lock`, and collect() commits its outcome
        // only inside `synchronized(lock) { if (!cancelled) … }`. If that guard is ever
        // removed or reordered, a `collect()` running after `cancel()` will silently
        // overwrite the cancellation payload with a success/failure envelope.
        stubPairSuccess()

        val collector = newCollector()
        collector.cancel("Aborted")
        val result = collector.collect()

        assertTrue(result.isSuccess)
        val error = collector.payload()!!.jsonObject("error")
        assertEquals("USER_CANCELLED", error.string("code"))
        assertEquals("Aborted", error.string("message"))
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun stubPairSuccess() {
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKPairingCallback>(2)
            callback.onComplete(mockPairingInfo, null)
        }
    }

    private fun stubPairError(error: PingOneSDKError) {
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKPairingCallback>(2)
            callback.onComplete(null, error)
        }
    }

    private fun newCollector(
        key: String = "mobilePairing",
        pairingKey: String = "01693783940919",
    ): MobilePairingCollector = MobilePairingCollector().apply {
        init(
            buildJsonObject {
                put("type", "MOBILE_PAIRING")
                put("key", key)
                put("pairingKey", pairingKey)
            }
        )
    }

    private fun JsonObject.string(key: String): String =
        this[key]!!.jsonPrimitive.content

    private fun JsonObject.jsonObject(key: String): JsonObject =
        this[key]!!.jsonObject
}
