/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import android.os.LocaleList
import com.pingidentity.orchestrate.ContinueNode
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
class PreferredLocalesTest {

    private lateinit var originalLocaleList: LocaleList
    private lateinit var originalLocale: Locale

    @BeforeTest
    fun setUp() {
        originalLocaleList = LocaleList.getDefault()
        originalLocale = Locale.getDefault()
    }

    @AfterTest
    fun tearDown() {
        LocaleList.setDefault(originalLocaleList)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `preferredLocales order mirrors the system locale list`() {
        val localeList = LocaleList(
            Locale.forLanguageTag("fr-FR"),
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("de-DE"),
        )
        LocaleList.setDefault(localeList)

        val result = preferredLocales()

        assertEquals(
            listOf(
                Locale.forLanguageTag("fr-FR"),
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("de-DE"),
            ),
            result,
        )
    }

    @Test
    fun `preferredLocales collapses duplicate locales`() {
        val localeList = LocaleList(
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("fr-FR"),
        )
        LocaleList.setDefault(localeList)

        val result = preferredLocales()

        assertEquals(listOf(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("fr-FR")), result)
    }

    /**
     * Pins the choice of [LocaleList.getAdjustedDefault] over [LocaleList.getDefault]: the first
     * candidate must be the app's *effective* locale, so that no existing single-locale device
     * changes behaviour. The two APIs only diverge when the effective locale is not the first system
     * preference, which the framework models through the hidden two-argument
     * `setDefault(LocaleList, int)`. That overload is a test-only API absent from the public SDK, so
     * it is invoked reflectively; without this test, swapping in `getDefault()` would pass silently.
     */
    @Test
    fun `preferredLocales reads the adjusted default rather than the raw system order`() {
        val systemOrder = LocaleList(
            Locale.forLanguageTag("fr-FR"),
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("de-DE"),
        )
        // Effective locale is index 1 (en-US), which the adjusted list hoists to the front.
        LocaleList::class.java
            .getDeclaredMethod("setDefault", LocaleList::class.java, Int::class.javaPrimitiveType)
            .invoke(null, systemOrder, 1)

        // Guard: if the two framework lists did not actually diverge, the assertion below would
        // pass under either API and prove nothing.
        assertNotEquals(LocaleList.getDefault(), LocaleList.getAdjustedDefault())

        assertEquals(
            listOf(
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("fr-FR"),
                Locale.forLanguageTag("de-DE"),
            ),
            preferredLocales(),
        )
    }

    @Test
    fun `end-to-end submitButtonText resolves through ordered preferred locales`() {
        val localeList = LocaleList(
            Locale.forLanguageTag("de-DE"),
            Locale.forLanguageTag("fr-FR"),
            Locale.forLanguageTag("en-US"),
        )
        LocaleList.setDefault(localeList)

        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit","fr":"Soumettre"}}""")
            }
        }

        assertEquals("Soumettre", continueNode.submitButtonText)
    }

    @Test
    fun `end-to-end pageFooter resolves through ordered preferred locales`() {
        val localeList = LocaleList(
            Locale.forLanguageTag("de-DE"),
            Locale.forLanguageTag("fr-FR"),
            Locale.forLanguageTag("en-US"),
        )
        LocaleList.setDefault(localeList)

        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"pageFooter":{"en":"Submit","fr":"Soumettre"}}""")
            }
        }

        assertEquals("Soumettre", continueNode.pageFooter)
    }
}
