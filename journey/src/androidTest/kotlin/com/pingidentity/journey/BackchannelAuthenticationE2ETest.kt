/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import androidx.core.net.toUri
import androidx.test.filters.SmallTest
import com.pingidentity.journey.IntegrationTestConfig.backchannelClientId
import com.pingidentity.journey.IntegrationTestConfig.backchannelClientSecret
import com.pingidentity.journey.module.session
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.network.HttpResponse
import com.pingidentity.network.isSuccess
import com.pingidentity.network.ktor.HttpClient
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.Workflow
import io.ktor.client.plugins.HttpRequestTimeoutException
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import com.pingidentity.network.HttpClient as SdkHttpClient

/**
 * E2E tests for AM/AIC transactional backchannel authentication (`Journey.start(backchannelUri:)`),
 * SDKS-5157 / SDKS-5330.
 *
 * There is no federation gateway in this test environment, so each test simulates the gateway's role
 * itself:
 *  1. Obtain a gateway access token via the OAuth2 client-credentials grant (client
 *     [backchannelClientId] / [backchannelClientSecret], scope `back_channel_authentication`).
 *  2. Call AM's `/authenticate/backchannel/initialize` with that token to obtain a `redirectUri`.
 *  3. Hand that `redirectUri` to `Journey.start(backchannelUri:)`, exactly as an app would after
 *     receiving it from a real gateway.
 *
 * Test server: openam-sdks.forgeblocks.com (AIC). Journey under test: `back-channel-authentication`.
 */
@SmallTest
class BackchannelAuthenticationE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "back-channel-authentication"
    }

    @Test
    fun backchannelAuthentication_happyPath_returnsSuccessNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()

        var result = defaultJourney.start(backchannelUri = redirectUri.toUri())
        if (result is ContinueNode) {
            result.handleLoginCallbacks(USERNAME, PASSWORD)
            result = result.next()
        }

        assertTrue(
            "Expected SuccessNode after backchannel authentication, got ${result::class.simpleName}",
            result is SuccessNode
        )
        assertNotNull("Expected a session to be created", defaultJourney.session())
    }

    // Host match is case-insensitive — an upper-cased host in the redirectUri must still be
    // accepted, since JourneyConfig.serverUrl's host is compared with ignoreCase = true.
    @Test
    fun backchannelAuthentication_hostUpperCased_returnsSuccessNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val upperCasedHostUri = redirectUri.uppercaseHost()

        var result = defaultJourney.start(backchannelUri = upperCasedHostUri.toUri())
        if (result is ContinueNode) {
            result.handleLoginCallbacks(USERNAME, PASSWORD)
            result = result.next()
        }

        assertTrue(
            "Expected SuccessNode after backchannel authentication, got ${result::class.simpleName}",
            result is SuccessNode
        )
        assertNotNull("Expected a session to be created", defaultJourney.session())
    }

    // Invalid credentials on a backchannel-continued login — completing the ContinueNode
    // returned by a backchannel start with the wrong credentials must behave exactly like a
    // normal, non-backchannel Journey (ErrorNode, no session persisted), not silently succeed or
    // crash. Verified directly through the backchannel entry point rather than assumed by analogy
    // with JourneyE2ETests.handleError()'s tree-based equivalent.
    @Test
    fun backchannelAuthentication_invalidCredentials_returnsErrorNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()

        var result = defaultJourney.start(backchannelUri = redirectUri.toUri())
        if (result is ContinueNode) {
            result.handleLoginCallbacks("invalidUser", "invalidPassword")
            result = result.next()
        }

        assertTrue(
            "Expected ErrorNode for invalid credentials, got ${result::class.simpleName}",
            result is ErrorNode
        )
        assertEquals("Login failure", (result as ErrorNode).message)
        assertNull("Expected no session to be created after failed login", defaultJourney.session())
    }

    // Missing authIndexType — a redirectUri missing the required authIndexType query
    // parameter must be rejected client-side with a FailureNode, before any network call.
    @Test
    fun backchannelAuthentication_missingAuthIndexType_returnsFailureNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val uriMissingAuthIndexType = redirectUri.withoutQueryParam("authIndexType")

        val result = defaultJourney.start(backchannelUri = uriMissingAuthIndexType.toUri())

        val failure = assertIsFailureNode(result)
        assertEquals("Missing authIndexType or authIndexValue", failure.cause.message)
    }

    // Missing authIndexValue — same guard as the previous case, for the value parameter.
    @Test
    fun backchannelAuthentication_missingAuthIndexValue_returnsFailureNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val uriMissingAuthIndexValue = redirectUri.withoutQueryParam("authIndexValue")

        val result = defaultJourney.start(backchannelUri = uriMissingAuthIndexValue.toUri())

        val failure = assertIsFailureNode(result)
        assertEquals("Missing authIndexType or authIndexValue", failure.cause.message)
    }

    // Blank/whitespace-only parameter values — empty or whitespace-only values must be
    // rejected the same way as absent ones, not treated as present.
    @Test
    fun backchannelAuthentication_emptyAuthIndexType_returnsFailureNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val uri = redirectUri.withQueryParam("authIndexType", "")

        val result = defaultJourney.start(backchannelUri = uri.toUri())

        val failure = assertIsFailureNode(result)
        assertEquals("Missing authIndexType or authIndexValue", failure.cause.message)
    }

    @Test
    fun backchannelAuthentication_whitespaceOnlyAuthIndexType_returnsFailureNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val uri = redirectUri.withQueryParam("authIndexType", " ")

        val result = defaultJourney.start(backchannelUri = uri.toUri())

        val failure = assertIsFailureNode(result)
        assertEquals("Missing authIndexType or authIndexValue", failure.cause.message)
    }

    @Test
    fun backchannelAuthentication_whitespaceOnlyAuthIndexValue_returnsFailureNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val uri = redirectUri.withQueryParam("authIndexValue", "  ")

        val result = defaultJourney.start(backchannelUri = uri.toUri())

        val failure = assertIsFailureNode(result)
        assertEquals("Missing authIndexType or authIndexValue", failure.cause.message)
    }

    // Malformed / unparseable URI string — pasting garbage text (not a URI at all) must be
    // handled gracefully as a FailureNode, not a crash.
    @Test
    fun backchannelAuthentication_malformedUriString_returnsFailureNodeWithoutCrashing() = runTest {
        val garbageUri = "not a uri at all !!".toUri()

        val result = defaultJourney.start(backchannelUri = garbageUri)

        val failure = assertIsFailureNode(result)
        assertEquals("Backchannel URI host does not match configured serverUrl", failure.cause.message)
    }

    // Opaque (non-hierarchical) URI — a syntactically valid but non-hierarchical URI (e.g.
    // a mailto: URI, which has no authority/query) must be rejected without crashing, via the
    // explicit isHierarchical guard.
    @Test
    fun backchannelAuthentication_opaqueUri_returnsFailureNode() = runTest {
        val opaqueUri = "mailto:user@example.com".toUri()

        val result = defaultJourney.start(backchannelUri = opaqueUri)

        val failure = assertIsFailureNode(result)
        assertEquals("Backchannel URI is not hierarchical", failure.cause.message)
    }

    // Host mismatch (security) — a redirectUri whose host does not match the configured
    // JourneyConfig.serverUrl must be rejected before any query parameter is trusted, guarding
    // against a malicious or misconfigured gateway forwarding a URI from an unexpected origin.
    @Test
    fun backchannelAuthentication_hostMismatch_returnsFailureNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val maliciousUri = redirectUri.withHost("attacker.example.com")

        val result = defaultJourney.start(backchannelUri = maliciousUri.toUri())

        val failure = assertIsFailureNode(result)
        assertEquals("Backchannel URI host does not match configured serverUrl", failure.cause.message)
    }

    // JourneyConfig absent or serverUrl unset — a misconfigured Journey must fail
    // gracefully (no crash, no network call) rather than throw. Neither sub-case reaches the
    // host-validation branch, so a placeholder URI (never dereferenced) is enough here.
    @Test
    fun backchannelAuthentication_journeyConfigAbsent_returnsFailureNode() = runTest {
        val rawWorkflow = Workflow {}

        val result = rawWorkflow.start(backchannelUri = placeholderBackchannelUri)

        val failure = assertIsFailureNode(result)
        assertEquals("JourneyConfig missing", failure.cause.message)
    }

    @Test
    fun backchannelAuthentication_serverUrlUnset_returnsFailureNode() = runTest {
        val journeyWithoutServerUrl = Journey {
            // serverUrl deliberately left unset
        }

        val result = journeyWithoutServerUrl.start(backchannelUri = placeholderBackchannelUri)

        val failure = assertIsFailureNode(result)
        assertEquals("JourneyConfig.serverUrl is not configured", failure.cause.message)
    }

    // Realm in the URI is ignored — JourneyConfig.realm is always used. The SDK never
    // trusts the `realm` query parameter in the redirectUri, so pointing it at a realm that
    // doesn't exist must not break the flow: the authenticate call still targets the configured
    // realm (alpha). If the SDK mistakenly used the URI's realm, this would fail against AM.
    @Test
    fun backchannelAuthentication_realmInUriIgnored_returnsSuccessNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val uriWithWrongRealm = redirectUri.withQueryParam("realm", "/bravo")

        var result = defaultJourney.start(backchannelUri = uriWithWrongRealm.toUri())
        if (result is ContinueNode) {
            result.handleLoginCallbacks(USERNAME, PASSWORD)
            result = result.next()
        }

        assertTrue(
            "Expected SuccessNode after backchannel authentication, got ${result::class.simpleName}",
            result is SuccessNode
        )
        assertNotNull("Expected a session to be created", defaultJourney.session())
    }

    // forceAuth option — the option lambda must be honored end-to-end on a backchannel
    // start, not just accepted and dropped. This confirms the flow still completes successfully
    // with forceAuth=true; the exact "ForceAuth=true" request-query assertion is already covered
    // by the unit-test suite (no mock engine to introspect here — see the TC-04 note above).
    @Test
    fun backchannelAuthentication_forceAuthOption_returnsSuccessNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()

        var result = defaultJourney.start(backchannelUri = redirectUri.toUri()) {
            forceAuth = true
        }
        if (result is ContinueNode) {
            result.handleLoginCallbacks(USERNAME, PASSWORD)
            result = result.next()
        }

        assertTrue(
            "Expected SuccessNode after backchannel authentication with forceAuth=true, got ${result::class.simpleName}",
            result is SuccessNode
        )
        assertNotNull("Expected a session to be created", defaultJourney.session())
    }

    // noSession option — the option lambda must be honored end-to-end, and, unlike
    // forceAuth, its effect is directly observable in the e2e flow: when noSession=true, AM
    // returns an empty session token, so the SDK's Session module skips persisting it (see the
    // "may be empty due to NoSession" comment in module/Session.kt).
    @Test
    fun backchannelAuthentication_noSessionOption_doesNotPersistSession() = runTest {
        val redirectUri = initializeBackchannelTransaction()

        var result = defaultJourney.start(backchannelUri = redirectUri.toUri()) {
            noSession = true
        }
        if (result is ContinueNode) {
            result.handleLoginCallbacks(USERNAME, PASSWORD)
            result = result.next()
        }

        assertTrue(
            "Expected SuccessNode after backchannel authentication with noSession=true, got ${result::class.simpleName}",
            result is SuccessNode
        )
        assertNull("Expected no session to be persisted when noSession=true", defaultJourney.session())
    }

    // AM rejects unknown transactions — a redirectUri that passes client-side validation
    // (correct host, well-formed authIndexType/authIndexValue) but names a transaction id AM has
    // never seen must surface as an ErrorNode carrying AM's rejection message, not a crash or an
    // opaque FailureNode.
    @Test
    fun backchannelAuthentication_unknownTransaction_returnsErrorNode() = runTest {
        val unknownTransactionUri =
            "$SERVER_URL/UI/Login?realm=%2F$REALM&authIndexType=transaction&authIndexValue=${UUID.randomUUID()}".toUri()

        val result = defaultJourney.start(backchannelUri = unknownTransactionUri)

        assertTrue(
            "Expected ErrorNode for unknown transaction, got ${result::class.simpleName}",
            result is ErrorNode
        )
        assertEquals("Unable to read transaction.", (result as ErrorNode).message)
    }

    // Reusing an already-COMPLETED transaction — per the design doc's error-handling
    // table ("Transaction already COMPLETED -> ErrorNode"). Verified against the live tenant,
    // the rejection doesn't happen where you'd first guess: starting a second authenticate call
    // against the same transaction id is accepted (AM returns a fresh ContinueNode with login
    // callbacks again, same as the first attempt) — the SSO session from the first login must be
    // signed off first, or the second call short-circuits via the existing cookie instead of
    // re-evaluating the transaction at all. The rejection surfaces one step later, when
    // submitting those callbacks tries to *finalize* the already-completed transaction.
    @Test
    fun backchannelAuthentication_completedTransactionReused_returnsErrorNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()

        var firstResult = defaultJourney.start(backchannelUri = redirectUri.toUri())
        if (firstResult is ContinueNode) {
            firstResult.handleLoginCallbacks(USERNAME, PASSWORD)
            firstResult = firstResult.next()
        }
        assertTrue(
            "Expected SuccessNode on first use, got ${firstResult::class.simpleName}",
            firstResult is SuccessNode
        )
        defaultJourney.signOff()

        var secondResult = defaultJourney.start(backchannelUri = redirectUri.toUri())
        assertTrue(
            "Expected AM to restart the journey with a fresh ContinueNode on reuse, got ${secondResult::class.simpleName}",
            secondResult is ContinueNode
        )
        (secondResult as ContinueNode).handleLoginCallbacks(USERNAME, PASSWORD)
        secondResult = secondResult.next()

        assertTrue(
            "Expected ErrorNode when finalizing an already-completed transaction, got ${secondResult::class.simpleName}",
            secondResult is ErrorNode
        )
        assertEquals("Unable to approve transaction", (secondResult as ErrorNode).message)
    }

    // allowRetry=false denies the transaction after the first failure — per the design
    // doc's error-handling table ("allowRetry: false + prior failure -> ErrorNode on subsequent
    // attempt"), a second attempt against a transaction initialized with allowRetry=false must be
    // rejected once the first attempt has failed, even with correct credentials.
    @Test
    fun backchannelAuthentication_deniedTransactionRetried_returnsErrorNode() = runTest {
        val redirectUri = initializeBackchannelTransaction(allowRetry = false)

        var firstResult = defaultJourney.start(backchannelUri = redirectUri.toUri())
        if (firstResult is ContinueNode) {
            firstResult.handleLoginCallbacks("invalidUser", "invalidPassword")
            firstResult = firstResult.next()
        }
        assertTrue(
            "Expected ErrorNode on first failed attempt, got ${firstResult::class.simpleName}",
            firstResult is ErrorNode
        )
        assertEquals("Login failure", (firstResult as ErrorNode).message)

        var secondResult = defaultJourney.start(backchannelUri = redirectUri.toUri())
        if (secondResult is ContinueNode) {
            secondResult.handleLoginCallbacks(USERNAME, PASSWORD)
            secondResult = secondResult.next()
        }

        assertTrue(
            "Expected ErrorNode when retrying a denied transaction, got ${secondResult::class.simpleName}",
            secondResult is ErrorNode
        )
        assertEquals("Unable to approve transaction", (secondResult as ErrorNode).message)
    }

    // Network error during the authenticate call — a transport-level failure that occurs
    // *after* the backchannelUri passes client-side validation must surface as a FailureNode, not
    // a crash. There's no airplane-mode toggle available from an instrumented test, so a Journey
    // with an unrealistically short timeout against the real server stands in for "no
    // connectivity" — same technique as JourneyE2ETests.handleFailure().
    @Test
    fun backchannelAuthentication_networkErrorDuringAuthenticate_returnsFailureNode() = runTest {
        val redirectUri = initializeBackchannelTransaction()
        val journeyWithShortTimeout = Journey {
            logger = Logger.STANDARD
            timeout = 10.milliseconds.inWholeMilliseconds
            serverUrl = SERVER_URL
            realm = REALM
            cookie = COOKIE
        }

        val result = journeyWithShortTimeout.start(backchannelUri = redirectUri.toUri())

        val failure = assertIsFailureNode(result)
        assertTrue(
            "Expected HttpRequestTimeoutException cause but got ${failure.cause}",
            failure.cause is HttpRequestTimeoutException
        )
    }

    // ---------------------------------------------------------------------------
    // Gateway simulation — obtains a real redirectUri from AM, standing in for the
    // federation gateway that would normally call these endpoints server-side.
    // ---------------------------------------------------------------------------

    /**
     * Simulates the federation gateway: gets a client-credentials access token, then calls
     * `/authenticate/backchannel/initialize` for [tree] and [USERNAME], returning the `redirectUri`
     * from the response. [allowRetry] is forwarded to AM as-is (default `true`, matching AM's own
     * default) — pass `false` to test the "first failure denies the transaction" behavior
     * documented in the design doc's error-handling table.
     */
    private suspend fun initializeBackchannelTransaction(allowRetry: Boolean = true): String {
        val client = HttpClient { logger = Logger.STANDARD }
        try {
            val accessToken = obtainGatewayAccessToken(client)

            val response = client.request {
                url = "$SERVER_URL/json/realms/root/realms/$REALM/authenticate/backchannel/initialize"
                header("Authorization", "Bearer $accessToken")
                header("Accept-API-Version", "resource=1, protocol=2.0")
                post(buildJsonObject {
                    put("type", "service")
                    put("value", tree)
                    putJsonObject("data") {
                        put("username", USERNAME)
                    }
                    put("allowRetry", allowRetry)
                })
            }
            val body = response.assertSuccess("backchannel/initialize")

            return Json.parseToJsonElement(body).jsonObject["redirectUri"]?.jsonPrimitive?.content
                ?: error("backchannel/initialize response missing 'redirectUri'")
        } finally {
            client.close()
        }
    }

    /**
     * A syntactically valid backchannel URI that is never dereferenced — used by test cases whose
     * validation guard fires before the URI is ever inspected (e.g. TC-10's config checks).
     */
    private val placeholderBackchannelUri
        get() = "$SERVER_URL/UI/Login?authIndexType=transaction&authIndexValue=placeholder".toUri()

    /** Upper-cases only the host portion of a `redirectUri` string, leaving the rest untouched. */
    private fun String.uppercaseHost(): String {
        val host = toUri().host ?: error("Cannot uppercase host: no host in URI: $this")
        return replaceFirst(host, host.uppercase())
    }

    /** Replaces only the host portion of a `redirectUri` string with [newHost]. */
    private fun String.withHost(newHost: String): String {
        val host = toUri().host ?: error("Cannot replace host: no host in URI: $this")
        return replaceFirst(host, newHost)
    }

    /** Returns a copy of this `redirectUri` string with the [name] query parameter removed. */
    private fun String.withoutQueryParam(name: String): String {
        val uri = toUri()
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != name }.forEach { key ->
            uri.getQueryParameters(key).forEach { value ->
                builder.appendQueryParameter(key, value)
            }
        }
        return builder.build().toString()
    }

    /**
     * Returns a copy of this `redirectUri` string with the [name] query parameter set to [value],
     * replacing its existing value (or adding it) while leaving every other parameter untouched.
     */
    private fun String.withQueryParam(name: String, value: String): String {
        val uri = toUri()
        val builder = uri.buildUpon().clearQuery()
        var replaced = false
        uri.queryParameterNames.forEach { key ->
            if (key == name) {
                builder.appendQueryParameter(key, value)
                replaced = true
            } else {
                uri.getQueryParameters(key).forEach { builder.appendQueryParameter(key, it) }
            }
        }
        if (!replaced) builder.appendQueryParameter(name, value)
        return builder.build().toString()
    }

    /** Asserts [node] is a [FailureNode] and returns it, failing with a clear message otherwise. */
    private fun assertIsFailureNode(node: Node): FailureNode {
        assertTrue("Expected FailureNode but got ${node::class.simpleName}", node is FailureNode)
        return node as FailureNode
    }

    /** Requests a gateway access token via the OAuth2 client-credentials grant. */
    private suspend fun obtainGatewayAccessToken(client: SdkHttpClient): String {
        val response = client.request {
            url = "$SERVER_URL/oauth2/$REALM/access_token"
            form {
                put("grant_type", "client_credentials")
                put("client_id", backchannelClientId)
                put("client_secret", backchannelClientSecret)
                put("scope", "back_channel_authentication")
            }
        }
        val body = response.assertSuccess("access_token")

        return Json.parseToJsonElement(body).jsonObject["access_token"]?.jsonPrimitive?.content
            ?: error("access_token response missing 'access_token'")
    }

    /**
     * Reads the response body and fails fast with the status if the call was not successful.
     * Deliberately excludes the body from the failure message — it can carry OAuth tokens or
     * transaction/redirect data, and instrumented-test failure messages are published in test
     * reports.
     */
    private suspend fun HttpResponse.assertSuccess(label: String): String {
        val body = body()
        check(status.isSuccess()) { "$label call failed: HTTP $status" }
        return body
    }
}
