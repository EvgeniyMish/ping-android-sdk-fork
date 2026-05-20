package com.pingidentity.samples.pingsampleapp.authenticator.service

import android.app.ActivityManager
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.samples.pingsampleapp.PingSampleApplication
import com.pingidentity.samples.pingsampleapp.authenticator.data.DiagnosticLogger
import com.pingidentity.samples.pingsampleapp.authenticator.notification.NotificationActionReceiver
import com.pingidentity.samples.pingsampleapp.authenticator.notification.NotificationHelper
import com.pingidentity.samples.pingsampleapp.authenticator.notification.PushNotificationActivity
import com.pingidentity.samples.pingsampleapp.pingonemfa.notification.PingOneNotificationHelper
import com.pingidentity.mfa.push.PushClient
import com.pingidentity.mfa.push.PushNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pingidentity.pingonemfa.push.PushNotification as PingOnePushNotification
import com.pingidentity.samples.pingsampleapp.pingonemfa.notification.PushNotificationStore
import com.pingidentity.samples.pingsampleapp.pingonemfa.notification.PingOnePushNotificationActivity

/**
 * Service to handle incoming Firebase Cloud Messaging notifications.
 */
class PushNotificationService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pushClient: PushClient? = null

    private val diagnosticLogger = DiagnosticLogger

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var pingOneNotificationHelper: PingOneNotificationHelper


    override fun onCreate() {
        super.onCreate()
        diagnosticLogger.d("PushNotificationService instance created")

        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannels()

        pingOneNotificationHelper = PingOneNotificationHelper(this)
        pingOneNotificationHelper.createNotificationChannel()

        scope.launch {
            pushClient = PingSampleApplication.getPushClient()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        diagnosticLogger.d("PushNotificationService instance destroyed")
    }

    /**
     * Checks if the application is currently in foreground.
     *
     * @return True if the app is in foreground, false otherwise
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName

        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    /**
     * Called when a new token is generated.
     */
    override fun onNewToken(token: String) {
        diagnosticLogger.d("New FCM token: ${token.take(8)}...${token.takeLast(4)}")
        scope.launch {
            // Update the device token in the PushClient
            pushClient?.setDeviceToken(token)
            // Update the device token in PingOneMFA
            PingOneMFA.setDeviceToken(token)
        }
    }

    /**
     * Called when a message is received.
     */
    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        diagnosticLogger.d("Message received from: ${remoteMessage.from}")

        // Check if the message received from PingOne MFA Push by looking for expected key
        if (remoteMessage.data.containsKey("PingOne")) {
            diagnosticLogger.d("Received PingOne MFA Push message")
            scope.launch {
                // Process the notification using PingOneMFA's processRemoteNotification method, which handles decryption and validation
                PingOneMFA.processRemoteNotification(remoteMessage)
                    .onSuccess {
                        diagnosticLogger.d("Successfully collected PingOne MFA push notification")
                        // Handle the notification (this will display a system notification or full-screen notification based on app state)
                        handlePingOneNotification(it)
                    }
                    .onFailure { error ->
                        diagnosticLogger.e("Failed to collect PingOne MFA push notification: ${error.message}")
                    }
            }
        }
        // Handle the message data payload
        if (remoteMessage.data.isNotEmpty()) {
            diagnosticLogger.d("Message data payload: ${remoteMessage.data}")

            scope.launch {
                try {
                    // Process the notification using PushClient directly
                    val result = pushClient?.processNotification(remoteMessage.data as Map<String, Any>)?.getOrNull()
                    result?.let { notification ->
                        handleNotification(notification)
                    }
                } catch (e: Exception) {
                    diagnosticLogger.e("Error processing notification: ${e.message}")
                }
            }
        }
    }

    /**
     * Displays a system notification for the push authentication request.
     */
    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    private fun displaySystemNotification(notification: PushNotification) {
        // Find the associated credential to get issuer and account name
        scope.launch(Dispatchers.Main) {
            try {
                val credentials = pushClient?.getCredentials()?.getOrElse { emptyList() } ?: emptyList()
                val credential = credentials.find { it.id == notification.credentialId }

                // Display the notification with credential info if available
                notificationHelper.showPushAuthenticationNotification(
                    notification = notification,
                    issuer = credential?.issuer,
                    accountName = credential?.accountName
                )
            } catch (e: Exception) {
                diagnosticLogger.e("Error displaying notification: ${e.message}")
                // Fall back to showing a notification without credential details
                notificationHelper.showPushAuthenticationNotification(
                    notification = notification,
                    issuer = null,
                    accountName = null
                )
            }
        }
    }

    /**
     * Shows a full-screen notification when the app is in the foreground.
     * This launches the PushNotificationActivity directly.
     *
     * @param notification The push notification to display
     */
    private fun showFullScreenNotification(notification: PushNotification) {
        scope.launch(Dispatchers.Main) {
            try {
                diagnosticLogger.d("Showing full screen notification: ${notification.id}")

                // Launch the PushNotificationActivity with the notification ID
                val intent = Intent(applicationContext, PushNotificationActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(NotificationActionReceiver.Companion.EXTRA_NOTIFICATION_ID, notification.id)
                }

                startActivity(intent)
            } catch (e: Exception) {
                diagnosticLogger.e("Error showing full-screen notification: ${e.message}")
            }
        }
    }

    /**
     * Handle a notification that's already been processed.
     * This displays system notifications and launches full-screen notifications when appropriate.
     */
    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    fun handleNotification(notification: PushNotification) {
        diagnosticLogger.d("Handling notification: ${notification.id}")
        
        // If app is in foreground, also display the notification full screen immediately
        if (isAppInForeground()) {
            diagnosticLogger.d("App is in foreground, launching notification activity")
            showFullScreenNotification(notification)
        } else {
            diagnosticLogger.d("App is in background, displaying system notification")
            displaySystemNotification(notification)
        }
    }

    /**
     * Handle a PingOne notification that's already been processed.
     *
     * If [PingOnePushNotification.isCancelAuthentication] is true, the server has revoked the
     * outstanding request (e.g. because it was handled on another device). In that case:
     * - Remove the notification from [PushNotificationStore] so it can no longer be acted on.
     * - Cancel any system banner that may still be visible.
     * - If the app is in the foreground and the activity is open, broadcast a cancellation
     *   signal so [PingOnePushNotificationActivity] can dismiss itself immediately.
     *
     * Otherwise, display the appropriate UI based on app state.
     */
    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    fun handlePingOneNotification(notification: PingOnePushNotification) {
        diagnosticLogger.d("Handling PingOne notification: ${notification.id}")

        if (notification.isCancelAuthentication()) {
            diagnosticLogger.d("PingOne notification is a cancellation — dismissing: ${notification.id}")

            val currentNotificationId = PushNotificationStore.remove()
            if (currentNotificationId == null){
                // notification was already dismissed, no -op
                diagnosticLogger.d("No active PingOne notification in store, nothing to cancel")
                return
            }
            // Dismiss the system banner if it was shown while the app was in the background.
            NotificationManagerCompat.from(this).cancel(currentNotificationId.hashCode())


            // If the activity is currently open for this notification, tell it to close.
            val cancelIntent = Intent(PingOnePushNotificationActivity.ACTION_CANCEL_NOTIFICATION).apply {
                putExtra(PingOnePushNotificationActivity.EXTRA_PINGONE_NOTIFICATION_ID, currentNotificationId)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(cancelIntent)
            return
        }

        if (isAppInForeground()) {
            diagnosticLogger.d("App is in foreground, launching notification activity")
            showPingOneFullScreenNotification(notification)
        } else {
            diagnosticLogger.d("App is in background, displaying system notification")
            displayPingOneSystemNotification(notification)
        }
    }

    /**
     * Shows a system banner for a PingOne push authentication request.
     * Delegates to [PingOneNotificationHelper].
     */
    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    private fun displayPingOneSystemNotification(notification: PingOnePushNotification) {
        pingOneNotificationHelper.showPushNotification(notification)
    }

    /**
     * Launches [PingOnePushNotificationActivity] to handle the push while the app is in the foreground.
     */
    private fun showPingOneFullScreenNotification(notification: PingOnePushNotification) {
        diagnosticLogger.d("Launching PingOnePushNotificationActivity for: ${notification.id}")

        // Store in memory and pass only the ID
        PushNotificationStore.put(notification)

        val intent = Intent(this, PingOnePushNotificationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PingOnePushNotificationActivity.EXTRA_PINGONE_NOTIFICATION_ID, notification.id)
        }
        startActivity(intent)
    }
}