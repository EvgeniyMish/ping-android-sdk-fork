/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Abstract base for DaVinci Recognize collectors.
 *
 * Parses all common server output fields from the DaVinci JSON payload and stores them
 * for use by concrete subclasses ([RecognizeEnrollCollector], [RecognizeAuthenticateCollector]).
 *
 * The field names mirror [com.pingidentity.recognize.journey.AbstractRecognizeCallback] on the
 * Journey side so that [mobileSDKOptions] sub-field logic can be ported verbatim between the two.
 *
 * Subclasses implement [collect] to drive the Recognize SDK and must populate [result] on both
 * the success and failure paths before returning, so that [payload] is non-null even when the
 * SDK call fails (enabling the server to receive the `clientError` value).
 */
abstract class AbstractRecognizeCollector : Collector<JsonObject> {

    /** The form-field key; becomes the `formData` field name submitted to the server. */
    var key: String = ""
        private set

    /** PingOne Recognize node host URL. */
    var host: String = ""
        private set

    /** API key for the Keyless / Recognize SDK. */
    var apiKey: String = ""
        private set

    /** Opaque transaction data to be signed by the SDK. */
    var transactionData: String = ""
        private set

    /** JWT audience claim forwarded to the Keyless SDK. */
    var audience: String = ""
        private set

    /** Existing client state payload supplied by the server. */
    var clientState: String = ""
        private set

    /** Indicates whether the SDK should generate a new client state (`"true"` / `"false"`). */
    var generateClientState: String = ""
        private set

    /**
     * Options forwarded to the PingOne Recognize mobile SDK.
     *
     * Relevant keys include `livenessConfiguration`, `operationInfoId`,
     * `operationInfoPayload`, `presentation`, `presentationStyle`, etc.
     */
    var mobileSDKOptions: JsonObject = JsonObject(emptyMap())
        private set

    /**
     * The JSON result stored after [collect] completes.
     *
     * `null` before [collect] is called. Subclasses populate this field on both success and
     * failure paths so that [payload] is non-null even when the SDK call fails.
     */
    protected var result: JsonObject? = null

    /**
     * Parses all common fields from the server JSON object.
     *
     * @param input The JSON object received from the DaVinci server.
     * @return This collector instance.
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        key = input["key"]?.jsonPrimitive?.contentOrNull ?: ""
        host = input["host"]?.jsonPrimitive?.contentOrNull ?: ""
        apiKey = input["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
        transactionData = input["transactionData"]?.jsonPrimitive?.contentOrNull ?: ""
        audience = input["audience"]?.jsonPrimitive?.contentOrNull ?: ""
        clientState = input["clientState"]?.jsonPrimitive?.contentOrNull ?: ""
        generateClientState = input["generateClientState"]?.jsonPrimitive?.contentOrNull ?: ""
        mobileSDKOptions = input["mobileSDKOptions"] as? JsonObject ?: JsonObject(emptyMap())
        return this
    }

    /**
     * Returns the `key` field as the collector ID, which maps to the `formData` field name
     * in the assembled DaVinci request.
     */
    override fun id(): String = key

    /**
     * Returns the JSON payload to be submitted to the server, or `null` if [collect] has not
     * yet been called.
     */
    override fun payload(): JsonObject? = result

    /**
     * Runs the Recognize SDK operation for this collector (enroll or authenticate).
     *
     * Implementations must populate [result] on both success and failure paths before returning,
     * so that [payload] is non-null even when the SDK call fails — allowing the server to receive
     * the `clientError` value in the form data.
     *
     * Implementations should call `coroutineContext.ensureActive()` before wrapping a caught
     * exception as `Result.failure` to preserve cooperative cancellation semantics.
     *
     * @return [Result.success] on SDK success, [Result.failure] on SDK or setup failure.
     */
    abstract suspend fun collect(): Result<JsonObject>
}
