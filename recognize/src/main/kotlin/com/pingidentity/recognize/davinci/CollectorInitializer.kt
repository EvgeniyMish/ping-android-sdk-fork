/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.davinci.plugin.CollectorFactory

/**
 * AndroidX App Startup initializer that registers [RecognizeCollector] with DaVinci's
 * [CollectorFactory] under the key "RECOGNIZE".
 *
 * Declared in AndroidManifest.xml — no explicit call needed from application code.
 */
class CollectorInitializer : Initializer<CollectorFactory> {

    override fun create(context: Context): CollectorFactory {
        // TODO: confirm DaVinci component inputType / type value with server team
        CollectorFactory.register("RECOGNIZE", ::RecognizeCollector)
        return CollectorFactory
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
