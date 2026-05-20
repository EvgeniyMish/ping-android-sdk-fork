package com.pingidentity.samples.pingsampleapp.pingonemfa.notification

import com.pingidentity.pingonemfa.push.PushNotification
import java.util.concurrent.atomic.AtomicReference

/**
 * In-process store for the single active [PushNotification].
 *
 * Only one PingOne MFA push authentication request can be outstanding at a time — the server
 * does not queue concurrent requests for the same device. This store therefore holds at most
 * one notification. Calling [put] while a notification is already present replaces it; the
 * previous notification is discarded.
 *
 * ## Usage
 * 1. Call [put] to store the notification before firing any Intent.
 * 2. Pass only [PushNotification.id] as a plain String extra — never parcel the object itself.
 * 3. Call [get] on the receiving end to retrieve the notification.
 * 4. Call [remove] once the notification has been acted on to free the reference.
 */
object PushNotificationStore {

    private val current = AtomicReference<PushNotification?>(null)

    /**
     * Stores [notification] as the current active push.
     * Any previously stored notification is replaced.
     */
    fun put(notification: PushNotification) {
        current.set(notification)
    }

    /**
     * Returns the current notification if its [PushNotification.id] matches [id],
     * or null if no notification is stored or the ID does not match.
     */
    fun get(id: String): PushNotification? =
        current.get()?.takeIf { it.id == id }

    /**
     * Atomically clears the store and returns the removed notification's ID,
     * or null if the store was already empty. Should be called after the notification
     * has been approved, denied, canceled, or dismissed.
     */
    fun remove(): String? {
        return current.getAndSet(null)?.id
    }
}
