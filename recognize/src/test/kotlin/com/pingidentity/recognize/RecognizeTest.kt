/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.auth.BiomAuthConfig
import io.keyless.sdk.configurations.enroll.BiomEnrollConfig
import io.keyless.sdk.errorshandling.AuthenticationSuccess
import io.keyless.sdk.errorshandling.EnrollmentSuccess
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecognizeTest {

    @BeforeTest
    fun setUp() {
        mockkObject(Recognize)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Recognize)
    }

    @Test
    fun setupIsMockableViaMockkObject() = runTest {
        val expected = Result.success(Unit)
        coEvery { Recognize.setup(any<SetupConfig>()) } returns expected

        val result = Recognize.setup(SetupConfig(apiKey = "k", hosts = emptyList()))
        assertEquals(expected, result)
    }

    @Test
    fun getUserIdIsMockableViaMockkObject() {
        val expected = Result.success("user-id")
        every { Recognize.getUserId() } returns expected

        assertEquals(expected, Recognize.getUserId())
    }

    @Test
    fun enrollIsMockableViaMockkObject() = runTest {
        val successValue = mockk<EnrollmentSuccess>()
        val expected = Result.success(successValue)
        coEvery { Recognize.enroll(any<BiomEnrollConfig>()) } returns expected

        val result = Recognize.enroll(BiomEnrollConfig())
        assertEquals(expected, result)
    }

    @Test
    fun authenticateIsMockableViaMockkObject() = runTest {
        val successValue = mockk<AuthenticationSuccess>()
        val expected = Result.success(successValue)
        coEvery { Recognize.authenticate(any<BiomAuthConfig>()) } returns expected

        val result = Recognize.authenticate(BiomAuthConfig())
        assertEquals(expected, result)
    }

    @Test
    fun setupPropagatesFailure() = runTest {
        val error = RuntimeException("setup failed")
        coEvery { Recognize.setup(any()) } returns Result.failure(error)

        val result = Recognize.setup(SetupConfig(apiKey = "k", hosts = emptyList()))
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun enrollPropagatesFailure() = runTest {
        val error = RuntimeException("enroll failed")
        coEvery { Recognize.enroll(any()) } returns Result.failure(error)

        val result = Recognize.enroll(BiomEnrollConfig())
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun authenticatePropagatesFailure() = runTest {
        val error = RuntimeException("auth failed")
        coEvery { Recognize.authenticate(any()) } returns Result.failure(error)

        val result = Recognize.authenticate(BiomAuthConfig())
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}
