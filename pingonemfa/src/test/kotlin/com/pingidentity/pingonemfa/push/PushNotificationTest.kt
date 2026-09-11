/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.push

import android.content.Context
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKError
import com.pingidentity.pingidsdkv2.types.DenyReason
import com.pingidentity.pingonemfa.commons.PingOneMFAException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushNotificationTest {
    private val context = mockk<Context>(relaxed = true)
    private val notificationObject = mockk<NotificationObject>(relaxed = true)

    @Test
    fun `approveNotification returns success when callback error is null`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneMobileConfirmationCallback>(3)
            callback.onComplete(null, null)
        }

        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )

        val result = push.approveNotification(context, "banner")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `approveNotification returns error when callback error is not null`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneMobileConfirmationCallback>(3)
            callback.onComplete(null, PingOneSDKError(1003, "mock"))
        }
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        val result = push.approveNotification(context, "banner")
        assertTrue(result.isFailure)
        // Known SDK failure: message contains the formatted SDK code
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull() as PingOneMFAException).internalErrorsList?.get(0)?.code == 1003 }
    }
    @Test
    fun `approveNotification returns failure when exception is thrown`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } throws RuntimeException("Simulated Network Error")

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        val result = push.approveNotification(context, "banner")

        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is Exception }
        assertTrue { result.exceptionOrNull()?.message == "Simulated Network Error" }
    }

    @Test
    fun `denyNotification returns success when callback error is null`() = runTest {
        every {
            notificationObject.deny(any(), DenyReason.NONE, any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(2)
            callback.onComplete(null)
        }
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        val result = push.denyNotification(context)
        assertTrue(result.isSuccess)

    }
    @Test
    fun `denyNotification returns error when callback error is not null`() = runTest {
        every {
            notificationObject.deny(any(), DenyReason.NONE, any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(2)
            callback.onComplete(PingOneSDKError(1003, "mock"))
        }
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        val result = push.denyNotification(context)
        assertTrue(result.isFailure)
        // Known SDK failure: message contains the formatted SDK code
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull() as PingOneMFAException).internalErrorsList?.get(0)?.code == 1003 }
    }

    @Test
    fun `denyNotification returns failure when exception is thrown`() = runTest {
        every {
            notificationObject.deny(any(), DenyReason.NONE, any())
        } throws RuntimeException("Simulated Network Error")

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )
        val result = push.denyNotification(context)
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is Exception }
        assertTrue { result.exceptionOrNull()?.message == "Simulated Network Error" }
    }

    @Test
    fun `getNumbersChallenge returns correct values when notificationObject numberMatchingType is not null`(){
        every { notificationObject.numberMatchingType } returns "mock"
        every { notificationObject.numberMatchingOptions } returns intArrayOf(1,2,3)
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
            )
        assertTrue(push.getNumbersChallenge()?.contentEquals(intArrayOf(1,2,3)) ?: false)
    }

    @Test
    fun `getNumbersChallenge returns empty array when notificationObject numberMatchingType is null`(){
        every { notificationObject.numberMatchingType } returns null
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
            )
        assertTrue((push.notificationObject.numberMatchingOptions == null) || push.notificationObject.numberMatchingOptions!!.isEmpty())
    }

    @Test
    fun `getPushType returns DRY when isTest`() {
        every { notificationObject.isTest } returns true

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertEquals(PushType.DRY, push.getPushType())
    }
    @Test
    fun `getPushType returns CHALLENGE when numberMatchingType present`() {
        every { notificationObject.isTest } returns false
        every { notificationObject.numberMatchingType } returns "type"

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertEquals(PushType.CHALLENGE, push.getPushType())
    }
    @Test
    fun `getPushType returns DEFAULT otherwise`() {
        every { notificationObject.isTest } returns false
        every { notificationObject.numberMatchingType } returns null

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertEquals(PushType.DEFAULT, push.getPushType())
    }

    @Test
    fun `isCancelAuthentication returns true when notificationObject isCancelAuth is true`() {
        every { notificationObject.isCancelAuth } returns true

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertTrue(push.isCancelAuthentication())
    }

    @Test
    fun `isCancelAuthentication returns false when notificationObject isCancelAuth is false`() {
        every { notificationObject.isCancelAuth } returns false

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertTrue(!push.isCancelAuthentication())
    }
}
