/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.samples.pingsampleapp.pingonemfa.notification.PushNotificationStore
import com.pingidentity.samples.pingsampleapp.authenticator.data.DiagnosticLogger

/**
 * BroadcastReceiver to handle action button taps on PingOne MFA system notifications.
 *
 * Receives [ACTION_APPROVE] and [ACTION_DENY] intents fired from the notification banner,
 * then delegates to [PingOneMFA.approvePushNotificationFromBanner] /
 * [PingOneMFA.denyPushNotificationFromBanner], which route through
 * [com.pingidentity.pingonemfa.push.PushApprovalService] so the network call is allowed
 * even when the app is in the background.
 *
 * The [PushNotification] is retrieved from [PushNotificationStore] by ID — it is never parceled
 * through the Intent because the native SDK's Parcelable implementation crashes when internal
 * nullable fields are null.
 */
class PingOneNotificationActionReceiver : BroadcastReceiver() {

    private val diagnosticLogger = DiagnosticLogger

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getStringExtra(EXTRA_PINGONE_NOTIFICATION_ID) ?: return
        val notification = PushNotificationStore.get(notificationId) ?: return

        // Dismiss the banner immediately so the user gets visual feedback
        NotificationManagerCompat.from(context).cancel(notification.id.hashCode())

        when (intent.action) {
            ACTION_APPROVE -> {
                diagnosticLogger.d("PingOne approve tapped for notification: ${notification.id}")
                PushNotificationStore.remove()
                PingOneMFA.approvePushNotificationFromBanner(notification)
            }
            ACTION_DENY -> {
                diagnosticLogger.d("PingOne deny tapped for notification: ${notification.id}")
                PushNotificationStore.remove()
                PingOneMFA.denyPushNotificationFromBanner(notification)
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "com.pingidentity.pingsampleapp.PINGONE_ACTION_APPROVE"
        const val ACTION_DENY = "com.pingidentity.pingsampleapp.PINGONE_ACTION_DENY"
        const val EXTRA_PINGONE_NOTIFICATION_ID = "pingone_notification_id"
    }
}
