package com.pingidentity.pingonemfa.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.types.DenyReason
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test

class PushApprovalServiceTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var notificationObject: NotificationObject
    private lateinit var pushNotification: PushNotification

    private lateinit var service: PushApprovalService

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        notificationObject = mockk(relaxed = true)

        pushNotification = PushNotification(
            notificationObject = notificationObject,
            title = "title",
            message = "message"
        )

        service = spyk(PushApprovalService(dispatcher = testDispatcher), recordPrivateCalls = true)
        // CRITICAL: block Android notification creation
        every {
            service["createForegroundNotification"]()
        } returns mockk<Notification>(relaxed = true)

        // Prevent Android framework calls
        every { service.startForeground(any(), any()) } just Runs
        every { service.stopForeground(any<Int>()) } just Runs
        every { service.stopSelf(any()) } just Runs

        // Notification plumbing
        every {
            service.getSystemService(NotificationManager::class.java)
        } returns mockk(relaxed = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `onStartCommand approves notification when user_action is approve`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneMobileConfirmationCallback>(3)
            callback.onComplete(null, null)
        }

        val intent = spyk(Intent())

        // Mock both overloads: the single-arg deprecated one (pre-Tiramisu) and the
        // two-arg typed one (API 33+). onStartCommand branches on Build.VERSION.SDK_INT
        // so only one path is exercised at runtime, but both must be stubbed so the test
        // compiles and runs correctly regardless of which API level the host JVM reports.
        every {
            intent.getParcelableExtra<PushNotification>("notification")
        } returns pushNotification
        every {
            intent.getParcelableExtra("notification", PushNotification::class.java)
        } returns pushNotification

        every {
            intent.getStringExtra("auth_method")
        } returns "banner"

        every {
            intent.getStringExtra("user_action")
        } returns "approve"

        service.onStartCommand(intent, 0, 1)

        testDispatcher.scheduler.advanceUntilIdle()

        // All arguments must use matchers when any() is present — mixing literals and
        // matchers causes MockK to misinterpret null as a non-matcher and fail verification
        // even when the call did occur with the expected values.
        verify {
            notificationObject.approve(
                any(),
                eq("banner"),
                isNull(),
                any()
            )
        }
    }

    @Test
    fun `onStartCommand denies notification when user_action is deny`() = runTest {
        every {
            notificationObject.deny(any(), DenyReason.NONE, any())
        } answers {
            val callback = args[2]
            callback!!
                .javaClass
                .getMethod("onComplete", Any::class.java)
                .invoke(callback, null)
        }

        val intent = spyk(Intent())

        // Mock both overloads: the single-arg deprecated one (pre-Tiramisu) and the
        // two-arg typed one (API 33+). See approve test for full explanation.
        every {
            intent.getParcelableExtra<PushNotification>("notification")
        } returns pushNotification
        every {
            intent.getParcelableExtra("notification", PushNotification::class.java)
        } returns pushNotification

        every {
            intent.getStringExtra("auth_method")
        } returns "banner"

        every {
            intent.getStringExtra("user_action")
        } returns "deny"

        service.onStartCommand(intent, 0, 1)

        testDispatcher.scheduler.advanceUntilIdle()
        verify {
            notificationObject.deny(
                service,
                DenyReason.NONE,
                any()
            )
        }

    }
}
