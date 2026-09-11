/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.davinci

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.davinci.plugin.CollectorFactory

/**
 * AndroidX App Startup [Initializer] that registers PingOne MFA DaVinci collectors with
 * [CollectorFactory].
 *
 * This class is declared in the module's `AndroidManifest.xml` under the
 * `androidx.startup.InitializationProvider` and runs automatically before
 * `Application.onCreate` — no manual call is required.
 *
 * Registered collectors:
 * - `"MOBILE_PAIRING"` → [MobilePairingCollector]
 */
class CollectorInitializer : Initializer<CollectorFactory> {

    /**
     * Registers all PingOne MFA collectors with [CollectorFactory].
     *
     * @param context The application [Context] provided by App Startup.
     * @return The [CollectorFactory] singleton, as required by the [Initializer] contract.
     */
    override fun create(context: Context): CollectorFactory {
        CollectorFactory.register("MOBILE_PAIRING", ::MobilePairingCollector)
        return CollectorFactory
    }

    /**
     * No dependencies — this initializer runs independently of other [Initializer]s.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }

}
