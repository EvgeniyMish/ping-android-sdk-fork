/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfa.push.PushType
import com.pingidentity.samples.pingsampleapp.R

/**
 * Helper class for building and posting system banners for PingOne MFA push notifications.
 *
 * For [com.pingidentity.pingonemfa.push.PushType.DEFAULT], the banner includes Allow and Deny action buttons so the user
 * can respond without opening the app. Action taps are routed to [PingOneNotificationActionReceiver].
 *
 * For all other types (CHALLENGE, DRY) no action buttons are added — tapping
 * the banner opens the app via its launcher intent instead.
 *
 * [PushNotification] is stored in [PushNotificationStore] and only its ID is carried through Intents.
 */
private const val CHANNEL_ID = "com.pingidentity.pingsampleapp.PINGONE_PUSH"

class PingOneNotificationHelper(private val context: Context) {

    /**
     * Creates the dedicated PingOne MFA notification channel.
     * Safe to call multiple times — a no-op if the channel already exists.
     */
    fun createNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "PingOne MFA",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts a system banner for [notification].
     *
     * - Uses [PushNotification.title] and [PushNotification.message] as the banner text,
     *   falling back to generic string resources when either is null.
     * - Adds Allow / Deny action buttons only for [PushType.DEFAULT].
     * - The content intent always opens the app launcher so tapping the banner body works
     *   regardless of push type.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showPushNotification(notification: PushNotification) {
        val notificationId = notification.id.hashCode()

        // Store so banner action receivers and the full-screen activity can retrieve it by ID.
        PushNotificationStore.put(notification)

        val title = notification.title ?: context.getString(R.string.system_notification_title)
        val message = notification.message ?: context.getString(R.string.system_notification_content)

        val openAppPendingIntent = buildOpenAppPendingIntent(notification.id, notificationId)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)

        if (notification.getPushType() == PushType.DEFAULT) {
            builder
                .addAction(buildDenyAction(notification.id, notificationId))
                .addAction(buildApproveAction(notification.id, notificationId))
        }

        with(NotificationManagerCompat.from(context)) {
            if (areNotificationsEnabled()) {
                notify(notificationId, builder.build())
            }
        }
    }

    private fun buildOpenAppPendingIntent(notificationId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PingOnePushNotificationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PingOnePushNotificationActivity.EXTRA_PINGONE_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDenyAction(
        notificationId: String,
        requestCode: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, PingOneNotificationActionReceiver::class.java).apply {
            action = PingOneNotificationActionReceiver.ACTION_DENY
            putExtra(PingOneNotificationActionReceiver.EXTRA_PINGONE_NOTIFICATION_ID, notificationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(
            R.drawable.ic_close,
            context.getString(R.string.system_notification_deny),
            pendingIntent
        )
    }

    private fun buildApproveAction(
        notificationId: String,
        requestCode: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, PingOneNotificationActionReceiver::class.java).apply {
            action = PingOneNotificationActionReceiver.ACTION_APPROVE
            putExtra(PingOneNotificationActionReceiver.EXTRA_PINGONE_NOTIFICATION_ID, notificationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(
            R.drawable.ic_check,
            context.getString(R.string.system_notification_approve),
            pendingIntent
        )
    }
}
