/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey.callback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pingidentity.recognize.journey.PingOneRecognizeAuthenticateCallback
import com.pingidentity.recognize.journey.PingOneRecognizeEnrollCallback

@Composable
fun RecognizeCallbacks(callback: Any, onNext: () -> Unit): Boolean =
    when (callback) {
        is PingOneRecognizeEnrollCallback -> {
            LaunchedEffect(callback) { callback.enroll(); onNext() }
            true
        }
        is PingOneRecognizeAuthenticateCallback -> {
            LaunchedEffect(callback) { callback.authenticate(); onNext() }
            true
        }
        else -> false
    }
