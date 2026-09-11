/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.plugin.CallbackRegistry
import io.keyless.sdk.Keyless

/**
 * AndroidX App Startup initializer that calls [Keyless.initialize].
 *
 * Declared as a dependency of [CallbackInitializer] so the Keyless SDK is ready before
 * any Recognize callback is instantiated. Declared in AndroidManifest.xml — no explicit
 * call needed from application code.
 */
class RecognizeKeylessInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        Keyless.initialize(context.applicationContext as Application)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
