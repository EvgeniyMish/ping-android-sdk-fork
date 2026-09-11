/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.journey.plugin.AbstractCallback
import io.keyless.sdk.biom.liveness.LivenessSettings
import io.keyless.sdk.configurations.ClientStateType
import io.keyless.sdk.configurations.OperationInfo
import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.enroll.BiomEnrollConfig
import io.keyless.sdk.configurations.enroll.PresentationStyle as EnrollPresentationStyle
import io.keyless.sdk.core.actions.model.JwtSigningInfo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Abstract base for Recognize Journey callbacks.
 *
 * Parses all common output fields from the server response and exposes them as typed properties.
 *
 * Shared config builders ([buildSetupConfig], [buildOperationInfo], [buildJwtSigningInfo],
 * [buildGeneratingClientState]) are defined here. The enroll-specific [buildEnrollConfig] is a
 * package-level extension on this class to keep enroll SDK types out of the base.
 */
abstract class AbstractRecognizeCallback : AbstractCallback() {

    // ── Common output fields ──────────────────────────────────────────────────

    /** URL of the PingOne Recognize authentication service WebSocket. */
    var websocketURL: String = ""
        private set

    /** Customer / tenant name configured on the server. */
    var customerName: String = ""
        private set

    /** RSA public key used to encrypt images before transmission. */
    var imageEncryptionPublicKey: String = ""
        private set

    /** Key ID that corresponds to [imageEncryptionPublicKey]. */
    var imageEncryptionKeyId: String = ""
        private set

    /** PingOne Recognize node host URL. */
    var host: String = ""
        private set

    /** API key for the Keyless / Recognize SDK. */
    var apiKey: String = ""
        private set

    /** Username of the subject. */
    var username: String = ""
        private set

    /** Opaque transaction data to be signed by the SDK. */
    var transactionData: String = ""
        private set

    /** JWT audience claim forwarded to the Keyless SDK. */
    var audience: String = ""
        private set

    /** Indicates whether the SDK should generate a new client state. */
    var generateClientState: String = ""
        private set

    /** Existing client state payload supplied by the server. */
    var clientState: String = ""
        private set

    /**
     * Options forwarded to the PingOne Recognize **mobile** SDK.
     *
     * Relevant keys include `livenessConfiguration`, `operationInfoId`,
     * `operationInfoPayload`, `customSecret`, `presentation`, etc.
     */
    var mobileSDKOptions: JsonObject = JsonObject(emptyMap())
        private set

    /**
     * Parses a single named output field and stores it in the corresponding property.
     *
     * Called once per field in the server JSON output array.
     *
     * @param name Field name as returned by the server (e.g. `"transactionData"`).
     * @param value Raw JSON element for the field value.
     */
    override fun init(name: String, value: JsonElement) {
        when (name) {
            "websocketURL" -> websocketURL = value.jsonPrimitive.content
            "customerName" -> customerName = value.jsonPrimitive.content
            "imageEncryptionPublicKey" -> imageEncryptionPublicKey = value.jsonPrimitive.content
            "imageEncryptionKeyId" -> imageEncryptionKeyId = value.jsonPrimitive.content
            "host" -> host = value.jsonPrimitive.content
            "apiKey" -> apiKey = value.jsonPrimitive.content
            "username" -> username = value.jsonPrimitive.content
            "transactionData" -> transactionData = value.jsonPrimitive.content
            "audience" -> audience = value.jsonPrimitive.content
            "generateClientState" -> generateClientState = value.jsonPrimitive.content
            "clientState" -> clientState = value.jsonPrimitive.content
            "mobileSDKOptions" -> if (value is JsonObject) mobileSDKOptions = value
            else -> {}
        }
    }

    /**
     * Builds a [SetupConfig] from the output fields received from the server.
     */
    internal fun buildSetupConfig(): SetupConfig = SetupConfig(
        apiKey = apiKey,
        hosts = listOf(host),
        numberOfEnrollmentCircuits = mobileSDKOptions["numberOfEnrollmentCircuits"]
            ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: SetupConfig.DEFAULT_ENROLLMENT_CIRCUIT_NUMBER,
    )

    /**
     * Builds an [OperationInfo] from the `mobileSDKOptions` fields, or returns `null` if none
     * of `operationInfoId`, `operationInfoPayload`, or `operationInfoExternalUserId` are present.
     */
    internal fun buildOperationInfo(): OperationInfo? {
        val opId = mobileSDKOptions["operationInfoId"]?.jsonPrimitive?.contentOrNull
        val opPayload = mobileSDKOptions["operationInfoPayload"]?.jsonPrimitive?.contentOrNull
        val opExternalUserId = mobileSDKOptions["operationInfoExternalUserId"]?.jsonPrimitive?.contentOrNull
        return if (opId != null || opPayload != null || opExternalUserId != null) {
            OperationInfo(
                operationId = opId ?: "",
                payload = opPayload ?: "",
                externalUserId = opExternalUserId ?: "",
            )
        } else null
    }

    /**
     * Builds a [JwtSigningInfo] from [transactionData] and [audience].
     * The `audience` claim is omitted when blank.
     */
    internal fun buildJwtSigningInfo(): JwtSigningInfo = if (audience.isNotBlank()) {
        JwtSigningInfo(claimTransactionData = transactionData, audience = audience)
    } else {
        JwtSigningInfo(claimTransactionData = transactionData)
    }

    /**
     * Returns [ClientStateType.BACKUP] when the server instructs the SDK to generate a new client
     * state, or `null` otherwise.
     */
    internal fun buildGeneratingClientState(): ClientStateType? =
        if (generateClientState.equals("true", ignoreCase = true)) ClientStateType.BACKUP else null

}

/**
 * Builds a [BiomEnrollConfig] from this callback's current output fields.
 *
 * Kept as a package-level extension so the enroll-specific SDK types
 * ([BiomEnrollConfig], enroll [io.keyless.sdk.configurations.enroll.PresentationStyle]) do not leak into [AbstractRecognizeCallback].
 *
 * @param clientStateOverride When non-null, overrides the server-supplied `clientState` field.
 * Pass an explicit value when enrolling from a clientState received during authentication.
 * @param retrieveSelfie When true, instructs the Keyless SDK to capture and return an enrollment frame.
 */
internal fun AbstractRecognizeCallback.buildEnrollConfig(
    clientStateOverride: String? = null,
    retrieveSelfie: Boolean = false,
): BiomEnrollConfig {
    val opts = mobileSDKOptions
    var config = BiomEnrollConfig(
        operationInfo = buildOperationInfo(),
        jwtSigningInfo = buildJwtSigningInfo(),
        generatingClientState = buildGeneratingClientState(),
        clientState = (clientStateOverride ?: clientState).takeIf { it.isNotEmpty() },
    )
    opts["livenessConfiguration"]?.jsonPrimitive?.contentOrNull
        ?.let { runCatching { LivenessSettings.LivenessConfiguration.valueOf(it) }.getOrNull() }
        ?.let { config = config.copy(livenessConfiguration = it) }
    opts["livenessEnvironmentAware"]?.jsonPrimitive?.contentOrNull?.toBoolean()
        ?.let { config = config.copy(livenessEnvironmentAware = it) }
    opts["cameraDelaySeconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        ?.let { config = config.copy(cameraDelaySeconds = it) }
    opts["showSuccessFeedback"]?.jsonPrimitive?.contentOrNull?.toBoolean()
        ?.let { config = config.copy(showSuccessFeedback = it) }
    opts["showFailureFeedback"]?.jsonPrimitive?.contentOrNull?.toBoolean()
        ?.let { config = config.copy(showFailureFeedback = it) }
    opts["showInstructionsScreen"]?.jsonPrimitive?.contentOrNull?.toBoolean()
        ?.let { config = config.copy(showInstructionsScreen = it) }
    config = config.copy(shouldRetrieveEnrollmentFrame = retrieveSelfie)
    opts["presentation"]?.jsonPrimitive?.contentOrNull
        ?.let { runCatching { EnrollPresentationStyle.valueOf(it) }.getOrNull() }
        ?.let { config = config.copy(presentationStyle = it) }
    return config
}
