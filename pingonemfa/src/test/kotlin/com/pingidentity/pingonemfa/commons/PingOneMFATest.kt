package com.pingidentity.pingonemfa.commons

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.JsonObject
import com.pingidentity.android.ContextProvider
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneGeo
import com.pingidentity.pingidsdkv2.PingOneSDKError
import com.pingidentity.pingidsdkv2.types.NotificationProvider
import com.pingidentity.pingidsdkv2.types.OneTimePasscodeInfo
import com.pingidentity.pingidsdkv2.types.PairingInfo
import com.pingidentity.pingonemfa.push.PushNotification
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PingOneMFATest {
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockRemoteMessage = mockk<RemoteMessage>(relaxed = true)
    private val mockNotificationObject = mockk<NotificationObject>(relaxed = true)
    private val mockPairingInfo = mockk<PairingInfo>(relaxed = true)
    private val mockDeviceInfo = mockk<JsonObject>(relaxed = true)
    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        // mock ContextProvider object and provide a mock Context
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext

        // mock PingOne static functions (Java class)
        mockkStatic("com.pingidentity.pingidsdkv2.PingOne")


        // default remote message data with APS containing an alert/body
        every { mockRemoteMessage.data } returns mapOf(
            "aps" to """
                {
                  "alert": {
                    "title": "mocked title",
                    "body": "mocked body"
                  }
                }
            """.trimIndent()
        )

        every{ mockDeviceInfo.toString()} returns """
            {
              "NA": {
                "users": [
                  {
                    "id": "user1",
                    "environment": { "id": "env1" },
                    "device": { "id": "dev1" },
                    "username": "jdoe",
                    "name": { "given": "John", "family": "Doe" }
                  }
                ]
              }
            }
        """.trimIndent()

    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initialize configures once and returns success when callback error is null`() = runTest {

        // Reset singleton state FIRST
        val field = PingOneMFA::class.java.getDeclaredField("isInitialized")
        field.isAccessible = true
        field.setBoolean(PingOneMFA, false)


        every {
            PingOne.configure(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(2)
            callback.onComplete(null)
        }

        // ACT
        val first = PingOneMFA.initialize(Geo.NORTH_AMERICA)
        val second = PingOneMFA.initialize(Geo.EUROPE)

        // ASSERT
        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)

        verify(exactly = 1) {
            PingOne.configure(any(), any(), any())
        }
    }

    @Test
    fun `initialize maps geo to PingOneGeo`() = runTest {

        // Reset singleton state FIRST
        val field = PingOneMFA::class.java.getDeclaredField("isInitialized")
        field.isAccessible = true
        field.setBoolean(PingOneMFA, false)

        every {
            PingOne.configure(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(2)
            callback.onComplete(null)
        }

        // ACT
        val result = PingOneMFA.initialize(Geo.EUROPE)

        // ASSERT
        assertTrue(result.isSuccess)

        verify(exactly = 1) {
            PingOne.configure(any(), PingOneGeo.EUROPE, any())
        }
    }

    @Test
    fun `initialize configures once and returns error`() = runTest {

        // Reset singleton state FIRST
        val field = PingOneMFA::class.java.getDeclaredField("isInitialized")
        field.isAccessible = true
        field.setBoolean(PingOneMFA, false)


        every {
            PingOne.configure(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(2)
            callback.onComplete(PingOneSDKError(100, "mockedError"))
        }

        // ACT
        val result = PingOneMFA.initialize(Geo.NORTH_AMERICA)

        // ASSERT — known SDK failure: message is the formatted string, no SDK type needed
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull() as PingOneMFAException).internalErrorsList?.get(0)?.code == 100 }
    }

    @Test
    fun `initialize configures once and throws exception`() = runTest {

        // Reset singleton state FIRST
        val field = PingOneMFA::class.java.getDeclaredField("isInitialized")
        field.isAccessible = true
        field.setBoolean(PingOneMFA, false)

        every {
            PingOne.configure(any(), any(), any())
        } throws RuntimeException("Simulated configuration failure")

        // ACT
        val result = PingOneMFA.initialize(Geo.NORTH_AMERICA)

        // ASSERT — unexpected exception path: message is forwarded from the original exception
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("Simulated configuration failure", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `register returns success when callback errors is null`() = runTest {
        every {
            PingOne.setDeviceToken(any(), any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSetDeviceTokenCallback>(3)
            callback.onComplete(null)
        }

        val result = PingOneMFA.setDeviceToken("token")

        assertTrue(result.isSuccess)
        verify {
            PingOne.setDeviceToken(mockContext, "token", NotificationProvider.FCM, any())
        }
    }

    @Test
    fun `register returns error when callback errors is not null`() = runTest {
        every {
            PingOne.setDeviceToken(any(), any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSetDeviceTokenCallback>(3)
            callback.onComplete(Array(1) { PingOneSDKError(10003, "mockedError") })
        }

        val result = PingOneMFA.setDeviceToken("token")

        // Known SDK failure: message contains the formatted SDK code
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull() as PingOneMFAException).internalErrorsList?.get(0)?.code == 10003 }
        verify {
            PingOne.setDeviceToken(mockContext, "token", NotificationProvider.FCM, any())
        }
    }

    @Test
    fun `register returns error when exception is thrown`() = runTest {
        every {
            PingOne.setDeviceToken(any(), any(), any(), any())
        } throws RuntimeException("Simulated network error")

        val result = PingOneMFA.setDeviceToken("token")

        // Unexpected exception path: message is forwarded from the original exception
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("Simulated network error", result.exceptionOrNull()!!.message)
        verify {
            PingOne.setDeviceToken(mockContext, "token", NotificationProvider.FCM, any())
        }
    }

    @Test
    fun `pair returns success when callback error is null`() = runTest {
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKPairingCallback>(2)
            callback.onComplete(mockPairingInfo, null)
        }

        val result = PingOneMFA.pair("PAIR-KEY")

        assertTrue(result.isSuccess)
        verify {
            PingOne.pair(mockContext, "PAIR-KEY", any())
        }
    }

    @Test
    fun `pair returns error when callback error is not null`() = runTest {
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKPairingCallback>(2)
            callback.onComplete(null, PingOneSDKError(10003, "mockedError"))
        }

        val result = PingOneMFA.pair("PAIR-KEY")

        // Known SDK failure: message contains the formatted SDK code
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull() as PingOneMFAException).internalErrorsList?.get(0)?.code == 10003 }
        verify {
            PingOne.pair(mockContext, "PAIR-KEY", any())
        }
    }

    @Test
    fun `pair returns error when exception is thrown`() = runTest {
        every {
            PingOne.pair(any(), any(), any())
        } throws RuntimeException("Simulated network error")

        val result = PingOneMFA.pair("PAIR-KEY")

        // Unexpected exception path: message is forwarded from the original exception
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("Simulated network error", result.exceptionOrNull()!!.message)
        verify {
            PingOne.pair(mockContext, "PAIR-KEY", any())
        }
    }

    @Test
    fun `pair late native success callback after cancellation is silently discarded`() = runTest {
        // Regression guard for the cancellation race: the UI cancels the pairing job, and the
        // native SDK callback can still arrive afterwards on its own thread. resume() on a
        // cancelled CancellableContinuation must be the documented no-op (prompt cancellation
        // guarantee), never a throw on the callback thread.
        lateinit var captured: PingOne.PingOneSDKPairingCallback
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            // Hand the callback out without invoking it — pair() is now suspended on it.
            captured = arg<PingOne.PingOneSDKPairingCallback>(2)
        }

        val job = launch { PingOneMFA.pair("PAIR-KEY") }
        runCurrent() // coroutine has entered pair() and suspended on the captured callback

        job.cancel() // user cancelled while pairing is still in flight

        // Native callback arrives late, after cancellation — must be silently discarded.
        val late = runCatching { captured.onComplete(mockPairingInfo, null) }
        assertTrue(
            late.isSuccess,
            "late success callback after cancellation must be discarded, but threw: ${late.exceptionOrNull()}",
        )

        advanceUntilIdle()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `pair late native error callback after cancellation is silently discarded`() = runTest {
        lateinit var captured: PingOne.PingOneSDKPairingCallback
        every {
            PingOne.pair(any(), any(), any())
        } answers {
            captured = arg<PingOne.PingOneSDKPairingCallback>(2)
        }

        val job = launch { PingOneMFA.pair("PAIR-KEY") }
        runCurrent()

        job.cancel()

        val late = runCatching { captured.onComplete(null, PingOneSDKError(10005, "late failure")) }
        assertTrue(
            late.isSuccess,
            "late error callback after cancellation must be discarded, but threw: ${late.exceptionOrNull()}",
        )

        advanceUntilIdle()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `getAccounts returns success when deviceInfo is present`() = runTest {
        every {
            PingOne.getInfo(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneGetInfoCallback>(1)
            callback.onComplete(mockDeviceInfo, emptyArray<PingOneSDKError>())
        }

        val result = PingOneMFA.getDeviceInfo()

        assertTrue(result.isSuccess)

        val accounts = result.getOrNull()!!.first
        assertEquals(1, accounts.size)
        assertEquals("user1", accounts.first().id)
        assertEquals("env1", accounts.first().environment)
        assertEquals("dev1", accounts.first().deviceId)
        assertEquals("jdoe", accounts.first().username)
        assertEquals("John", accounts.first().name)
        assertEquals("Doe", accounts.first().family)
        verify {
            PingOne.getInfo(mockContext, any())
        }
    }

    @Test
    fun `getAccounts returns error when deviceInfo is not present`() = runTest {
        every{
            PingOne.getInfo(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneGetInfoCallback>(1)
            callback.onComplete(null, Array(1){ PingOneSDKError(10003, "mockedError") })
        }
        val result = PingOneMFA.getDeviceInfo()
        // Known SDK failure: message contains the formatted SDK code
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull() as PingOneMFAException).internalErrorsList?.get(0)?.message!!.contains("mockedError") }
    }

    @Test
    fun `getAccounts returns error when errors list is empty`() = runTest {
        // Defensive case: SDK returns null deviceInfo and an empty errors array — should not hang
        every {
            PingOne.getInfo(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneGetInfoCallback>(1)
            callback.onComplete(null, emptyArray())
        }
        val result = PingOneMFA.getDeviceInfo()
        // Fallback exception path: cause carries the generic message
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("getDeviceInfo failed: no error details provided", result.exceptionOrNull()!!.message
        )
    }

    @Test
    fun `getAccounts returns error when exception is thrown`() = runTest{
        every {
            PingOne.getInfo(any(), any())
        } throws RuntimeException("Simulated network error")
        val result = PingOneMFA.getDeviceInfo()
        // Unexpected exception path: message is forwarded from the original exception
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("Simulated network error", result.exceptionOrNull()!!.message)
    }




    @Test
    fun `collectOtp returns success when callback error is null`() = runTest {
        every {
            PingOne.getOneTimePassCode(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneOneTimePasscodeCallback>(1)
            callback.onComplete(OneTimePasscodeInfo("123456", 100000, 30), null)
        }
        val result = PingOneMFA.getOneTimePasscode()
        assertTrue(result.isSuccess)
        assertEquals(result.getOrNull()?.code, "123456")
    }

    @Test
    fun `collectOtp returns error when callback error is not null`() = runTest {
        every {
            PingOne.getOneTimePassCode(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneOneTimePasscodeCallback>(1)
            callback.onComplete(null, PingOneSDKError(10003, "mockedError"))
        }
        val result = PingOneMFA.getOneTimePasscode()
        // Known SDK failure: message contains the formatted SDK code
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull()!! as PingOneMFAException).internalErrorsList?.get(0)?.code == 10003 }
    }

    @Test
    fun `collectOtp returns error when both otpInfo and error are null`() = runTest {
        // Defensive case: SDK returns null otpInfo and null error — should not hang
        every {
            PingOne.getOneTimePassCode(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneOneTimePasscodeCallback>(1)
            callback.onComplete(null, null)
        }
        val result = PingOneMFA.getOneTimePasscode()
        // Fallback exception path: cause carries the generic message
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("getOneTimePasscode failed: no error details provided", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `collectOtp returns error when exception is thrown`() = runTest{
        every {
            PingOne.getOneTimePassCode(any(), any())
        } throws RuntimeException("Simulated network error")
        val result = PingOneMFA.getOneTimePasscode()
        // Unexpected exception path: message is forwarded from the original exception
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("Simulated network error", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `collectPush returns success when callback error is null`() = runTest{
        every {
            PingOne.processRemoteNotification(any(), any<RemoteMessage>(), any())
        } answers {
            val callback = arg<PingOne.PingOneNotificationCallback>(2)
            callback.onComplete(mockNotificationObject, null)
        }
        val result = PingOneMFA.processRemoteNotification(mockRemoteMessage)
        assertTrue(result.isSuccess)
        assertEquals(result.getOrNull()?.notificationObject, mockNotificationObject)
        assertEquals("mocked title", result.getOrNull()?.title)
        assertEquals("mocked body", result.getOrNull()?.message)

        verify {
            PingOne.processRemoteNotification(mockContext, mockRemoteMessage, any())
        }
    }

    @Test
    fun `collectPush returns error when callback error is not null`() = runTest{
        every {
            PingOne.processRemoteNotification(any(), any<RemoteMessage>(), any())
        } answers {
            val callback = arg<PingOne.PingOneNotificationCallback>(2)
            callback.onComplete(null, PingOneSDKError(10003, "mockedError"))
        }
        val result = PingOneMFA.processRemoteNotification(mockRemoteMessage)
        // Known SDK failure: message contains the formatted SDK code
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { (result.exceptionOrNull() as PingOneMFAException).internalErrorsList?.get(0)?.code == 10003 }
        verify {
            PingOne.processRemoteNotification(mockContext, mockRemoteMessage, any())
        }
    }

    @Test
    fun `collectPush returns success with null when both notificationObject and error are null`() = runTest {
        // Both-null means the message was not a PingOne MFA push — no-op success.
        every {
            PingOne.processRemoteNotification(any(), any<RemoteMessage>(), any())
        } answers {
            val callback = arg<PingOne.PingOneNotificationCallback>(2)
            callback.onComplete(null, null)
        }
        val result = PingOneMFA.processRemoteNotification(mockRemoteMessage)
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `collectPush returns error when exception is thrown`() = runTest{
        every {
            PingOne.processRemoteNotification(any(), any<RemoteMessage>(), any())
        } throws RuntimeException("Mocked Exception")
        val result = PingOneMFA.processRemoteNotification(mockRemoteMessage)
        // Unexpected exception path: message is forwarded from the original exception
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("Mocked Exception", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `collectMobilePayload returns payload when error is null`() = runTest {
        every {
            PingOne.generateMobilePayload(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneGenerateMobilePayloadCallback>(1)
            callback.onComplete("mockPayload", null)
        }
        val result = PingOneMFA.generateMobilePayload()
        assertTrue(result.isSuccess)
        assertEquals(result.getOrNull(), "mockPayload")
    }

    @Test
    fun `collectMobilePayload returns error when error is not null`() = runTest {
        every {
            PingOne.generateMobilePayload(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneGenerateMobilePayloadCallback>(1)
            callback.onComplete(null, PingOneSDKError(10003, "mockedError"))
        }
        val result = PingOneMFA.generateMobilePayload()
        // Known SDK failure: message contains the formatted SDK code
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { result.exceptionOrNull()!!.message!!.contains("mockedError") }
    }

    @Test
    fun `collectMobilePayload returns error when both payload and error are null`() = runTest {
        // Defensive case: SDK returns null payload and null error — should not hang
        every {
            PingOne.generateMobilePayload(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneGenerateMobilePayloadCallback>(1)
            callback.onComplete(null, null)
        }
        val result = PingOneMFA.generateMobilePayload()
        // Fallback exception path: cause carries the generic message
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("generateMobilePayload failed: no error details provided", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `collectMobilePayload returns error when exception is thrown`() = runTest {
        every {
            PingOne.generateMobilePayload(any(), any())
        } throws RuntimeException("Mocked Exception")
        val result = PingOneMFA.generateMobilePayload()
        // Unexpected exception path: message is forwarded from the original exception
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertEquals("Mocked Exception", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `approvePushNotificationFromBanner starts foreground service with correct intent`() {
        val mockNotification = mockk<PushNotification>(relaxed = true)
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.startForegroundService(any(), any())
        } returns mockk()

        PingOneMFA.approvePushNotificationFromBanner(mockNotification)

        // verify service start
        verify(exactly = 1) {
            ContextCompat.startForegroundService(mockContext, any())
        }
    }

    @Test
    fun `denyPushNotificationFromBanner starts foreground service with intent`() {
        val mockNotification = mockk<PushNotification>(relaxed = true)

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.startForegroundService(any(), any())
        } returns mockk()

        // act
        PingOneMFA.denyPushNotificationFromBanner(mockNotification)

        // verify wiring
        verify(exactly = 1) {
            ContextCompat.startForegroundService(mockContext, any())
        }
    }
}
