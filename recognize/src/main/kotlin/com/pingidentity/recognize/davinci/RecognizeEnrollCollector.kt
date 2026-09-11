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
import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.enroll.BiomEnrollConfig
import io.keyless.sdk.configurations.enroll.PresentationStyle
import io.keyless.sdk.core.actions.model.JwtSigningInfo
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.coroutineContext

/**
 * DaVinci [com.pingidentity.davinci.plugin.Collector] for the Recognize **enrollment** operation.
 *
 * Returned by [RecognizeCollector] when the server JSON contains `operationType = "ENROLL"`.
 * Chains [Recognize.setup] → [Recognize.enroll] and populates [payload] with a flat [JsonObject]
 * on both success and failure so the server always receives a response in `formData.{key}`.
 *
 * | Field / mobileSDKOptions key                    | BiomEnrollConfig property             |
 * |-------------------------------------------------|---------------------------------------|
 * | `transactionData`                               | `jwtSigningInfo.claimTransactionData` |
 * | `clientState`                                   | `clientState`                         |
 * | `generateClientState` (`"true"` → BACKUP)       | `generatingClientState`               |
 * | `mobileSDKOptions.operationInfoId`              | `operationInfo.operationId`           |
 * | `mobileSDKOptions.operationInfoPayload`         | `operationInfo.payload`               |
 * | `mobileSDKOptions.operationInfoExternalUserId`  | `operationInfo.externalUserId`        |
 * | `mobileSDKOptions.livenessConfiguration`        | `livenessConfiguration`               |
 * | `mobileSDKOptions.livenessEnvironmentAware`     | `livenessEnvironmentAware`            |
 * | `mobileSDKOptions.cameraDelaySeconds`           | `cameraDelaySeconds`                  |
 * | `mobileSDKOptions.shouldRetrieveEnrollmentFrame`| `shouldRetrieveEnrollmentFrame`       |
 * | `mobileSDKOptions.showSuccessFeedback`          | `showSuccessFeedback`                 |
 * | `mobileSDKOptions.showFailureFeedback`          | `showFailureFeedback`                 |
 * | `mobileSDKOptions.showInstructionsScreen`       | `showInstructionsScreen`              |
 * | `mobileSDKOptions.presentation`                 | `presentationStyle`                   |
 * | `mobileSDKOptions.numberOfEnrollmentCircuits`   | not mapped (no SDK field in 5.8.4)    |
 *
 * @see AbstractRecognizeCollector
 * @see RecognizeCollector
 */
class RecognizeEnrollCollector : AbstractRecognizeCollector() {

    /**
     * Performs the Recognize enrollment ceremony.
     *
     * Builds [SetupConfig] and [BiomEnrollConfig] from the fields parsed by
     * [AbstractRecognizeCollector.init], then chains [Recognize.setup] → [Recognize.enroll].
     * Populates [result] (and therefore [payload]) on both success and failure paths so that the
     * server receives the `clientError` value in the form data even when enrollment fails.
     *
     * @return [Result.success] wrapping the payload [JsonObject] on SDK success;
     *         [Result.failure] on setup or enrollment failure.
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
            val storedClientState = clientState
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

            val jwtSigningInfo = if (storedAudience.isNotBlank()) {
                JwtSigningInfo(claimTransactionData = storedTransactionData, audience = storedAudience)
            } else {
                JwtSigningInfo(claimTransactionData = storedTransactionData)
            }

            var biomEnrollConfig = BiomEnrollConfig(
                operationInfo = operationInfo,
                jwtSigningInfo = jwtSigningInfo,
                generatingClientState = storedGeneratingClientState,
                clientState = storedClientState.takeIf { it.isNotEmpty() },
            )
            opts["livenessConfiguration"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { LivenessSettings.LivenessConfiguration.valueOf(it) }.getOrNull() }
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(livenessConfiguration = it) }
            opts["livenessEnvironmentAware"]?.jsonPrimitive?.contentOrNull?.toBoolean()
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(livenessEnvironmentAware = it) }
            opts["cameraDelaySeconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(cameraDelaySeconds = it) }
            opts["shouldRetrieveEnrollmentFrame"]?.jsonPrimitive?.contentOrNull?.toBoolean()
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(shouldRetrieveEnrollmentFrame = it) }
            opts["showSuccessFeedback"]?.jsonPrimitive?.contentOrNull?.toBoolean()
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(showSuccessFeedback = it) }
            opts["showFailureFeedback"]?.jsonPrimitive?.contentOrNull?.toBoolean()
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(showFailureFeedback = it) }
            opts["showInstructionsScreen"]?.jsonPrimitive?.contentOrNull?.toBoolean()
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(showInstructionsScreen = it) }
            opts["presentation"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { PresentationStyle.valueOf(it) }.getOrNull() }
                ?.let { biomEnrollConfig = biomEnrollConfig.copy(presentationStyle = it) }

            Recognize.setup(setupConfig)
                .fold(
                    onSuccess = { Recognize.enroll(biomEnrollConfig) },
                    onFailure = { Result.failure(it) },
                )
                .fold(
                    onSuccess = { success ->
                        val payload = buildJsonObject {
                            put("signedJwt", success.signedJwt ?: "")
                            put("clientState", success.clientState ?: "")
                            put("recognizeId", success.keylessId)
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
                put("clientError", e.message ?: "UNKNOWN_ERROR")
                put("clientErrorCode", "")
            }
            Result.failure(e)
        }
    }
}
