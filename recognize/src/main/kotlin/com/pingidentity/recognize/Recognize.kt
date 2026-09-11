/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import io.keyless.sdk.Keyless
import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.auth.BiomAuthConfig
import io.keyless.sdk.configurations.enroll.BiomEnrollConfig
import io.keyless.sdk.errorshandling.AuthenticationSuccess
import io.keyless.sdk.errorshandling.EnrollmentSuccess
import io.keyless.sdk.errorshandling.KeylessSdkError
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Coroutine wrappers around the Keyless SDK that bridge its callback-based API into suspending functions.
 */
object Recognize {
    /**
     * Verifies that the current user and device are active in the Keyless SDK.
     *
     * @return [Result.success] with [Unit] if the check passes, or [Result.failure] wrapping a
     * [RecognizeException] if the SDK reports an error.
     */
    internal suspend fun validateUserAndDeviceActive(): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            Keyless.validateUserAndDeviceActive { result ->
                when (result) {
                    is Keyless.KeylessResult.Success -> {
                        cont.resume(Result.success(Unit)) { cause, _, _ ->
                            cont.cancel(cause)
                        }
                    }
                    is Keyless.KeylessResult.Failure -> {
                        cont.resume(Result.failure(RecognizeException.from(result.error))) { cause, _, _ ->
                            cont.cancel(cause)
                        }
                    }
                }
            }
        }

    /**
     * Configures the Keyless SDK with the given [setupConfig].
     *
     * @param setupConfig SDK configuration (API key, hosts, circuit count).
     * @return [Result.success] on completion, or [Result.failure] wrapping a [RecognizeException].
     */
    internal suspend fun setup(
        setupConfig: SetupConfig
    ) = suspendCancellableCoroutine { continuation ->
        Keyless.configure(setupConfig) { result ->
            when (result) {
                is Keyless.KeylessResult.Success -> {
                    continuation.resume(Result.success(result.value)) { cause, _, _ ->
                        // The Keyless SDK has no cancellation API, so the underlying call
                        // completes regardless. We cancel the continuation so the coroutine
                        // reports cancellation to its caller.
                        continuation.cancel(cause)
                    }
                }

                is Keyless.KeylessResult.Failure -> {
                    continuation.resume(Result.failure(RecognizeException.from(result.error))) { cause, _, _ ->
                        continuation.cancel(cause)
                    }
                }
            }
        }
    }

    /**
     * Retrieves the device public signing key from the configured Keyless SDK.
     *
     * The key is read synchronously and is not cached so callers can retrieve the
     * current key before submitting each completed operation.
     *
     * @return [Result.success] with the public signing key, or [Result.failure]
     * wrapping a [RecognizeException] when retrieval fails.
     */
    internal fun getDevicePublicSigningKey(): Result<String> = try {
        when (val result = Keyless.getDevicePublicSigningKey()) {
            is Keyless.KeylessResult.Success -> Result.success(result.value)
            is Keyless.KeylessResult.Failure -> Result.failure(RecognizeException.from(result.error))
        }
    } catch (error: Throwable) {
        Result.failure(error.asRecognizeException())
    }

    /**
     * Retrieves the current user identifier from the configured Keyless SDK.
     *
     * @return [Result.success] with the user identifier, or [Result.failure]
     * wrapping a [RecognizeException] when retrieval fails.
     */
    internal fun getUserId(): Result<String> = try {
        when (val result = Keyless.getUserId()) {
            is Keyless.KeylessResult.Success -> Result.success(result.value)
            is Keyless.KeylessResult.Failure -> Result.failure(RecognizeException.from(result.error))
        }
    } catch (error: Throwable) {
        Result.failure(error.asRecognizeException())
    }

    /**
     * Runs a biometric enrollment flow using the Keyless SDK.
     *
     * @param biomEnrollConfig Enrollment parameters (liveness, presentation style, selfie capture, etc.).
     * @return [Result.success] with [EnrollmentSuccess] on completion, or [Result.failure] wrapping
     * a [RecognizeException].
     */
    suspend fun enroll(
        biomEnrollConfig: BiomEnrollConfig
    ): Result<EnrollmentSuccess> = suspendCancellableCoroutine { cont ->
        Keyless.enroll(configuration = biomEnrollConfig) { result ->
            when(result) {
                is Keyless.KeylessResult.Success -> {
                    cont.resume(Result.success(result.value)) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }
                is Keyless.KeylessResult.Failure -> {
                    cont.resume(Result.failure(RecognizeException.from(result.error as KeylessSdkError))) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }
            }
        }
    }

    /**
     * Runs a biometric authentication flow using the Keyless SDK.
     *
     * @param config Authentication parameters.
     * @return [Result.success] with [AuthenticationSuccess] on completion, or [Result.failure]
     * wrapping a [RecognizeException].
     */
    suspend fun authenticate(
        config: BiomAuthConfig
    ): Result<AuthenticationSuccess> = suspendCancellableCoroutine { cont ->
        Keyless.authenticate(configuration = config) { result ->
            when(result) {
                is Keyless.KeylessResult.Success -> {
                    cont.resume(Result.success(result.value)) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }

                is Keyless.KeylessResult.Failure -> {
                    cont.resume(Result.failure(RecognizeException.from(result.error as KeylessSdkError))) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }
            }
        }
    }
}
