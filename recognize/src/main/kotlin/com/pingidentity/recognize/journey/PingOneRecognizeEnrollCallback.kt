/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.recognize.Recognize
import com.pingidentity.recognize.asRecognizeException
import com.pingidentity.recognize.RecognizeSuccess

/**
 * Journey callback that handles PingOne Recognize **enrollment** operations.
 *
 * This callback is returned by [RecognizeCallback] when the server output contains
 * `operationType = "ENROLL"`. All output fields are parsed by [AbstractRecognizeCallback];
 * this class only adds the [enroll] operation.
 *
 * On success, the signed JWT, client state, and recognize ID are submitted to the Journey
 * via the existing five input fields: `IDToken1signedJwt`, `IDToken1clientState`,
 * `IDToken1recognizeId`, `IDToken1clientError`, `IDToken1clientErrorCode`. The freshly retrieved
 * device public signing key is exposed through [RecognizeSuccess], but Journey enrollment has no
 * corresponding input slot. Key retrieval failure is returned as a failed operation.
 *
 * @see RecognizeCallback
 * @see PingOneRecognizeAuthenticateCallback
 */
class PingOneRecognizeEnrollCallback : AbstractRecognizeCallback() {

    /**
     * Performs the PingOne Recognize enrollment ceremony.
     *
     * | Callback field / mobileSDKOptions key           | EnrollConfig property              |
     * |-------------------------------------------------|------------------------------------|
     * | `transactionData`                               | `jwtSigningInfo.claimTransactionData` |
     * | `clientState`                                   | `clientState`                      |
     * | `generateClientState` (`"true"` → BACKUP)       | `generatingClientState`            |
     * | `mobileSDKOptions.operationInfoId`              | `operationInfo.operationId`        |
     * | `mobileSDKOptions.operationInfoPayload`         | `operationInfo.payload`            |
     * | `mobileSDKOptions.operationInfoExternalUserId`  | `operationInfo.externalUserId`     |
     * | `mobileSDKOptions.livenessConfiguration`        | `livenessConfiguration`            |
     * | `mobileSDKOptions.livenessEnvironmentAware`     | `livenessEnvironmentAware`         |
     * | `mobileSDKOptions.cameraDelaySeconds`           | `cameraDelaySeconds`               |
     * | `mobileSDKOptions.showSuccessFeedback`          | `showSuccessFeedback`              |
     * | `mobileSDKOptions.showFailureFeedback`          | `showFailureFeedback`              |
     * | `mobileSDKOptions.showInstructionsScreen`       | `showInstructionsScreen`           |
     * | `mobileSDKOptions.presentation`                 | `presentationStyle`                |
     * | `mobileSDKOptions.numberOfEnrollmentCircuits`   | `setupConfig.numberOfEnrollmentCircuits` |
     *
     * @return [Result] containing [RecognizeSuccess] on success, or a [Throwable] on failure.
     */
    suspend fun enroll(config: RecognizeEnrollConfig.() -> Unit = {}): Result<RecognizeSuccess> {
        val resolvedConfig = RecognizeEnrollConfig().apply(config)
        val result = Recognize.setup(buildSetupConfig()).fold(
            onSuccess = {
                Recognize.enroll(buildEnrollConfig(retrieveSelfie = resolvedConfig.retrieveSelfie)).fold(
                    onSuccess = { success ->
                        Recognize.getDevicePublicSigningKey().map { devicePublicSigningKey ->
                            RecognizeSuccess(
                                selfie = success.enrollmentFrame,
                                signedJwt = success.signedJwt,
                                clientState = success.clientState,
                                recognizeId = success.keylessId,
                                devicePublicSigningKey = devicePublicSigningKey,
                            )
                        }
                    },
                    onFailure = { Result.failure(it) },
                )
            },
            onFailure = { Result.failure(it) },
        )

        result.onSuccess { success ->
            submitResult(
                signedJwt = success.signedJwt ?: "",
                clientState = success.clientState ?: "",
                recognizeId = success.recognizeId,
                clientError = "",
                clientErrorCode = "",
            )
        }.onFailure { error ->
            val ex = error.asRecognizeException()
            submitResult(
                signedJwt = "",
                clientState = "",
                recognizeId = "",
                clientError = ex.message,
                clientErrorCode = ex.code.toString(),
            )
        }
        return result
    }

    private fun submitResult(
        signedJwt: String,
        clientState: String,
        recognizeId: String,
        clientError: String,
        clientErrorCode: String,
    ) {
        input(
            signedJwt,
            clientState,
            recognizeId,
            clientError,
            clientErrorCode
        )
    }
}
