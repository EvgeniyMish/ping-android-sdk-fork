/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extension property for Connector class to get a list of collectors.
 *
 * @return A list of Collector instances.
 */
val ContinueNode.callbacks: List<Callback>
    get() = this.actions.filterIsInstance<Callback>()

private const val HEADER = "header"
private const val DESCRIPTION = "description"
private const val STAGE = "stage"
private const val SUBMIT_BUTTON_TEXT = "submitButtonText"
private const val PAGE_FOOTER = "pageFooter"

/**
 * Extension property to retrieve the header text for this ContinueNode.
 *
 * The header text is typically displayed at the top of the authentication screen
 * to provide context about the current step in the journey.
 *
 * @return The header text from the node's input, or an empty string if not present
 */
val ContinueNode.header: String
    get() = this.input[HEADER]?.jsonPrimitive?.content ?: ""

/**
 * Extension property to retrieve the description text for this ContinueNode.
 *
 * The description provides additional context or instructions for the current
 * authentication step, typically displayed below the header.
 *
 * @return The description text from the node's input, or an empty string if not present
 *
 */
val ContinueNode.description: String
    get() = this.input[DESCRIPTION]?.jsonPrimitive?.content ?: ""

/**
 * Extension property to retrieve the stage identifier for this ContinueNode.
 *
 * The stage field contains metadata about the current authentication step, which can include:
 * - A simple stage name (e.g., "Login", "MFA")
 * - A JSON string with localized UI text (e.g., `{"submitButtonText":{"en":"Submit"}}`)
 *
 * This property returns the raw stage value as a string. For parsed localized values,
 * use [submitButtonText] or [pageFooter] properties instead.
 *
 * @return The stage identifier from the node's input, or an empty string if not present
 *
 */
val ContinueNode.stage: String
    get() = this.input[STAGE]?.jsonPrimitive?.content ?: ""

/**
 * Extension property to retrieve the localized submit button text for this ContinueNode.
 *
 * This property extracts locale-specific text from the `stage` field's JSON structure:
 * ```json
 * {"submitButtonText":{"en":"Submit","en-gb":"Submit","fr":"Soumettre"}}
 * ```
 *
 * When the localization map has more than one entry, the device's ordered preferred-locale list
 * is walked (the app's effective locale first, then the rest of the user's preferences in system
 * order) and, for each candidate in turn:
 * - Exact BCP-47 tag match (e.g. `"en-gb"`)
 * - The same identifier with `-` replaced by `_` (e.g. `"en_gb"`)
 * - Language-only match (e.g. `"en"` for `"en-GB"`)
 *
 * The device identifier is lowercased before lookup; the server's localization keys are matched
 * as provided (lowercase keys expected). If no candidate matches, the first available value in
 * the map is returned.
 *
 * @return The localized submit button text, or an empty string if not available
 *
 * @see getLocalizedValueFromStage
 * @see resolveLocalizedValue
 * @see pageFooter
 */
val ContinueNode.submitButtonText: String
    get() {
        // First, try to get localized value from stage JSON
        return getLocalizedValueFromStage(SUBMIT_BUTTON_TEXT) ?: ""
    }

/**
 * Extension property to retrieve the localized page footer text for this ContinueNode.
 *
 * This property extracts locale-specific footer text from the `stage` field's JSON structure.
 * The footer is typically displayed at the bottom of the authentication screen to provide
 * additional information, legal notices, or copyright text.
 *
 * The `stage` field may contain:
 * ```json
 * {"pageFooter":{"en":"© 2026 Company","en-gb":"© 2026 Company Ltd","fr":"© 2026 Société"}}
 * ```
 *
 * When the localization map has more than one entry, the device's ordered preferred-locale list
 * is walked (the app's effective locale first, then the rest of the user's preferences in system
 * order) and, for each candidate in turn:
 * - Exact BCP-47 tag match (e.g. `"en-gb"`)
 * - The same identifier with `-` replaced by `_` (e.g. `"en_gb"`)
 * - Language-only match (e.g. `"en"` for `"en-GB"`)
 *
 * The device identifier is lowercased before lookup; the server's localization keys are matched
 * as provided (lowercase keys expected). If no candidate matches, the first available value in
 * the map is returned.
 *
 * @return The localized footer text, or an empty string if not available
 *
 * @see getLocalizedValueFromStage
 * @see resolveLocalizedValue
 * @see submitButtonText
 */
val ContinueNode.pageFooter: String
    get() {
        // First, try to get localized value from stage JSON
        return getLocalizedValueFromStage(PAGE_FOOTER) ?: ""
    }

/**
 * Attempts to extract a localized value from the `stage` field's JSON structure.
 *
 * The `stage` field may contain a JSON string with localized UI text:
 * ```json
 * {"submitButtonText":{"en":"Submit","en-gb":"Submit","fr":"Soumettre"},"pageFooter":{"en":"Footer"}}
 * ```
 *
 * This method parses the JSON, flattens the localization map for [key], and delegates the
 * matching algorithm to [resolveLocalizedValue] using the candidate locales from
 * [preferredLocales].
 *
 * @param key The key to look up in the stage JSON (e.g., "submitButtonText" or "pageFooter")
 * @return The localized string value, or `null` if:
 *         - The `stage` field is empty or missing
 *         - The JSON is malformed
 *         - The specified key doesn't exist in the JSON
 *         - The key's value is not a valid localization map
 *
 * @see submitButtonText
 * @see pageFooter
 * @see resolveLocalizedValue
 */
private fun ContinueNode.getLocalizedValueFromStage(key: String): String? {
    return try {
        val stageValue = this.input[STAGE]?.jsonPrimitive?.content
        if (stageValue.isNullOrEmpty()) return null

        val jsonObject = Json.parseToJsonElement(stageValue).jsonObject
        val localizedDict = jsonObject[key]?.jsonObject ?: return null

        // Convert JsonObject to Map<String, String>
        val localizedMap = localizedDict.entries.associate { (k, v) ->
            k to (v.jsonPrimitive.content)
        }

        resolveLocalizedValue(localizedMap, preferredLocales())
    } catch (e: Exception) {
        null
    }
}


