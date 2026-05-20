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

/*
 * Simple model for a push notification. Implements Parcelable so it can be passed between components.
 */
@Parcelize
data class PushNotification(
    val id: String = UUID.randomUUID().toString(),
    val notificationObject: NotificationObject,
    val title: String?,
    val message: String?,
    val sentAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null
): Parcelable {

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

    fun isCancelAuthentication(): Boolean {
        return notificationObject.isCancelAuth
    }

    fun getNumbersChallenge(): IntArray? {
        return notificationObject.numberMatchingOptions
    }

    fun getPushType () : PushType {
        return when {
            notificationObject.isTest -> PushType.DRY
            notificationObject.numberMatchingType != null -> PushType.CHALLENGE
            else -> PushType.DEFAULT
        }
    }
}
