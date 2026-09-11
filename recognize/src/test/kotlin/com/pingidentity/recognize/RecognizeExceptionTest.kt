/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import io.keyless.sdk.errorshandling.KeylessSdkError
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RecognizeExceptionTest {

    private fun keylessSdkError(code: Int, message: String, debuggingInfo: Map<String, String> = emptyMap()): KeylessSdkError =
        mockk {
            every { this@mockk.code } returns code
            every { this@mockk.message } returns message
            every { this@mockk.debuggingInfo } returns debuggingInfo
        }

    @Test
    fun `from maps KeylessSdkError fields`() {
        val info = mapOf("FLOW_ID_KEY" to "flow-1", "SESSION_ID_KEY" to "session-2")
        val sdkError = keylessSdkError(code = 21, message = "user cancelled", debuggingInfo = info)

        val ex = RecognizeException.from(sdkError)

        assertEquals(21, ex.code)
        assertEquals("user cancelled", ex.message)
        assertEquals(info, ex.debuggingInfo)
        assertSame(sdkError, ex.cause)
    }

    @Test
    fun `from maps null message to UNKNOWN_ERROR`() {
        val sdkError = keylessSdkError(code = 1, message = "anything")
        every { sdkError.message } returns null

        val ex = RecognizeException.from(sdkError)
        assertEquals("UNKNOWN_ERROR", ex.message)
    }

    @Test
    fun `RecognizeException preserves code message and debuggingInfo`() {
        val info = mapOf("FLOW_ID_KEY" to "flow-1")
        val ex = RecognizeException(code = 42, message = "liveness failed", debuggingInfo = info)

        assertEquals(42, ex.code)
        assertEquals("liveness failed", ex.message)
        assertEquals(info, ex.debuggingInfo)
    }
}
