/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.types.NotificationProvider
import com.pingidentity.pingonemfa.otp.OtpCodeInfo
import com.pingidentity.pingonemfa.push.PushApprovalService
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfa.util.AccountParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

object PingOneMFA {
    private val logger: Logger = Logger.logger
    @Volatile
    private var isInitialized: Boolean = false
    private val lock = Mutex()

    //SDK must be initialized once and cannot handle parallel configure calls
    suspend fun initialize(geo: Geo): Result<Unit> = lock.withLock {
        if (isInitialized) {
            return Result.success(Unit)
        }
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.configure(
                    ContextProvider.context,
                    geo.toPingOneGeo()
                ) { error ->
                    continuation.resume(
                        error?.let {
                            logger.e("PingOne initialization failed: ${it.userInfo}")
                            Result.failure(PingOneMFAException(it))
                        } ?: run {
                            isInitialized = true
                            Result.success(Unit)
                        }
                    )
                }
            }catch (e: Exception){
                logger.e("PingOne initialization failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }
    }

    /*
     * Registers push token with PingOne. Should be called each time the token is refreshed.
     */
    suspend fun setDeviceToken(pushToken: String) : Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.setDeviceToken(
                    ContextProvider.context,
                    pushToken,
                    NotificationProvider.FCM
                ) { errors ->
                    val result =
                        errors
                            ?.firstOrNull { it != null }
                            ?.let { err ->
                                logger.e("PingOne push token registration failed: ${err.userInfo}")
                                Result.failure(PingOneMFAException(err))
                            }
                            ?: Result.success(Unit)

                    continuation.resume(result)

                }
            } catch (e : Exception) {
                logger.e("PingOne push token registration failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }
    }

    /*
     * Starts pairing process with PingOne.
     */
    suspend fun pair(pairingKey: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.pair(
                ContextProvider.context,
                pairingKey
            ) { _, error ->
                val result = error?.let { err ->
                    logger.e("PingOne pairing failed: ${err.userInfo}")
                    Result.failure(PingOneMFAException(err))
                } ?: Result.success(Unit)
                continuation.resume(result)
            }
        } catch (e: Exception) {
            logger.e("PingOne pairing failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e)))
        }
    }

    /*
     * Retrieves all paired accounts from PingOne
     */
    suspend fun getDeviceInfo(): Result<List<PingOneMfaAccount>> =
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.getInfo(
                    ContextProvider.context
                ) { deviceInfo, errors ->
                    val result = deviceInfo?.let {
                        Result.success(AccountParser().parseAccounts(it.toString()))
                    } ?: run {
                        /*
                         * errors is a list that may be empty or contain nulls — take the first
                         * non-null entry; if none exists fall back to a generic exception so the
                         * coroutine is always resumed with a typed failure
                         */
                        val error = errors.firstOrNull { it != null }
                        logger.e("PingOne getDeviceInfo failed: ${error?.userInfo}")
                        Result.failure(error?.let {
                            PingOneMFAException(it)
                        } ?: PingOneMFAException(Exception("getDeviceInfo failed: no error details provided"))
                        )
                    }
                    continuation.resume(result)
                }
            }catch (e: Exception){
                logger.e("PingOne getDeviceInfo failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }

    /*
     * Retrieves OTP code from PingOne.
     */
    suspend fun getOneTimePasscode(): Result<OtpCodeInfo> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.getOneTimePassCode(ContextProvider.context) { otpInfo, error ->
                val result = otpInfo?.let {
                    Result.success(
                        OtpCodeInfo(
                            otpInfo.passcode,
                            maxOf(
                                0,
                                ((otpInfo.validUntil * 1000 - System.currentTimeMillis()) / 1000).toInt()
                            )
                        )
                    )
                } ?: run {
                    /*
                     * otpInfo is null but error may also be null if the SDK misbehaves;
                     * fall back to a generic exception so the coroutine is never left hanging
                     */
                    logger.e("PingOne getOneTimePasscode failed: ${error?.userInfo}")
                    Result.failure(error?.let {
                        PingOneMFAException(it)
                    } ?: PingOneMFAException(Exception("getOneTimePasscode failed: no error details provided"))
                    )
                }
                continuation.resume(result)
            }
        } catch (e: Exception) {
            logger.e("PingOne getOneTimePasscode failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e)))
        }
    }

    /*
     * Transforms received FCM Remote Message object from PingOne into PushNotification object
     */
    suspend fun processRemoteNotification(message: RemoteMessage): Result<PushNotification> =
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.processRemoteNotification(
                    ContextProvider.context,
                    message
                ) { notificationObject, error ->
                    val result = notificationObject?.let {
                        Result.success(
                            PushNotification(
                                notificationObject = notificationObject,
                                /*
                                 * Parse title and message from the "aps" field in the FCM data
                                 * payload, which contains the original FCM payload sent by PingOne.
                                 */
                                title = getTitleFromRemoteMessageData(message.data["aps"]),
                                message = getBodyFromRemoteMessageData(message.data["aps"])
                            )
                        )
                    } ?: run {
                        /*
                         * notificationObject is null but error may also be null if the SDK
                         * misbehaves; fall back to a generic exception so the coroutine is
                         * never left hanging
                         */
                        logger.e("PingOne processRemoteNotification failed: ${error?.userInfo}")
                        Result.failure(error?.let {
                            PingOneMFAException(it)
                        } ?: PingOneMFAException(Exception("processRemoteNotification failed: no error details provided"))
                        )
                    }
                    continuation.resume(result)
                }
            }catch (e: Exception){
                logger.e("PingOne processRemoteNotification failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }

    /*
     * Retrieves mobile payload from PingOne.
     */
    suspend fun generateMobilePayload(): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.generateMobilePayload(ContextProvider.context) { payload, error ->
                val result = payload?.let {
                    Result.success(payload)
                } ?: run {
                    /*
                     * payload is null but error may also be null if the SDK misbehaves;
                     * fall back to a generic exception so the coroutine is never left hanging
                     */
                    logger.e("PingOne generateMobilePayload failed: ${error?.userInfo}")
                    Result.failure(error?.let {
                        PingOneMFAException(it)
                    } ?: PingOneMFAException(Exception("generateMobilePayload failed: no error details provided"))
                    )
                }
                continuation.resume(result)
            }
        }catch (e: Exception){
            logger.e("PingOne generateMobilePayload failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e)))
        }
    }

    /*
     * Approves MFA push notification. Should be called from notification action if application is in the background.
     */
    fun approvePushNotificationFromBanner(notification: PushNotification){
        val appContext = ContextProvider.context
        val intent = Intent(appContext, PushApprovalService::class.java).apply {
            putExtra("notification", notification)
            putExtra("auth_method", "banner")
            putExtra("user_action", "approve")
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    /*
     * Denies MFA push notification. Should be called from notification action if application is in the background.
     */
    fun denyPushNotificationFromBanner(notification: PushNotification){
        val appContext = ContextProvider.context
        val intent = Intent(appContext, PushApprovalService::class.java).apply {
            putExtra("notification", notification)
            putExtra("auth_method", "banner")
            putExtra("user_action", "deny")
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun getTitleFromRemoteMessageData(data: String?): String? =
        data?.let {
            Json.parseToJsonElement(it)
                .jsonObject["alert"]
                ?.jsonObject
                ?.get("title")
                ?.jsonPrimitive
                ?.contentOrNull
        }

    private fun getBodyFromRemoteMessageData(data: String?): String? =
        data?.let {
            Json.parseToJsonElement(it)
                .jsonObject["alert"]
                ?.jsonObject
                ?.get("body")
                ?.jsonPrimitive
                ?.contentOrNull
        }

}
