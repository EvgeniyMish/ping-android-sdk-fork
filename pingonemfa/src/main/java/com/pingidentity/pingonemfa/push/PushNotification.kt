/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.push

import android.content.Context
import android.os.Parcelable
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.types.DenyReason
import com.pingidentity.pingonemfa.commons.PingOneMFAException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.Parcelize
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Wrapper model for a PingOne MFA push authentication request.
 *
 * Produced by [com.pingidentity.pingonemfa.commons.PingOneMFA.processRemoteNotification] from an
 * incoming FCM [com.google.firebase.messaging.RemoteMessage]. All user-facing operations
 * (approve, deny) are exposed as suspend functions that return [Result].
 *
 * @property id Wrapper-generated unique identifier for this push request.
 * @property title Notification title extracted from the FCM data payload, or null if absent.
 * @property message Notification body extracted from the FCM data payload, or null if absent.
 */
@Parcelize
data class PushNotification(
    val id: String = UUID.randomUUID().toString(),
    val notificationObject: NotificationObject,
    val title: String?,
    val message: String?
): Parcelable {

    /**
     * Approves this push authentication request.
     *
     * @param context Android context — use `applicationContext` when calling from a service.
     * @param authenticationMethod The authentication method string sent to PingOne (e.g. `"app"`).
     * @param numberChallenge The number selected by the user for a [PushType.CHALLENGE] push,
     *   or `null` for a [PushType.DEFAULT] push.
     */
    suspend fun approveNotification(
        context: Context,
        authenticationMethod: String,
        numberChallenge: Int? = null) : Result<Unit> = suspendCancellableCoroutine { cont ->
            try {
                notificationObject.approve(
                    context,
                    authenticationMethod,
                    numberChallenge
                ) { _, error ->
                    if (error == null) {
                        cont.resume(Result.success(Unit))
                    } else {
                        cont.resume(Result.failure(PingOneMFAException(error)))
                    }
                }
            } catch (e: Exception) {
                cont.resume(Result.failure(PingOneMFAException(e)))
            }
        }

    /**
     * Denies this push authentication request.
     *
     * @param context Android context — use `applicationContext` when calling from a service.
     */
    suspend fun denyNotification(context: Context) : Result<Unit> = suspendCancellableCoroutine { cont ->
            try {
                notificationObject.deny(
                    context,
                    DenyReason.NONE
                ) { error ->
                    if (error == null) {
                        cont.resume(Result.success(Unit))
                    } else {
                        cont.resume(Result.failure(PingOneMFAException(error)))
                    }
                }
            } catch (e: Exception) {
                cont.resume(Result.failure(PingOneMFAException(e)))
            }
        }

    /**
     * Returns `true` when the server has canceled this authentication request — for example,
     * because the user approved it on another device.
     *
     * When this returns `true`, the app should dismiss any visible approval UI immediately
     * without requiring the user to take action.
     */
    fun isCancelAuthentication(): Boolean {
        return notificationObject.isCancelAuth
    }

    /**
     * Returns the server-provided number options for a [PushType.CHALLENGE] push, or `null`
     * when free-form digit entry is expected instead.
     */
    fun getNumbersChallenge(): IntArray? {
        return notificationObject.numberMatchingOptions
    }

    /**
     * Returns the [PushType] that describes the interaction model required by this push.
     * UI components should switch on this to decide which approval flow to present.
     */
    fun getPushType () : PushType {
        return when {
            notificationObject.isTest -> PushType.DRY
            notificationObject.numberMatchingType != null -> PushType.CHALLENGE
            else -> PushType.DEFAULT
        }
    }
}
