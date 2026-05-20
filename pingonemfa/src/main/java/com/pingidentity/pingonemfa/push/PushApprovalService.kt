/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.push

//noinspection SuspiciousImport
import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pingidentity.logger.Logger
import com.pingidentity.pingidsdkv2.types.DenyReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Service for handling push notifications actions. This service exists to solve ONE specific Android restriction:
 * Android does NOT allow network calls in the background (from notification actions in particular).
 */
internal class PushApprovalService(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Service(){

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val logger: Logger = Logger.logger

    override fun onBind(p0: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        val notificationObject =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra("notification", PushNotification::class.java)
            } else {
                intent?.getParcelableExtra("notification")
            }
        val authMethod = intent?.getStringExtra("auth_method") ?: ""
        val userAction = intent?.getStringExtra("user_action") ?: ""
        if (notificationObject == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                if (userAction.equals("approve", ignoreCase = true)) {
                    approveNotificationWithAppInBackground(notificationObject, authMethod)
                }else{
                    denyNotificationWithAppInBackground(notificationObject)
                }
            } catch (e: Exception) {
                logger.e("MfaApprovalService: push approval failed", e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "mfa_approval_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "MFA Approval",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Approving login…")
            .setContentText("Contacting server")
            .setSmallIcon(R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    private suspend fun approveNotificationWithAppInBackground(
        notification: PushNotification,
        auth: String
    ) = suspendCancellableCoroutine { cont ->
        try {
            notification.notificationObject.approve(
                this,
                auth,
                null
            ) { _, error ->
                if (!cont.isActive) return@approve
                if (error == null) cont.resume(Unit)
                else cont.resumeWithException(Exception(error.message ?: "Approval failed"))
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
    private suspend fun denyNotificationWithAppInBackground(
        notification: PushNotification
    ) = suspendCancellableCoroutine { cont ->
        try {
            notification.notificationObject.deny(
                this,
                DenyReason.NONE
            ){ error ->
                if (!cont.isActive) return@deny
                if (error == null) cont.resume(Unit)
                else cont.resumeWithException(Exception(error.message ?: "Deny action failed"))
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 7001
    }

    /*
     * Cancel the coroutine scope when Android destroys the service.
     *
     * Without this, the SupervisorJob stays alive after onDestroy() is called:
     * any in-flight approve/deny network call would keep running against a dead
     * service instance, holding a reference to it and leaking memory until the
     * coroutine eventually completes or is garbage-collected. Cancelling here
     * ensures all child coroutines are interrupted immediately and the scope
     * cannot launch new work after the service is gone.
     */
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}