/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.davinci

import android.content.Context
import com.pingidentity.davinci.plugin.CollectorFactory
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectorInitializerTest {

    @Before
    fun setUp() {
        CollectorFactory.reset()
    }

    @After
    fun tearDown() {
        CollectorFactory.reset()
    }

    @Test
    fun `create registers MOBILE_PAIRING with the CollectorFactory`() {
        val initializer = CollectorInitializer()
        initializer.create(mockk<Context>(relaxed = true))

        val factory = CollectorFactory.collectors()["MOBILE_PAIRING"]
        assertNotNull(factory, "MOBILE_PAIRING must be registered")
        val instance = factory.invoke()
        assertTrue(instance is MobilePairingCollector)
    }

    @Test
    fun `create returns the CollectorFactory singleton`() {
        val initializer = CollectorInitializer()
        val returned = initializer.create(mockk<Context>(relaxed = true))
        assertEquals(CollectorFactory, returned)
    }

    @Test
    fun `dependencies is empty`() {
        val initializer = CollectorInitializer()
        assertTrue(initializer.dependencies().isEmpty())
    }
}
