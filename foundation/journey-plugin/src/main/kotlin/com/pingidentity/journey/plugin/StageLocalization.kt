/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import android.os.LocaleList
import java.util.Locale

/**
 * Resolves the best matching localized value from a map of locale identifiers to strings.
 *
 * The candidate [locales] are tried in order. For each candidate the lookup chain is:
 * 1. Exact BCP-47 language tag match (e.g. `"en-gb"`), lowercased.
 * 2. The same identifier with `-` replaced by `_` (e.g. `"en_gb"`).
 * 3. The language-only subtag of that tag (e.g. `"en"` from `"en-gb"`), skipped when blank. Note
 *    this is the BCP-47 subtag, which uses the modern ISO 639 codes, and not [Locale.getLanguage].
 *
 * If no candidate matches any of these forms, the first value present in [localizedMap] is
 * returned. This function is pure and holds no framework dependency; [preferredLocales] is the
 * seam that supplies real candidate locales.
 *
 * @param localizedMap A map of locale identifiers (as provided by the server) to localized strings.
 * @param locales The candidate locales to try, in priority order.
 * @return The best matching localized value, or `null` if [localizedMap] is empty.
 */
internal fun resolveLocalizedValue(localizedMap: Map<String, String>, locales: List<Locale>): String? {
    if (localizedMap.isEmpty()) return null

    // If there's only one value, return it regardless of locale.
    if (localizedMap.size == 1) return localizedMap.values.first()

    for (locale in locales) {
        val identifier = locale.toLanguageTag().lowercase()

        // Try exact match first (e.g. "en-gb")
        localizedMap[identifier]?.let { return it }

        // Try with underscore instead of hyphen (e.g. "en_gb")
        val underscoreIdentifier = identifier.replace("-", "_")
        localizedMap[underscoreIdentifier]?.let { return it }

        // Try language code only (e.g. "en" from "en-GB"). The subtag is taken from the BCP-47
        // tag rather than Locale.language, because Locale.language still reports the obsolete
        // ISO 639 codes on Android ("iw", "ji", "in" for Hebrew, Yiddish and Indonesian) and so
        // would miss the modern keys ("he", "yi", "id") that iOS matches.
        val languageCode = identifier.substringBefore('-')
        if (languageCode.isNotEmpty()) {
            localizedMap[languageCode]?.let { return it }
        }
    }

    // If no match found, return the first available value.
    return localizedMap.values.firstOrNull()
}

/**
 * Returns the ordered list of candidate locales to use when resolving a localized value.
 *
 * This is the single seam where device/process locale state is read, kept separate from the
 * pure [resolveLocalizedValue] algorithm so that it can be exercised deterministically in tests.
 *
 * The candidates come from [LocaleList.getAdjustedDefault], so the first entry is always the
 * app's effective locale, and the remainder are the user's other preferred locales in system
 * order. Falls back to [Locale.getDefault] alone when the framework call is unavailable (e.g.
 * `null` under a plain JVM unit test) or returns an empty list.
 *
 * @return The candidate locales, in priority order.
 */
internal fun preferredLocales(): List<Locale> {
    // The explicit nullable cast avoids Kotlin's compiler-inserted not-null assertion on this
    // platform-annotated @NonNull call, which would otherwise throw instead of falling through to
    // the guard below when isReturnDefaultValues (unit tests without Robolectric) yields null.
    val adjustedDefault = LocaleList.getAdjustedDefault() as LocaleList?
    if (adjustedDefault == null || adjustedDefault.isEmpty) {
        return listOf(Locale.getDefault())
    }

    return (0 until adjustedDefault.size()).map { adjustedDefault[it] }.distinct()
}
