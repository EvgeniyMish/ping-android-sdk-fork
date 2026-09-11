/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.collector.QRCodeCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E tests for [QRCodeCollector] — DaVinci QR_CODE-type form field (SDKS-4679).
 *
 * The test flow starts with a "Select Test Form" that presents a FLOW_BUTTON "QRCode" button.
 * Tapping QRCode transitions to the "Automation - QR Code" showForm node which contains:
 *
 *   index 0 — LabelCollector  (key "title-text", richContent "QR Code Collector Test")
 *   index 1 — QRCodeCollector (key "qr-code", content as raw Base64 data URI, non-empty fallbackText)
 *   index 2 — SubmitCollector (key "submit", label "Continue")
 *
 * The ERROR_DISPLAY field returned by the form has no registered collector type and is
 * intentionally dropped by the CollectorFactory, hence 3 collectors for 4 fields.
 *
 * Tapping Continue loops back to the Select Test Form.
 *
 * Parity with iOS (SDKS-5299): Android keeps the raw data URI ("data:image/png;base64,...")
 * in [QRCodeCollector.content] exactly as received from the server; iOS was aligned to the
 * same format (ping-ios-sdk#195). These tests pin that contract — if content ever arrives
 * stripped of the prefix, the content assertions below must fail.
 *
 */
@SmallTest
class QRCodeCollectorE2ETest {

    companion object {
        // --- Select Test Form ---
        const val QRCODE_BUTTON_INDEX = 2   // FLOW_BUTTON (0 = Metadata, 1 = Image, 2 = QRCode)

        // --- Automation - QR Code node ---
        const val LABEL_INDEX = 0           // LABEL (title-text)
        const val QRCODE_INDEX = 1          // QR_CODE (qr-code)
        const val SUBMIT_INDEX = 2          // SUBMIT_BUTTON (Continue)

        const val EXPECTED_NODE_NAME = "Automation - QR Code"
        const val EXPECTED_QRCODE_KEY = "qr-code"
        const val EXPECTED_LABEL_RICH_CONTENT = "QR Code Collector Test"
        const val EXPECTED_FALLBACK_TEXT =
            "If you can't scan the QR code, use the following link:https://www.pingidentity.com/en.html"
        const val EXPECTED_CONTENT_PREFIX = "data:image/png;base64,"
    }

    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = DaVinciTestConfig.davinciClientId
            discoveryEndpoint = DaVinciTestConfig.davinciDiscoveryEndpoint
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = DaVinciTestConfig.davinciRedirectUri
            acrValues = DaVinciTestConfig.davinciImageAcrValues
        }
    }

    @BeforeTest
    fun setUp() = runTest {
        daVinci.user()?.logout()
    }

    // -------------------------------------------------------------------------
    // Helper: navigate from start() to the Automation - QR Code form
    // -------------------------------------------------------------------------

    private suspend fun navigateToQRCodeForm(): ContinueNode {
        var node = daVinci.start() as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertTrue(node.collectors[QRCODE_BUTTON_INDEX] is FlowCollector)
        assertEquals("QRCode", (node.collectors[QRCODE_BUTTON_INDEX] as FlowCollector).label)
        (node.collectors[QRCODE_BUTTON_INDEX] as FlowCollector).value = "click"
        return node.next() as ContinueNode
    }

    // =========================================================================
    // Node shape (QRC-013)
    // =========================================================================

    @Test
    fun automationQRCodeNodeHasCorrectShape() = runTest {
        val node = navigateToQRCodeForm()

        assertEquals(EXPECTED_NODE_NAME, node.name)
        // 4 fields in the form JSON; the unregistered ERROR_DISPLAY type is dropped
        assertEquals(3, node.collectors.size)
        assertTrue(node.collectors[LABEL_INDEX] is LabelCollector)
        assertTrue(node.collectors[QRCODE_INDEX] is QRCodeCollector)
        assertTrue(node.collectors[SUBMIT_INDEX] is SubmitCollector)
    }

    @Test
    fun automationQRCodeNodeHasCorrectName() = runTest {
        val node = navigateToQRCodeForm()
        assertEquals(EXPECTED_NODE_NAME, node.name)
    }

    // =========================================================================
    // QRCodeCollector properties (QRC-001, QRC-003, QRC-004, QRC-006)
    // =========================================================================

    @Test
    fun qrCodeCollectorIsInstantiatedForQR_CODEType() = runTest {
        val node = navigateToQRCodeForm()
        assertTrue(node.collectors[QRCODE_INDEX] is QRCodeCollector)
    }

    @Test
    fun qrCodeCollectorHasCorrectKey() = runTest {
        val node = navigateToQRCodeForm()
        val collector = node.collectors[QRCODE_INDEX] as QRCodeCollector
        assertEquals(EXPECTED_QRCODE_KEY, collector.key)
    }

    @Test
    fun qrCodeCollectorContentIsTheRawDataUri() = runTest {
        val node = navigateToQRCodeForm()
        val collector = node.collectors[QRCODE_INDEX] as QRCodeCollector

        // SDKS-5299: content must retain the raw data URI exactly as received
        assertTrue(
            collector.content.startsWith(EXPECTED_CONTENT_PREFIX),
            "Expected content to start with '$EXPECTED_CONTENT_PREFIX', was: ${collector.content.take(60)}"
        )
    }

    @Test
    fun qrCodeCollectorHasCorrectFallbackText() = runTest {
        val node = navigateToQRCodeForm()
        val collector = node.collectors[QRCODE_INDEX] as QRCodeCollector
        assertEquals(EXPECTED_FALLBACK_TEXT, collector.fallbackText)
        assertTrue(collector.fallbackText.isNotEmpty())
    }

    @Test
    fun qrCodeCollectorIdReturnsKey() = runTest {
        val node = navigateToQRCodeForm()
        val collector = node.collectors[QRCODE_INDEX] as QRCodeCollector
        assertEquals(collector.key, collector.id())
    }

    // =========================================================================
    // bitmap() decoding (QRC-007)
    // =========================================================================

    @Test
    fun qrCodeCollectorBitmapDecodesToValidBitmap() = runTest {
        val node = navigateToQRCodeForm()
        val collector = node.collectors[QRCODE_INDEX] as QRCodeCollector

        val bitmap = collector.bitmap()
        assertNotNull(bitmap, "bitmap() must decode the valid Base64 PNG data URI")
        assertTrue(bitmap.width > 0, "Decoded bitmap must have positive width")
        assertTrue(bitmap.height > 0, "Decoded bitmap must have positive height")
    }

    // =========================================================================
    // Flow integration (QRC-014, QRC-015)
    // =========================================================================

    @Test
    fun qrCodeCollectorDoesNotContributeToFormPayload() = runTest {
        val node = navigateToQRCodeForm()
        val collector = node.collectors[QRCODE_INDEX] as QRCodeCollector

        // A null payload means the QR_CODE field is omitted from the form POST
        assertNull(collector.payload())
    }

    @Test
    fun submittingQRCodeFormLoopsBackToSelectTestForm() = runTest {
        var node = navigateToQRCodeForm()

        // Submit without interacting with the QR code — submission must succeed
        (node.collectors[SUBMIT_INDEX] as SubmitCollector).value = "click"
        node = node.next() as ContinueNode
        assertEquals("Select Test Form", node.name)
    }

    // =========================================================================
    // LabelCollector companion field
    // =========================================================================

    @Test
    fun labelCollectorRichContentContainsTitle() = runTest {
        val node = navigateToQRCodeForm()
        val label = node.collectors[LABEL_INDEX] as LabelCollector
        val richContent = label.richContent
        assertNotNull(richContent)
        assertEquals(EXPECTED_LABEL_RICH_CONTENT, richContent.content)
    }
}
