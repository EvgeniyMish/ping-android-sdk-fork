/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.davinci

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.orchestrate.Closeable
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfa.commons.PingOneMFAException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A DaVinci [Collector] for the `MOBILE_PAIRING` node type.
 *
 * When a DaVinci flow presents a `MOBILE_PAIRING` collector, the server supplies a
 * `pairingKey` in the node JSON. This class reads that key, calls [PingOneMFA.pair],
 * and posts the outcome back to the server as a JSON object via [payload].
 *
 * ## Resume envelope
 * Implementing [Submittable] with `eventType() = "action"` causes the DaVinci core to emit
 * `parameters.eventType: "action"` on the resume POST when [payload] is non-null. The core
 * then places [payload] under `formData[[id]]`; [id] returns [key] (`"mobilePairing"` per
 * the connector spec), so the final envelope shape is:
 *
 * - Success: `formData.mobilePairing = { "status": "CLAIMED" }`
 * - Failure: `formData.mobilePairing = { "error": { "code": "<nativeCode>", "message": "<message>" } }` — where `<nativeCode>` is the numeric native SDK error code as a string (e.g. `"10005"`), or `"INTERNAL_ERROR"` for unexpected failures.
 * - Cancel:  `formData.mobilePairing = { "error": { "code": "USER_CANCELLED", "message": "…" } }`
 *
 * ## Lifecycle
 * 1. [CollectorInitializer] registers this class with [com.pingidentity.davinci.plugin.CollectorFactory]
 *    at app startup under the key `"MOBILE_PAIRING"`.
 * 2. When the DaVinci engine receives a node containing a `MOBILE_PAIRING` collector, the factory
 *    instantiates this class and calls [init] with the server-provided JSON object.
 * 3. The UI calls [collect] to perform pairing, or [cancel] if the user abandons the flow.
 *    A spinner or loading indicator should be shown while [collect] is in progress.
 * 4. The DaVinci engine reads [payload] when `node.next()` is called and includes the outcome
 *    in the resume POST body.
 *
 * ## Cancellation semantics
 * The native PingOne MFA SDK does not expose an API to abort an in-flight pairing. Calling
 * [cancel] only changes what [payload] will report to DaVinci — any native pairing operation
 * already in flight will run to completion in the background, and its result is discarded
 * so the user-canceled payload is preserved.
 *
 * @see CollectorInitializer
 * @see PingOneMFA.pair
 */
class MobilePairingCollector : Collector<JsonObject>, Submittable, Closeable {

    /**
     * The field key sent by the DaVinci server, used as this collector's [id].
     * Populated during [init]; defaults to an empty string if the server omits the field.
     */
    var key: String = ""
        private set

    /**
     * The pairing key supplied by the DaVinci server.
     * Populated during [init] and passed to [PingOneMFA.pair] in [collect].
     */
    var pairingKey: String = ""
        private set

    /**
     * Guards mutations to [value] and reads/writes of [cancelled] so a late-arriving
     * pairing callback cannot clobber a cancellation payload committed by [cancel].
     */
    private val lock = Any()

    @Volatile
    private var value: JsonObject? = null

    /**
     * `true` once [cancel] has been called. Guards [collect] from overwriting a user-canceled
     * payload if a native pairing callback lands after cancellation. Read and written only
     * inside `synchronized(lock)`.
     */
    private var cancelled: Boolean = false

    /**
     * Initializes this collector from the server-provided JSON object.
     *
     * Reads the `key` field (used as the collector's [id]) and the `pairingKey` field
     * (passed to [PingOneMFA.pair] during [collect]). The `pairingKey` field is required:
     * missing, empty, or JSON-null values throw [IllegalArgumentException].
     *
     * @param input The JSON object for this collector entry from the DaVinci node response.
     * @return This collector instance.
     * @throws IllegalArgumentException If `pairingKey` is absent, empty, or JSON null.
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        key = input.stringOrEmpty("key")
        pairingKey = input.stringOrEmpty("pairingKey")
        require(pairingKey.isNotEmpty()) {
            "pairingKey is required for the MOBILE_PAIRING collector"
        }
        return this
    }

    /**
     * Returns [key] as the unique identifier for this collector. The DaVinci core uses this
     * value as the field name under `formData` in the resume POST.
     */
    override fun id(): String = key

    /**
     * Returns `"action"` as the DaVinci event type.
     *
     * The core inspects this only when [payload] is non-null. When set, it becomes
     * `parameters.eventType` on the resume POST — matching the contract used by
     * self-submitting SDK Integrator connectors.
     */
    override fun eventType(): String = "action"

    /**
     * Returns the pairing outcome to be posted back to DaVinci under `formData[[id]]`, or `null`
     * if neither [collect] nor [cancel] has been called yet.
     *
     * Success: `{ "status": "CLAIMED" }`
     * Failure/cancel: `{ "error": { "code": "…", "message": "…" } }`
     */
    override fun payload(): JsonObject? = value

    /**
     * Pairs the device with a PingOne MFA account using the [pairingKey] received from the
     * DaVinci server, then stores the outcome for submission via [payload].
     *
     * This is a suspending function and must be called from a coroutine. The UI should display
     * a loading indicator while this call is in progress.
     *
     * On success, stores `{ "status": "CLAIMED" }` and returns [Result.success].
     * On failure, stores `{ "error": { "code": "…", "message": "…" } }` and returns
     * [Result.failure] with the underlying [PingOneMFAException].
     *
     * If [cancel] is called while this function is awaiting a pairing result, the native
     * outcome is discarded so the user-canceled payload is preserved. The [Result] value
     * from the native SDK is still returned to the caller so it can be logged or forwarded
     * to telemetry.
     *
     * @return [Result.success] if pairing succeeded, or [Result.failure] with the underlying
     *   [PingOneMFAException].
     */
    suspend fun collect(): Result<Unit> {
        val result = PingOneMFA.pair(pairingKey)
        val outcome: JsonObject = result.fold(
            onSuccess = { buildSuccess() },
            onFailure = { e -> buildError(mapError(e)) },
        )
        // Commit the outcome under the same lock cancel() uses, so we cannot
        // clobber a cancellation payload that arrived between the pair() return
        // and this write.
        synchronized(lock) {
            if (!cancelled) {
                value = outcome
            }
        }
        return result
    }

    /**
     * Records a user-initiated cancellation and stores the corresponding error envelope
     * so it can be posted back to DaVinci via [payload].
     *
     * Stores `{ "error": { "code": "USER_CANCELLED", "message": [message] } }` under [payload].
     *
     * Note: this does **not** cancel any in-flight [collect] call — the native PingOne MFA SDK
     * has no abort API. If [collect] is currently awaiting a pairing result, its outcome will
     * be discarded when it resumes so the user-canceled payload is preserved.
     *
     * @param message A human-readable description of the cancellation reason.
     *   Defaults to `"User canceled the pairing flow"`.
     */
    fun cancel(message: String = "User canceled the pairing flow") {
        val payload = buildError(ErrorInfo(code = "USER_CANCELLED", message = message))
        synchronized(lock) {
            cancelled = true
            value = payload
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Safely extracts a string field from a server-provided JSON object.
     *
     * Returns the primitive's content for JSON strings (and, leniently, numbers/booleans),
     * `null` for JSON null, and `""` when the field is absent or non-primitive (object,
     * array) — instead of throwing. Used in [init] so malformed server input degrades to
     * the empty string and flows into the single `require` validation below rather than
     * escaping as an opaque [IllegalArgumentException] from the serialization library.
     */
    private fun JsonObject.stringOrEmpty(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private data class ErrorInfo(val code: String, val message: String)

    private fun buildSuccess(): JsonObject = buildJsonObject {
        put("status", "CLAIMED")
    }

    private fun buildError(info: ErrorInfo): JsonObject = buildJsonObject {
        put("error", buildJsonObject {
            put("code", info.code)
            put("message", info.message)
        })
    }

    /**
     * Maps a pairing failure to a `{ code, message }` pair for the resume envelope.
     *
     * The server-side connector treats any presence of `error` as the error branch and
     * independently re-verifies pairing status via `readPairingKey`, so the code is
     * telemetry.
     */
    private fun mapError(throwable: Throwable): ErrorInfo {
        val message = throwable.message ?: "Pairing failed"
        val nativeCode = (throwable as? PingOneMFAException)
            ?.internalErrorsList
            ?.firstOrNull()
            ?.code
        val code = if (nativeCode != null) "$nativeCode" else "INTERNAL_ERROR"
        return ErrorInfo(code = code, message = message)
    }

    override fun close() {
        synchronized(lock) {
            value = null
            cancelled = false
        }
    }
}
