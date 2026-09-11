/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import android.content.Context
import com.pingidentity.journey.plugin.CallbackRegistry
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class CallbackInitializerTest {

    @Test
    fun `create registers PingOneRecognizeCallback and returns registry`() {
        val context = mockk<Context>(relaxed = true)

        val initializer = CallbackInitializer()
        val result = initializer.create(context)

        assertSame(CallbackRegistry, result)
        assertNotNull(CallbackRegistry.callbacks()["PingOneRecognizeCallback"])
    }

    @Test
    fun `create does not register obsolete callback keys`() {
        val context = mockk<Context>(relaxed = true)

        CallbackInitializer().create(context)

        assertNull(CallbackRegistry.callbacks()["RecognizeActionCallback"])
        assertNull(CallbackRegistry.callbacks()["RecognizeInitializeCallback"])
        assertNull(CallbackRegistry.callbacks()["RecognizeDataCollectionCallback"])
    }
}
