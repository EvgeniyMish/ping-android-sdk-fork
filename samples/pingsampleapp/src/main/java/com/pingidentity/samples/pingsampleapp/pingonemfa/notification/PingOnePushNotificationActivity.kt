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
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.PingOnePushNotificationScreen
import com.pingidentity.samples.pingsampleapp.theme.AppTheme

/**
 * Activity displayed when a PingOne MFA push notification arrives while the app is in the foreground.
 *
 * Retrieves the [PushNotification] from [PushNotificationStore] using the ID passed as a String
 * extra — the notification object itself is never parceled through the Intent because the native
 * SDK's Parcelable implementation crashes when internal nullable fields are null.
 *
 * ## Cancellation
 * If the server cancels the authentication request while this activity is open (e.g. because the
 * user approved on another device), [PushNotificationService] broadcasts [ACTION_CANCEL_NOTIFICATION]
 * via [LocalBroadcastManager]. This activity registers a receiver in [onStart] / [onStop] that
 * matches the notification ID and calls [finish] immediately, so the user is never left on a stale
 * prompt.
 */
class PingOnePushNotificationActivity : ComponentActivity() {

    private var notificationId: String? = null

    /**
     * Receives [ACTION_CANCEL_NOTIFICATION] and closes the activity if the cancellation is for
     * the notification this activity is currently showing.
     */
    private val cancellationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val cancelledId = intent.getStringExtra(EXTRA_PINGONE_NOTIFICATION_ID) ?: return
            if (cancelledId == notificationId) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notification = getNotification()
        if (notification == null) {
            finish()
            return
        }

        notificationId = notification.id

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PingOnePushNotificationScreen(
                        notification = notification,
                        onFinish = {
                            PushNotificationStore.remove()
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Register while visible so we receive cancellations the moment they arrive.
        LocalBroadcastManager.getInstance(this).registerReceiver(
            cancellationReceiver,
            IntentFilter(ACTION_CANCEL_NOTIFICATION),
        )
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(cancellationReceiver)
    }

    private fun getNotification(): PushNotification? {
        val id = intent?.getStringExtra(EXTRA_PINGONE_NOTIFICATION_ID) ?: return null
        return PushNotificationStore.get(id)
    }

    companion object {
        const val EXTRA_PINGONE_NOTIFICATION_ID = "pingone_notification_id"

        /**
         * Broadcast action sent by [com.pingidentity.samples.pingsampleapp.authenticator.service.PushNotificationService]
         * when the server cancels an outstanding authentication request. The Intent carries
         * [EXTRA_PINGONE_NOTIFICATION_ID] so the activity can verify the cancellation is for the
         * notification it is currently showing.
         */
        const val ACTION_CANCEL_NOTIFICATION =
            "com.pingidentity.pingsampleapp.PINGONE_CANCEL_NOTIFICATION"
    }
}
