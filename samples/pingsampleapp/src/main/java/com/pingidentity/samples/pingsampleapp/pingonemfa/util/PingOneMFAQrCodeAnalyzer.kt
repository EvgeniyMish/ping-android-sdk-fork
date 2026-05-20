/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.util

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.TimeUnit

/**
 * Analyzes camera images to detect PingOne MFA pairing QR codes.
 *
 * Only triggers [onQrCodeDetected] for QR codes whose content matches the PingOne pairing key
 * scheme (numeric prefix, 12 or 14 characters). All other QR codes are silently ignored.
 *
 * @param onQrCodeDetected Callback invoked with the raw pairing key when a valid code is found.
 */
class PingOneMFAQrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    // Throttle to avoid firing multiple times on the same code
    private var lastAnalyzedTimestamp = 0L

    /**
     * Releases the ML Kit barcode client.
     *
     * CameraX 1.x does not expose an analyzer lifecycle callback, so callers are
     * responsible for calling this when the camera is unbound. In the QR scanner screen
     * this is done inside the DisposableEffect that also shuts down the camera executor,
     * ensuring the native ML Kit resources are freed at the same time as the camera.
     */
    fun close() {
        scanner.close()
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        if (currentTimestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
            imageProxy.image?.let { image ->
                val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)

                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val found = barcodes.find { barcode ->
                            barcode.format == Barcode.FORMAT_QR_CODE &&
                                    barcode.rawValue?.matchesPingOnePairingKeyScheme() == true
                        }
                        found?.rawValue?.let { pairingKey ->
                            lastAnalyzedTimestamp = currentTimestamp
                            onQrCodeDetected(pairingKey)
                        }
                    }
                    .addOnFailureListener { it.printStackTrace() }
                    .addOnCompleteListener { imageProxy.close() }
            } ?: imageProxy.close()
        } else {
            imageProxy.close()
        }
    }
}

/**
 * Returns true if this string looks like a valid PingOne MFA pairing key.
 *
 * Accepts numeric and alphanumeric pairing keys that start with two digits
 * and are exactly 12 or 14 characters long.
 */
fun String.matchesPingOnePairingKeyScheme(): Boolean =
    length >= 2 && this[0].isDigit() && this[1].isDigit() && (length == 12 || length == 14)
