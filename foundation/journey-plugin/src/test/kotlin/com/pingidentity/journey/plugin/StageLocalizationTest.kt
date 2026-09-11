/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageLocalizationTest {

    @Test
    fun `Should return null when localized map is empty`() {
        val result = resolveLocalizedValue(emptyMap(), listOf(Locale.forLanguageTag("en-US")))

        assertNull(result)
    }

    @Test
    fun `Should return the single entry regardless of locale`() {
        val localizedMap = mapOf("fr" to "Soumettre")

        val result = resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("de-DE")))

        assertEquals("Soumettre", result)
    }

    @Test
    fun `Should match exact hyphenated language tag`() {
        val localizedMap = linkedMapOf(
            "en-us" to "Submit US",
            "fr-ca" to "Soumettre CA",
        )

        val result = resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("en-US")))

        assertEquals("Submit US", result)
    }

    @Test
    fun `Should match underscore variant of the language tag`() {
        val localizedMap = linkedMapOf(
            "en_us" to "Submit US",
            "fr_ca" to "Soumettre CA",
        )

        val result = resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("en-US")))

        assertEquals("Submit US", result)
    }

    @Test
    fun `Should fall back to language-only match when full tag is absent`() {
        val localizedMap = linkedMapOf(
            "en" to "Submit",
            "de" to "Einreichen",
        )

        val result = resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("en-US")))

        assertEquals("Submit", result)
    }

    /**
     * The language-only step must use the BCP-47 subtag, not [Locale.getLanguage]. Norwegian
     * Nynorsk is the one case where the two differ identically on every runtime: `toLanguageTag()`
     * maps `no-NO-NY` to `nn-NO` while `language` stays `"no"`. This is the deterministic guard for
     * the same defect that affects Hebrew, Yiddish and Indonesian on Android only.
     */
    @Test
    fun `Should match the BCP-47 language subtag rather than the legacy Locale language code`() {
        val nynorsk = Locale("no", "NO", "NY")
        val localizedMap = linkedMapOf(
            "de" to "Senden",
            "nn" to "Send",
        )

        val result = resolveLocalizedValue(localizedMap, listOf(nynorsk))

        assertEquals("Send", result)
    }

    /**
     * The real-world case from the ticket: Android's [Locale.getLanguage] returns the obsolete
     * `"iw"` for Hebrew, so a server map keyed on the modern `"he"` used to fall through to the
     * first entry while iOS matched it. Deterministic on Android; on a desktop JVM the assertion
     * holds regardless, so it documents intent rather than guarding it — see the Nynorsk case above
     * for the runtime-independent guard.
     */
    @Test
    fun `Should match modern ISO 639 language keys for locales with obsolete Java codes`() {
        val localizedMap = linkedMapOf(
            "en" to "Submit",
            "he" to "שלח",
            "id" to "Kirim",
        )

        assertEquals("שלח", resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("he-IL"))))
        assertEquals("Kirim", resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("id-ID"))))
    }

    @Test
    fun `Should match a script locale by its exact language tag`() {
        val localizedMap = linkedMapOf(
            "zh-hans-cn" to "提交",
            "en" to "Submit",
        )

        val result = resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("zh-Hans-CN")))

        assertEquals("提交", result)
    }

    @Test
    fun `Should return first map entry in insertion order when no candidate matches`() {
        val localizedMap = linkedMapOf(
            "ja" to "送信",
            "ko" to "제출",
        )

        val result = resolveLocalizedValue(localizedMap, listOf(Locale.forLanguageTag("en-US")))

        assertEquals("送信", result)
    }

    @Test
    fun `Should return first map entry when candidate list is empty`() {
        val localizedMap = linkedMapOf(
            "en" to "Submit",
            "fr" to "Soumettre",
        )

        val result = resolveLocalizedValue(localizedMap, emptyList())

        assertEquals("Submit", result)
    }

    /**
     * Covers [preferredLocales]'s guard branch. This class deliberately runs without Robolectric,
     * so the static `LocaleList.getAdjustedDefault()` resolves against the mockable android jar and
     * returns `null` under `isReturnDefaultValues`. That fallback is what keeps every non-Robolectric
     * test in this module (notably `ContinueNodeTest`) resolving against a real locale.
     */
    @Test
    fun `preferredLocales falls back to the default locale when the framework list is unavailable`() {
        val result = preferredLocales()

        assertEquals(listOf(Locale.getDefault()), result)
    }
}
