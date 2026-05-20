/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.push

/**
 * Describes the interaction model required by a PingOne MFA push notification.
 *
 * The value is determined by the server and delivered inside the push payload.
 * UI components should switch on this type to decide which approval flow to present.
 */
enum class PushType {
    /**
     * A standard authentication request. The user approves or denies with a single tap.
     */
    DEFAULT,

    /**
     * A silent test push sent by the server to verify the device's push registration.
     * No user action is required — the notification should be dismissed automatically
     * or shown with a "Dismiss" button only.
     */
    DRY,

    /**
     * A number-matching challenge. The server provides a set of numbers (or expects
     * free-form digit input when none are provided), and the user must confirm the one
     * that matches what they see on their other device. Use [PushNotification.getNumbersChallenge]
     * to retrieve the options; a null return means free-form entry is expected.
     */
    CHALLENGE
}
