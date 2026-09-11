/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import android.content.Context
import com.pingidentity.davinci.plugin.CollectorFactory
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class CollectorInitializerTest {

    @BeforeTest
    fun setUp() {
        CollectorFactory.reset()
    }

    @AfterTest
    fun tearDown() {
        CollectorFactory.reset()
    }

    @Test
    fun `create registers RECOGNIZE collector and returns factory`() {
        val context = mockk<Context>(relaxed = true)

        val initializer = CollectorInitializer()
        val result = initializer.create(context)

        assertSame(CollectorFactory, result)
        assertNotNull(CollectorFactory.collectors()["RECOGNIZE"])
    }
}
