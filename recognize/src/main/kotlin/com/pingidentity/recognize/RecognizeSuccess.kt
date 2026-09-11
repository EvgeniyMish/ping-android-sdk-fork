/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import android.graphics.Bitmap

/**
 * The result of a PingOne Recognize [enroll][com.pingidentity.recognize.journey.PingOneRecognizeEnrollCallback.enroll]
 * or [authenticate][com.pingidentity.recognize.journey.PingOneRecognizeAuthenticateCallback.authenticate] operation.
 *
 * @property selfie The captured selfie frame, or `null` if `retrieveSelfie` was `false` or the SDK
 *   did not return a frame.
 * @property signedJwt The signed JWT produced by the Keyless SDK, forwarded to the server.
 * @property clientState Opaque client state returned by the SDK.
 * @property recognizeId The current Keyless SDK user identifier. Populated after enroll and
 *   authenticate operations.
 * @property devicePublicSigningKey The freshly retrieved device public signing key.
 */
data class RecognizeSuccess(
    val selfie: Bitmap?,
    val signedJwt: String?,
    val clientState: String?,
    val recognizeId: String,
    val devicePublicSigningKey: String,
)
