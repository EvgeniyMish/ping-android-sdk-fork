/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.plugin.CallbackRegistry

/**
 * AndroidX App Startup initializer that registers Recognize Journey callbacks with
 * [CallbackRegistry].
 *
 * Declared in AndroidManifest.xml — no explicit call needed from application code.
 * Keyless SDK initialization is handled by [RecognizeKeylessInitializer].
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    override fun create(context: Context): CallbackRegistry {
        CallbackRegistry.register(
            "PingOneRecognizeCallback",
            ::RecognizeCallback
        )
        return CallbackRegistry
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(RecognizeKeylessInitializer::class.java)
    }
}
