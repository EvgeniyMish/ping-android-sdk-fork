/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import com.pingidentity.recognize.Recognize
import com.pingidentity.recognize.RecognizeException
import io.keyless.sdk.biom.liveness.LivenessSettings
import io.keyless.sdk.configurations.ClientStateType
import io.keyless.sdk.configurations.OperationInfo
import io.keyless.sdk.configurations.PresentationStyle
import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.auth.BiomAuthConfig
import io.keyless.sdk.core.actions.model.JwtSigningInfo
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.coroutineContext

/**
 * DaVinci [com.pingidentity.davinci.plugin.Collector] for the Recognize **authentication** operation.
 *
 * Returned by [RecognizeCollector] when the server JSON contains `operationType = "AUTHENTICATE"`.
 * Chains [Recognize.setup] → [Recognize.authenticate] and populates [payload] with a flat
 * [JsonObject] on both success and failure so the server always receives a response in
 * `formData.{key}`.
 *
 * | Field / mobileSDKOptions key                        | BiomAuthConfig property               |
 * |-----------------------------------------------------|---------------------------------------|
 * | `transactionData`                                   | `jwtSigningInfo.claimTransactionData` |
 * | `generateClientState` (`"true"` → BACKUP)           | `generatingClientState`               |
 * | `mobileSDKOptions.operationInfoId`                  | `operationInfo.operationId`           |
 * | `mobileSDKOptions.operationInfoPayload`             | `operationInfo.payload`               |
 * | `mobileSDKOptions.operationInfoExternalUserId`      | `operationInfo.externalUserId`        |
 * | `mobileSDKOptions.livenessConfiguration`            | `livenessConfiguration`               |
 * | `mobileSDKOptions.livenessEnvironmentAware`         | `livenessEnvironmentAware`            |
 * | `mobileSDKOptions.cameraDelaySeconds`               | `cameraDelaySeconds`                  |
 * | `mobileSDKOptions.showSuccessFeedback`              | `showSuccessFeedback`                 |
 * | `mobileSDKOptions.presentationStyle`                | `presentationStyle`                   |
 * | `mobileSDKOptions.shouldRetriveAuthenticationFrame` | `shouldRetrieveAuthenticationFrame`   |
 *
 * Note: `showFailureFeedback`, `shouldRetrieveSecret`, and `shouldDeleteSecret` are not mapped —
 * `BiomAuthConfig` (SDK 5.8.4) handles them via typed `KeylessSecret` parameters instead.
 *
 * Note: `mobileSDKOptions` uses key `"presentationStyle"` for auth and `"presentation"` for enroll
 * — these are different keys.
 *
 * Note: `shouldRetriveAuthenticationFrame` preserves the server-side typo (missing `e` in
 * `Retrieve`) — this is the actual JSON key sent by the server.
 *
 * @see AbstractRecognizeCollector
 * @see RecognizeCollector
 */
class RecognizeAuthenticateCollector : AbstractRecognizeCollector() {

    /**
     * Performs the Recognize authentication ceremony.
     *
     * Builds [SetupConfig] and [BiomAuthConfig] from the fields parsed by
     * [AbstractRecognizeCollector.init], then chains [Recognize.setup] → [Recognize.authenticate].
     * Populates [result] (and therefore [payload]) on both success and failure paths so that the
     * server receives the `clientError` value in the form data even when authentication fails.
     *
     * @return [Result.success] wrapping the payload [JsonObject] on SDK success;
     *         [Result.failure] on setup or authentication failure.
     */
    override suspend fun collect(): Result<JsonObject> {
        return try {
            val setupConfig = SetupConfig(
                apiKey = apiKey,
                hosts = listOf(host),
            )

            val opts = mobileSDKOptions
            val storedTransactionData = transactionData
            val storedAudience = audience
            val storedGenerateClientState = generateClientState

            val opId = opts["operationInfoId"]?.jsonPrimitive?.contentOrNull
            val opPayload = opts["operationInfoPayload"]?.jsonPrimitive?.contentOrNull
            val opExternalUserId = opts["operationInfoExternalUserId"]?.jsonPrimitive?.contentOrNull
            val operationInfo = if (opId != null || opPayload != null || opExternalUserId != null) {
                OperationInfo(
                    operationId = opId ?: "",
                    payload = opPayload ?: "",
                    externalUserId = opExternalUserId ?: "",
                )
            } else null

            // "true" → ClientStateType.BACKUP, "false"/empty → null
            val storedGeneratingClientState =
                if (storedGenerateClientState.equals("true", ignoreCase = true)) {
                    ClientStateType.BACKUP
                } else null

            // Seed a base instance to read SDK defaults for fields not supplied by the server
            val base = BiomAuthConfig()
            val jwtSigningInfo = if (storedAudience.isNotBlank()) {
                JwtSigningInfo(claimTransactionData = storedTransactionData, audience = storedAudience)
            } else {
                JwtSigningInfo(claimTransactionData = storedTransactionData)
            }

            val biomAuthConfig = BiomAuthConfig(
                operationInfo = operationInfo,
                jwtSigningInfo = jwtSigningInfo,
                dynamicLinkingInfo = base.dynamicLinkingInfo,
                livenessConfiguration = opts["livenessConfiguration"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { LivenessSettings.LivenessConfiguration.valueOf(it) }.getOrNull() }
                    ?: base.livenessConfiguration,
                livenessEnvironmentAware = opts["livenessEnvironmentAware"]?.jsonPrimitive?.contentOrNull
                    ?.toBoolean() ?: base.livenessEnvironmentAware,
                cameraDelaySeconds = opts["cameraDelaySeconds"]?.jsonPrimitive?.contentOrNull
                    ?.toIntOrNull() ?: base.cameraDelaySeconds,
                showSuccessFeedback = opts["showSuccessFeedback"]?.jsonPrimitive?.contentOrNull
                    ?.toBoolean() ?: base.showSuccessFeedback,
                deviceToRevoke = base.deviceToRevoke,
                // Auth uses key "presentationStyle"; enroll uses "presentation" — different keys
                presentationStyle = opts["presentationStyle"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { PresentationStyle.valueOf(it) }.getOrNull() }
                    ?: base.presentationStyle,
                generatingClientState = storedGeneratingClientState,
                // Server sends this key with a typo (missing 'e' in 'Retrieve')
                shouldRetrieveAuthenticationFrame = opts["shouldRetriveAuthenticationFrame"]
                    ?.jsonPrimitive?.contentOrNull?.toBoolean() ?: base.shouldRetrieveAuthenticationFrame,
                savingSecret = base.savingSecret,
                deletingSecret = base.deletingSecret,
                retrievingSecret = base.retrievingSecret,
                shouldRetrieveSecretIDs = base.shouldRetrieveSecretIDs,
            )

            Recognize.setup(setupConfig)
                .fold(
                    onSuccess = { Recognize.authenticate(biomAuthConfig) },
                    onFailure = { Result.failure(it) },
                )
                .fold(
                    onSuccess = { success ->
                        val payload = buildJsonObject {
                            put("signedJwt", success.signedJwt ?: "")
                            put("clientState", success.clientState ?: "")
                            put("recognizeId", "")
                            put("devicePublicSigningKey", "")
                            put("clientError", "")
                            put("clientErrorCode", "")
                        }
                        result = payload
                        Result.success(payload)
                    },
                    onFailure = { error ->
                        val ex = error as RecognizeException
                        result = buildJsonObject {
                            put("signedJwt", "")
                            put("clientState", "")
                            put("recognizeId", "")
                            put("devicePublicSigningKey", "")
                            put("clientError", ex.message)
                            put("clientErrorCode", ex.code.toString())
                        }
                        Result.failure(ex)
                    },
                )
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            // TODO: DaVinci error mapping not yet implemented
            result = buildJsonObject {
                put("signedJwt", "")
                put("clientState", "")
                put("recognizeId", "")
                put("devicePublicSigningKey", "")
                put("clientError", e.message ?: "UNKNOWN_ERROR")
                put("clientErrorCode", "")
            }
            Result.failure(e)
        }
    }
}
